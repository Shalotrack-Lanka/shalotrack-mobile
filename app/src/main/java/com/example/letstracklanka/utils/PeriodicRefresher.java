package com.example.letstracklanka.utils;

import android.os.Handler;
import android.os.Looper;

/**
 * Generic foreground-only polling helper: repeats a refresh action on a
 * fixed interval while the host screen is visible, so a list or thread
 * updates on its own instead of requiring the user to leave and reopen
 * the screen (or pull-to-refresh) to see something new.
 *
 * Deliberately scoped to "while this screen is on-screen" rather than
 * true OS background refresh (WorkManager/JobScheduler): start() is
 * called from the host's onResume() and stop() from onPause(), so the
 * timer costs nothing the moment the user actually leaves the screen.
 * Updates while the app is genuinely backgrounded are already covered by
 * FCM push (see ShaloTrackFirebaseMessagingService) -- a battery-draining
 * true background poller would be solving a problem push already solves.
 *
 * Not thread-safety-hardened beyond normal main-thread Handler use --
 * every method here must be called from the main thread, same as the
 * activities that own one of these.
 */
public class PeriodicRefresher {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long intervalMs;
    private final Runnable refreshAction;
    private Runnable tickRunnable;

    public PeriodicRefresher(long intervalMs, Runnable refreshAction) {
        this.intervalMs = intervalMs;
        this.refreshAction = refreshAction;
    }

    public void start() {
        stop();
        tickRunnable = new Runnable() {
            @Override
            public void run() {
                refreshAction.run();
                handler.postDelayed(this, intervalMs);
            }
        };
        handler.postDelayed(tickRunnable, intervalMs);
    }

    public void stop() {
        if (tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
            tickRunnable = null;
        }
    }
}