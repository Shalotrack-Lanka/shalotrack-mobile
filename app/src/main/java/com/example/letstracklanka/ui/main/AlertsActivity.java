package com.example.letstracklanka.ui.main;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.ui.vehicles.VehiclesActivity;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class AlertsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alerts);

        // Load map in background
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapAlerts);
        if (mapFragment != null) {
            mapFragment.getMapAsync(googleMap -> { });
        }

        // --- Tab Switching Logic (Alerts vs Promotions) ---
        MaterialButton btnTabAlerts = findViewById(R.id.btnTabAlerts);
        MaterialButton btnTabPromotions = findViewById(R.id.btnTabPromotions);
        MaterialCardView btnSearchAlerts = findViewById(R.id.btnSearchAlerts);

        btnSearchAlerts.setOnClickListener(v -> Toast.makeText(this, "Search clicked", Toast.LENGTH_SHORT).show());

        btnTabAlerts.setOnClickListener(v -> {
            // Set Alerts button to Blue, white text
            btnTabAlerts.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1877F2")));
            btnTabAlerts.setTextColor(Color.WHITE);
            // Set Promotions button to Transparent, black text
            btnTabPromotions.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            btnTabPromotions.setTextColor(Color.BLACK);

            Toast.makeText(this, "Showing Alerts", Toast.LENGTH_SHORT).show();
        });

        btnTabPromotions.setOnClickListener(v -> {
            // Set Promotions button to Blue, white text
            btnTabPromotions.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1877F2")));
            btnTabPromotions.setTextColor(Color.WHITE);
            // Set Alerts button to Transparent, black text
            btnTabAlerts.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            btnTabAlerts.setTextColor(Color.BLACK);

            Toast.makeText(this, "Showing Promotions", Toast.LENGTH_SHORT).show();
        });


        // --- Bottom Navigation Setup ---
        LinearLayout navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                Intent intent = new Intent(AlertsActivity.this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        LinearLayout navVehicles = findViewById(R.id.nav_vehicles);
        if (navVehicles != null) {
            navVehicles.setOnClickListener(v -> {
                Intent intent = new Intent(AlertsActivity.this, VehiclesActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        LinearLayout navTags = findViewById(R.id.nav_tags);
        if (navTags != null) {
            navTags.setOnClickListener(v -> {
                Intent intent = new Intent(AlertsActivity.this, TagsActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        LinearLayout navCircles = findViewById(R.id.nav_circles);
        if (navCircles != null) {
            navCircles.setOnClickListener(v -> {
                Intent intent = new Intent(AlertsActivity.this, CirclesActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }
    }
}