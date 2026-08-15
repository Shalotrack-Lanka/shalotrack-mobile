package com.example.letstracklanka.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.example.letstracklanka.R;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class CirclesActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_circles);

        drawerLayout = findViewById(R.id.drawerLayout);
        DrawerMenuHelper.wireDrawer(this, drawerLayout,
                findViewById(R.id.tvDrawerName), findViewById(R.id.tvDrawerPhone), findViewById(R.id.tvDrawerEmail));

        // Load the Google Map in the background
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapCircles);
        if (mapFragment != null) {
            mapFragment.getMapAsync(googleMap -> {
                // No action needed when map loads, it will just show in the background
            });
        }

        // --- Setup Buttons and Clicks ---

        // Create Circle and Help buttons
        MaterialButton btnCreateCircle = findViewById(R.id.btnCreateCircle);
        MaterialButton btnWatchHelp = findViewById(R.id.btnWatchHelp);
        FloatingActionButton fabAddCircle = findViewById(R.id.fabAddCircle);

        btnCreateCircle.setOnClickListener(v -> Toast.makeText(this, "Create Circle clicked", Toast.LENGTH_SHORT).show());
        btnWatchHelp.setOnClickListener(v -> Toast.makeText(this, "Watch Help Videos clicked", Toast.LENGTH_SHORT).show());
        fabAddCircle.setOnClickListener(v -> Toast.makeText(this, "Add Circle (+)", Toast.LENGTH_SHORT).show());

        // --- Setup Bottom Navigation Bar ---

        // Go to Home screen
        View navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                Intent intent = new Intent(CirclesActivity.this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // Go to Vehicles screen
        View navVehicles = findViewById(R.id.nav_vehicles);
        if (navVehicles != null) {
            navVehicles.setOnClickListener(v -> {
                Intent intent = new Intent(CirclesActivity.this, com.example.letstracklanka.ui.vehicles.VehiclesActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // Go to Tags screen
        View navTags = findViewById(R.id.nav_tags);
        if (navTags != null) {
            navTags.setOnClickListener(v -> {
                Intent intent = new Intent(CirclesActivity.this, TagsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // Go to Alerts screen (Updated to navigate to the new AlertsActivity)
        View navAlerts = findViewById(R.id.nav_alerts);
        if (navAlerts != null) {
            navAlerts.setOnClickListener(v -> {
                Intent intent = new Intent(CirclesActivity.this, AlertsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // FIX: previously expanded bottomSheetCircles -- the "no circles
        // added" empty-state box, which had nothing to do with a menu at
        // all. Found during a systematic dead-end audit. Now opens this
        // screen's own real drawer.
        View navMenu = findViewById(R.id.nav_menu);
        if (navMenu != null) {
            navMenu.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
            });
        }
    }
}