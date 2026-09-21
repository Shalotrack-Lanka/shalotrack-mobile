package com.example.letstracklanka.ui.main.reports;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.VehicleStatsResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * KM Report -- report generation feature. Previously this card just opened
 * ValueActivity (the live Value/Stats screen) with a fixed range, but that
 * screen is a single Today/Week/Month/All snapshot with one chart metric at
 * a time -- it has no per-day itemized view, which is what an actual
 * exportable/reviewable report needs. This is a dedicated screen instead:
 * period totals up top (same numbers ValueActivity shows), plus a full,
 * scrollable day-by-day breakdown below so every day in the picked range
 * (whether that's a 3-day span or the full 90-day cap) can be inspected
 * individually rather than only as one aggregated chart bar.
 *
 * Reuses the EXISTING GetStatsForRangeAsync endpoint (built for ValueActivity's
 * report mode) -- that response already returns a DailyBreakdown entry per
 * calendar day in the range (Sri Lanka local time), which is exactly the
 * per-day detail this screen needed. No new backend surface, no new cost.
 *
 * ValueActivity's own EXTRA_REPORT_FROM_MILLIS/EXTRA_REPORT_TO_MILLIS report
 * mode is now unused dead code following this change (nothing launches it in
 * report mode anymore) -- left in place rather than ripped out in the same
 * change that replaces its only caller; worth a follow-up cleanup pass.
 */
public class KmReportActivity extends AppCompatActivity {

    public static final String EXTRA_VEHICLE_ID = "extra_vehicle_id";
    public static final String EXTRA_VEHICLE_NAME = "extra_vehicle_name";
    public static final String EXTRA_FROM_MILLIS = "extra_from_millis";
    public static final String EXTRA_TO_MILLIS = "extra_to_millis";

    private ApiService mainApiService;
    private String vehicleId;
    private long fromMillis;
    private long toMillis;

    private TextView tvVehicleName, tvRange;
    private TextView tvDistanceValue, tvTripsValue, tvStopsValue;
    private TextView tvAvgSpeedValue, tvMaxSpeedValue, tvIgnitionOnValue;
    private TextView tvOverspeedSummary;
    private TextView tvErrorBannerMessage, tvErrorBannerRetry;
    private View errorBanner, progressBar, layoutEmptyState, layoutSummary;
    private RecyclerView rvDailyBreakdown;
    private KmReportDailyAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_km_report);

        mainApiService = ApiClient.getClient().create(ApiService.class);

        vehicleId = getIntent().getStringExtra(EXTRA_VEHICLE_ID);
        String vehicleName = getIntent().getStringExtra(EXTRA_VEHICLE_NAME);
        fromMillis = getIntent().getLongExtra(EXTRA_FROM_MILLIS, -1);
        toMillis = getIntent().getLongExtra(EXTRA_TO_MILLIS, -1);

        initViews();

        if (tvVehicleName != null && vehicleName != null && !vehicleName.trim().isEmpty()) {
            tvVehicleName.setText("KM Report — " + vehicleName);
        }

        SimpleDateFormat displayFmt = new SimpleDateFormat("d MMM yyyy", Locale.US);
        if (tvRange != null && fromMillis > 0 && toMillis > 0) {
            tvRange.setText(displayFmt.format(new java.util.Date(fromMillis))
                    + " – " + displayFmt.format(new java.util.Date(toMillis)));
        }

        if (vehicleId == null || fromMillis <= 0 || toMillis <= fromMillis) {
            showErrorBanner("Missing report parameters. Go back and try again.", null);
            return;
        }

        fetchReport();
    }

    private void initViews() {
        View btnBack = findViewById(R.id.btnBackKmReport);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        tvVehicleName = findViewById(R.id.tvKmReportVehicleName);
        tvRange = findViewById(R.id.tvKmReportRange);

        layoutSummary = findViewById(R.id.layoutKmReportSummary);
        tvDistanceValue = findViewById(R.id.tvKmDistanceValue);
        tvTripsValue = findViewById(R.id.tvKmTripsValue);
        tvStopsValue = findViewById(R.id.tvKmStopsValue);
        tvAvgSpeedValue = findViewById(R.id.tvKmAvgSpeedValue);
        tvMaxSpeedValue = findViewById(R.id.tvKmMaxSpeedValue);
        tvIgnitionOnValue = findViewById(R.id.tvKmIgnitionOnValue);
        tvOverspeedSummary = findViewById(R.id.tvKmOverspeedSummary);

        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        tvErrorBannerRetry = findViewById(R.id.tvErrorBannerRetry);
        progressBar = findViewById(R.id.progressKmReport);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        rvDailyBreakdown = findViewById(R.id.rvKmDailyBreakdown);

        if (tvErrorBannerRetry != null) tvErrorBannerRetry.setOnClickListener(v -> fetchReport());

        rvDailyBreakdown.setLayoutManager(new LinearLayoutManager(this));
        adapter = new KmReportDailyAdapter();
        rvDailyBreakdown.setAdapter(adapter);
    }

    private void fetchReport() {
        hideErrorBanner();
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
        if (layoutSummary != null) layoutSummary.setVisibility(View.GONE);
        if (rvDailyBreakdown != null) rvDailyBreakdown.setVisibility(View.GONE);

        String fromIso = toIsoUtc(fromMillis);
        String toIso = toIsoUtc(toMillis);

        mainApiService.getVehicleStatsForRange(vehicleId, fromIso, toIso).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        VehicleStatsResponse stats = extractObject(body.string());
                        renderReport(stats);
                    } else {
                        Log.w("KmReport", "fetchReport failed, code " + response.code());
                        showErrorBanner("Couldn't load this report (code " + response.code() + ")", KmReportActivity.this::fetchReport);
                    }
                } catch (Exception e) {
                    Log.e("KmReport", "fetchReport parse error", e);
                    showErrorBanner("Something went wrong loading this report.", KmReportActivity.this::fetchReport);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                Log.e("KmReport", "fetchReport network error", t);
                showErrorBanner("Network error — check your connection.", KmReportActivity.this::fetchReport);
            }
        });
    }

    private void renderReport(VehicleStatsResponse stats) {
        if (stats == null || (stats.getTotalTripCount() == 0 && stats.getTotalStopCount() == 0)) {
            if (layoutSummary != null) layoutSummary.setVisibility(View.GONE);
            if (rvDailyBreakdown != null) rvDailyBreakdown.setVisibility(View.GONE);
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        if (layoutSummary != null) layoutSummary.setVisibility(View.VISIBLE);
        if (rvDailyBreakdown != null) rvDailyBreakdown.setVisibility(View.VISIBLE);
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);

        if (tvDistanceValue != null) tvDistanceValue.setText(String.format(Locale.US, "%.1f km", stats.getTotalDistanceKm()));
        if (tvTripsValue != null) tvTripsValue.setText(String.valueOf(stats.getTotalTripCount()));
        if (tvStopsValue != null) tvStopsValue.setText(String.valueOf(stats.getTotalStopCount()));
        if (tvAvgSpeedValue != null) tvAvgSpeedValue.setText(String.format(Locale.US, "%.0f km/h", stats.getAverageSpeed()));
        if (tvMaxSpeedValue != null) tvMaxSpeedValue.setText(String.format(Locale.US, "%.0f km/h", stats.getMaxSpeed()));
        if (tvIgnitionOnValue != null) tvIgnitionOnValue.setText(formatMinutes(stats.getTotalIgnitionOnMinutes()));

        if (tvOverspeedSummary != null) {
            int count = stats.getOverspeedIncidentCount();
            tvOverspeedSummary.setText((count == 1 ? "1 overspeed incident" : count + " overspeed incidents") + " in this period");
        }

        // Most-recent-day-first, matching StopReportAdapter/TripHistoryActivity's
        // convention elsewhere in this app -- the API returns DailyBreakdown in
        // chronological (ascending) order for the chart use case ValueActivity
        // was built for, but a report list reads better newest-first.
        java.util.List<com.example.letstracklanka.data.model.DailyStatResponse> daily = stats.getDailyBreakdown();
        java.util.List<com.example.letstracklanka.data.model.DailyStatResponse> reversed = new java.util.ArrayList<>(daily != null ? daily : java.util.Collections.emptyList());
        java.util.Collections.reverse(reversed);
        adapter.updateDays(reversed);
    }

    private String formatMinutes(double totalMinutes) {
        int hours = (int) (totalMinutes / 60);
        int minutes = (int) (totalMinutes % 60);
        if (hours == 0) return minutes + "m";
        return hours + "h " + minutes + "m";
    }

    private String toIsoUtc(long epochMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new java.util.Date(epochMillis));
    }

    private void showErrorBanner(String message, Runnable retryAction) {
        if (errorBanner == null) return;
        if (tvErrorBannerMessage != null) tvErrorBannerMessage.setText(message);
        if (tvErrorBannerRetry != null) {
            tvErrorBannerRetry.setOnClickListener(retryAction != null ? v -> { hideErrorBanner(); retryAction.run(); } : null);
        }
        errorBanner.setVisibility(View.VISIBLE);
    }

    private void hideErrorBanner() {
        if (errorBanner != null) errorBanner.setVisibility(View.GONE);
    }

    private VehicleStatsResponse extractObject(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), VehicleStatsResponse.class);
            }
            return null;
        } catch (Exception e) {
            Log.e("KmReport", "extractObject error", e);
            return null;
        }
    }
}