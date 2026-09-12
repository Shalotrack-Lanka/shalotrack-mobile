package com.example.letstracklanka.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.letstracklanka.R;
import com.example.letstracklanka.widget.SignalRingView;
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });


        SignalRingView signalRing = findViewById(R.id.signalRing);
        View sweepArm = findViewById(R.id.sweepArm);
        View dotA = findViewById(R.id.dotA);
        View dotB = findViewById(R.id.dotB);
        View dotC = findViewById(R.id.dotC);
        View mainLogo = findViewById(R.id.mainLogo);
        TextView tvLoadingMessage = findViewById(R.id.tvLoadingMessage);

        // අලුත් Splash Animation එක Play කරනවා
        SplashAnimations.play(
                signalRing,
                sweepArm,
                dotA, dotB, dotC,
                mainLogo,
                tvLoadingMessage,
                this::goToHome
        );
    }

    private void goToHome() {
        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}