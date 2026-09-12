package com.example.letstracklanka.ui.main;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import com.example.letstracklanka.widget.SignalRingView;
import com.example.letstracklanka.widget.SignalRingView;
/**
 * Drop-in replacement for whatever previously animated mainLogo / progressLine
 * in MainActivity.java. Call SplashAnimations.play(...) once, right after
 * setContentView() and your existing Lottie day/night setup.
 *
 * Example:
 *   SplashAnimations.play(
 *       findViewById(R.id.signalRing),
 *       findViewById(R.id.sweepArm),
 *       findViewById(R.id.dotA), findViewById(R.id.dotB), findViewById(R.id.dotC),
 *       findViewById(R.id.mainLogo),
 *       findViewById(R.id.tvLoadingMessage),
 *       () -> {
 *           // TODO: your existing "go to next screen" call goes here
 *       }
 *   );
 */
public class SplashAnimations {

    public static void play(SignalRingView ring,
                            View sweepArm,
                            View dotA, View dotB, View dotC,
                            View logo,
                            TextView statusText,
                            Runnable onComplete) {

        // 1) continuous radar sweep — spins for as long as the splash is on screen
        ObjectAnimator sweepSpin = ObjectAnimator.ofFloat(sweepArm, View.ROTATION, 0f, 360f);
        sweepSpin.setDuration(2600);
        sweepSpin.setRepeatCount(ObjectAnimator.INFINITE);
        sweepSpin.setInterpolator(new LinearInterpolator());
        sweepSpin.start();

        // 2) satellite dots pop in with a stagger, then pulse gently
        int[] delays = {150, 320, 480};
        View[] dots = {dotA, dotB, dotC};
        for (int i = 0; i < dots.length; i++) {
            View dot = dots[i];
            dot.setScaleX(0f);
            dot.setScaleY(0f);
            dot.setAlpha(0f);
            dot.animate()
                    .scaleX(1f).scaleY(1f).alpha(1f)
                    .setStartDelay(delays[i])
                    .setDuration(360)
                    .setInterpolator(new OvershootInterpolator(3f))
                    .withEndAction(() -> pulse(dot))
                    .start();
        }

        // 3) logo bounces into place at the centre of the ring
        logo.setScaleX(0f);
        logo.setScaleY(0f);
        logo.setAlpha(0f);
        AnimatorSet logoIn = new AnimatorSet();
        logoIn.playTogether(
                ObjectAnimator.ofFloat(logo, View.SCALE_X, 0f, 1f),
                ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0f, 1f),
                ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f)
        );
        logoIn.setStartDelay(400);
        logoIn.setDuration(550);
        logoIn.setInterpolator(new OvershootInterpolator(1.6f));
        logoIn.start();

        // 4) status text slides up and fades in
        statusText.setAlpha(0f);
        statusText.setTranslationY(24f);
        statusText.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(700)
                .setDuration(400)
                .start();

        // 5) the ring fills as "lock" is acquired, then the satellites flash locked
        ring.animateProgress(0.78f, 1800, () -> {
            lockDots(dotA, dotB, dotC);
            if (onComplete != null) onComplete.run();
        });
    }

    private static void pulse(View dot) {
        dot.animate()
                .scaleX(1.18f).scaleY(1.18f)
                .setDuration(650)
                .withEndAction(() -> dot.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(650)
                        .withEndAction(() -> pulse(dot))
                        .start())
                .start();
    }

    private static void lockDots(View... dots) {
        for (View dot : dots) {
            dot.animate().scaleX(1.3f).scaleY(1.3f).setDuration(180)
                    .withEndAction(() -> dot.animate().scaleX(1f).scaleY(1f).setDuration(180).start())
                    .start();
        }
    }
}