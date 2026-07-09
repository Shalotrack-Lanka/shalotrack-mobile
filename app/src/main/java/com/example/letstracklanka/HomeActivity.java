package com.example.letstracklanka;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
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
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private CardView mapTypeMenu;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private MaterialCardView cardDefault, cardTerrain, cardSatellite, cardHybrid;
    private LatLng myCurrentLocation;

    // API සහ Real-time Variables
    private ShaloTrackApi apiService;
    private Handler handler = new Handler();
    private Runnable runnable;
    private final int UPDATE_INTERVAL = 10000; // තත්පර 10කට වරක්
    private String selectedDeviceId = "DEMO_DEVICE_001"; // ඔයාගේ Device ID එක

    // යටින් එන වාහනේ කාඩ් එකේ කොටස්
    private TextView tvDeviceStatus, tvDeviceAddress, tvDeviceName, tvDeviceTime;
    private ImageView imgDeviceIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        apiService = ApiClient.getClient().create(ShaloTrackApi.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // යටින් එන වාහනේ කාඩ් එකේ (Included Layout) අයිතම අල්ලගැනීම
        View bottomSheetView = findViewById(R.id.bottomSheet); // අලුත් XML එකට අනුව NestedScrollView එකේ ID එක
        if(bottomSheetView != null) {
            tvDeviceName = bottomSheetView.findViewById(R.id.tvDeviceName);
            tvDeviceStatus = bottomSheetView.findViewById(R.id.tvDeviceStatus);
            tvDeviceTime = bottomSheetView.findViewById(R.id.tvDeviceTime);
            tvDeviceAddress = bottomSheetView.findViewById(R.id.tvDeviceAddress);
            imgDeviceIcon = bottomSheetView.findViewById(R.id.imgDeviceIcon);
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // --- පරණ මෙනු සහ SOS බටන් සෙට් කිරීම ---
        NestedScrollView bottomSheet = findViewById(R.id.bottomSheet);
        if(bottomSheet != null) {
            BottomSheetBehavior<NestedScrollView> bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }

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
        if(btnSendLocation != null) btnSendLocation.setOnClickListener(v -> showSendLocationBottomSheet());

        MaterialButton btnHomeSOS = findViewById(R.id.btnSOS);
        if (btnHomeSOS != null) btnHomeSOS.setOnClickListener(v -> showSOSBottomSheet());

        // --- මෙන්න අලුතින් වෙනස් කළ Custom Bottom Navigation කේතය ---
        LinearLayout navVehicles = findViewById(R.id.nav_vehicles);
        if (navVehicles != null) {
            navVehicles.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, VehiclesActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0); // ඇනිමේෂන් එකක් නැතිව යනවා
            });
        }
        // -----------------------------------------------------------
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        enableMyLocation();
        startRealTimeTracking();
    }

    // --- Real Time Tracking කේතය ---
    private void startRealTimeTracking() {
        runnable = new Runnable() {
            @Override
            public void run() {
                fetchRealTimeVehicleData();
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        handler.post(runnable);
    }

    private void fetchRealTimeVehicleData() {
        if (apiService == null || mMap == null) return;

        apiService.getCurrentLocation(selectedDeviceId).enqueue(new Callback<LocationResponse>() {
            @Override
            public void onResponse(Call<LocationResponse> call, Response<LocationResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    double lat = response.body().getLatitude();
                    double lng = response.body().getLongitude();
                    LatLng carLocation = new LatLng(lat, lng);

                    mMap.clear();
                    mMap.addMarker(new MarkerOptions().position(carLocation).title("LT Demo Device"));
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(carLocation, 16f));

                    if(tvDeviceAddress != null) tvDeviceAddress.setText("Location: " + lat + ", " + lng);
                }
            }
            @Override
            public void onFailure(Call<LocationResponse> call, Throwable t) { }
        });

        apiService.getDeviceStatus(selectedDeviceId).enqueue(new Callback<StatusResponse>() {
            @Override
            public void onResponse(Call<StatusResponse> call, Response<StatusResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    double speed = response.body().getSpeed();
                    boolean isAccOn = response.body().isAccStatus();

                    if(tvDeviceStatus != null && imgDeviceIcon != null) {
                        if (speed > 0) {
                            tvDeviceStatus.setText("Moving (" + (int)speed + " km/h)");
                            tvDeviceStatus.setTextColor(Color.parseColor("#00BFA5"));
                            imgDeviceIcon.setColorFilter(Color.parseColor("#00BFA5"));
                        } else {
                            tvDeviceStatus.setText(isAccOn ? "Idle (Engine ON)" : "Parked");
                            tvDeviceStatus.setTextColor(Color.parseColor("#1877F2"));
                            imgDeviceIcon.setColorFilter(Color.parseColor("#1877F2"));
                        }
                    }
                    if(tvDeviceTime != null) tvDeviceTime.setText("(Updated Just now)");
                }
            }
            @Override
            public void onFailure(Call<StatusResponse> call, Throwable t) { }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    // --- පරණ ෆන්ක්ෂන් ටික (SOS, Location) ---
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
                if (location != null) {
                    myCurrentLocation = new LatLng(location.getLatitude(), location.getLongitude());
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

    // ... (ShowSOSBottomSheet, ShareLocationBottomSheet වගේ පරණ ෆන්ක්ෂන් ටික ඔයාගේ කලින් කේතයේ තියෙන විදිහටම තියාගන්න)

    @SuppressLint("InflateParams")
    private void showSOSBottomSheet() {
        BottomSheetDialog sosDialog = new BottomSheetDialog(this);
        View sosView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_sos, null);
        sosDialog.setContentView(sosView);

        ImageView btnClose = sosView.findViewById(R.id.btnCloseSOS);
        LinearLayout btnTapSOS = sosView.findViewById(R.id.btnTapSOS);
        MaterialButton btnAddContacts = sosView.findViewById(R.id.btnAddContacts);
        LinearLayout btnUpgradeCallCenter = sosView.findViewById(R.id.btnUpgradeCallCenter);

        View bgPulseCircle = sosView.findViewById(R.id.bgPulseCircle);
        if (bgPulseCircle != null) {
            android.view.animation.Animation pulseAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse_animation);
            bgPulseCircle.startAnimation(pulseAnim);
        }

        btnClose.setOnClickListener(v -> sosDialog.dismiss());

        btnTapSOS.setOnClickListener(v -> {
            sosDialog.dismiss();

            if (myCurrentLocation != null) {
                String locationLink = "https://www.google.com/maps?q=" + myCurrentLocation.latitude + "," + myCurrentLocation.longitude;
                String message = "EMERGENCY SOS!\nI need help. Here is my current location:\n" + locationLink;

                Intent smsIntent = new Intent(Intent.ACTION_VIEW);
                smsIntent.setType("vnd.android-dir/mms-sms");
                smsIntent.putExtra("sms_body", message);
                startActivity(smsIntent);

                Toast.makeText(this, "Opening SMS to send SOS...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Trying to find your location...", Toast.LENGTH_SHORT).show();
                getDeviceLocation();
            }
        });

        btnAddContacts.setOnClickListener(v -> {
            Toast.makeText(this, "Contacts feature coming soon!", Toast.LENGTH_SHORT).show();
        });

        btnUpgradeCallCenter.setOnClickListener(v -> {
            sosDialog.dismiss();
            showCallCenterBottomSheet();
        });

        sosDialog.show();
    }

    @SuppressLint("InflateParams")
    private void showCallCenterBottomSheet() {
        BottomSheetDialog callCenterDialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_call_center, null);
        callCenterDialog.setContentView(view);

        ImageView btnClose = view.findViewById(R.id.btnCloseCallCenter);
        MaterialButton btnCloseBottom = view.findViewById(R.id.btnCallCenterClose);
        MaterialButton btnUnavailable = view.findViewById(R.id.btnCallCenterUnavailable);

        androidx.viewpager2.widget.ViewPager2 viewPager = view.findViewById(R.id.viewPagerCallCenter);
        LinearLayout layoutDots = view.findViewById(R.id.layoutDotsCallCenter);

        CallCenterPagerAdapter adapter = new CallCenterPagerAdapter();
        viewPager.setAdapter(adapter);

        ImageView[] dots = new ImageView[adapter.getItemCount()];
        for (int i = 0; i < adapter.getItemCount(); i++) {
            dots[i] = new ImageView(this);
            dots[i].setImageDrawable(androidx.core.content.ContextCompat.getDrawable(this, android.R.drawable.presence_invisible));
            dots[i].setColorFilter(Color.parseColor("#CCCCCC"));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0);
            layoutDots.addView(dots[i], params);
        }
        dots[0].setColorFilter(Color.parseColor("#1877F2"));

        viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                for (int i = 0; i < adapter.getItemCount(); i++) {
                    dots[i].setColorFilter(Color.parseColor("#CCCCCC"));
                }
                dots[position].setColorFilter(Color.parseColor("#1877F2"));
            }
        });

        btnClose.setOnClickListener(v -> callCenterDialog.dismiss());
        btnCloseBottom.setOnClickListener(v -> callCenterDialog.dismiss());

        btnUnavailable.setOnClickListener(v -> {
            Toast.makeText(this, "Feature not available in your region yet.", Toast.LENGTH_SHORT).show();
        });

        callCenterDialog.show();
    }

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
            showOrangeBottomSheet();
        });

        btnShareLive.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showLiveMenuBottomSheet();
        });

        bottomSheetDialog.show();
    }

    @SuppressLint("InflateParams")
    private void showOrangeBottomSheet() {
        BottomSheetDialog orangeSheetDialog = new BottomSheetDialog(this);
        View orangeView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_share_current, null);
        orangeSheetDialog.setContentView(orangeView);

        ImageView btnClose = orangeView.findViewById(R.id.btnCloseOrangeSheet);
        LinearLayout btnFinalShareMyLoc = orangeView.findViewById(R.id.btnFinalShareMyLoc);
        LinearLayout btnFinalShareDeviceLoc = orangeView.findViewById(R.id.btnFinalShareDeviceLoc);

        btnClose.setOnClickListener(v -> orangeSheetDialog.dismiss());

        btnFinalShareMyLoc.setOnClickListener(v -> {
            orangeSheetDialog.dismiss();
            shareLocationLink("my current location");
        });

        btnFinalShareDeviceLoc.setOnClickListener(v -> {
            orangeSheetDialog.dismiss();
            showShareDeviceBottomSheet();
        });

        orangeSheetDialog.show();
    }

    @SuppressLint("InflateParams")
    private void showShareDeviceBottomSheet() {
        BottomSheetDialog deviceSheetDialog = new BottomSheetDialog(this);
        View deviceView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_share_device, null);
        deviceSheetDialog.setContentView(deviceView);

        ImageView btnClose = deviceView.findViewById(R.id.btnCloseDeviceSheet);
        Spinner spinnerDevices = deviceView.findViewById(R.id.spinnerDevices);
        MaterialButton btnFinalShareDevice = deviceView.findViewById(R.id.btnFinalShareDevice);

        btnClose.setOnClickListener(v -> deviceSheetDialog.dismiss());

        String[] devices = {"Select device", "Nissan GT-R R35", "Tecno Camon 40 Pro", "iPhone 13 Pro"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, devices);
        spinnerDevices.setAdapter(adapter);

        btnFinalShareDevice.setOnClickListener(v -> {
            String selectedDevice = spinnerDevices.getSelectedItem().toString();
            if (selectedDevice.equals("Select device")) {
                Toast.makeText(this, "Please select a device first!", Toast.LENGTH_SHORT).show();
            } else {
                deviceSheetDialog.dismiss();
                shareLocationLink("the location of " + selectedDevice);
            }
        });

        deviceSheetDialog.show();
    }

    @SuppressLint("InflateParams")
    private void showLiveMenuBottomSheet() {
        BottomSheetDialog liveMenuDialog = new BottomSheetDialog(this);
        View liveView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_share_live, null);
        liveMenuDialog.setContentView(liveView);

        ImageView btnClose = liveView.findViewById(R.id.btnCloseLiveSheet);
        LinearLayout btnLiveShareMyLoc = liveView.findViewById(R.id.btnLiveShareMyLoc);
        LinearLayout btnLiveShareDeviceLoc = liveView.findViewById(R.id.btnLiveShareDeviceLoc);

        btnClose.setOnClickListener(v -> liveMenuDialog.dismiss());

        btnLiveShareMyLoc.setOnClickListener(v -> {
            liveMenuDialog.dismiss();
            showLiveDurationBottomSheet();
        });

        btnLiveShareDeviceLoc.setOnClickListener(v -> {
            liveMenuDialog.dismiss();
            showLiveDeviceDurationBottomSheet();
        });

        liveMenuDialog.show();
    }

    @SuppressLint("InflateParams")
    private void showLiveDurationBottomSheet() {
        BottomSheetDialog durationDialog = new BottomSheetDialog(this);
        View durView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_live_duration, null);
        durationDialog.setContentView(durView);

        ImageView btnClose = durView.findViewById(R.id.btnCloseDurationSheet);
        MaterialButton btnShare = durView.findViewById(R.id.btnFinalShareLiveMyLoc);

        MaterialButton[] durButtons = {
                durView.findViewById(R.id.btnDur15m), durView.findViewById(R.id.btnDur1h), durView.findViewById(R.id.btnDur3h),
                durView.findViewById(R.id.btnDur8h), durView.findViewById(R.id.btnDur12h), durView.findViewById(R.id.btnDur24h)
        };

        final String[] selectedDuration = {""};

        btnClose.setOnClickListener(v -> durationDialog.dismiss());

        for (MaterialButton btn : durButtons) {
            btn.setOnClickListener(v -> {
                for (MaterialButton b : durButtons) {
                    b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F0F0F0")));
                    b.setTextColor(Color.BLACK);
                }
                btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E3F2FD")));
                btn.setTextColor(Color.parseColor("#1877F2"));
                selectedDuration[0] = btn.getText().toString();
            });
        }

        btnShare.setOnClickListener(v -> {
            if (selectedDuration[0].isEmpty()) {
                Toast.makeText(this, "Please select a duration!", Toast.LENGTH_SHORT).show();
            } else {
                durationDialog.dismiss();
                shareLocationLink("my LIVE location for " + selectedDuration[0]);
            }
        });

        durationDialog.show();
    }

    @SuppressLint("InflateParams")
    private void showLiveDeviceDurationBottomSheet() {
        BottomSheetDialog devDurDialog = new BottomSheetDialog(this);
        View devView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_live_device_duration, null);
        devDurDialog.setContentView(devView);

        ImageView btnClose = devView.findViewById(R.id.btnCloseDeviceDurationSheet);
        Spinner spinnerLiveDevices = devView.findViewById(R.id.spinnerLiveDevices);
        MaterialButton btnShare = devView.findViewById(R.id.btnFinalShareDeviceLive);

        String[] devices = {"Select device", "Nissan GT-R R35", "Tecno Camon 40 Pro", "iPhone 13 Pro"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, devices);
        spinnerLiveDevices.setAdapter(adapter);

        MaterialButton[] durButtons = {
                devView.findViewById(R.id.btnDevDur15m), devView.findViewById(R.id.btnDevDur1h), devView.findViewById(R.id.btnDevDur3h),
                devView.findViewById(R.id.btnDevDur8h), devView.findViewById(R.id.btnDevDur12h), devView.findViewById(R.id.btnDevDur24h)
        };

        final String[] selectedDuration = {""};

        btnClose.setOnClickListener(v -> devDurDialog.dismiss());

        for (MaterialButton btn : durButtons) {
            btn.setOnClickListener(v -> {
                for (MaterialButton b : durButtons) {
                    b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F0F0F0")));
                    b.setTextColor(Color.BLACK);
                }
                btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E3F2FD")));
                btn.setTextColor(Color.parseColor("#1877F2"));
                selectedDuration[0] = btn.getText().toString();
            });
        }

        btnShare.setOnClickListener(v -> {
            String selectedDevice = spinnerLiveDevices.getSelectedItem().toString();
            if (selectedDevice.equals("Select device")) {
                Toast.makeText(this, "Please select a device!", Toast.LENGTH_SHORT).show();
            } else if (selectedDuration[0].isEmpty()) {
                Toast.makeText(this, "Please select a duration!", Toast.LENGTH_SHORT).show();
            } else {
                devDurDialog.dismiss();
                shareLocationLink("the LIVE location of " + selectedDevice + " for " + selectedDuration[0]);
            }
        });

        devDurDialog.show();
    }

    private void shareLocationLink(String entityName) {
        if (myCurrentLocation != null) {
            String locationLink = "https://www.google.com/maps?q=" + myCurrentLocation.latitude + "," + myCurrentLocation.longitude;
            String message = "Here is " + entityName + ": \n" + locationLink;

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Location Tracking");
            shareIntent.putExtra(Intent.EXTRA_TEXT, message);
            startActivity(Intent.createChooser(shareIntent, "Share Location via"));
        } else {
            Toast.makeText(this, "Location not found yet. Searching...", Toast.LENGTH_SHORT).show();
            getDeviceLocation();
        }
    }
}