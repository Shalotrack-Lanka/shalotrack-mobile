package com.example.letstracklanka;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;

public class ProcessingActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        // තත්පර 4ක් ඉඳලා කෙලින්ම HomeActivity එකට යනවා
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(ProcessingActivity.this, HomeActivity.class); // MainActivity වෙනුවට HomeActivity දැම්මා
            startActivity(intent);
            finish();
        }, 4000);
    }
}