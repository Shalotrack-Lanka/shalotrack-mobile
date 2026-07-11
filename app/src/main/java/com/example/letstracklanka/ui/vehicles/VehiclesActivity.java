package com.example.letstracklanka.ui.vehicles;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.ui.main.HomeActivity;
import com.example.letstracklanka.ui.main.TagsActivity;
import com.example.letstracklanka.ui.main.CirclesActivity;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VehiclesActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ShaloTrackApi trackingApi;
    private ApiService mainApiService;

    // UI components
    private View layoutCollapsed;
    private LinearLayout layoutExpanded, layoutLeftFabs;
    private GridLayout gridMenu;
    private ImageView btnCloseExpanded;
    private View fabAdd, fabHistory, btnRefresh;
    private BottomSheetBehavior<View> bottomSheetBehavior;

    // Data display components
    private TextView tvCollapsedStatus, tvCollapsedAddress;
    private TextView tvExpandedStatus, tvExpandedAddress, tvLastUpdated, tvVehicleNameCollapsed, tvVehicleNameExpanded;
    private CardView dotIgnition, dotAC;

    private final Handler handler = new Handler();
    private Runnable trackingRunnable;
    private final int UPDATE_INTERVAL = 5000;
    
    private static final String DEMO_VEHICLE_ID = "39019073-09b8-4dbc-b5f9-6d7ade5ec4df";

    private String currentCustomerId = null;
    private String selectedVehicleId = DEMO_VEHICLE_ID;
    private String selectedVehicleName = "Demo Vehicle";
    private boolean hasRealVehicle = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicles);

        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);
        mainApiService = ApiClient.getClient().create(ApiService.class);

        initViews();
        setupBottomSheet();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapVehicles);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        loadUserData();
        startRealTimeTracking();
    }

    private void initViews() {
        layoutCollapsed = findViewById(R.id.layoutCollapsed);
        layoutExpanded = findViewById(R.id.layoutExpanded);
        layoutLeftFabs = findViewById(R.id.layoutLeftFabs);
        gridMenu = findViewById(R.id.gridMenu);
        fabAdd = findViewById(R.id.fabAdd);
        fabHistory = findViewById(R.id.fabHistory);

        tvCollapsedStatus = findViewById(R.id.tvCollapsedStatus);
        tvCollapsedAddress = findViewById(R.id.tvCollapsedAddress);
        tvExpandedStatus = findViewById(R.id.tvExpandedStatus);
        tvExpandedAddress = findViewById(R.id.tvExpandedAddress);
        tvLastUpdated = findViewById(R.id.tvLastUpdated);
        tvVehicleNameCollapsed = findViewById(R.id.tvVehicleNameCollapsed);
        tvVehicleNameExpanded = findViewById(R.id.tvVehicleNameExpanded);
        dotIgnition = findViewById(R.id.dotIgnition);
        dotAC = findViewById(R.id.dotAC);

        btnCloseExpanded = findViewById(R.id.btnCloseExpanded);
        btnRefresh = findViewById(R.id.btnRefresh);
    }

    private void setupBottomSheet() {
        View bottomSheet = findViewById(R.id.bottomSheetVehicleDetails);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    gridMenu.setVisibility(View.VISIBLE);
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    layoutExpanded.setVisibility(View.GONE);
                    gridMenu.setVisibility(View.GONE);
                    layoutCollapsed.setVisibility(View.VISIBLE);
                    if (fabAdd != null) fabAdd.setVisibility(View.VISIBLE);
                    if (fabHistory != null) fabHistory.setVisibility(View.GONE);
                    if (layoutLeftFabs != null) layoutLeftFabs.setVisibility(View.GONE);
                }
            }
            @Override public void onSlide(@NonNull View bottomSheet, float slideOffset) { }
        });

        if (layoutCollapsed != null) {
            layoutCollapsed.setOnClickListener(v -> {
                layoutCollapsed.setVisibility(View.GONE);
                if (fabAdd != null) fabAdd.setVisibility(View.GONE);
                layoutExpanded.setVisibility(View.VISIBLE);
                gridMenu.setVisibility(View.GONE);
                if (fabHistory != null) fabHistory.setVisibility(View.VISIBLE);
                if (layoutLeftFabs != null) layoutLeftFabs.setVisibility(View.VISIBLE);
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
            });
        }

        if (btnCloseExpanded != null) {
            btnCloseExpanded.setOnClickListener(v -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED));
        }

        // --- Grid Menu Listeners ---
        View btnMenuAlerts = findViewById(R.id.btnMenuAlerts);
        if (btnMenuAlerts != null) {
            btnMenuAlerts.setOnClickListener(v -> showCallCenterBottomSheet());
        }

        // --- Bottom Navigation Setup ---
        View navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                Intent intent = new Intent(VehiclesActivity.this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        View navVehicles = findViewById(R.id.nav_vehicles);
        if (navVehicles != null) {
            navVehicles.setOnClickListener(v -> {
                // Already in Vehicles, just collapse the sheet if it's open
                if (bottomSheetBehavior != null) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            });
        }

        View navTags = findViewById(R.id.nav_tags);
        if (navTags != null) {
            navTags.setOnClickListener(v -> {
                Intent intent = new Intent(VehiclesActivity.this, TagsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        View navCircles = findViewById(R.id.nav_circles);
        if (navCircles != null) {
            navCircles.setOnClickListener(v -> {
                Intent intent = new Intent(VehiclesActivity.this, CirclesActivity.class);
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
                if (bottomSheetBehavior != null) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            });
        }

        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                Toast.makeText(this, "Refreshing location...", Toast.LENGTH_SHORT).show();
                fetchLocationData();
            });
        }
    }

    private void showCallCenterBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_call_center, null);
        dialog.setContentView(view);

        ViewPager2 viewPager = view.findViewById(R.id.viewPagerCallCenter);
        if (viewPager != null) {
            viewPager.setAdapter(new CallCenterPagerAdapter());
        }

        ImageView btnClose = view.findViewById(R.id.btnCloseCallCenter);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        View btnCloseBottom = view.findViewById(R.id.btnCallCenterClose);
        if (btnCloseBottom != null) btnCloseBottom.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void loadUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        mainApiService.getCustomerByEmail(user.getEmail()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        String json = body.string();
                        List<CustomerResponse> list = parseList(json, CustomerResponse.class);
                        for (CustomerResponse c : list) {
                            if (user.getEmail().equalsIgnoreCase(c.getEmail())) {
                                currentCustomerId = c.getCustomerId();
                                break;
                            }
                        }
                        if (currentCustomerId != null) fetchVehicles();
                    }
                } catch (Exception e) {
                    Log.e("VehiclesActivity", "Error loading user data", e);
                }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("VehiclesActivity", "Failed to fetch user data", t);
            }
        });
    }

    private void fetchVehicles() {
        if (currentCustomerId == null) return;
        mainApiService.getVehiclesByCustomer(currentCustomerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<VehicleResponse> list = parseList(body.string(), VehicleResponse.class);
                        if (!list.isEmpty()) {
                            VehicleResponse vehicle = list.get(list.size() - 1);
                            selectedVehicleId = vehicle.getVehicleId();
                            selectedVehicleName = vehicle.getMake() + " " + vehicle.getModel();
                            hasRealVehicle = true;
                            updateVehicleUI();
                        }
                    }
                } catch (Exception e) {
                    Log.e("VehiclesActivity", "Error fetching vehicles", e);
                }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("VehiclesActivity", "Failed to fetch vehicles", t);
            }
        });
    }

    private void updateVehicleUI() {
        if (tvVehicleNameCollapsed != null) tvVehicleNameCollapsed.setText(selectedVehicleName);
        if (tvVehicleNameExpanded != null) tvVehicleNameExpanded.setText(selectedVehicleName);
    }

    @Override public void onMapReady(@NonNull GoogleMap googleMap) { mMap = googleMap; }

    private void startRealTimeTracking() {
        if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable);
        trackingRunnable = new Runnable() {
            @Override public void run() { fetchLocationData(); handler.postDelayed(this, UPDATE_INTERVAL); }
        };
        handler.post(trackingRunnable);
    }

    private void fetchLocationData() {
        if (mMap == null) return;
        trackingApi.getAllCurrentLocations().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<LocationResponse> list = parseList(body.string(), LocationResponse.class);
                        mMap.clear();
                        LocationResponse primary = null;
                        LocationResponse demo = null;

                        for (LocationResponse loc : list) {
                            String vid = loc.getVehicleId();
                            boolean isMine = hasRealVehicle && vid.equalsIgnoreCase(selectedVehicleId);
                            boolean isDemo = vid.equalsIgnoreCase(DEMO_VEHICLE_ID);

                            if (isMine || isDemo) {
                                LatLng pos = new LatLng(loc.getLatitude(), loc.getLongitude());
                                if (pos.latitude != 0) {
                                    String name = isMine ? selectedVehicleName : "Demo Vehicle";
                                    mMap.addMarker(new MarkerOptions().position(pos).title(name));
                                    
                                    if (isMine) primary = loc;
                                    if (isDemo) demo = loc;
                                }
                            }
                        }

                        // Use demo if real car has no signal
                        if (primary == null) primary = demo;
                        if (primary != null) updateUI(primary);
                    }
                } catch (Exception e) {
                    Log.e("VehiclesActivity", "Error fetching location", e);
                }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("VehiclesActivity", "Failed to fetch location", t);
            }
        });
    }

    private void updateUI(LocationResponse loc) {
        LatLng pos = new LatLng(loc.getLatitude(), loc.getLongitude());
        if (pos.latitude == 0) return;
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));
        
        String nameToShow = loc.getVehicleId().equalsIgnoreCase(DEMO_VEHICLE_ID) ? "Demo Vehicle" : selectedVehicleName;
        if (tvVehicleNameCollapsed != null) tvVehicleNameCollapsed.setText(nameToShow);

        if (tvCollapsedAddress != null) tvCollapsedAddress.setText(String.format(Locale.getDefault(), "%.6f, %.6f", pos.latitude, pos.longitude));
        if (tvExpandedAddress != null) tvExpandedAddress.setText(String.format(Locale.getDefault(), "%.6f, %.6f", pos.latitude, pos.longitude));
        
        String status = loc.getSpeed() > 0 ? "Moving (" + (int)loc.getSpeed() + " km/h)" : (loc.isIgnitionOn() ? "Idle" : "Parked");
        int color = loc.getSpeed() > 0 ? Color.parseColor("#00BFA5") : Color.parseColor("#1976D2");
        if (tvCollapsedStatus != null) { tvCollapsedStatus.setText(status); tvCollapsedStatus.setTextColor(color); }
        if (tvExpandedStatus != null) { tvExpandedStatus.setText(status); tvExpandedStatus.setTextColor(color); }
        
        int dotColor = loc.isIgnitionOn() ? Color.parseColor("#4CAF50") : Color.parseColor("#E53935");
        if (dotIgnition != null) dotIgnition.setCardBackgroundColor(dotColor);
        if (dotAC != null) dotAC.setCardBackgroundColor(dotColor);

        if (tvLastUpdated != null) tvLastUpdated.setText(String.format(Locale.getDefault(), "Sync: %s", new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(new Date())));
    }

    private <T> List<T> parseList(String json, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        Gson gson = new Gson();
        String trimmed = json.trim();
        try {
            if (trimmed.startsWith("[")) {
                list = gson.fromJson(trimmed, TypeToken.getParameterized(List.class, clazz).getType());
            } else if (trimmed.startsWith("{")) {
                list.add(gson.fromJson(trimmed, clazz));
            }
        } catch (Exception e) {
            Log.e("VehiclesActivity", "Error parsing list JSON", e);
        }
        return list;
    }

    @Override protected void onDestroy() { super.onDestroy(); if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable); }
}
