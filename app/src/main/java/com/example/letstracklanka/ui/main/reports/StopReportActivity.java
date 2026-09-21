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
import com.example.letstracklanka.data.model.StopSummary;
import com.example.letstracklanka.data.model.TripsReportResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Stop Alert Report -- report generation feature. Deliberately reuses the
 * EXISTING GET api/GpsTracking/trips endpoint (ShaloTrackApi.getTripsSummary)
 * rather than adding a new backend endpoint: that response has always
 * included a "stops" array alongside "trips" (used server-side by
 * VehicleStatsService.BuildDailyBreakdown already), it was just never
 * deserialized on the Android side until TripsReportResponse.getStops()
 * was added for this feature. No new backend surface, no new cost.
 */
public class StopReportActivity extends AppCompatActivity {

    public static final String EXTRA_VEHICLE_ID = "extra_vehicle_id";
    public static final String EXTRA_VEHICLE_NAME = "extra_vehicle_name";
    public static final String EXTRA_FROM_MILLIS = "extra_from_millis";
    public static final String EXTRA_TO_MILLIS = "extra_to_millis";

    private ShaloTrackApi trackingApi;
    private String vehicleId;
    private long fromMillis;
    private long toMillis;

    private TextView tvVehicleName, tvRange, tvSummary;
    private TextView tvErrorBannerMessage, tvErrorBannerRetry;
    private View errorBanner, progressBar, layoutEmptyState;
    private RecyclerView rvStops;
    private StopReportAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stop_report);

        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);

        vehicleId = getIntent().getStringExtra(EXTRA_VEHICLE_ID);
        String vehicleName = getIntent().getStringExtra(EXTRA_VEHICLE_NAME);
        fromMillis = getIntent().getLongExtra(EXTRA_FROM_MILLIS, -1);
        toMillis = getIntent().getLongExtra(EXTRA_TO_MILLIS, -1);

        initViews();

        if (tvVehicleName != null && vehicleName != null && !vehicleName.trim().isEmpty()) {
            tvVehicleName.setText("Stop Alert Report — " + vehicleName);
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
        View btnBack = findViewById(R.id.btnBackStopReport);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        tvVehicleName = findViewById(R.id.tvStopReportVehicleName);
        tvRange = findViewById(R.id.tvStopReportRange);
        tvSummary = findViewById(R.id.tvStopReportSummary);
        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        tvErrorBannerRetry = findViewById(R.id.tvErrorBannerRetry);
        progressBar = findViewById(R.id.progressStopReport);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        rvStops = findViewById(R.id.rvStopReport);

        if (tvErrorBannerRetry != null) tvErrorBannerRetry.setOnClickListener(v -> fetchReport());

        rvStops.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StopReportAdapter(new ArrayList<>());
        rvStops.setAdapter(adapter);
    }

    private void fetchReport() {
        hideErrorBanner();
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);

        String fromIso = toIsoUtc(fromMillis);
        String toIso = toIsoUtc(toMillis);

        trackingApi.getTripsSummary(vehicleId, fromIso, toIso).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        TripsReportResponse report = extractObject(body.string());
                        List<StopSummary> stops = report != null ? report.getStops() : null;
                        renderReport(stops);
                    } else {
                        Log.w("StopReport", "fetchReport failed, code " + response.code());
                        showErrorBanner("Couldn't load this report (code " + response.code() + ")", StopReportActivity.this::fetchReport);
                    }
                } catch (Exception e) {
                    Log.e("StopReport", "fetchReport parse error", e);
                    showErrorBanner("Something went wrong loading this report.", StopReportActivity.this::fetchReport);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                Log.e("StopReport", "fetchReport network error", t);
                showErrorBanner("Network error — check your connection.", StopReportActivity.this::fetchReport);
            }
        });
    }

    private void renderReport(List<StopSummary> stops) {
        if (stops == null || stops.isEmpty()) {
            if (rvStops != null) rvStops.setVisibility(View.GONE);
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
            if (tvSummary != null) tvSummary.setText("0 stops in this period.");
            return;
        }

        if (rvStops != null) rvStops.setVisibility(View.VISIBLE);
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
        adapter.updateStops(stops);

        long ongoing = stops.stream().filter(StopSummary::isInProgress).count();
        String summary = stops.size() + (stops.size() == 1 ? " stop total" : " stops total");
        if (ongoing > 0) summary += " — " + ongoing + " ongoing";
        if (tvSummary != null) tvSummary.setText(summary);
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

    private TripsReportResponse extractObject(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), TripsReportResponse.class);
            }
            return null;
        } catch (Exception e) {
            Log.e("StopReport", "extractObject error", e);
            return null;
        }
    }
}