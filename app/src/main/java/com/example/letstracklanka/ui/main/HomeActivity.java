package com.example.letstracklanka.ui.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.data.model.EmergencyContactResponse;
import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.model.RegisterFcmTokenRequest;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.ui.common.BottomNav;
import com.example.letstracklanka.ui.common.EdgeToEdge;
import com.example.letstracklanka.ui.common.EmptyState;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.NetworkWatcher;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.ui.common.TimeText;
import com.example.letstracklanka.ui.common.VehicleMarkers;
import com.example.letstracklanka.ui.common.VehicleStatus;
import com.example.letstracklanka.ui.contacts.EmergencyContactsActivity;
import com.example.letstracklanka.ui.history.TripHistoryActivity;
import com.example.letstracklanka.ui.vehicles.AddVehicleActivity;
import com.example.letstracklanka.ui.vehicles.VehicleListActivity;
import com.example.letstracklanka.ui.vehicles.VehicleListAdapter;
import com.example.letstracklanka.ui.vehicles.VehiclesActivity;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.CustomerSession;
import com.example.letstracklanka.utils.SecureScreen;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Map tab (Home). Live map of every vehicle, the followed vehicle in a bottom sheet.
 *
 * Location is ALWAYS the vehicle's (tracker) position, never the phone's GPS.
 *
 * Network budget (the old screen polled the full dashboard every SECOND, in the background too):
 *   - dashboard (all vehicles' status)  every 15 s, only while this screen is visible
 *   - followed vehicle's position        live push (SignalR); 5 s poll only while the push is down,
 *                                        30 s safety poll while it is up
 *   - tracker health (battery, signal)   every 60 s, only while visible
 * Everything stops in onStop and resumes in onStart.
 */
public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "HomeActivity";

    private static final long DASHBOARD_REFRESH_MS = 15_000;
    private static final long POLL_WHEN_PUSH_DOWN_MS = 5_000;
    private static final long POLL_WHEN_PUSH_UP_MS = 30_000;
    private static final long HEALTH_REFRESH_MS = 60_000;
    private static final float FOLLOW_ZOOM = 15.5f;

    private static final String MAP_PREFS_NAME = "ShaloTrackMapPrefs";
    private static final String MAP_TYPE_PREF_KEY = "selected_map_type";
    private static final long SOS_HOLD_DURATION_MS = 3000;

    private ApiService api;
    private ShaloTrackApi trackingApi;
    private AddressResolver addressResolver;

    private GoogleMap map;
    private VehicleTrailRenderer trail;
    private RealtimeLocationClient realtime;
    private final Map<String, Marker> otherMarkers = new HashMap<>();

    private String customerId;
    private final List<DashboardVehicle> vehicles = new ArrayList<>();
    @Nullable private DashboardVehicle selected;
    @Nullable private LatLng selectedPosition;
    private double liveSpeed = 0;
    private boolean liveIgnition = false;
    @Nullable private String liveUpdatedAt;
    private boolean followCamera = true;
    private boolean pendingCameraJump = true;
    private boolean firstDashboardDone = false;
    private boolean dashboardLoading = false;

    // Views
    private InlineBanner banner;
    private EmptyState empty;
    private View selectedGroup, mapTypePanel, tvOtherVehiclesLabel;
    private TextView tvFleetCount, tvCountMoving, tvCountStopped, tvCountOffline;
    private TextView tvSelPlate, tvSelModel, tvSelStatus, tvSelSpeed, tvSelSpeedUnit, tvSelAddress, tvSelUpdated;
    private TextView tvFactIgnition, tvFactBattery, tvFactSignal;
    private RecyclerView recyclerOthers;
    private VehicleListAdapter othersAdapter;
    private BottomSheetBehavior<NestedScrollView> sheet;
    private View mapTypeNormal, mapTypeSatellite, mapTypeTerrain, mapTypeHybrid;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private CountDownTimer sosHoldTimer;

    private final Runnable dashboardTick = new Runnable() {
        @Override
        public void run() {
            fetchDashboard();
            handler.postDelayed(this, DASHBOARD_REFRESH_MS);
        }
    };
    private final Runnable locationTick = new Runnable() {
        @Override
        public void run() {
            fetchSelectedLocation();
            boolean pushUp = realtime != null && realtime.isConnected();
            handler.postDelayed(this, pushUp ? POLL_WHEN_PUSH_UP_MS : POLL_WHEN_PUSH_DOWN_MS);
        }
    };
    private final Runnable healthTick = new Runnable() {
        @Override
        public void run() {
            fetchSelectedHealth();
            handler.postDelayed(this, HEALTH_REFRESH_MS);
        }
    };
    private final Runnable agoTick = new Runnable() {
        @Override
        public void run() {
            renderUpdatedAgo();
            handler.postDelayed(this, 10_000);
        }
    };

    // ════════════════════════════════════════════════════════════
    // Lifecycle
    // ════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        SecureScreen.guardTouches(this);
        requestNotificationPermissionIfNeeded();

        api = ApiClient.getClient().create(ApiService.class);
        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);
        addressResolver = new AddressResolver(this);

        bindViews();
        BottomNav.bind(this, BottomNav.Tab.MAP);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        NetworkWatcher.watch(this, new NetworkWatcher.Listener() {
            @Override
            public void onLost() {
                banner.showOffline(R.string.st_banner_offline_map, null);
            }

            @Override
            public void onRestored() {
                banner.hide();
                if (customerId == null) loadCustomer(); else fetchDashboard();
            }
        });

        loadCustomer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (customerId != null) startRefreshing();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopRefreshing();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRefreshing();
        if (sosHoldTimer != null) sosHoldTimer.cancel();
        if (realtime != null) realtime.stop();
    }

    private void startRefreshing() {
        stopRefreshing();
        handler.post(dashboardTick);
        handler.post(agoTick);
        if (selected != null) {
            handler.post(locationTick);
            handler.post(healthTick);
        }
    }

    private void stopRefreshing() {
        handler.removeCallbacks(dashboardTick);
        handler.removeCallbacks(locationTick);
        handler.removeCallbacks(healthTick);
        handler.removeCallbacks(agoTick);
    }

    // ════════════════════════════════════════════════════════════
    // Views
    // ════════════════════════════════════════════════════════════

    private void bindViews() {
        EdgeToEdge.marginTop(findViewById(R.id.homeTopBar));
        banner = InlineBanner.in(this);
        empty = new EmptyState(findViewById(R.id.emptyState));

        tvFleetCount = findViewById(R.id.tvFleetCount);
        tvCountMoving = findViewById(R.id.tvCountMoving);
        tvCountStopped = findViewById(R.id.tvCountStopped);
        tvCountOffline = findViewById(R.id.tvCountOffline);
        tvCountMoving.setCompoundDrawablesRelativeWithIntrinsicBounds(VehicleStatus.MOVING.dotDrawable(this, 7), null, null, null);
        tvCountStopped.setCompoundDrawablesRelativeWithIntrinsicBounds(VehicleStatus.PARKED.dotDrawable(this, 7), null, null, null);
        tvCountOffline.setCompoundDrawablesRelativeWithIntrinsicBounds(VehicleStatus.OFFLINE.dotDrawable(this, 7), null, null, null);

        selectedGroup = findViewById(R.id.selectedVehicleGroup);
        tvSelPlate = findViewById(R.id.tvSelPlate);
        tvSelModel = findViewById(R.id.tvSelModel);
        tvSelStatus = findViewById(R.id.tvSelStatus);
        tvSelSpeed = findViewById(R.id.tvSelSpeed);
        tvSelSpeedUnit = findViewById(R.id.tvSelSpeedUnit);
        tvSelAddress = findViewById(R.id.tvSelAddress);
        tvSelUpdated = findViewById(R.id.tvSelUpdated);
        tvFactIgnition = findViewById(R.id.tvFactIgnition);
        tvFactBattery = findViewById(R.id.tvFactBattery);
        tvFactSignal = findViewById(R.id.tvFactSignal);
        tvOtherVehiclesLabel = findViewById(R.id.tvOtherVehiclesLabel);

        NestedScrollView sheetView = findViewById(R.id.homeSheet);
        sheet = BottomSheetBehavior.from(sheetView);
        sheet.setState(BottomSheetBehavior.STATE_COLLAPSED);

        recyclerOthers = findViewById(R.id.recyclerHomeVehicles);
        recyclerOthers.setLayoutManager(new LinearLayoutManager(this));
        othersAdapter = new VehicleListAdapter(new ArrayList<>(), this::selectVehicle, null);
        recyclerOthers.setAdapter(othersAdapter);

        mapTypePanel = findViewById(R.id.mapTypePanel);
        mapTypeNormal = findViewById(R.id.mapTypeNormal);
        mapTypeSatellite = findViewById(R.id.mapTypeSatellite);
        mapTypeTerrain = findViewById(R.id.mapTypeTerrain);
        mapTypeHybrid = findViewById(R.id.mapTypeHybrid);
        findViewById(R.id.btnLayers).setOnClickListener(v ->
                mapTypePanel.setVisibility(mapTypePanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        mapTypeNormal.setOnClickListener(v -> setMapType(GoogleMap.MAP_TYPE_NORMAL));
        mapTypeSatellite.setOnClickListener(v -> setMapType(GoogleMap.MAP_TYPE_SATELLITE));
        mapTypeTerrain.setOnClickListener(v -> setMapType(GoogleMap.MAP_TYPE_TERRAIN));
        mapTypeHybrid.setOnClickListener(v -> setMapType(GoogleMap.MAP_TYPE_HYBRID));

        findViewById(R.id.btnRecenter).setOnClickListener(v -> recenter());
        findViewById(R.id.homeSummary).setOnClickListener(v -> fitAllVehicles());

        MaterialButton btnShare = findViewById(R.id.btnShareLocation);
        btnShare.setOnClickListener(v -> shareSelectedLocation());
        findViewById(R.id.btnTrips).setOnClickListener(v -> openTrips());
        findViewById(R.id.btnSos).setOnClickListener(v -> showSosSheet());
        findViewById(R.id.btnVehicleDetails).setOnClickListener(v -> openVehiclePage());

        selectedGroup.setVisibility(View.GONE);
        tvOtherVehiclesLabel.setVisibility(View.GONE);
        recyclerOthers.setVisibility(View.GONE);
    }

    // ════════════════════════════════════════════════════════════
    // Map
    // ════════════════════════════════════════════════════════════

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setMapToolbarEnabled(false);
        map.getUiSettings().setCompassEnabled(false);
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.getUiSettings().setRotateGesturesEnabled(false);
        map.setPadding(0, 0, 0, dp(292));
        trail = new VehicleTrailRenderer(this, map, trackingApi);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(7.8731, 80.7718), 7f)); // Sri Lanka

        map.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) followCamera = false;
        });
        map.setOnMarkerClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof String) {
                for (DashboardVehicle v : vehicles) {
                    if (((String) tag).equalsIgnoreCase(v.getVehicleId())) {
                        selectVehicle(v);
                        return true;
                    }
                }
            }
            return true;   // never show the default info window / toolbar
        });
        map.setOnMapClickListener(latLng -> mapTypePanel.setVisibility(View.GONE));

        int savedType = getSharedPreferences(MAP_PREFS_NAME, MODE_PRIVATE).getInt(MAP_TYPE_PREF_KEY, GoogleMap.MAP_TYPE_NORMAL);
        setMapType(savedType);

        if (!vehicles.isEmpty()) renderMarkers();
        if (selected != null) {
            trail.setVehicleTypeForNextMarker(selected.getVehicleType());
            trail.loadInitialTrail(selected.getVehicleId(), () -> { });
            if (selected.hasPosition()) {
                applyPosition(new LatLng(selected.getLatitude(), selected.getLongitude()), selected.getHeading());
            }
        }
    }

    private void setMapType(int type) {
        mapTypePanel.setVisibility(View.GONE);
        mapTypeNormal.setSelected(type == GoogleMap.MAP_TYPE_NORMAL);
        mapTypeSatellite.setSelected(type == GoogleMap.MAP_TYPE_SATELLITE);
        mapTypeTerrain.setSelected(type == GoogleMap.MAP_TYPE_TERRAIN);
        mapTypeHybrid.setSelected(type == GoogleMap.MAP_TYPE_HYBRID);
        getSharedPreferences(MAP_PREFS_NAME, MODE_PRIVATE).edit().putInt(MAP_TYPE_PREF_KEY, type).apply();
        if (map == null) return;
        map.setMapType(type);
        // The muted Daylight style only applies to the normal map (satellite imagery is never styled).
        try {
            map.setMapStyle(type == GoogleMap.MAP_TYPE_NORMAL
                    ? MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_daylight) : null);
        } catch (Exception e) {
            Log.w(TAG, "map style not applied", e);
        }
    }

    private void recenter() {
        followCamera = true;
        LatLng target = trail != null && trail.getMarkerPosition() != null ? trail.getMarkerPosition() : selectedPosition;
        if (map != null && target != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, FOLLOW_ZOOM));
        } else if (selected != null) {
            banner.showError(R.string.st_home_no_position_yet, null);
        }
    }

    /** Tapping the fleet summary zooms out to show every vehicle with a position. */
    private void fitAllVehicles() {
        if (map == null) return;
        LatLngBounds.Builder b = new LatLngBounds.Builder();
        int count = 0;
        for (DashboardVehicle v : vehicles) {
            if (v.hasPosition()) {
                b.include(new LatLng(v.getLatitude(), v.getLongitude()));
                count++;
            }
        }
        if (count == 0) return;
        followCamera = false;
        if (count == 1) {
            recenter();
            return;
        }
        try {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), dp(64)));
        } catch (IllegalStateException e) {
            Log.w(TAG, "fitAllVehicles before layout", e);
        }
    }

    /** Markers for every vehicle except the followed one (that one is the animated trail marker). */
    private void renderMarkers() {
        if (map == null) return;
        Set<String> keep = new HashSet<>();
        for (DashboardVehicle v : vehicles) {
            String id = v.getVehicleId() != null ? v.getVehicleId().toLowerCase(Locale.US) : null;
            if (id == null || !v.hasPosition()) continue;
            if (selected != null && id.equalsIgnoreCase(selected.getVehicleId())) continue;
            keep.add(id);
            LatLng pos = new LatLng(v.getLatitude(), v.getLongitude());
            Marker m = otherMarkers.get(id);
            if (m == null) {
                m = map.addMarker(new MarkerOptions()
                        .position(pos)
                        .anchor(0.5f, 0.5f)
                        .icon(VehicleMarkers.icon(this, v))
                        .title(Plates.plain(v.getVehicleNumber())));
                if (m != null) {
                    m.setTag(id);
                    otherMarkers.put(id, m);
                }
            } else {
                m.setPosition(pos);
                m.setIcon(VehicleMarkers.icon(this, v));
            }
        }
        for (String id : new ArrayList<>(otherMarkers.keySet())) {
            if (!keep.contains(id)) {
                Marker m = otherMarkers.remove(id);
                if (m != null) m.remove();
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // Data
    // ════════════════════════════════════════════════════════════

    private void loadCustomer() {
        CustomerSession.get(this, new CustomerSession.Callback2() {
            @Override
            public void onCustomer(@NonNull String id) {
                customerId = id;
                registerFcmToken();
                startRefreshing();
            }

            @Override
            public void onError(int httpCode) {
                if (httpCode == 404) {
                    // Signed in to Firebase but no customer profile: send them to finish registration.
                    startActivity(new Intent(HomeActivity.this, com.example.letstracklanka.ui.auth.RouteAfterLoginActivity.class));
                    finish();
                    return;
                }
                showFailure(httpCode, HomeActivity.this::loadCustomer);
            }
        });
    }

    private void fetchDashboard() {
        if (customerId == null || dashboardLoading) return;
        dashboardLoading = true;
        api.getCustomerDashboard(customerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                dashboardLoading = false;
                if (isFinishing() || isDestroyed()) return;
                try (ResponseBody body = response.body()) {
                    if (response.code() == 403 || response.code() == 404) {
                        CustomerSession.forget(HomeActivity.this);
                        customerId = null;
                        stopRefreshing();
                        loadCustomer();
                        return;
                    }
                    if (!response.isSuccessful() || body == null) {
                        showFailure(response.code(), HomeActivity.this::fetchDashboard);
                        return;
                    }
                    if (!banner.isShowing() || NetworkWatcher.isOnline(HomeActivity.this)) banner.hide();
                    onDashboard(parseVehicles(body.string()));
                } catch (Exception e) {
                    Log.e(TAG, "dashboard parse error", e);
                    showFailure(-1, HomeActivity.this::fetchDashboard);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                dashboardLoading = false;
                if (isFinishing() || isDestroyed()) return;
                showFailure(0, HomeActivity.this::fetchDashboard);
            }
        });
    }

    private void onDashboard(List<DashboardVehicle> list) {
        vehicles.clear();
        vehicles.addAll(list);
        firstDashboardDone = true;
        renderSummary();

        if (vehicles.isEmpty()) {
            selected = null;
            selectedGroup.setVisibility(View.GONE);
            tvOtherVehiclesLabel.setVisibility(View.GONE);
            recyclerOthers.setVisibility(View.GONE);
            empty.show(getString(R.string.st_home_empty_title), getString(R.string.st_home_empty_body),
                    getString(R.string.st_vehicles_add_first), this::openAddVehicle);
            renderMarkers();
            return;
        }
        empty.hide();

        DashboardVehicle next = pickSelected();
        boolean changed = selected == null || next == null
                || !next.getVehicleId().equalsIgnoreCase(selected.getVehicleId());
        if (changed && next != null) {
            selectVehicle(next);
        } else if (next != null) {
            // Same vehicle: refresh its status from the dashboard, keep live position from the push.
            selected = next;
            renderSelected();
        }

        othersAdapter.updateVehicles(vehicles);
        othersAdapter.setExcludedVehicleId(selected != null ? selected.getVehicleId() : null);
        boolean hasOthers = othersAdapter.getItemCount() > 0;
        tvOtherVehiclesLabel.setVisibility(hasOthers ? View.VISIBLE : View.GONE);
        recyclerOthers.setVisibility(hasOthers ? View.VISIBLE : View.GONE);
        renderMarkers();
    }

    /** The remembered vehicle if it is still in the list, otherwise the best first one to follow. */
    @Nullable
    private DashboardVehicle pickSelected() {
        String remembered = VehicleListActivity.selectedVehicleId(this);
        if (remembered != null) {
            for (DashboardVehicle v : vehicles) if (remembered.equalsIgnoreCase(v.getVehicleId())) return v;
        }
        DashboardVehicle firstWithPosition = null;
        for (DashboardVehicle v : vehicles) {
            if (!v.isDemo() && !v.isShared() && VehicleStatus.of(v).isReporting()) return v;
            if (firstWithPosition == null && v.hasPosition() && !v.isDemo()) firstWithPosition = v;
        }
        if (firstWithPosition != null) return firstWithPosition;
        return vehicles.isEmpty() ? null : vehicles.get(0);
    }

    /** Follow another vehicle on this screen (no navigation away). */
    private void selectVehicle(DashboardVehicle v) {
        boolean switching = selected == null || !v.getVehicleId().equalsIgnoreCase(selected.getVehicleId());
        selected = v;
        VehicleListActivity.rememberSelected(this, v.getVehicleId());
        if (!switching) {
            recenter();
            return;
        }

        liveSpeed = v.getSpeed();
        liveIgnition = v.isIgnitionOn();
        liveUpdatedAt = v.getLastUpdate();
        selectedPosition = v.hasPosition() ? new LatLng(v.getLatitude(), v.getLongitude()) : null;
        followCamera = true;
        pendingCameraJump = true;
        tvSelAddress.setText(selectedPosition != null ? getString(R.string.st_home_finding_place) : getString(R.string.st_home_no_position));
        tvFactBattery.setVisibility(View.INVISIBLE);
        tvFactSignal.setVisibility(View.INVISIBLE);

        if (trail != null) {
            trail.resetForVehicleSwitch(v.getVehicleType());
            trail.loadInitialTrail(v.getVehicleId(), () -> { });
            if (selectedPosition != null) applyPosition(selectedPosition, v.getHeading());
        }

        if (realtime == null) {
            realtime = new RealtimeLocationClient();
            realtime.connect(v.getVehicleId(), payload -> runOnUiThread(() -> onLivePush(payload)));
        } else {
            realtime.switchVehicle(v.getVehicleId());
        }

        othersAdapter.setExcludedVehicleId(v.getVehicleId());
        boolean hasOthers = othersAdapter.getItemCount() > 0;
        tvOtherVehiclesLabel.setVisibility(hasOthers ? View.VISIBLE : View.GONE);
        recyclerOthers.setVisibility(hasOthers ? View.VISIBLE : View.GONE);
        renderMarkers();
        renderSelected();

        handler.removeCallbacks(locationTick);
        handler.removeCallbacks(healthTick);
        // A dashboard reply can land just after onStop: never restart polling for a screen that is not visible.
        if (getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            handler.post(locationTick);
            handler.post(healthTick);
        }
        if (sheet.getState() != BottomSheetBehavior.STATE_COLLAPSED) sheet.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    private void fetchSelectedLocation() {
        DashboardVehicle v = selected;
        if (v == null || !v.hasDevice()) return;
        String id = v.getVehicleId();
        trackingApi.getVehicleLocation(id).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null || isFinishing()) return;
                    if (selected == null || !id.equalsIgnoreCase(selected.getVehicleId())) return;
                    LocationResponse loc = ApiJson.object(body.string(), LocationResponse.class);
                    if (loc == null || (loc.getLatitude() == 0 && loc.getLongitude() == 0)) return;
                    liveSpeed = loc.getSpeed();
                    liveIgnition = loc.isIgnitionOn();
                    if (loc.getLastUpdate() != null) liveUpdatedAt = loc.getLastUpdate();
                    applyPosition(new LatLng(loc.getLatitude(), loc.getLongitude()), loc.getHeading());
                    renderSelected();
                } catch (Exception e) {
                    Log.w(TAG, "location parse error", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                // The dashboard refresh reports connection problems; one missed poll needs no banner.
            }
        });
    }

    private void onLivePush(RealtimeLocationPayload p) {
        if (p == null || p.getVehicleId() == null || selected == null) return;
        if (!p.getVehicleId().equalsIgnoreCase(selected.getVehicleId())) return;
        if (p.getLatitude() == 0 && p.getLongitude() == 0) return;
        liveSpeed = p.getSpeed();
        liveIgnition = p.isIgnitionOn();
        liveUpdatedAt = p.getLastUpdate() != null ? p.getLastUpdate() : java.time.Instant.now().toString();
        applyPosition(new LatLng(p.getLatitude(), p.getLongitude()), (float) p.getHeading());
        renderSelected();
    }

    private void applyPosition(LatLng pos, float heading) {
        selectedPosition = pos;
        if (trail != null && selected != null) {
            trail.updatePosition(pos, heading, Plates.plain(selected.getVehicleNumber()));
        }
        if (map != null && (followCamera || pendingCameraJump)) {
            if (pendingCameraJump) {
                pendingCameraJump = false;
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, FOLLOW_ZOOM));
            } else {
                map.animateCamera(CameraUpdateFactory.newLatLng(pos));
            }
        }
        addressResolver.resolveAddress(pos.latitude, pos.longitude, address -> {
            if (!isFinishing()) tvSelAddress.setText(address);
        });
    }

    /** Battery, mobile signal and power from the tracker's status record (same rules as Fleet). */
    private void fetchSelectedHealth() {
        DashboardVehicle v = selected;
        if (v == null || !v.hasDevice()) {
            tvFactBattery.setVisibility(View.INVISIBLE);
            tvFactSignal.setVisibility(View.INVISIBLE);
            return;
        }
        String id = v.getVehicleId();
        trackingApi.getVehicleStatus(id).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null || isFinishing()) return;
                    if (selected == null || !id.equalsIgnoreCase(selected.getVehicleId())) return;
                    JsonObject s = ApiJson.dataObject(body.string());
                    if (s == null) return;
                    renderHealth(s);
                } catch (Exception e) {
                    Log.w(TAG, "health parse error", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                // keep the last values
            }
        });
    }

    private void renderHealth(JsonObject s) {
        int battery = intOr(ApiJson.get(s, "batteryLevel"), -1);
        if (battery > 0) {
            tvFactBattery.setText(getString(R.string.st_fact_battery, Math.min(100, battery)));
            tintFact(tvFactBattery, battery <= 10 ? R.color.st_danger_text
                    : battery <= 20 ? R.color.st_status_offline_text : R.color.st_navy);
            tvFactBattery.setVisibility(View.VISIBLE);
        } else {
            tvFactBattery.setVisibility(View.INVISIBLE);
        }

        JsonElement power = ApiJson.get(s, "powerStatus");
        boolean unplugged = power != null && ("1".equals(power.getAsString())
                || "disconnected".equalsIgnoreCase(power.getAsString()));
        if (unplugged) {
            tvFactSignal.setText(R.string.st_fact_power_cut);
            tvFactSignal.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_st_plug_16, 0, 0, 0);
            tintFact(tvFactSignal, R.color.st_danger_text);
            tvFactSignal.setVisibility(View.VISIBLE);
            return;
        }
        int signal = intOr(ApiJson.get(s, "gpsSignal"), -1);   // the tracker's MOBILE signal, 0 to 4
        if (signal >= 0) {
            tvFactSignal.setText(signal <= 0 ? R.string.st_fact_signal_none
                    : signal <= 2 ? R.string.st_fact_signal_weak : R.string.st_fact_signal_good);
            tvFactSignal.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_st_signal_16, 0, 0, 0);
            tintFact(tvFactSignal, signal >= 3 ? R.color.st_navy : R.color.st_status_offline_text);
            tvFactSignal.setVisibility(View.VISIBLE);
        } else {
            tvFactSignal.setVisibility(View.INVISIBLE);
        }
    }

    private void tintFact(TextView tv, int colorRes) {
        tv.setCompoundDrawableTintList(ColorStateList.valueOf(ContextCompat.getColor(this, colorRes)));
    }

    private static int intOr(@Nullable JsonElement e, int fallback) {
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private void registerFcmToken() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) return;
            api.registerFcmToken(new RegisterFcmTokenRequest(task.getResult(), "android")).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    if (response.body() != null) response.body().close();
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    // retried on the next app start
                }
            });
        });
    }

    private static List<DashboardVehicle> parseVehicles(String json) {
        JsonObject data = ApiJson.dataObject(json);
        if (data == null) return new ArrayList<>();
        JsonArray arr = data.has("vehicles") && data.get("vehicles").isJsonArray() ? data.getAsJsonArray("vehicles") : null;
        if (arr == null) return new ArrayList<>();
        List<DashboardVehicle> list = new Gson().fromJson(arr, TypeToken.getParameterized(List.class, DashboardVehicle.class).getType());
        List<DashboardVehicle> clean = new ArrayList<>();
        if (list != null) for (DashboardVehicle v : list) if (v != null && v.getVehicleId() != null) clean.add(v);
        return clean;
    }

    private void showFailure(int httpCode, Runnable retry) {
        if (httpCode == 0 && !NetworkWatcher.isOnline(this)) {
            banner.showOffline(R.string.st_banner_offline_map, retry);
        } else if (firstDashboardDone) {
            banner.showError(R.string.st_home_refresh_failed, retry);
        } else {
            banner.showError(R.string.st_home_load_failed, retry);
        }
    }

    // ════════════════════════════════════════════════════════════
    // Rendering
    // ════════════════════════════════════════════════════════════

    private void renderSummary() {
        int moving = 0, stopped = 0, offline = 0;
        for (DashboardVehicle v : vehicles) {
            switch (VehicleStatus.of(v)) {
                case MOVING: moving++; break;
                case IDLING:
                case PARKED: stopped++; break;
                default: offline++; break;
            }
        }
        tvFleetCount.setText(getResources().getQuantityString(R.plurals.st_fleet_count, vehicles.size(), vehicles.size()));
        tvCountMoving.setText(String.valueOf(moving));
        tvCountStopped.setText(String.valueOf(stopped));
        tvCountOffline.setText(String.valueOf(offline));
        tvCountMoving.setVisibility(moving > 0 ? View.VISIBLE : View.GONE);
        tvCountStopped.setVisibility(stopped > 0 ? View.VISIBLE : View.GONE);
        tvCountOffline.setVisibility(offline > 0 ? View.VISIBLE : View.GONE);
        findViewById(R.id.homeSummary).setContentDescription(getString(R.string.st_home_summary_description,
                vehicles.size(), moving, stopped, offline));
    }

    private void renderSelected() {
        DashboardVehicle v = selected;
        if (v == null) {
            selectedGroup.setVisibility(View.GONE);
            return;
        }
        selectedGroup.setVisibility(View.VISIBLE);
        tvSelPlate.setText(Plates.format(this, v.getVehicleNumber()));
        String makeModel = ((v.getMake() != null ? v.getMake() : "") + " " + (v.getModel() != null ? v.getModel() : "")).trim();
        tvSelModel.setText(v.isShared() ? getString(R.string.st_vehicle_caption_shared,
                v.getOwnerName() != null ? v.getOwnerName() : getString(R.string.st_vehicle_someone)) : makeModel);

        // Live push wins over the 15 s dashboard for speed and ignition.
        VehicleStatus status = VehicleStatus.of(v);
        if (status.isReporting()) status = VehicleStatus.ofLive(liveSpeed, liveIgnition);
        status.applyToPill(tvSelStatus);

        if (status.isReporting()) {
            tvSelSpeed.setText(String.valueOf(Math.round(liveSpeed)));
            tvSelSpeed.setTextColor(ContextCompat.getColor(this, R.color.st_text_1));
        } else {
            tvSelSpeed.setText("–");
            tvSelSpeed.setTextColor(ContextCompat.getColor(this, R.color.st_text_4));
        }
        tvSelSpeedUnit.setVisibility(status.isReporting() ? View.VISIBLE : View.INVISIBLE);

        tvFactIgnition.setText(liveIgnition ? R.string.st_fact_ignition_on : R.string.st_fact_ignition_off);
        tintFact(tvFactIgnition, liveIgnition && status.isReporting() ? R.color.st_status_moving : R.color.st_text_3);
        tvFactIgnition.setVisibility(v.hasDevice() ? View.VISIBLE : View.INVISIBLE);

        if (!v.hasDevice()) {
            tvSelAddress.setText(R.string.st_home_no_tracker);
            tvSelUpdated.setText(R.string.st_home_no_tracker_hint);
        } else if (selectedPosition == null) {
            tvSelAddress.setText(R.string.st_home_no_position);
        }
        renderUpdatedAgo();
    }

    private void renderUpdatedAgo() {
        if (selected == null || !selected.hasDevice()) return;
        String when = liveUpdatedAt != null ? liveUpdatedAt : selected.getLastUpdate();
        VehicleStatus status = VehicleStatus.of(selected);
        tvSelUpdated.setText(status == VehicleStatus.OFFLINE
                ? getString(R.string.st_home_last_seen, TimeText.ago(this, when))
                : getString(R.string.st_home_updated, TimeText.ago(this, when)));
    }

    // ════════════════════════════════════════════════════════════
    // Actions
    // ════════════════════════════════════════════════════════════

    private void shareSelectedLocation() {
        LatLng pos = trail != null && trail.getMarkerPosition() != null ? trail.getMarkerPosition() : selectedPosition;
        if (selected == null || pos == null) {
            banner.showError(R.string.st_home_no_position_yet, null);
            return;
        }
        String link = String.format(Locale.US, "https://www.google.com/maps?q=%.6f,%.6f", pos.latitude, pos.longitude);
        String text = getString(R.string.st_home_share_text, Plates.plain(selected.getVehicleNumber()), link);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(send, getString(R.string.st_home_share_location)));
        } catch (Exception e) {
            banner.showError(R.string.st_error_no_share_app, null);
        }
    }

    private void openTrips() {
        if (selected == null) return;
        Intent i = new Intent(this, TripHistoryActivity.class);
        i.putExtra(TripHistoryActivity.EXTRA_VEHICLE_ID, selected.getVehicleId());
        i.putExtra(TripHistoryActivity.EXTRA_VEHICLE_NAME, Plates.plain(selected.getVehicleNumber()));
        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
    }

    private void openVehiclePage() {
        if (selected == null) return;
        Intent i = new Intent(this, VehiclesActivity.class);
        i.putExtra(VehiclesActivity.EXTRA_VEHICLE_ID, selected.getVehicleId());
        startActivity(i);
    }

    private void openAddVehicle() {
        if (customerId == null) return;
        Intent i = new Intent(this, AddVehicleActivity.class);
        i.putExtra(AddVehicleActivity.EXTRA_CUSTOMER_ID, customerId);
        startActivity(i);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    // ════════════════════════════════════════════════════════════
    // SOS: open the sheet, then HOLD 3 s to send (an accidental tap never sends an emergency alert)
    // ════════════════════════════════════════════════════════════

    private void showSosSheet() {
        if (selected == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_sos, null);
        dialog.setContentView(view);
        View close = view.findViewById(R.id.btnCloseSOS);
        if (close != null) close.setOnClickListener(v -> dialog.dismiss());
        View addContacts = view.findViewById(R.id.btnAddContacts);
        if (addContacts != null) addContacts.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, EmergencyContactsActivity.class));
        });
        TextView status = view.findViewById(R.id.tvSosStatus);
        View hold = view.findViewById(R.id.btnTapSOS);
        View pulse = view.findViewById(R.id.bgPulseCircle);
        if (hold != null) setupSosHold(hold, pulse, status, dialog);
        dialog.show();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupSosHold(View hold, @Nullable View pulse, @Nullable TextView status, BottomSheetDialog dialog) {
        hold.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (sosHoldTimer != null) sosHoldTimer.cancel();
                    sosHoldTimer = new CountDownTimer(SOS_HOLD_DURATION_MS, 50) {
                        @Override
                        public void onTick(long left) {
                            if (pulse == null) return;
                            float progress = 1f - ((float) left / SOS_HOLD_DURATION_MS);
                            pulse.setScaleX(1f + progress * 0.25f);
                            pulse.setScaleY(1f + progress * 0.25f);
                            pulse.setAlpha(1f - progress * 0.5f);
                        }

                        @Override
                        public void onFinish() {
                            resetPulse(pulse);
                            triggerSos(dialog, status);
                        }
                    };
                    sosHoldTimer.start();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (sosHoldTimer != null) {
                        sosHoldTimer.cancel();
                        sosHoldTimer = null;
                    }
                    resetPulse(pulse);
                    return true;
                default:
                    return false;
            }
        });
    }

    private static void resetPulse(@Nullable View pulse) {
        if (pulse == null) return;
        pulse.setScaleX(1f);
        pulse.setScaleY(1f);
        pulse.setAlpha(1f);
    }

    private void triggerSos(BottomSheetDialog dialog, @Nullable TextView status) {
        if (selected == null) return;
        if (status != null) {
            status.setVisibility(View.VISIBLE);
            status.setText(R.string.st_sos_sending);
        }
        api.triggerSOS(selected.getVehicleId()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.body() != null) response.body().close();
                if (response.isSuccessful()) {
                    dialog.dismiss();
                    showEmergencyContactsAfterSos();
                } else if (status != null) {
                    status.setText(R.string.st_sos_failed);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (status != null) status.setText(R.string.st_sos_failed_network);
            }
        });
    }

    /** After SOS: national numbers (119 Police, 1990 Suwa Seriya) and the customer's own contacts, one tap to call. */
    private void showEmergencyContactsAfterSos() {
        api.getMyEmergencyContacts().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                List<EmergencyContactResponse> contacts = new ArrayList<>();
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<EmergencyContactResponse> parsed = ApiJson.list(body.string(), EmergencyContactResponse.class);
                        if (parsed != null) contacts = parsed;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "emergency contacts parse error", e);
                }
                showSosContactsSheet(contacts);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                showSosContactsSheet(new ArrayList<>());
            }
        });
    }

    private void showSosContactsSheet(List<EmergencyContactResponse> contacts) {
        if (isFinishing() || isDestroyed()) return;
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_sos_contacts, null);
        dialog.setContentView(view);
        LinearLayout national = view.findViewById(R.id.nationalNumbersContainer);
        LinearLayout personal = view.findViewById(R.id.personalContactsContainer);
        View none = view.findViewById(R.id.tvNoContacts);
        if (national != null) {
            addCallRow(national, getString(R.string.st_sos_police), "119");
            addCallRow(national, getString(R.string.st_sos_ambulance), "1990");
        }
        if (personal != null) {
            if (contacts.isEmpty()) {
                if (none != null) none.setVisibility(View.VISIBLE);
            } else {
                for (EmergencyContactResponse c : contacts) addCallRow(personal, c.getName(), c.getPhoneNumber());
            }
        }
        dialog.show();
    }

    private void addCallRow(LinearLayout container, String title, String number) {
        View row = getLayoutInflater().inflate(R.layout.item_call_row, container, false);
        ((TextView) row.findViewById(R.id.tvCallTitle)).setText(title);
        ((TextView) row.findViewById(R.id.tvCallNumber)).setText(number);
        ImageView icon = row.findViewById(R.id.imgCallIcon);
        icon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        row.setContentDescription(getString(R.string.st_sos_call_description, title, number));
        row.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + number)));
            } catch (Exception e) {
                banner.showError(R.string.st_error_no_dialer, null);
            }
        });
        container.addView(row);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
