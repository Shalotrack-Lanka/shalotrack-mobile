package com.example.letstracklanka;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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

        imgFullLogo.post(() -> {
            float centerX = imgFullLogo.getX() + (imgFullLogo.getWidth() / 2f);
            float centerY = imgFullLogo.getY() + (imgFullLogo.getHeight() / 2f);


            float orbitWidth = imgNeonRing.getWidth() / 2f;
            float orbitHeight = imgNeonRing.getHeight() / 2f;

            float itemHalfW = items[0].getWidth() / 2f;
            float itemHalfH = items[0].getHeight() / 2f;


            imgNeonRing.animate().alpha(1f).setDuration(200).start();
            for (ImageView item : items) {
                item.animate().alpha(1f).setDuration(200).start();

                item.setRotation(0f);
            }

            ValueAnimator orbitAnimator = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
            orbitAnimator.setDuration(12000);
            orbitAnimator.setRepeatCount(ValueAnimator.INFINITE);
            orbitAnimator.setInterpolator(new LinearInterpolator());

            orbitAnimator.addUpdateListener(animation -> {
                float currentAngle = (float) animation.getAnimatedValue();

                for (int i = 0; i < items.length; i++) {
                    float angleOffset = (float) (i * (2 * Math.PI / items.length));
                    float finalAngle = currentAngle + angleOffset;


                    float x = centerX + orbitWidth * (float) Math.cos(finalAngle) - itemHalfW;
                    float y = centerY + orbitHeight * (float) Math.sin(finalAngle) - itemHalfH;

                    items[i].setX(x);
                    items[i].setY(y);


                }
            });

            orbitAnimator.start();
        });
    }
}