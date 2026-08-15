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

public class TagsActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tags);

        drawerLayout = findViewById(R.id.drawerLayout);
        DrawerMenuHelper.wireDrawer(this, drawerLayout,
                findViewById(R.id.tvDrawerName), findViewById(R.id.tvDrawerPhone), findViewById(R.id.tvDrawerEmail));

        // Load the Google Map in the background
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(googleMap -> {
                // No action needed when map loads, it will just show in the background
            });
        }

        // --- Setup Buttons and Clicks ---

        // Add TAG button
        MaterialButton btnAddTag = findViewById(R.id.btnAddTag);
        btnAddTag.setOnClickListener(v -> {
            Toast.makeText(this, "Add TAG clicked", Toast.LENGTH_SHORT).show();
        });

        // Help video buttons
        MaterialButton btnTouchTag = findViewById(R.id.btnTouchTag);
        MaterialButton btnStickerTag = findViewById(R.id.btnStickerTag);
        MaterialButton btnBluetoothTag = findViewById(R.id.btnBluetoothTag);
        MaterialButton btnParkingTag = findViewById(R.id.btnParkingTag);

        btnTouchTag.setOnClickListener(v -> Toast.makeText(this, "Playing Touch TAG video...", Toast.LENGTH_SHORT).show());
        btnStickerTag.setOnClickListener(v -> Toast.makeText(this, "Playing Sticker TAG video...", Toast.LENGTH_SHORT).show());
        btnBluetoothTag.setOnClickListener(v -> Toast.makeText(this, "Playing Bluetooth TAG video...", Toast.LENGTH_SHORT).show());
        btnParkingTag.setOnClickListener(v -> Toast.makeText(this, "Playing Parking TAG video...", Toast.LENGTH_SHORT).show());

        // Floating Action Buttons (FAB) for scanning and adding
        FloatingActionButton fabScan = findViewById(R.id.fabScan);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        fabScan.setOnClickListener(v -> Toast.makeText(this, "Open QR Scanner", Toast.LENGTH_SHORT).show());
        fabAdd.setOnClickListener(v -> Toast.makeText(this, "Add new item", Toast.LENGTH_SHORT).show());

        // --- Setup Bottom Navigation Bar ---

        // Go to Home screen
        View navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                Intent intent = new Intent(TagsActivity.this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // Go to Vehicles screen
        View navVehicles = findViewById(R.id.nav_vehicles);
        if (navVehicles != null) {
            navVehicles.setOnClickListener(v -> {
                Intent intent = new Intent(TagsActivity.this, com.example.letstracklanka.ui.vehicles.VehiclesActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // Go to Circles screen
        View navCircles = findViewById(R.id.nav_circles);
        if (navCircles != null) {
            navCircles.setOnClickListener(v -> {
                Intent intent = new Intent(TagsActivity.this, CirclesActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // Go to Alerts screen (Updated to go to the new AlertsActivity instead of Call Center popup)
        View navAlerts = findViewById(R.id.nav_alerts);
        if (navAlerts != null) {
            navAlerts.setOnClickListener(v -> {
                Intent intent = new Intent(TagsActivity.this, AlertsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // FIX: previously expanded bottomSheetTags -- the "no tags added"
        // empty-state box, which had nothing to do with a menu at all.
        // Found during a systematic dead-end audit. Now opens this
        // screen's own real drawer.
        View navMenu = findViewById(R.id.nav_menu);
        if (navMenu != null) {
            navMenu.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
            });
        }
    }
}