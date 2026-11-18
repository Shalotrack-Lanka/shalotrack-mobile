package com.example.letstracklanka.ui.history;

import android.content.Intent;
import android.os.Bundle;
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
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.data.model.TripSummary;
import com.example.letstracklanka.data.model.TripsReportResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.ui.common.BottomNav;
import com.example.letstracklanka.ui.common.EdgeToEdge;
import com.example.letstracklanka.ui.common.EmptyState;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.NetworkWatcher;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.ui.common.TimeText;
import com.example.letstracklanka.ui.common.VehiclePickerSheet;
import com.example.letstracklanka.ui.vehicles.VehicleListActivity;
import com.example.letstracklanka.utils.ApiJson;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Trips tab: one vehicle's trips for Today / Yesterday / the last 7 days / a picked day, with totals.
 *
 * Opened three ways:
 *   - the Trips tab: follows the vehicle selected on the map (or the first vehicle);
 *   - "Trips" on the map or a vehicle's page: EXTRA_VEHICLE_ID (+ EXTRA_VEHICLE_NAME);
 *   - a Trip Report: EXTRA_REPORT_FROM_MILLIS / EXTRA_REPORT_TO_MILLIS fix the range (back arrow, no tab bar).
 *
 * Ranges are bounded on purpose (at most 7 days per request): the API derives trips from raw GPS points,
 * so unbounded or 30-day windows are the expensive queries.
 */
public class TripHistoryActivity extends AppCompatActivity {

    public static final String EXTRA_VEHICLE_ID = "extra_vehicle_id";
    public static final String EXTRA_VEHICLE_NAME = "extra_vehicle_name";
    public static final String EXTRA_REPORT_FROM_MILLIS = "extra_report_from_millis";
    public static final String EXTRA_REPORT_TO_MILLIS = "extra_report_to_millis";

    private enum Range { TODAY, YESTERDAY, LAST_7, DAY, REPORT }

    private ShaloTrackApi trackingApi;

    private String vehicleId;
    private String vehicleLabel;
    private Range range = Range.TODAY;
    private ZonedDateTime from;
    private ZonedDateTime to;
    private boolean reportMode = false;
    private int requestSeq = 0;

    private TextView tvPlate, segToday, segYesterday, seg7Days, segPickDate;
    private TextView tvSumDistance, tvSumDriving, tvSumTrips, tvSumTopSpeed;
    private RecyclerView list;
    private TripHistoryAdapter adapter;
    private ProgressBar progress;
    private SwipeRefreshLayout swipe;
    private InlineBanner banner;
    private EmptyState empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_trip_history);
        com.example.letstracklanka.utils.SecureScreen.guardTouches(this);
        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);
        bindViews();

        long reportFrom = getIntent().getLongExtra(EXTRA_REPORT_FROM_MILLIS, -1);
        long reportTo = getIntent().getLongExtra(EXTRA_REPORT_TO_MILLIS, -1);
        reportMode = reportFrom > 0 && reportTo > reportFrom;
        if (reportMode) {
            BottomNav.hide(this);
            View back = findViewById(R.id.btnBackHistory);
            back.setVisibility(View.VISIBLE);
            back.setOnClickListener(v -> finish());
            findViewById(R.id.rangeSegments).setVisibility(View.GONE);
            EdgeToEdge.padBottom(findViewById(R.id.rvTripHistory));
            from = java.time.Instant.ofEpochMilli(reportFrom).atZone(ZoneId.systemDefault());
            to = java.time.Instant.ofEpochMilli(reportTo).atZone(ZoneId.systemDefault());
            range = Range.REPORT;
        } else {
            BottomNav.bind(this, BottomNav.Tab.TRIPS);
            setRange(Range.TODAY, null, false);
        }

        NetworkWatcher.watch(this, new NetworkWatcher.Listener() {
            @Override
            public void onLost() {
                banner.showOffline(R.string.st_banner_offline_list, null);
            }

            @Override
            public void onRestored() {
                banner.hide();
                fetchTrips();
            }
        });

        applyIntentVehicle(getIntent());
        if (vehicleId == null) loadDefaultVehicle(); else fetchTrips();
    }

    /** The tab is reordered to the front with a new vehicle when "Trips" is tapped on another screen. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String before = vehicleId;
        applyIntentVehicle(intent);
        if (vehicleId != null && !vehicleId.equalsIgnoreCase(before)) fetchTrips();
    }

    private void applyIntentVehicle(Intent intent) {
        String id = intent.getStringExtra(EXTRA_VEHICLE_ID);
        if (id == null) return;
        setVehicle(id, intent.getStringExtra(EXTRA_VEHICLE_NAME));
    }

    private void setVehicle(String id, @Nullable String label) {
        vehicleId = id;
        vehicleLabel = label != null && !label.trim().isEmpty() ? label : vehicleLabel;
        adapter.setVehicleId(id);
        tvPlate.setText(vehicleLabel != null ? Plates.format(this, vehicleLabel) : getString(R.string.st_picker_title));
    }

    private void bindViews() {
        EdgeToEdge.padTop(findViewById(R.id.tripsHeader));
        tvPlate = findViewById(R.id.tvTripVehiclePlate);
        segToday = findViewById(R.id.segToday);
        segYesterday = findViewById(R.id.segYesterday);
        seg7Days = findViewById(R.id.seg7Days);
        segPickDate = findViewById(R.id.segPickDate);
        tvSumDistance = findViewById(R.id.tvSumDistance);
        tvSumDriving = findViewById(R.id.tvSumDriving);
        tvSumTrips = findViewById(R.id.tvSumTrips);
        tvSumTopSpeed = findViewById(R.id.tvSumTopSpeed);
        list = findViewById(R.id.rvTripHistory);
        progress = findViewById(R.id.progressTrips);
        swipe = findViewById(R.id.swipeRefreshTrips);
        banner = InlineBanner.in(this);
        empty = new EmptyState(findViewById(R.id.emptyState));

        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TripHistoryAdapter(this::openTripDetail, trackingApi);
        list.setAdapter(adapter);

        swipe.setColorSchemeResources(R.color.st_navy);
        swipe.setOnRefreshListener(this::fetchTrips);

        segToday.setOnClickListener(v -> setRange(Range.TODAY, null, true));
        segYesterday.setOnClickListener(v -> setRange(Range.YESTERDAY, null, true));
        seg7Days.setOnClickListener(v -> setRange(Range.LAST_7, null, true));
        segPickDate.setOnClickListener(v -> pickDay());
        findViewById(R.id.btnTripVehicle).setOnClickListener(v -> pickVehicle());
    }

    private void setRange(Range r, @Nullable LocalDate day, boolean fetch) {
        range = r;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        switch (r) {
            case YESTERDAY:
                from = today.minusDays(1).atStartOfDay(zone);
                to = today.atStartOfDay(zone).minusSeconds(1);
                break;
            case LAST_7:
                from = today.minusDays(6).atStartOfDay(zone);
                to = ZonedDateTime.now(zone);
                break;
            case DAY:
                LocalDate d = day != null ? day : today;
                from = d.atStartOfDay(zone);
                to = d.plusDays(1).atStartOfDay(zone).minusSeconds(1);
                break;
            case TODAY:
            default:
                from = today.atStartOfDay(zone);
                to = ZonedDateTime.now(zone);
                break;
        }
        segToday.setSelected(r == Range.TODAY);
        segYesterday.setSelected(r == Range.YESTERDAY);
        seg7Days.setSelected(r == Range.LAST_7);
        segPickDate.setSelected(r == Range.DAY);
        segPickDate.setText(r == Range.DAY && day != null
                ? day.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
                : getString(R.string.st_trips_pick_date));
        if (fetch) fetchTrips();
    }

    private void pickDay() {
        new DateDrillDownBottomSheet(this, selected -> {
            LocalDate day = LocalDate.of(selected.get(Calendar.YEAR), selected.get(Calendar.MONTH) + 1,
                    selected.get(Calendar.DAY_OF_MONTH));
            setRange(Range.DAY, day, true);
        }).show();
    }

    private void pickVehicle() {
        VehiclePickerSheet.show(this, false, v -> {
            if (v == null) return;
            VehicleListActivity.rememberSelected(this, v.getVehicleId());
            setVehicle(v.getVehicleId(), v.getVehicleNumber());
            fetchTrips();
        });
    }

    private void loadDefaultVehicle() {
        progress.setVisibility(View.VISIBLE);
        VehiclePickerSheet.load(this, new VehiclePickerSheet.LoadCallback() {
            @Override
            public void onLoaded(List<DashboardVehicle> vehicles) {
                progress.setVisibility(View.GONE);
                if (vehicles.isEmpty()) {
                    empty.show(getString(R.string.st_trips_no_vehicle_title), getString(R.string.st_trips_no_vehicle_body), null, null);
                    return;
                }
                String remembered = VehicleListActivity.selectedVehicleId(TripHistoryActivity.this);
                DashboardVehicle pick = vehicles.get(0);
                for (DashboardVehicle v : vehicles) {
                    if (remembered != null && remembered.equalsIgnoreCase(v.getVehicleId())) {
                        pick = v;
                        break;
                    }
                }
                setVehicle(pick.getVehicleId(), pick.getVehicleNumber());
                fetchTrips();
            }

            @Override
            public void onFailed(int httpCode) {
                progress.setVisibility(View.GONE);
                showFailure(httpCode, TripHistoryActivity.this::loadDefaultVehicle);
            }
        });
    }

    private void fetchTrips() {
        if (vehicleId == null) {
            swipe.setRefreshing(false);
            return;
        }
        final int seq = ++requestSeq;
        empty.hide();
        if (!swipe.isRefreshing()) progress.setVisibility(View.VISIBLE);
        // Whole seconds: Instant.toString() can carry 9 fraction digits, more than .NET's parser promises to read.
        String fromIso = from.toInstant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        String toIso = to.toInstant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        trackingApi.getTripsSummary(vehicleId, fromIso, toIso).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (seq != requestSeq || isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        showFailure(response.code(), TripHistoryActivity.this::fetchTrips);
                        return;
                    }
                    banner.hide();
                    TripsReportResponse report = ApiJson.object(body.string(), TripsReportResponse.class);
                    List<TripSummary> trips = report != null && report.getTrips() != null ? report.getTrips() : new ArrayList<>();
                    render(trips);
                } catch (Exception e) {
                    showFailure(-1, TripHistoryActivity.this::fetchTrips);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (seq != requestSeq || isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                showFailure(0, TripHistoryActivity.this::fetchTrips);
            }
        });
    }

    private void render(List<TripSummary> trips) {
        double km = 0, minutes = 0, top = 0;
        for (TripSummary t : trips) {
            km += t.getDistanceKm();
            minutes += t.getDurationMinutes();
            top = Math.max(top, t.getMaxSpeed());
        }
        tvSumDistance.setText(String.format(Locale.getDefault(), "%.1f", km));
        tvSumDriving.setText(TimeText.duration(this, minutes));
        tvSumTrips.setText(String.valueOf(trips.size()));
        tvSumTopSpeed.setText(String.valueOf(Math.round(top)));

        adapter.updateTrips(this, trips);
        if (trips.isEmpty()) {
            list.setVisibility(View.GONE);
            empty.show(getString(R.string.st_trips_empty_title, rangeLabel()), getString(R.string.st_trips_empty_body),
                    reportMode ? null : getString(R.string.st_trips_pick_another_day), reportMode ? null : this::pickDay);
        } else {
            list.setVisibility(View.VISIBLE);
            list.scrollToPosition(0);
        }
    }

    private String rangeLabel() {
        switch (range) {
            case YESTERDAY: return getString(R.string.st_time_yesterday).toLowerCase(Locale.getDefault());
            case LAST_7: return getString(R.string.st_trips_range_last_7);
            case DAY:
            case REPORT: return from.toLocalDate().format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()));
            case TODAY:
            default: return getString(R.string.st_time_today).toLowerCase(Locale.getDefault());
        }
    }

    private void showFailure(int httpCode, Runnable retry) {
        if (httpCode == 0 && !NetworkWatcher.isOnline(this)) {
            banner.showOffline(R.string.st_banner_offline_list, retry);
        } else {
            banner.showError(R.string.st_trips_load_failed, retry);
        }
    }

    private void openTripDetail(TripSummary trip) {
        if (vehicleId == null) return;
        Intent intent = new Intent(this, TripDetailActivity.class);
        intent.putExtra(TripDetailActivity.EXTRA_VEHICLE_ID, vehicleId);
        intent.putExtra(TripDetailActivity.EXTRA_FROM_ISO, trip.getStartTime());
        intent.putExtra(TripDetailActivity.EXTRA_TO_ISO, trip.getEndTime());
        intent.putExtra(TripDetailActivity.EXTRA_VEHICLE_NAME, vehicleLabel != null ? Plates.plain(vehicleLabel) : "");
        intent.putExtra(TripDetailActivity.EXTRA_DISTANCE_KM, trip.getDistanceKm());
        intent.putExtra(TripDetailActivity.EXTRA_DURATION_MIN, trip.getDurationMinutes());
        intent.putExtra(TripDetailActivity.EXTRA_MAX_SPEED, trip.getMaxSpeed());
        intent.putExtra(TripDetailActivity.EXTRA_AVG_SPEED, trip.getAvgSpeed());
        startActivity(intent);
    }
}
