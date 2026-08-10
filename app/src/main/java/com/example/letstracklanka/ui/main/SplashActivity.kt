package com.example.letstracklanka.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.letstracklanka.R
import com.example.letstracklanka.ui.auth.EmailInputActivity
import com.example.letstracklanka.ui.auth.ProcessingActivity
import com.example.letstracklanka.utils.SessionManager
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val sessionManager = SessionManager(this)

        // Handle App Link for Email Verification
        val intentData = intent.data
        if (intentData != null && intentData.host == "auth.shalotrack.com") {
            val oobCode = intentData.getQueryParameter("oobCode")
            if (oobCode != null) {
                FirebaseAuth.getInstance().applyActionCode(oobCode)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // Reload user to update verification status
                            FirebaseAuth.getInstance().currentUser?.reload()?.addOnCompleteListener {
                                navigateBasedOnState(sessionManager)
                            }
                        } else {
                            navigateBasedOnState(sessionManager)
                        }
                    }
                return
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            navigateBasedOnState(sessionManager)
        }, 2000)
    }

    private fun navigateBasedOnState(sessionManager: SessionManager) {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            if (sessionManager.isLoggedIn) {
                // SESSION OK: Go to Animation (MainActivity) which leads to Home
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            } else {
                // FIREBASE AUTH OK BUT NO LOCAL SESSION: 
                // Might need to re-verify profile/email
                if (currentUser.email != null) {
                    // We have an email, maybe they just need to re-sync or we can try to go to processing
                    // but usually if isLoggedIn is false, they should go through the flow.
                    // For safety, if they have email, we could try to send them to ProcessingActivity
                    // to see if we can get their customerId.
                    val intent = Intent(this, ProcessingActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                } else {
                    // PARTIAL SESSION: Go to Details screen to finish linking email
                    val intent = Intent(this, EmailInputActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
            }
        } else {
            // NO SESSION: Go to Welcome screen
            val intent = Intent(this, WelcomeActivity::class.java)
            startActivity(intent)
        }
        finish()
    }
}
