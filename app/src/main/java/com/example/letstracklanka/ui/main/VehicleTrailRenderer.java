package com.example.letstracklanka.ui.main;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import com.example.letstracklanka.data.model.TrackingPoint;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.ResponseBody;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Draws the historical trail (blue polyline) for one vehicle and smoothly animates a
 * custom car-icon marker between successive positions, rotating it to face the
 * direction of travel.
 *
 * Usage from HomeActivity / VehiclesActivity, inside onMapReady():
 *
 *   trailRenderer = new VehicleTrailRenderer(this, mMap, trackingApi);
 *   // once you know the vehicleId (e.g. after fetchMyVehicles() succeeds):
 *   trailRenderer.loadInitialTrail(vehicleId, () -> Log.d("Trail", "history loaded"));
 *
 * Inside fetchLocation()'s success callback, once you have a LocationResponse `loc`:
 *
 *   trailRenderer.updatePosition(
 *       new LatLng(loc.getLatitude(), loc.getLongitude()),
 *       loc.getHeading(),
 *       title);
 */
public class VehicleTrailRenderer {

    private static final String TAG = "VehicleTrailRenderer";
    private static final int HISTORY_WINDOW_HOURS = 2;

    // FIX: previously a FIXED 400ms animation regardless of how much real
    // time had passed since the last fix. With the device reporting every
    // ~20s, that meant a quick 400ms slide followed by ~19.6s of the marker
    // sitting frozen, then another quick dash -- which reads exactly as
    // "jumping" because the animation duration had no relationship to the
    // real reporting interval. Now the animation duration is the ACTUAL
    // elapsed time since the last accepted fix, clamped to a sane range, so
    // the marker glides continuously across the real ~20s window instead.
    private static final long MIN_ANIMATION_DURATION_MS = 500;
    private static final long MAX_ANIMATION_DURATION_MS = 25_000; // caps it if the app was backgrounded for a while

    // Jitter filter: NOT road-snapping (see conversation -- Google Roads
    // API / self-hosted map-matching were deliberately skipped given cost
    // and deployment timeline). This is a much cheaper, purely
    // physics-based sanity check: reject a fix only if the implied speed
    // between it and the last ACCEPTED fix is beyond anything a real
    // vehicle could do, which is the classic signature of a noisy/
    // multipath GPS point rather than genuine movement. Won't guarantee
    // the marker sits exactly on the road, but stops the worst, most
    // visible "teleported off the road" jumps.
    private static final double MIN_MEANINGFUL_MOVEMENT_METERS = 3.0; // below this, treat as "hasn't moved"
    private static final long MIN_INTERVAL_FOR_SPEED_CHECK_MS = 2000;  // too short an interval to sanity-check reliably
    private static final double MAX_PLAUSIBLE_SPEED_MPS = 55.6; // ~200 km/h, deliberately generous -- only catches genuine GPS errors

    private static final float ICON_ROTATION_OFFSET = -90f; // to make the vehicle look aligned to the respective heading of the map

    private final GoogleMap map;
    private final ShaloTrackApi api;
    private final BitmapDescriptor carIcon;

    private Marker marker;
    private Polyline polyline;
    private float currentBearing = 0f;
    private long lastAcceptedFixTimeMs = 0L;
    private final List<LatLng> pathPoints = new ArrayList<>();

    public VehicleTrailRenderer(Context context, GoogleMap map, ShaloTrackApi api) {
        this.map = map;
        this.api = api;
        // NOTE: requires res/drawable/ic_car_marker.xml — add via
        // Android Studio: right-click res -> New -> Vector Asset -> search "directions car".
        BitmapDescriptor icon;
        try {
            int resId = context.getResources().getIdentifier(
                    "ic_car_marker", "drawable", context.getPackageName());
            if (resId != 0) {
                android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(context, resId);
                int width = 80, height = 80;   // adjust marker size here
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                drawable.setBounds(0, 0, width, height);
                drawable.draw(canvas);
                icon = BitmapDescriptorFactory.fromBitmap(bitmap);
            } else {
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
            }
        } catch (Exception e) {
            Log.w(TAG, "ic_car_marker not found, falling back to default marker", e);
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
        }
        this.carIcon = icon;
    }

    /** Call once, after you know the vehicleId, to seed the trail from recent history. */
    public void loadInitialTrail(String vehicleId, Runnable onComplete) {
        String toIso = isoNow();
        String fromIso = isoHoursAgo(HISTORY_WINDOW_HOURS);

        api.getTrackingHistory(vehicleId, fromIso, toIso, 500).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<ResponseBody> call, Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<TrackingPoint> points = parseList(body.string());
                        pathPoints.clear();
                        for (int i = points.size() - 1; i >= 0; i--) {
                            TrackingPoint p = points.get(i);
                            if (p.getLatitude() != 0 || p.getLongitude() != 0) {
                                pathPoints.add(new LatLng(p.getLatitude(), p.getLongitude()));
                            }
                        }
                        redrawPolyline();
                    } else if (response.code() == 400) {
                        Log.w(TAG, "Trail load 400 — vehicleId missing/invalid.");
                    } else if (response.code() == 404) {
                        Log.d(TAG, "No trail history yet for this vehicle.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error loading trail", e);
                } finally {
                    if (onComplete != null) onComplete.run();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Trail history request failed", t);
                if (onComplete != null) onComplete.run();
            }
        });
    }

    /**
     * Call this every time your existing polling loop gets a fresh position.
     * @param heading compass bearing in degrees (0 = north). Pass 0 if unknown/stationary.
     */
    public void updatePosition(LatLng newPos, float heading, String title) {
        long now = System.currentTimeMillis();

        if (marker == null) {
            marker = map.addMarker(new MarkerOptions()
                    .position(newPos)
                    .icon(carIcon)
                    .anchor(0.5f, 0.5f)   // rotate around the icon's center, not its base
                    .flat(true)            // rotates with the map instead of always facing the viewer
                    .rotation(heading + ICON_ROTATION_OFFSET) //fixes the heading of the car img
                    .title(title));
            currentBearing = heading;
            lastAcceptedFixTimeMs = now;
            appendToPathIfNew(newPos);
            return;
        }

        double distanceMeters = haversineMeters(marker.getPosition(), newPos);

        if (distanceMeters < MIN_MEANINGFUL_MOVEMENT_METERS) {
            // Effectively the same position (GPS noise floor even when
            // stationary) -- nothing to animate, and NOT touching
            // lastAcceptedFixTimeMs here is deliberate: it should keep
            // tracking time since the last position that actually changed,
            // not reset on every duplicate poll of an unmoving vehicle.
            return;
        }

        long elapsedMs = now - lastAcceptedFixTimeMs;

        if (elapsedMs >= MIN_INTERVAL_FOR_SPEED_CHECK_MS) {
            double impliedSpeedMps = distanceMeters / (elapsedMs / 1000.0);
            if (impliedSpeedMps > MAX_PLAUSIBLE_SPEED_MPS) {
                // Rejected as noise -- a real vehicle cannot plausibly have
                // covered this distance in this time. Deliberately NOT
                // updating the marker, polyline, or lastAcceptedFixTimeMs:
                // we just wait for the next fix rather than accept a fix
                // that would visibly yank the marker off the road.
                Log.w(TAG, "Rejected fix as GPS jitter: " + String.format(Locale.US,
                        "%.0fm in %.1fs implies %.0f km/h (max plausible %.0f km/h)",
                        distanceMeters, elapsedMs / 1000.0,
                        impliedSpeedMps * 3.6, MAX_PLAUSIBLE_SPEED_MPS * 3.6));
                return;
            }
        }
        // If elapsedMs is too short to sanity-check reliably (e.g. a
        // realtime push landing right after a poll tick), the fix is
        // accepted unfiltered rather than risk a false rejection from an
        // unstable near-zero-time-denominator speed calculation.

        long animationDurationMs = Math.max(MIN_ANIMATION_DURATION_MS,
                Math.min(elapsedMs, MAX_ANIMATION_DURATION_MS));
        animateMarkerTo(marker, marker.getPosition(), newPos, currentBearing, heading, animationDurationMs);
        currentBearing = heading;
        lastAcceptedFixTimeMs = now;

        appendToPathIfNew(newPos);
    }

    private void appendToPathIfNew(LatLng newPos) {
        if (pathPoints.isEmpty() || !pathPoints.get(pathPoints.size() - 1).equals(newPos)) {
            pathPoints.add(newPos);
            redrawPolyline();
        }
    }

    /** Great-circle distance in meters between two points (Haversine formula). */
    private double haversineMeters(LatLng a, LatLng b) {
        final double EARTH_RADIUS_M = 6_371_000;
        double lat1 = Math.toRadians(a.latitude);
        double lat2 = Math.toRadians(b.latitude);
        double dLat = Math.toRadians(b.latitude - a.latitude);
        double dLng = Math.toRadians(b.longitude - a.longitude);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    private void animateMarkerTo(Marker marker, LatLng from, LatLng to, float fromBearing, float toBearing, long durationMs) {
        // Take the shorter rotation path (e.g. 350 deg -> 10 deg should turn +20, not -340).
        float bearingDelta = ((toBearing - fromBearing + 540) % 360) - 180;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(durationMs);
        animator.addUpdateListener(animation -> {
            float f = (float) animation.getAnimatedValue();
            double lat = from.latitude + (to.latitude - from.latitude) * f;
            double lng = from.longitude + (to.longitude - from.longitude) * f;
            marker.setPosition(new LatLng(lat, lng));
            marker.setRotation((fromBearing + bearingDelta * f + ICON_ROTATION_OFFSET + 360) % 360);
        });
        animator.start();
    }

    private void redrawPolyline() {
        Log.d(TAG, "redrawPolyline called, pathPoints.size()=" + pathPoints.size());   // TEMP DEBUG
        if (polyline != null) polyline.remove();
        if (pathPoints.size() < 2) return;
        polyline = map.addPolyline(new PolylineOptions()
                .addAll(pathPoints)
                .width(10f)
                .color(Color.parseColor("#1877F2"))
                .zIndex(1f)
                .geodesic(true));
    }

    private List<TrackingPoint> parseList(String json) {
        List<TrackingPoint> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                list = gson.fromJson(root.getAsJsonArray("data"),
                        TypeToken.getParameterized(List.class, TrackingPoint.class).getType());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing trail JSON", e);
        }
        return list;
    }

    private String isoNow() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new java.util.Date());
    }

    private String isoHoursAgo(int hours) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.add(Calendar.HOUR_OF_DAY, -hours);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(cal.getTime());
    }
}