package com.example.letstracklanka.ui.main;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.example.letstracklanka.R;

import java.util.Calendar;

/**
 * MainActivity — loading/transition screen between SplashActivity and HomeActivity.
 *
 * ── Time-aware animation ─────────────────────────────────────────────────────
 * Reads the device clock at launch and selects the appropriate Lottie scene:
 *
 *   06:00 – 18:59  →  shalotrack_drive_day.json
 *                      Blue sky, sun, moving clouds, daylight buildings,
 *                      headlights off, sky reflections in windows.
 *
 *   19:00 – 05:59  →  shalotrack_drive_night.json
 *                      Deep navy sky, stars, glowing city windows,
 *                      street lights, headlight beams, tail light glow.
 *
 * ── Timing ───────────────────────────────────────────────────────────────────
 *   0 ms      Lottie starts (file resolved from device time)
 *   300 ms    Logo fades in (350ms, scale 0.85 → 1.0)
 *   600 ms    "Connecting to your vehicles..." fades in (300ms)
 *   800 ms    Progress line grows left → right (2200ms)
 *   3000 ms   Crossfade to HomeActivity
 */
public class MainActivity extends AppCompatActivity {

    private static final long NAVIGATE_AFTER_MS  = 3000L;
    private static final int  DAY_START_HOUR      = 6;   // 06:00
    private static final int  NIGHT_START_HOUR    = 19;  // 19:00

    private LottieAnimationView lottieAnim;
    private ImageView           mainLogo;
    private TextView            tvLoadingMessage;
    private View                progressLine;
    private Handler             handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        lottieAnim       = findViewById(R.id.lottieCarAnimation);
        mainLogo         = findViewById(R.id.mainLogo);
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage);
        progressLine     = findViewById(R.id.progressLine);

        handler = new Handler(Looper.getMainLooper());

        // Select day or night animation from device clock — no permissions needed
        lottieAnim.setAnimation(resolveAnimationFileName());
        lottieAnim.playAnimation();

        playEntrance();

        handler.postDelayed(this::goToHome, NAVIGATE_AFTER_MS);
    }

    // ── Time logic ────────────────────────────────────────────────────────────

    /**
     * Returns the Lottie asset filename for the current time of day.
     * Day   = 06:00–18:59  →  shalotrack_drive_day.json
     * Night = 19:00–05:59  →  shalotrack_drive_night.json
     */
    private String resolveAnimationFileName() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean isDay = (hour >= DAY_START_HOUR && hour < NIGHT_START_HOUR);
        return isDay ? "shalotrack_drive_day.json" : "shalotrack_drive_night.json";
    }

    // ── Animation ─────────────────────────────────────────────────────────────

    private void playEntrance() {

        // Logo fade-in at 300ms (scale from 0.85 → 1.0 for a gentle pop)
        handler.postDelayed(() -> {
            ObjectAnimator logoFade   = ObjectAnimator.ofFloat(mainLogo, View.ALPHA,   0f, 1f);
            ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(mainLogo, View.SCALE_X, 0.85f, 1f);
            ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(mainLogo, View.SCALE_Y, 0.85f, 1f);
            logoFade.setDuration(350);
            logoScaleX.setDuration(350);
            logoScaleY.setDuration(350);
            logoFade.setInterpolator(new DecelerateInterpolator());
            logoScaleX.setInterpolator(new DecelerateInterpolator());
            logoScaleY.setInterpolator(new DecelerateInterpolator());
            AnimatorSet logoSet = new AnimatorSet();
            logoSet.playTogether(logoFade, logoScaleX, logoScaleY);
            logoSet.start();
        }, 300);

        // "Connecting to your vehicles..." fades in at 600ms
        handler.postDelayed(() -> {
            ObjectAnimator textFade = ObjectAnimator.ofFloat(tvLoadingMessage, View.ALPHA, 0f, 1f);
            textFade.setDuration(300);
            textFade.setInterpolator(new DecelerateInterpolator());
            textFade.start();
        }, 600);

        // Progress line grows left → right from 800ms over 2200ms
        handler.postDelayed(() -> {
            progressLine.setAlpha(1f);
            ObjectAnimator lineGrow = ObjectAnimator.ofFloat(progressLine, View.SCALE_X, 0f, 1f);
            lineGrow.setDuration(2200);
            lineGrow.setInterpolator(new DecelerateInterpolator(1.5f));
            lineGrow.start();
        }, 800);
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void goToHome() {
        lottieAnim.cancelAnimation();
        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
        startActivity(intent);
        @SuppressWarnings("deprecation")
        int unused = 0; // overridePendingTransition is deprecated in API 34 but needed for API 26+
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        lottieAnim.cancelAnimation();
    }
}