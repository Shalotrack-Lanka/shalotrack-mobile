package com.example.letstracklanka.ui.main;


import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.AlertResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.vehicles.VehiclesActivity;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AlertsActivity extends AppCompatActivity {

    // API Service for making network calls
    private ApiService mainApiService;

    // UI Components for the Alerts list
    private RecyclerView recyclerAlerts;
    private AlertAdapter adapter;
    private ProgressBar progressAlerts;
    private TextView tvEmptyAlerts;

    // UI Components for displaying network errors
    private View errorBanner;
    private TextView tvErrorBannerMessage, tvErrorBannerRetry;
    private ConnectivityManager.NetworkCallback networkCallback;

    // Variable to track which tab is currently active (Alerts or Promotions)
    // "Promotions" has no backend or data model behind it anywhere in this app --
    // it's left as an honest placeholder (Toast), not wired to fake data.
    private boolean showingAlertsTab = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Link this activity to its XML layout file
        setContentView(R.layout.activity_alerts);

        // Initialize the API client
        mainApiService = ApiClient.getClient().create(ApiService.class);

        // Connect error banner UI elements
        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        tvErrorBannerRetry = findViewById(R.id.tvErrorBannerRetry);

        // Start checking for internet connection
        registerNetworkMonitor();

        // Load the Google Map in the background
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapAlerts);
        if (mapFragment != null) {
            mapFragment.getMapAsync(googleMap -> { });
        }

        // Setup the list that will display the alerts
        initAlertsList();

        // --- Handle switching between 'Alerts' and 'Promotions' tabs ---
        MaterialButton btnTabAlerts = findViewById(R.id.btnTabAlerts);
        MaterialButton btnTabPromotions = findViewById(R.id.btnTabPromotions);

        // FIX: Search isn't a real feature yet -- removed the Toast entirely
        // rather than replace it with another popup. A "coming soon" message
        // doesn't fit the error-banner styling (which is deliberately red/
        // danger-colored), so the honest choice is a genuinely inert icon,
        // not a fake interaction.

        // When the user clicks the "Alerts" tab
        btnTabAlerts.setOnClickListener(v -> {
            // Highlight the Alerts tab with the primary brand color
            btnTabAlerts.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, com.example.letstracklanka.R.color.brand_primary)));
            btnTabAlerts.setTextColor(Color.WHITE);
            // Remove highlight from the Promotions tab
            btnTabPromotions.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            btnTabPromotions.setTextColor(Color.BLACK);

            // Show the alerts list and fetch data from server
            showingAlertsTab = true;
            recyclerAlerts.setVisibility(View.VISIBLE);
            fetchAlerts();
        });

        // When the user clicks the "Promotions" tab
        btnTabPromotions.setOnClickListener(v -> {
            // Highlight the Promotions tab with the primary brand color
            btnTabPromotions.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, com.example.letstracklanka.R.color.brand_primary)));
            btnTabPromotions.setTextColor(Color.WHITE);
            // Remove highlight from the Alerts tab
            btnTabAlerts.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            btnTabAlerts.setTextColor(Color.BLACK);

            // Hide the alerts list and show a "coming soon" message
            // Deliberately not wired to real data -- there is no Promotions feature
            // or data model anywhere in this app yet.
            showingAlertsTab = false;
            recyclerAlerts.setVisibility(View.GONE);
            progressAlerts.setVisibility(View.GONE);
            tvEmptyAlerts.setVisibility(View.VISIBLE);
            tvEmptyAlerts.setText("Promotions coming soon.");
        });

        // --- Bottom Navigation Setup (Clicking on bottom menu icons) ---

        // Go to Home screen
        LinearLayout navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                Intent intent = new Intent(AlertsActivity.this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // Go to Vehicles screen
        LinearLayout navVehicles = findViewById(R.id.nav_vehicles);
        if (navVehicles != null) {
            navVehicles.setOnClickListener(v -> {
                Intent intent = new Intent(AlertsActivity.this, VehiclesActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // Go to Tags screen
        LinearLayout navTags = findViewById(R.id.nav_tags);
        if (navTags != null) {
            navTags.setOnClickListener(v -> {
                Intent intent = new Intent(AlertsActivity.this, TagsActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // Go to Circles screen
        LinearLayout navCircles = findViewById(R.id.nav_circles);
        if (navCircles != null) {
            navCircles.setOnClickListener(v -> {
                Intent intent = new Intent(AlertsActivity.this, CirclesActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }
    }

    // Set up the RecyclerView (List) to show alerts
    private void initAlertsList() {
        recyclerAlerts = findViewById(R.id.recyclerAlerts);
        progressAlerts = findViewById(R.id.progressAlerts);
        tvEmptyAlerts = findViewById(R.id.tvEmptyAlerts);

        // Prepare the list view layout
        recyclerAlerts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AlertAdapter(new ArrayList<>(), this::onAlertClicked);
        recyclerAlerts.setAdapter(adapter);

        // Call the server to get alerts
        fetchAlerts();
    }

    // Get the latest alerts from the server API
    private void fetchAlerts() {
        // Show loading spinner while waiting for data
        progressAlerts.setVisibility(View.VISIBLE);
        recyclerAlerts.setVisibility(View.GONE);
        tvEmptyAlerts.setVisibility(View.GONE);

        // Send network request to get page 1, max 20 alerts
        mainApiService.getMyAlerts(1, 20).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                progressAlerts.setVisibility(View.GONE); // Hide loading spinner
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        // If successful, read JSON data and display alerts
                        List<AlertResponse> alerts = parseList(body.string(), AlertResponse.class);
                        renderAlerts(alerts);
                    } else {
                        // Show error message if API fails
                        showEmpty("Could not load alerts (code " + response.code() + ")");
                        showRetryDialog("Couldn't load alerts", AlertsActivity.this::fetchAlerts);
                    }
                } catch (Exception e) {
                    // Show error if data format is wrong
                    Log.e("AlertsActivity", "fetchAlerts parse error", e);
                    showEmpty("Something went wrong loading alerts.");
                    showRetryDialog("Something went wrong loading alerts", AlertsActivity.this::fetchAlerts);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                // Show error if there is no internet or server is down
                progressAlerts.setVisibility(View.GONE);
                showEmpty("Network error -- check your connection.");
                showRetryDialog("Network error — couldn't load alerts", AlertsActivity.this::fetchAlerts);
            }
        });
    }

    // Display the received alerts in the list UI
    private void renderAlerts(List<AlertResponse> alerts) {
        if (!showingAlertsTab) return;   // Stop if the user switched tabs before data arrived

        if (alerts == null || alerts.isEmpty()) {
            showEmpty("No alerts yet."); // Show message if list is empty
            return;
        }

        // Show list and hide empty message
        recyclerAlerts.setVisibility(View.VISIBLE);
        tvEmptyAlerts.setVisibility(View.GONE);
        adapter.updateAlerts(alerts);
    }

    // Helper method to show messages when there are no alerts or an error happens
    private void showEmpty(String message) {
        recyclerAlerts.setVisibility(View.GONE);
        tvEmptyAlerts.setVisibility(View.VISIBLE);
        tvEmptyAlerts.setText(message);
    }

    // When the user clicks an alert in the list
    private void onAlertClicked(AlertResponse alert) {
        if (alert.isRead()) return;   // If it's already read, do nothing

        // Tell the server to mark this alert as 'read'
        mainApiService.markAlertAsRead(alert.getAlertId()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    fetchAlerts();   // Reload the list so the read/unread dot updates
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("AlertsActivity", "markAlertAsRead failed", t);
                // Show a small retry popup if it failed to mark as read
                // FIX: was completely silent before -- small, non-blocking
                // indicator now, matching the requested "small in-line
                // indicator" for this minor, recoverable action.
                showRetryDialog("Couldn't mark alert as read", () -> onAlertClicked(alert));
            }
        });
    }

    /**
     * Show a red error banner at the top to let user try again.
     * Same reusable in-line banner pattern as Home/Vehicles/TripHistory.
     */
    private void showRetryDialog(String message, Runnable retryAction) {
        if (errorBanner == null) return;
        tvErrorBannerMessage.setText(message);
        tvErrorBannerRetry.setOnClickListener(v -> {
            hideErrorBanner();
            retryAction.run();
        });
        errorBanner.setVisibility(View.VISIBLE);
    }

    // Hide the red error banner
    private void hideErrorBanner() {
        if (errorBanner != null) errorBanner.setVisibility(View.GONE);
    }

    /**
     * Monitor internet connection changes (Wi-Fi or Mobile Data).
     * Same real-time connectivity monitoring pattern as Home/Vehicles.
     */
    private void registerNetworkMonitor() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(@NonNull Network network) {
                // Internet connection dropped
                runOnUiThread(() -> showRetryDialog("No internet connection", AlertsActivity.this::fetchAlerts));
            }

            @Override
            public void onAvailable(@NonNull Network network) {
                // Internet connection restored
                runOnUiThread(() -> {
                    hideErrorBanner();
                    fetchAlerts();
                });
            }
        };
        cm.registerDefaultNetworkCallback(networkCallback);
    }

    // Clean up background tasks to prevent memory leaks when screen is closed
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        }
    }

    // Helper method to convert JSON string into a Java List of objects safely
    private <T> List<T> parseList(String json, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                // Convert JSON array to Java List
                list = gson.fromJson(root.getAsJsonArray("data"), TypeToken.getParameterized(List.class, clazz).getType());
            }
        } catch (Exception e) {
            Log.e("AlertsActivity", "parseList error", e);
        }
        return list;
    }
}