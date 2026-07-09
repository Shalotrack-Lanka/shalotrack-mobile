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
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageView imgFullLogo = findViewById(R.id.imgFullLogo);
        ImageView imgNeonRing = findViewById(R.id.imgNeonRing);

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

        imgNeonRing.post(() -> { // Changed to imgNeonRing.post to ensure it's measured
            // Calculate center based on the Neon Ring instead of the Full Logo
            float centerX = imgNeonRing.getX() + (imgNeonRing.getWidth() / 2f);
            float centerY = imgNeonRing.getY() + (imgNeonRing.getHeight() / 2f);

            float orbitWidth  = imgNeonRing.getWidth()  / 2f;
            float orbitHeight = imgNeonRing.getHeight() / 2f;

            float itemHalfW = items[0].getWidth()  / 2f;
            float itemHalfH = items[0].getHeight() / 2f;

            // Fade in ring + items
            imgNeonRing.animate().alpha(1f).setDuration(400).start();
            for (ImageView item : items) {
                item.animate().alpha(1f).setDuration(400).start();
            }

            ValueAnimator orbitAnimator = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
            orbitAnimator.setDuration(14000);
            orbitAnimator.setRepeatCount(ValueAnimator.INFINITE);
            orbitAnimator.setInterpolator(new LinearInterpolator());

            orbitAnimator.addUpdateListener(animation -> {
                float currentAngle = (float) animation.getAnimatedValue();

                for (int i = 0; i < items.length; i++) {
                    float angleOffset = (float) (i * (2 * Math.PI / items.length));
                    float finalAngle  = currentAngle + angleOffset;

                    float x = centerX + orbitWidth  * (float) Math.cos(finalAngle) - itemHalfW;
                    float y = centerY + orbitHeight * (float) Math.sin(finalAngle) - itemHalfH;

                    items[i].setX(x);
                    items[i].setY(y);

                    // Depth effect: items at the back (sin < 0) appear smaller + transparent
                    float sinVal = (float) Math.sin(finalAngle);
                    float depthScale = 0.65f + 0.35f * ((sinVal + 1f) / 2f);
                    float depthAlpha = 0.45f + 0.55f * ((sinVal + 1f) / 2f);

                    items[i].setScaleX(depthScale);
                    items[i].setScaleY(depthScale);
                    items[i].setAlpha(depthAlpha);

                    // Elevation: front items render on top
                    items[i].setTranslationZ(sinVal * 8f);
                }
            });

            orbitAnimator.start();

            // තත්පර 4කට පස්සේ HomeActivity එකට යන්න
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                startActivity(intent);
                finish(); // ආපහු Back කරාම Loading තිරයට එන එක නවත්වන්න මේක දානවා
            }, 4000); // 4000 කියන්නේ මිලි තත්පර 4000 (තත්පර 4යි)
        });
    }

}