package com.example.letstracklanka.ui.vehicles;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.main.HomeActivity;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * "My Vehicles" -- shows every vehicle the customer owns with live status,
 * tap-to-select (switches which vehicle Home/Vehicles actively track, via a
 * shared preference), and a Remove action for selling/retiring a vehicle.
 */
public class VehicleListActivity extends AppCompatActivity {

    // Shared with HomeActivity/VehiclesActivity -- same preference file/key
    // used for the map-type sharing earlier, reused here for the analogous
    // "which vehicle is currently selected" concept.
    public static final String VEHICLE_PREFS_NAME = "ShaloTrackVehiclePrefs";
    public static final String SELECTED_VEHICLE_ID_KEY = "selected_vehicle_id";

    private ApiService mainApiService;
    private String currentCustomerId;

    private RecyclerView recyclerVehicleList;
    private VehicleListAdapter adapter;
    private ProgressBar progressVehicleList;
    private TextView tvEmptyVehicleList;
    private View errorBanner;
    private TextView tvErrorBannerMessage, tvErrorBannerRetry;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicle_list);

        mainApiService = ApiClient.getClient().create(ApiService.class);

        initViews();
        registerNetworkMonitor();
        loadUserData();
    }

    private void initViews() {
        recyclerVehicleList = findViewById(R.id.recyclerVehicleList);
        progressVehicleList = findViewById(R.id.progressVehicleList);
        tvEmptyVehicleList = findViewById(R.id.tvEmptyVehicleList);
        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        tvErrorBannerRetry = findViewById(R.id.tvErrorBannerRetry);

        recyclerVehicleList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VehicleListAdapter(new ArrayList<>(), this::onVehicleSelected, this::confirmRemoveVehicle);
        recyclerVehicleList.setAdapter(adapter);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    // ---- Selecting a vehicle (switches Home/Vehicles to it) ----

    private void onVehicleSelected(DashboardVehicle vehicle) {
        getSharedPreferences(VEHICLE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(SELECTED_VEHICLE_ID_KEY, vehicle.getVehicleId())
                .apply();

        Intent intent = new Intent(VehicleListActivity.this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    // ---- Removing a vehicle ----

    private void confirmRemoveVehicle(DashboardVehicle vehicle) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + vehicle.getVehicleNumber() + "?")
                .setMessage("This removes the vehicle from your account and frees its GPS device so it can be linked to a new vehicle. Trip history and alerts are kept. This can't be undone from the app.")
                .setPositiveButton("Remove", (dialog, which) -> removeVehicle(vehicle))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeVehicle(DashboardVehicle vehicle) {
        mainApiService.deleteVehicle(vehicle.getVehicleId()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    // If the removed vehicle was the currently-selected one,
                    // clear that selection so Home falls back to a sensible
                    // default instead of pointing at a vehicle that's gone.
                    SharedPreferences prefs = getSharedPreferences(VEHICLE_PREFS_NAME, Context.MODE_PRIVATE);
                    if (vehicle.getVehicleId().equals(prefs.getString(SELECTED_VEHICLE_ID_KEY, null))) {
                        prefs.edit().remove(SELECTED_VEHICLE_ID_KEY).apply();
                    }
                    fetchVehicles();   // refresh the list so it disappears
                } else {
                    showRetryDialog("Couldn't remove vehicle (code " + response.code() + ")", () -> removeVehicle(vehicle));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("VehicleListActivity", "removeVehicle network error", t);
                showRetryDialog("Network error — couldn't remove vehicle", () -> removeVehicle(vehicle));
            }
        });
    }

    // ---- Data loading ----

    private void loadUserData() {
        mainApiService.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        CustomerResponse customer = extractObject(body.string(), CustomerResponse.class);
                        if (customer != null && customer.getCustomerId() != null) {
                            currentCustomerId = customer.getCustomerId();
                            fetchVehicles();
                        } else {
                            showRetryDialog("Couldn't load your profile", VehicleListActivity.this::loadUserData);
                        }
                    } else {
                        showRetryDialog("Couldn't load your profile (code " + response.code() + ")", VehicleListActivity.this::loadUserData);
                    }
                } catch (Exception e) {
                    Log.e("VehicleListActivity", "loadUserData error", e);
                    showRetryDialog("Something went wrong loading your profile", VehicleListActivity.this::loadUserData);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                showRetryDialog("Network error — couldn't load your profile", VehicleListActivity.this::loadUserData);
            }
        });
    }

    private void fetchVehicles() {
        if (currentCustomerId == null) return;

        progressVehicleList.setVisibility(View.VISIBLE);
        recyclerVehicleList.setVisibility(View.GONE);
        tvEmptyVehicleList.setVisibility(View.GONE);

        mainApiService.getCustomerDashboard(currentCustomerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                progressVehicleList.setVisibility(View.GONE);
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        showRetryDialog("Couldn't load your vehicles (code " + response.code() + ")", VehicleListActivity.this::fetchVehicles);
                        return;
                    }
                    hideErrorBanner();

                    List<DashboardVehicle> vehicles = extractVehiclesList(body.string());

                    if (vehicles.isEmpty()) {
                        recyclerVehicleList.setVisibility(View.GONE);
                        tvEmptyVehicleList.setVisibility(View.VISIBLE);
                        tvEmptyVehicleList.setText("No vehicles yet");
                    } else {
                        recyclerVehicleList.setVisibility(View.VISIBLE);
                        tvEmptyVehicleList.setVisibility(View.GONE);
                        adapter.updateVehicles(vehicles);
                    }
                } catch (Exception e) {
                    Log.e("VehicleListActivity", "fetchVehicles parse error", e);
                    showRetryDialog("Something went wrong loading your vehicles", VehicleListActivity.this::fetchVehicles);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                progressVehicleList.setVisibility(View.GONE);
                showRetryDialog("Network error — couldn't load your vehicles", VehicleListActivity.this::fetchVehicles);
            }
        });
    }

    // ---- In-line error banner (same pattern as the rest of the app) ----

    private void showRetryDialog(String message, Runnable retryAction) {
        if (errorBanner == null) return;
        tvErrorBannerMessage.setText(message);
        if (retryAction != null) {
            tvErrorBannerRetry.setVisibility(View.VISIBLE);
            tvErrorBannerRetry.setOnClickListener(v -> {
                hideErrorBanner();
                retryAction.run();
            });
        } else {
            tvErrorBannerRetry.setVisibility(View.GONE);
        }
        errorBanner.setVisibility(View.VISIBLE);
    }

    private void hideErrorBanner() {
        if (errorBanner != null) errorBanner.setVisibility(View.GONE);
    }

    private void registerNetworkMonitor() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> showRetryDialog("No internet connection", VehicleListActivity.this::loadUserData));
            }

            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    hideErrorBanner();
                    loadUserData();
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

    // ---- Helpers ----

    private <T> T extractObject(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), clazz);
            }
            return null;
        } catch (Exception e) {
            Log.e("VehicleListActivity", "extractObject error", e);
            return null;
        }
    }

    private List<DashboardVehicle> extractVehiclesList(String json) {
        List<DashboardVehicle> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null || !root.has("data") || root.get("data").isJsonNull()) return list;
            JsonObject data = root.getAsJsonObject("data");
            if (!data.has("vehicles") || !data.get("vehicles").isJsonArray()) return list;
            list = gson.fromJson(data.getAsJsonArray("vehicles"),
                    TypeToken.getParameterized(List.class, DashboardVehicle.class).getType());
        } catch (Exception e) {
            Log.e("VehicleListActivity", "extractVehiclesList error", e);
        }
        return list;
    }
}