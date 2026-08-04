package com.example.letstracklanka.ui.history;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.letstracklanka.data.model.TrackingPoint;

/**
 * Loads a Google Static Maps thumbnail into an ImageView for a trip's
 * start/end points, with in-memory caching so re-binding a recycled row
 * (scrolling) doesn't re-fetch an image already loaded once.
 *
 * Deliberately NOT a live embedded MapView/SupportMapFragment: a scrolling
 * RecyclerView full of live interactive maps is a well-documented
 * performance/memory problem (each one spins up its own native GL
 * rendering context). A static image is the standard approach for map
 * thumbnails in a list.
 *
 * Uses a DEDICATED API key (meta-data name in STATIC_MAPS_META_DATA_KEY),
 * separate from the Maps SDK's own "com.google.android.geo.API_KEY". The
 * SDK key is Android-app-restricted (package name + SHA-1), which is
 * correct for SDK calls but rejects raw HTTP requests like this class
 * makes -- confirmed via an actual 403 with that exact cause. This
 * dedicated key should be created in Cloud Console with API restrictions
 * limited to "Maps Static API" but no Application (Android-app)
 * restriction. Read from AndroidManifest.xml meta-data at runtime via
 * PackageManager -- never hardcoded here.
 */
public class StaticMapLoader {

    private static final String TAG = "StaticMapLoader";
    private static final int CACHE_SIZE_ENTRIES = 40;
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> cache = new LruCache<>(CACHE_SIZE_ENTRIES);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String STATIC_MAPS_META_DATA_KEY = "com.example.letstracklanka.STATIC_MAPS_API_KEY";
    private static String cachedApiKey;

    /**
     * @param widthPx/heightPx should match the ImageView's actual rendered
     *                         size -- requesting a larger image than needed
     *                         wastes bandwidth and, since Static Maps is
     *                         billed per request regardless of size, gains
     *                         nothing.
     */
    public static void load(ImageView imageView, double startLat, double startLng,
                            double endLat, double endLng, int widthPx, int heightPx) {
        Context context = imageView.getContext();
        String apiKey = getApiKey(context);
        if (apiKey == null) {
            Log.w(TAG, "No Maps API key found in manifest meta-data; skipping map thumbnail load.");
            return;
        }

        String url = buildUrl(startLat, startLng, endLat, endLng, widthPx, heightPx, apiKey);

        // Guards against a slow-loading image landing on a recycled row
        // that has since been rebound to a different trip -- same pattern
        // already used for address resolution in this adapter.
        imageView.setTag(url);

        Bitmap cached = cache.get(url);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        executor.submit(() -> {
            Bitmap bitmap = downloadBitmap(url);
            if (bitmap != null) {
                cache.put(url, bitmap);
                mainHandler.post(() -> {
                    if (url.equals(imageView.getTag())) {
                        imageView.setImageBitmap(bitmap);
                    }
                });
            }
        });
    }

    private static String buildUrl(double startLat, double startLng, double endLat, double endLng,
                                   int widthPx, int heightPx, String apiKey) {
        // Straight line between start/end, NOT the actual driven route --
        // this list-row granularity doesn't have the full per-trip GPS
        // point array (that's a separate, heavier fetch used only in
        // TripDetailActivity's full playback screen).
        return String.format(Locale.US,
                "https://maps.googleapis.com/maps/api/staticmap?size=%dx%d&maptype=roadmap"
                        + "&markers=color:green%%7Clabel:S%%7C%f,%f"
                        + "&markers=color:red%%7Clabel:E%%7C%f,%f"
                        + "&path=color:0x1877F2FF%%7Cweight:4%%7C%f,%f%%7C%f,%f"
                        + "&key=%s",
                widthPx, heightPx, startLat, startLng, endLat, endLng,
                startLat, startLng, endLat, endLng, apiKey);
    }

    // Max points sent to Static Maps for a real route. A ~20s-interval
    // trip can easily have 100-300+ raw points; a small 140dp-tall
    // thumbnail doesn't need that much fidelity, and Static Maps URLs
    // have a real length ceiling (8192 chars with a valid key). Downsampled
    // via simple fixed-stride sampling below, not a real curve-simplification
    // algorithm (e.g. Douglas-Peucker) -- good enough for a thumbnail, not
    // claiming geometric precision.
    private static final int MAX_ROUTE_POINTS = 100;

    /**
     * Loads a thumbnail using the trip's REAL GPS points, not a straight
     * line -- see loadRealRouteMap() in TripHistoryAdapter for the real
     * cost tradeoff this requires (an extra network call per trip row to
     * fetch the point history in the first place).
     */
    public static void loadRoute(ImageView imageView, List<TrackingPoint> points, int widthPx, int heightPx) {
        if (points == null || points.size() < 2) return;
        Context context = imageView.getContext();
        String apiKey = getApiKey(context);
        if (apiKey == null) {
            Log.w(TAG, "No Maps API key found in manifest meta-data; skipping map thumbnail load.");
            return;
        }

        String url = buildRouteUrl(points, widthPx, heightPx, apiKey);
        imageView.setTag(url);

        Bitmap cached = cache.get(url);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        executor.submit(() -> {
            Bitmap bitmap = downloadBitmap(url);
            if (bitmap != null) {
                cache.put(url, bitmap);
                mainHandler.post(() -> {
                    if (url.equals(imageView.getTag())) {
                        imageView.setImageBitmap(bitmap);
                    }
                });
            }
        });
    }

    private static String buildRouteUrl(List<TrackingPoint> points, int widthPx, int heightPx, String apiKey) {
        List<TrackingPoint> sampled = downsample(points, MAX_ROUTE_POINTS);

        List<double[]> latLngs = new ArrayList<>(sampled.size());
        for (TrackingPoint p : sampled) {
            latLngs.add(new double[]{p.getLatitude(), p.getLongitude()});
        }
        String encodedPath = encodePolyline(latLngs);

        TrackingPoint first = sampled.get(0);
        TrackingPoint last = sampled.get(sampled.size() - 1);

        String encodedPathParam;
        try {
            encodedPathParam = java.net.URLEncoder.encode(encodedPath, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "Error URL-encoding polyline", e);
            encodedPathParam = "";
        }

        return String.format(Locale.US,
                "https://maps.googleapis.com/maps/api/staticmap?size=%dx%d&maptype=roadmap"
                        + "&markers=color:green%%7Clabel:S%%7C%f,%f"
                        + "&markers=color:red%%7Clabel:E%%7C%f,%f"
                        + "&path=color:0x1877F2FF%%7Cweight:4%%7Cenc:%s"
                        + "&key=%s",
                widthPx, heightPx,
                first.getLatitude(), first.getLongitude(),
                last.getLatitude(), last.getLongitude(),
                encodedPathParam, apiKey);
    }

    /** Simple fixed-stride downsampling -- always keeps the first and last
     * point (so the route visually starts/ends exactly at the markers),
     * evenly thins everything in between. Not a real curve-simplification
     * algorithm; good enough for a small thumbnail, not claiming geometric
     * precision. */
    private static List<TrackingPoint> downsample(List<TrackingPoint> points, int maxPoints) {
        if (points.size() <= maxPoints) return points;
        List<TrackingPoint> result = new ArrayList<>(maxPoints);
        double stride = (points.size() - 1) / (double) (maxPoints - 1);
        for (int i = 0; i < maxPoints; i++) {
            int index = (int) Math.round(i * stride);
            index = Math.min(index, points.size() - 1);
            result.add(points.get(index));
        }
        return result;
    }

    /**
     * Google's standard Encoded Polyline Algorithm Format. This is a
     * fixed, publicly documented specification (not something inferred or
     * guessed) -- delta-encodes each lat/lng against the previous point,
     * scaled to 1e-5 degree precision, then packs into 5-bit chunks
     * offset by 63 into printable ASCII.
     */
    private static String encodePolyline(List<double[]> points) {
        StringBuilder sb = new StringBuilder();
        long lastLat = 0;
        long lastLng = 0;
        for (double[] point : points) {
            long lat = Math.round(point[0] * 1e5);
            long lng = Math.round(point[1] * 1e5);
            encodeSignedNumber(sb, (int) (lat - lastLat));
            encodeSignedNumber(sb, (int) (lng - lastLng));
            lastLat = lat;
            lastLng = lng;
        }
        return sb.toString();
    }

    private static void encodeSignedNumber(StringBuilder sb, int num) {
        int sgnNum = num << 1;
        if (num < 0) sgnNum = ~sgnNum;
        encodeNumber(sb, sgnNum);
    }

    private static void encodeNumber(StringBuilder sb, int num) {
        while (num >= 0x20) {
            int nextValue = (0x20 | (num & 0x1f)) + 63;
            sb.append((char) nextValue);
            num >>= 5;
        }
        num += 63;
        sb.append((char) num);
    }

    private static Bitmap downloadBitmap(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.connect();
            if (connection.getResponseCode() != 200) {
                // FIX: previously only logged the status code, which left a
                // 403 ambiguous between "API not enabled" and "key
                // restriction mismatch" -- two different fixes. Google's
                // error responses (getErrorStream(), NOT getInputStream(),
                // which throws for non-2xx codes) explain which one it
                // actually is.
                String errorBody = readErrorStream(connection);
                Log.w(TAG, "Static map request failed, code " + connection.getResponseCode()
                        + ", body=" + errorBody);
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error downloading static map", e);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readErrorStream(HttpURLConnection connection) {
        try (InputStream err = connection.getErrorStream()) {
            if (err == null) return "(no error body)";
            java.util.Scanner scanner = new java.util.Scanner(err, "UTF-8").useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "(empty error body)";
        } catch (Exception e) {
            return "(couldn't read error body: " + e.getMessage() + ")";
        }
    }

    private static String getApiKey(Context context) {
        if (cachedApiKey != null) return cachedApiKey;
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA);
            if (appInfo.metaData != null) {
                // FIX: was reading "com.google.android.geo.API_KEY" -- the
                // same key the Maps SDK uses, which is Android-app-
                // restricted and rejects raw HTTP requests like this one
                // makes (confirmed via a 403 with that exact cause). Now
                // reads a separate, dedicated key created specifically for
                // Static Maps, with no Android-app restriction, so it
                // doesn't touch or weaken the SDK key's own protection.
                cachedApiKey = appInfo.metaData.getString(STATIC_MAPS_META_DATA_KEY);
                if (cachedApiKey == null) {
                    Log.w(TAG, "No meta-data found for " + STATIC_MAPS_META_DATA_KEY
                            + " -- add it to AndroidManifest.xml with your dedicated Static Maps key.");
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not read Maps API key from manifest", e);
        }
        return cachedApiKey;
    }
}