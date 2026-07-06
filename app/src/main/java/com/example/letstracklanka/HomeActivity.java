package com.example.letstracklanka;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private CardView mapTypeMenu;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private MaterialCardView cardDefault, cardTerrain, cardSatellite, cardHybrid;
    private LatLng myCurrentLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        NestedScrollView bottomSheet = findViewById(R.id.bottomSheet);
        BottomSheetBehavior<NestedScrollView> bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        mapTypeMenu = findViewById(R.id.mapTypeMenu);
        cardDefault = findViewById(R.id.cardDefault);
        cardTerrain = findViewById(R.id.cardTerrain);
        cardSatellite = findViewById(R.id.cardSatellite);
        cardHybrid = findViewById(R.id.cardHybrid);

        FloatingActionButton fabLayers = findViewById(R.id.fabLayers);
        fabLayers.setOnClickListener(v -> {
            if (mapTypeMenu.getVisibility() == View.VISIBLE) {
                mapTypeMenu.setVisibility(View.GONE);
            } else {
                mapTypeMenu.setVisibility(View.VISIBLE);
            }
        });

        cardDefault.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_NORMAL, cardDefault));
        cardTerrain.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_TERRAIN, cardTerrain));
        cardSatellite.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_SATELLITE, cardSatellite));
        cardHybrid.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_HYBRID, cardHybrid));

        FloatingActionButton fabLocation = findViewById(R.id.fabLocation);
        fabLocation.setOnClickListener(v -> getDeviceLocation());

        MaterialButton btnSendLocation = findViewById(R.id.btnSendLocation);
        btnSendLocation.setOnClickListener(v -> showSendLocationBottomSheet());
    }

    // 1 වෙනි මෙනු එක
    @SuppressLint("InflateParams")
    private void showSendLocationBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_send_location, null);
        bottomSheetDialog.setContentView(sheetView);

        ImageView btnClose = sheetView.findViewById(R.id.btnCloseSheet);
        LinearLayout btnShareCurrent = sheetView.findViewById(R.id.btnShareCurrentLoc);
        LinearLayout btnShareLive = sheetView.findViewById(R.id.btnShareLiveLoc);

        btnClose.setOnClickListener(v -> bottomSheetDialog.dismiss());

        btnShareCurrent.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showOrangeBottomSheet(); // 2 වෙනි මෙනු එක ඕපන් කරනවා
        });

        btnShareLive.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Toast.makeText(this, "Sharing Live Location...", Toast.LENGTH_SHORT).show();
        });

        bottomSheetDialog.show();
    }

    // 2 වෙනි මෙනු එක
    @SuppressLint("InflateParams")
    private void showOrangeBottomSheet() {
        BottomSheetDialog orangeSheetDialog = new BottomSheetDialog(this);
        View orangeView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_share_current, null);
        orangeSheetDialog.setContentView(orangeView);

        ImageView btnClose = orangeView.findViewById(R.id.btnCloseOrangeSheet);
        LinearLayout btnFinalShareMyLoc = orangeView.findViewById(R.id.btnFinalShareMyLoc);
        LinearLayout btnFinalShareDeviceLoc = orangeView.findViewById(R.id.btnFinalShareDeviceLoc);

        btnClose.setOnClickListener(v -> orangeSheetDialog.dismiss());

        // My Location Share කිරීම
        btnFinalShareMyLoc.setOnClickListener(v -> {
            orangeSheetDialog.dismiss();
            shareLocationLink("my current location");
        });

        // Device Location Share කිරීමේ බටන් එක එබුවම 3 වෙනි මෙනු එකට යනවා
        btnFinalShareDeviceLoc.setOnClickListener(v -> {
            orangeSheetDialog.dismiss();
            showShareDeviceBottomSheet(); // අලුත් මෙනු එක ලෝඩ් කරනවා
        });

        orangeSheetDialog.show();
    }

    // 3 වෙනි මෙනු එක (ඔයා අන්තිමට එවපු පින්තූරේ මෙනු එක)
    @SuppressLint("InflateParams")
    private void showShareDeviceBottomSheet() {
        BottomSheetDialog deviceSheetDialog = new BottomSheetDialog(this);
        View deviceView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_share_device, null);
        deviceSheetDialog.setContentView(deviceView);

        ImageView btnClose = deviceView.findViewById(R.id.btnCloseDeviceSheet);
        Spinner spinnerDevices = deviceView.findViewById(R.id.spinnerDevices);
        MaterialButton btnFinalShareDevice = deviceView.findViewById(R.id.btnFinalShareDevice);

        btnClose.setOnClickListener(v -> deviceSheetDialog.dismiss());

        // Spinner එකට දාන්න Devices ලිස්ට් එකක් හදනවා
        String[] devices = {"Select device", "Nissan GT-R R35", "Tecno Camon 40 Pro", "iPhone 13 Pro"};

        // ඒ ලිස්ට් එක Spinner එකට සෙට් කරනවා
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, devices);
        spinnerDevices.setAdapter(adapter);

        // Share බටන් එක එබුවම
        btnFinalShareDevice.setOnClickListener(v -> {
            String selectedDevice = spinnerDevices.getSelectedItem().toString();

            if (selectedDevice.equals("Select device")) {
                Toast.makeText(this, "Please select a device first!", Toast.LENGTH_SHORT).show();
            } else {
                deviceSheetDialog.dismiss();
                // තෝරපු Device එකේ නමත් එක්කම ලොකේෂන් එක Share කරනවා
                shareLocationLink("the location of " + selectedDevice);
            }
        });

        deviceSheetDialog.show();
    }

    // ලොකේෂන් එක Share කරන්න පාවිච්චි කරන පොදු ෆන්ක්ෂන් එක
    private void shareLocationLink(String entityName) {
        if (myCurrentLocation != null) {
            String locationLink = "https://www.google.com/maps?q=" + myCurrentLocation.latitude + "," + myCurrentLocation.longitude;
            String message = "Here is " + entityName + ": \n" + locationLink;

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Live Location");
            shareIntent.putExtra(Intent.EXTRA_TEXT, message);
            startActivity(Intent.createChooser(shareIntent, "Share Location via"));
        } else {
            Toast.makeText(this, "Location not found yet. Searching...", Toast.LENGTH_SHORT).show();
            getDeviceLocation();
        }
    }

    private void changeMapType(int mapType, MaterialCardView selectedCard) {
        if (mMap != null) {
            mMap.setMapType(mapType);
            cardDefault.setStrokeWidth(0);
            cardTerrain.setStrokeWidth(0);
            cardSatellite.setStrokeWidth(0);
            cardHybrid.setStrokeWidth(0);
            selectedCard.setStrokeWidth(8);
            selectedCard.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#1877F2")));
            mapTypeMenu.setVisibility(View.GONE);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        enableMyLocation();
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
            getDeviceLocation();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void getDeviceLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null && mMap != null) {
                    myCurrentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myCurrentLocation, 15f));
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        }
    }
}