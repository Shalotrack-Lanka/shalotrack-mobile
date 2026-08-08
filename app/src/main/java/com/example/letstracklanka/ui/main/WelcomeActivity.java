package com.example.letstracklanka.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;


import com.example.letstracklanka.R;
import com.example.letstracklanka.ui.auth.LoginActivity;
import com.example.letstracklanka.ui.auth.SignUpActivity;
import com.google.android.material.button.MaterialButton;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;


public class WelcomeActivity extends AppCompatActivity {

    // UI elements on the screen
    private ImageView imgBackground;
    private TextView txtDescription;
    private TextView txtSubDescription;
    private LinearLayout layoutDots;
    private View[] dots;

    private int currentIndex = 0;
    private final Handler sliderHandler = new Handler(Looper.getMainLooper());

    // Main titles for the slider
    private final String[] descriptions = {
            "Track your family in real-time",
            "Your privacy is our priority,\nno data selling",
            "Locate pets, vehicles\nand valuables",
            "Tag your items,\nfind them instantly",
            "Say it. See it. Track it.\nVoiceTrack by ShaloTrack"
    };

    // Small sub-titles for the slider
    private final String[] subDescriptions = {
            "Stay connected, stay safe",
            "We never sell your data",
            "Everything in one place",
            "Smart tags, instant results",
            "Hands-free tracking made easy"
    };

    // Background images for the slider
    private final int[] images = {
            R.drawable.bg_1,
            R.drawable.bg_2,
            R.drawable.bg_3,
            R.drawable.bg_4,
            R.drawable.bg_5
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- 1. FIREBASE SESSION CHECK ---
        // Check if the user is already logged in
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            // If logged in, skip this screen and go directly to the Main Screen
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // --- 2. LOAD WELCOME UI ---
        // If not logged in, show the welcome screen
        setContentView(R.layout.activity_welcome);
        FirebaseApp.initializeApp(this);

        // Connect the UI elements to the code
        imgBackground   = findViewById(R.id.imgBackground);
        txtDescription  = findViewById(R.id.txtDescription);
        txtSubDescription = findViewById(R.id.txtSubDescription);
        layoutDots      = findViewById(R.id.layoutDots);

        // Prepare the dots and show the first slide
        setupDots();
        updateUI();

        // Start the automatic slider to change slides every 3 seconds
        sliderHandler.postDelayed(sliderRunnable, 3000);

        // Action when the user clicks the "Get Started" button
        MaterialButton btnGetStarted = findViewById(R.id.btnGetStarted);
        btnGetStarted.setOnClickListener(v -> {
            // Go to the Sign Up page
            Intent intent = new Intent(WelcomeActivity.this, SignUpActivity.class);
            startActivity(intent);
        });

        // Action when the user clicks the "Sign In" text
        TextView txtSignIn = findViewById(R.id.txtSignIn);
        txtSignIn.setOnClickListener(v -> {
            // Go to the Login page
            Intent intent = new Intent(WelcomeActivity.this, LoginActivity.class);
            startActivity(intent);
        });
    }

    // This function creates the small dots at the bottom of the slider
    private void setupDots() {
        dots = new View[descriptions.length];
        int activeDpWidth  = dpToPx(24);
        int inactiveDpWidth = dpToPx(7);
        int height = dpToPx(7);

        // Loop to create each dot
        for (int i = 0; i < dots.length; i++) {
            dots[i] = new View(this);
            dots[i].setBackgroundResource(R.drawable.dot_bg);

            // Make the first dot long and the rest short
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    i == 0 ? activeDpWidth : inactiveDpWidth, height);
            params.setMargins(dpToPx(4), 0, dpToPx(4), 0);
            dots[i].setLayoutParams(params);

            layoutDots.addView(dots[i]);
        }
    }

    // This function changes the image, text, and dots with a fade animation
    private void updateUI() {
        // Fade out the old image, change it, and fade in the new image
        imgBackground.animate().alpha(0.5f).setDuration(400).withEndAction(() -> {
            imgBackground.setImageResource(images[currentIndex]);
            imgBackground.animate().alpha(1f).setDuration(400).start();
        }).start();

        // Change the main title with animation
        txtDescription.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            txtDescription.setText(descriptions[currentIndex]);
            txtDescription.animate().alpha(1f).setDuration(300).start();
        }).start();

        // Change the sub-title with animation
        txtSubDescription.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            txtSubDescription.setText(subDescriptions[currentIndex]);
            txtSubDescription.animate().alpha(1f).setDuration(300).start();
        }).start();

        // Update the size and color of the dots based on the current slide
        for (int i = 0; i < dots.length; i++) {
            LinearLayout.LayoutParams params =
                    (LinearLayout.LayoutParams) dots[i].getLayoutParams();
            if (i == currentIndex) {
                // Active dot settings
                params.width = dpToPx(24);
                dots[i].setAlpha(1.0f);
            } else {
                // Inactive dot settings
                params.width = dpToPx(7);
                dots[i].setAlpha(0.35f);
            }
            dots[i].setLayoutParams(params);
        }
    }

    // This is the timer logic that automatically moves to the next slide
    private final Runnable sliderRunnable = new Runnable() {
        @Override
        public void run() {
            // Move to the next slide, or go back to the first slide if it's the last one
            currentIndex = (currentIndex + 1) % descriptions.length;
            updateUI();
            sliderHandler.postDelayed(this, 3000);
        }
    };

    // Helper function to convert display size so dots look good on all phone screens
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop the auto-slider timer when the app is closed to save phone battery
        sliderHandler.removeCallbacks(sliderRunnable);
    }
}