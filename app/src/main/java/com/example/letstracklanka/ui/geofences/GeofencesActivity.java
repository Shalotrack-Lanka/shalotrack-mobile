package com.example.letstracklanka.ui.geofences;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.data.model.GeofenceResponse;
import com.example.letstracklanka.data.model.UpdateGeofenceRequest;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.EdgeToEdge;
import com.example.letstracklanka.ui.common.EmptyState;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.NetworkWatcher;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.ui.common.VehicleMarkers;
import com.example.letstracklanka.ui.common.VehiclePickerSheet;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.SecureScreen;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Geofences (activity_geofences). All of the customer's areas, plus read-only ones on vehicles shared
 * with them. Opened for one vehicle (EXTRA_VEHICLE_ID) it lists the areas that apply to that vehicle
 * (its own and "all vehicles" ones), starts the map on that vehicle and scopes a new area to it.
 *
 * The map starts where the vehicles are (their last reported positions), never the phone's location.
 * Data is read on open and after every change; nothing is polled.
 */
public class GeofencesActivity extends AppCompatActivity implements GeofenceSheet.Host {

    public static final String EXTRA_VEHICLE_ID = "extra_vehicle_id";
    public static final String EXTRA_VEHICLE_NAME = "extra_vehicle_name";

    private static final LatLng SRI_LANKA = new LatLng(7.8731, 80.7718);
    private static final float SRI_LANKA_ZOOM = 7f;
    private static final float PLACE_ZOOM = 15.5f;

    private ApiService api;
    @Nullable private String vehicleId;
    @Nullable private String vehicleName;

    @Nullable private GoogleMap map;
    private boolean cameraPlaced;
    private final Map<String, Circle> circles = new HashMap<>();
    private final List<Marker> vehicleMarkers = new ArrayList<>();
    @Nullable private Circle preview;

    private final List<GeofenceResponse> all = new ArrayList<>();
    private final List<DashboardVehicle> vehicles = new ArrayList<>();
    private boolean geofencesLoaded;
    private boolean placing;

    private BottomSheetBehavior<View> sheetBehavior;
    private View topBar, placementBar, centerPin, hint;
    private RecyclerView recycler;
    private ProgressBar progress;
    private TextView tvSubtitle;
    private MaterialButton btnAdd;
    private GeofenceAdapter adapter;
    private InlineBanner banner;
    private EmptyState empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_geofences);
        SecureScreen.guardTouches(this);
        api = ApiClient.getClient().create(ApiService.class);

        vehicleId = trimToNull(getIntent().getStringExtra(EXTRA_VEHICLE_ID));
        vehicleName = trimToNull(getIntent().getStringExtra(EXTRA_VEHICLE_NAME));

        bindViews();

        SupportMapFragment fragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapGeofences);
        if (fragment != null) fragment.getMapAsync(this::onMapReady);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (placing) {
                    stopPlacing();
                } else {
                    finish();
                }
            }
        });

        NetworkWatcher.watch(this, new NetworkWatcher.Listener() {
            @Override
            public void onLost() {
                banner.showOffline(R.string.st_banner_offline_generic, null);
            }

            @Override
            public void onRestored() {
                banner.hide();
                if (!geofencesLoaded) loadGeofences();
            }
        });

        loadVehicles();
        loadGeofences();
    }

    private void bindViews() {
        topBar = findViewById(R.id.geofenceTopBar);
        EdgeToEdge.marginTop(topBar);
        placementBar = findViewById(R.id.placementBar);
        EdgeToEdge.padBottom(placementBar);
        centerPin = findViewById(R.id.imgCenterPin);
        hint = findViewById(R.id.tvPlacementHint);
        recycler = findViewById(R.id.recyclerGeofences);
        progress = findViewById(R.id.progressGeofences);
        tvSubtitle = findViewById(R.id.tvGeofencesSubtitle);
        btnAdd = findViewById(R.id.btnAddGeofence);
        banner = InlineBanner.in(this);
        empty = new EmptyState(findViewById(R.id.emptyState));

        View sheet = findViewById(R.id.geofenceSheet);
        EdgeToEdge.padBottom(recycler);
        sheetBehavior = BottomSheetBehavior.from(sheet);
        sheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
        sheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                updateMapPadding();
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                updateMapPadding();
            }
        });
        // Hiding the list is only for placing a new area; a swipe must not lose it.
        sheetBehavior.setHideable(false);

        adapter = new GeofenceAdapter(new GeofenceAdapter.Listener() {
            @Override
            public void onToggle(GeofenceResponse g, boolean active) {
                toggle(g, active);
            }

            @Override
            public void onOpen(GeofenceResponse g) {
                focus(g);
                if (g.isOwner()) GeofenceSheet.edit(GeofencesActivity.this, g, GeofencesActivity.this);
            }
        });
        recycler.setLayoutManager(new LinearLayoutManager(this));
        DividerItemDecoration divider = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        android.graphics.drawable.Drawable d = ContextCompat.getDrawable(this, R.drawable.bg_st_divider_inset);
        if (d != null) divider.setDrawable(d);
        recycler.addItemDecoration(divider);
        recycler.setAdapter(adapter);

        findViewById(R.id.btnBackGeofences).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        btnAdd.setOnClickListener(v -> startPlacing());
        findViewById(R.id.btnCancelPlacement).setOnClickListener(v -> stopPlacing());
        findViewById(R.id.btnSetCentre).setOnClickListener(v -> {
            if (map == null) return;
            LatLng centre = map.getCameraPosition().target;
            stopPlacing();
            String plate = null;
            if (vehicleId != null) for (DashboardVehicle dv : vehicles) if (vehicleId.equals(dv.getVehicleId())) plate = dv.getVehicleNumber();
            GeofenceSheet.create(this, centre, ownedScope(), plate != null ? plate : vehicleName, this);
        });
    }

    /** A new area opened from a vehicle page applies to that vehicle, if the customer owns it. */
    @Nullable
    private String ownedScope() {
        if (vehicleId == null) return null;
        for (DashboardVehicle v : vehicles) {
            if (vehicleId.equals(v.getVehicleId())) return v.isShared() || v.isDemo() ? null : vehicleId;
        }
        return null;
    }

    // ---- Map ----

    private void onMapReady(@NonNull GoogleMap m) {
        map = m;
        try {
            m.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_daylight));
        } catch (Exception ignored) {
            // default style
        }
        m.getUiSettings().setMapToolbarEnabled(false);
        m.getUiSettings().setCompassEnabled(false);
        m.getUiSettings().setRotateGesturesEnabled(false);
        m.getUiSettings().setTiltGesturesEnabled(false);
        m.moveCamera(CameraUpdateFactory.newLatLngZoom(SRI_LANKA, SRI_LANKA_ZOOM));
        findViewById(R.id.geofencesRoot).post(this::updateMapPadding);
        drawCircles();
        drawVehicles();
        placeCameraOnce();
    }

    private void updateMapPadding() {
        if (map == null) return;
        View root = findViewById(R.id.geofencesRoot);
        View sheet = findViewById(R.id.geofenceSheet);
        int top = topBar.getBottom();
        int bottom;
        if (placing) {
            bottom = placementBar.getHeight();
        } else {
            bottom = Math.max(0, root.getHeight() - sheet.getTop());
        }
        // Keep the visible part of the map for the camera, but never hand it less than a third of the screen.
        bottom = Math.min(bottom, root.getHeight() * 2 / 3);
        map.setPadding(0, top, 0, bottom);
    }

    private void drawCircles() {
        if (map == null) return;
        for (Circle c : circles.values()) c.remove();
        circles.clear();
        int navy = ContextCompat.getColor(this, R.color.st_navy);
        int off = ContextCompat.getColor(this, R.color.st_text_4);
        for (GeofenceResponse g : visible()) {
            int color = g.isActive() ? navy : off;
            Circle c = map.addCircle(new CircleOptions()
                    .center(new LatLng(g.getLatitude(), g.getLongitude()))
                    .radius(g.getRadiusMeters())
                    .strokeColor(color)
                    .strokeWidth(dp(2))
                    .fillColor(withAlpha(color, g.isActive() ? 0x24 : 0x14)));
            circles.put(g.getGeofenceId(), c);
        }
    }

    private void drawVehicles() {
        if (map == null) return;
        for (Marker m : vehicleMarkers) m.remove();
        vehicleMarkers.clear();
        for (DashboardVehicle v : vehicles) {
            if (!v.hasPosition()) continue;
            Marker m = map.addMarker(new MarkerOptions()
                    .position(new LatLng(v.getLatitude(), v.getLongitude()))
                    .icon(VehicleMarkers.icon(this, v))
                    .anchor(0.5f, 0.5f)
                    .title(Plates.plain(v.getVehicleNumber())));
            if (m != null) vehicleMarkers.add(m);
        }
    }

    /** Once both lists are in: the chosen vehicle, else all areas, else all vehicles, else Sri Lanka. */
    private void placeCameraOnce() {
        if (map == null || cameraPlaced || !geofencesLoaded || !vehiclesSettled) return;
        cameraPlaced = true;
        View root = findViewById(R.id.geofencesRoot);
        root.post(() -> {
            if (map == null) return;
            updateMapPadding();
            if (vehicleId != null) {
                for (DashboardVehicle v : vehicles) {
                    if (vehicleId.equals(v.getVehicleId()) && v.hasPosition()) {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(v.getLatitude(), v.getLongitude()), 14f));
                        return;
                    }
                }
            }
            LatLngBounds.Builder b = new LatLngBounds.Builder();
            int n = 0;
            for (GeofenceResponse g : visible()) {
                include(b, new LatLng(g.getLatitude(), g.getLongitude()), g.getRadiusMeters());
                n++;
            }
            if (n == 0) {
                for (DashboardVehicle v : vehicles) {
                    if (v.hasPosition()) {
                        include(b, new LatLng(v.getLatitude(), v.getLongitude()), 500);
                        n++;
                    }
                }
            }
            if (n == 0) return;
            try {
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), dp(48)));
            } catch (Exception e) {
                // map not laid out yet: leave the Sri Lanka view
            }
        });
    }

    private void focus(GeofenceResponse g) {
        if (map == null) return;
        LatLngBounds.Builder b = new LatLngBounds.Builder();
        include(b, new LatLng(g.getLatitude(), g.getLongitude()), g.getRadiusMeters());
        try {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), dp(48)));
        } catch (Exception ignored) {
            map.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(g.getLatitude(), g.getLongitude())));
        }
    }

    // ---- Placing a new area ----

    private void startPlacing() {
        if (map == null) return;
        placing = true;
        sheetBehavior.setHideable(true);
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        placementBar.setVisibility(View.VISIBLE);
        centerPin.setVisibility(View.VISIBLE);
        hint.setVisibility(View.VISIBLE);
        placementBar.post(this::updateMapPadding);
        if (map.getCameraPosition().zoom < 13f) map.animateCamera(CameraUpdateFactory.zoomTo(PLACE_ZOOM));
    }

    private void stopPlacing() {
        placing = false;
        placementBar.setVisibility(View.GONE);
        centerPin.setVisibility(View.GONE);
        hint.setVisibility(View.GONE);
        sheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
        sheetBehavior.setHideable(false);
        updateMapPadding();
    }

    // ---- GeofenceSheet.Host ----

    @Override
    public void onPreview(@NonNull LatLng center, int radiusMeters) {
        if (map == null) return;
        int signal = ContextCompat.getColor(this, R.color.st_signal);
        if (preview == null) {
            preview = map.addCircle(new CircleOptions().center(center).radius(radiusMeters)
                    .strokeColor(signal).strokeWidth(dp(2)).fillColor(withAlpha(signal, 0x2E)));
            LatLngBounds.Builder b = new LatLngBounds.Builder();
            include(b, center, radiusMeters);
            try {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), dp(64)));
            } catch (Exception ignored) {
                // keep the current view
            }
        } else {
            preview.setRadius(radiusMeters);
        }
    }

    @Override
    public void onPreviewEnd() {
        if (preview != null) {
            preview.remove();
            preview = null;
        }
    }

    @Override
    public void onSaved() {
        banner.hide();
        loadGeofences();
    }

    @Override
    public void onRemoved() {
        banner.hide();
        loadGeofences();
    }

    // ---- Data ----

    private boolean vehiclesSettled;

    private void loadVehicles() {
        VehiclePickerSheet.load(this, new VehiclePickerSheet.LoadCallback() {
            @Override
            public void onLoaded(List<DashboardVehicle> list) {
                if (isDestroyed()) return;
                vehicles.clear();
                vehicles.addAll(list);
                vehiclesSettled = true;
                // Opened for a vehicle someone shared (or the demo): only its owner can add areas for it.
                if (vehicleId != null && ownedScope() == null) btnAdd.setVisibility(View.GONE);
                drawVehicles();
                placeCameraOnce();
                renderSubtitle();
            }

            @Override
            public void onFailed(int httpCode) {
                if (isDestroyed()) return;
                // Only the map context is missing; the list still works.
                vehiclesSettled = true;
                placeCameraOnce();
            }
        });
    }

    private void loadGeofences() {
        if (all.isEmpty()) progress.setVisibility(View.VISIBLE);
        api.getMyGeofences().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isDestroyed()) return;
                progress.setVisibility(View.GONE);
                List<GeofenceResponse> list = null;
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) list = ApiJson.list(body.string(), GeofenceResponse.class);
                } catch (Exception ignored) {
                    list = null;
                }
                if (list == null) {
                    onLoadFailed(false);
                    return;
                }
                geofencesLoaded = true;
                all.clear();
                for (GeofenceResponse g : list) if (g != null && g.getGeofenceId() != null) all.add(g);
                render();
                drawCircles();
                placeCameraOnce();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isDestroyed()) return;
                progress.setVisibility(View.GONE);
                onLoadFailed(true);
            }
        });
    }

    private void onLoadFailed(boolean network) {
        Runnable retry = () -> {
            banner.hide();
            empty.hide();
            loadGeofences();
        };
        if (all.isEmpty()) {
            recycler.setVisibility(View.GONE);
            empty.show(getString(R.string.st_geofences_load_failed_title),
                    getString(network ? R.string.st_error_network : R.string.st_geofences_load_failed),
                    getString(R.string.st_action_retry), retry);
        } else {
            banner.showError(network ? R.string.st_error_network : R.string.st_geofences_load_failed, retry);
        }
    }

    /** For one vehicle: areas scoped to it and "all vehicles" areas. Otherwise everything. */
    private List<GeofenceResponse> visible() {
        if (vehicleId == null) return all;
        List<GeofenceResponse> out = new ArrayList<>();
        for (GeofenceResponse g : all) if (g.getVehicleId() == null || vehicleId.equals(g.getVehicleId())) out.add(g);
        return out;
    }

    private void render() {
        List<GeofenceResponse> shown = visible();
        adapter.submit(shown);
        renderSubtitle();
        if (shown.isEmpty()) {
            recycler.setVisibility(View.GONE);
            empty.show(getString(R.string.st_geofences_empty_title), getString(R.string.st_geofences_empty_body), null, null);
        } else {
            empty.hide();
            recycler.setVisibility(View.VISIBLE);
        }
    }

    private void renderSubtitle() {
        int n = visible().size();
        String count = getResources().getQuantityString(R.plurals.st_geofences_count, n, n);
        String who;
        if (vehicleId == null) {
            who = getString(R.string.st_geofence_scope_all);
        } else {
            String plate = vehicleName;
            for (DashboardVehicle v : vehicles) if (vehicleId.equals(v.getVehicleId())) plate = v.getVehicleNumber();
            who = plate != null ? Plates.plain(plate) : getString(R.string.st_geofence_scope_one);
        }
        tvSubtitle.setText(geofencesLoaded ? getString(R.string.st_geofences_subtitle, count, who) : who);
    }

    private void toggle(GeofenceResponse g, boolean active) {
        UpdateGeofenceRequest request = new UpdateGeofenceRequest(g.getVehicleId(), g.getName(), g.getLatitude(),
                g.getLongitude(), g.getRadiusMeters(), g.isAlertOnEnter(), g.isAlertOnExit(), active);
        api.updateGeofence(g.getGeofenceId(), request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.body() != null) response.body().close();
                if (isDestroyed()) return;
                if (!response.isSuccessful()) banner.showError(R.string.st_geofence_toggle_failed, null);
                // Either way, show what the server now holds.
                loadGeofences();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isDestroyed()) return;
                banner.showError(R.string.st_error_network, null);
                render();   // puts the switch back
            }
        });
    }

    // ---- Helpers ----

    /** Adds a circle's bounding box (approximate, fine for camera framing). */
    private static void include(LatLngBounds.Builder b, LatLng c, int radiusMeters) {
        double dLat = radiusMeters / 111_320d;
        double dLng = radiusMeters / (111_320d * Math.max(0.2, Math.cos(Math.toRadians(c.latitude))));
        b.include(new LatLng(c.latitude + dLat, c.longitude + dLng));
        b.include(new LatLng(c.latitude - dLat, c.longitude - dLng));
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Nullable
    private static String trimToNull(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
