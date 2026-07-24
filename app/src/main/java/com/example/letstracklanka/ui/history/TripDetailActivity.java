package com.example.letstracklanka.ui.history;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.TrackingPoint;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.ui.main.AddressResolver;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Trip playback: loads the raw GPS points for one specific trip's time window
 * (reusing the same endpoint and TrackingPoint model built earlier for the live
 * trail feature -- no new backend work needed) and lets the user scrub through
 * or auto-play the route, watching a marker retrace exactly what happened.
 */
public class TripDetailActivity extends AppCompatActivity {

    public static final String EXTRA_VEHICLE_ID = "vehicle_id";
    public static final String EXTRA_FROM_ISO = "from_iso";
    public static final String EXTRA_TO_ISO = "to_iso";
    public static final String EXTRA_VEHICLE_NAME = "vehicle_name";
    public static final String EXTRA_DISTANCE_KM = "distance_km";
    public static final String EXTRA_DURATION_MIN = "duration_min";
    public static final String EXTRA_MAX_SPEED = "max_speed";
    public static final String EXTRA_AVG_SPEED = "avg_speed";

    private static final int PLAYBACK_INTERVAL_MS = 300;
    private static final int[] SPEED_MULTIPLIERS = {1, 2, 4};

    private ShaloTrackApi trackingApi;
    private AddressResolver addressResolver;

    private GoogleMap mMap;
    private Marker playbackMarker;
    private Polyline traveledPolyline;
    private Polyline fullRoutePolyline;

    private List<TrackingPoint> points = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isPlaying = false;
    private boolean programmaticSeekUpdate = false;
    private int speedMultiplierIndex = 0;   // index into SPEED_MULTIPLIERS

    private final Handler playbackHandler = new Handler(Looper.getMainLooper());
    private Runnable playbackRunnable;

    private SeekBar seekPlayback;
    private ImageView btnPlayPause;
    private TextView tvPlaybackReadout, tvDetailStartAddress, tvDetailEndAddress;
    private TextView tvDetailVehicleName, tvDetailDate, btnPlaybackSpeed;
    private TextView tvStatDistance, tvStatDuration, tvStatMaxSpeed, tvStatAvgSpeed;
    private View progressTripDetail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_detail);

        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);
        addressResolver = new AddressResolver(this);

        initViews();
        populateHeaderAndStats();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapTripDetail);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this::onMapReady);
        }
    }

    private void initViews() {
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        seekPlayback = findViewById(R.id.seekPlayback);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPlaybackSpeed = findViewById(R.id.btnPlaybackSpeed);
        tvPlaybackReadout = findViewById(R.id.tvPlaybackReadout);
        tvDetailStartAddress = findViewById(R.id.tvDetailStartAddress);
        tvDetailEndAddress = findViewById(R.id.tvDetailEndAddress);
        tvDetailVehicleName = findViewById(R.id.tvDetailVehicleName);
        tvDetailDate = findViewById(R.id.tvDetailDate);
        tvStatDistance = findViewById(R.id.tvStatDistance);
        tvStatDuration = findViewById(R.id.tvStatDuration);
        tvStatMaxSpeed = findViewById(R.id.tvStatMaxSpeed);
        tvStatAvgSpeed = findViewById(R.id.tvStatAvgSpeed);
        progressTripDetail = findViewById(R.id.progressTripDetail);

        btnPlayPause.setOnClickListener(v -> togglePlayback());

        btnPlaybackSpeed.setOnClickListener(v -> {
            speedMultiplierIndex = (speedMultiplierIndex + 1) % SPEED_MULTIPLIERS.length;
            btnPlaybackSpeed.setText(SPEED_MULTIPLIERS[speedMultiplierIndex] + "x");
        });

        seekPlayback.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && !programmaticSeekUpdate) {
                    pausePlayback();
                    currentIndex = progress;
                    updatePlaybackPosition(currentIndex);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    private void populateHeaderAndStats() {
        String vehicleName = getIntent().getStringExtra(EXTRA_VEHICLE_NAME);
        double distanceKm = getIntent().getDoubleExtra(EXTRA_DISTANCE_KM, 0);
        double durationMin = getIntent().getDoubleExtra(EXTRA_DURATION_MIN, 0);
        double maxSpeed = getIntent().getDoubleExtra(EXTRA_MAX_SPEED, 0);
        double avgSpeed = getIntent().getDoubleExtra(EXTRA_AVG_SPEED, 0);
        String fromIso = getIntent().getStringExtra(EXTRA_FROM_ISO);

        if (vehicleName != null) tvDetailVehicleName.setText(vehicleName);
        tvDetailDate.setText(formatDate(fromIso));

        tvStatDistance.setText(String.format(Locale.getDefault(), "%.1f km", distanceKm));
        tvStatDuration.setText(formatDuration(durationMin));
        tvStatMaxSpeed.setText((int) maxSpeed + " km/h");
        tvStatAvgSpeed.setText((int) avgSpeed + " km/h");
    }

    private void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        loadTripPoints();
    }

    private void loadTripPoints() {
        String vehicleId = getIntent().getStringExtra(EXTRA_VEHICLE_ID);
        String fromIso = getIntent().getStringExtra(EXTRA_FROM_ISO);
        String toIso = getIntent().getStringExtra(EXTRA_TO_ISO);

        if (vehicleId == null || fromIso == null || toIso == null) {
            tvPlaybackReadout.setText("Missing trip data");
            return;
        }

        progressTripDetail.setVisibility(View.VISIBLE);

        trackingApi.getTrackingHistory(vehicleId, fromIso, toIso, 500).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                progressTripDetail.setVisibility(View.GONE);
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        points = parseList(body.string(), TrackingPoint.class);
                        Collections.sort(points, Comparator.comparing(TrackingPoint::getEventTime));
                        if (!points.isEmpty()) {
                            drawFullRoute();
                            resolveEndpointAddresses();
                            seekPlayback.setMax(Math.max(points.size() - 1, 1));
                            currentIndex = 0;
                            updatePlaybackPosition(0);
                        } else {
                            tvPlaybackReadout.setText("No tracking points found for this trip");
                        }
                    } else {
                        tvPlaybackReadout.setText("Could not load trip data (code " + response.code() + ")");
                    }
                } catch (Exception e) {
                    Log.e("TripDetailActivity", "loadTripPoints parse error", e);
                    tvPlaybackReadout.setText("Something went wrong loading this trip");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                progressTripDetail.setVisibility(View.GONE);
                tvPlaybackReadout.setText("Network error — check your connection");
            }
        });
    }

    private void drawFullRoute() {
        PolylineOptions options = new PolylineOptions().width(6f).color(0x331877F2).geodesic(true);
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (TrackingPoint p : points) {
            LatLng pos = new LatLng(p.getLatitude(), p.getLongitude());
            options.add(pos);
            boundsBuilder.include(pos);
        }
        fullRoutePolyline = mMap.addPolyline(options);

        traveledPolyline = mMap.addPolyline(new PolylineOptions().width(8f).color(0xFF1877F2).geodesic(true));

        LatLng start = new LatLng(points.get(0).getLatitude(), points.get(0).getLongitude());
        LatLng end = new LatLng(points.get(points.size() - 1).getLatitude(), points.get(points.size() - 1).getLongitude());

        // Static markers showing where the trip began and ended, distinct from the
        // moving car marker below (which shows the CURRENT playback position).
        mMap.addMarker(new MarkerOptions()
                .position(start)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title("Trip start")
                .anchor(0.5f, 1f));

        mMap.addMarker(new MarkerOptions()
                .position(end)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                .title("Trip end")
                .anchor(0.5f, 1f));

        playbackMarker = mMap.addMarker(new MarkerOptions()
                .position(start)
                .icon(loadCarIcon())
                .anchor(0.5f, 0.5f)
                .flat(true));

        try {
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80));
        } catch (Exception e) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(start, 14f));
        }
    }

    /**
     * Loads res/drawable/ic_car_marker.xml as a bitmap, same pattern (and same fix)
     * used in VehicleTrailRenderer for the live map earlier tonight -- raw vector
     * resource IDs handed straight to BitmapDescriptorFactory.fromResource() caused
     * a silent exception on some devices that quietly broke unrelated UI updates.
     * Converting to a bitmap explicitly avoids that. Falls back to a default marker
     * if the drawable isn't found, rather than crashing.
     */
    private BitmapDescriptor loadCarIcon() {
        try {
            int resId = getResources().getIdentifier("ic_car_marker", "drawable", getPackageName());
            if (resId != 0) {
                Drawable drawable = ContextCompat.getDrawable(this, resId);
                int width = 80, height = 80;
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, width, height);
                drawable.draw(canvas);
                return BitmapDescriptorFactory.fromBitmap(bitmap);
            }
        } catch (Exception e) {
            Log.w("TripDetailActivity", "ic_car_marker not found, falling back to default marker", e);
        }
        return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
    }

    /**
     * TrackingPoint has no heading field (never needed for the original live-trail
     * feature), so rotation here is approximated from the bearing between this
     * point and the next one -- accurate enough for a car icon to visibly turn
     * along the actual route during playback.
     */
    private float bearingToNext(int index) {
        if (index >= points.size() - 1) return 0f;
        TrackingPoint from = points.get(index);
        TrackingPoint to = points.get(index + 1);

        double lat1 = Math.toRadians(from.getLatitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double dLon = Math.toRadians(to.getLongitude() - from.getLongitude());

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (float) ((bearing + 360) % 360);
    }

    private void resolveEndpointAddresses() {
        TrackingPoint first = points.get(0);
        TrackingPoint last = points.get(points.size() - 1);

        addressResolver.resolveAddress(first.getLatitude(), first.getLongitude(),
                address -> tvDetailStartAddress.setText(address));
        addressResolver.resolveAddress(last.getLatitude(), last.getLongitude(),
                address -> tvDetailEndAddress.setText(address));
    }

    private void togglePlayback() {
        if (isPlaying) {
            pausePlayback();
        } else {
            startPlayback();
        }
    }

    private void startPlayback() {
        if (points.isEmpty()) return;
        if (currentIndex >= points.size() - 1) currentIndex = 0;   // restart from beginning if at the end

        isPlaying = true;
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);

        playbackRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentIndex >= points.size() - 1) {
                    pausePlayback();
                    return;
                }
                int step = SPEED_MULTIPLIERS[speedMultiplierIndex];
                currentIndex = Math.min(currentIndex + step, points.size() - 1);
                updatePlaybackPosition(currentIndex);
                playbackHandler.postDelayed(this, PLAYBACK_INTERVAL_MS);
            }
        };
        playbackHandler.postDelayed(playbackRunnable, PLAYBACK_INTERVAL_MS);
    }

    private void pausePlayback() {
        isPlaying = false;
        btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        if (playbackRunnable != null) playbackHandler.removeCallbacks(playbackRunnable);
    }

    private void updatePlaybackPosition(int index) {
        if (points.isEmpty() || index < 0 || index >= points.size()) return;

        TrackingPoint p = points.get(index);
        LatLng pos = new LatLng(p.getLatitude(), p.getLongitude());

        if (playbackMarker != null) {
            playbackMarker.setPosition(pos);
            playbackMarker.setRotation(bearingToNext(index));
        }

        if (traveledPolyline != null) {
            List<LatLng> traveled = new ArrayList<>();
            for (int i = 0; i <= index; i++) {
                traveled.add(new LatLng(points.get(i).getLatitude(), points.get(i).getLongitude()));
            }
            traveledPolyline.setPoints(traveled);
        }

        tvPlaybackReadout.setText(formatTime(p.getEventTime()) + " · " + (int) p.getSpeed() + " km/h");

        programmaticSeekUpdate = true;
        seekPlayback.setProgress(index);
        programmaticSeekUpdate = false;
    }

    private String formatTime(String isoUtc) {
        if (isoUtc == null) return "--:--";
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = parser.parse(isoUtc.length() > 19 ? isoUtc.substring(0, 20) : isoUtc);
            SimpleDateFormat display = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault());
            display.setTimeZone(TimeZone.getDefault());
            return date != null ? display.format(date) : isoUtc;
        } catch (ParseException e) {
            return isoUtc;
        }
    }

    private String formatDate(String isoUtc) {
        if (isoUtc == null) return "";
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = parser.parse(isoUtc.length() > 19 ? isoUtc.substring(0, 20) : isoUtc);
            SimpleDateFormat display = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());
            display.setTimeZone(TimeZone.getDefault());
            return date != null ? display.format(date) : "";
        } catch (ParseException e) {
            return "";
        }
    }

    private String formatDuration(double totalMinutes) {
        int hours = (int) (totalMinutes / 60);
        int minutes = (int) (totalMinutes % 60);
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private <T> List<T> parseList(String json, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                list = gson.fromJson(root.getAsJsonArray("data"), TypeToken.getParameterized(List.class, clazz).getType());
            }
        } catch (Exception e) {
            Log.e("TripDetailActivity", "parseList error", e);
        }
        return list;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (playbackRunnable != null) playbackHandler.removeCallbacks(playbackRunnable);
    }
}