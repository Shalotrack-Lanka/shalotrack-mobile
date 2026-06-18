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

public class WelcomeActivity extends AppCompatActivity {

    private ImageView imgBackground;
    private TextView txtDescription;
    private LinearLayout layoutDots;
    private ImageView[] dots;

    private int currentIndex = 0;
    private Handler sliderHandler = new Handler(Looper.getMainLooper());

    private final String[] descriptions = {
            "Track your family in real-time",
            "Your privacy is our priority,\nno data selling",
            "Locate pets, vehicles\nand valuables",
            "Tag your items,\nfind them instantly",
            "Say it. See it. Track it.\nVoiceTrack by ShaloTrack"
    };

    // ඔයා ජෙනරේට් කරගත්තු පින්තූර 5 මෙතනින් තමයි ලෝඩ් වෙන්නේ
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

        imgBackground = findViewById(R.id.imgBackground);
        txtDescription = findViewById(R.id.txtDescription);
        layoutDots = findViewById(R.id.layoutDots);

        setupDots();
        updateUI();

        sliderHandler.postDelayed(sliderRunnable, 3000);

        MaterialButton btnGetStarted = findViewById(R.id.btnGetStarted);
        btnGetStarted.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get Started එබුවම SignUpActivity එකට යනවා
                Intent intent = new Intent(WelcomeActivity.this, SignUpActivity.class);
                startActivity(intent);
            }
        });
    }

    private void setupDots() {
        dots = new ImageView[descriptions.length];
        for (int i = 0; i < dots.length; i++) {
            dots[i] = new ImageView(this);
            dots[i].setImageResource(R.drawable.dot_bg);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(24, 24);
            params.setMargins(12, 0, 12, 0);
            dots[i].setLayoutParams(params);

            layoutDots.addView(dots[i]);
        }
    }

    private void updateUI() {
        imgBackground.animate().alpha(0.5f).setDuration(400).withEndAction(() -> {
            imgBackground.setImageResource(images[currentIndex]);
            imgBackground.animate().alpha(1f).setDuration(400).start();
        }).start();

        txtDescription.animate().alpha(0f).setDuration(400).withEndAction(() -> {
            txtDescription.setText(descriptions[currentIndex]);
            txtDescription.animate().alpha(1f).setDuration(400).start();
        }).start();

        for (int i = 0; i < dots.length; i++) {
            if (i == currentIndex) {
                dots[i].setAlpha(1.0f);
            } else {
                dots[i].setAlpha(0.4f);
            }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sliderHandler.removeCallbacks(sliderRunnable);
    }
}