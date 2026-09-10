package com.example.letstracklanka.ui.main

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.letstracklanka.R
import com.example.letstracklanka.ui.auth.EmailInputActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * SplashActivity — app entry point, owns Firebase auth routing.
 *
 * ── Animation sequence (total ~2400 ms before navigation) ───────────────
 *
 *   0 ms      Layout visible. Logo: alpha=0, scale=0.3. Everything hidden.
 *
 *   0–550 ms  PHASE 1 — Scale-in with overshoot.
 *             Logo scales 0.3 → 1.05 (slight overshoot) + fades 0 → 1.
 *             Interpolator: OvershootInterpolator(1.5f) — snappy, not bouncy.
 *
 *   550–750ms PHASE 2 — Settle.
 *             Logo scales 1.05 → 1.0 smoothly. Overshoot lands.
 *
 *   600 ms    Wordmark + tagline fade in (200 ms each, staggered 80 ms).
 *
 *   800 ms    PHASE 3 — Pulse ring starts expanding behind the logo.
 *             Ring: alpha 0→0.6→0, scale 0.8→1.6 over 900 ms, repeating.
 *
 *   900–2400ms PHASE 4 — Logo gentle breathing pulse.
 *             Scale 1.0 → 1.06 → 1.0, 750 ms period, repeat once.
 *             Gives a live "heartbeat" feel while auth state resolves.
 *
 *   2400 ms   Navigate to destination. SplashActivity finishes.
 *
 * ── Auth routing table ──────────────────────────────────────────────────
 *   Logged in + email linked  →  MainActivity (→ HomeActivity)
 *   Logged in, no email       →  EmailInputActivity (finish account setup)
 *   Not logged in             →  WelcomeActivity
 *
 * ── Custom animation rationale ──────────────────────────────────────────
 *   installSplashScreen() / windowSplashScreenAnimatedIcon constrains the
 *   icon to an adaptive icon container (~110 dp). The ShaloTrack logo loses
 *   all detail at that size. Driving the animation from the layout gives
 *   full-screen real estate and complete sequence control at zero extra cost.
 *   Theme.ShaloTrack.Starting still fires the navy background immediately
 *   on window open — there is no white flash before setContentView.
 */
class SplashActivity : AppCompatActivity() {

    companion object {
        // Total time before navigation fires. Must be >= sum of all phases.
        private const val TOTAL_SPLASH_MS = 2400L
    }

    // Views
    private lateinit var logo: ImageView
    private lateinit var pulseRing: ImageView
    private lateinit var wordmark: TextView
    private lateinit var tagline: TextView

    // Repeating pulse animators — kept as fields so we can cancel on destroy
    private var logoBreathAnimator: AnimatorSet? = null
    private var ringPulseAnimator: AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        logo      = findViewById(R.id.splashLogo)
        pulseRing = findViewById(R.id.splashPulseRing)
        wordmark  = findViewById(R.id.splashWordmark)
        tagline   = findViewById(R.id.splashTagline)

        // Resolve auth state immediately (synchronous cached read — no network).
        val destination = resolveDestination()

        // Kick off the animation sequence, then navigate after TOTAL_SPLASH_MS.
        playEntranceAnimation()

        logo.postDelayed({
            cancelRepeatingAnimators()
            startActivity(destination)
            finish()
            // Smooth cross-fade into the next screen
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, TOTAL_SPLASH_MS)
    }

    // ── Animation ────────────────────────────────────────────────────────

    private fun playEntranceAnimation() {

        // ── PHASE 1: Scale-in with overshoot (0–550 ms) ─────────────────
        val scaleXIn = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.3f, 1.08f).apply {
            duration = 550
            interpolator = OvershootInterpolator(1.5f)
        }
        val scaleYIn = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.3f, 1.08f).apply {
            duration = 550
            interpolator = OvershootInterpolator(1.5f)
        }
        val fadeIn = ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f).apply {
            duration = 380
            interpolator = DecelerateInterpolator()
        }

        // ── PHASE 2: Settle overshoot (550–720 ms) ───────────────────────
        val settleX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 1.08f, 1.0f).apply {
            duration = 170
            startDelay = 550
            interpolator = DecelerateInterpolator()
        }
        val settleY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 1.08f, 1.0f).apply {
            duration = 170
            startDelay = 550
            interpolator = DecelerateInterpolator()
        }

        // ── Wordmark + tagline fade in (600 ms, staggered) ───────────────
        val wordmarkFade = ObjectAnimator.ofFloat(wordmark, View.ALPHA, 0f, 1f).apply {
            duration = 300
            startDelay = 600
            interpolator = DecelerateInterpolator()
        }
        val taglineFade = ObjectAnimator.ofFloat(tagline, View.ALPHA, 0f, 0.65f).apply {
            duration = 300
            startDelay = 680
            interpolator = DecelerateInterpolator()
        }

        // Run entrance phases together
        AnimatorSet().apply {
            playTogether(scaleXIn, scaleYIn, fadeIn, settleX, settleY, wordmarkFade, taglineFade)
            start()
        }

        // ── PHASE 3: Ring pulse — starts at 800 ms, repeats ──────────────
        logo.postDelayed({ startRingPulse() }, 800)

        // ── PHASE 4: Logo breathing pulse — starts at 900 ms, repeats ────
        logo.postDelayed({ startLogoBreath() }, 900)
    }

    /**
     * Expanding ring behind the logo — GPS "signal broadcast" effect.
     * Scales 0.85→1.55, fades 0.5→0, 1000 ms period, repeats.
     */
    private fun startRingPulse() {
        val ringScaleX = ObjectAnimator.ofFloat(pulseRing, View.SCALE_X, 0.85f, 1.55f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = DecelerateInterpolator()
        }
        val ringScaleY = ObjectAnimator.ofFloat(pulseRing, View.SCALE_Y, 0.85f, 1.55f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = DecelerateInterpolator()
        }
        val ringAlpha = ObjectAnimator.ofFloat(pulseRing, View.ALPHA, 0.5f, 0f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = DecelerateInterpolator()
        }
        ringPulseAnimator = AnimatorSet().apply {
            playTogether(ringScaleX, ringScaleY, ringAlpha)
            start()
        }
    }

    /**
     * Gentle logo breathing — 1.0→1.06→1.0 over 750 ms, repeats.
     * Subtle enough to feel alive, not distracting.
     */
    private fun startLogoBreath() {
        val breathX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 1.0f, 1.06f, 1.0f).apply {
            duration = 750
            repeatCount = ObjectAnimator.INFINITE
            interpolator = DecelerateInterpolator()
        }
        val breathY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 1.0f, 1.06f, 1.0f).apply {
            duration = 750
            repeatCount = ObjectAnimator.INFINITE
            interpolator = DecelerateInterpolator()
        }
        logoBreathAnimator = AnimatorSet().apply {
            playTogether(breathX, breathY)
            start()
        }
    }

    // ── Auth routing ─────────────────────────────────────────────────────

    /**
     * Resolves which screen to open. FirebaseAuth.currentUser is a
     * synchronous in-memory read — no network call happens here.
     *
     *   ┌──────────────────────────────┬──────────────────────────┐
     *   │ Auth state                   │ Destination              │
     *   ├──────────────────────────────┼──────────────────────────┤
     *   │ Logged in, email linked      │ MainActivity (→ Home)    │
     *   │ Logged in, no email linked   │ EmailInputActivity       │
     *   │ Not logged in                │ WelcomeActivity          │
     *   └──────────────────────────────┴──────────────────────────┘
     */
    private fun resolveDestination(): Intent {
        val user = FirebaseAuth.getInstance().currentUser
        return when {
            user == null -> {
                Intent(this, WelcomeActivity::class.java)
            }
            user.email.isNullOrBlank() -> {
                Intent(this, EmailInputActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            }
            else -> {
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            }
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        // Cancel repeating animators to prevent leaks if the activity is
        // destroyed early (e.g. user presses back during splash).
        cancelRepeatingAnimators()
        logo.removeCallbacks(null)
    }

    private fun cancelRepeatingAnimators() {
        logoBreathAnimator?.cancel()
        ringPulseAnimator?.cancel()
    }
}