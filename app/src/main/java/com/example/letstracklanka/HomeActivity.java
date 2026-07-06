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

    // Map Cards
    private MaterialCardView cardDefault, cardTerrain, cardSatellite, cardHybrid;

    // ඔයා ඉන්න තැන ලොකේෂන් එක සේව් කරගන්න Variable එකක්
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
        // Warning එක නැති කරන්න මේක Local Variable එකක් විදිහට හැදුවා
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

    // null Warning එක අයින් කරන්න @SuppressLint එක දැම්මා
    @SuppressLint("InflateParams")
    private void showSendLocationBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);

        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_send_location, null);
        bottomSheetDialog.setContentView(sheetView);

        ImageView btnClose = sheetView.findViewById(R.id.btnCloseSheet);
        LinearLayout btnShareCurrent = sheetView.findViewById(R.id.btnShareCurrentLoc);
        LinearLayout btnShareLive = sheetView.findViewById(R.id.btnShareLiveLoc);

        btnClose.setOnClickListener(v -> bottomSheetDialog.dismiss());

        // --- Current Location Share කරන කෑල්ල ---
        btnShareCurrent.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();

            if (myCurrentLocation != null) {
                // Google Maps ලින්ක් එක හදනවා
                String locationLink = "https://maps.google.com/?q=" + myCurrentLocation.latitude + "," + myCurrentLocation.longitude;
                String message = "Here is my current location: \n" + locationLink;

                // Share කරන්න ෆෝන් එකේ ඇප්ස් වලට යවනවා
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "My Location");
                shareIntent.putExtra(Intent.EXTRA_TEXT, message);
                startActivity(Intent.createChooser(shareIntent, "Share Location via"));
            } else {
                Toast.makeText(this, "Location not found yet. Please try again.", Toast.LENGTH_SHORT).show();
                getDeviceLocation(); // ලොකේෂන් එක අරන් නැත්නම් ආයෙත් ගන්න ට්‍රයි කරනවා
            }
        });

        // --- Live Location Share කරන කෑල්ල ---
        btnShareLive.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();

            // Firebase හරහා Live Location යවන්න Database එකක් ඕනේ නිසා, දැනට පණිවිඩයක් පෙන්වනවා
            Toast.makeText(this, "Live Location sharing will be available soon!", Toast.LENGTH_SHORT).show();
        });

        bottomSheetDialog.show();
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
                    // ලොකේෂන් එක හම්බුණාම ඒක myCurrentLocation කියන variable එකේ සේව් කරගන්නවා
                    myCurrentLocation = new LatLng(location.getLatitude(), location.getLongitude());

                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myCurrentLocation, 15f));
                } else {
                    Toast.makeText(this, "Unable to find location. Is GPS turned on?", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

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