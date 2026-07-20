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
    private static final int ANIMATION_DURATION_MS = 400; //to make the movement very smooth lower the better

    private static final float ICON_ROTATION_OFFSET = -90f; // to make the vehicle look aligned to the respective heading of the map

    private final GoogleMap map;
    private final ShaloTrackApi api;
    private final BitmapDescriptor carIcon;

    private Marker marker;
    private Polyline polyline;
    private float currentBearing = 0f;
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
        if (marker == null) {
            marker = map.addMarker(new MarkerOptions()
                    .position(newPos)
                    .icon(carIcon)
                    .anchor(0.5f, 0.5f)   // rotate around the icon's center, not its base
                    .flat(true)            // rotates with the map instead of always facing the viewer
                    .rotation(heading + ICON_ROTATION_OFFSET) //fixes the heading of the car img
                    .title(title));
            currentBearing = heading;
        } else {
            animateMarkerTo(marker, marker.getPosition(), newPos, currentBearing, heading);
            currentBearing = heading;
        }

        if (pathPoints.isEmpty() || !pathPoints.get(pathPoints.size() - 1).equals(newPos)) {
            pathPoints.add(newPos);
            redrawPolyline();
        }
    }

    private void animateMarkerTo(Marker marker, LatLng from, LatLng to, float fromBearing, float toBearing) {
        // Take the shorter rotation path (e.g. 350 deg -> 10 deg should turn +20, not -340).
        float bearingDelta = ((toBearing - fromBearing + 540) % 360) - 180;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ANIMATION_DURATION_MS);
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