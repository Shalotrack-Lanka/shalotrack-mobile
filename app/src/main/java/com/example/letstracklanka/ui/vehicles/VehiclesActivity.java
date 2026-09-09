package com.example.letstracklanka.ui.vehicles;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateDeviceAssignmentRequest;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.data.model.DeviceLookupResponse;
import com.example.letstracklanka.data.model.InviteVehicleShareRequest;
import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.ui.main.AddressResolver;
import com.example.letstracklanka.ui.main.FallbackPollScheduler;
import com.example.letstracklanka.ui.main.HomeActivity;
import com.example.letstracklanka.ui.history.TripHistoryActivity;
import com.example.letstracklanka.ui.main.TagsActivity;
import com.example.letstracklanka.ui.main.CirclesActivity;
import com.example.letstracklanka.ui.main.VehicleTrailRenderer;
import com.example.letstracklanka.ui.main.RealtimeLocationClient;
import com.example.letstracklanka.ui.main.RealtimeLocationPayload;
import com.example.letstracklanka.ui.main.AlertsActivity;
import com.example.letstracklanka.ui.main.DrawerMenuHelper;
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
import com.google.gson.JsonObject;
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

    private VehicleTrailRenderer trailRenderer;
    private RealtimeLocationClient realtimeClient;

    private View layoutCollapsed;
    private LinearLayout layoutExpanded, layoutLeftFabs;
    private GridLayout gridMenu;
    private ImageView btnCloseExpanded;
    private View fabAdd, fabHistory, btnRefresh;
    private ActivityResultLauncher<Intent> addVehicleLauncher;
    private BottomSheetBehavior<View> bottomSheetBehavior;

    private TextView tvCollapsedStatus, tvCollapsedAddress;
    private TextView tvExpandedStatus, tvExpandedAddress, tvLastUpdated, tvVehicleNameCollapsed, tvVehicleNameExpanded;
    private TextView tvVehicleImei, tvGpsDeviceStatus;
    private CardView dotIgnition, dotAC;
    private AddressResolver addressResolver;

    private RecyclerView recyclerVehiclesTabList;
    private VehicleListAdapter vehiclesTabAdapter;
    private android.widget.EditText etVehicleSearch;
    private TextView tvCountMoving, tvCountIdle, tvCountParked, tvCountOffline;

    private final Handler handler = new Handler();
    private FallbackPollScheduler fallbackPollScheduler;
    private Runnable vehicleListRefreshRunnable;
    private final int UPDATE_INTERVAL = 1000;
    // (FALLBACK_POLL_INTERVAL_TICKS moved into FallbackPollScheduler,
    // shared with HomeActivity -- this was the exact, line-for-line
    // duplicated piece between the two files.)
    // Matches UPDATE_INTERVAL per explicit request. Real cost tradeoff: this
    // fires a full dashboard API call (ALL of the customer's vehicles) every
    // second the Vehicles screen is open, not the lightweight single-vehicle
    // poll the "Currently Tracking" panel uses. Fine for a couple of test
    // vehicles; worth revisiting if a customer's vehicle count grows, or if
    // this shows up as meaningful load on the dashboard endpoint.
    private static final int VEHICLE_LIST_REFRESH_INTERVAL_MS = 1000;

    private String currentCustomerId = null;
    private String selectedVehicleId = null;
    // NEW -- persists whether the currently-displayed vehicle is a
    // shared one, for use later in showVehicleDetails() to hide
    // structural-change controls (Edit, Link GPS Device) for shared
    // viewers. dashboardMatch.isShared() itself was only ever a local
    // variable inside fetchVehicles(), never available where it was
    // actually needed.
    private boolean selectedVehicleIsShared = false;
    private String selectedVehicleName = "No vehicle yet";
    private boolean hasRealVehicle = false;

    private VehicleResponse selectedVehicle = null;
    private LatLng lastKnownPosition = null;
    // NEW -- same pattern as HomeActivity's identical fix. fetchLocationData()
    // never moved the camera at all (unlike handlePushedLocation, which
    // already does on every push), so even after fixing the marker
    // placement itself, a newly-selected vehicle could still be sitting
    // off-screen with nothing indicating it. Only follows on an actual
    // switch, not every regular poll of the same vehicle.
    private boolean cameraFollowPendingForSwitch = false;

    private View errorBanner;
    private TextView tvErrorBannerMessage, tvErrorBannerRetry;
    private ConnectivityManager.NetworkCallback networkCallback;
    private DrawerLayout drawerLayout;

    private static final String MAP_PREFS_NAME = "ShaloTrackMapPrefs";
    private static final double MOVEMENT_SPEED_THRESHOLD_KMH = 7.0;
    private static final long ONLINE_THRESHOLD_MINUTES = 10;
    private static final String MAP_TYPE_PREF_KEY = "selected_map_type";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicles);

        // Must be registered here, synchronously during onCreate -- not
        // inside initViews() or any later callback.
        addVehicleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        fetchVehiclesTabList(); // real refresh, not assumed automatic
                    }
                });

        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        tvErrorBannerRetry = findViewById(R.id.tvErrorBannerRetry);
        registerNetworkMonitor();

        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);
        mainApiService = ApiClient.getClient().create(ApiService.class);
        addressResolver = new AddressResolver(this);

        initViews();
        setupBottomSheet();
        setupGridMenu();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapVehicles);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        loadUserData();
        startRealTimeTracking();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        DrawerMenuHelper.wireDrawer(this, drawerLayout,
                findViewById(R.id.tvDrawerName), findViewById(R.id.tvDrawerPhone), findViewById(R.id.tvDrawerEmail));

        layoutCollapsed = findViewById(R.id.layoutCollapsed);
        layoutExpanded = findViewById(R.id.layoutExpanded);
        layoutLeftFabs = findViewById(R.id.layoutLeftFabs);
        gridMenu = findViewById(R.id.gridMenu);
        fabAdd = findViewById(R.id.fabAdd);
        fabHistory = findViewById(R.id.fabHistory);

        if (fabHistory != null) {
            fabHistory.setOnClickListener(v -> openTripHistory());
        }

        // FIX: found during a systematic dead-end audit -- this was a
        // real, visible, tappable-looking button with no click listener
        // at all anywhere in this file. Now launches the real Add
        // Vehicle flow, refreshing the list on a successful add.
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> {
                if (currentCustomerId == null) {
                    Toast.makeText(this, "Still loading your profile \u2014 try again in a moment.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(VehiclesActivity.this, AddVehicleActivity.class);
                intent.putExtra(AddVehicleActivity.EXTRA_CUSTOMER_ID, currentCustomerId);
                addVehicleLauncher.launch(intent);
            });
        }

        tvCollapsedStatus = findViewById(R.id.tvCollapsedStatus);
        tvCollapsedAddress = findViewById(R.id.tvCollapsedAddress);
        tvExpandedStatus = findViewById(R.id.tvExpandedStatus);
        tvExpandedAddress = findViewById(R.id.tvExpandedAddress);
        tvLastUpdated = findViewById(R.id.tvLastUpdated);
        tvVehicleNameCollapsed = findViewById(R.id.tvVehicleNameCollapsed);
        tvVehicleNameExpanded = findViewById(R.id.tvVehicleNameExpanded);
        tvVehicleImei = findViewById(R.id.tvVehicleImei);
        tvGpsDeviceStatus = findViewById(R.id.tvGpsDeviceStatus);
        dotIgnition = findViewById(R.id.dotIgnition);
        dotAC = findViewById(R.id.dotAC);

        btnCloseExpanded = findViewById(R.id.btnCloseExpanded);
        btnRefresh = findViewById(R.id.btnRefresh);

        recyclerVehiclesTabList = findViewById(R.id.recyclerVehiclesTabList);
        if (recyclerVehiclesTabList != null) {
            recyclerVehiclesTabList.setLayoutManager(new LinearLayoutManager(this));
            // FIX: the XML nestedScrollingEnabled="false" attribute alone
            // wasn't reliably enough -- confirmed via a real screenshot
            // showing the list squished/cut off inside the NestedScrollView.
            // Setting this explicitly in code is the more reliable fix for
            // this well-known RecyclerView-inside-scrolling-container issue.
            recyclerVehiclesTabList.setNestedScrollingEnabled(false);
            recyclerVehiclesTabList.setHasFixedSize(false);
            vehiclesTabAdapter = new VehicleListAdapter(new ArrayList<>(), this::onVehiclesTabVehicleSelected, this::confirmRemoveVehicleFromVehiclesTab);
            recyclerVehiclesTabList.setAdapter(vehiclesTabAdapter);
        }

        // NEW (Letstrack parity): search bar filters the vehicle list
        // client-side, no extra network call per keystroke.
        etVehicleSearch = findViewById(R.id.etVehicleSearch);
        if (etVehicleSearch != null) {
            etVehicleSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (vehiclesTabAdapter != null) vehiclesTabAdapter.filter(s.toString());
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        tvCountMoving = findViewById(R.id.tvCountMoving);
        tvCountIdle = findViewById(R.id.tvCountIdle);
        tvCountParked = findViewById(R.id.tvCountParked);
        tvCountOffline = findViewById(R.id.tvCountOffline);
    }

    // NEW (Letstrack parity): classifies every vehicle into exactly one of
    // the four summary buckets and updates the count chips. Counts are
    // always computed from ALL vehicles (adapter.getAllVehicles()), not
    // the currently search-filtered subset -- the summary should reflect
    // the whole fleet regardless of what's typed in the search box.
    // "No Device" vehicles are folded into "Offline": Letstrack's own
    // reference only has these four categories, no fifth "No Device"
    // chip, and a vehicle with nothing assigned is, at minimum, also not
    // reporting.
    private void updateVehicleStatusCounts() {
        if (vehiclesTabAdapter == null) return;
        List<DashboardVehicle> all = vehiclesTabAdapter.getAllVehicles();
        int moving = 0, idle = 0, parked = 0, offline = 0;
        if (all != null) {
            for (DashboardVehicle v : all) {
                if (!v.hasDevice() || !v.isOnline()) {
                    offline++;
                } else if (v.getSpeed() > MOVEMENT_SPEED_THRESHOLD_KMH) {
                    moving++;
                } else if (v.isIgnitionOn()) {
                    idle++;
                } else {
                    parked++;
                }
            }
        }
        if (tvCountMoving != null) tvCountMoving.setText(String.valueOf(moving));
        if (tvCountIdle != null) tvCountIdle.setText(String.valueOf(idle));
        if (tvCountParked != null) tvCountParked.setText(String.valueOf(parked));
        if (tvCountOffline != null) tvCountOffline.setText(String.valueOf(offline));
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
                    if (layoutLeftFabs != null) layoutLeftFabs.setVisibility(View.GONE);
                }
            }
            @Override public void onSlide(@NonNull View bottomSheet, float slideOffset) { }
        });

        if (layoutCollapsed != null) {
            layoutCollapsed.setOnClickListener(v -> expandVehicleDetails());
        }

        if (btnCloseExpanded != null) {
            btnCloseExpanded.setOnClickListener(v -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED));
        }

        View btnMenuAlerts = findViewById(R.id.btnMenuAlerts);
        if (btnMenuAlerts != null) {
            btnMenuAlerts.setOnClickListener(v -> {
                // NEW: filters to the currently selected vehicle's alerts,
                // unlike the bottom-nav Alerts tab below (nav_alerts),
                // which deliberately stays a global "all vehicles" view.
                Intent intent = new Intent(VehiclesActivity.this, AlertsActivity.class);
                intent.putExtra(AlertsActivity.EXTRA_VEHICLE_ID, selectedVehicleId);
                intent.putExtra(AlertsActivity.EXTRA_VEHICLE_NAME, selectedVehicleName);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

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
            navAlerts.setOnClickListener(v -> {
                Intent intent = new Intent(VehiclesActivity.this, AlertsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        View navMenu = findViewById(R.id.nav_menu);
        if (navMenu != null) {
            navMenu.setOnClickListener(v -> {
                // FIX: previously navigated to HomeActivity with an
                // open_drawer extra -- now opens this screen's own real
                // drawer directly, found during a systematic dead-end
                // audit of "Menu doesn't work from every tab".
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                Toast.makeText(this, "Refreshing location...", Toast.LENGTH_SHORT).show();
                fetchLocationData();
            });
        }
    }

    private void setupGridMenu() {
        View btnMenuHistory = findViewById(R.id.btnMenuHistory);
        if (btnMenuHistory != null) {
            btnMenuHistory.setOnClickListener(v -> openTripHistory());
        }

        View btnMenuVoiceTrack = findViewById(R.id.btnMenuVoiceTrack);
        if (btnMenuVoiceTrack != null) {
            btnMenuVoiceTrack.setOnClickListener(v ->
                    Toast.makeText(this, "Not available for this device", Toast.LENGTH_SHORT).show());
        }

        View btnMenuValue = findViewById(R.id.btnMenuValue);
        if (btnMenuValue != null) {
            btnMenuValue.setOnClickListener(v -> {
                if (selectedVehicleId == null) {
                    Toast.makeText(this, "No vehicle selected.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(VehiclesActivity.this, ValueActivity.class);
                intent.putExtra(ValueActivity.EXTRA_VEHICLE_ID, selectedVehicleId);
                startActivity(intent);
            });
        }

        View btnMenuPlaces = findViewById(R.id.btnMenuPlaces);
        if (btnMenuPlaces != null) {
            // FIX: this was still the "Coming soon" placeholder even after
            // PlacesActivity was built -- only the drawer's own Places item
            // had been rewired. This is a separate entry point (grid menu
            // inside a vehicle's detail panel), found not working when
            // reported directly.
            btnMenuPlaces.setOnClickListener(v ->
                    startActivity(new Intent(VehiclesActivity.this, com.example.letstracklanka.ui.places.PlacesActivity.class)));
        }

        View btnMenuImmobilize = findViewById(R.id.btnMenuImmobilize);
        if (btnMenuImmobilize != null) {
            btnMenuImmobilize.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Not available yet")
                    .setMessage("Remote engine cut requires additional safety configuration " +
                            "and is not enabled for this vehicle yet.")
                    .setPositiveButton("OK", null)
                    .show());
        }

        View btnMenuNav = findViewById(R.id.btnMenuNav);
        if (btnMenuNav != null) {
            btnMenuNav.setOnClickListener(v -> openNavigation());
        }

        View btnMenuDetails = findViewById(R.id.btnMenuDetails);
        if (btnMenuDetails != null) {
            btnMenuDetails.setOnClickListener(v -> showVehicleDetails());
        }

        View btnMenuShare = findViewById(R.id.btnMenuShare);
        if (btnMenuShare != null) {
            btnMenuShare.setOnClickListener(v -> showShareVehicleDialog());
        }
    }

    // Vehicle Sharing invite flow -- deliberately a simple dialog rather
    // than a full screen, matching this action's actual weight (enter a
    // number, tap invite). The backend resolves the phone number to a
    // real registered Customer directly at invite time, so a clear,
    // specific error ("No account found for that number") comes back
    // if they haven't installed the app yet -- surfaced here rather than
    // shown as a generic failure.
    private void showShareVehicleDialog() {
        if (selectedVehicleId == null) {
            Toast.makeText(this, "No vehicle selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText input = new EditText(this);
        input.setHint("Phone number, e.g. 0771234567");
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Share this vehicle")
                .setMessage("The person must already have the ShaloTrack app installed and registered with this number.")
                .setView(input)
                .setPositiveButton("Invite", (dialog, which) -> {
                    String phoneNumber = input.getText() != null ? input.getText().toString().trim() : "";
                    if (phoneNumber.isEmpty()) {
                        Toast.makeText(this, "Enter a phone number.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sendShareInvite(phoneNumber);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // NEW -- the actual fix for the confirmed missing-feature gap: reuses
    // the exact same lookup-then-assign pair already proven in
    // AddVehicleActivity, just as a simple dialog here instead of a full
    // form, since this vehicle already exists and only needs a device.
    private void showLinkGpsDeviceDialog(BottomSheetDialog detailsDialog, String vehicleId) {
        if (vehicleId == null) {
            Toast.makeText(this, "No vehicle selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText input = new EditText(this);
        input.setHint("Device IMEI");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Link GPS Device")
                .setMessage("Enter the IMEI printed on the device.")
                .setView(input)
                .setPositiveButton("Link", (dialog, which) -> {
                    String imei = input.getText() != null ? input.getText().toString().trim() : "";
                    if (imei.isEmpty()) {
                        Toast.makeText(this, "Enter an IMEI.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    lookupThenLinkDevice(detailsDialog, vehicleId, imei);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void lookupThenLinkDevice(BottomSheetDialog detailsDialog, String vehicleId, String imei) {
        mainApiService.lookupDeviceByImei(imei).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                String deviceId = null;
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        DeviceLookupResponse device = extractObject(body.string(), DeviceLookupResponse.class);
                        if (device != null) deviceId = device.getDeviceId();
                    }
                } catch (Exception e) {
                    Log.e("VehiclesActivity", "lookupDeviceByImei parse error", e);
                }

                if (deviceId == null) {
                    Toast.makeText(VehiclesActivity.this, "Device not found for that IMEI.", Toast.LENGTH_LONG).show();
                    return;
                }

                CreateDeviceAssignmentRequest assignRequest = new CreateDeviceAssignmentRequest(vehicleId, deviceId);
                mainApiService.assignDevice(assignRequest).enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(VehiclesActivity.this, "Device linked.", Toast.LENGTH_SHORT).show();
                            detailsDialog.dismiss();
                            fetchVehicles();
                        } else {
                            Log.w("VehiclesActivity", "assignDevice failed, code " + response.code());
                            Toast.makeText(VehiclesActivity.this, "Couldn't link that device. Try again.", Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        Log.e("VehiclesActivity", "assignDevice network error", t);
                        Toast.makeText(VehiclesActivity.this, "Network error \u2014 check your connection.", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("VehiclesActivity", "lookupDeviceByImei network error", t);
                Toast.makeText(VehiclesActivity.this, "Network error \u2014 check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendShareInvite(String phoneNumber) {
        InviteVehicleShareRequest request = new InviteVehicleShareRequest(selectedVehicleId, phoneNumber);
        mainApiService.inviteVehicleShare(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    String bodyString = body != null ? body.string() : null;
                    if (response.isSuccessful()) {
                        Toast.makeText(VehiclesActivity.this, "Invite sent.", Toast.LENGTH_SHORT).show();
                    } else {
                        String errorMessage = extractApiMessage(bodyString);
                        Toast.makeText(VehiclesActivity.this,
                                errorMessage != null ? errorMessage : "Couldn't send the invite. Try again.",
                                Toast.LENGTH_LONG).show();
                        Log.w("VehiclesActivity", "inviteVehicleShare failed, code " + response.code());
                    }
                } catch (Exception e) {
                    Log.e("VehiclesActivity", "sendShareInvite parse error", e);
                    Toast.makeText(VehiclesActivity.this, "Something went wrong. Try again.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("VehiclesActivity", "sendShareInvite network error", t);
                Toast.makeText(VehiclesActivity.this, "Network error \u2014 check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Pulls the real "message" field out of a failed ApiResponse body, so
    // the person sees the actual reason (e.g. "No account found for that
    // number") instead of a generic failure toast.
    private String extractApiMessage(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            com.google.gson.JsonObject root = new com.google.gson.Gson().fromJson(json, com.google.gson.JsonObject.class);
            if (root != null && root.has("message") && !root.get("message").isJsonNull()) {
                return root.get("message").getAsString();
            }
        } catch (Exception e) {
            Log.e("VehiclesActivity", "extractApiMessage error", e);
        }
        return null;
    }

    private void openTripHistory() {
        Intent intent = new Intent(VehiclesActivity.this, TripHistoryActivity.class);
        // FIX: previously passed NOTHING -- TripHistoryActivity had to guess
        // the vehicle itself via a confirmed-buggy endpoint that silently
        // omits offline vehicles, so History always showed one arbitrary
        // vehicle's trips regardless of which one you were actually
        // viewing. Now passes the vehicle actually on screen.
        intent.putExtra(TripHistoryActivity.EXTRA_VEHICLE_ID, selectedVehicleId);
        intent.putExtra(TripHistoryActivity.EXTRA_VEHICLE_NAME, selectedVehicleName);
        startActivity(intent);
    }

    private void openNavigation() {
        if (lastKnownPosition == null) {
            Toast.makeText(this, "No location available yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" +
                lastKnownPosition.latitude + "," + lastKnownPosition.longitude);
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Uri webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" +
                    lastKnownPosition.latitude + "," + lastKnownPosition.longitude);
            startActivity(new Intent(Intent.ACTION_VIEW, webUri));
        }
    }

    // Real, interactive redesign, matching a confirmed mockup design
    // directly: a real card with an avatar header, vehicle fields and GPS
    // device fields each in their own labeled section with icons.
    // Previous version was a plain AlertDialog with every field
    // concatenated into one block of text.
    private void showVehicleDetails() {
        if (selectedVehicle == null) {
            Toast.makeText(this, "Vehicle details not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_vehicle_details, null);
        dialog.setContentView(view);

        TextView tvName = view.findViewById(R.id.tvDetailsVehicleName);
        TextView tvNumber = view.findViewById(R.id.tvDetailsVehicleNumber);
        View btnClose = view.findViewById(R.id.btnCloseDetails);
        View btnEdit = view.findViewById(R.id.btnEditVehicleDetails);
        LinearLayout vehicleFieldsContainer = view.findViewById(R.id.vehicleFieldsContainer);
        LinearLayout gpsFieldsContainer = view.findViewById(R.id.gpsFieldsContainer);
        View tvGpsSectionLabel = view.findViewById(R.id.tvGpsSectionLabel);
        View layoutNoGpsDevice = view.findViewById(R.id.layoutNoGpsDevice);
        View btnLinkGpsDevice = view.findViewById(R.id.btnLinkGpsDevice);

        if (tvName != null) tvName.setText(safe(selectedVehicleName));
        if (tvNumber != null) tvNumber.setText(safe(selectedVehicle.getVehicleNumber()));
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        // NEW -- real, existing backend endpoint (PUT /api/Vehicles/{id})
        // was never surfaced anywhere in the Android UI until now.
        // FIX: owner-only now, per direct confirmation -- was previously
        // visible/clickable for shared viewers too, inconsistent with
        // the "full view access, no structural changes" boundary used
        // everywhere else for shared vehicles.
        if (btnEdit != null) {
            btnEdit.setVisibility(selectedVehicleIsShared ? View.GONE : View.VISIBLE);
            if (!selectedVehicleIsShared) {
                btnEdit.setOnClickListener(v -> {
                    dialog.dismiss();
                    android.content.Intent intent = new android.content.Intent(this, AddVehicleActivity.class);
                    intent.putExtra(AddVehicleActivity.EXTRA_EDIT_VEHICLE_ID, selectedVehicle.getVehicleId());
                    intent.putExtra(AddVehicleActivity.EXTRA_VEHICLE_NUMBER, selectedVehicle.getVehicleNumber());
                    intent.putExtra(AddVehicleActivity.EXTRA_MAKE, selectedVehicle.getMake());
                    intent.putExtra(AddVehicleActivity.EXTRA_MODEL, selectedVehicle.getModel());
                    intent.putExtra(AddVehicleActivity.EXTRA_YEAR,
                            selectedVehicle.getYear() != null ? selectedVehicle.getYear() : 0);
                    intent.putExtra(AddVehicleActivity.EXTRA_COLOR, selectedVehicle.getColor());
                    intent.putExtra(AddVehicleActivity.EXTRA_VEHICLE_TYPE, selectedVehicle.getVehicleType());
                    intent.putExtra(AddVehicleActivity.EXTRA_FUEL_TYPE, selectedVehicle.getFuelType());
                    intent.putExtra(AddVehicleActivity.EXTRA_CHASSIS_NUMBER, selectedVehicle.getChassisNumber());
                    intent.putExtra(AddVehicleActivity.EXTRA_ENGINE_NUMBER, selectedVehicle.getEngineNumber());
                    addVehicleLauncher.launch(intent);
                });
            }
        }

        if (vehicleFieldsContainer != null) {
            addDetailRow(vehicleFieldsContainer, R.drawable.ic_car_3d_small, "Make", safe(selectedVehicle.getMake()), true);
            addDetailRow(vehicleFieldsContainer, R.drawable.ic_detail_tag, "Model", safe(selectedVehicle.getModel()), false);
            addDetailRow(vehicleFieldsContainer, R.drawable.ic_detail_tag, "Year",
                    selectedVehicle.getYear() != null ? String.valueOf(selectedVehicle.getYear()) : "--", false);
            addDetailRow(vehicleFieldsContainer, R.drawable.ic_detail_palette, "Color", safe(selectedVehicle.getColor()), false);
            addDetailRow(vehicleFieldsContainer, R.drawable.ic_car_3d_small, "Vehicle type", safe(selectedVehicle.getVehicleType()), false);
            addDetailRow(vehicleFieldsContainer, R.drawable.ic_detail_palette, "Fuel type", safe(selectedVehicle.getFuelType()), false);
            addDetailRow(vehicleFieldsContainer, R.drawable.ic_detail_tag, "Chassis number", safe(selectedVehicle.getChassisNumber()), false);
            addDetailRow(vehicleFieldsContainer, R.drawable.ic_detail_tag, "Engine number", safe(selectedVehicle.getEngineNumber()), false);
        }

        boolean hasDevice = selectedVehicle.hasGpsDevice();
        if (tvGpsSectionLabel != null) tvGpsSectionLabel.setVisibility(hasDevice ? View.VISIBLE : View.GONE);
        if (gpsFieldsContainer != null) gpsFieldsContainer.setVisibility(hasDevice ? View.VISIBLE : View.GONE);
        // FIX: real, confirmed gap -- a vehicle without a device had no
        // way to link one anywhere in the app except during the
        // original "Add Vehicle" creation flow, despite that flow's own
        // error message promising "you can link it later from the
        // vehicle's settings" -- a capability that never actually
        // existed. This wrapper (not just the text inside it) is what
        // needs toggling now that it also contains the real button.
        if (layoutNoGpsDevice != null) layoutNoGpsDevice.setVisibility(hasDevice ? View.GONE : View.VISIBLE);
        // FIX: owner-only now, per direct confirmation -- same boundary
        // as btnEdit above. Hides just the button, not the whole
        // wrapper -- the informational "No GPS device" text itself
        // should still be visible to a shared viewer, they just can't
        // act on it.
        if (btnLinkGpsDevice != null) {
            btnLinkGpsDevice.setVisibility(selectedVehicleIsShared ? View.GONE : View.VISIBLE);
            if (!selectedVehicleIsShared) {
                btnLinkGpsDevice.setOnClickListener(v -> showLinkGpsDeviceDialog(dialog, selectedVehicle.getVehicleId()));
            }
        }

        if (hasDevice && gpsFieldsContainer != null) {
            addDetailRow(gpsFieldsContainer, R.drawable.ic_detail_sim, "IMEI", safe(selectedVehicle.getImei()), true);
            addDetailRow(gpsFieldsContainer, R.drawable.ic_detail_sim, "SIM number", safe(selectedVehicle.getSimNumber()), false);
            addDetailRow(gpsFieldsContainer, R.drawable.ic_detail_tag, "Device model", safe(selectedVehicle.getDeviceModel()), false);
            addDetailRow(gpsFieldsContainer, R.drawable.ic_detail_sim, "Network provider", safe(selectedVehicle.getNetworkProvider()), false);
            addDetailRow(gpsFieldsContainer, R.drawable.ic_detail_tag, "Firmware version", safe(selectedVehicle.getFirmwareVersion()), false);
            addDetailRow(gpsFieldsContainer, R.drawable.ic_detail_tag, "Activation status", safe(selectedVehicle.getActivationStatus()), false);
            addDetailRow(gpsFieldsContainer, R.drawable.ic_detail_tag, "Warranty expiry", formatDetailDate(selectedVehicle.getWarrantyExpiryDate()), false);
            addDetailRow(gpsFieldsContainer, R.drawable.ic_detail_tag, "Installed", formatDetailDate(selectedVehicle.getInstalledAt()), false);
        }

        dialog.show();
    }

    // Shared row builder: icon, label, value, with a divider between rows
    // (skipped for the first row in each section).
    private void addDetailRow(LinearLayout container, int iconRes, String label, String value, boolean isFirst) {
        int density = (int) getResources().getDisplayMetrics().density;

        if (!isFirst) {
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_stroke));
            container.addView(divider);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(14 * density, 12 * density, 14 * density, 12 * density);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        int iconSize = 18 * density;
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMarginEnd(10 * density);
        icon.setLayoutParams(iconParams);
        row.addView(icon);

        TextView labelText = new TextView(this);
        labelText.setText(label);
        labelText.setTextSize(13);
        labelText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelText.setLayoutParams(labelParams);
        row.addView(labelText);

        TextView valueText = new TextView(this);
        valueText.setText(value);
        valueText.setTextSize(13);
        valueText.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        row.addView(valueText);

        container.addView(row);
    }

    // Backend sends ISO-8601 -- shown as a plain readable date rather than
    // the raw string. Falls back to "--" for null/unparseable values
    // instead of showing something confusing.
    private String formatDetailDate(String iso) {
        if (iso == null || iso.trim().isEmpty()) return "--";
        try {
            String trimmed = iso.length() > 10 ? iso.substring(0, 10) : iso;
            java.text.SimpleDateFormat parser = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US);
            java.util.Date date = parser.parse(trimmed);
            return date != null ? formatter.format(date) : "--";
        } catch (Exception e) {
            return "--";
        }
    }

    private String safe(String value) {
        return value != null ? value : "--";
    }

    private void loadUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        mainApiService.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        hideErrorBanner();
                        CustomerResponse customer = extractObject(body.string(), CustomerResponse.class);
                        if (customer != null && customer.getCustomerId() != null) {
                            currentCustomerId = customer.getCustomerId();
                            fetchVehicles();
                            fetchVehiclesTabList();
                        }
                    } else {
                        Log.w("VehiclesActivity", "getMyProfile failed with code " + response.code());
                        showRetryDialog("Couldn't load your profile", VehiclesActivity.this::loadUserData);
                    }
                } catch (Exception e) {
                    Log.e("VehiclesActivity", "Error loading user data", e);
                }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("VehiclesActivity", "Failed to fetch user data", t);
                showRetryDialog("Network error — couldn't load your profile", VehiclesActivity.this::loadUserData);
            }
        });
    }

    // REMOVED: pickSelectedVehicle(). Confirmed via actual logged API
    // responses that /api/Vehicles/customer/{customerId} sometimes omits
    // offline vehicles entirely (Honda Vezel was missing from it while
    // present in the dashboard endpoint), so its old fallback of
    // "list.get(list.size()-1)" was really "silently show whichever
    // vehicle happens to be the only one this incomplete endpoint
    // returned" -- which is exactly why tapping Honda Vezel always
    // displayed JAPAN Mazda. Selection now comes from the dashboard list
    // (vehiclesTabAdapter.getAllVehicles(), same data already backing the
    // switcher list you tap, confirmed complete), via findDashboardVehicle()
    // below. This is a client-side workaround for a server-side gap --
    // /api/Vehicles/customer/{customerId} still needs fixing on the API
    // side so it stops omitting offline vehicles.

    private DashboardVehicle findDashboardVehicle(String vehicleId) {
        if (vehicleId == null || vehiclesTabAdapter == null) return null;
        List<DashboardVehicle> all = vehiclesTabAdapter.getAllVehicles();
        if (all == null) return null;
        for (DashboardVehicle v : all) {
            if (vehicleId.equalsIgnoreCase(v.getVehicleId())) return v;
        }
        return null;
    }

    private void fetchVehicles() {
        if (currentCustomerId == null) return;
        mainApiService.getVehiclesByCustomer(currentCustomerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        hideErrorBanner();
                        List<VehicleResponse> list = parseList(body.string(), VehicleResponse.class);

                        String targetId = getSharedPreferences(
                                com.example.letstracklanka.ui.vehicles.VehicleListActivity.VEHICLE_PREFS_NAME,
                                Context.MODE_PRIVATE)
                                .getString(com.example.letstracklanka.ui.vehicles.VehicleListActivity.SELECTED_VEHICLE_ID_KEY, null);

                        // Reliable source of truth for WHICH vehicle is
                        // selected (see class-level comment above).
                        DashboardVehicle dashboardMatch = findDashboardVehicle(targetId);

                        // Supplementary only -- may legitimately be null even
                        // for a real, linked vehicle, if this specific
                        // endpoint's known gap is why it's missing.
                        VehicleResponse detailMatch = null;
                        if (targetId != null) {
                            for (VehicleResponse v : list) {
                                if (targetId.equalsIgnoreCase(v.getVehicleId())) { detailMatch = v; break; }
                            }
                        }

                        if (dashboardMatch == null && detailMatch == null && !list.isEmpty()) {
                            // No saved selection yet (e.g. very first launch,
                            // before the switcher list/dashboard data has
                            // loaded) -- fall back to the old behavior rather
                            // than showing nothing.
                            detailMatch = list.get(list.size() - 1);
                        }

                        if (dashboardMatch == null && detailMatch == null) {
                            return; // genuinely nothing available to show yet
                        }

                        selectedVehicle = detailMatch; // may be null; showVehicleDetails() already null-checks this
                        selectedVehicleId = dashboardMatch != null ? dashboardMatch.getVehicleId() : detailMatch.getVehicleId();
                        selectedVehicleIsShared = dashboardMatch != null && dashboardMatch.isShared();

                        // NEW -- same real gap fixed in HomeActivity: the
                        // marker's type was never set on initial load,
                        // only on a later switch.
                        String initialVehicleType = dashboardMatch != null
                                ? dashboardMatch.getVehicleType()
                                : (detailMatch != null ? detailMatch.getVehicleType() : null);
                        trailRenderer.setVehicleTypeForNextMarker(initialVehicleType);

                        trailRenderer.loadInitialTrail(selectedVehicleId, () -> {});

                        // FIX: real bug confirmed via screenshot -- "Vehicle
                        // details not loaded yet" always fired for a shared
                        // vehicle, since getVehiclesByCustomer only ever
                        // returns owned vehicles, so detailMatch was always
                        // null for one. Fetched separately here rather than
                        // blocking the rest of this method (trail/tracking
                        // setup below don't need this, only the Details
                        // sheet does) -- overwrites selectedVehicle once it
                        // actually arrives.
                        if (dashboardMatch != null && dashboardMatch.isShared() && detailMatch == null) {
                            mainApiService.getVehicleById(selectedVehicleId).enqueue(new Callback<VehicleResponse>() {
                                @Override
                                public void onResponse(@NonNull Call<VehicleResponse> call, @NonNull Response<VehicleResponse> resp) {
                                    if (resp.isSuccessful() && resp.body() != null) {
                                        selectedVehicle = resp.body();
                                    } else {
                                        Log.w("VehiclesActivity", "getVehicleById failed for shared vehicle, code " + resp.code());
                                    }
                                }

                                @Override
                                public void onFailure(@NonNull Call<VehicleResponse> call, @NonNull Throwable t) {
                                    Log.e("VehiclesActivity", "getVehicleById network error for shared vehicle", t);
                                }
                            });
                        }

                        // FIX: previously "if (realtimeClient == null)" only ever
                        // connected once, at app launch. Switching vehicles
                        // afterward updated selectedVehicleId but left the
                        // realtime connection permanently subscribed to whatever
                        // vehicle was selected FIRST -- its live pushes kept
                        // arriving and overwriting the newly-selected vehicle's
                        // status/address moments after switching, which is why
                        // "currently tracking" looked stuck. Now torn down and
                        // reconnected to the actual current selection every time.
                        //
                        // IMPORTANT: RealtimeLocationClient.stop() calls
                        // hubConnection.stop().timeout(3, SECONDS).blockingAwait()
                        // -- a genuinely blocking call, up to 3 seconds. This
                        // onResponse() callback runs on the main thread (Retrofit's
                        // Android default), so calling stop() directly here would
                        // risk freezing the UI, or an ANR, on every vehicle switch.
                        // The old client's teardown doesn't need to finish before
                        // the new one starts (the vehicle-ID guard added in
                        // handlePushedLocation() already protects against any
                        // stale message arriving during the brief overlap), so
                        // it's pushed onto a background thread instead.
                        RealtimeLocationClient oldRealtimeClient = realtimeClient;
                        if (oldRealtimeClient != null) {
                            new Thread(oldRealtimeClient::stop, "RealtimeClient-Stop").start();
                        }
                        realtimeClient = new RealtimeLocationClient();
                        realtimeClient.connect(selectedVehicleId, payload ->
                                runOnUiThread(() -> handlePushedLocation(payload)));

                        selectedVehicleName = dashboardMatch != null
                                ? (dashboardMatch.getMake() + " " + dashboardMatch.getModel()).trim()
                                : (detailMatch.getMake() + " " + detailMatch.getModel()).trim();
                        hasRealVehicle = true;

                        if (detailMatch != null && detailMatch.hasGpsDevice() && detailMatch.getImei() != null) {
                            if (tvVehicleImei != null) tvVehicleImei.setText("IMEI: " + detailMatch.getImei());
                            if (tvGpsDeviceStatus != null) {
                                tvGpsDeviceStatus.setText("GPS Device: Linked");
                                tvGpsDeviceStatus.setTextColor(ContextCompat.getColor(VehiclesActivity.this, com.example.letstracklanka.R.color.status_moving));
                            }
                        } else if (dashboardMatch != null && dashboardMatch.hasDevice()) {
                            // FIX: the vehicle IS linked -- confirmed via the
                            // reliable dashboard data -- but this specific
                            // endpoint's known gap means it didn't come back
                            // with an IMEI this time. Say so honestly instead
                            // of "Not linked", which would be a flatly false
                            // statement about a real, linked device.
                            if (tvVehicleImei != null) tvVehicleImei.setText("IMEI: Pending sync");
                            if (tvGpsDeviceStatus != null) {
                                tvGpsDeviceStatus.setText("GPS Device: Linked");
                                tvGpsDeviceStatus.setTextColor(ContextCompat.getColor(VehiclesActivity.this, com.example.letstracklanka.R.color.status_moving));
                            }
                        } else {
                            if (tvVehicleImei != null) tvVehicleImei.setText("IMEI: Not linked");
                            if (tvGpsDeviceStatus != null) {
                                tvGpsDeviceStatus.setText("GPS Device: Not linked");
                                tvGpsDeviceStatus.setTextColor(Color.parseColor("#E53935"));
                            }
                        }

                        updateVehicleUI();
                        fetchLocationData();
                    } else {
                        Log.w("VehiclesActivity", "fetchVehicles failed, code " + response.code());
                        showRetryDialog("Couldn't load your vehicle", VehiclesActivity.this::fetchVehicles);
                    }
                } catch (Exception e) {
                    Log.e("VehiclesActivity", "Error fetching vehicles", e);
                    showRetryDialog("Something went wrong loading your vehicle", VehiclesActivity.this::fetchVehicles);
                }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("VehiclesActivity", "Failed to fetch vehicles", t);
                showRetryDialog("Network error — couldn't load your vehicle", VehiclesActivity.this::fetchVehicles);
            }
        });
    }

    private void fetchVehiclesTabList() {
        if (currentCustomerId == null || vehiclesTabAdapter == null) return;
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

                    List<DashboardVehicle> vehicles = gson.fromJson(data.getAsJsonArray("vehicles"),
                            TypeToken.getParameterized(List.class, DashboardVehicle.class).getType());
                    vehiclesTabAdapter.updateVehicles(vehicles);
                    updateVehicleStatusCounts();
                } catch (Exception e) {
                    Log.e("VehiclesActivity", "fetchVehiclesTabList parse error", e);
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("VehiclesActivity", "fetchVehiclesTabList network error", t);
            }
        });
    }

    private void onVehiclesTabVehicleSelected(DashboardVehicle vehicle) {
        getSharedPreferences(
                com.example.letstracklanka.ui.vehicles.VehicleListActivity.VEHICLE_PREFS_NAME,
                Context.MODE_PRIVATE)
                .edit()
                .putString(com.example.letstracklanka.ui.vehicles.VehicleListActivity.SELECTED_VEHICLE_ID_KEY, vehicle.getVehicleId())
                .apply();

        // FIX: same real root cause as HomeActivity's identical bug --
        // both handlePushedLocation() and fetchLocationData() call
        // trailRenderer.updatePosition() unconditionally, which after the
        // marker exists once always animates. Without this reset, the
        // very next poll (within 1s, via vehicleListRefreshRunnable) would
        // animate the marker across the whole map from the previously-
        // selected vehicle's position to the new one's.
        trailRenderer.resetForVehicleSwitch(vehicle.getVehicleType());
        cameraFollowPendingForSwitch = true;

        fetchVehicles();
        // NEW: tapping a vehicle in the switcher list now opens the same
        // expanded detail panel that tapping the collapsed summary bar
        // already did -- previously this only updated the selection
        // silently in the background, leaving the user looking at the
        // same list with no visible confirmation anything happened.
        expandVehicleDetails();
    }

    // Shared by both layoutCollapsed's own tap and the vehicle-switcher
    // list's row tap -- previously duplicated inline only in the former.
    private void expandVehicleDetails() {
        if (layoutCollapsed != null) layoutCollapsed.setVisibility(View.GONE);
        if (fabAdd != null) fabAdd.setVisibility(View.GONE);
        if (layoutExpanded != null) layoutExpanded.setVisibility(View.VISIBLE);
        if (gridMenu != null) gridMenu.setVisibility(View.VISIBLE);
        if (layoutLeftFabs != null) layoutLeftFabs.setVisibility(View.VISIBLE);

        View bottomSheetView = findViewById(R.id.bottomSheetVehicleDetails);
        if (bottomSheetView != null && bottomSheetBehavior != null) {
            bottomSheetView.post(() -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED));
        }
    }

    private void confirmRemoveVehicleFromVehiclesTab(DashboardVehicle vehicle) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + vehicle.getVehicleNumber() + "?")
                .setMessage("This removes the vehicle from your account and frees its GPS device so it can be linked to a new vehicle. Trip history and alerts are kept. This can't be undone from the app.")
                .setPositiveButton("Remove", (dialog, which) -> {
                    mainApiService.deleteVehicle(vehicle.getVehicleId()).enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                            if (response.isSuccessful()) {
                                fetchVehiclesTabList();
                                fetchVehicles();
                            } else {
                                // FIX: previously only logged/showed response.code(),
                                // discarding response.errorBody() entirely -- which is
                                // exactly where the API's actual validation message
                                // lives. Reading it here doesn't fix the 400 itself
                                // (that needs ApiService.java + the C# controller to
                                // diagnose properly, not a guess), but it turns the
                                // NEXT occurrence into an actionable message instead
                                // of a bare, useless code.
                                String serverMessage = null;
                                try {
                                    if (response.errorBody() != null) {
                                        serverMessage = response.errorBody().string();
                                    }
                                } catch (Exception e) {
                                    Log.e("VehiclesActivity", "Failed to read error body", e);
                                }
                                Log.e("VehiclesActivity", "deleteVehicle failed, code " + response.code()
                                        + ", vehicleId=" + vehicle.getVehicleId()
                                        + ", body=" + serverMessage);
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

    private void updateVehicleUI() {
        if (tvVehicleNameCollapsed != null) tvVehicleNameCollapsed.setText(selectedVehicleName);
        if (tvVehicleNameExpanded != null) tvVehicleNameExpanded.setText(selectedVehicleName);
    }

    @Override public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        trailRenderer = new VehicleTrailRenderer(this, mMap, trackingApi);

        int savedMapType = getSharedPreferences(MAP_PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(MAP_TYPE_PREF_KEY, GoogleMap.MAP_TYPE_NORMAL);
        mMap.setMapType(savedMapType);
    }

    private void startRealTimeTracking() {
        // FIX: was duplicated, line-for-line identical logic between
        // this file and HomeActivity -- now shared via
        // FallbackPollScheduler. Behavior is unchanged.
        if (fallbackPollScheduler == null) fallbackPollScheduler = new FallbackPollScheduler(handler);
        fallbackPollScheduler.start(
                () -> realtimeClient != null && realtimeClient.isConnected(),
                this::fetchLocationData,
                null // no extra-per-tick action here, unlike HomeActivity's fetchDashboard()
        );

        // FIX: fetchVehiclesTabList() was previously only ever called once,
        // at initial load (plus once more after a delete) -- meaning the
        // switcher list's speed/status went stale the moment the screen
        // opened, while the "Currently Tracking" panel kept updating every
        // second via a completely separate pipeline. That's exactly why the
        // same vehicle could show two different speeds at the same moment.
        if (vehicleListRefreshRunnable != null) handler.removeCallbacks(vehicleListRefreshRunnable);
        vehicleListRefreshRunnable = new Runnable() {
            @Override public void run() {
                fetchVehiclesTabList();
                handler.postDelayed(this, VEHICLE_LIST_REFRESH_INTERVAL_MS);
            }
        };
        handler.postDelayed(vehicleListRefreshRunnable, VEHICLE_LIST_REFRESH_INTERVAL_MS);
    }

    private void handlePushedLocation(RealtimeLocationPayload payload) {
        if (payload.getVehicleId() == null || mMap == null || !hasRealVehicle) return;
        // FIX: guards against a message already in flight from the OLD
        // realtime connection landing just after we've reconnected to a
        // newly-selected vehicle -- without this, that one stale message
        // could still briefly flash the previous vehicle's data.
        if (selectedVehicleId == null || !selectedVehicleId.equalsIgnoreCase(payload.getVehicleId())) return;
        LatLng pos = new LatLng(payload.getLatitude(), payload.getLongitude());
        if (pos.latitude == 0 && pos.longitude == 0) return;

        trailRenderer.updatePosition(pos, (float) payload.getHeading(), selectedVehicleName);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));

        if (tvVehicleNameCollapsed != null) tvVehicleNameCollapsed.setText(selectedVehicleName);

        addressResolver.resolveAddress(pos.latitude, pos.longitude, address -> {
            if (tvCollapsedAddress != null) tvCollapsedAddress.setText(address);
            if (tvExpandedAddress != null) tvExpandedAddress.setText(address);
        });

        String status = payload.getSpeed() > MOVEMENT_SPEED_THRESHOLD_KMH
                ? "Moving (" + (int) payload.getSpeed() + " km/h)"
                : (payload.isIgnitionOn() ? "Idle" : "Parked");
        int color = payload.getSpeed() > MOVEMENT_SPEED_THRESHOLD_KMH ? ContextCompat.getColor(this, com.example.letstracklanka.R.color.status_moving) : Color.parseColor("#1976D2");
        if (tvCollapsedStatus != null) { tvCollapsedStatus.setText(status); tvCollapsedStatus.setTextColor(color); }
        if (tvExpandedStatus != null) { tvExpandedStatus.setText(status); tvExpandedStatus.setTextColor(color); }

        int dotColor = payload.isIgnitionOn() ? ContextCompat.getColor(this, com.example.letstracklanka.R.color.status_success) : Color.parseColor("#E53935");
        if (dotIgnition != null) dotIgnition.setCardBackgroundColor(dotColor);
        if (dotAC != null) dotAC.setCardBackgroundColor(dotColor);

        if (tvLastUpdated != null) tvLastUpdated.setText(String.format(Locale.getDefault(), "Sync: %s", new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(new Date())));
    }

    private void fetchLocationData() {
        if (mMap == null || !hasRealVehicle || selectedVehicleId == null) return;

        trackingApi.getVehicleLocation(selectedVehicleId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        LocationResponse loc = extractObject(body.string(), LocationResponse.class);
                        if (loc == null || loc.getVehicleId() == null) return;

                        LatLng pos = new LatLng(loc.getLatitude(), loc.getLongitude());
                        if (pos.latitude != 0 || pos.longitude != 0) {
                            lastKnownPosition = pos;
                            trailRenderer.updatePosition(pos, loc.getHeading(), selectedVehicleName);
                            updateUI(loc);

                            if (cameraFollowPendingForSwitch && mMap != null) {
                                cameraFollowPendingForSwitch = false;
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));
                            }
                        }
                    } else if (response.code() == 404) {
                        Log.d("VehiclesActivity", "No current location yet for " + selectedVehicleId);
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
        if (pos.latitude == 0 && pos.longitude == 0) return;
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));

        if (tvVehicleNameCollapsed != null) tvVehicleNameCollapsed.setText(selectedVehicleName);

        addressResolver.resolveAddress(pos.latitude, pos.longitude, address -> {
            if (tvCollapsedAddress != null) tvCollapsedAddress.setText(address);
            if (tvExpandedAddress != null) tvExpandedAddress.setText(address);
        });

        boolean isStale = loc.getMinutesSinceUpdate() > ONLINE_THRESHOLD_MINUTES;
        String status;
        int color;
        if (isStale) {
            status = "Offline";
            color = Color.parseColor("#F59E0B");
        } else if (loc.getSpeed() > MOVEMENT_SPEED_THRESHOLD_KMH) {
            status = "Moving (" + (int) loc.getSpeed() + " km/h)";
            color = ContextCompat.getColor(this, com.example.letstracklanka.R.color.status_moving);
        } else {
            status = loc.isIgnitionOn() ? "Idle" : "Parked";
            color = Color.parseColor("#1976D2");
        }
        if (tvCollapsedStatus != null) { tvCollapsedStatus.setText(status); tvCollapsedStatus.setTextColor(color); }
        if (tvExpandedStatus != null) { tvExpandedStatus.setText(status); tvExpandedStatus.setTextColor(color); }

        int dotColor = loc.isIgnitionOn() ? ContextCompat.getColor(this, com.example.letstracklanka.R.color.status_success) : Color.parseColor("#E53935");
        if (dotIgnition != null) dotIgnition.setCardBackgroundColor(dotColor);
        if (dotAC != null) dotAC.setCardBackgroundColor(dotColor);

        if (tvLastUpdated != null) tvLastUpdated.setText(String.format(Locale.getDefault(), "Sync: %s", new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(new Date())));
    }

    private <T> List<T> parseList(String json, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                list = gson.fromJson(root.getAsJsonArray("data"), TypeToken.getParameterized(List.class, clazz).getType());
            } else if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                list.add(gson.fromJson(root.getAsJsonObject("data"), clazz));
            }
        } catch (Exception e) {
            Log.e("VehiclesActivity", "Error parsing list JSON", e);
        }
        return list;
    }

    private <T> T extractObject(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), clazz);
            }
            return null;
        } catch (Exception e) {
            Log.e("VehiclesActivity", "Error parsing object JSON", e);
            return null;
        }
    }

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

    private void registerNetworkMonitor() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> showRetryDialog("No internet connection", VehiclesActivity.this::loadUserData));
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

    @Override protected void onDestroy() {
        super.onDestroy();
        if (fallbackPollScheduler != null) fallbackPollScheduler.stop();
        if (vehicleListRefreshRunnable != null) handler.removeCallbacks(vehicleListRefreshRunnable);
        // Same blocking-call concern as the reconnect fix above: stop() can
        // block up to 3 seconds. onDestroy() runs on the main thread too, so
        // this is pushed to a background thread rather than left inline.
        // Pre-existing code (not introduced by this session's changes), but
        // worth fixing now that stop()'s actual blocking behavior is confirmed.
        RealtimeLocationClient clientToStop = realtimeClient;
        if (clientToStop != null) {
            new Thread(clientToStop::stop, "RealtimeClient-Stop-OnDestroy").start();
        }

        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        }
    }
}