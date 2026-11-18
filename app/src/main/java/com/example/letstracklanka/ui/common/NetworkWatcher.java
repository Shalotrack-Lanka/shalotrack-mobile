package com.example.letstracklanka.ui.common;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/**
 * Watches the phone's own connection for one screen and unregisters itself when the screen is destroyed.
 * Calls onLost when the connection drops, and onRestored only when it comes BACK after a loss (the old
 * per-screen copies also fired on registration, which loaded every screen's data twice on open).
 */
public final class NetworkWatcher implements DefaultLifecycleObserver {

    public interface Listener {
        void onLost();
        void onRestored();
    }

    private final ConnectivityManager cm;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback callback;
    private volatile boolean lost;

    private NetworkWatcher(Context context, Listener listener) {
        this.cm = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        this.listener = listener;
        this.lost = !isOnline(context);
    }

    /** Starts watching for this lifecycle owner (an Activity). */
    public static <T extends Context & LifecycleOwner> NetworkWatcher watch(T owner, Listener listener) {
        NetworkWatcher w = new NetworkWatcher(owner, listener);
        owner.getLifecycle().addObserver(w);
        w.start();
        if (w.lost) w.main.post(listener::onLost);
        return w;
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void start() {
        if (cm == null) return;
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                if (lost) {
                    lost = false;
                    main.post(listener::onRestored);
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                lost = true;
                main.post(listener::onLost);
            }
        };
        try {
            cm.registerDefaultNetworkCallback(callback);
        } catch (RuntimeException e) {
            // Too many callbacks registered app-wide (rare): the screen still works, just without the banner.
            callback = null;
        }
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        if (cm != null && callback != null) {
            try {
                cm.unregisterNetworkCallback(callback);
            } catch (RuntimeException ignored) {
                // already unregistered
            }
        }
        callback = null;
        main.removeCallbacksAndMessages(null);
    }
}
