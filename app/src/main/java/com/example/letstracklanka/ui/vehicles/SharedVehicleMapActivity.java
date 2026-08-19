package com.example.letstracklanka.ui.vehicles;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.ui.main.VehicleTrailRenderer;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Full live-tracking view for a vehicle shared with the viewer -- same
 * animated marker and heading rotation experience as HomeActivity's own
 * tracking, achieved by reusing VehicleTrailRenderer directly rather than
 * duplicating its animation logic. HomeActivity itself is untouched by
 * this addition; this is a fully separate, standalone screen.
 *
 * Backend access for this vehicleId only succeeds if the viewer has an
 * Accepted share for it (see the real fix already made in
 * CurrentLocationService.OwnsVehicleAsync) -- a vehicle they don't have
 * access to will simply never get a location back from the poll below.
 */
public class SharedVehicleMapActivity extends AppCompatActivity {

    public static final String EXTRA_VEHICLE_ID = "extra_vehicle_id";
    public static final String EXTRA_VEHICLE_TITLE = "extra_vehicle_title";
    public static final String EXTRA_OWNER_NAME = "extra_owner_name";

    private static final int UPDATE_INTERVAL = 1000;

    private ShaloTrackApi trackingApi;
    private GoogleMap googleMap;
    private VehicleTrailRenderer trailRenderer;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable trackingRunnable;

    private String vehicleId;
    private String vehicleTitle;

    private TextView tvStatus;
    private ProgressBar progressSharedMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shared_vehicle_map);

        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);

        vehicleId = getIntent().getStringExtra(EXTRA_VEHICLE_ID);
        vehicleTitle = getIntent().getStringExtra(EXTRA_VEHICLE_TITLE);
        String ownerName = getIntent().getStringExtra(EXTRA_OWNER_NAME);

        initViews(ownerName);

        if (vehicleId == null || vehicleId.isEmpty()) {
            if (tvStatus != null) tvStatus.setText("No vehicle specified.");
            return;
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this::onMapReady);
        }
    }

    private void initViews(String ownerName) {
        View btnBack = findViewById(R.id.btnBackSharedMap);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        TextView tvTitle = findViewById(R.id.tvSharedVehicleTitle);
        if (tvTitle != null && vehicleTitle != null) tvTitle.setText(vehicleTitle);

        TextView tvOwner = findViewById(R.id.tvSharedVehicleOwner);
        if (tvOwner != null && ownerName != null) tvOwner.setText("Shared by " + ownerName);

        tvStatus = findViewById(R.id.tvSharedVehicleStatus);
        progressSharedMap = findViewById(R.id.progressSharedMap);
    }

    private void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        trailRenderer = new VehicleTrailRenderer(this, googleMap, trackingApi);

        trailRenderer.loadInitialTrail(vehicleId, () -> {
            if (progressSharedMap != null) progressSharedMap.setVisibility(View.GONE);
            startPolling();
        });
    }

    private void startPolling() {
        if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable);
        trackingRunnable = new Runnable() {
            @Override
            public void run() {
                fetchLocation();
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        handler.post(trackingRunnable);
    }

    private void fetchLocation() {
        if (vehicleId == null) return;
        trackingApi.getVehicleLocation(vehicleId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null && googleMap != null) {
                        LocationResponse loc = extractLocation(body.string());
                        if (loc != null) {
                            LatLng pos = new LatLng(loc.getLatitude(), loc.getLongitude());
                            if (pos.latitude != 0 || pos.longitude != 0) {
                                trailRenderer.updatePosition(pos, loc.getHeading(), vehicleTitle);
                                if (tvStatus != null) tvStatus.setText(loc.getSpeed() > 2 ? "Moving" : "Parked");
                            }
                        }
                    } else if (response.code() == 404) {
                        Log.d("SharedVehicleMapActivity", "No current location yet for vehicle " + vehicleId);
                        if (tvStatus != null) tvStatus.setText("No location data yet");
                    } else if (response.code() == 403 || response.code() == 401) {
                        // Access was revoked mid-session -- stop polling
                        // rather than keep hammering an endpoint that will
                        // never succeed again for this share.
                        Log.w("SharedVehicleMapActivity", "Access no longer permitted, code " + response.code());
                        if (tvStatus != null) tvStatus.setText("Access to this vehicle has ended.");
                        if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable);
                    }
                } catch (Exception e) {
                    Log.e("SharedVehicleMapActivity", "fetchLocation parse error", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("SharedVehicleMapActivity", "fetchLocation network error", t);
            }
        });
    }

    private LocationResponse extractLocation(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), LocationResponse.class);
            }
            return gson.fromJson(json, LocationResponse.class);
        } catch (Exception e) {
            Log.e("SharedVehicleMapActivity", "extractLocation parse error", e);
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable);
    }
}