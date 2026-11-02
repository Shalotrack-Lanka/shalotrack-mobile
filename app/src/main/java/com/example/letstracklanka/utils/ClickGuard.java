package com.example.letstracklanka.utils;

import android.os.SystemClock;
import android.view.View;

/**
 * Ignores a second tap that arrives within 700 ms of the first. A double-tap or a held-down finger on
 * Send, Add or Pay otherwise fires two requests (two complaints, two renewals) and, on a slow network,
 * floods our own API. Use: {@code button.setOnClickListener(ClickGuard.wrap(v -> submit()));}
 */
public final class ClickGuard {

    private static final long WINDOW_MS = 700L;

    private ClickGuard() {}

    public static View.OnClickListener wrap(View.OnClickListener real) {
        return new View.OnClickListener() {
            private long last = 0L;

            @Override
            public void onClick(View v) {
                long now = SystemClock.elapsedRealtime();
                if (now - last < WINDOW_MS) return;
                last = now;
                real.onClick(v);
            }
        };
    }
}
