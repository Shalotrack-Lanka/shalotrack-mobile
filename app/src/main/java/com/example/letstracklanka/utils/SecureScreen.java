package com.example.letstracklanka.utils;

import android.app.Activity;
import android.view.WindowManager;

/**
 * Screen-level protections.
 *
 * enable(): FLAG_SECURE (no screenshots, no screen recording, blank thumbnail in recent apps) plus the
 * tap-jacking guard. For screens that show or collect personal data: sign-in, code, details, profile,
 * payments, account deletion.
 *
 * guardTouches(): ignores taps while another app draws over this screen (the "tap-jacking" trick where a
 * transparent overlay makes you press a button you cannot see). Android 12+ blocks most such overlays by
 * itself; this covers Android 8 to 11, which this app still supports. Used on every screen with an action
 * that matters (SOS, remove vehicle, sign out, delete account, link tracker).
 */
public final class SecureScreen {

    private SecureScreen() {}

    public static void enable(Activity activity) {
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        guardTouches(activity);
    }

    public static void guardTouches(Activity activity) {
        activity.getWindow().getDecorView().setFilterTouchesWhenObscured(true);
    }
}
