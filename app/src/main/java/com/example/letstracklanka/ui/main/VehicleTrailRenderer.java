package com.example.letstracklanka.ui.main;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import com.example.letstracklanka.data.model.SnapToRoadRequest;
import com.example.letstracklanka.data.model.SnappedPoint;
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

    // Used for showing messages in Android Studio log
    private static final String TAG = "VehicleTrailRenderer";

    // How many hours of previous travel history to load when opening the app
    private static final int HISTORY_WINDOW_HOURS = 1;

    // Minimum and maximum time for the smooth movement animation (in milliseconds)
    // Marker animation duration matches the real elapsed time since the
    // last accepted fix (not a fixed value), clamped to this range.
    private static final long MIN_ANIMATION_DURATION_MS = 500;
    private static final long MAX_ANIMATION_DURATION_MS = 25_000;

    // Rules to ignore fake/jumping GPS points (Jitter filter)
    // A fix is rejected if the implied speed since the last accepted fix exceeds
    // what a real vehicle could do -- the signature of a noisy GPS point.
    private static final double MIN_MEANINGFUL_MOVEMENT_METERS = 3.0;
    private static final long MIN_INTERVAL_FOR_SPEED_CHECK_MS = 2000;
    private static final double MAX_PLAUSIBLE_SPEED_MPS = 55.6; // ~200 km/h

    // Adjusting the car icon rotation so it points to the correct front side
    // FIX: was -90f, correcting for the OLD icon's artwork having its
    // front drawn facing sideways rather than up. The new PNG (real
    // Flaticon asset) already has its front facing straight up in its own
    // unrotated form -- matching Google's own documented convention for
    // rotating vehicle markers -- so no correction is needed anymore.
    // Left at -90f, this would have made the car consistently point 90
    // degrees off from the real direction of travel.
    private static final float ICON_ROTATION_OFFSET = 0f;

    // Variables for Google Maps and server API
    private final GoogleMap map;
    private final ShaloTrackApi api;
    // (carIcon field removed -- replaced by iconsByType map above)

    // Variables to hold the map marker (Car) and the blue line (Trail)
    private Marker marker;
    private Polyline polyline;
    private ValueAnimator currentAnimator;

    // Current details of the vehicle
    private float currentBearing = 0f;
    private long lastAcceptedFixTimeMs = 0L;
    private final List<LatLng> pathPoints = new ArrayList<>();

    // Cached smoothed trail, recomputed only when a point is committed
    // (~once per real fix), not on every animation frame.
    private List<LatLng> smoothedSettledPoints = new ArrayList<>();

    // Number of small points created between two real points to make the curve smooth
    // Interpolated points generated per real segment when smoothing.
    private static final int CATMULL_ROM_SEGMENTS = 8;

    // Road-snapping: how many of the most recent real points get sent for
    // context each time. More context generally means better snapping, but
    // costs more per request (and this project's Roads API pricing/cost
    // reasoning was already discussed at length in chat) -- 5 is a
    // judgment call, not a measured value.
    private static final int SNAP_BUFFER_SIZE = 5;
    private String vehicleId;

    private static final int MARKER_SIZE_PX = 80;
    // NEW -- was a single final BitmapDescriptor for every vehicle
    // regardless of type. Now loads every variant upfront (car/SUV/van/
    // truck/bike/tuk), keyed the same way the spinner options and
    // vehicleType string are already cased. "car" also serves as the
    // fallback for null/unrecognized types.
    private final java.util.Map<String, BitmapDescriptor> iconsByType = new java.util.HashMap<>();
    // Set right before a vehicle switch (see resetForVehicleSwitch), read
    // when the marker is actually created in updatePosition()'s
    // marker == null branch below.
    private String pendingVehicleType = "car";

    // Setup the Renderer and prepare the Car icon graphic
    public VehicleTrailRenderer(Context context, GoogleMap map, ShaloTrackApi api) {
        this.map = map;
        this.api = api;

        iconsByType.put("car", loadIconFromDrawableName(context, "ic_car_marker"));
        iconsByType.put("suv", loadIconFromDrawableName(context, "ic_car_marker")); // MDI has no SUV-distinct top-down asset; reuses the car marker
        iconsByType.put("van", loadIconFromDrawableName(context, "ic_type_van"));
        iconsByType.put("truck", loadIconFromDrawableName(context, "ic_type_truck"));
        iconsByType.put("bike", loadIconFromDrawableName(context, "ic_type_bike"));
        iconsByType.put("tuk", loadIconFromDrawableName(context, "ic_type_tuk"));
    }

    // Extracted from what used to be inline constructor logic -- looks up
    // a drawable by name (dynamic getIdentifier lookup, not a hardcoded
    // R.drawable reference, matching how ic_car_marker was already being
    // found), draws it into a bitmap, and falls back to the default
    // Google Maps pin if the drawable is missing or fails to load.
    private BitmapDescriptor loadIconFromDrawableName(Context context, String drawableName) {
        try {
            int resId = context.getResources().getIdentifier(
                    drawableName, "drawable", context.getPackageName());
            if (resId != 0) {
                android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(context, resId);
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                        MARKER_SIZE_PX, MARKER_SIZE_PX, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                drawable.setBounds(0, 0, MARKER_SIZE_PX, MARKER_SIZE_PX);
                drawable.draw(canvas);
                return BitmapDescriptorFactory.fromBitmap(bitmap);
            }
        } catch (Exception e) {
            Log.w(TAG, drawableName + " not found or failed to load, falling back to default marker", e);
        }
        return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
    }

    // Real key normalization matching AddVehicleActivity's exact spinner
    // strings ("Car", "SUV", "Van", "Truck", "Bike", "Tuk"),
    // case-insensitive for safety, falling back to "car" for null/
    // unrecognized values.
    private BitmapDescriptor iconForCurrentType() {
        BitmapDescriptor icon = iconsByType.get(pendingVehicleType);
        return icon != null ? icon : iconsByType.get("car");
    }

    /**
     * Call once, after you know the vehicleId, to seed the trail from recent history.
     * This downloads the past travel locations and draws the blue line.
     */
    public void loadInitialTrail(String vehicleId, Runnable onComplete) {
        this.vehicleId = vehicleId;

        // Get time now, and time 2 hours ago (HISTORY_WINDOW_HOURS)
        String toIso = isoNow();
        String fromIso = isoHoursAgo(HISTORY_WINDOW_HOURS);

        // Call the server to get history for that time period
        api.getTrackingHistory(vehicleId, fromIso, toIso, 500).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<ResponseBody> call, Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        // If successful, read the locations
                        List<TrackingPoint> points = parseList(body.string());
                        pathPoints.clear();

                        // Add valid points to our trail line (reverse order to get chronological)
                        for (int i = points.size() - 1; i >= 0; i--) {
                            TrackingPoint p = points.get(i);
                            if (p.getLatitude() != 0 || p.getLongitude() != 0) {
                                pathPoints.add(new LatLng(p.getLatitude(), p.getLongitude()));
                            }
                        }
                        // Draw the blue line on the map
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

    // NEW -- real bug fix. Without this, switching to a different vehicle
    // went through the exact same animateMarkerTo() path as a normal live
    // poll of the SAME vehicle -- since the marker object persists for the
    // renderer's entire lifetime (marker == null is only ever true once,
    // the very first time a position ever arrives), the marker would
    // sweep/animate across the whole map from the previously-selected
    // vehicle's position to the newly-selected one's, taking up to
    // MAX_ANIMATION_DURATION_MS (25s) if elapsed time happened to be
    // large. Confirmed as the actual root cause of "the switching
    // transition is really bad."
    //
    // Removing the marker entirely makes the next updatePosition() call
    // go through the marker == null branch again, placing the new
    // vehicle's marker instantly with no animation -- correct behavior
    // for a genuine switch, since there's no real continuous motion to
    // animate between two different vehicles' positions.
    // NEW -- for the initial marker placement on app load, where there's
    // no existing marker/trail to reset, just the type to set before the
    // very first updatePosition() call creates the marker.
    public void setVehicleTypeForNextMarker(String vehicleType) {
        pendingVehicleType = vehicleType != null
                ? vehicleType.trim().toLowerCase(java.util.Locale.US) : "car";
    }

    public void resetForVehicleSwitch(String vehicleType) {
        pendingVehicleType = vehicleType != null
                ? vehicleType.trim().toLowerCase(java.util.Locale.US) : "car";
        if (marker != null) {
            marker.remove();
            marker = null;
        }
        if (polyline != null) {
            polyline.remove();
            polyline = null;
        }
        pathPoints.clear();
        lastAcceptedFixTimeMs = 0;
    }

    public void updatePosition(LatLng newPos, float heading, String title) {
        long now = System.currentTimeMillis();

        // If this is the first location received, just put the car on the map
        if (marker == null) {
            marker = map.addMarker(new MarkerOptions()
                    .position(newPos)
                    .icon(iconForCurrentType())
                    .anchor(0.5f, 0.5f) // Put the icon center directly on the coordinate
                    .flat(true) // Make the car icon lie flat on the map
                    .rotation(heading + ICON_ROTATION_OFFSET)
                    .title(title));
            currentBearing = heading;
            lastAcceptedFixTimeMs = now;
            appendToPathIfNew(newPos);
            return;
        }

        // Calculate distance from old location to new location
        double distanceMeters = haversineMeters(marker.getPosition(), newPos);

        if (distanceMeters < MIN_MEANINGFUL_MOVEMENT_METERS) {
            // Effectively unchanged (GPS noise floor). lastAcceptedFixTimeMs
            // is deliberately left alone so it keeps tracking time since the
            // last position that actually changed.
            // If movement is very small (less than 3 meters), ignore it as fake GPS jumping
            return;
        }

        // Check time difference to calculate speed
        long elapsedMs = now - lastAcceptedFixTimeMs;

        if (elapsedMs >= MIN_INTERVAL_FOR_SPEED_CHECK_MS) {
            // Calculate speed in meters per second
            double impliedSpeedMps = distanceMeters / (elapsedMs / 1000.0);

            // If calculated speed is too high (e.g. 300km/h), it's a fake GPS jump. Ignore it.
            if (impliedSpeedMps > MAX_PLAUSIBLE_SPEED_MPS) {
                Log.w(TAG, "Rejected fix as GPS jitter: " + String.format(Locale.US,
                        "%.0fm in %.1fs implies %.0f km/h (max plausible %.0f km/h)",
                        distanceMeters, elapsedMs / 1000.0,
                        impliedSpeedMps * 3.6, MAX_PLAUSIBLE_SPEED_MPS * 3.6));
                return;
            }
        }
        // Below MIN_INTERVAL_FOR_SPEED_CHECK_MS, the speed check is skipped
        // and the fix is accepted unfiltered -- too short an interval to
        // divide by reliably.

        // Calculate how long the movement animation should take
        long animationDurationMs = Math.max(MIN_ANIMATION_DURATION_MS,
                Math.min(elapsedMs, MAX_ANIMATION_DURATION_MS));

        // The polyline's growing tip tracks the marker on every animation
        // frame (extendPolylineLiveTip, below); newPos is only permanently
        // committed to pathPoints once the animation actually reaches it.
        // Start smooth moving animation
        animateMarkerTo(marker, marker.getPosition(), newPos, currentBearing, heading, animationDurationMs, newPos);

        // Update records to the new position
        currentBearing = heading;
        lastAcceptedFixTimeMs = now;
    }

    // Add a new point to the blue line if it's different from the last one
    private void appendToPathIfNew(LatLng newPos) {
        if (pathPoints.isEmpty() || !pathPoints.get(pathPoints.size() - 1).equals(newPos)) {
            pathPoints.add(newPos);
            redrawPolyline();
            requestRoadSnap(); // Try to align line with actual Google Roads
        }
    }

    // Corrects the drawn TRAIL toward the actual road network -- deliberately
    // NOT used for the live marker's own animation target (see class-level
    // design note): waiting on this network call before animating the
    // marker would reintroduce the exact lag/jump problem already fixed
    // earlier. This is best-effort -- if it fails or is slow, the raw
    // jitter-filtered trail already drawn simply stands as-is; nothing
    // blocks on it.
    private void requestRoadSnap() {
        if (vehicleId == null || api == null) return;

        // Get the last 5 points to analyze road direction
        int startIndex = Math.max(0, pathPoints.size() - SNAP_BUFFER_SIZE);
        List<LatLng> snapshot = new ArrayList<>(pathPoints.subList(startIndex, pathPoints.size()));
        if (snapshot.size() < 2) return; // need at least 2 points for meaningful road context

        // Prepare data to send to server
        SnapToRoadRequest request = new SnapToRoadRequest();
        for (LatLng p : snapshot) {
            request.points.add(new SnapToRoadRequest.Point(p.latitude, p.longitude));
        }

        final int capturedStartIndex = startIndex;
        final int capturedWindowSize = snapshot.size();

        // Send points to server to snap to the closest real road
        api.snapToRoad(vehicleId, request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<ResponseBody> call, Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        Log.w(TAG, "Road snap failed, code " + response.code());
                        return;
                    }
                    // Apply corrected road points to the map
                    List<SnappedPoint> snapped = parseSnappedPoints(body.string());
                    applySnappedPoints(capturedStartIndex, capturedWindowSize, snapped);
                } catch (Exception e) {
                    Log.e(TAG, "Error applying road snap", e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ResponseBody> call, Throwable t) {
                // If it fails, the map will just keep showing the raw GPS line
                Log.w(TAG, "Road snap request failed (non-fatal, trail keeps raw points)", t);
            }
        });
    }

    // Replaces the SNAP_BUFFER_SIZE-sized window of pathPoints starting at
    // startIndex with the snapped coordinates, matched by Google's own
    // originalIndex (not assumed positional -- Google can drop points far
    // from any known road, so the result list may be shorter than the
    // request). Guards against pathPoints having changed shape since the
    // request was sent (new real points may have arrived while this was
    // in flight) by only ever writing within the originally-requested
    // window.
    private void applySnappedPoints(int startIndex, int windowSize, List<SnappedPoint> snapped) {
        if (snapped == null || snapped.isEmpty()) return;
        boolean changed = false;

        // Loop through the corrected road points and update our main trail list
        for (SnappedPoint sp : snapped) {
            if (sp.getOriginalIndex() == null) continue;
            int targetIndex = startIndex + sp.getOriginalIndex();

            // Only update points that are within our safe window
            if (targetIndex < startIndex || targetIndex >= startIndex + windowSize) continue;
            if (targetIndex >= pathPoints.size()) continue;

            // Apply the new corrected coordinates
            pathPoints.set(targetIndex, new LatLng(sp.getLatitude(), sp.getLongitude()));
            changed = true;
        }

        // If points were updated, redraw the blue line
        if (changed) redrawPolyline();
    }

    // Helper method to read the Road-Snapping JSON data from the server
    private List<SnappedPoint> parseSnappedPoints(String json) {
        List<SnappedPoint> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                list = gson.fromJson(root.getAsJsonArray("data"),
                        TypeToken.getParameterized(List.class, SnappedPoint.class).getType());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing road-snap response", e);
        }
        return list;
    }

    /**
     * Great-circle distance in meters between two points (Haversine formula).
     * Calculates the real-world distance between two GPS coordinates.
     */
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

    // Engine to smoothly move the car marker from point A to point B on the map
    private void animateMarkerTo(Marker marker, LatLng from, LatLng to, float fromBearing, float toBearing,
                                 long durationMs, LatLng committingPoint) {

        // Stop any running animations
        if (currentAnimator != null) {
            currentAnimator.cancel();
        }

        // Shorter rotation path (e.g. 350 -> 10 deg turns +20, not -340).
        // Decide which way to turn the car icon (left or right) for the shortest turn
        float bearingDelta = ((toBearing - fromBearing + 540) % 360) - 180;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        final boolean[] wasCancelled = {false};
        animator.setDuration(durationMs);

        // FIX: no interpolator was set, so this used Android's platform
        // default (AccelerateDecelerateInterpolator). Fine for a single,
        // isolated animation, but each new GPS fix cancels the previous
        // animator and starts a fresh one -- chaining multiple ease-in-
        // ease-out segments back-to-back means the marker decelerates to
        // a stop at the end of each segment, then re-accelerates for the
        // next one, creating a subtle stutter/pulse at every boundary.
        // Linear keeps a constant speed through each segment, which flows
        // continuously into the next update instead.
        animator.setInterpolator(new LinearInterpolator());

        // Calculate the smooth position of the car for every single animation frame
        animator.addUpdateListener(animation -> {
            float f = (float) animation.getAnimatedValue();

            // Calculate intermediate location
            double lat = from.latitude + (to.latitude - from.latitude) * f;
            double lng = from.longitude + (to.longitude - from.longitude) * f;
            LatLng interpolated = new LatLng(lat, lng);

            // Update car icon location and rotation
            marker.setPosition(interpolated);
            marker.setRotation((fromBearing + bearingDelta * f + ICON_ROTATION_OFFSET + 360) % 360);

            // Draw the trail following behind the car
            extendPolylineLiveTip(interpolated);
        });

        // Actions to do when animation finishes
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                wasCancelled[0] = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // cancel()'s interaction with onAnimationEnd is inconsistent
                // across Android versions, hence the explicit flag rather
                // than relying on which callback fires.

                // If animation completed normally, permanently save the point
                if (!wasCancelled[0]) {
                    appendToPathIfNew(committingPoint);
                }
            }
        });
        currentAnimator = animator;
        animator.start();
    }

    // Fully recreate and draw the blue trail line
    private void redrawPolyline() {
        // Curve the sharp corners using Catmull-Rom smoothing
        smoothedSettledPoints = pathPoints.size() < 2 ? new ArrayList<>(pathPoints) : smoothPath(pathPoints);

        // Delete old line
        if (polyline != null) polyline.remove();
        if (smoothedSettledPoints.size() < 2) return;

        // Draw new thick blue line
        polyline = map.addPolyline(new PolylineOptions()
                .addAll(smoothedSettledPoints)
                .width(10f)
                .color(Color.parseColor("#1877F2"))
                .zIndex(1f)
                .geodesic(true));
    }

    /**
     * Called every animation frame; reuses the cached smoothed trail and appends
     * only the marker's current interpolated position, via setPoints() rather than
     * rebuilding the whole polyline each frame.
     * Makes the blue line follow the car live without lagging.
     */
    private void extendPolylineLiveTip(LatLng livePos) {
        if (smoothedSettledPoints.isEmpty()) return;
        List<LatLng> renderPoints = new ArrayList<>(smoothedSettledPoints);
        renderPoints.add(livePos); // Add current live car location

        if (polyline == null) {
            // Draw new line if none exists
            polyline = map.addPolyline(new PolylineOptions()
                    .addAll(renderPoints)
                    .width(10f)
                    .color(Color.parseColor("#1877F2"))
                    .zIndex(1f)
                    .geodesic(true));
        } else {
            // Update existing line fast
            polyline.setPoints(renderPoints);
        }
    }

    /**
     * Catmull-Rom spline: passes through every real point while smoothing between them.
     * This turns straight, sharp lines into realistic curved roads.
     */
    private List<LatLng> smoothPath(List<LatLng> raw) {
        if (raw.size() < 3) return raw;

        List<LatLng> smoothed = new ArrayList<>();
        int n = raw.size();
        for (int i = 0; i < n - 1; i++) {
            LatLng p0 = raw.get(Math.max(i - 1, 0));
            LatLng p1 = raw.get(i);
            LatLng p2 = raw.get(Math.min(i + 1, n - 1));
            LatLng p3 = raw.get(Math.min(i + 2, n - 1));

            // Generate small points to create the curve
            for (int t = 0; t < CATMULL_ROM_SEGMENTS; t++) {
                double f = t / (double) CATMULL_ROM_SEGMENTS;
                smoothed.add(catmullRomPoint(p0, p1, p2, p3, f));
            }
        }
        smoothed.add(raw.get(n - 1));
        return smoothed;
    }

    // Mathematical formula to calculate coordinates for smooth curves
    private LatLng catmullRomPoint(LatLng p0, LatLng p1, LatLng p2, LatLng p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;

        double lat = 0.5 * ((2 * p1.latitude)
                + (-p0.latitude + p2.latitude) * t
                + (2 * p0.latitude - 5 * p1.latitude + 4 * p2.latitude - p3.latitude) * t2
                + (-p0.latitude + 3 * p1.latitude - 3 * p2.latitude + p3.latitude) * t3);

        double lng = 0.5 * ((2 * p1.longitude)
                + (-p0.longitude + p2.longitude) * t
                + (2 * p0.longitude - 5 * p1.longitude + 4 * p2.longitude - p3.longitude) * t2
                + (-p0.longitude + 3 * p1.longitude - 3 * p2.longitude + p3.longitude) * t3);

        return new LatLng(lat, lng);
    }

    // Helper method to convert JSON text of tracking history into a Java List
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

    // Get current time in standard UTC ISO format (e.g. 2026-08-08T12:00:00Z)
    private String isoNow() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new java.util.Date());
    }

    // Get time 'X' hours ago in standard UTC ISO format
    private String isoHoursAgo(int hours) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.add(Calendar.HOUR_OF_DAY, -hours);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(cal.getTime());
    }
}