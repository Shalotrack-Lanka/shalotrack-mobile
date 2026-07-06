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
import android.widget.ImageView;
import android.widget.LinearLayout;
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
        btnSendLocation.setOnClickListener(v -> showSendLocationBottomSheet()); // 1 වෙනි මෙනු එක ලෝඩ් කරනවා
    }

    // 1 වෙනි මෙනු එක (නිල් පාට බටන් තියෙන එක)
    @SuppressLint("InflateParams")
    private void showSendLocationBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_send_location, null);
        bottomSheetDialog.setContentView(sheetView);

        ImageView btnClose = sheetView.findViewById(R.id.btnCloseSheet);
        LinearLayout btnShareCurrent = sheetView.findViewById(R.id.btnShareCurrentLoc);
        LinearLayout btnShareLive = sheetView.findViewById(R.id.btnShareLiveLoc);

        btnClose.setOnClickListener(v -> bottomSheetDialog.dismiss());

        // නිල් පාට "Share Your Current Location" එබුවම 2 වෙනි මෙනු එකට යනවා
        btnShareCurrent.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showOrangeBottomSheet(); // තැඹිලි පාට මෙනු එක ඕපන් කරනවා
        });

        btnShareLive.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Toast.makeText(this, "Sharing Live Location...", Toast.LENGTH_SHORT).show();
        });

        bottomSheetDialog.show();
    }

    // 2 වෙනි මෙනු එක (තැඹිලි පාට බටන් තියෙන එක - පින්තූරේ විදිහට)
    @SuppressLint("InflateParams")
    private void showOrangeBottomSheet() {
        BottomSheetDialog orangeSheetDialog = new BottomSheetDialog(this);
        View orangeView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_share_current, null);
        orangeSheetDialog.setContentView(orangeView);

        ImageView btnClose = orangeView.findViewById(R.id.btnCloseOrangeSheet);
        LinearLayout btnFinalShareMyLoc = orangeView.findViewById(R.id.btnFinalShareMyLoc);
        LinearLayout btnFinalShareDeviceLoc = orangeView.findViewById(R.id.btnFinalShareDeviceLoc);

        btnClose.setOnClickListener(v -> orangeSheetDialog.dismiss());

        // මෙතනින් තමයි Real-time Location එක ඇත්තටම Share වෙන්නේ!
        btnFinalShareMyLoc.setOnClickListener(v -> {
            orangeSheetDialog.dismiss();

            if (myCurrentLocation != null) {
                // ඔයා ඉන්න තැනට Google Maps ලින්ක් එකක් හදනවා
                String locationLink = "https://www.google.com/maps?q=" + myCurrentLocation.latitude + "," + myCurrentLocation.longitude;
                String message = "Here is my current location: \n" + locationLink;

                // Share කරන්න ෆෝන් එකේ ඇප්ස් වලට (WhatsApp, MSG) යවනවා
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "My Location");
                shareIntent.putExtra(Intent.EXTRA_TEXT, message);
                startActivity(Intent.createChooser(shareIntent, "Share Location via"));
            } else {
                Toast.makeText(this, "Location not found yet. Searching...", Toast.LENGTH_SHORT).show();
                getDeviceLocation();
            }
        });

        btnFinalShareDeviceLoc.setOnClickListener(v -> {
            orangeSheetDialog.dismiss();
            Toast.makeText(this, "Select a device to share its location.", Toast.LENGTH_SHORT).show();
        });

        orangeSheetDialog.show();
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