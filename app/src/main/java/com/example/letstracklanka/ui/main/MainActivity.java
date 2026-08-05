package com.example.letstracklanka.ui.main;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.letstracklanka.R;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make the app take up the full screen
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Adjust the layout so it doesn't hide behind the phone's top or bottom status bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Find the main logo and the glowing ring from the screen design
        ImageView imgFullLogo = findViewById(R.id.imgFullLogo);
        ImageView imgNeonRing = findViewById(R.id.imgNeonRing);

        // Find all the small floating icons that will orbit around the logo
        ImageView[] items = new ImageView[]{
                findViewById(R.id.imgItem1),
                findViewById(R.id.imgItem2),
                findViewById(R.id.imgItem3),
                findViewById(R.id.imgItem4),
                findViewById(R.id.imgItem5),
                findViewById(R.id.imgItem6),
                findViewById(R.id.imgItem7),
                findViewById(R.id.imgItem8),
                findViewById(R.id.imgItem9)
        };

        // Wait until the glowing ring is fully drawn on the screen before doing math
        imgNeonRing.post(() -> {

            // Find the exact center point of the glowing ring
            float centerX = imgNeonRing.getX() + (imgNeonRing.getWidth() / 2f);
            float centerY = imgNeonRing.getY() + (imgNeonRing.getHeight() / 2f);

            // Calculate how wide and tall the orbit path should be
            float orbitWidth  = imgNeonRing.getWidth()  / 2f;
            float orbitHeight = imgNeonRing.getHeight() / 2f;

            // Get the center point of the small floating icons
            float itemHalfW = items[0].getWidth()  / 2f;
            float itemHalfH = items[0].getHeight() / 2f;

            // Slowly show the ring and all the small icons
            imgNeonRing.animate().alpha(1f).setDuration(400).start();
            for (ImageView item : items) {
                item.animate().alpha(1f).setDuration(400).start();
            }

            // Create a looping animation that spins in a full circle
            ValueAnimator orbitAnimator = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
            orbitAnimator.setDuration(14000); // One full spin takes 14 seconds
            orbitAnimator.setRepeatCount(ValueAnimator.INFINITE); // Keep spinning forever
            orbitAnimator.setInterpolator(new LinearInterpolator()); // Spin at a steady, smooth speed

            // This runs continuously to move the icons step-by-step
            orbitAnimator.addUpdateListener(animation -> {
                float currentAngle = (float) animation.getAnimatedValue();

                for (int i = 0; i < items.length; i++) {
                    // Space the icons equally around the circle
                    float angleOffset = (float) (i * (2 * Math.PI / items.length));
                    float finalAngle  = currentAngle + angleOffset;

                    // Calculate the new X and Y position for this specific icon
                    float x = centerX + orbitWidth  * (float) Math.cos(finalAngle) - itemHalfW;
                    float y = centerY + orbitHeight * (float) Math.sin(finalAngle) - itemHalfH;

                    items[i].setX(x);
                    items[i].setY(y);

                    // Create a 3D effect
                    float sinVal = (float) Math.sin(finalAngle);
                    float depthScale = 0.65f + 0.35f * ((sinVal + 1f) / 2f);
                    float depthAlpha = 0.45f + 0.55f * ((sinVal + 1f) / 2f);

                    items[i].setScaleX(depthScale);
                    items[i].setScaleY(depthScale);
                    items[i].setAlpha(depthAlpha);

                    // Make sure icons at the front overlap the ones at the back
                    items[i].setTranslationZ(sinVal * 8f);
                }
            });

            // Start the spinning animation
            orbitAnimator.start();

            // Wait for 4 seconds and then move to the Home screen
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                startActivity(intent);

                // Close this animation screen so the user can't come back to it using the back button
                finish();
            }, 4000);
        });
    }

}