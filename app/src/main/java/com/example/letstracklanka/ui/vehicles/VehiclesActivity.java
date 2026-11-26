package com.example.letstracklanka.ui.vehicles;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.ui.common.BottomNav;
import com.example.letstracklanka.ui.common.EdgeToEdge;
import com.example.letstracklanka.ui.common.EmptyState;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.NetworkWatcher;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.ui.common.TimeText;
import com.example.letstracklanka.ui.common.VehiclePickerSheet;
import com.example.letstracklanka.ui.common.VehicleStatus;
import com.example.letstracklanka.ui.devices.DevicesActivity;
import com.example.letstracklanka.ui.geofences.GeofencesActivity;
import com.example.letstracklanka.ui.history.TripHistoryActivity;
import com.example.letstracklanka.ui.main.AddressResolver;
import com.example.letstracklanka.ui.main.AlertsActivity;
import com.example.letstracklanka.ui.more.MoreRows;
import com.example.letstracklanka.ui.more.ProfileText;
import com.example.letstracklanka.ui.vehicles.page.AlertSettingsSheet;
import com.example.letstracklanka.ui.vehicles.page.HealthCard;
import com.example.letstracklanka.ui.vehicles.page.LiveLinksSheet;
import com.example.letstracklanka.ui.vehicles.page.RemindersSheet;
import com.example.letstracklanka.utils.ApiErrors;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.SecureScreen;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Vehicle page (activity_vehicles), opened from the Vehicles tab or the Map sheet for one vehicle.
 *
 * Owner: status, quick actions, tracker health, live location links, reminders, alert settings, geofences,
 * sharing, statistics, details with Edit, tracker link/unlink, remove vehicle.
 * Shared or demo vehicle: status, quick actions, health, statistics, geofences (read-only) and details;
 * nothing that changes the vehicle (the server refuses those anyway).
 *
 * No map on this page: "Map" makes the Map tab follow this vehicle (live position and trail live there).
 * Data: the dashboard row (status, position), the vehicle record (details), the tracker status (health).
 * Refreshed on open, on pull, and every 30 s while on screen; nothing runs in the background.
 *
 * Replaces the old Letstrack-style page (drawer, second vehicle list, 1-second polling, fake tiles).
 */
public class VehiclesActivity extends AppCompatActivity {

    /** The vehicle to open. Saved as the selected vehicle so the Map tab follows it too. */
    public static final String EXTRA_VEHICLE_ID = "extra_vehicle_id";

    private static final long REFRESH_MS = 30_000;
    /** Geofence names shown on the row before "+N more". */
    private static final int GEOFENCE_NAMES = 2;

    private ApiService api;
    private ShaloTrackApi trackingApi;
    private String vehicleId;

    @Nullable private DashboardVehicle vehicle;
    @Nullable private VehicleResponse details;
    private boolean owner;
    private boolean loading;
    private long lastLoadedAt;

    private SwipeRefreshLayout swipe;
    private View progress, body;
    private InlineBanner banner;
    private EmptyState empty;
    private TextView tvPlate, tvStatus, tvMakeModel, tvWhere, tvUpdated, tvSharedNote;
    private ViewGroup manageCard, detailsCard, dangerCard;
    private View manageLabel;
    private MaterialButton btnEdit;
    private HealthCard health;

    @Nullable private View rowLive, rowReminders, rowAlertSettings, rowGeofences, rowShared;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            load(false);
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private ActivityResultLauncher<Intent> editLauncher;
    /** Geofences, sharing: reload the row text when they come back. */
    private ActivityResultLauncher<Intent> rowsLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_vehicles);
        SecureScreen.guardTouches(this);
        api = ApiClient.getClient().create(ApiService.class);
        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);
        editLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> load(false));
        rowsLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> loadRowSummaries());

        String requested = getIntent().getStringExtra(EXTRA_VEHICLE_ID);
        vehicleId = requested != null && !requested.trim().isEmpty()
                ? requested.trim() : VehicleListActivity.selectedVehicleId(this);
        if (vehicleId != null) VehicleListActivity.rememberSelected(this, vehicleId);

        bindViews();
        NetworkWatcher.watch(this, new NetworkWatcher.Listener() {
            @Override
            public void onLost() {
                banner.showOffline(R.string.st_banner_offline_list, null);
            }

            @Override
            public void onRestored() {
                banner.hide();
                load(false);
            }
        });

        if (vehicleId == null) {
            showMissing();
            return;
        }
        load(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (lastLoadedAt != 0 && SystemClock.elapsedRealtime() - lastLoadedAt > REFRESH_MS) load(false);
        handler.postDelayed(refresh, REFRESH_MS);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(refresh);
    }

    private void bindViews() {
        EdgeToEdge.padTop(findViewById(R.id.vehiclePageTopBar));
        EdgeToEdge.padBottom(findViewById(R.id.vehiclePageContent));
        swipe = findViewById(R.id.swipeRefreshVehiclePage);
        progress = findViewById(R.id.progressVehiclePage);
        body = findViewById(R.id.vehiclePageBody);
        banner = InlineBanner.in(this);
        empty = new EmptyState(findViewById(R.id.emptyState));
        tvPlate = findViewById(R.id.tvVehiclePlate);
        tvStatus = findViewById(R.id.tvVehicleStatus);
        tvMakeModel = findViewById(R.id.tvVehicleMakeModel);
        tvWhere = findViewById(R.id.tvVehicleWhere);
        tvUpdated = findViewById(R.id.tvVehicleUpdated);
        tvSharedNote = findViewById(R.id.tvVehicleSharedNote);
        manageCard = findViewById(R.id.manageCard);
        manageLabel = findViewById(R.id.tvManageLabel);
        detailsCard = findViewById(R.id.detailsCard);
        dangerCard = findViewById(R.id.dangerCard);
        btnEdit = findViewById(R.id.btnEditVehicle);
        health = new HealthCard(findViewById(R.id.healthCard));

        findViewById(R.id.btnBackVehicle).setOnClickListener(v -> finish());
        swipe.setColorSchemeResources(R.color.st_navy);
        swipe.setOnRefreshListener(() -> load(false));

        findViewById(R.id.btnActMap).setOnClickListener(v -> showOnMap());
        findViewById(R.id.btnActTrips).setOnClickListener(v -> openTrips());
        findViewById(R.id.btnActAlerts).setOnClickListener(v -> openAlerts());
        findViewById(R.id.btnActNavigate).setOnClickListener(v -> navigate());
        btnEdit.setOnClickListener(v -> openEdit());
    }

    // ---- Data ----

    private void load(boolean firstTime) {
        if (loading || vehicleId == null) return;
        loading = true;
        if (firstTime) progress.setVisibility(View.VISIBLE);
        VehiclePickerSheet.load(this, new VehiclePickerSheet.LoadCallback() {
            @Override
            public void onLoaded(List<DashboardVehicle> vehicles) {
                loading = false;
                if (isDestroyed()) return;
                swipe.setRefreshing(false);
                progress.setVisibility(View.GONE);
                DashboardVehicle found = null;
                for (DashboardVehicle v : vehicles) if (vehicleId.equalsIgnoreCase(v.getVehicleId())) found = v;
                if (found == null) {
                    showMissing();
                    return;
                }
                boolean firstBind = vehicle == null;
                vehicle = found;
                owner = !found.isShared() && !found.isDemo();
                lastLoadedAt = SystemClock.elapsedRealtime();
                banner.hide();
                renderHeader();
                if (firstBind) buildSections();
                loadDetails();
                loadHealth();
            }

            @Override
            public void onFailed(int httpCode) {
                loading = false;
                if (isDestroyed()) return;
                swipe.setRefreshing(false);
                progress.setVisibility(View.GONE);
                Runnable retry = () -> {
                    banner.hide();
                    load(vehicle == null);
                };
                if (vehicle == null) {
                    empty.show(getString(R.string.st_vehicle_page_load_failed_title),
                            getString(httpCode == 0 ? R.string.st_error_network : R.string.st_load_failed_body),
                            getString(R.string.st_action_retry), retry);
                } else {
                    banner.showError(httpCode == 0 ? R.string.st_error_network : R.string.st_vehicle_page_refresh_failed, retry);
                }
            }
        });
    }

    private void showMissing() {
        progress.setVisibility(View.GONE);
        body.setVisibility(View.GONE);
        empty.show(getString(R.string.st_vehicle_page_missing_title), getString(R.string.st_vehicle_page_missing_body),
                getString(R.string.st_vehicles_show_all), () -> {
                    BottomNav.go(this, BottomNav.Tab.VEHICLES);
                    finish();
                });
    }

    private void loadDetails() {
        api.getVehicleById(vehicleId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isDestroyed()) {
                    ApiErrors.close(response);
                    return;
                }
                VehicleResponse loaded = null;
                if (response.isSuccessful()) {
                    try (ResponseBody b = response.body()) {
                        loaded = b != null ? VehicleResponse.from(b.string()) : null;
                    } catch (Exception ignored) {
                        loaded = null;
                    }
                } else {
                    ApiErrors.close(response);
                }
                if (loaded != null) {
                    details = loaded;
                    renderHeader();
                    renderDetails();
                } else if (details == null) {
                    renderDetails();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!isDestroyed() && details == null) renderDetails();
            }
        });
    }

    private void loadHealth() {
        DashboardVehicle v = vehicle;
        if (v == null || !v.hasDevice()) {
            health.showUnavailable(getString(R.string.st_health_no_tracker));
            return;
        }
        trackingApi.getVehicleStatus(vehicleId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isDestroyed()) {
                    ApiErrors.close(response);
                    return;
                }
                JsonObject data = null;
                if (response.isSuccessful()) {
                    try (ResponseBody b = response.body()) {
                        data = b != null ? ApiJson.dataObject(b.string()) : null;
                    } catch (Exception ignored) {
                        data = null;
                    }
                } else {
                    ApiErrors.close(response);
                }
                if (data != null) health.show(VehiclesActivity.this, data);
                else health.showUnavailable(getString(R.string.st_health_unavailable));
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!isDestroyed()) health.showUnavailable(getString(R.string.st_error_network));
            }
        });
    }

    // ---- Rendering ----

    private void renderHeader() {
        DashboardVehicle v = vehicle;
        if (v == null) return;
        empty.hide();
        body.setVisibility(View.VISIBLE);

        tvPlate.setText(Plates.format(this, v.getVehicleNumber()));
        VehicleStatus status = VehicleStatus.of(v);
        status.applyToPill(tvStatus);

        String make = joined(v.getMake(), v.getModel());
        Integer year = details != null ? details.getYear() : null;
        tvMakeModel.setText(year != null && year > 0 ? getString(R.string.st_vehicle_make_year, make, year) : make);
        tvMakeModel.setVisibility(make.isEmpty() ? View.GONE : View.VISIBLE);

        if (!v.hasDevice()) {
            tvWhere.setText(R.string.st_home_no_tracker);
            tvUpdated.setText(R.string.st_home_no_tracker_hint);
        } else if (v.hasPosition()) {
            tvWhere.setText(R.string.st_home_finding_place);
            final String id = v.getVehicleId();
            AddressResolver.resolveCached(this, v.getLatitude(), v.getLongitude(), name -> {
                if (vehicle != null && id.equals(vehicle.getVehicleId())) tvWhere.setText(name);
            });
            String ago = TimeText.ago(this, v.getLastUpdate());
            tvUpdated.setText(getString(R.string.st_vehicle_speed_updated, Math.round(v.getSpeed()), ago));
        } else {
            tvWhere.setText(R.string.st_home_no_position);
            tvUpdated.setText("");
        }

        if (v.isDemo()) {
            tvSharedNote.setText(R.string.st_vehicle_page_demo_note);
            tvSharedNote.setVisibility(View.VISIBLE);
        } else if (v.isShared()) {
            String ownerName = v.getOwnerName() != null && !v.getOwnerName().trim().isEmpty()
                    ? v.getOwnerName().trim() : getString(R.string.st_vehicle_someone);
            tvSharedNote.setText(getString(R.string.st_vehicle_page_shared_note, ownerName));
            tvSharedNote.setVisibility(View.VISIBLE);
        } else {
            tvSharedNote.setVisibility(View.GONE);
        }
        findViewById(R.id.vehicleHeader).setContentDescription(getString(R.string.st_vehicle_row_description,
                Plates.plain(v.getVehicleNumber()), getString(status.label)));
    }

    /** Built once per page: which rows exist depends only on owner vs viewer. */
    private void buildSections() {
        manageCard.removeAllViews();
        dangerCard.removeAllViews();
        DashboardVehicle v = vehicle;
        if (v == null) return;

        if (owner) {
            rowLive = MoreRows.add(manageCard, R.drawable.ic_st_share, getString(R.string.st_live_title),
                    getString(R.string.st_live_row_none), false,
                    x -> LiveLinksSheet.show(this, vehicleId, v.getVehicleNumber(), this::onLiveLinks));
            rowReminders = MoreRows.add(manageCard, R.drawable.ic_st_calendar, getString(R.string.st_reminders_title),
                    getString(R.string.st_reminders_row_none), false,
                    x -> RemindersSheet.show(this, vehicleId, this::onReminders));
            rowAlertSettings = MoreRows.add(manageCard, R.drawable.ic_st_gauge, getString(R.string.st_alert_settings_title),
                    getString(R.string.st_alert_settings_row_default), false,
                    x -> AlertSettingsSheet.show(this, vehicleId, v.getVehicleNumber(), this::onAlertSettings));
        }
        rowGeofences = MoreRows.add(manageCard, R.drawable.ic_st_geofence, getString(R.string.st_more_geofences),
                getString(owner ? R.string.st_vehicle_geofences_detail : R.string.st_vehicle_geofences_detail_viewer), false,
                x -> openGeofences());
        if (owner) {
            rowShared = MoreRows.add(manageCard, R.drawable.ic_st_users, getString(R.string.st_more_sharing),
                    getString(R.string.st_vehicle_sharing_detail), false,
                    x -> {
                        Intent i = new Intent(this, VehicleSharingActivity.class);
                        i.putExtra(VehicleSharingActivity.EXTRA_VEHICLE_ID, vehicleId);
                        rowsLauncher.launch(i);
                    });
        }
        MoreRows.add(manageCard, R.drawable.ic_st_chart, getString(R.string.st_vehicle_statistics),
                getString(R.string.st_vehicle_statistics_detail), false, x -> {
                    Intent i = new Intent(this, ValueActivity.class);
                    i.putExtra(ValueActivity.EXTRA_VEHICLE_ID, vehicleId);
                    startActivity(i);
                });
        ((TextView) manageLabel).setText(owner ? R.string.st_vehicle_section_manage : R.string.st_vehicle_section_more);

        if (owner) {
            MoreRows.add(dangerCard, R.drawable.ic_st_device, getString(R.string.st_vehicle_tracker_row),
                    getString(v.hasDevice() ? R.string.st_vehicle_tracker_linked : R.string.st_vehicle_tracker_none), false,
                    x -> {
                        Intent i = new Intent(this, DevicesActivity.class);
                        i.putExtra(EXTRA_VEHICLE_ID, vehicleId);
                        editLauncher.launch(i);
                    });
            MoreRows.add(dangerCard, R.drawable.ic_st_trash, getString(R.string.st_remove_vehicle_confirm),
                    getString(R.string.st_vehicle_remove_detail), true, x -> confirmRemove());
            dangerCard.setVisibility(View.VISIBLE);
            btnEdit.setVisibility(View.VISIBLE);

            loadRowSummaries();
        } else {
            dangerCard.setVisibility(View.GONE);
            btnEdit.setVisibility(View.GONE);
        }
    }

    private void renderDetails() {
        detailsCard.removeAllViews();
        VehicleResponse d = details;
        DashboardVehicle v = vehicle;
        addDetail(R.string.st_detail_plate, v != null ? Plates.plain(v.getVehicleNumber()) : null);
        addDetail(R.string.st_detail_make, d != null ? d.getMake() : v != null ? v.getMake() : null);
        addDetail(R.string.st_detail_model, d != null ? d.getModel() : v != null ? v.getModel() : null);
        if (d != null) {
            addDetail(R.string.st_detail_year, d.getYear() != null && d.getYear() > 0 ? String.valueOf(d.getYear()) : null);
            addDetail(R.string.st_detail_colour, d.getColor());
            addDetail(R.string.st_detail_type, d.getVehicleType());
            addDetail(R.string.st_detail_fuel, d.getFuelType());
            // Chassis and engine numbers identify the vehicle: show the last 4 only (full values are on Edit).
            addDetail(R.string.st_detail_chassis, tail(d.getChassisNumber()));
            addDetail(R.string.st_detail_engine, tail(d.getEngineNumber()));
            addDetail(R.string.st_detail_tracker, d.hasGpsDevice() ? tail(d.getImei()) : getString(R.string.st_detail_tracker_none));
            addDetail(R.string.st_detail_warranty, TimeText.date(d.getWarrantyExpiryDate()));
        }
        if (detailsCard.getChildCount() == 0) addDetail(R.string.st_detail_plate, "");
    }

    private void addDetail(@StringRes int label, @Nullable String value) {
        if (value == null || value.trim().isEmpty()) return;
        View row = LayoutInflater.from(this).inflate(R.layout.item_kv_row, detailsCard, false);
        ((TextView) row.findViewById(R.id.tvKvLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tvKvValue)).setText(value.trim());
        row.setContentDescription(getString(label) + ", " + value.trim());
        detailsCard.addView(row);
    }

    // ---- Row updates from the sheets ----

    /** The live text for each owner row: one small read each, on open and after a change, never polled. */
    private void loadRowSummaries() {
        if (vehicle == null || !owner) return;
        LiveLinksSheet.loadSummary(this, vehicleId, this::onLiveLinks);
        RemindersSheet.loadSummary(this, vehicleId, this::onReminders);
        AlertSettingsSheet.loadSummary(this, vehicleId, this::onAlertSettings);
        loadGeofenceNames();
        loadShares();
    }

    private void onLiveLinks(int active, @Nullable Instant soonestEnd) {
        if (active == 0 || soonestEnd == null) {
            setDetail(rowLive, getString(R.string.st_live_row_none));
            return;
        }
        String in = untilText(soonestEnd);
        setDetail(rowLive, active == 1
                ? getString(R.string.st_live_row_one, in)
                : getResources().getQuantityString(R.plurals.st_live_row_many, active, active, in));
    }

    private void onReminders(@Nullable String soonest, int daysLeft) {
        if (soonest == null) {
            setDetail(rowReminders, getString(R.string.st_reminders_row_none));
            return;
        }
        // Overdue in red, due within 30 days in amber.
        setDetail(rowReminders, soonest, daysLeft < 0 ? R.color.st_danger_text
                : daysLeft <= 30 ? R.color.st_status_offline_text : R.color.st_text_3);
    }

    private void onAlertSettings(int speed, boolean idleOn, int idleMinutes) {
        setDetail(rowAlertSettings, idleOn
                ? getString(R.string.st_alert_settings_row_idle, speed, idleMinutes)
                : getString(R.string.st_alert_settings_row, speed));
    }

    private void setDetail(@Nullable View row, String text) {
        setDetail(row, text, R.color.st_text_3);
    }

    private void setDetail(@Nullable View row, String text, @ColorRes int colour) {
        if (row == null) return;
        TextView d = row.findViewById(R.id.moreRowDetail);
        TextView t = row.findViewById(R.id.moreRowTitle);
        d.setText(text);
        d.setTextColor(ContextCompat.getColor(this, colour));
        d.setVisibility(View.VISIBLE);
        row.setContentDescription(t.getText() + ", " + text);
    }

    /** Geofences that cover this vehicle: its own plus the "all vehicles" ones. */
    private void loadGeofenceNames() {
        api.getMyGeofences().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isDestroyed() || !response.isSuccessful()) {
                    ApiErrors.close(response);
                    return;
                }
                List<String> names = new ArrayList<>();
                try (ResponseBody b = response.body()) {
                    JsonArray arr = b != null ? ApiJson.dataArray(b.string()) : null;
                    if (arr == null) return;
                    for (JsonElement e : arr) {
                        if (!e.isJsonObject()) continue;
                        JsonObject g = e.getAsJsonObject();
                        String forVehicle = ApiJson.string(g, "vehicleId");
                        boolean covers = forVehicle == null || forVehicle.isEmpty() || vehicleId.equalsIgnoreCase(forVehicle);
                        JsonElement active = ApiJson.get(g, "isActive");
                        boolean on = active == null || !active.isJsonPrimitive() || active.getAsBoolean();
                        String name = ApiJson.string(g, "name");
                        if (covers && on && name != null && !name.trim().isEmpty()) names.add(name.trim());
                    }
                } catch (Exception ignored) {
                    return;   // the row keeps its neutral text
                }
                String text;
                if (names.isEmpty()) {
                    text = getString(R.string.st_vehicle_geofences_none);
                } else if (names.size() <= GEOFENCE_NAMES) {
                    text = android.text.TextUtils.join(", ", names);
                } else {
                    int more = names.size() - GEOFENCE_NAMES;
                    text = getResources().getQuantityString(R.plurals.st_vehicle_geofences_more, more,
                            android.text.TextUtils.join(", ", names.subList(0, GEOFENCE_NAMES)), more);
                }
                setDetail(rowGeofences, text, R.color.st_text_3);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                // the row keeps its neutral text
            }
        });
    }

    /** Who this vehicle is shared with: accepted shares by name, pending ones as invited. */
    private void loadShares() {
        api.getMyVehicleShares(vehicleId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isDestroyed() || !response.isSuccessful()) {
                    ApiErrors.close(response);
                    return;
                }
                List<String> people = new ArrayList<>();
                String first = null;
                boolean firstPending = false;
                try (ResponseBody b = response.body()) {
                    JsonArray arr = b != null ? ApiJson.dataArray(b.string()) : null;
                    if (arr == null) return;
                    for (JsonElement e : arr) {
                        if (!e.isJsonObject()) continue;
                        JsonObject s = e.getAsJsonObject();
                        String forVehicle = ApiJson.string(s, "vehicleId");
                        if (forVehicle != null && !vehicleId.equalsIgnoreCase(forVehicle)) continue;
                        String status = ApiJson.string(s, "status");
                        boolean accepted = "Accepted".equalsIgnoreCase(status) || "1".equals(status);
                        boolean pending = "Pending".equalsIgnoreCase(status) || "0".equals(status);
                        if (!accepted && !pending) continue;
                        // The name arrives once they accept; until then, the number the owner typed.
                        String name = ApiJson.string(s, "otherPartyName");
                        if (name == null || name.trim().isEmpty()) {
                            String phone = ApiJson.string(s, "otherPartyPhoneNumber");
                            name = phone != null && !phone.trim().isEmpty()
                                    ? ProfileText.phone(phone)
                                    : getString(R.string.st_vehicle_someone);
                        }
                        if (first == null) {
                            first = name.trim();
                            firstPending = pending;
                        }
                        people.add(name.trim());
                    }
                } catch (Exception ignored) {
                    return;   // the row keeps its neutral text
                }
                String text;
                if (first == null) {
                    text = getString(R.string.st_vehicle_shared_none);
                } else if (people.size() == 1) {
                    text = getString(firstPending ? R.string.st_vehicle_shared_invited : R.string.st_vehicle_shared_viewer, first);
                } else {
                    int more = people.size() - 1;
                    text = getResources().getQuantityString(R.plurals.st_vehicle_shared_more, more, first, more);
                }
                setDetail(rowShared, text, R.color.st_text_3);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                // the row keeps its neutral text
            }
        });
    }

    /** "3 h" or "40 min" until the given time (rounded up, never "0"). */
    private String untilText(Instant end) {
        long minutes = Math.max(1, (long) Math.ceil(Duration.between(Instant.now(), end).getSeconds() / 60.0));
        if (minutes < 60) return getString(R.string.st_live_in_minutes, (int) minutes);
        return getString(R.string.st_live_in_hours, (int) Math.ceil(minutes / 60.0));
    }

    // ---- Actions ----

    private void showOnMap() {
        VehicleListActivity.rememberSelected(this, vehicleId);
        BottomNav.go(this, BottomNav.Tab.MAP);
    }

    private void openTrips() {
        Intent i = new Intent(this, TripHistoryActivity.class);
        i.putExtra(TripHistoryActivity.EXTRA_VEHICLE_ID, vehicleId);
        if (vehicle != null) i.putExtra(TripHistoryActivity.EXTRA_VEHICLE_NAME, Plates.plain(vehicle.getVehicleNumber()));
        startActivity(i);
    }

    private void openAlerts() {
        Intent i = new Intent(this, AlertsActivity.class);
        i.putExtra(AlertsActivity.EXTRA_VEHICLE_ID, vehicleId);
        if (vehicle != null) i.putExtra(AlertsActivity.EXTRA_VEHICLE_NAME, vehicle.getVehicleNumber());
        startActivity(i);
    }

    private void openGeofences() {
        Intent i = new Intent(this, GeofencesActivity.class);
        i.putExtra(GeofencesActivity.EXTRA_VEHICLE_ID, vehicleId);
        if (vehicle != null) i.putExtra(GeofencesActivity.EXTRA_VEHICLE_NAME, vehicle.getVehicleNumber());
        rowsLauncher.launch(i);
    }

    /** Directions to where the vehicle is (its reported position; the phone's location is never read here). */
    private void navigate() {
        DashboardVehicle v = vehicle;
        if (v == null || !v.hasPosition()) {
            banner.showError(R.string.st_home_no_position_yet, null);
            return;
        }
        Uri uri = Uri.parse(String.format(Locale.US, "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f",
                v.getLatitude(), v.getLongitude()));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            banner.showError(R.string.st_error_no_browser, null);
        }
    }

    private void openEdit() {
        VehicleResponse d = details;
        if (d == null) {
            banner.showError(R.string.st_vehicle_page_details_loading, null);
            return;
        }
        Intent intent = new Intent(this, AddVehicleActivity.class);
        intent.putExtra(AddVehicleActivity.EXTRA_EDIT_VEHICLE_ID, d.getVehicleId());
        intent.putExtra(AddVehicleActivity.EXTRA_VEHICLE_NUMBER, d.getVehicleNumber());
        intent.putExtra(AddVehicleActivity.EXTRA_MAKE, d.getMake());
        intent.putExtra(AddVehicleActivity.EXTRA_MODEL, d.getModel());
        intent.putExtra(AddVehicleActivity.EXTRA_YEAR, d.getYear() != null ? d.getYear() : 0);
        intent.putExtra(AddVehicleActivity.EXTRA_COLOR, d.getColor());
        intent.putExtra(AddVehicleActivity.EXTRA_VEHICLE_TYPE, d.getVehicleType());
        intent.putExtra(AddVehicleActivity.EXTRA_FUEL_TYPE, d.getFuelType());
        intent.putExtra(AddVehicleActivity.EXTRA_CHASSIS_NUMBER, d.getChassisNumber());
        intent.putExtra(AddVehicleActivity.EXTRA_ENGINE_NUMBER, d.getEngineNumber());
        editLauncher.launch(intent);
    }

    @SuppressLint("InflateParams") // dialog content has no parent to measure against
    private void confirmRemove() {
        DashboardVehicle v = vehicle;
        if (v == null) return;
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_confirm, null);
        sheet.setContentView(view);
        ((TextView) view.findViewById(R.id.tvConfirmTitle)).setText(
                getString(R.string.st_remove_vehicle_title, Plates.plain(v.getVehicleNumber())));
        ((TextView) view.findViewById(R.id.tvConfirmBody)).setText(R.string.st_remove_vehicle_body);
        TextView error = view.findViewById(R.id.tvConfirmError);
        MaterialButton confirm = view.findViewById(R.id.btnConfirmAction);
        MaterialButton cancel = view.findViewById(R.id.btnConfirmCancel);
        confirm.setText(R.string.st_remove_vehicle_confirm);
        cancel.setOnClickListener(x -> sheet.dismiss());
        confirm.setOnClickListener(x -> {
            confirm.setEnabled(false);
            cancel.setEnabled(false);
            confirm.setText(R.string.st_action_removing);
            error.setVisibility(View.GONE);
            api.deleteVehicle(vehicleId).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        ApiErrors.close(response);
                        clearSelectedIfThis();
                        sheet.dismiss();
                        BottomNav.go(VehiclesActivity.this, BottomNav.Tab.VEHICLES);
                        finish();
                        return;
                    }
                    confirm.setEnabled(true);
                    cancel.setEnabled(true);
                    confirm.setText(R.string.st_remove_vehicle_confirm);
                    error.setText(ApiErrors.read(response, getString(R.string.st_remove_vehicle_failed)).message);
                    error.setVisibility(View.VISIBLE);
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    confirm.setEnabled(true);
                    cancel.setEnabled(true);
                    confirm.setText(R.string.st_remove_vehicle_confirm);
                    error.setText(R.string.st_error_network);
                    error.setVisibility(View.VISIBLE);
                }
            });
        });
        sheet.show();
    }

    private void clearSelectedIfThis() {
        SharedPreferences prefs = getSharedPreferences(VehicleListActivity.VEHICLE_PREFS_NAME, Context.MODE_PRIVATE);
        if (vehicleId.equalsIgnoreCase(prefs.getString(VehicleListActivity.SELECTED_VEHICLE_ID_KEY, null))) {
            prefs.edit().remove(VehicleListActivity.SELECTED_VEHICLE_ID_KEY).apply();
        }
    }

    // ---- Small helpers ----

    private static String joined(@Nullable String a, @Nullable String b) {
        return ((a != null ? a.trim() : "") + " " + (b != null ? b.trim() : "")).trim();
    }

    @Nullable
    private String tail(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() <= 4 ? t : getString(R.string.st_detail_ends_with, t.substring(t.length() - 4));
    }
}
