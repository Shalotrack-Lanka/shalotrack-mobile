package com.example.letstracklanka.utils;

import android.app.Activity;
import android.view.WindowManager;

/**
 * Marks a screen as secure (FLAG_SECURE): no screenshots, no screen recording, and a blank thumbnail in the
 * recent-apps list. Used on screens that show or collect personal data (sign-in, code, details, profile,
 * payments, account deletion). Call before or right after setContentView.
 */
public final class SecureScreen {

    private SecureScreen() {}

    public static void enable(Activity activity) {
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
    }
}
