package com.example.letstracklanka.ui.main;

import android.os.Handler;

import java.util.function.BooleanSupplier;

/**
 * Location polling for a screen that also listens for live pushes (SignalR). Same cadence as the Map
 * tab: poll once at start, then every 5 s while the push connection is down, and only every 30 s as a
 * safety net while it is up (pushes already carry every new position). The host must call stop() in
 * onStop and start() again in onStart, so nothing polls while the app is in the background.
 *
 * The old version ticked every second, polled every 5 s even with pushes working, and was never
 * stopped until the screen was destroyed.
 */
public class FallbackPollScheduler {

    private static final long TICK_MS = 1000;
    private static final int POLL_EVERY_TICKS_WHEN_DOWN = 5;
    private static final int POLL_EVERY_TICKS_WHEN_UP = 30;

    private final Handler handler;
    private Runnable tickRunnable;
    private int ticksSincePoll = 0;

    public FallbackPollScheduler(Handler handler) {
        this.handler = handler;
    }

    /**
     * @param isPushConnected asked on every tick (the host's client is often created later, async).
     * @param pollAction      the host's single-vehicle location fetch.
     * @param extraPerTick    optional work on every poll (not every tick); null for none.
     */
    public void start(BooleanSupplier isPushConnected, Runnable pollAction, Runnable extraPerTick) {
        stop();
        ticksSincePoll = 0;
        tickRunnable = new Runnable() {
            boolean first = true;

            @Override
            public void run() {
                int every = isPushConnected.getAsBoolean() ? POLL_EVERY_TICKS_WHEN_UP : POLL_EVERY_TICKS_WHEN_DOWN;
                if (first || ++ticksSincePoll >= every) {
                    first = false;
                    ticksSincePoll = 0;
                    pollAction.run();
                    if (extraPerTick != null) extraPerTick.run();
                }
                handler.postDelayed(this, TICK_MS);
            }
        };
        handler.post(tickRunnable);
    }

    public void stop() {
        if (tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
            tickRunnable = null;
        }
    }
}
