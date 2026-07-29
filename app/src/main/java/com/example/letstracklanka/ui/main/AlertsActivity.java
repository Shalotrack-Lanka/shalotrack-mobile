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

    private ApiService mainApiService;

    private RecyclerView recyclerAlerts;
    private AlertAdapter adapter;
    private ProgressBar progressAlerts;
    private TextView tvEmptyAlerts;
    private View errorBanner;
    private TextView tvErrorBannerMessage, tvErrorBannerRetry;
    private ConnectivityManager.NetworkCallback networkCallback;

    // "Promotions" has no backend or data model behind it anywhere in this app --
    // it's left as an honest placeholder (Toast), not wired to fake data.
    private boolean showingAlertsTab = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alerts);

        mainApiService = ApiClient.getClient().create(ApiService.class);

        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        tvErrorBannerRetry = findViewById(R.id.tvErrorBannerRetry);
        registerNetworkMonitor();

        // Load map in background
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapAlerts);
        if (mapFragment != null) {
            mapFragment.getMapAsync(googleMap -> { });
        }

        initAlertsList();

        // --- Tab Switching Logic (Alerts vs Promotions) ---
        MaterialButton btnTabAlerts = findViewById(R.id.btnTabAlerts);
        MaterialButton btnTabPromotions = findViewById(R.id.btnTabPromotions);

        // FIX: Search isn't a real feature yet -- removed the Toast entirely
        // rather than replace it with another popup. A "coming soon" message
        // doesn't fit the error-banner styling (which is deliberately red/
        // danger-colored), so the honest choice is a genuinely inert icon,
        // not a fake interaction.

        btnTabAlerts.setOnClickListener(v -> {
            btnTabAlerts.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, com.example.letstracklanka.R.color.brand_primary)));
            btnTabAlerts.setTextColor(Color.WHITE);
            btnTabPromotions.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            btnTabPromotions.setTextColor(Color.BLACK);

            showingAlertsTab = true;
            recyclerAlerts.setVisibility(View.VISIBLE);
            fetchAlerts();
        });

        btnTabPromotions.setOnClickListener(v -> {
            btnTabPromotions.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, com.example.letstracklanka.R.color.brand_primary)));
            btnTabPromotions.setTextColor(Color.WHITE);
            btnTabAlerts.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            btnTabAlerts.setTextColor(Color.BLACK);

            // Deliberately not wired to real data -- there is no Promotions feature
            // or data model anywhere in this app yet.
            showingAlertsTab = false;
            recyclerAlerts.setVisibility(View.GONE);
            progressAlerts.setVisibility(View.GONE);
            tvEmptyAlerts.setVisibility(View.VISIBLE);
            tvEmptyAlerts.setText("Promotions coming soon.");
        });

        // --- Bottom Navigation Setup ---
        LinearLayout navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                Intent intent = new Intent(AlertsActivity.this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        LinearLayout navVehicles = findViewById(R.id.nav_vehicles);
        if (navVehicles != null) {
            navVehicles.setOnClickListener(v -> {
                Intent intent = new Intent(AlertsActivity.this, VehiclesActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        LinearLayout navTags = findViewById(R.id.nav_tags);
        if (navTags != null) {
            navTags.setOnClickListener(v -> {
                Intent intent = new Intent(AlertsActivity.this, TagsActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        LinearLayout navCircles = findViewById(R.id.nav_circles);
        if (navCircles != null) {
            navCircles.setOnClickListener(v -> {
                Intent intent = new Intent(AlertsActivity.this, CirclesActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }
    }

    private void initAlertsList() {
        recyclerAlerts = findViewById(R.id.recyclerAlerts);
        progressAlerts = findViewById(R.id.progressAlerts);
        tvEmptyAlerts = findViewById(R.id.tvEmptyAlerts);

        recyclerAlerts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AlertAdapter(new ArrayList<>(), this::onAlertClicked);
        recyclerAlerts.setAdapter(adapter);

        fetchAlerts();
    }

    private void fetchAlerts() {
        progressAlerts.setVisibility(View.VISIBLE);
        recyclerAlerts.setVisibility(View.GONE);
        tvEmptyAlerts.setVisibility(View.GONE);

        mainApiService.getMyAlerts(1, 20).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                progressAlerts.setVisibility(View.GONE);
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<AlertResponse> alerts = parseList(body.string(), AlertResponse.class);
                        renderAlerts(alerts);
                    } else {
                        showEmpty("Could not load alerts (code " + response.code() + ")");
                        showRetryDialog("Couldn't load alerts", AlertsActivity.this::fetchAlerts);
                    }
                } catch (Exception e) {
                    Log.e("AlertsActivity", "fetchAlerts parse error", e);
                    showEmpty("Something went wrong loading alerts.");
                    showRetryDialog("Something went wrong loading alerts", AlertsActivity.this::fetchAlerts);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                progressAlerts.setVisibility(View.GONE);
                showEmpty("Network error -- check your connection.");
                showRetryDialog("Network error — couldn't load alerts", AlertsActivity.this::fetchAlerts);
            }
        });
    }

    private void renderAlerts(List<AlertResponse> alerts) {
        if (!showingAlertsTab) return;   // tab may have changed while the request was in flight

        if (alerts == null || alerts.isEmpty()) {
            showEmpty("No alerts yet.");
            return;
        }
        recyclerAlerts.setVisibility(View.VISIBLE);
        tvEmptyAlerts.setVisibility(View.GONE);
        adapter.updateAlerts(alerts);
    }

    private void showEmpty(String message) {
        recyclerAlerts.setVisibility(View.GONE);
        tvEmptyAlerts.setVisibility(View.VISIBLE);
        tvEmptyAlerts.setText(message);
    }

    private void onAlertClicked(AlertResponse alert) {
        if (alert.isRead()) return;   // already read, nothing to do

        mainApiService.markAlertAsRead(alert.getAlertId()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    fetchAlerts();   // refresh so the read/unread dot updates
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("AlertsActivity", "markAlertAsRead failed", t);
                // FIX: was completely silent before -- small, non-blocking
                // indicator now, matching the requested "small in-line
                // indicator" for this minor, recoverable action.
                showRetryDialog("Couldn't mark alert as read", () -> onAlertClicked(alert));
            }
        });
    }

    /** Same reusable in-line banner pattern as Home/Vehicles/TripHistory. */
    private void showRetryDialog(String message, Runnable retryAction) {
        if (errorBanner == null) return;
        tvErrorBannerMessage.setText(message);
        tvErrorBannerRetry.setOnClickListener(v -> {
            hideErrorBanner();
            retryAction.run();
        });
        errorBanner.setVisibility(View.VISIBLE);
    }

    private void hideErrorBanner() {
        if (errorBanner != null) errorBanner.setVisibility(View.GONE);
    }

    /** Same real-time connectivity monitoring pattern as Home/Vehicles. */
    private void registerNetworkMonitor() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> showRetryDialog("No internet connection", AlertsActivity.this::fetchAlerts));
            }

            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    hideErrorBanner();
                    fetchAlerts();
                });
            }
        };
        cm.registerDefaultNetworkCallback(networkCallback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        }
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
            Log.e("AlertsActivity", "parseList error", e);
        }
        return list;
    }
}