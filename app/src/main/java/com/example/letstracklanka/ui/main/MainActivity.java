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

/**
 * MainActivity — brief loading/transition screen between SplashActivity and HomeActivity.
 *
 * ── What this screen does ────────────────────────────────────────────────
 * Plays a 15KB Lottie animation of a car driving at night on a navy road
 * (ShaloTrack brand colors: navy #081E3D, orange #FA6908). The animation
 * loops for 3000ms while the app finishes any async startup work, then
 * cross-fades into HomeActivity.
 *
 * ── Why Lottie instead of the old orbiting-items approach ───────────────
 * The previous implementation (orbiting dogs/luggage/headphones on a white
 * background) was unrelated to the product's purpose and off-brand. A car
 * driving on a road directly reflects the GPS vehicle tracking use case.
 * Lottie renders at any resolution as crisp vectors at ~15KB — no MP4,
 * no asset bundle, no decoding overhead.
 *
 * ── Timing ──────────────────────────────────────────────────────────────
 *   0 ms      Lottie starts playing (autoPlay=true in XML)
 *   300 ms    Logo fades in (350ms)
 *   600 ms    "Connecting to your vehicles..." fades in (300ms)
 *   800 ms    Progress line starts growing (left to right, 2200ms)
 *   3000 ms   Navigate to HomeActivity with crossfade
 */
public class MainActivity extends AppCompatActivity {

    private static final long NAVIGATE_AFTER_MS = 3000L;

    private LottieAnimationView lottieAnim;
    private ImageView           mainLogo;
    private TextView            tvLoadingMessage;
    private View                progressLine;
    private Handler             handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Respect system bar insets — keeps content clear of status/nav bars
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

        playEntrance();

        // Navigate to HomeActivity after NAVIGATE_AFTER_MS
        handler.postDelayed(this::goToHome, NAVIGATE_AFTER_MS);
    }

    // ── Animation ────────────────────────────────────────────────────────

    private void playEntrance() {

        // Logo fade-in at 300ms (scale from 0.85 → 1.0 for a gentle pop)
        handler.postDelayed(() -> {
            ObjectAnimator logoFade  = ObjectAnimator.ofFloat(mainLogo, View.ALPHA, 0f, 1f);
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

        // Progress line grows left→right from 800ms over 2200ms
        handler.postDelayed(() -> {
            progressLine.setAlpha(1f);
            ObjectAnimator lineGrow = ObjectAnimator.ofFloat(progressLine, View.SCALE_X, 0f, 1f);
            lineGrow.setDuration(2200);
            lineGrow.setInterpolator(new DecelerateInterpolator(1.5f));
            lineGrow.start();
        }, 800);
    }

    // ── Navigation ───────────────────────────────────────────────────────

    private void goToHome() {
        lottieAnim.cancelAnimation();
        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
        startActivity(intent);
        // Smooth crossfade — no jarring slide transition
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cancel all pending callbacks to prevent leaks
        handler.removeCallbacksAndMessages(null);
        lottieAnim.cancelAnimation();
    }
}