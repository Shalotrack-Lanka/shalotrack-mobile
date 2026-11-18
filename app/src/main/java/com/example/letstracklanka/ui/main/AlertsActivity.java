package com.example.letstracklanka.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.AlertResponse;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.BottomNav;
import com.example.letstracklanka.ui.common.EdgeToEdge;
import com.example.letstracklanka.ui.common.EmptyState;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.NetworkWatcher;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.ui.common.VehiclePickerSheet;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.AuthGate;
import com.example.letstracklanka.utils.SecureScreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Alerts tab, and the same screen for one vehicle when opened from that vehicle's page
 * (EXTRA_VEHICLE_ID / EXTRA_VEHICLE_NAME: back arrow, no bottom bar).
 *
 * Data: GET api/alerts, 50 per page, newer pages loaded as the list nears its end. The kind chips
 * filter what is already loaded; if a filter leaves too little to scroll, older pages are fetched
 * automatically (at most three in a row) so a rare kind still shows something.
 * Refresh: on open, on pull, when a notification is tapped and when the tab comes back after more
 * than a minute away. No polling: push notifications already tell the user about new alerts.
 */
public class AlertsActivity extends AppCompatActivity {

    public static final String EXTRA_VEHICLE_ID = "extra_vehicle_id";
    public static final String EXTRA_VEHICLE_NAME = "extra_vehicle_name";
    /** Set by the push notification tap: show every vehicle's alerts, newest first, read fresh. */
    public static final String EXTRA_FROM_NOTIFICATION = "extra_from_notification";

    private static final int PAGE_SIZE = 50;
    private static final int MAX_AUTO_FILL_PAGES = 3;
    private static final long STALE_AFTER_MS = 60_000;

    /** Kind chips. A chip covers related kinds so every alert type is reachable from one of them. */
    private enum KindFilter {
        ALL(R.id.chipAlertsAll, R.string.st_alerts_filter_all),
        SPEED(R.id.chipAlertsSpeed, R.string.st_alerts_filter_speed, AlertAdapter.Kind.OVERSPEED),
        POWER(R.id.chipAlertsPower, R.string.st_alerts_filter_power,
                AlertAdapter.Kind.POWER_CUT, AlertAdapter.Kind.LOW_BATTERY, AlertAdapter.Kind.DEVICE_OFFLINE),
        GEOFENCE(R.id.chipAlertsGeofence, R.string.st_alerts_filter_geofence,
                AlertAdapter.Kind.GEOFENCE_ENTER, AlertAdapter.Kind.GEOFENCE_EXIT),
        IGNITION(R.id.chipAlertsIgnition, R.string.st_alerts_filter_ignition,
                AlertAdapter.Kind.IGNITION_ON, AlertAdapter.Kind.IGNITION_OFF, AlertAdapter.Kind.IDLE),
        SOS(R.id.chipAlertsSos, R.string.st_alerts_filter_sos, AlertAdapter.Kind.SOS);

        final int chipId;
        final int label;
        final Set<AlertAdapter.Kind> kinds;

        KindFilter(int chipId, int label, AlertAdapter.Kind... kinds) {
            this.chipId = chipId;
            this.label = label;
            this.kinds = kinds.length == 0 ? EnumSet.noneOf(AlertAdapter.Kind.class) : EnumSet.copyOf(Arrays.asList(kinds));
        }

        boolean matches(AlertResponse a) {
            return this == ALL || kinds.contains(AlertAdapter.Kind.of(a.getAlertType()));
        }
    }

    private ApiService api;

    @Nullable private String vehicleId;
    @Nullable private String vehicleName;
    private boolean openedForOneVehicle;
    private boolean navBound;
    private KindFilter kindFilter = KindFilter.ALL;

    private final List<AlertResponse> loaded = new ArrayList<>();
    private final Set<Long> loadedIds = new HashSet<>();
    private int nextPage = 1;
    private boolean hasMore = true;
    private boolean loading;
    private int autoFillPages;
    /** Bumped on every reload so replies for an older vehicle or filter are dropped. */
    private int generation;
    private long lastLoadedAt;

    private View header;
    private View btnBack;
    private TextView btnVehicle;
    private com.google.android.material.button.MaterialButton btnMarkAll;
    private boolean markingAll;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recycler;
    private LinearLayoutManager layoutManager;
    private ProgressBar progress;
    private AlertAdapter adapter;
    private InlineBanner banner;
    private EmptyState empty;
    private final List<TextView> chips = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Notifications open this screen directly; nobody signed in means nothing to show.
        if (!AuthGate.requireSignedIn(this)) return;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_alerts);
        SecureScreen.guardTouches(this);

        api = ApiClient.getClient().create(ApiService.class);
        bindViews();
        readIntent(getIntent());
        applyMode();

        NetworkWatcher.watch(this, new NetworkWatcher.Listener() {
            @Override
            public void onLost() {
                banner.showOffline(R.string.st_banner_offline_list, null);
            }

            @Override
            public void onRestored() {
                banner.hide();
                if (loaded.isEmpty() || lastLoadedAt == 0) reload(true);
            }
        });

        reload(true);
    }

    /**
     * Delivered when this screen already exists: a notification tap (singleTop), or the Alerts tab being
     * tapped again (the bar reorders the existing screen to the front, which also lands here). Only a
     * notification or a different vehicle reloads; a plain tab switch keeps the list and its scroll
     * position (onStart refreshes it if it is more than a minute old).
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        boolean fromPush = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false);
        String requested = trimToNull(intent.getStringExtra(EXTRA_VEHICLE_ID));
        boolean otherVehicle = requested != null && !requested.equals(vehicleId);
        if (!fromPush && !otherVehicle) return;
        setIntent(intent);
        if (!AuthGate.requireSignedIn(this)) return;
        readIntent(intent);
        applyMode();
        reload(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (lastLoadedAt != 0 && SystemClock.elapsedRealtime() - lastLoadedAt > STALE_AFTER_MS) reload(false);
    }

    // ---- Setup ----

    private void bindViews() {
        header = findViewById(R.id.alertsHeader);
        EdgeToEdge.padTop(header);
        btnBack = findViewById(R.id.btnBackAlerts);
        btnVehicle = findViewById(R.id.btnAlertsVehicle);
        btnMarkAll = findViewById(R.id.btnMarkAllRead);
        btnMarkAll.setOnClickListener(v -> markAllRead());
        swipeRefresh = findViewById(R.id.swipeRefreshAlerts);
        recycler = findViewById(R.id.recyclerAlerts);
        progress = findViewById(R.id.progressAlerts);
        banner = InlineBanner.in(this);
        empty = new EmptyState(findViewById(R.id.emptyState));

        layoutManager = new LinearLayoutManager(this);
        recycler.setLayoutManager(layoutManager);
        adapter = new AlertAdapter(new ArrayList<>(), this::onAlertTapped);
        recycler.setAdapter(adapter);
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loading || !hasMore) return;
                int last = layoutManager.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 8) loadNextPage(false);
            }
        });

        swipeRefresh.setColorSchemeResources(R.color.st_navy);
        swipeRefresh.setOnRefreshListener(() -> reload(false));

        btnBack.setOnClickListener(v -> finish());
        btnVehicle.setOnClickListener(v -> VehiclePickerSheet.show(this, true, this::onVehiclePicked));

        for (KindFilter f : KindFilter.values()) {
            TextView chip = findViewById(f.chipId);
            chips.add(chip);
            chip.setOnClickListener(v -> selectKind(f));
        }
        selectKindChip(KindFilter.ALL);
    }

    private void readIntent(Intent intent) {
        vehicleId = trimToNull(intent.getStringExtra(EXTRA_VEHICLE_ID));
        vehicleName = trimToNull(intent.getStringExtra(EXTRA_VEHICLE_NAME));
        openedForOneVehicle = vehicleId != null;
        kindFilter = KindFilter.ALL;
        selectKindChip(KindFilter.ALL);
    }

    private void applyMode() {
        if (openedForOneVehicle) {
            btnBack.setVisibility(View.VISIBLE);
            BottomNav.hide(this);
        } else {
            btnBack.setVisibility(View.GONE);
            View bar = findViewById(R.id.bottomNav);
            if (bar != null) bar.setVisibility(View.VISIBLE);
            if (!navBound) {
                BottomNav.bind(this, BottomNav.Tab.ALERTS);
                navBound = true;
            }
        }
        showVehicleLabel();
    }

    private void showVehicleLabel() {
        if (vehicleId == null) {
            btnVehicle.setText(R.string.st_picker_all_vehicles);
            btnVehicle.setSelected(false);
            btnVehicle.setContentDescription(getString(R.string.st_alerts_vehicle_all_a11y));
        } else {
            // Plain text on the chip: the plate's two-tone span would fight the chip's selected colours.
            String name = vehicleName != null ? Plates.plain(vehicleName) : getString(R.string.st_alerts_one_vehicle);
            btnVehicle.setText(name);
            btnVehicle.setSelected(true);
            btnVehicle.setContentDescription(getString(R.string.st_alerts_vehicle_one_a11y, name));
        }
    }

    private void onVehiclePicked(@Nullable DashboardVehicle v) {
        String newId = v != null ? v.getVehicleId() : null;
        if (newId == null ? vehicleId == null : newId.equals(vehicleId)) return;
        vehicleId = newId;
        vehicleName = v != null ? v.getVehicleNumber() : null;
        showVehicleLabel();
        reload(true);
    }

    private void selectKind(KindFilter f) {
        if (f == kindFilter) return;
        kindFilter = f;
        selectKindChip(f);
        autoFillPages = 0;
        render();
        recycler.scrollToPosition(0);
        maybeAutoFill();
    }

    private void selectKindChip(KindFilter f) {
        KindFilter[] all = KindFilter.values();
        for (int i = 0; i < chips.size() && i < all.length; i++) chips.get(i).setSelected(all[i] == f);
    }

    // ---- Data ----

    /** Starts again from page 1. showSpinner: centred spinner (first load, new vehicle) rather than the pull indicator. */
    private void reload(boolean showSpinner) {
        generation++;
        nextPage = 1;
        hasMore = true;
        loading = false;
        autoFillPages = 0;
        if (showSpinner) {
            loaded.clear();
            loadedIds.clear();
            adapter.updateAlerts(this, Collections.emptyList());
            empty.hide();
            progress.setVisibility(View.VISIBLE);
        }
        loadNextPage(true);
    }

    private void loadNextPage(boolean replace) {
        if (loading || !hasMore) return;
        loading = true;
        final int gen = generation;
        final int page = nextPage;
        api.getMyAlerts(page, PAGE_SIZE, vehicleId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (gen != generation || isDestroyed()) return;
                List<AlertResponse> pageItems = null;
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) pageItems = ApiJson.list(body.string(), AlertResponse.class);
                } catch (Exception ignored) {
                    pageItems = null;
                }
                finishLoad();
                if (pageItems == null) {
                    onLoadFailed(page, response.code(), replace);
                    return;
                }
                onPageLoaded(pageItems, replace);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (gen != generation || isDestroyed()) return;
                finishLoad();
                onLoadFailed(page, 0, replace);
            }
        });
    }

    private void finishLoad() {
        loading = false;
        progress.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
    }

    private void onPageLoaded(List<AlertResponse> items, boolean replace) {
        if (replace) {
            loaded.clear();
            loadedIds.clear();
        }
        for (AlertResponse a : items) {
            // A new alert arriving between page reads shifts the pages by one; skip what is already shown.
            if (a != null && loadedIds.add(a.getAlertId())) loaded.add(a);
        }
        hasMore = items.size() >= PAGE_SIZE;
        nextPage++;
        lastLoadedAt = SystemClock.elapsedRealtime();
        if (!NetworkWatcher.isOnline(this)) banner.showOffline(R.string.st_banner_offline_list, null);
        else banner.hide();
        render();
        maybeAutoFill();
    }

    private void onLoadFailed(int page, int httpCode, boolean replace) {
        Runnable retry = page == 1 ? () -> reload(loaded.isEmpty()) : () -> loadNextPage(false);
        int message = httpCode == 0 ? R.string.st_error_network
                : page == 1 ? R.string.st_alerts_load_failed : R.string.st_alerts_load_more_failed;
        if (replace && loaded.isEmpty()) {
            banner.hide();
            empty.show(getString(R.string.st_alerts_load_failed_title), getString(message),
                    getString(R.string.st_action_retry), retry);
        } else {
            // Keep what is on screen; say what failed above it.
            banner.showError(message, retry);
        }
    }

    /** If the chosen kind leaves the screen without enough to scroll, read a few older pages for it. */
    private void maybeAutoFill() {
        if (!hasMore || loading || kindFilter == KindFilter.ALL) return;
        if (autoFillPages >= MAX_AUTO_FILL_PAGES) return;
        recycler.post(() -> {
            if (isDestroyed() || loading || !hasMore) return;
            if (!recycler.canScrollVertically(1)) {
                autoFillPages++;
                loadNextPage(false);
            }
        });
    }

    private void render() {
        List<AlertResponse> shown = new ArrayList<>();
        boolean anyUnread = false;
        for (AlertResponse a : loaded) {
            if (!a.isRead()) anyUnread = true;
            if (kindFilter.matches(a)) shown.add(a);
        }
        adapter.updateAlerts(this, shown);
        btnMarkAll.setVisibility(anyUnread && !markingAll ? View.VISIBLE : View.GONE);

        if (!shown.isEmpty()) {
            empty.hide();
            return;
        }
        if (loading) return;
        if (!loaded.isEmpty()) {
            // Alerts exist, none of this kind in what has been read so far.
            String kind = getString(kindFilter.label);
            empty.show(getString(R.string.st_alerts_empty_kind_title, kind),
                    getString(hasMore ? R.string.st_alerts_empty_kind_body_more : R.string.st_alerts_empty_kind_body),
                    hasMore ? getString(R.string.st_alerts_action_older) : getString(R.string.st_alerts_action_show_all),
                    hasMore ? () -> { autoFillPages = 0; loadNextPage(false); } : () -> selectKind(KindFilter.ALL));
        } else if (vehicleId != null) {
            String name = vehicleName != null ? Plates.plain(vehicleName) : getString(R.string.st_alerts_one_vehicle);
            empty.show(getString(R.string.st_alerts_empty_vehicle_title, name), getString(R.string.st_alerts_empty_body), null, null);
        } else {
            empty.show(getString(R.string.st_alerts_empty_title), getString(R.string.st_alerts_empty_body), null, null);
        }
    }

    // ---- Tap: mark read ----

    private void onAlertTapped(AlertResponse alert) {
        if (alert.isRead()) return;
        // Optimistic: the dot goes at once; it comes back if the server says no.
        alert.setRead(true);
        render();
        BottomNav.markOneRead(this);
        api.markAlertAsRead(alert.getAlertId()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                ResponseBody body = response.body();
                if (body != null) body.close();
                if (!response.isSuccessful()) revert(alert, false);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                revert(alert, true);
            }
        });
    }

    /**
     * Every unread alert (this vehicle's, or all vehicles') becomes read on the server in one request.
     * Optimistic on screen; put back exactly as it was if the server says no.
     */
    private void markAllRead() {
        if (markingAll) return;
        final List<AlertResponse> changed = new ArrayList<>();
        for (AlertResponse a : loaded) {
            if (!a.isRead()) {
                a.setRead(true);
                changed.add(a);
            }
        }
        markingAll = true;
        render();
        final String scope = vehicleId;
        api.markAllAlertsRead(scope).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.body() != null) response.body().close();
                markingAll = false;
                if (isDestroyed()) return;
                if (response.isSuccessful()) {
                    // All vehicles: nothing is unread now. One vehicle: others may still be, so ask.
                    if (scope == null) BottomNav.setUnread(AlertsActivity.this, 0);
                    else BottomNav.refreshAlertsBadge(AlertsActivity.this, true);
                    render();
                    return;
                }
                undoMarkAll(changed, false);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                markingAll = false;
                if (!isDestroyed()) undoMarkAll(changed, true);
            }
        });
    }

    private void undoMarkAll(List<AlertResponse> changed, boolean network) {
        for (AlertResponse a : changed) a.setRead(false);
        render();
        banner.showError(network ? R.string.st_error_network : R.string.st_alerts_mark_all_failed, () -> {
            banner.hide();
            markAllRead();
        });
    }

    private void revert(AlertResponse alert, boolean network) {
        if (isDestroyed()) return;
        alert.setRead(false);
        BottomNav.markOneUnread(this);
        render();
        banner.showError(network ? R.string.st_error_network : R.string.st_alerts_mark_read_failed,
                () -> {
                    banner.hide();
                    onAlertTapped(alert);
                });
    }

    @Nullable
    private static String trimToNull(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
