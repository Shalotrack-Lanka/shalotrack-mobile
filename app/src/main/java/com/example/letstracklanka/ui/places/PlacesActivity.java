package com.example.letstracklanka.ui.places;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateSavedPlaceRequest;
import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.model.SavedPlaceResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.ui.main.AddressResolver;
import com.example.letstracklanka.ui.vehicles.VehicleListActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Real Places screen -- replaces the previous stub (bottom_sheet_places.xml,
 * just a close button, nothing else). Primary save flow is "save the
 * vehicle's current live location", matching this app's actual purpose
 * (vehicle tracking) rather than a general-purpose map-picker, which is a
 * separate, larger sub-feature left for later.
 *
 * Reads the currently-selected vehicle independently via
 * VehicleListActivity's own SharedPreferences constants, since this screen
 * can be opened from the drawer on Tags/Circles too, neither of which
 * track any vehicle location themselves.
 */
public class PlacesActivity extends AppCompatActivity {

    private ApiService mainApiService;
    private ShaloTrackApi trackingApi;
    private AddressResolver addressResolver;

    private View errorBanner;
    private TextView tvErrorBannerMessage;
    private RecyclerView rvPlaces;
    private ProgressBar progressPlaces;
    private View layoutEmptyState;
    private SavedPlaceAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_places);

        mainApiService = ApiClient.getClient().create(ApiService.class);
        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);
        addressResolver = new AddressResolver(this);

        initViews();
        fetchPlaces();
    }

    private void initViews() {
        View btnBack = findViewById(R.id.btnBackPlaces);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        rvPlaces = findViewById(R.id.rvPlaces);
        progressPlaces = findViewById(R.id.progressPlaces);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);

        rvPlaces.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SavedPlaceAdapter(addressResolver, this::confirmDeletePlace);
        rvPlaces.setAdapter(adapter);

        FloatingActionButton fabAddPlace = findViewById(R.id.fabAddPlace);
        if (fabAddPlace != null) fabAddPlace.setOnClickListener(v -> startSaveCurrentLocationFlow());
    }

    private void fetchPlaces() {
        hideError();
        if (progressPlaces != null) progressPlaces.setVisibility(View.VISIBLE);

        mainApiService.getMyPlaces().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (progressPlaces != null) progressPlaces.setVisibility(View.GONE);
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        showError("Couldn't load your places (code " + response.code() + ")");
                        return;
                    }
                    List<SavedPlaceResponse> places = parseList(body.string());
                    adapter.updatePlaces(places);
                    if (layoutEmptyState != null) {
                        layoutEmptyState.setVisibility(places.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                } catch (Exception e) {
                    Log.e("PlacesActivity", "fetchPlaces parse error", e);
                    showError("Something went wrong loading your places.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (progressPlaces != null) progressPlaces.setVisibility(View.GONE);
                Log.e("PlacesActivity", "fetchPlaces network error", t);
                showError("Network error \u2014 check your connection.");
            }
        });
    }

    // Step 1: resolve the currently-selected vehicle, independent of
    // whichever screen this Activity was opened from.
    private void startSaveCurrentLocationFlow() {
        String vehicleId = getSharedPreferences(VehicleListActivity.VEHICLE_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(VehicleListActivity.SELECTED_VEHICLE_ID_KEY, null);

        if (vehicleId == null) {
            Toast.makeText(this, "No vehicle selected \u2014 pick one from Vehicles first.", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Getting current location...", Toast.LENGTH_SHORT).show();

        // Step 2: fetch that vehicle's real current position, same
        // endpoint/parsing pattern already proven in VehiclesActivity.
        trackingApi.getVehicleLocation(vehicleId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        Toast.makeText(PlacesActivity.this,
                                "Couldn't get the vehicle's current location. Try again shortly.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    LocationResponse loc = extractObject(body.string(), LocationResponse.class);
                    if (loc == null) {
                        Toast.makeText(PlacesActivity.this,
                                "No location data available yet for this vehicle.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    showNamePlaceDialog(loc.getLatitude(), loc.getLongitude());
                } catch (Exception e) {
                    Log.e("PlacesActivity", "startSaveCurrentLocationFlow parse error", e);
                    Toast.makeText(PlacesActivity.this, "Something went wrong. Try again.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("PlacesActivity", "startSaveCurrentLocationFlow network error", t);
                Toast.makeText(PlacesActivity.this, "Network error \u2014 check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Step 3: name it, then save.
    private void showNamePlaceDialog(double latitude, double longitude) {
        EditText input = new EditText(this);
        input.setHint("e.g. Home, Office");
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Save this location as")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Give this place a name.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    savePlace(name, latitude, longitude);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void savePlace(String name, double latitude, double longitude) {
        CreateSavedPlaceRequest request = new CreateSavedPlaceRequest(name, latitude, longitude);
        mainApiService.addPlace(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(PlacesActivity.this, "Place saved.", Toast.LENGTH_SHORT).show();
                    fetchPlaces();
                } else {
                    Log.w("PlacesActivity", "savePlace failed, code " + response.code());
                    Toast.makeText(PlacesActivity.this, "Couldn't save this place. Try again.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("PlacesActivity", "savePlace network error", t);
                Toast.makeText(PlacesActivity.this, "Network error \u2014 check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmDeletePlace(SavedPlaceResponse place) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + place.getName() + "?")
                .setMessage("This can't be undone.")
                .setPositiveButton("Remove", (dialog, which) -> deletePlace(place))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deletePlace(SavedPlaceResponse place) {
        mainApiService.deletePlace(place.getPlaceId()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    fetchPlaces();
                } else {
                    Log.w("PlacesActivity", "deletePlace failed, code " + response.code());
                    Toast.makeText(PlacesActivity.this, "Couldn't remove this place. Try again.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("PlacesActivity", "deletePlace network error", t);
                Toast.makeText(PlacesActivity.this, "Network error \u2014 check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showError(String message) {
        if (errorBanner == null) return;
        errorBanner.setVisibility(View.VISIBLE);
        if (tvErrorBannerMessage != null) tvErrorBannerMessage.setText(message);
    }

    private void hideError() {
        if (errorBanner != null) errorBanner.setVisibility(View.GONE);
    }

    private List<SavedPlaceResponse> parseList(String json) {
        List<SavedPlaceResponse> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                Type listType = new TypeToken<List<SavedPlaceResponse>>() {}.getType();
                List<SavedPlaceResponse> parsed = gson.fromJson(root.getAsJsonArray("data"), listType);
                if (parsed != null) list = parsed;
            }
        } catch (Exception e) {
            Log.e("PlacesActivity", "parseList error", e);
        }
        return list;
    }

    private <T> T extractObject(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), clazz);
            }
        } catch (Exception e) {
            Log.e("PlacesActivity", "extractObject error", e);
        }
        return null;
    }
}