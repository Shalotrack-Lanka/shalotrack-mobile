package com.example.letstracklanka.ui.auth;

import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Edge-to-edge for the sign-in screens. With targetSdk 36 Android draws behind the status and navigation
 * bars on every device, so without this the Send/Continue button can sit under the gesture bar and the
 * keyboard can cover the field being typed in. Call after setContentView, passing the root view.
 */
public final class AuthUi {

    private AuthUi() {}

    public static void edgeToEdge(ComponentActivity activity, View root) {
        EdgeToEdge.enable(activity);
        final int l = root.getPaddingLeft(), t = root.getPaddingTop(),
                r = root.getPaddingRight(), b = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
