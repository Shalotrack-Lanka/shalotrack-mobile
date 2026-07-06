package com.example.letstracklanka;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

// අලුතින් එකතු කළ Firebase Imports
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

public class WelcomeActivity extends AppCompatActivity {

    private ImageView imgBackground;
    private TextView txtDescription;
    private TextView txtSubDescription;
    private LinearLayout layoutDots;
    private View[] dots;

    private int currentIndex = 0;
    private Handler sliderHandler = new Handler(Looper.getMainLooper());

    private final String[] descriptions = {
            "Track your family in real-time",
            "Your privacy is our priority,\nno data selling",
            "Locate pets, vehicles\nand valuables",
            "Tag your items,\nfind them instantly",
            "Say it. See it. Track it.\nVoiceTrack by ShaloTrack"
    };

    private final String[] subDescriptions = {
            "Stay connected, stay safe",
            "We never sell your data",
            "Everything in one place",
            "Smart tags, instant results",
            "Hands-free tracking made easy"
    };

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
        setContentView(R.layout.activity_welcome);

        // --- Firebase සහ App Check ආරම්භ කිරීම (මෙතනයි අලුතින් එකතු වුණේ) ---
        FirebaseApp.initializeApp(this);
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
        );
        // -------------------------------------------------------------

        imgBackground   = findViewById(R.id.imgBackground);
        txtDescription  = findViewById(R.id.txtDescription);
        txtSubDescription = findViewById(R.id.txtSubDescription);
        layoutDots      = findViewById(R.id.layoutDots);

        setupDots();
        updateUI();

        sliderHandler.postDelayed(sliderRunnable, 3000);

        MaterialButton btnGetStarted = findViewById(R.id.btnGetStarted);
        btnGetStarted.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, SignUpActivity.class);
            startActivity(intent);
        });

        TextView txtSignIn = findViewById(R.id.txtSignIn);
        txtSignIn.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, LoginActivity.class);
            startActivity(intent);
        });
    }

    private void setupDots() {
        dots = new View[descriptions.length];
        int activeDpWidth  = dpToPx(24);
        int inactiveDpWidth = dpToPx(7);
        int height = dpToPx(7);

        for (int i = 0; i < dots.length; i++) {
            dots[i] = new View(this);
            dots[i].setBackgroundResource(R.drawable.dot_bg);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    i == 0 ? activeDpWidth : inactiveDpWidth, height);
            params.setMargins(dpToPx(4), 0, dpToPx(4), 0);
            dots[i].setLayoutParams(params);

            layoutDots.addView(dots[i]);
        }
    }

    private void updateUI() {
        imgBackground.animate().alpha(0.5f).setDuration(400).withEndAction(() -> {
            imgBackground.setImageResource(images[currentIndex]);
            imgBackground.animate().alpha(1f).setDuration(400).start();
        }).start();

        txtDescription.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            txtDescription.setText(descriptions[currentIndex]);
            txtDescription.animate().alpha(1f).setDuration(300).start();
        }).start();

        txtSubDescription.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            txtSubDescription.setText(subDescriptions[currentIndex]);
            txtSubDescription.animate().alpha(1f).setDuration(300).start();
        }).start();

        for (int i = 0; i < dots.length; i++) {
            LinearLayout.LayoutParams params =
                    (LinearLayout.LayoutParams) dots[i].getLayoutParams();
            if (i == currentIndex) {
                params.width = dpToPx(24);
                dots[i].setAlpha(1.0f);
            } else {
                params.width = dpToPx(7);
                dots[i].setAlpha(0.35f);
            }
            dots[i].setLayoutParams(params);
        }
    }

    private Runnable sliderRunnable = new Runnable() {
        @Override
        public void run() {
            currentIndex = (currentIndex + 1) % descriptions.length;
            updateUI();
            sliderHandler.postDelayed(this, 3000);
        }
    };

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sliderHandler.removeCallbacks(sliderRunnable);
    }
}