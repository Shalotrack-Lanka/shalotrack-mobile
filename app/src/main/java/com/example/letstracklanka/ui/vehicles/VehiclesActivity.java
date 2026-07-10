package com.example.letstracklanka.ui.vehicles;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.model.StatusResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.ui.main.HomeActivity;
import com.example.letstracklanka.ui.main.TagsActivity; // TagsActivity එක Import කළා
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VehiclesActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ShaloTrackApi apiService;

    // UI කොටස්
    private LinearLayout layoutCollapsed, layoutExpanded, layoutLeftFabs;
    private GridLayout gridMenu;
    private FloatingActionButton fabAdd, fabHistory;
    private ImageView btnCloseExpanded;

    // Data පෙන්වන කොටස්
    private TextView tvCollapsedStatus, tvCollapsedAddress;
    private TextView tvExpandedStatus, tvExpandedAddress, tvLastUpdated;
    private CardView dotIgnition, dotAC;
    private MaterialButton btnRefresh;

    private Handler handler = new Handler();
    private Runnable runnable;
    private final int UPDATE_INTERVAL = 10000;
    private String selectedDeviceId = "DEMO_DEVICE_001";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicles);

        try {
            // Layouts සහ FABs අල්ලගැනීම
            layoutCollapsed = findViewById(R.id.layoutCollapsed);
            layoutExpanded = findViewById(R.id.layoutExpanded);
            layoutLeftFabs = findViewById(R.id.layoutLeftFabs);
            gridMenu = findViewById(R.id.gridMenu);
            fabAdd = findViewById(R.id.fabAdd);
            fabHistory = findViewById(R.id.fabHistory);

            // Text Views සහ Buttons අල්ලගැනීම
            tvCollapsedStatus = findViewById(R.id.tvCollapsedStatus);
            tvCollapsedAddress = findViewById(R.id.tvCollapsedAddress);
            tvExpandedStatus = findViewById(R.id.tvExpandedStatus);
            tvExpandedAddress = findViewById(R.id.tvExpandedAddress);
            tvLastUpdated = findViewById(R.id.tvLastUpdated);
            dotIgnition = findViewById(R.id.dotIgnition);
            dotAC = findViewById(R.id.dotAC);

            btnCloseExpanded = findViewById(R.id.btnCloseExpanded);
            btnRefresh = findViewById(R.id.btnRefresh);
        } catch (Exception e) {
            e.printStackTrace();
        }

        apiService = ApiClient.getClient().create(ShaloTrackApi.class);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapVehicles);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // --- Bottom Sheet Toggling Logic ---
        View bottomSheet = findViewById(R.id.bottomSheetVehicleDetails);
        BottomSheetBehavior<View> bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    // Fully Expanded (උඩටම ඇද්දම)
                    gridMenu.setVisibility(View.VISIBLE);
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    // Collapsed (පල්ලෙහාට ගියාම)
                    layoutExpanded.setVisibility(View.GONE);
                    gridMenu.setVisibility(View.GONE);

                    layoutCollapsed.setVisibility(View.VISIBLE);
                    fabAdd.setVisibility(View.VISIBLE);

                    fabHistory.setVisibility(View.GONE);
                    layoutLeftFabs.setVisibility(View.GONE);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) { }
        });

        // පොඩි කාඩ් එක ක්ලික් කරද්දී Half-Expanded වෙනවා
        if (layoutCollapsed != null) {
            layoutCollapsed.setOnClickListener(v -> {
                layoutCollapsed.setVisibility(View.GONE);
                fabAdd.setVisibility(View.GONE);

                layoutExpanded.setVisibility(View.VISIBLE);
                gridMenu.setVisibility(View.GONE); // තාම ග්‍රිඩ් එක පේන්නේ නෑ

                fabHistory.setVisibility(View.VISIBLE);
                layoutLeftFabs.setVisibility(View.VISIBLE);

                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
            });
        }

        // 'X' බටන් එක ක්ලික් කරද්දී ආපහු Collapsed වෙනවා
        if (btnCloseExpanded != null) {
            btnCloseExpanded.setOnClickListener(v -> {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            });
        }

        // --- Bottom Navigation Setup ---

        // Home බටන් එක
        LinearLayout navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                Intent intent = new Intent(VehiclesActivity.this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish();
            });
        }

        // Tags බටන් එකට අදාළ අලුත් කේතය
        LinearLayout bottomNavBar = findViewById(R.id.bottomNavBar);
        if (bottomNavBar != null && bottomNavBar.getChildCount() > 2) {
            View navTags = bottomNavBar.getChildAt(2); // 3 වැනි අයිතමය Tags
            navTags.setOnClickListener(v -> {
                Intent intent = new Intent(VehiclesActivity.this, TagsActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // Refresh බටන් එක
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                Toast.makeText(this, "Refreshing location...", Toast.LENGTH_SHORT).show();
                fetchRealTimeVehicleData();
            });
        }

        startRealTimeTracking();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        fetchRealTimeVehicleData();
    }

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

                    String addressText = lat + ", " + lng;
                    if (tvCollapsedAddress != null) tvCollapsedAddress.setText(addressText);
                    if (tvExpandedAddress != null) tvExpandedAddress.setText(addressText);

                    if(tvLastUpdated != null) {
                        String currentTime = new SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.getDefault()).format(new Date());
                        tvLastUpdated.setText("Updated: " + currentTime);
                    }
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

                    if (tvCollapsedStatus != null && tvExpandedStatus != null) {
                        if (speed > 0) {
                            String movingText = "Moving (" + (int)speed + " km/h)";
                            tvCollapsedStatus.setText(movingText);
                            tvCollapsedStatus.setTextColor(Color.parseColor("#00BFA5"));
                            tvExpandedStatus.setText(movingText);
                            tvExpandedStatus.setTextColor(Color.parseColor("#00BFA5"));
                        } else {
                            tvCollapsedStatus.setText("Parked ");
                            tvCollapsedStatus.setTextColor(Color.parseColor("#1976D2"));
                            tvExpandedStatus.setText("Parked ");
                            tvExpandedStatus.setTextColor(Color.parseColor("#1976D2"));
                        }
                    }

                    if (dotIgnition != null && dotAC != null) {
                        if(isAccOn) {
                            dotIgnition.setCardBackgroundColor(Color.parseColor("#4CAF50"));
                            dotAC.setCardBackgroundColor(Color.parseColor("#4CAF50"));
                        } else {
                            dotIgnition.setCardBackgroundColor(Color.parseColor("#E53935"));
                            dotAC.setCardBackgroundColor(Color.parseColor("#E53935"));
                        }
                    }
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
}