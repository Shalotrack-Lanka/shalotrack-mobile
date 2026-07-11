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

public class TagsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tags);

        // --- Load the Map in the background ---
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(googleMap -> {
                // No action needed when map loads, it will just show in the background
            });
        }

        // --- Button Clicks ---
        MaterialButton btnAddTag = findViewById(R.id.btnAddTag);
        btnAddTag.setOnClickListener(v -> {
            Toast.makeText(this, "Add TAG clicked", Toast.LENGTH_SHORT).show();
        });

        MaterialButton btnTouchTag = findViewById(R.id.btnTouchTag);
        MaterialButton btnStickerTag = findViewById(R.id.btnStickerTag);
        MaterialButton btnBluetoothTag = findViewById(R.id.btnBluetoothTag);
        MaterialButton btnParkingTag = findViewById(R.id.btnParkingTag);

        btnTouchTag.setOnClickListener(v -> Toast.makeText(this, "Playing Touch TAG video...", Toast.LENGTH_SHORT).show());
        btnStickerTag.setOnClickListener(v -> Toast.makeText(this, "Playing Sticker TAG video...", Toast.LENGTH_SHORT).show());
        btnBluetoothTag.setOnClickListener(v -> Toast.makeText(this, "Playing Bluetooth TAG video...", Toast.LENGTH_SHORT).show());
        btnParkingTag.setOnClickListener(v -> Toast.makeText(this, "Playing Parking TAG video...", Toast.LENGTH_SHORT).show());

        FloatingActionButton fabScan = findViewById(R.id.fabScan);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        fabScan.setOnClickListener(v -> Toast.makeText(this, "Open QR Scanner", Toast.LENGTH_SHORT).show());
        fabAdd.setOnClickListener(v -> Toast.makeText(this, "Add new item", Toast.LENGTH_SHORT).show());

        // --- Bottom Navigation Bar Setup ---
        View navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                Intent intent = new Intent(TagsActivity.this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        View navVehicles = findViewById(R.id.nav_vehicles);
        if (navVehicles != null) {
            navVehicles.setOnClickListener(v -> {
                Intent intent = new Intent(TagsActivity.this, com.example.letstracklanka.ui.vehicles.VehiclesActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        View navCircles = findViewById(R.id.nav_circles);
        if (navCircles != null) {
            navCircles.setOnClickListener(v -> {
                Intent intent = new Intent(TagsActivity.this, CirclesActivity.class);
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
                View bs = findViewById(R.id.bottomSheetTags);
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