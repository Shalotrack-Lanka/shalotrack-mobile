package com.example.letstracklanka.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateGeofenceRequest;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.GeofenceResponse;
import com.example.letstracklanka.data.model.UpdateGeofenceRequest;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Real geofencing UI: tap the map to place a new circle's center, adjust
 * its radius with a live preview, name it, choose which vehicle(s) it
 * applies to, and save. Lists all existing geofences below, each
 * independently toggleable, tap to delete.
 */
public class CirclesActivity extends AppCompatActivity {

    private static final int DEFAULT_RADIUS_METERS = 200;
    private static final int MIN_RADIUS_METERS = 50;
    private static final int MAX_RADIUS_METERS = 5000;

    private DrawerLayout drawerLayout;
    private ApiService mainApiService;
    private GoogleMap googleMap;

    private GeofenceAdapter geofenceAdapter;
    private final Map<String, Circle> drawnCircles = new HashMap<>();

    private String currentCustomerId;
    private final List<VehicleResponse> myVehicles = new ArrayList<>();

    private boolean isPlacingCenter = false;
    private Circle previewCircle;

    private View tvPlacementHint;
    private RecyclerView recyclerGeofences;
    private View tvNoCirclesBottomSheet;
    private View viewDarkOverlay;
    private View layoutEmptyStateOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_circles);

        mainApiService = ApiClient.getClient().create(ApiService.class);

        drawerLayout = findViewById(R.id.drawerLayout);
        DrawerMenuHelper.wireDrawer(this, drawerLayout,
                findViewById(R.id.tvDrawerName), findViewById(R.id.tvDrawerPhone), findViewById(R.id.tvDrawerEmail));

        tvPlacementHint = findViewById(R.id.tvPlacementHint);
        tvNoCirclesBottomSheet = findViewById(R.id.tvNoCirclesBottomSheet);
        viewDarkOverlay = findViewById(R.id.viewDarkOverlay);
        layoutEmptyStateOverlay = findViewById(R.id.layoutEmptyStateOverlay);

        recyclerGeofences = findViewById(R.id.recyclerGeofences);
        recyclerGeofences.setLayoutManager(new LinearLayoutManager(this));
        geofenceAdapter = new GeofenceAdapter(new GeofenceAdapter.OnGeofenceActionListener() {
            @Override
            public void onToggleActive(GeofenceResponse geofence, boolean newIsActive) {
                updateGeofenceActiveState(geofence, newIsActive);
            }

            @Override
            public void onRowClick(GeofenceResponse geofence) {
                confirmDeleteGeofence(geofence);
            }
        });
        recyclerGeofences.setAdapter(geofenceAdapter);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapCircles);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this::onMapReady);
        }

        loadMyVehicles();

        // --- Setup Buttons and Clicks ---

        MaterialButton btnCreateCircle = findViewById(R.id.btnCreateCircle);
        MaterialButton btnWatchHelp = findViewById(R.id.btnWatchHelp);
        FloatingActionButton fabAddCircle = findViewById(R.id.fabAddCircle);

        btnCreateCircle.setOnClickListener(v -> startPlacingCenter());
        fabAddCircle.setOnClickListener(v -> startPlacingCenter());
        // Honest placeholder: a real help-video feature isn't something
        // buildable here, left as-is rather than faked with fabricated
        // content.
        btnWatchHelp.setOnClickListener(v -> Toast.makeText(this, "Help videos coming soon.", Toast.LENGTH_SHORT).show());

        // --- Setup Bottom Navigation Bar (unchanged from before) ---

        View navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                Intent intent = new Intent(CirclesActivity.this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        View navVehicles = findViewById(R.id.nav_vehicles);
        if (navVehicles != null) {
            navVehicles.setOnClickListener(v -> {
                Intent intent = new Intent(CirclesActivity.this, com.example.letstracklanka.ui.vehicles.VehiclesActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        View navTags = findViewById(R.id.nav_tags);
        if (navTags != null) {
            navTags.setOnClickListener(v -> {
                Intent intent = new Intent(CirclesActivity.this, TagsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        View navAlerts = findViewById(R.id.nav_alerts);
        if (navAlerts != null) {
            navAlerts.setOnClickListener(v -> {
                Intent intent = new Intent(CirclesActivity.this, AlertsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        View navMenu = findViewById(R.id.nav_menu);
        if (navMenu != null) {
            navMenu.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
            });
        }
    }

    // Fetched once and cached for the activity's lifetime, since this
    // only populates a dropdown in the create sheet -- no need to
    // re-fetch every time that sheet opens. Same profile-then-vehicles
    // chain already proven in HomeActivity.loadUserData().
    private void loadMyVehicles() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        mainApiService.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        CustomerResponse customer = extractObject(body.string(), CustomerResponse.class);
                        if (customer != null && customer.getCustomerId() != null) {
                            currentCustomerId = customer.getCustomerId();
                            fetchMyVehiclesForPicker();
                        }
                    } else {
                        Log.w("CirclesActivity", "getMyProfile failed, code " + response.code());
                    }
                } catch (Exception e) {
                    Log.e("CirclesActivity", "loadMyVehicles parse error", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("CirclesActivity", "loadMyVehicles network error", t);
            }
        });
    }

    private void fetchMyVehiclesForPicker() {
        mainApiService.getVehiclesByCustomer(currentCustomerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<VehicleResponse> list = extractList(body.string(), VehicleResponse.class);
                        myVehicles.clear();
                        if (list != null) myVehicles.addAll(list);
                    } else {
                        Log.w("CirclesActivity", "fetchMyVehiclesForPicker failed, code " + response.code());
                    }
                } catch (Exception e) {
                    Log.e("CirclesActivity", "fetchMyVehiclesForPicker parse error", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("CirclesActivity", "fetchMyVehiclesForPicker network error", t);
            }
        });
    }

    private void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(6.9271, 79.8612), 10f)); // Colombo default

        map.setOnMapClickListener(latLng -> {
            if (isPlacingCenter) {
                isPlacingCenter = false;
                tvPlacementHint.setVisibility(View.GONE);
                showCreateGeofenceSheet(latLng);
            }
        });

        fetchGeofences();
    }

    private void startPlacingCenter() {
        isPlacingCenter = true;
        tvPlacementHint.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Tap the map to place your geofence.", Toast.LENGTH_SHORT).show();
    }

    private void fetchGeofences() {
        mainApiService.getMyGeofences().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                List<GeofenceResponse> geofences = new ArrayList<>();
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<GeofenceResponse> parsed = extractList(body.string(), GeofenceResponse.class);
                        if (parsed != null) geofences = parsed;
                    } else {
                        Log.w("CirclesActivity", "fetchGeofences failed, code " + response.code());
                    }
                } catch (Exception e) {
                    Log.e("CirclesActivity", "fetchGeofences parse error", e);
                }
                displayGeofences(geofences);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("CirclesActivity", "fetchGeofences network error", t);
                Toast.makeText(CirclesActivity.this, "Network error \u2014 check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayGeofences(List<GeofenceResponse> geofences) {
        boolean hasGeofences = !geofences.isEmpty();
        recyclerGeofences.setVisibility(hasGeofences ? View.VISIBLE : View.GONE);
        tvNoCirclesBottomSheet.setVisibility(hasGeofences ? View.GONE : View.VISIBLE);

        // FIX: real, severe bug found via screenshot -- these two views
        // had no ID at all before, so nothing ever hid them. The dark
        // overlay + "You currently have no Circles" messaging would have
        // permanently covered the map, even after real geofences
        // existed, making the whole feature unusable the moment anyone
        // actually created one.
        if (viewDarkOverlay != null) viewDarkOverlay.setVisibility(hasGeofences ? View.GONE : View.VISIBLE);
        if (layoutEmptyStateOverlay != null) layoutEmptyStateOverlay.setVisibility(hasGeofences ? View.GONE : View.VISIBLE);

        geofenceAdapter.updateGeofences(geofences);
        redrawGeofenceCircles(geofences);
    }

    private void redrawGeofenceCircles(List<GeofenceResponse> geofences) {
        for (Circle circle : drawnCircles.values()) {
            circle.remove();
        }
        drawnCircles.clear();

        if (googleMap == null) return;

        for (GeofenceResponse geofence : geofences) {
            int color = geofence.getVehicleId() != null
                    ? ContextCompat.getColor(this, R.color.status_danger)
                    : ContextCompat.getColor(this, R.color.brand_accent);

            Circle circle = googleMap.addCircle(new CircleOptions()
                    .center(new LatLng(geofence.getLatitude(), geofence.getLongitude()))
                    .radius(geofence.getRadiusMeters())
                    .strokeColor(color)
                    .strokeWidth(3f)
                    .fillColor(withAlpha(color, 40)));

            drawnCircles.put(geofence.getGeofenceId(), circle);
        }
    }

    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    // Live radius preview: created when the sheet opens, updated as the
    // slider moves, removed unconditionally when the sheet closes either
    // way -- a successful save redraws the real, saved circle from
    // scratch via fetchGeofences() rather than trying to "convert" this
    // preview into the real one.
    private void showCreateGeofenceSheet(LatLng center) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_create_geofence, null);
        dialog.setContentView(view);

        TextInputEditText etName = view.findViewById(R.id.etGeofenceName);
        AutoCompleteTextView spinnerVehicle = view.findViewById(R.id.spinnerGeofenceVehicle);
        TextView tvRadiusValue = view.findViewById(R.id.tvRadiusValue);
        Slider sliderRadius = view.findViewById(R.id.sliderRadius);
        SwitchCompat switchEnter = view.findViewById(R.id.switchAlertOnEnter);
        SwitchCompat switchExit = view.findViewById(R.id.switchAlertOnExit);
        View btnCancel = view.findViewById(R.id.btnCancelGeofence);
        View btnSave = view.findViewById(R.id.btnSaveGeofence);

        // NEW -- vehicle scope picker. Index 0 is always "All Vehicles"
        // (VehicleId null); every entry after that lines up 1:1 with
        // myVehicles, resolved back to a real VehicleId by index at save
        // time rather than by re-parsing the displayed label text.
        List<String> vehicleLabels = new ArrayList<>();
        vehicleLabels.add("All Vehicles");
        for (VehicleResponse vehicle : myVehicles) {
            String label = (safe(vehicle.getMake()) + " " + safe(vehicle.getModel())).trim();
            if (vehicle.getVehicleNumber() != null) label += " (" + vehicle.getVehicleNumber() + ")";
            vehicleLabels.add(label);
        }
        ArrayAdapter<String> vehicleAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, vehicleLabels);
        spinnerVehicle.setAdapter(vehicleAdapter);
        spinnerVehicle.setText(vehicleLabels.get(0), false);

        sliderRadius.setValue(DEFAULT_RADIUS_METERS);
        tvRadiusValue.setText(DEFAULT_RADIUS_METERS + " m");

        if (googleMap != null) {
            previewCircle = googleMap.addCircle(new CircleOptions()
                    .center(center)
                    .radius(DEFAULT_RADIUS_METERS)
                    .strokeColor(ContextCompat.getColor(this, R.color.brand_accent))
                    .strokeWidth(3f)
                    .fillColor(withAlpha(ContextCompat.getColor(this, R.color.brand_accent), 40)));
        }

        sliderRadius.addOnChangeListener((slider, value, fromUser) -> {
            int radius = (int) value;
            tvRadiusValue.setText(radius + " m");
            if (previewCircle != null) previewCircle.setRadius(radius);
        });

        dialog.setOnDismissListener(d -> removePreviewCircle());

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter a name for this geofence.", Toast.LENGTH_SHORT).show();
                return;
            }

            int radiusMeters = Math.max(MIN_RADIUS_METERS, Math.min(MAX_RADIUS_METERS, (int) sliderRadius.getValue()));

            // Resolve the selected label back to an index, then to a real
            // VehicleId (or null for "All Vehicles"). Falls back to "All
            // Vehicles" (index 0) if the text somehow doesn't match any
            // known label, rather than silently sending a stale/wrong ID.
            int selectedIndex = vehicleLabels.indexOf(spinnerVehicle.getText().toString());
            String scopedVehicleId = (selectedIndex > 0) ? myVehicles.get(selectedIndex - 1).getVehicleId() : null;

            CreateGeofenceRequest request = new CreateGeofenceRequest(
                    scopedVehicleId,
                    name, center.latitude, center.longitude, radiusMeters,
                    switchEnter.isChecked(), switchExit.isChecked());

            mainApiService.addGeofence(request).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(CirclesActivity.this, "Geofence created.", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        fetchGeofences();
                    } else {
                        Log.w("CirclesActivity", "addGeofence failed, code " + response.code());
                        Toast.makeText(CirclesActivity.this, "Couldn't create geofence. Try again.", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    Log.e("CirclesActivity", "addGeofence network error", t);
                    Toast.makeText(CirclesActivity.this, "Network error \u2014 check your connection.", Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private String safe(String s) {
        return s != null ? s : "";
    }

    private void removePreviewCircle() {
        if (previewCircle != null) {
            previewCircle.remove();
            previewCircle = null;
        }
    }

    private void updateGeofenceActiveState(GeofenceResponse geofence, boolean newIsActive) {
        UpdateGeofenceRequest request = new UpdateGeofenceRequest(
                geofence.getVehicleId(), geofence.getName(), geofence.getLatitude(), geofence.getLongitude(),
                geofence.getRadiusMeters(), geofence.isAlertOnEnter(), geofence.isAlertOnExit(), newIsActive);

        mainApiService.updateGeofence(geofence.getGeofenceId(), request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    Log.w("CirclesActivity", "updateGeofence failed, code " + response.code());
                    Toast.makeText(CirclesActivity.this, "Couldn't update geofence. Try again.", Toast.LENGTH_SHORT).show();
                    fetchGeofences(); // revert the switch to the real, server-confirmed state
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("CirclesActivity", "updateGeofence network error", t);
                Toast.makeText(CirclesActivity.this, "Network error \u2014 check your connection.", Toast.LENGTH_SHORT).show();
                fetchGeofences();
            }
        });
    }

    private void confirmDeleteGeofence(GeofenceResponse geofence) {
        if (!geofence.isOwner()) {
            Toast.makeText(this, "Only the owner can remove this geofence.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Remove geofence?")
                .setMessage("Remove \"" + geofence.getName() + "\"? This can't be undone.")
                .setPositiveButton("Remove", (dialog, which) -> deleteGeofence(geofence))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteGeofence(GeofenceResponse geofence) {
        mainApiService.deleteGeofence(geofence.getGeofenceId()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(CirclesActivity.this, "Geofence removed.", Toast.LENGTH_SHORT).show();
                    fetchGeofences();
                } else {
                    Log.w("CirclesActivity", "deleteGeofence failed, code " + response.code());
                    Toast.makeText(CirclesActivity.this, "Couldn't remove geofence. Try again.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("CirclesActivity", "deleteGeofence network error", t);
                Toast.makeText(CirclesActivity.this, "Network error \u2014 check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private <T> T extractObject(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), clazz);
            }
            return gson.fromJson(json, clazz);
        } catch (Exception e) {
            Log.e("CirclesActivity", "extractObject error", e);
            return null;
        }
    }

    private <T> List<T> extractList(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                Type listType = TypeToken.getParameterized(List.class, clazz).getType();
                return gson.fromJson(root.getAsJsonArray("data"), listType);
            }
        } catch (Exception e) {
            Log.e("CirclesActivity", "extractList error", e);
        }
        return null;
    }
}