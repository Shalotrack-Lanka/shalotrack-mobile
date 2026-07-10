package com.example.letstracklanka.ui.main;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.letstracklanka.R;
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

        // TODO: Find your bottom navigation bar items here and set up Intents to navigate between Home, Vehicles, Tags, etc.
    }
}