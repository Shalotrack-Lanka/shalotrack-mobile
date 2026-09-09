package com.example.letstracklanka.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.letstracklanka.ui.auth.EmailInputActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    // Set the minimum duration in milliseconds you want the splash screen visible
    private val minSplashDuration = 4000L
    private val startTime = SystemClock.elapsedRealtime()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install System Splash Screen before calling super.onCreate
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep the splash screen visible until 2 seconds have passed
        splashScreen.setKeepOnScreenCondition {
            val elapsedTime = SystemClock.elapsedRealtime() - startTime
            elapsedTime < minSplashDuration
        }

        checkAuthAndNavigate()
    }

    private fun checkAuthAndNavigate() {
        val currentUser = FirebaseAuth.getInstance().currentUser

        val intent = when {
            currentUser != null -> {
                if (currentUser.email != null) {
                    // SESSION OK: Go to MainActivity which leads to Home
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                } else {
                    // PARTIAL SESSION: Go to Details screen to finish linking email
                    Intent(this, EmailInputActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                }
            }
            else -> {
                // NO SESSION: Go to Welcome screen
                Intent(this, WelcomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            }
        }

        // Wait for the minimum duration before firing the intent
        val elapsedTime = SystemClock.elapsedRealtime() - startTime
        val remainingTime = (minSplashDuration - elapsedTime).coerceAtLeast(0L)

        window.decorView.postDelayed({
            startActivity(intent)
            finish()
        }, remainingTime)
    }
}