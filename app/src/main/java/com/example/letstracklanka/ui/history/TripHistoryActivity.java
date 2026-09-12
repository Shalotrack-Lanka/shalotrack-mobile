package com.example.letstracklanka.ui.history;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.TripSummary;
import com.example.letstracklanka.data.model.TripsReportResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Rewritten for the new day-grouped, Letstrack-style layout (activity_trip_history.xml,
 * item_trip_history_day_header.xml, item_trip_history_card.xml). Previous version used a
 * completely different set of view IDs (tvRangeLabel/chipToday/recyclerTrips/etc.) that no
 * longer exist in the new layout at all -- this is a full rewrite, not a patch.
 *
 * IMPORTANT FIX: the previous version had NO vehicle context passed in at all -- it called
 * getVehiclesByCustomer() itself and grabbed list.get(list.size()-1), the exact same
 * "silently pick whichever vehicle happens to be last in a confirmed-incomplete endpoint"
 * bug already fixed in VehiclesActivity's vehicle switcher. Worse here: since nothing passed
 * vehicle context, tapping "History" on ANY vehicle would always show the same one arbitrary
 * vehicle's trips, never the one actually being viewed. Now reads EXTRA_VEHICLE_ID/
 * EXTRA_VEHICLE_NAME directly from the Intent (see VehiclesActivity.openTripHistory()) and
 * never calls getVehiclesByCustomer at all for vehicle selection.
 *
 * "Post" and "Save Place" are intentionally NOT wired anywhere in this Activity or
 * TripHistoryAdapter -- both hidden in the layouts per explicit decision to defer them past
 * this deployment.
 *
 * Assumes Material Components version with date-range-picker support (materialdatepicker
 * package) is already a dependency -- consistent with the extensive existing use of Material
 * widgets throughout this app, but not verified against build.gradle directly.
 */
public class TripHistoryActivity extends AppCompatActivity {

    public static final String EXTRA_VEHICLE_ID = "extra_vehicle_id";
    public static final String EXTRA_VEHICLE_NAME = "extra_vehicle_name";

    // Was DEFAULT_RANGE_DAYS = 7 (too narrow), briefly changed to 365*5
    // (dangerously wide -- GetTripsSummaryAsync processes raw GPS points
    // to derive trips, so one request covering years of data risks
    // scanning millions of points server-side, not just being slow to
    // render). Real fix: small initial window, loaded incrementally as
    // the user actually scrolls, never one giant request.
    private static final int INITIAL_RANGE_DAYS = 30;
    private static final int LOAD_MORE_CHUNK_DAYS = 30;

    private ApiService mainApiService;
    private ShaloTrackApi trackingApi;

    private String selectedVehicleId;
    private String selectedVehicleName = "Vehicle";

    private TextView tvHistoryDeviceName, tvErrorBannerMessage, tvErrorBannerRetry;
    private View btnBackHistory, btnCalendarPicker, errorBanner;
    private RecyclerView rvTripHistory;
    private TripHistoryAdapter adapter;
    private ConnectivityManager.NetworkCallback networkCallback;

    private Date rangeFrom;
    private Date rangeTo;

    // Incremental loading state -- accumulated across multiple fetches
    // rather than replacing the list each time. isCustomRangeSelected
    // stops auto-loading-further-back once the user has explicitly picked
    // their own range via the calendar picker -- they asked for exactly
    // that period, not an ever-expanding one.
    private final List<TripSummary> allLoadedTrips = new ArrayList<>();
    private boolean isLoadingMore = false;
    private boolean hasMoreToLoad = true;
    private boolean isCustomRangeSelected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_history);

        mainApiService = ApiClient.getClient().create(ApiService.class);
        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);

        selectedVehicleId = getIntent().getStringExtra(EXTRA_VEHICLE_ID);
        String passedName = getIntent().getStringExtra(EXTRA_VEHICLE_NAME);
        if (passedName != null && !passedName.trim().isEmpty()) selectedVehicleName = passedName;

        initViews();
        registerNetworkMonitor();

        if (selectedVehicleId == null) {
            showErrorBanner("No vehicle selected. Go back and try again.", null);
            return;
        }

        setDefaultRange();
        fetchTrips();
    }

    private void initViews() {
        btnBackHistory = findViewById(R.id.btnBackHistory);
        tvHistoryDeviceName = findViewById(R.id.tvHistoryDeviceName);
        btnCalendarPicker = findViewById(R.id.btnCalendarPicker);
        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        tvErrorBannerRetry = findViewById(R.id.tvErrorBannerRetry);
        rvTripHistory = findViewById(R.id.rvTripHistory);

        if (tvHistoryDeviceName != null) tvHistoryDeviceName.setText(selectedVehicleName);
        if (btnBackHistory != null) btnBackHistory.setOnClickListener(v -> finish());
        if (btnCalendarPicker != null) btnCalendarPicker.setOnClickListener(v -> showRangePicker());
        if (tvErrorBannerRetry != null) tvErrorBannerRetry.setOnClickListener(v -> fetchTrips());

        rvTripHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TripHistoryAdapter(this::openTripDetail, trackingApi);
        adapter.setVehicleId(selectedVehicleId);
        rvTripHistory.setAdapter(adapter);

        // Real chunked loading -- fires loadOlderTrips() when the user
        // scrolls near the bottom, instead of ever fetching years of data
        // in one request.
        rvTripHistory.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || isLoadingMore || !hasMoreToLoad || isCustomRangeSelected) return;
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int lastVisible = lm.findLastVisibleItemPosition();
                int totalCount = adapter.getItemCount();
                if (lastVisible >= totalCount - 5) {
                    loadOlderTrips();
                }
            }
        });
    }

    private void setDefaultRange() {
        Calendar cal = Calendar.getInstance();
        rangeTo = cal.getTime();
        cal.add(Calendar.DAY_OF_YEAR, -INITIAL_RANGE_DAYS);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        rangeFrom = cal.getTime();
    }

    // Replaced the previous raw MaterialDatePicker calendar grid with a
    // structured Month -> Week -> Day drill-down, per explicit request.
    // Selecting a day sets that single day as the range, using the same
    // isCustomRangeSelected + fetchTrips() path already established for
    // the old range picker.
    private void showRangePicker() {
        DateDrillDownBottomSheet drillDown = new DateDrillDownBottomSheet(this, selectedDay -> {
            Calendar startOfDay = (Calendar) selectedDay.clone();
            startOfDay.set(Calendar.HOUR_OF_DAY, 0);
            startOfDay.set(Calendar.MINUTE, 0);
            startOfDay.set(Calendar.SECOND, 0);

            Calendar endOfDay = (Calendar) selectedDay.clone();
            endOfDay.set(Calendar.HOUR_OF_DAY, 23);
            endOfDay.set(Calendar.MINUTE, 59);
            endOfDay.set(Calendar.SECOND, 59);

            rangeFrom = startOfDay.getTime();
            rangeTo = endOfDay.getTime();
            isCustomRangeSelected = true;
            allLoadedTrips.clear();
            fetchTrips();
        });
        drillDown.show();
    }

    private void fetchTrips() {
        if (selectedVehicleId == null || rangeFrom == null || rangeTo == null) return;
        hideErrorBanner();

        String fromIso = toIsoUtc(rangeFrom);
        String toIso = toIsoUtc(rangeTo);

        trackingApi.getTripsSummary(selectedVehicleId, fromIso, toIso).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        TripsReportResponse report = extractObject(body.string(), TripsReportResponse.class);
                        List<TripSummary> trips = report != null ? report.getTrips() : null;

                        allLoadedTrips.clear();
                        if (trips != null) allLoadedTrips.addAll(trips);
                        hasMoreToLoad = !isCustomRangeSelected; // a custom pick is exactly what was asked for, nothing more to load

                        if (!allLoadedTrips.isEmpty()) {
                            adapter.updateTrips(allLoadedTrips);
                            hideErrorBanner();
                        } else {
                            adapter.updateTrips(new ArrayList<>());
                            showErrorBanner("No trips found for this period.", null);
                        }
                    } else {
                        Log.w("TripHistory", "fetchTrips failed, code " + response.code());
                        showErrorBanner("Couldn't load trips (code " + response.code() + ")", TripHistoryActivity.this::fetchTrips);
                    }
                } catch (Exception e) {
                    Log.e("TripHistory", "fetchTrips parse error", e);
                    showErrorBanner("Something went wrong loading trips.", TripHistoryActivity.this::fetchTrips);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("TripHistory", "fetchTrips network error", t);
                showErrorBanner("Network error — check your connection.", TripHistoryActivity.this::fetchTrips);
            }
        });
    }

    // Extends the window another LOAD_MORE_CHUNK_DAYS further back and
    // fetches just that smaller slice, appending to what's already shown
    // -- never re-fetches the whole accumulated range. A failure here
    // shows a quiet Toast rather than the full-screen error banner, since
    // the trips already on screen are still valid and shouldn't be hidden
    // by a failed "load more" attempt.
    private void loadOlderTrips() {
        if (rangeFrom == null) return;
        isLoadingMore = true;

        Date newTo = new Date(rangeFrom.getTime() - 1000); // just before the current window starts
        Calendar cal = Calendar.getInstance();
        cal.setTime(rangeFrom);
        cal.add(Calendar.DAY_OF_YEAR, -LOAD_MORE_CHUNK_DAYS);
        Date newFrom = cal.getTime();

        String fromIso = toIsoUtc(newFrom);
        String toIso = toIsoUtc(newTo);

        trackingApi.getTripsSummary(selectedVehicleId, fromIso, toIso).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                isLoadingMore = false;
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        TripsReportResponse report = extractObject(body.string(), TripsReportResponse.class);
                        List<TripSummary> olderTrips = report != null ? report.getTrips() : null;

                        rangeFrom = newFrom; // window only advances after a successful fetch

                        if (olderTrips != null && !olderTrips.isEmpty()) {
                            allLoadedTrips.addAll(olderTrips);
                            adapter.updateTrips(allLoadedTrips);
                        }
                        // An empty chunk doesn't mean "no more ever" -- an
                        // idle 30-day stretch is normal. Keep hasMoreToLoad
                        // true and let the next scroll try the next chunk.
                    } else {
                        Log.w("TripHistory", "loadOlderTrips failed, code " + response.code());
                        Toast.makeText(TripHistoryActivity.this, "Couldn't load older trips.", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e("TripHistory", "loadOlderTrips parse error", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                isLoadingMore = false;
                Log.e("TripHistory", "loadOlderTrips network error", t);
                Toast.makeText(TripHistoryActivity.this, "Network error \u2014 couldn't load older trips.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openTripDetail(TripSummary trip) {
        if (selectedVehicleId == null) return;
        Intent intent = new Intent(TripHistoryActivity.this, TripDetailActivity.class);
        intent.putExtra(TripDetailActivity.EXTRA_VEHICLE_ID, selectedVehicleId);
        intent.putExtra(TripDetailActivity.EXTRA_FROM_ISO, trip.getStartTime());
        intent.putExtra(TripDetailActivity.EXTRA_TO_ISO, trip.getEndTime());
        intent.putExtra(TripDetailActivity.EXTRA_VEHICLE_NAME, selectedVehicleName);
        intent.putExtra(TripDetailActivity.EXTRA_DISTANCE_KM, trip.getDistanceKm());
        intent.putExtra(TripDetailActivity.EXTRA_DURATION_MIN, trip.getDurationMinutes());
        intent.putExtra(TripDetailActivity.EXTRA_MAX_SPEED, trip.getMaxSpeed());
        intent.putExtra(TripDetailActivity.EXTRA_AVG_SPEED, trip.getAvgSpeed());
        startActivity(intent);
    }

    private void showErrorBanner(String message, Runnable retryAction) {
        if (errorBanner == null) return;
        errorBanner.setVisibility(View.VISIBLE);
        if (tvErrorBannerMessage != null) tvErrorBannerMessage.setText(message);
        if (tvErrorBannerRetry != null) {
            tvErrorBannerRetry.setVisibility(retryAction != null ? View.VISIBLE : View.GONE);
        }
        // The retry click listener itself is wired once in initViews() to
        // fetchTrips() directly, since that's the only retry action this
        // screen ever needs -- no need to re-wire a different Runnable per
        // call the way the old per-message retryAction parameter implied.
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
                runOnUiThread(() -> showErrorBanner("No internet connection.", TripHistoryActivity.this::fetchTrips));
            }

            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(TripHistoryActivity.this::fetchTrips);
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

    private String toIsoUtc(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }

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
            Log.e("TripHistory", "extractObject error", e);
            return null;
        }
    }
}