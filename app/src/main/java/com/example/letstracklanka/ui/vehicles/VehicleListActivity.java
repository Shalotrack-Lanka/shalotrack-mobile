package com.example.letstracklanka.ui.vehicles;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.BottomNav;
import com.example.letstracklanka.ui.common.EdgeToEdge;
import com.example.letstracklanka.ui.common.EmptyState;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.NetworkWatcher;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.ui.common.VehicleStatus;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.CustomerSession;
import com.example.letstracklanka.utils.SecureScreen;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Vehicles tab. Every vehicle the customer owns, plus vehicles shared with them and the demo vehicle,
 * with live status. Search, status filters, pull to refresh, Add, Link tracker, swipe to remove.
 * Tapping a vehicle opens its page (VehiclesActivity) for that vehicle.
 *
 * Refresh: on open, on pull, on return to the tab and every 30 s while the tab is on screen. Never while
 * the app is in the background (the old screens polled the whole dashboard every second, all the time).
 */
public class VehicleListActivity extends AppCompatActivity {

    // Shared with Home / vehicle page / Places: which vehicle the map follows.
    public static final String VEHICLE_PREFS_NAME = "ShaloTrackVehiclePrefs";
    public static final String SELECTED_VEHICLE_ID_KEY = "selected_vehicle_id";

    private static final long AUTO_REFRESH_MS = 30_000;

    private ApiService api;
    private String customerId;

    private RecyclerView recycler;
    private VehicleListAdapter adapter;
    private ProgressBar progress;
    private SwipeRefreshLayout swipeRefresh;
    private EditText etSearch;
    private TextView chipAll, chipMoving, chipStopped, chipOffline, chipNoDevice, tvHint;
    private InlineBanner banner;
    private EmptyState empty;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            fetchVehicles(false);
            handler.postDelayed(this, AUTO_REFRESH_MS);
        }
    };
    private boolean firstLoadDone = false;
    private boolean loading = false;

    private ActivityResultLauncher<Intent> addVehicleLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_vehicle_list);
        SecureScreen.guardTouches(this);

        api = ApiClient.getClient().create(ApiService.class);
        addVehicleLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> fetchVehicles(true));

        bindViews();
        BottomNav.bind(this, BottomNav.Tab.VEHICLES);

        NetworkWatcher.watch(this, new NetworkWatcher.Listener() {
            @Override
            public void onLost() {
                banner.showOffline(R.string.st_banner_offline_list, null);
            }

            @Override
            public void onRestored() {
                banner.hide();
                fetchVehicles(false);
            }
        });

        loadCustomerThenVehicles();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (firstLoadDone) fetchVehicles(false);
        handler.postDelayed(autoRefresh, AUTO_REFRESH_MS);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(autoRefresh);
    }

    private void bindViews() {
        EdgeToEdge.padTop(findViewById(R.id.vehiclesContent));
        recycler = findViewById(R.id.recyclerVehicleList);
        progress = findViewById(R.id.progressVehicleList);
        swipeRefresh = findViewById(R.id.swipeRefreshVehicles);
        etSearch = findViewById(R.id.etVehicleSearch);
        chipAll = findViewById(R.id.chipAll);
        chipMoving = findViewById(R.id.chipMoving);
        chipStopped = findViewById(R.id.chipStopped);
        chipOffline = findViewById(R.id.chipOffline);
        chipNoDevice = findViewById(R.id.chipNoDevice);
        tvHint = findViewById(R.id.tvVehicleListHint);
        banner = InlineBanner.in(this);
        empty = new EmptyState(findViewById(R.id.emptyState));

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VehicleListAdapter(new ArrayList<>(), this::openVehicle, this::confirmRemove);
        adapter.setOnLinkTrackerListener(this::openLinkTracker);
        recycler.setAdapter(adapter);

        swipeRefresh.setColorSchemeResources(R.color.st_navy);
        swipeRefresh.setOnRefreshListener(() -> fetchVehicles(false));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
                renderEmptyIfNeeded();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        chipAll.setOnClickListener(v -> selectFilter(null, chipAll));
        chipMoving.setOnClickListener(v -> selectFilter(VehicleListAdapter.StatusFilter.MOVING, chipMoving));
        chipStopped.setOnClickListener(v -> selectFilter(VehicleListAdapter.StatusFilter.STOPPED, chipStopped));
        chipOffline.setOnClickListener(v -> selectFilter(VehicleListAdapter.StatusFilter.OFFLINE, chipOffline));
        chipNoDevice.setOnClickListener(v -> selectFilter(VehicleListAdapter.StatusFilter.NO_DEVICE, chipNoDevice));
        chipAll.setSelected(true);

        MaterialButton btnAdd = findViewById(R.id.btnAddVehicle);
        btnAdd.setOnClickListener(v -> openAddVehicle());
    }

    private void selectFilter(@Nullable VehicleListAdapter.StatusFilter filter, TextView chip) {
        for (TextView c : new TextView[]{chipAll, chipMoving, chipStopped, chipOffline, chipNoDevice}) {
            c.setSelected(c == chip);
        }
        adapter.setStatusFilter(filter);
        renderEmptyIfNeeded();
    }

    // ---- Data ----

    private void loadCustomerThenVehicles() {
        progress.setVisibility(View.VISIBLE);
        CustomerSession.get(this, new CustomerSession.Callback2() {
            @Override
            public void onCustomer(@NonNull String id) {
                customerId = id;
                fetchVehicles(true);
            }

            @Override
            public void onError(int httpCode) {
                progress.setVisibility(View.GONE);
                showLoadFailure(httpCode);
            }
        });
    }

    private void fetchVehicles(boolean showSpinner) {
        if (customerId == null) {
            if (!loading) loadCustomerThenVehicles();
            return;
        }
        if (loading) return;
        loading = true;
        if (showSpinner && !firstLoadDone) progress.setVisibility(View.VISIBLE);

        api.getCustomerDashboard(customerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                loading = false;
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                try (ResponseBody body = response.body()) {
                    if (response.code() == 403 || response.code() == 404) {
                        // Cached id belongs to another session: ask the server who this is, then retry once.
                        CustomerSession.forget(VehicleListActivity.this);
                        customerId = null;
                        if (!firstLoadDone) loadCustomerThenVehicles();
                        return;
                    }
                    if (!response.isSuccessful() || body == null) {
                        showLoadFailure(response.code());
                        return;
                    }
                    banner.hide();
                    List<DashboardVehicle> list = parseVehicles(body.string());
                    firstLoadDone = true;
                    adapter.updateVehicles(list);
                    updateChipCounts(list);
                    renderEmptyIfNeeded();
                } catch (Exception e) {
                    showLoadFailure(-1);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                loading = false;
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                showLoadFailure(0);
            }
        });
    }

    private void showLoadFailure(int httpCode) {
        Runnable retry = () -> fetchVehicles(true);
        if (httpCode == 0 && !NetworkWatcher.isOnline(this)) {
            banner.showOffline(R.string.st_banner_offline_list, retry);
        } else if (firstLoadDone) {
            banner.showError(R.string.st_vehicles_refresh_failed, retry);
        } else {
            banner.hide();
            empty.show(getString(R.string.st_vehicles_load_failed_title),
                    getString(R.string.st_load_failed_body), getString(R.string.st_action_try_again), retry);
            recycler.setVisibility(View.GONE);
        }
    }

    private void renderEmptyIfNeeded() {
        if (!firstLoadDone) return;
        boolean hasAny = !adapter.getAllVehicles().isEmpty();
        boolean visible = adapter.getItemCount() > 0;
        recycler.setVisibility(visible ? View.VISIBLE : View.GONE);
        boolean ownsAny = false;
        for (DashboardVehicle v : adapter.getAllVehicles()) if (!v.isShared() && !v.isDemo()) { ownsAny = true; break; }
        tvHint.setVisibility(ownsAny && visible ? View.VISIBLE : View.GONE);
        if (visible) {
            empty.hide();
        } else if (!hasAny) {
            empty.show(getString(R.string.st_vehicles_empty_title), getString(R.string.st_vehicles_empty_body),
                    getString(R.string.st_vehicles_add_first), this::openAddVehicle);
        } else {
            empty.show(getString(R.string.st_vehicles_no_match_title), getString(R.string.st_vehicles_no_match_body),
                    getString(R.string.st_vehicles_show_all), () -> {
                        etSearch.setText("");
                        selectFilter(null, chipAll);
                    });
        }
    }

    private void updateChipCounts(List<DashboardVehicle> all) {
        int moving = 0, stopped = 0, offline = 0, noDevice = 0;
        for (DashboardVehicle v : all) {
            switch (VehicleStatus.of(v)) {
                case MOVING: moving++; break;
                case IDLING:
                case PARKED: stopped++; break;
                case OFFLINE: offline++; break;
                default: noDevice++; break;
            }
        }
        chipAll.setText(getString(R.string.st_filter_all, all.size()));
        setChip(chipMoving, VehicleStatus.MOVING, getString(R.string.st_filter_moving, moving), moving);
        setChip(chipStopped, VehicleStatus.PARKED, getString(R.string.st_filter_stopped, stopped), stopped);
        setChip(chipOffline, VehicleStatus.OFFLINE, getString(R.string.st_filter_offline, offline), offline);
        setChip(chipNoDevice, VehicleStatus.NO_DEVICE, getString(R.string.st_filter_no_tracker, noDevice), noDevice);
    }

    private void setChip(TextView chip, VehicleStatus status, String text, int count) {
        chip.setText(text);
        chip.setCompoundDrawablesRelativeWithIntrinsicBounds(status.dotDrawable(this, 7), null, null, null);
        // Hide empty filters (except the one currently selected) so the row stays short.
        chip.setVisibility(count > 0 || chip.isSelected() ? View.VISIBLE : View.GONE);
    }

    private static List<DashboardVehicle> parseVehicles(String json) {
        JsonObject data = ApiJson.dataObject(json);
        if (data == null) return new ArrayList<>();
        JsonArray arr = data.has("vehicles") && data.get("vehicles").isJsonArray()
                ? data.getAsJsonArray("vehicles") : null;
        if (arr == null) return new ArrayList<>();
        List<DashboardVehicle> list = new Gson().fromJson(arr,
                TypeToken.getParameterized(List.class, DashboardVehicle.class).getType());
        return list != null ? list : new ArrayList<>();
    }

    // ---- Actions ----

    private void openVehicle(DashboardVehicle v) {
        rememberSelected(this, v.getVehicleId());
        Intent intent = new Intent(this, VehiclesActivity.class);
        intent.putExtra(VehiclesActivity.EXTRA_VEHICLE_ID, v.getVehicleId());
        startActivity(intent);
    }

    private void openAddVehicle() {
        if (customerId == null) {
            banner.showError(R.string.st_profile_still_loading, this::loadCustomerThenVehicles);
            return;
        }
        Intent intent = new Intent(this, AddVehicleActivity.class);
        intent.putExtra(AddVehicleActivity.EXTRA_CUSTOMER_ID, customerId);
        addVehicleLauncher.launch(intent);
    }

    private void openLinkTracker(DashboardVehicle v) {
        Intent intent = new Intent(this, com.example.letstracklanka.ui.devices.DevicesActivity.class);
        intent.putExtra(VehiclesActivity.EXTRA_VEHICLE_ID, v.getVehicleId());
        addVehicleLauncher.launch(intent);
    }

    /** Confirmation in a bottom sheet (design system), not a system dialog. */
    private void confirmRemove(DashboardVehicle v) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_confirm, null);
        sheet.setContentView(view);
        ((TextView) view.findViewById(R.id.tvConfirmTitle)).setText(
                getString(R.string.st_remove_vehicle_title, Plates.plain(v.getVehicleNumber())));
        ((TextView) view.findViewById(R.id.tvConfirmBody)).setText(R.string.st_remove_vehicle_body);
        TextView error = view.findViewById(R.id.tvConfirmError);
        MaterialButton confirm = view.findViewById(R.id.btnConfirmAction);
        confirm.setText(R.string.st_remove_vehicle_confirm);
        view.findViewById(R.id.btnConfirmCancel).setOnClickListener(x -> sheet.dismiss());
        confirm.setOnClickListener(x -> {
            confirm.setEnabled(false);
            error.setVisibility(View.GONE);
            api.deleteVehicle(v.getVehicleId()).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    confirm.setEnabled(true);
                    if (response.isSuccessful()) {
                        if (response.body() != null) response.body().close();
                        clearSelectedIf(v.getVehicleId());
                        sheet.dismiss();
                        fetchVehicles(false);
                        return;
                    }
                    String msg = getString(R.string.st_remove_vehicle_failed);
                    try {
                        if (response.errorBody() != null && response.code() < 500) {
                            msg = ApiJson.message(response.errorBody().string(), msg);
                        }
                    } catch (Exception ignored) {
                        // keep the generic message
                    }
                    error.setText(msg);
                    error.setVisibility(View.VISIBLE);
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    confirm.setEnabled(true);
                    error.setText(R.string.st_error_network);
                    error.setVisibility(View.VISIBLE);
                }
            });
        });
        sheet.show();
    }

    // ---- Selected vehicle (shared with the map) ----

    public static void rememberSelected(Context c, String vehicleId) {
        if (vehicleId == null) return;
        c.getSharedPreferences(VEHICLE_PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(SELECTED_VEHICLE_ID_KEY, vehicleId).apply();
    }

    @Nullable
    public static String selectedVehicleId(Context c) {
        return c.getSharedPreferences(VEHICLE_PREFS_NAME, Context.MODE_PRIVATE).getString(SELECTED_VEHICLE_ID_KEY, null);
    }

    private void clearSelectedIf(String vehicleId) {
        SharedPreferences prefs = getSharedPreferences(VEHICLE_PREFS_NAME, Context.MODE_PRIVATE);
        if (vehicleId != null && vehicleId.equalsIgnoreCase(prefs.getString(SELECTED_VEHICLE_ID_KEY, null))) {
            prefs.edit().remove(SELECTED_VEHICLE_ID_KEY).apply();
        }
    }
}
