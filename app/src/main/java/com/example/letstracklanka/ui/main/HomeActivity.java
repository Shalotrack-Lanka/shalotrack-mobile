package com.example.letstracklanka.ui.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.widget.NestedScrollView;

import com.example.letstracklanka.data.model.CreateDeviceAssignmentRequest;
import com.example.letstracklanka.data.model.CreateVehicleRequest;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.DashboardResponse;
import com.example.letstracklanka.data.model.GpsDeviceResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.R;
import com.example.letstracklanka.ui.vehicles.VehiclesActivity;
import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private ShaloTrackApi trackingApi;
    private ApiService mainApiService;
    private final Handler handler = new Handler();
    private Runnable trackingRunnable;
    private final int UPDATE_INTERVAL = 5000;
    
    private static final String DEMO_VEHICLE_ID = "39019073-09b8-4dbc-b5f9-6d7ade5ec4df";

    private String currentCustomerId = null;
    private final Map<String, String> myVehicles = new HashMap<>(); 
    private final Map<String, Marker> mapMarkers = new HashMap<>();

    private TextView tvDeviceStatus, tvDeviceAddress, tvDeviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);
        mainApiService = ApiClient.getClient().create(ApiService.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        setupUI();
        
        // --- START TRACKING IMMEDIATELY ---
        startRealTimeTracking();
        
        // --- LOAD USER DATA ---
        loadUserData();
    }

    private void initViews() {
        View bottomSheetView = findViewById(R.id.bottomSheet);
        if(bottomSheetView != null) {
            tvDeviceName = bottomSheetView.findViewById(R.id.tvDeviceName);
            tvDeviceStatus = bottomSheetView.findViewById(R.id.tvDeviceStatus);
            tvDeviceAddress = bottomSheetView.findViewById(R.id.tvDeviceAddress);
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    private void setupUI() {
        NestedScrollView bottomSheet = findViewById(R.id.bottomSheet);
        if(bottomSheet != null) BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_COLLAPSED);
        
        findViewById(R.id.nav_vehicles).setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, VehiclesActivity.class));
            overridePendingTransition(0, 0);
        });

        // RESTORED: Add Vehicle Button Logic
        View btnAddVehicle = findViewById(R.id.btnAddVehicle);
        if (btnAddVehicle != null) {
            btnAddVehicle.setOnClickListener(v -> showAddVehicleDialog());
        }

        findViewById(R.id.fabLocation).setOnClickListener(v -> getPhoneLocation());
    }

    private void showAddVehicleDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_vehicle, null);
        EditText etVehicleNumber = dialogView.findViewById(R.id.etVehicleNumber);
        EditText etMake = dialogView.findViewById(R.id.etMake);
        EditText etModel = dialogView.findViewById(R.id.etModel);
        EditText etYear = dialogView.findViewById(R.id.etYear);
        EditText etChassis = dialogView.findViewById(R.id.etChassisNumber);
        EditText etEngine = dialogView.findViewById(R.id.etEngineNumber);
        EditText etColor = dialogView.findViewById(R.id.etColor);
        EditText etType = dialogView.findViewById(R.id.etVehicleType);
        EditText etFuel = dialogView.findViewById(R.id.etFuelType);
        EditText etImei = dialogView.findViewById(R.id.etImei);

        new AlertDialog.Builder(this)
                .setTitle("Register My GPS Vehicle")
                .setView(dialogView)
                .setPositiveButton("Link Device", (dialog, which) -> {
                    String vNum = etVehicleNumber.getText().toString().trim();
                    String make = etMake.getText().toString().trim();
                    String model = etModel.getText().toString().trim();
                    String yearStr = etYear.getText().toString().trim();
                    String imei = etImei.getText().toString().trim();
                    String chassis = etChassis.getText().toString().trim();
                    String engine = etEngine.getText().toString().trim();
                    String color = etColor.getText().toString().trim();
                    String type = etType.getText().toString().trim();
                    String fuel = etFuel.getText().toString().trim();

                    if (vNum.isEmpty() || imei.isEmpty()) {
                        Toast.makeText(this, "Vehicle Number and IMEI are required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int year = yearStr.isEmpty() ? 2024 : Integer.parseInt(yearStr);
                    
                    Toast.makeText(this, "Linking Hardware...", Toast.LENGTH_SHORT).show();
                    processVehicleAddition(vNum, chassis, engine, make, model, year, color, type, fuel, imei);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void processVehicleAddition(String vNum, String chassis, String engine, String make, String model, 
                                       int year, String color, String type, String fuel, String imei) {
        mainApiService.getGpsDevices().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<GpsDeviceResponse> devices = parseList(body.string(), GpsDeviceResponse.class);
                        GpsDeviceResponse targetDevice = null;
                        for (GpsDeviceResponse device : devices) {
                            if (imei.equalsIgnoreCase(device.getImeiNumber())) {
                                targetDevice = device;
                                break;
                            }
                        }
                        if (targetDevice != null) {
                            createVehicle(vNum, chassis, engine, make, model, year, color, type, fuel, targetDevice.getDeviceId());
                        } else {
                            Toast.makeText(HomeActivity.this, "IMEI not found in registry", Toast.LENGTH_LONG).show();
                        }
                    }
                } catch (Exception e) {
                    Log.e("HomeActivity", "Error finding device", e);
                }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(HomeActivity.this, "Registry Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createVehicle(String vNum, String chassis, String engine, String make, String model, 
                              int year, String color, String type, String fuel, String deviceId) {
        if (currentCustomerId == null) return;
        CreateVehicleRequest request = new CreateVehicleRequest(currentCustomerId, vNum, chassis, engine, make, model, year, color, type, fuel);
        mainApiService.createVehicle(request).enqueue(new Callback<VehicleResponse>() {
            @Override
            public void onResponse(@NonNull Call<VehicleResponse> call, @NonNull Response<VehicleResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    assignDeviceToVehicle(response.body().getVehicleId(), deviceId);
                }
            }
            @Override public void onFailure(@NonNull Call<VehicleResponse> call, @NonNull Throwable t) { }
        });
    }

    private void assignDeviceToVehicle(String vehicleId, String deviceId) {
        mainApiService.assignDevice(new CreateDeviceAssignmentRequest(vehicleId, deviceId)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(HomeActivity.this, "Vehicle Linked to DB!", Toast.LENGTH_LONG).show();
                    loadUserData(); 
                }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {}
        });
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
                        if (currentCustomerId != null) fetchMyVehicles();
                    }
                } catch (Exception e) { }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {}
        });
    }

    private void fetchMyVehicles() {
        if (currentCustomerId == null) return;
        mainApiService.getVehiclesByCustomer(currentCustomerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<VehicleResponse> list = parseList(body.string(), VehicleResponse.class);
                        myVehicles.clear();
                        for (VehicleResponse v : list) {
                            myVehicles.put(v.getVehicleId().toLowerCase(), v.getMake() + " " + v.getModel());
                        }
                    }
                } catch (Exception e) { }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) { }
        });
    }

    private void startRealTimeTracking() {
        if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable);
        trackingRunnable = new Runnable() {
            @Override public void run() { 
                fetchLocation(); 
                if (currentCustomerId != null) fetchDashboard();
                handler.postDelayed(this, UPDATE_INTERVAL); 
            }
        };
        handler.post(trackingRunnable);
    }

    private void fetchDashboard() {
        if (currentCustomerId == null) return;
        mainApiService.getCustomerDashboard(currentCustomerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<DashboardResponse> dashboardItems = parseList(body.string(), DashboardResponse.class);
                        for (DashboardResponse item : dashboardItems) {
                            myVehicles.put(item.getVehicleId().toLowerCase(), item.getDisplayName());
                        }
                    }
                } catch (Exception e) {
                    Log.e("HomeActivity", "Dashboard error", e);
                }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {}
        });
    }

    private void fetchLocation() {
        trackingApi.getAllCurrentLocations().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null && mMap != null) {
                        List<LocationResponse> locations = parseList(body.string(), LocationResponse.class);
                        LocationResponse toShow = null;
                        for (LocationResponse loc : locations) {
                            String vid = loc.getVehicleId() != null ? loc.getVehicleId().toLowerCase() : "";
                            boolean isMine = myVehicles.containsKey(vid);
                            boolean isDemo = DEMO_VEHICLE_ID.equalsIgnoreCase(vid);
                            if (isDemo || isMine) {
                                LatLng pos = new LatLng(loc.getLatitude(), loc.getLongitude());
                                if (pos.latitude != 0) {
                                    updateMarker(vid, pos, isMine ? myVehicles.get(vid) : "Demo Vehicle");
                                    if (isMine) toShow = loc;
                                    else if (toShow == null) toShow = loc;
                                }
                            }
                        }
                        if (toShow != null) updateUI(toShow);
                    }
                } catch (Exception e) { }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) { }
        });
    }

    private void updateMarker(String id, LatLng pos, String title) {
        if (mMap == null) return;
        if (mapMarkers.containsKey(id)) {
            Marker m = mapMarkers.get(id);
            if (m != null) m.setPosition(pos);
        } else {
            Marker m = mMap.addMarker(new MarkerOptions().position(pos).title(title));
            mapMarkers.put(id, m);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 14f));
        }
    }

    private void updateUI(LocationResponse loc) {
        String vid = loc.getVehicleId().toLowerCase();
        String name = myVehicles.getOrDefault(vid, "Demo Vehicle");
        if (tvDeviceName != null) tvDeviceName.setText(name);
        if (tvDeviceAddress != null) tvDeviceAddress.setText(String.format(Locale.getDefault(), "%.6f, %.6f", loc.getLatitude(), loc.getLongitude()));
        if (tvDeviceStatus != null) {
            tvDeviceStatus.setText(loc.getSpeed() > 0 ? "Moving (" + (int)loc.getSpeed() + " km/h)" : (loc.isIgnitionOn() ? "Idle" : "Parked"));
            tvDeviceStatus.setTextColor(loc.getSpeed() > 0 ? Color.parseColor("#00BFA5") : Color.parseColor("#1877F2"));
        }
    }

    @Override public void onMapReady(@NonNull GoogleMap googleMap) { 
        mMap = googleMap; 
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(6.9271, 79.8612), 10f)); 
    }

    private void getPhoneLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 15f));
            });
        }
    }

    private <T> List<T> parseList(String json, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        Gson gson = new Gson();
        String trimmed = json.trim();
        try {
            if (trimmed.startsWith("[")) list = gson.fromJson(trimmed, TypeToken.getParameterized(List.class, clazz).getType());
            else if (trimmed.startsWith("{")) list.add(gson.fromJson(trimmed, clazz));
        } catch (Exception e) { Log.e("HomeActivity", "Parse error", e); }
        return list;
    }

    @Override protected void onDestroy() { super.onDestroy(); if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable); }
}
