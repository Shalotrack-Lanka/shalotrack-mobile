package com.example.letstracklanka;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private BottomSheetBehavior<NestedScrollView> bottomSheetBehavior;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // 1. Setup the Map
        // We find the map fragment and wait for it to load in the background
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // 2. Setup the Bottom Sheet Behavior
        // This controls how the menu slides up and down
        NestedScrollView bottomSheet = findViewById(R.id.bottomSheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

        // State collapsed means it is halfway down when the app opens
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        // 3. Find Action Buttons and set Click Listeners
        MaterialButton btnSendLocation = findViewById(R.id.btnSendLocation);
        MaterialButton btnSOS = findViewById(R.id.btnSOS);
        FloatingActionButton fabRefresh = findViewById(R.id.fabRefresh);

        // When you click 'Send Location' button
        btnSendLocation.setOnClickListener(v -> {
            Toast.makeText(this, "Send Location Clicked", Toast.LENGTH_SHORT).show();
            // TODO: Write code here to share the location
        });

        // When you click 'SOS' button
        btnSOS.setOnClickListener(v -> {
            Toast.makeText(this, "SOS Clicked - Emergency Alert!", Toast.LENGTH_SHORT).show();
            // TODO: Write code here to send emergency signals
        });

        // When you click 'Refresh' button
        fabRefresh.setOnClickListener(v -> {
            Toast.makeText(this, "Refreshing Location...", Toast.LENGTH_SHORT).show();
            // TODO: Write code to get new location from the database
        });

        // 4. Find Bottom Sheet Menu Items
        findViewById(R.id.btnAddPerson).setOnClickListener(v -> {
            Toast.makeText(this, "Add Person Menu Clicked", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnAddVehicle).setOnClickListener(v -> {
            Toast.makeText(this, "Add Vehicle Menu Clicked", Toast.LENGTH_SHORT).show();
        });
    }

    // This function runs automatically when the map is fully loaded
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Set Sri Lanka center location (Kandy/Matale area)
        LatLng sriLanka = new LatLng(7.8731, 80.7718);

        // Add a pin on the map
        mMap.addMarker(new MarkerOptions().position(sriLanka).title("LT Demo Device"));

        // Zoom the camera to show Sri Lanka
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(sriLanka, 8f));
    }
}