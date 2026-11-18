package com.example.letstracklanka.ui.main;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reverse-geocodes lat/lng into a human-readable address using Android's free,
 * built-in Geocoder (not Google's paid Geocoding API -- no billing setup, no
 * per-request cost, consistent with the project's cost-minimization priority).
 *
 * Debounced: only performs a real lookup when the position has moved more than
 * MIN_MOVE_METERS since the last lookup.
 *
 * FIX (round 2): getAddressLine(0) was tried FIRST, but for some areas Google's
 * formatted line leads with a Plus Code (e.g. "2WG4+GMG, Ragama, Sri Lanka") even
 * though the individual getLocality()/getSubAdminArea() fields have a clean name
 * ("Ragama") available. Now tries the clean structured fields first, broadening
 * from most to least specific, and only falls back to the raw address line (which
 * may contain a Plus Code) as the last resort before raw coordinates.
 */
public class AddressResolver {

    private static final String TAG = "AddressResolver";
    private static final float MIN_MOVE_METERS = 30f;

    private final Geocoder geocoder;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private double lastLat = Double.NaN;
    private double lastLng = Double.NaN;
    private String lastAddress = null;

    public interface AddressCallback {
        void onAddress(String address);
    }

    public AddressResolver(Context context) {
        this.geocoder = new Geocoder(context, Locale.getDefault());
    }

    public void resolveAddress(double lat, double lng, AddressCallback callback) {
        String fallback = String.format(Locale.getDefault(), "%.6f, %.6f", lat, lng);

        if (!Geocoder.isPresent()) {
            callback.onAddress(fallback);
            return;
        }

        if (!Double.isNaN(lastLat) && lastAddress != null) {
            float[] results = new float[1];
            Location.distanceBetween(lastLat, lastLng, lat, lng, results);
            if (results[0] < MIN_MOVE_METERS) {
                callback.onAddress(lastAddress);
                return;
            }
        }

        executor.execute(() -> {
            String resolved = fallback;
            try {
                @SuppressWarnings("deprecation")
                List<Address> results = geocoder.getFromLocation(lat, lng, 1);
                if (results != null && !results.isEmpty()) {
                    Address a = results.get(0);

                    // FIX: try clean structured fields FIRST, most to least specific.
                    StringBuilder sb = new StringBuilder();
                    if (a.getThoroughfare() != null) sb.append(a.getThoroughfare());
                    if (a.getSubLocality() != null) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(a.getSubLocality());
                    }
                    if (a.getLocality() != null) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(a.getLocality());
                    }
                    if (sb.length() == 0 && a.getSubAdminArea() != null) {
                        sb.append(a.getSubAdminArea());
                    }
                    if (sb.length() == 0 && a.getAdminArea() != null) {
                        sb.append(a.getAdminArea());
                    }
                    if (sb.length() == 0 && a.getFeatureName() != null) {
                        sb.append(a.getFeatureName());
                    }

                    if (sb.length() > 0) {
                        resolved = sb.toString();
                    } else {
                        // Last resort: the geocoder's own formatted line, which may
                        // contain a Plus Code for less-mapped areas -- better than
                        // raw coordinates, but not preferred over the fields above.
                        String addressLine = a.getAddressLine(0);
                        if (addressLine != null && !addressLine.trim().isEmpty()) {
                            resolved = addressLine;
                        }
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "Geocoding failed, using coordinates fallback", e);
            } catch (Exception e) {
                Log.w(TAG, "Unexpected geocoding error, using coordinates fallback", e);
            }

            lastLat = lat;
            lastLng = lng;
            lastAddress = resolved;

            String finalResolved = resolved;
            mainHandler.post(() -> callback.onAddress(finalResolved));
        });
    }

    // ── Shared, cached lookups for lists (trip cards) ─────────────────────────────
    // A list shows many different places, so the "same as last time" debounce above does not help, and
    // the old trip list created one resolver (one thread) per card. These share ONE background thread and
    // a cache keyed to ~11 m, so scrolling back up never geocodes the same place twice.

    private static final ExecutorService SHARED_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final android.util.LruCache<String, String> SHARED_CACHE = new android.util.LruCache<>(200);
    private static final Handler SHARED_MAIN = new Handler(Looper.getMainLooper());

    /** Cached place name for a point; calls back on the main thread. */
    public static void resolveCached(Context context, double lat, double lng, AddressCallback callback) {
        String key = String.format(Locale.US, "%.4f,%.4f", lat, lng);
        String hit = SHARED_CACHE.get(key);
        if (hit != null) {
            callback.onAddress(hit);
            return;
        }
        Context app = context.getApplicationContext();
        SHARED_EXECUTOR.execute(() -> {
            String name = lookupBlocking(app, lat, lng);
            SHARED_CACHE.put(key, name);
            SHARED_MAIN.post(() -> callback.onAddress(name));
        });
    }

    private static String lookupBlocking(Context context, double lat, double lng) {
        String fallback = String.format(Locale.getDefault(), "%.5f, %.5f", lat, lng);
        if (!Geocoder.isPresent()) return fallback;
        try {
            @SuppressWarnings("deprecation")
            List<Address> results = new Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1);
            if (results == null || results.isEmpty()) return fallback;
            Address a = results.get(0);
            StringBuilder sb = new StringBuilder();
            if (a.getThoroughfare() != null) sb.append(a.getThoroughfare());
            if (a.getSubLocality() != null) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(a.getSubLocality());
            }
            if (a.getLocality() != null) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(a.getLocality());
            }
            if (sb.length() == 0 && a.getSubAdminArea() != null) sb.append(a.getSubAdminArea());
            if (sb.length() == 0 && a.getAdminArea() != null) sb.append(a.getAdminArea());
            if (sb.length() > 0) return sb.toString();
            String line = a.getAddressLine(0);
            return line != null && !line.trim().isEmpty() ? line : fallback;
        } catch (Exception e) {
            Log.w(TAG, "Geocoding failed, using coordinates", e);
            return fallback;
        }
    }
}