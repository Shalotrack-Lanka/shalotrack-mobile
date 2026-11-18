package com.example.letstracklanka.ui.common;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Edge-to-edge handling for the redesigned screens. The app targets Android 16 (SDK 36), where the system
 * always draws the app behind the status and navigation bars. Instead of letting that happen differently
 * per Android version, every redesigned screen opts in on every version and pads the right views itself:
 * the map runs under the status bar, while the header, floating buttons and bottom bar move clear of it.
 */
public final class EdgeToEdge {

    private EdgeToEdge() {}

    public static void enable(Activity activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
    }

    /** Adds the status-bar height to the view's top padding (keeps the padding it already has). */
    public static void padTop(View view) {
        if (view == null) return;
        final int start = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(v.getPaddingLeft(), start + bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Adds the navigation-bar height to the view's bottom padding (gesture bar or 3-button bar). */
    public static void padBottom(View view) {
        if (view == null) return;
        final int start = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), start + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Pads both ends: for a screen with no bottom bar of its own. */
    public static void padSystemBars(View view) {
        if (view == null) return;
        final int top = view.getPaddingTop();
        final int bottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(v.getPaddingLeft(), top + bars.top, v.getPaddingRight(), bottom + Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** Adds the status-bar height to the view's top MARGIN (for floating controls over the map). */
    public static void marginTop(View view) {
        if (view == null || !(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
        final int start = ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).topMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.topMargin = start + bars.top;
            v.setLayoutParams(lp);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
