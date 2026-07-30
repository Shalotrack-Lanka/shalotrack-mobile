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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
 * Uses the same Maps API key already configured for the Maps SDK
 * elsewhere in this app (read from the AndroidManifest.xml meta-data at
 * runtime via PackageManager -- never hardcoded here), assuming the
 * standard "com.google.android.geo.API_KEY" meta-data name and that
 * Static Maps API is enabled for that key in Google Cloud Console. If
 * thumbnails come back blank, check that second part first -- the Maps
 * SDK and Static Maps API are enabled/billed separately even when they
 * share one key.
 */
public class StaticMapLoader {

    private static final String TAG = "StaticMapLoader";
    private static final int CACHE_SIZE_ENTRIES = 40;
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> cache = new LruCache<>(CACHE_SIZE_ENTRIES);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

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
                cachedApiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY");
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not read Maps API key from manifest", e);
        }
        return cachedApiKey;
    }
}