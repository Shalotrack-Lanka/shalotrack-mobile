package com.example.letstracklanka.ui.main;

// Import all required libraries and classes
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.RegisterFcmTokenRequest;
import com.google.firebase.messaging.FirebaseMessaging;
import com.example.letstracklanka.data.model.DashboardResponse;
import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.ui.auth.LoginActivity;
import com.example.letstracklanka.ui.contacts.EmergencyContactsActivity;
import com.example.letstracklanka.ui.vehicles.VehiclesActivity;
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
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {
    // Timer interval for real-time tracking
    private static final int UPDATE_INTERVAL = 1000;

    // Variables for Map and APIs
    private GoogleMap mMap;
    private ShaloTrackApi trackingApi;
    private ApiService mainApiService;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable trackingRunnable;

    // Variables to store current user and vehicle data
    private String currentCustomerId = null;
    private CustomerResponse currentCustomer = null;
    private final Map<String, String> myVehicles = new HashMap<>();
    private final Map<String, Marker> mapMarkers = new HashMap<>();
    private LatLng lastVehiclePosition = null;

    // UI Components
    private RecyclerView recyclerHomeVehicles;
    private com.example.letstracklanka.ui.vehicles.VehicleListAdapter homeVehicleAdapter;
    private View errorBanner;
    private TextView tvErrorBannerMessage, tvErrorBannerRetry;
    private MaterialCardView cardDefault, cardTerrain, cardSatellite, cardHybrid;
    private View mapTypeMenu;
    private VehicleTrailRenderer trailRenderer;
    private RealtimeLocationClient realtimeClient;
    private AddressResolver addressResolver;
    private DrawerLayout drawerLayout;
    private TextView tvDrawerName, tvDrawerPhone, tvDrawerEmail;
    private com.google.android.material.button.MaterialButton btnSOS;
    // Hold-to-confirm state for SOS -- deliberately requires a sustained
    // press (not a single tap) so an accidental brush against the button
    // can't trigger a real emergency alert.
    private static final long SOS_HOLD_DURATION_MS = 3000;
    private CountDownTimer sosHoldTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ask for notification permissions if using Android 13 or higher
        requestNotificationPermissionIfNeeded();
        setContentView(R.layout.activity_home);

        // Initialize UI components for error messages
        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        tvErrorBannerRetry = findViewById(R.id.tvErrorBannerRetry);

        // Check internet connection
        registerNetworkMonitor();

        // Setup API clients
        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);
        addressResolver = new AddressResolver(this);
        mainApiService = ApiClient.getClient().create(ApiService.class);

        // Setup the screen layout and buttons
        initViews();
        setupUI();

        // Start running the background location checks
        startRealTimeTracking();
        loadUserData();

        // -------------------------------------------------------------------
        // Start Background Tracking Foreground Service
        // -------------------------------------------------------------------
        Intent serviceIntent = new Intent(this, com.example.letstracklanka.services.TrackingForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        // -------------------------------------------------------------------

        // Open the side menu automatically if requested by another screen
        handleOpenDrawerExtra(getIntent());
    }

    // FIX: VehiclesActivity/TagsActivity/AlertsActivity's Menu tab navigates
    // here with FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP -- when
    // HomeActivity is already in the back stack (the normal case when
    // tapping Menu from any tab other than Home itself), Android reuses the
    // existing instance via onNewIntent() instead of a fresh onCreate().
    // The open_drawer check only ever ran in onCreate(), so it silently
    // never fired for that reused-instance path -- exactly why this only
    // ever worked when already on Home.
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // so any other getIntent() calls elsewhere also see this new intent
        handleOpenDrawerExtra(intent);
    }

    private void handleOpenDrawerExtra(Intent intent) {
        if (intent != null && intent.getBooleanExtra("open_drawer", false)) {
            if (drawerLayout != null) {
                drawerLayout.post(() -> drawerLayout.openDrawer(GravityCompat.START));
            }
        }
    }

    // Connect XML views to Java variables
    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        tvDrawerName = findViewById(R.id.tvDrawerName);
        tvDrawerPhone = findViewById(R.id.tvDrawerPhone);
        tvDrawerEmail = findViewById(R.id.tvDrawerEmail);

        btnSOS = findViewById(R.id.btnSOS);
        if (btnSOS != null) {
            btnSOS.setOnClickListener(v -> showSOSBottomSheet());
        }

        // Logout, edit profile, and every other drawer item are now wired
        // by DrawerMenuHelper.wireDrawer() below, not here.
        // Setup the list of vehicles
        recyclerHomeVehicles = findViewById(R.id.recyclerHomeVehicles);
        if (recyclerHomeVehicles != null) {
            recyclerHomeVehicles.setLayoutManager(new LinearLayoutManager(this));
            homeVehicleAdapter = new com.example.letstracklanka.ui.vehicles.VehicleListAdapter(
                    new ArrayList<>(), this::onHomeVehicleSelected, this::confirmRemoveVehicleFromHome);
            recyclerHomeVehicles.setAdapter(homeVehicleAdapter);
        }

        // Map type selection buttons
        cardDefault = findViewById(R.id.cardDefault);
        cardTerrain = findViewById(R.id.cardTerrain);
        cardSatellite = findViewById(R.id.cardSatellite);
        cardHybrid = findViewById(R.id.cardHybrid);
        mapTypeMenu = findViewById(R.id.mapTypeMenu);

        // Load the Google Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        // Every drawer item's real behavior now lives in DrawerMenuHelper,
        // shared with Vehicles/Alerts/Tags/Circles so all five screens get
        // an identical, real, working drawer instead of duplicated logic
        // across each Activity.
        DrawerMenuHelper.wireDrawer(this, drawerLayout, tvDrawerName, tvDrawerPhone, tvDrawerEmail);
    }

    // Set up main screen buttons and bottom navigation
    private void setupUI() {
        NestedScrollView bottomSheet = findViewById(R.id.bottomSheet);
        if (bottomSheet != null) BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_COLLAPSED);

        // Open/Close Map Layers menu
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

        // Go to Vehicles Screen
        findViewById(R.id.nav_vehicles).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, VehiclesActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        View btnAddVehicle = findViewById(R.id.btnAddVehicle);
        if (btnAddVehicle != null) {
            btnAddVehicle.setOnClickListener(v -> {
                if (currentCustomerId == null) {
                    Toast.makeText(this, "Still loading your profile \u2014 try again in a moment.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(HomeActivity.this, com.example.letstracklanka.ui.vehicles.AddVehicleActivity.class);
                intent.putExtra(com.example.letstracklanka.ui.vehicles.AddVehicleActivity.EXTRA_CUSTOMER_ID, currentCustomerId);
                startActivity(intent);
            });
        }

        // Show coming soon message for future features
        int[] comingSoonAddIds = {R.id.btnAddPerson, R.id.btnAddPet, R.id.btnAddTag, R.id.btnAddPlace};
        for (int id : comingSoonAddIds) {
            View item = findViewById(id);
            if (item != null) {
                item.setOnClickListener(v -> Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show());
            }
        }

        // Handle map type changes (Normal, Satellite, etc.)
        if (cardDefault != null) cardDefault.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_NORMAL, cardDefault));
        if (cardTerrain != null) cardTerrain.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_TERRAIN, cardTerrain));
        if (cardSatellite != null) cardSatellite.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_SATELLITE, cardSatellite));
        if (cardHybrid != null) cardHybrid.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_HYBRID, cardHybrid));

        // Find vehicle location on map
        FloatingActionButton fabLocation = findViewById(R.id.fabLocation);
        if (fabLocation != null) fabLocation.setOnClickListener(v -> getPhoneLocation());

        // SOS Button
        MaterialButton btnHomeSOS = findViewById(R.id.btnSOS);
        if (btnHomeSOS != null) btnHomeSOS.setOnClickListener(v -> showSOSBottomSheet());

        // Share location via SMS/WhatsApp
        MaterialButton btnSendLocation = findViewById(R.id.btnSendLocation);
        if (btnSendLocation != null) {
            btnSendLocation.setOnClickListener(v -> {
                if (lastVehiclePosition == null) {
                    Toast.makeText(this, "Vehicle location not available yet, try again in a moment", Toast.LENGTH_SHORT).show();
                    return;
                }
                String locationLink = "https://www.google.com/maps?q=" + lastVehiclePosition.latitude + "," + lastVehiclePosition.longitude;
                String message = "Here is my vehicle's current location:\n" + locationLink;
                try {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, message);
                    startActivity(Intent.createChooser(shareIntent, "Share vehicle location via"));
                } catch (Exception e) {
                    Toast.makeText(this, "No app available to share location", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Refresh dashboard and location data manually
        FloatingActionButton fabRefresh = findViewById(R.id.fabRefresh);
        if (fabRefresh != null) {
            fabRefresh.setOnClickListener(v -> {
                Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show();
                fetchLocation();
                fetchDashboard();
            });
        }

        // Bottom Navigation Bar setup
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


    // Start a timer to get location and dashboard data continuously
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

    // Get latest dashboard data (e.g. speeds, status) for vehicles
    private void fetchDashboard() {
        if (currentCustomerId == null) return;
        mainApiService.getCustomerDashboard(currentCustomerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        Log.e("HomeActivity", "fetchDashboard failed, code " + response.code());
                        showRetryDialog("Couldn't load dashboard data", HomeActivity.this::fetchDashboard);
                        return;
                    }
                    hideErrorBanner();
                    Gson gson = new Gson();
                    JsonObject root = gson.fromJson(body.string(), JsonObject.class);
                    if (root == null || !root.has("data") || root.get("data").isJsonNull()) return;
                    JsonObject data = root.getAsJsonObject("data");
                    if (!data.has("vehicles") || !data.get("vehicles").isJsonArray()) return;

                    // Update the list of vehicles in the UI
                    if (homeVehicleAdapter != null) {
                        List<com.example.letstracklanka.data.model.DashboardVehicle> dashboardVehicles =
                                gson.fromJson(data.getAsJsonArray("vehicles"),
                                        com.google.gson.reflect.TypeToken.getParameterized(
                                                List.class, com.example.letstracklanka.data.model.DashboardVehicle.class).getType());
                        homeVehicleAdapter.updateVehicles(dashboardVehicles);
                    }

                    // Save vehicle names to memory
                    for (com.google.gson.JsonElement el : data.getAsJsonArray("vehicles")) {
                        JsonObject v = el.getAsJsonObject();
                        if (!v.has("vehicleId") || v.get("vehicleId").isJsonNull()) continue;
                        String vehicleId = v.get("vehicleId").getAsString().toLowerCase();
                        String make = v.has("make") && !v.get("make").isJsonNull() ? v.get("make").getAsString() : "";
                        String model = v.has("model") && !v.get("model").isJsonNull() ? v.get("model").getAsString() : "";
                        myVehicles.put(vehicleId, (make + " " + model).trim());
                    }
                } catch (Exception e) {
                    Log.e("HomeActivity", "Dashboard error", e);
                    showRetryDialog("Something went wrong loading dashboard data", HomeActivity.this::fetchDashboard);
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("HomeActivity", "fetchDashboard network error", t);
                showRetryDialog("Network error — couldn't load dashboard data", HomeActivity.this::fetchDashboard);
            }
        });
    }

    // Process live location updates when they arrive from WebSocket
    private void handlePushedLocation(RealtimeLocationPayload payload) {
        if (payload.getVehicleId() == null || mMap == null) return;
        if (!payload.getVehicleId().toLowerCase().equals(getSelectedVehicleId())) return;
        LatLng pos = new LatLng(payload.getLatitude(), payload.getLongitude());
        if (pos.latitude == 0 && pos.longitude == 0) return;

        lastVehiclePosition = pos;
        String title = myVehicles.getOrDefault(payload.getVehicleId().toLowerCase(), "My Vehicle");

        // Draw the vehicle trail and move the camera on the map
        trailRenderer.updatePosition(pos, (float) payload.getHeading(), title);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));
    }

    // When the user clicks on a vehicle in the list, save its ID and reload map
    private void onHomeVehicleSelected(com.example.letstracklanka.data.model.DashboardVehicle vehicle) {
        getSharedPreferences(
                com.example.letstracklanka.ui.vehicles.VehicleListActivity.VEHICLE_PREFS_NAME,
                Context.MODE_PRIVATE)
                .edit()
                .putString(com.example.letstracklanka.ui.vehicles.VehicleListActivity.SELECTED_VEHICLE_ID_KEY, vehicle.getVehicleId())
                .apply();
        fetchLocation();
    }

    // Show warning before completely removing a vehicle from the account
    private void confirmRemoveVehicleFromHome(com.example.letstracklanka.data.model.DashboardVehicle vehicle) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + vehicle.getVehicleNumber() + "?")
                .setMessage("This removes the vehicle from your account and frees its GPS device so it can be linked to a new vehicle. Trip history and alerts are kept. This can't be undone from the app.")
                .setPositiveButton("Remove", (dialog, which) -> {
                    mainApiService.deleteVehicle(vehicle.getVehicleId()).enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                            if (response.isSuccessful()) {
                                fetchDashboard();
                            } else {
                                showRetryDialog("Couldn't remove vehicle (code " + response.code() + ")", null);
                            }
                        }
                        @Override
                        public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                            showRetryDialog("Network error — couldn't remove vehicle", null);
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Get the ID of the currently selected vehicle from memory
    private String getSelectedVehicleId() {
        if (myVehicles.isEmpty()) return "";
        String selected = getSharedPreferences(
                com.example.letstracklanka.ui.vehicles.VehicleListActivity.VEHICLE_PREFS_NAME,
                android.content.Context.MODE_PRIVATE)
                .getString(com.example.letstracklanka.ui.vehicles.VehicleListActivity.SELECTED_VEHICLE_ID_KEY, null);
        if (selected != null && myVehicles.containsKey(selected.toLowerCase())) {
            return selected.toLowerCase();
        }
        return myVehicles.keySet().iterator().next();
    }

    // Call the server to get the current location of the selected vehicle
    private void fetchLocation() {
        if (myVehicles.isEmpty()) {
            return;
        }
        String vehicleId = getSelectedVehicleId();
        {
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
                                    lastVehiclePosition = pos;
                                    String title = myVehicles.getOrDefault(vehicleId, "My Vehicle");
                                    trailRenderer.updatePosition(pos, loc.getHeading(), title);
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

    // Read location details from the API response
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

    // Draw or move the vehicle icon on the map
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

    // Temporary empty function for UI updates
    private void updateUI(LocationResponse loc) {
    }

    // Display a permission box to show notifications on Android 13+
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }

    // Get a unique key from Firebase to send push notifications to this phone
    private void registerFcmToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w("HomeActivity", "Fetching FCM token failed", task.getException());
                return;
            }
            String token = task.getResult();
            mainApiService.registerFcmToken(new RegisterFcmTokenRequest(token, "android"))
                    .enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                            if (!response.isSuccessful()) {
                                Log.w("HomeActivity", "FCM token registration failed, code " + response.code());
                            }
                        }
                        @Override
                        public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                            Log.w("HomeActivity", "FCM token registration network error", t);
                        }
                    });
        });
    }

    // Get the current user's profile and start loading their vehicles
    private void loadUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        mainApiService.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        hideErrorBanner();
                        String json = body.string();

                        // Drawer header (name/phone/email) is now populated
                        // independently by DrawerMenuHelper.wireDrawer(),
                        // called once from initViews() -- no longer
                        // duplicated here.

                        CustomerResponse customer = extractCustomer(json);
                        if (customer != null && customer.getCustomerId() != null) {
                            currentCustomerId = customer.getCustomerId();
                            currentCustomer = customer;
                            registerFcmToken();
                            fetchMyVehicles();
                            fetchDashboard();
                        }
                    } else if (response.code() == 404) {
                        Log.w("HomeActivity", "No customer profile exists yet for this account.");
                    } else {
                        Log.w("HomeActivity", "getMyProfile failed with code " + response.code());
                        showRetryDialog("Couldn't load your profile", HomeActivity.this::loadUserData);
                    }
                } catch (Exception e) {
                    Log.e("HomeActivity", "loadUserData parse error", e);
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("HomeActivity", "loadUserData network error", t);
                showRetryDialog("Network error — couldn't load your profile", HomeActivity.this::loadUserData);
            }
        });
    }

    // Helper functions to read user and common data safely
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
    private <T> T extractObject(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), clazz);
            }
            return gson.fromJson(json, clazz);
        } catch (Exception e) {
            Log.e("HomeActivity", "extractObject parse error", e);
            return null;
        }
    }

    // Get the list of vehicles assigned to the logged-in user
    private void fetchMyVehicles() {
        if (currentCustomerId == null) return;
        mainApiService.getVehiclesByCustomer(currentCustomerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        hideErrorBanner();
                        List<VehicleResponse> list = parseList(body.string(), VehicleResponse.class);
                        myVehicles.clear();

                        // Setup live tracking connections for each vehicle
                        for (VehicleResponse v : list) {
                            myVehicles.put(v.getVehicleId().toLowerCase(), v.getMake() + " " + v.getModel());
                            trailRenderer.loadInitialTrail(v.getVehicleId(), () -> {});
                            if (realtimeClient == null) {
                                realtimeClient = new RealtimeLocationClient();
                                realtimeClient.connect(v.getVehicleId(), payload ->
                                        runOnUiThread(() -> handlePushedLocation(payload)));
                            }
                        }
                        if (!myVehicles.isEmpty()) {
                            fetchLocation();
                        }
                    } else {
                        Log.e("HomeActivity", "fetchMyVehicles failed, code " + response.code());
                        showRetryDialog("Couldn't load your vehicles", HomeActivity.this::fetchMyVehicles);
                    }
                } catch (Exception e) {
                    Log.e("HomeActivity", "fetchMyVehicles parse error", e);
                    showRetryDialog("Something went wrong loading your vehicles", HomeActivity.this::fetchMyVehicles);
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("HomeActivity", "fetchMyVehicles network error", t);
                showRetryDialog("Network error — couldn't load your vehicles", HomeActivity.this::fetchMyVehicles);
            }
        });
    }

    // Show a red warning banner if something fails (e.g. No Internet)
    private void showRetryDialog(String message, Runnable retryAction) {
        if (errorBanner == null) return;
        tvErrorBannerMessage.setText(message);
        if (retryAction != null) {
            tvErrorBannerRetry.setVisibility(View.VISIBLE);
            tvErrorBannerRetry.setOnClickListener(v -> {
                hideErrorBanner();
                retryAction.run();
            });
        } else {
            tvErrorBannerRetry.setVisibility(View.GONE);
        }
        errorBanner.setVisibility(View.VISIBLE);
    }
    private void hideErrorBanner() {
        if (errorBanner != null) errorBanner.setVisibility(View.GONE);
    }

    // Opens the dedicated SOS bottom sheet (bottom_sheet_sos.xml). Tapping
    // the Home screen's own btnSOS just opens this -- reaching the sheet
    // is itself a deliberate action, not something that could happen by
    // accident, so no hold is required just to get here.
    private void showSOSBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_sos, null);
        dialog.setContentView(view);

        View btnClose = view.findViewById(R.id.btnCloseSOS);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        View btnAddContacts = view.findViewById(R.id.btnAddContacts);
        if (btnAddContacts != null) {
            btnAddContacts.setOnClickListener(v -> {
                dialog.dismiss();
                startActivity(new Intent(HomeActivity.this, EmergencyContactsActivity.class));
            });
        }

        // NOTE: btnUpgradeCallCenter is deliberately left unwired -- no
        // "24/7 emergency call center" feature exists anywhere in this
        // backend. Not guessing at what "Upgrade" should actually do here
        // until that's a real, scoped feature.

        View btnTapSOS = view.findViewById(R.id.btnTapSOS);
        View bgPulseCircle = view.findViewById(R.id.bgPulseCircle);
        if (btnTapSOS != null) {
            setupSOSHoldToConfirm(btnTapSOS, bgPulseCircle, dialog);
        }

        dialog.show();
    }

    // Hold-to-confirm: same 3-second requirement whether the user taps
    // repeatedly or holds steadily -- there's no faster path via a quick
    // tap, "(or press and hold)" in the sheet's own text just describes
    // two names for the same gesture. Progress is shown by scaling/
    // fading the existing pulse circle behind the button, NOT by
    // changing the button's own text -- keeps "Tap to send SOS" / "(or
    // press and hold)" visible and unchanged throughout the hold.
    @SuppressLint("ClickableViewAccessibility")
    private void setupSOSHoldToConfirm(View btnTapSOS, View bgPulseCircle, BottomSheetDialog dialog) {
        btnTapSOS.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    if (sosHoldTimer != null) sosHoldTimer.cancel();
                    sosHoldTimer = new CountDownTimer(SOS_HOLD_DURATION_MS, 50) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            if (bgPulseCircle == null) return;
                            float progress = 1f - ((float) millisUntilFinished / SOS_HOLD_DURATION_MS);
                            float scale = 1f + (progress * 0.25f); // grows up to 25% larger
                            bgPulseCircle.setScaleX(scale);
                            bgPulseCircle.setScaleY(scale);
                            bgPulseCircle.setAlpha(1f - (progress * 0.5f)); // fades as it grows, standard pulse feel
                        }

                        @Override
                        public void onFinish() {
                            resetPulseCircle(bgPulseCircle);
                            dialog.dismiss();
                            triggerSOS();
                        }
                    };
                    sosHoldTimer.start();
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (sosHoldTimer != null) {
                        sosHoldTimer.cancel();
                        sosHoldTimer = null;
                    }
                    resetPulseCircle(bgPulseCircle);
                    return true;
                default:
                    return false;
            }
        });
    }

    private void resetPulseCircle(View bgPulseCircle) {
        if (bgPulseCircle == null) return;
        bgPulseCircle.setScaleX(1f);
        bgPulseCircle.setScaleY(1f);
        bgPulseCircle.setAlpha(1f);
    }

    // Real API call -- POST /api/SOS/{vehicleId}/trigger. Uses the same
    // getSelectedVehicleId() already established elsewhere in this file,
    // not a separate tracking mechanism.
    private void triggerSOS() {
        String vehicleId = getSelectedVehicleId();
        if (vehicleId == null || vehicleId.isEmpty()) {
            Toast.makeText(this, "No vehicle selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        mainApiService.triggerSOS(vehicleId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(HomeActivity.this, "SOS sent.", Toast.LENGTH_SHORT).show();
                    showEmergencyContactsAfterSOS();
                } else {
                    Log.w("HomeActivity", "triggerSOS failed, code " + response.code());
                    Toast.makeText(HomeActivity.this, "Couldn't send SOS. Please try again.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("HomeActivity", "triggerSOS network error", t);
                Toast.makeText(HomeActivity.this, "Network error \u2014 SOS could not be sent. Try again.", Toast.LENGTH_LONG).show();
            }
        });
    }

    // Real, interactive redesign -- previous version was a plain
    // AlertDialog with unstyled text rows that technically had a click
    // listener but gave zero visual indication of being tappable,
    // confirmed directly by the user as not usable in a real emergency.
    //
    // National numbers are limited to 119 (Police) and 1990 (Suwa Seriya
    // Ambulance) deliberately -- these are the only two confirmed
    // unambiguously consistent across every source checked, including
    // Sri Lanka Police's own site and Wikipedia's citation of the actual
    // Telecommunications Regulatory Commission registry. Fire/rescue
    // numbers conflicted across sources (110 vs 111 depending on the
    // source) and were deliberately left out rather than risk a wrong
    // number in a genuine emergency -- worth getting verified locally
    // before adding.
    private void showEmergencyContactsAfterSOS() {
        mainApiService.getMyEmergencyContacts().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                List<com.example.letstracklanka.data.model.EmergencyContactResponse> contacts = new java.util.ArrayList<>();
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<com.example.letstracklanka.data.model.EmergencyContactResponse> parsed =
                                parseList(body.string(), com.example.letstracklanka.data.model.EmergencyContactResponse.class);
                        if (parsed != null) contacts = parsed;
                    } else {
                        Log.w("HomeActivity", "showEmergencyContactsAfterSOS failed, code " + response.code());
                    }
                } catch (Exception e) {
                    Log.e("HomeActivity", "showEmergencyContactsAfterSOS parse error", e);
                }
                // Show the sheet regardless -- national emergency numbers
                // are still useful even if the personal-contacts fetch
                // failed, and SOS itself already succeeded either way.
                showSosContactsSheet(contacts);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("HomeActivity", "showEmergencyContactsAfterSOS network error", t);
                // Still show national numbers even if the personal
                // contacts fetch fails outright over the network.
                showSosContactsSheet(new java.util.ArrayList<>());
            }
        });
    }

    private void showSosContactsSheet(List<com.example.letstracklanka.data.model.EmergencyContactResponse> contacts) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_sos_contacts, null);
        dialog.setContentView(view);

        LinearLayout nationalContainer = view.findViewById(R.id.nationalNumbersContainer);
        LinearLayout personalContainer = view.findViewById(R.id.personalContactsContainer);
        View tvNoContacts = view.findViewById(R.id.tvNoContacts);

        if (nationalContainer != null) {
            addSosRow(nationalContainer, "Police Emergency", "119", "119",
                    "P", ContextCompat.getColor(this, R.color.status_danger));
            addSosRow(nationalContainer, "Ambulance (Suwa Seriya)", "1990", "1990",
                    "A", ContextCompat.getColor(this, R.color.status_danger));
        }

        if (personalContainer != null) {
            if (contacts.isEmpty()) {
                if (tvNoContacts != null) tvNoContacts.setVisibility(View.VISIBLE);
            } else {
                for (com.example.letstracklanka.data.model.EmergencyContactResponse contact : contacts) {
                    String initials = contact.getName() != null && !contact.getName().isEmpty()
                            ? contact.getName().substring(0, 1).toUpperCase(java.util.Locale.US) : "?";
                    addSosRow(personalContainer, contact.getName(), contact.getPhoneNumber(),
                            contact.getPhoneNumber(), initials, ContextCompat.getColor(this, R.color.brand_accent));
                }
            }
        }

        dialog.show();
    }

    // Shared row builder for both national numbers and personal contacts
    // -- avatar/initials circle, name + number, and a large, unmistakably
    // tappable call-icon circle. Every row is a real ripple-enabled
    // clickable target, not plain text with an invisible listener.
    private void addSosRow(LinearLayout container, String title, String subtitle,
                           String phoneNumberToDial, String avatarLetter, int avatarColor) {
        int density = (int) getResources().getDisplayMetrics().density;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(8 * density, 10 * density, 8 * density, 10 * density);
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setClickable(true);
        row.setFocusable(true);

        LinearLayout avatar = new LinearLayout(this);
        int avatarSize = 42 * density;
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(avatarSize, avatarSize);
        avatar.setLayoutParams(avatarParams);
        avatar.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable avatarBg = new android.graphics.drawable.GradientDrawable();
        avatarBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        avatarBg.setColor(avatarColor);
        avatar.setBackground(avatarBg);
        TextView avatarText = new TextView(this);
        avatarText.setText(avatarLetter);
        avatarText.setTextColor(Color.WHITE);
        avatarText.setTextSize(16);
        avatarText.setTypeface(null, android.graphics.Typeface.BOLD);
        avatar.addView(avatarText);
        row.addView(avatar);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(14 * density);
        textCol.setLayoutParams(textParams);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        textCol.addView(titleView);
        textCol.addView(subtitleView);
        row.addView(textCol);

        LinearLayout callButton = new LinearLayout(this);
        int callSize = 42 * density;
        LinearLayout.LayoutParams callParams = new LinearLayout.LayoutParams(callSize, callSize);
        callButton.setLayoutParams(callParams);
        callButton.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable callBg = new android.graphics.drawable.GradientDrawable();
        callBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        callBg.setColor(ContextCompat.getColor(this, R.color.status_success));
        callButton.setBackground(callBg);
        ImageView callIcon = new ImageView(this);
        callIcon.setImageResource(R.drawable.ic_phone_call);
        int iconSize = 20 * density;
        callButton.addView(callIcon, new LinearLayout.LayoutParams(iconSize, iconSize));
        row.addView(callButton);

        row.setOnClickListener(v -> {
            Intent dialIntent = new Intent(Intent.ACTION_DIAL);
            dialIntent.setData(android.net.Uri.parse("tel:" + phoneNumberToDial));
            startActivity(dialIntent);
        });

        container.addView(row);
    }

    // Move camera to the latest known vehicle location when floating button is clicked
    private void getPhoneLocation() {
        if (lastVehiclePosition != null && mMap != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(lastVehiclePosition, 15f));
        } else {
            Toast.makeText(this, "Vehicle location not available yet", Toast.LENGTH_SHORT).show();
        }
    }

    // This runs when Google Maps is fully loaded on the screen
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        trailRenderer = new VehicleTrailRenderer(this, mMap, trackingApi);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(6.9271, 79.8612), 10f)); // Start in Colombo

        // Restore the last used Map type (Satellite, Terrain, etc.)
        int savedMapType = getSharedPreferences(MAP_PREFS_NAME, MODE_PRIVATE)
                .getInt(MAP_TYPE_PREF_KEY, GoogleMap.MAP_TYPE_NORMAL);
        MaterialCardView savedCard;
        if (savedMapType == GoogleMap.MAP_TYPE_TERRAIN) savedCard = cardTerrain;
        else if (savedMapType == GoogleMap.MAP_TYPE_SATELLITE) savedCard = cardSatellite;
        else if (savedMapType == GoogleMap.MAP_TYPE_HYBRID) savedCard = cardHybrid;
        else savedCard = cardDefault;
        changeMapType(savedMapType, savedCard);
    }

    // Configuration settings for map and tracking speeds
    private static final String MAP_PREFS_NAME = "ShaloTrackMapPrefs";
    private static final double MOVEMENT_SPEED_THRESHOLD_KMH = 7.0;
    private static final String MAP_TYPE_PREF_KEY = "selected_map_type";

    // Change map view (Satellite, Normal) and highlight the selected button
    private void changeMapType(int mapType, MaterialCardView selectedCard) {
        if (mMap != null) {
            mMap.setMapType(mapType);
            cardDefault.setStrokeWidth(0);
            cardTerrain.setStrokeWidth(0);
            cardSatellite.setStrokeWidth(0);
            cardHybrid.setStrokeWidth(0);
            selectedCard.setStrokeWidth(8);
            selectedCard.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, com.example.letstracklanka.R.color.brand_primary)));
            if (mapTypeMenu != null) mapTypeMenu.setVisibility(View.GONE);
            getSharedPreferences(MAP_PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(MAP_TYPE_PREF_KEY, mapType)
                    .apply();
        }
    }

    // Helper to safely read a list of items from JSON
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

    private ConnectivityManager.NetworkCallback networkCallback;

    // Monitor internet connection. Show warning if WiFi/Data drops.
    private void registerNetworkMonitor() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> showRetryDialog("No internet connection", HomeActivity.this::loadUserData));
            }
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    hideErrorBanner();
                    loadUserData();
                });
            }
        };
        cm.registerDefaultNetworkCallback(networkCallback);
    }

    // Clean up background jobs to save memory when closing the screen
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable);
        if (sosHoldTimer != null) sosHoldTimer.cancel();
        if (realtimeClient != null) realtimeClient.stop();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        }
    }
}