package com.example.letstracklanka.ui.renewals;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.lang.ref.WeakReference;

/**
 * App-wide "your subscription needs renewing" prompt. Install once from ShaloTrackApp.onCreate.
 *
 * Tracks the activity that is on screen and, when the interceptor reports a 402, shows ONE dialog on
 * it. Several screens often fire requests together, so the prompt is limited to one at a time and
 * one per minute; it is never shown on the renewal screen itself, nor over a dialog that is open.
 * All work happens on the main thread.
 */
public final class RenewalPrompt {

    private static final long MIN_GAP_MS = 60_000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> resumed = new WeakReference<>(null);
    private static AlertDialog showing;
    private static long lastShownAt = -MIN_GAP_MS;
    private static boolean installed = false;

    private RenewalPrompt() {
    }

    public static void install(@NonNull Application app) {
        if (installed) return;
        installed = true;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(@NonNull Activity activity) {
                resumed = new WeakReference<>(activity);
            }

            @Override public void onActivityPaused(@NonNull Activity activity) {
                if (resumed.get() == activity) resumed = new WeakReference<>(null);
                dismissIfOn(activity);
            }

            @Override public void onActivityDestroyed(@NonNull Activity activity) {
                dismissIfOn(activity);
            }

            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { }
            @Override public void onActivityStarted(@NonNull Activity a) { }
            @Override public void onActivityStopped(@NonNull Activity a) { }
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }
        });
    }

    /** Safe to call from any thread (the OkHttp interceptor runs on a worker). */
    public static void onRenewalRequired() {
        MAIN.post(RenewalPrompt::showIfAppropriate);
    }

    private static void showIfAppropriate() {
        Activity activity = resumed.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (activity instanceof RenewalActivity) return;
        if (showing != null && showing.isShowing()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastShownAt < MIN_GAP_MS) return;
        lastShownAt = now;

        showing = new AlertDialog.Builder(activity)
                .setTitle("Renewal required")
                .setMessage("The subscription for one of your vehicles has expired, so live tracking and "
                        + "history are paused. Renew it to get them back.")
                .setPositiveButton("Renew now", (d, w) ->
                        activity.startActivity(new Intent(activity, RenewalActivity.class)))
                .setNegativeButton("Later", null)
                .create();
        showing.show();
    }

    private static void dismissIfOn(Activity activity) {
        if (showing != null && showing.isShowing()
                && showing.getContext() != null && contextIs(showing, activity)) {
            showing.dismiss();
        }
    }

    private static boolean contextIs(AlertDialog dialog, Activity activity) {
        android.content.Context c = dialog.getContext();
        while (c instanceof android.content.ContextWrapper) {
            if (c == activity) return true;
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return false;
    }
}