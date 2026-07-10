package com.example.letstracklanka.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.letstracklanka.R;
import com.example.letstracklanka.ui.vehicles.VehiclesActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class TagsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Link this Java file to the XML layout we just created
        setContentView(R.layout.activity_tags);

        // Find the main "Add TAG" button and add a click event
        MaterialButton btnAddTag = findViewById(R.id.btnAddTag);
        btnAddTag.setOnClickListener(v -> {
            Toast.makeText(this, "Add TAG clicked", Toast.LENGTH_SHORT).show();
            // You can add Intent here to go to the add tag screen later
        });

        // Find the Help Video buttons
        MaterialButton btnTouchTag = findViewById(R.id.btnTouchTag);
        MaterialButton btnStickerTag = findViewById(R.id.btnStickerTag);
        MaterialButton btnBluetoothTag = findViewById(R.id.btnBluetoothTag);
        MaterialButton btnParkingTag = findViewById(R.id.btnParkingTag);

        // Add click events for help videos
        btnTouchTag.setOnClickListener(v -> Toast.makeText(this, "Playing Touch TAG video...", Toast.LENGTH_SHORT).show());
        btnStickerTag.setOnClickListener(v -> Toast.makeText(this, "Playing Sticker TAG video...", Toast.LENGTH_SHORT).show());
        btnBluetoothTag.setOnClickListener(v -> Toast.makeText(this, "Playing Bluetooth TAG video...", Toast.LENGTH_SHORT).show());
        btnParkingTag.setOnClickListener(v -> Toast.makeText(this, "Playing Parking TAG video...", Toast.LENGTH_SHORT).show());

        // Find and set click events for the Floating Action Buttons (FABs)
        FloatingActionButton fabScan = findViewById(R.id.fabScan);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        fabScan.setOnClickListener(v -> Toast.makeText(this, "Open QR Scanner", Toast.LENGTH_SHORT).show());
        fabAdd.setOnClickListener(v -> Toast.makeText(this, "Add new item", Toast.LENGTH_SHORT).show());

        // --- Bottom Navigation Bar Setup ---

        // Home Button
        LinearLayout navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                Intent intent = new Intent(TagsActivity.this, HomeActivity.class);
                // පරණ Home එකක් ඇරලා තියෙනවා නම් ඒකටම යනවා (අලුතින් ඕපන් කරන්නේ නෑ)
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0); // ඇනිමේෂන් නැතිව සුමටව මාරු වෙන්න
            });
        }

        // Vehicles Button
        LinearLayout navVehicles = findViewById(R.id.nav_vehicles);
        if (navVehicles != null) {
            navVehicles.setOnClickListener(v -> {
                Intent intent = new Intent(TagsActivity.this, VehiclesActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0); // ඇනිමේෂන් නැතිව සුමටව මාරු වෙන්න
            });
        }
    }
}