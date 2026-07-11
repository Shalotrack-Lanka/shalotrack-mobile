package com.example.letstracklanka.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
        View navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                Intent intent = new Intent(CirclesActivity.this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        View navVehicles = findViewById(R.id.nav_vehicles);
        if (navVehicles != null) {
            navVehicles.setOnClickListener(v -> {
                Intent intent = new Intent(CirclesActivity.this, com.example.letstracklanka.ui.vehicles.VehiclesActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        View navTags = findViewById(R.id.nav_tags);
        if (navTags != null) {
            navTags.setOnClickListener(v -> {
                Intent intent = new Intent(CirclesActivity.this, TagsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        View navAlerts = findViewById(R.id.nav_alerts);
        if (navAlerts != null) {
            navAlerts.setOnClickListener(v -> showCallCenterBottomSheet());
        }

        View navMenu = findViewById(R.id.nav_menu);
        if (navMenu != null) {
            navMenu.setOnClickListener(v -> {
                View bs = findViewById(R.id.bottomSheetCircles);
                if (bs != null) {
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(bs).setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                }
            });
        }
    }

    private void showCallCenterBottomSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_call_center, null);
        dialog.setContentView(view);
        androidx.viewpager2.widget.ViewPager2 viewPager = view.findViewById(R.id.viewPagerCallCenter);
        if (viewPager != null) {
            viewPager.setAdapter(new com.example.letstracklanka.ui.vehicles.CallCenterPagerAdapter());
        }
        dialog.show();
    }
}