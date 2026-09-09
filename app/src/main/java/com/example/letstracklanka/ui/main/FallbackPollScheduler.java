package com.example.letstracklanka.ui.main;

import android.os.Handler;

import java.util.function.BooleanSupplier;

/**
 * Extracted from the exact, line-for-line identical fallback-polling
 * logic that was duplicated in both HomeActivity and VehiclesActivity
 * (both fixed with the same SignalR-suppression bug, same fix, same
 * shape). Deliberately scoped narrow: this owns ONLY the tick timing and
 * the decision of whether a given tick should poll -- it does not touch
 * VehicleTrailRenderer or RealtimeLocationClient connection setup at
 * all, since that logic involves subtle, pre-existing race-condition
 * guards (two independent async code paths in HomeActivity racing to
 * establish the initial connection) that predate this refactor and
 * weren't safe to merge in the same pass.
 *
 * Each host still owns its own RealtimeLocationClient field and decides
 * when/how to create and connect it; this class only asks (via
 * isPushConnected) whether that connection is currently up, and calls
 * back into the host's own poll method when a poll should actually
 * happen.
 */
public class FallbackPollScheduler {

    private static final int UPDATE_INTERVAL_MS = 1000;
    // Same reasoning as before: RealtimeLocationClient already retries a
    // dropped connection every 5s, so real disconnects should be brief.
    // 5s fallback trades a small extra battery/network cost during that
    // short window for smoother, less corner-cutting fallback movement.
    private static final int FALLBACK_POLL_INTERVAL_TICKS = 5;

    private final Handler handler;
    private Runnable tickRunnable;
    private int tickCount = 0;

    public FallbackPollScheduler(Handler handler) {
        this.handler = handler;
    }

    /**
     * @param isPushConnected re-evaluated fresh on every tick (not
     *                        captured once), since the host's own
     *                        RealtimeLocationClient field typically
     *                        starts null and gets assigned later inside
     *                        an async callback.
     * @param pollAction      the host's own fetchLocation()/
     *                        fetchLocationData() equivalent.
     * @param extraPerTick    optional, called every tick regardless of
     *                        push state (e.g. HomeActivity's
     *                        fetchDashboard(), which has no SignalR
     *                        equivalent to suppress against). Pass null
     *                        if the host doesn't need this.
     */
    public void start(BooleanSupplier isPushConnected, Runnable pollAction, Runnable extraPerTick) {
        stop();
        tickCount = 0;
        tickRunnable = new Runnable() {
            @Override
            public void run() {
                tickCount++;
                boolean pushConnected = isPushConnected.getAsBoolean();
                if (tickCount == 1 || !pushConnected || tickCount % FALLBACK_POLL_INTERVAL_TICKS == 0) {
                    pollAction.run();
                }
                if (extraPerTick != null) extraPerTick.run();
                handler.postDelayed(this, UPDATE_INTERVAL_MS);
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