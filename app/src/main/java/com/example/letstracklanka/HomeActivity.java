package com.example.letstracklanka;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private BottomSheetBehavior<NestedScrollView> bottomSheetBehavior;
    private CardView mapTypeMenu;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Location Service එක Initialize කරනවා
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Map එක Load කරනවා
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Bottom Sheet Setup
        NestedScrollView bottomSheet = findViewById(R.id.bottomSheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        // Map Type Menu එක හොයාගන්නවා
        mapTypeMenu = findViewById(R.id.mapTypeMenu);

        // 1. Layers Button Click (මෙනු එක පෙන්නන්න/හංගන්න)
        FloatingActionButton fabLayers = findViewById(R.id.fabLayers);
        fabLayers.setOnClickListener(v -> {
            if (mapTypeMenu.getVisibility() == View.VISIBLE) {
                mapTypeMenu.setVisibility(View.GONE);
            } else {
                mapTypeMenu.setVisibility(View.VISIBLE);
            }
        });

        // 2. Map Types ක්ලික් කරද්දී වෙනස් වීම
        findViewById(R.id.typeDefault).setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_NORMAL));
        findViewById(R.id.typeTerrain).setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_TERRAIN));
        findViewById(R.id.typeSatellite).setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_SATELLITE));
        findViewById(R.id.typeHybrid).setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_HYBRID));

        // 3. My Location Button Click
        FloatingActionButton fabLocation = findViewById(R.id.fabLocation);
        fabLocation.setOnClickListener(v -> getDeviceLocation());
    }

    // Map Type එක වෙනස් කරන ෆන්ක්ෂන් එක
    private void changeMapType(int mapType) {
        if (mMap != null) {
            mMap.setMapType(mapType);
            mapTypeMenu.setVisibility(View.GONE); // වෙනස් කළාට පස්සේ මෙනු එක හංගනවා
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // ඇප් එක අරිනකොටම Location එක ඉල්ලනවා
        enableMyLocation();
    }

    // Location Permission එක ඉල්ලනවා සහ Blue Dot එක දානවා
    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false); // පරණ Default බටන් එක හංගනවා
            getDeviceLocation(); // කරන්ට් Location එකට කැමරාව ගෙනියනවා
        } else {
            // Permission නැත්නම් ඉල්ලනවා
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    // Current Location එක හොයාගෙන කැමරාව එතනට Zoom කරනවා
    private void getDeviceLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null && mMap != null) {
                    LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f)); // 15f කියන්නේ හොඳට Zoom වෙන ගාණක්
                } else {
                    Toast.makeText(this, "Unable to find location. Is GPS turned on?", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    // Permission එක දුන්නට පස්සේ වෙන දේ
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            } else {
                Toast.makeText(this, "Permission denied. Real-time location will not work.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}