package com.example.letstracklanka.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.letstracklanka.R;
import com.example.letstracklanka.ui.vehicles.VehiclesActivity;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class CirclesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_circles);

        // Map එක ලෝඩ් කිරීම
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapCircles);
        if (mapFragment != null) {
            mapFragment.getMapAsync(googleMap -> { });
        }

        // Button Clicks
        MaterialButton btnCreateCircle = findViewById(R.id.btnCreateCircle);
        MaterialButton btnWatchHelp = findViewById(R.id.btnWatchHelp);
        FloatingActionButton fabAddCircle = findViewById(R.id.fabAddCircle);

        btnCreateCircle.setOnClickListener(v -> Toast.makeText(this, "Create Circle clicked", Toast.LENGTH_SHORT).show());
        btnWatchHelp.setOnClickListener(v -> Toast.makeText(this, "Watch Help Videos clicked", Toast.LENGTH_SHORT).show());
        fabAddCircle.setOnClickListener(v -> Toast.makeText(this, "Add Circle (+)", Toast.LENGTH_SHORT).show());

        // --- Bottom Navigation Bar Setup ---

        LinearLayout navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                Intent intent = new Intent(CirclesActivity.this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        LinearLayout navVehicles = findViewById(R.id.nav_vehicles);
        if (navVehicles != null) {
            navVehicles.setOnClickListener(v -> {
                Intent intent = new Intent(CirclesActivity.this, VehiclesActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        LinearLayout navTags = findViewById(R.id.nav_tags);
        if (navTags != null) {
            navTags.setOnClickListener(v -> {
                Intent intent = new Intent(CirclesActivity.this, TagsActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }
    }
}