package com.example.letstracklanka.ui.history;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.TripSummary;
import com.example.letstracklanka.data.model.TripsReportResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
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

    private static final int DEFAULT_RANGE_DAYS = 7;

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
        adapter = new TripHistoryAdapter(this::openTripDetail);
        rvTripHistory.setAdapter(adapter);
    }

    private void setDefaultRange() {
        Calendar cal = Calendar.getInstance();
        rangeTo = cal.getTime();
        cal.add(Calendar.DAY_OF_YEAR, -DEFAULT_RANGE_DAYS);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        rangeFrom = cal.getTime();
    }

    private void showRangePicker() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now())
                .build();

        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Select date range")
                .setCalendarConstraints(constraints)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            rangeFrom = new Date(selection.first);
            Calendar endCal = Calendar.getInstance();
            endCal.setTimeInMillis(selection.second);
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);
            rangeTo = endCal.getTime();
            fetchTrips();
        });

        picker.show(getSupportFragmentManager(), "trip_history_date_range_picker");
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
                        if (trips != null && !trips.isEmpty()) {
                            adapter.updateTrips(trips);
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