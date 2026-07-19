package com.example.letstracklanka.ui.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
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
import androidx.core.view.GravityCompat;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateDeviceAssignmentRequest;
import com.example.letstracklanka.data.model.CreateVehicleRequest;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.DashboardResponse;
import com.example.letstracklanka.data.model.GpsDeviceResponse;
import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.ui.auth.LoginActivity;
import com.example.letstracklanka.ui.vehicles.VehiclesActivity;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int UPDATE_INTERVAL = 10000;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private ShaloTrackApi trackingApi;
    private ApiService mainApiService;
    private final Handler handler = new Handler();
    private Runnable trackingRunnable;

    private String currentCustomerId = null;
    private final Map<String, String> myVehicles = new HashMap<>();
    private final Map<String, Marker> mapMarkers = new HashMap<>();
    private LatLng myCurrentLocation = null;

    private TextView tvDeviceStatus, tvDeviceAddress, tvDeviceName;
    private MaterialCardView cardDefault, cardTerrain, cardSatellite, cardHybrid;
    private View mapTypeMenu;

    // Drawer Variables
    private DrawerLayout drawerLayout;
    private TextView tvDrawerName, tvDrawerPhone, tvDrawerEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);
        mainApiService = ApiClient.getClient().create(ApiService.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        setupUI();
        enableMyLocation();

        startRealTimeTracking();
        loadUserData();

        // ---------------------------------------------------------
        // අනිත් ස්ක්‍රීන් වලින් එන සිග්නල් එක අඳුරගෙන ඉබේම Menu එක අරින්න
        // ---------------------------------------------------------
        if (getIntent().getBooleanExtra("open_drawer", false)) {
            if (drawerLayout != null) {
                // UI එක සම්පූර්ණයෙන්ම ලෝඩ් වුණාට පස්සේ Drawer එක අරින්න
                drawerLayout.post(() -> drawerLayout.openDrawer(GravityCompat.START));
            }
        }
    }

    private void initViews() {
        // Drawer initialization
        drawerLayout = findViewById(R.id.drawerLayout);
        tvDrawerName = findViewById(R.id.tvDrawerName);
        tvDrawerPhone = findViewById(R.id.tvDrawerPhone);
        tvDrawerEmail = findViewById(R.id.tvDrawerEmail);

        // Log out button in the drawer
        TextView tvLogOut = findViewById(R.id.tvLogOut);
        if(tvLogOut != null) {
            tvLogOut.setOnClickListener(v -> {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }

        View bottomSheetView = findViewById(R.id.bottomSheet);
        if (bottomSheetView != null) {
            tvDeviceName = bottomSheetView.findViewById(R.id.tvDeviceName);
            tvDeviceStatus = bottomSheetView.findViewById(R.id.tvDeviceStatus);
            tvDeviceAddress = bottomSheetView.findViewById(R.id.tvDeviceAddress);
        }

        cardDefault = findViewById(R.id.cardDefault);
        cardTerrain = findViewById(R.id.cardTerrain);
        cardSatellite = findViewById(R.id.cardSatellite);
        cardHybrid = findViewById(R.id.cardHybrid);
        mapTypeMenu = findViewById(R.id.mapTypeMenu);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        // ---------------------------------------------------------
        // මෙන්න Edit Profile Pen Icon එක ක්ලික් කළාම වෙන දේ කේතය
        // ---------------------------------------------------------
        ImageView ivEditProfileMenu = findViewById(R.id.ivEditProfileMenu);
        if (ivEditProfileMenu != null) {
            ivEditProfileMenu.setOnClickListener(v -> {
                // Drawer එක close කරලා Bottom Sheet එක අරින්න
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showEditProfileBottomSheet();
            });
        }
    }

    private void setupUI() {
        NestedScrollView bottomSheet = findViewById(R.id.bottomSheet);
        if (bottomSheet != null) BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_COLLAPSED);

        FloatingActionButton fabLayers = findViewById(R.id.fabLayers);
        if (fabLayers != null) {
            fabLayers.setOnClickListener(v -> {
                if (mapTypeMenu != null) {
                    if (mapTypeMenu.getVisibility() == View.VISIBLE) {
                        mapTypeMenu.setVisibility(View.GONE);
                    } else {
                        mapTypeMenu.setVisibility(View.VISIBLE);
                    }
                }
            });
        }

        findViewById(R.id.nav_vehicles).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, VehiclesActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        View btnAddVehicle = findViewById(R.id.btnAddVehicle);
        if (btnAddVehicle != null) {
            btnAddVehicle.setOnClickListener(v -> showAddVehicleDialog());
        }

        if (cardDefault != null) cardDefault.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_NORMAL, cardDefault));
        if (cardTerrain != null) cardTerrain.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_TERRAIN, cardTerrain));
        if (cardSatellite != null) cardSatellite.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_SATELLITE, cardSatellite));
        if (cardHybrid != null) cardHybrid.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_HYBRID, cardHybrid));

        FloatingActionButton fabLocation = findViewById(R.id.fabLocation);
        if (fabLocation != null) fabLocation.setOnClickListener(v -> getPhoneLocation());

        MaterialButton btnHomeSOS = findViewById(R.id.btnSOS);
        if (btnHomeSOS != null) btnHomeSOS.setOnClickListener(v -> showSOSBottomSheet());

        LinearLayout bottomNavBar = findViewById(R.id.bottomNavBar);
        if (bottomNavBar != null) {
            View navTags = findViewById(R.id.nav_tags);
            if (navTags != null) {
                navTags.setOnClickListener(v -> {
                    Intent intent = new Intent(HomeActivity.this, TagsActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }

            View navCircles = findViewById(R.id.nav_circles);
            if (navCircles != null) {
                navCircles.setOnClickListener(v -> {
                    Intent intent = new Intent(HomeActivity.this, CirclesActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }

            View navAlerts = findViewById(R.id.nav_alerts);
            if (navAlerts != null) {
                navAlerts.setOnClickListener(v -> {
                    Intent intent = new Intent(HomeActivity.this, AlertsActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }

            View navMenu = findViewById(R.id.nav_menu);
            if (navMenu != null) {
                navMenu.setOnClickListener(v -> {
                    if (drawerLayout != null) {
                        drawerLayout.openDrawer(GravityCompat.START);
                    }
                });
            }
        }
    }

    // ---------------------------------------------------------
    // මෙන්න අලුත් Edit Profile Bottom Sheet එක ඕපන් කරන Method එක
    // ---------------------------------------------------------
    private void showEditProfileBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_edit_profile, null);
        dialog.setContentView(view);

        // Views හොයාගැනීම
        ImageView btnClose = view.findViewById(R.id.btnCloseEditProfile);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveProfile);

        EditText etFirstName = view.findViewById(R.id.etFirstName);
        EditText etSurname = view.findViewById(R.id.etSurname);
        EditText etPhone = view.findViewById(R.id.etPhone);
        EditText etEmail = view.findViewById(R.id.etEmail);

        // දැනට Drawer එකේ තියෙන විස්තර මේකට සෙට් කිරීම
        if (tvDrawerName != null) {
            String fullName = tvDrawerName.getText().toString();
            String[] nameParts = fullName.split(" ");
            if (nameParts.length > 0) etFirstName.setText(nameParts[0]);
            if (nameParts.length > 1) {
                StringBuilder surname = new StringBuilder();
                for (int i = 1; i < nameParts.length; i++) {
                    surname.append(nameParts[i]).append(" ");
                }
                etSurname.setText(surname.toString().trim());
            }
        }
        if (tvDrawerPhone != null) etPhone.setText(tvDrawerPhone.getText().toString());
        if (tvDrawerEmail != null) etEmail.setText(tvDrawerEmail.getText().toString());

        // Close button action
        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Save button action (දැනට බොත්තම එබුවම වැහෙන විදිහට හදලා තියෙන්නේ. පස්සේ API එකට Data යවන්න පුළුවන්)
        btnSave.setOnClickListener(v -> {
            Toast.makeText(this, "Profile Saved Successfully!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        // Bottom sheet එක ෆුල් ස්ක්‍රීන් පේන්න (Keyboard එක ආවම අවුල් නොයන්න)
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.show();
    }
    // ---------------------------------------------------------

    private void showCallCenterBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_call_center, null);
        dialog.setContentView(view);

        ViewPager2 viewPager = view.findViewById(R.id.viewPagerCallCenter);
        if (viewPager != null) {
            viewPager.setAdapter(new com.example.letstracklanka.ui.vehicles.CallCenterPagerAdapter());
        }

        ImageView btnClose = view.findViewById(R.id.btnCloseCallCenter);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        View btnCloseBottom = view.findViewById(R.id.btnCallCenterClose);
        if (btnCloseBottom != null) btnCloseBottom.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
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
                    } else {
                        Toast.makeText(HomeActivity.this,
                                "Could not check device registry (code " + response.code() + ")",
                                Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Log.e("HomeActivity", "Error finding device", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(HomeActivity.this, "Registry Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createVehicle(String vNum, String chassis, String engine, String make, String model,
                               int year, String color, String type, String fuel, String deviceId) {
        if (currentCustomerId == null) {
            Toast.makeText(this, "Your profile isn't loaded yet. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }
        CreateVehicleRequest request = new CreateVehicleRequest(currentCustomerId, vNum, chassis, engine, make, model, year, color, type, fuel);
        mainApiService.createVehicle(request).enqueue(new Callback<VehicleResponse>() {
            @Override
            public void onResponse(@NonNull Call<VehicleResponse> call, @NonNull Response<VehicleResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    assignDeviceToVehicle(response.body().getVehicleId(), deviceId);
                } else {
                    Toast.makeText(HomeActivity.this,
                            "Could not create vehicle (code " + response.code() + ")",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<VehicleResponse> call, @NonNull Throwable t) {
                Toast.makeText(HomeActivity.this, "Network error creating vehicle", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void assignDeviceToVehicle(String vehicleId, String deviceId) {
        mainApiService.assignDevice(new CreateDeviceAssignmentRequest(vehicleId, deviceId)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(HomeActivity.this, "Vehicle Linked to DB!", Toast.LENGTH_LONG).show();
                    loadUserData();
                } else {
                    Toast.makeText(HomeActivity.this,
                            "Could not assign device (code " + response.code() + ")",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(HomeActivity.this, "Network error assigning device", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSOSBottomSheet() {
        BottomSheetDialog sosDialog = new BottomSheetDialog(this);
        View sosView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_sos, null);
        sosDialog.setContentView(sosView);

        ImageView btnClose = sosView.findViewById(R.id.btnCloseSOS);
        LinearLayout btnTapSOS = sosView.findViewById(R.id.btnTapSOS);

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
        sosDialog.show();
    }

    private void startRealTimeTracking() {
        if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable);
        trackingRunnable = new Runnable() {
            @Override
            public void run() {
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
                    if (!response.isSuccessful() || body == null) return;

                    Gson gson = new Gson();
                    JsonObject root = gson.fromJson(body.string(), JsonObject.class);
                    if (root == null || !root.has("data") || root.get("data").isJsonNull()) return;

                    JsonObject data = root.getAsJsonObject("data");
                    if (!data.has("vehicles") || !data.get("vehicles").isJsonArray()) return;

                    for (com.google.gson.JsonElement el : data.getAsJsonArray("vehicles")) {
                        JsonObject v = el.getAsJsonObject();
                        if (!v.has("vehicleId") || v.get("vehicleId").isJsonNull()) continue;

                        String vehicleId = v.get("vehicleId").getAsString().toLowerCase();
                        String make = v.has("make") && !v.get("make").isJsonNull() ? v.get("make").getAsString() : "";
                        String model = v.has("model") && !v.get("model").isJsonNull() ? v.get("model").getAsString() : "";
                        myVehicles.put(vehicleId, (make + " " + model).trim());

                        boolean hasLocation = v.has("latitude") && !v.get("latitude").isJsonNull()
                                && v.has("longitude") && !v.get("longitude").isJsonNull();
                        if (hasLocation && mMap != null) {
                            double lat = v.get("latitude").getAsDouble();
                            double lng = v.get("longitude").getAsDouble();
                            if (lat != 0 || lng != 0) {
                                updateMarker(vehicleId, new LatLng(lat, lng), myVehicles.get(vehicleId));
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e("HomeActivity", "Dashboard error", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
            }
        });
    }

    private void fetchLocation() {
        if (myVehicles.isEmpty()) {
            return;
        }

        for (String vehicleId : myVehicles.keySet()) {
            trackingApi.getVehicleLocation(vehicleId).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    try (ResponseBody body = response.body()) {
                        if (response.isSuccessful() && body != null && mMap != null) {
                            String json = body.string();
                            LocationResponse loc = extractLocation(json);
                            if (loc != null && loc.getVehicleId() != null) {
                                LatLng pos = new LatLng(loc.getLatitude(), loc.getLongitude());
                                if (pos.latitude != 0 || pos.longitude != 0) {
                                    String title = myVehicles.getOrDefault(vehicleId, "My Vehicle");
                                    updateMarker(vehicleId, pos, title);
                                    updateUI(loc);
                                }
                            }
                        } else if (response.code() == 404) {
                            Log.d("HomeActivity", "No current location yet for vehicle " + vehicleId);
                        }
                    } catch (Exception e) {
                        Log.e("HomeActivity", "Location parse error for " + vehicleId, e);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    Log.e("HomeActivity", "Location fetch failed for " + vehicleId, t);
                }
            });
        }
    }

    private LocationResponse extractLocation(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), LocationResponse.class);
            }
            return gson.fromJson(json, LocationResponse.class);
        } catch (Exception e) {
            Log.e("HomeActivity", "extractLocation parse error", e);
            return null;
        }
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
        String name = myVehicles.getOrDefault(vid, "My Vehicle");
        if (tvDeviceName != null) tvDeviceName.setText(name);
        if (tvDeviceAddress != null) tvDeviceAddress.setText(String.format(Locale.getDefault(), "%.6f, %.6f", loc.getLatitude(), loc.getLongitude()));
        if (tvDeviceStatus != null) {
            tvDeviceStatus.setText(loc.getSpeed() > 0 ? "Moving (" + (int) loc.getSpeed() + " km/h)" : (loc.isIgnitionOn() ? "Idle" : "Parked"));
            tvDeviceStatus.setTextColor(loc.getSpeed() > 0 ? Color.parseColor("#00BFA5") : Color.parseColor("#1877F2"));
        }
    }

    private void loadUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        mainApiService.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        String json = body.string();

                        // Drawer Menu එකට විස්තර සෙට් කිරීම
                        try {
                            Gson gson = new Gson();
                            JsonObject root = gson.fromJson(json, JsonObject.class);
                            JsonObject data = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : root;

                            String name = data.has("name") && !data.get("name").isJsonNull() ? data.get("name").getAsString() :
                                    (data.has("fullName") && !data.get("fullName").isJsonNull() ? data.get("fullName").getAsString() : "Unknown User");

                            String phone = data.has("phone") && !data.get("phone").isJsonNull() ? data.get("phone").getAsString() :
                                    (data.has("phoneNumber") && !data.get("phoneNumber").isJsonNull() ? data.get("phoneNumber").getAsString() : "No Phone Number");

                            String email = data.has("email") && !data.get("email").isJsonNull() ? data.get("email").getAsString() : "No Email";

                            if(tvDrawerName != null) tvDrawerName.setText(name);
                            if(tvDrawerPhone != null) tvDrawerPhone.setText(phone);
                            if(tvDrawerEmail != null) tvDrawerEmail.setText(email);
                        } catch(Exception e) {
                            Log.e("HomeActivity", "Drawer UI update error", e);
                        }

                        CustomerResponse customer = extractCustomer(json);
                        if (customer != null && customer.getCustomerId() != null) {
                            currentCustomerId = customer.getCustomerId();
                            fetchMyVehicles();
                            fetchDashboard();
                        }
                    } else if (response.code() == 404) {
                        Log.w("HomeActivity", "No customer profile exists yet for this account.");
                    } else {
                        Log.w("HomeActivity", "getMyProfile failed with code " + response.code());
                    }
                } catch (Exception e) {
                    Log.e("HomeActivity", "loadUserData parse error", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("HomeActivity", "loadUserData network error", t);
            }
        });
    }

    private CustomerResponse extractCustomer(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), CustomerResponse.class);
            }
            return gson.fromJson(json, CustomerResponse.class);
        } catch (Exception e) {
            Log.e("HomeActivity", "extractCustomer parse error", e);
            return null;
        }
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
                } catch (Exception e) {
                    Log.e("HomeActivity", "fetchMyVehicles parse error", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
            }
        });
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (mMap != null) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
            }
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

    private void getPhoneLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    LatLng pos = new LatLng(location.getLatitude(), location.getLongitude());
                    if (mMap != null) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));
                }
            });
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(6.9271, 79.8612), 10f));
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
            if (mapTypeMenu != null) mapTypeMenu.setVisibility(View.GONE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        }
    }

    private <T> List<T> parseList(String json, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        Gson gson = new Gson();
        String trimmed = json.trim();
        try {
            JsonObject maybeEnvelope = null;
            try {
                maybeEnvelope = trimmed.startsWith("{") ? gson.fromJson(trimmed, JsonObject.class) : null;
            } catch (Exception ignored) {
            }

            if (maybeEnvelope != null && maybeEnvelope.has("data") && maybeEnvelope.get("data").isJsonArray()) {
                list = gson.fromJson(maybeEnvelope.getAsJsonArray("data"), TypeToken.getParameterized(List.class, clazz).getType());
            } else if (trimmed.startsWith("[")) {
                list = gson.fromJson(trimmed, TypeToken.getParameterized(List.class, clazz).getType());
            } else if (trimmed.startsWith("{")) {
                list.add(gson.fromJson(trimmed, clazz));
            }
        } catch (Exception e) {
            Log.e("HomeActivity", "Parse error", e);
        }
        return list;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable);
    }
}