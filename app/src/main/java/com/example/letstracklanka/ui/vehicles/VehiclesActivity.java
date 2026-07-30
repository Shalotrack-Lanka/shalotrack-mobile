package com.example.letstracklanka.ui.vehicles;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.ui.main.AddressResolver;
import com.example.letstracklanka.ui.main.HomeActivity;
import com.example.letstracklanka.ui.history.TripHistoryActivity;
import com.example.letstracklanka.ui.main.TagsActivity;
import com.example.letstracklanka.ui.main.CirclesActivity;
import com.example.letstracklanka.ui.main.VehicleTrailRenderer;
import com.example.letstracklanka.ui.main.RealtimeLocationClient;
import com.example.letstracklanka.ui.main.RealtimeLocationPayload;
import com.example.letstracklanka.ui.main.AlertsActivity;
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
    private Runnable trackingRunnable;
    private final int UPDATE_INTERVAL = 1000;

    private String currentCustomerId = null;
    private String selectedVehicleId = null;
    private String selectedVehicleName = "No vehicle yet";
    private boolean hasRealVehicle = false;

    private VehicleResponse selectedVehicle = null;
    private LatLng lastKnownPosition = null;

    private View errorBanner;
    private TextView tvErrorBannerMessage, tvErrorBannerRetry;
    private ConnectivityManager.NetworkCallback networkCallback;

    private static final String MAP_PREFS_NAME = "ShaloTrackMapPrefs";
    private static final double MOVEMENT_SPEED_THRESHOLD_KMH = 7.0;
    private static final long ONLINE_THRESHOLD_MINUTES = 10;
    private static final String MAP_TYPE_PREF_KEY = "selected_map_type";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicles);

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
        layoutCollapsed = findViewById(R.id.layoutCollapsed);
        layoutExpanded = findViewById(R.id.layoutExpanded);
        layoutLeftFabs = findViewById(R.id.layoutLeftFabs);
        gridMenu = findViewById(R.id.gridMenu);
        fabAdd = findViewById(R.id.fabAdd);
        fabHistory = findViewById(R.id.fabHistory);

        if (fabHistory != null) {
            fabHistory.setOnClickListener(v -> openTripHistory());
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
                Intent intent = new Intent(VehiclesActivity.this, AlertsActivity.class);
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
                Intent intent = new Intent(VehiclesActivity.this, HomeActivity.class);
                intent.putExtra("open_drawer", true);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
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
            btnMenuValue.setOnClickListener(v ->
                    Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show());
        }

        View btnMenuPlaces = findViewById(R.id.btnMenuPlaces);
        if (btnMenuPlaces != null) {
            btnMenuPlaces.setOnClickListener(v ->
                    Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show());
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
    }

    private void openTripHistory() {
        Intent intent = new Intent(VehiclesActivity.this, TripHistoryActivity.class);
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

    private void showVehicleDetails() {
        if (selectedVehicle == null) {
            Toast.makeText(this, "Vehicle details not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String message = "Vehicle Number: " + safe(selectedVehicle.getVehicleNumber()) + "\n" +
                "Make: " + safe(selectedVehicle.getMake()) + "\n" +
                "Model: " + safe(selectedVehicle.getModel()) + "\n" +
                "IMEI: " + (selectedVehicle.hasGpsDevice() && selectedVehicle.getImei() != null
                ? selectedVehicle.getImei() : "Not linked");

        new AlertDialog.Builder(this)
                .setTitle(selectedVehicleName)
                .setMessage(message)
                .setPositiveButton("Close", null)
                .show();
    }

    private String safe(String value) {
        return value != null ? value : "--";
    }

    private void showCallCenterBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_call_center, null);
        dialog.setContentView(view);

        ViewPager2 viewPager = view.findViewById(R.id.viewPagerCallCenter);
        ImageView btnClose = view.findViewById(R.id.btnCloseCallCenter);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        View btnCloseBottom = view.findViewById(R.id.btnCallCenterClose);
        if (btnCloseBottom != null) btnCloseBottom.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
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
                        trailRenderer.loadInitialTrail(selectedVehicleId, () -> {});

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
        if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable);
        trackingRunnable = new Runnable() {
            @Override public void run() {
                fetchLocationData();
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        handler.post(trackingRunnable);
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
        if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable);
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