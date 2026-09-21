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
import com.example.letstracklanka.data.model.AlertReportResponse;
import com.example.letstracklanka.data.model.AlertResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.main.AlertAdapter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Alert Report -- report generation feature. Distinct from AlertsActivity
 * (the drawer's live, unranged notification feed): this requires a
 * specific vehicle + date range (picked via ReportFilterBottomSheet) and
 * shows a per-type count summary on top of the full list for that window.
 * Backed by GET api/Alerts/report, not GET api/Alerts.
 *
 * Reuses AlertAdapter as-is for the list (day-grouped, same visual
 * language as the live feed) rather than building a parallel row layout --
 * a report reads exactly like the feed, just bounded to a chosen window.
 * Taps are intentionally inert here (see onAlertClicked below): a report
 * is a read-only snapshot, not a place to mutate read/unread state as a
 * side effect of looking at it.
 */
public class AlertReportActivity extends AppCompatActivity {

    public static final String EXTRA_VEHICLE_ID = "extra_vehicle_id";
    public static final String EXTRA_VEHICLE_NAME = "extra_vehicle_name";
    public static final String EXTRA_FROM_MILLIS = "extra_from_millis";
    public static final String EXTRA_TO_MILLIS = "extra_to_millis";

    private ApiService mainApiService;
    private String vehicleId;
    private long fromMillis;
    private long toMillis;

    private TextView tvVehicleName, tvRange, tvSummary;
    private TextView tvErrorBannerMessage, tvErrorBannerRetry;
    private View errorBanner, progressBar, layoutEmptyState;
    private RecyclerView rvAlerts;
    private AlertAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert_report);

        mainApiService = ApiClient.getClient().create(ApiService.class);

        vehicleId = getIntent().getStringExtra(EXTRA_VEHICLE_ID);
        String vehicleName = getIntent().getStringExtra(EXTRA_VEHICLE_NAME);
        fromMillis = getIntent().getLongExtra(EXTRA_FROM_MILLIS, -1);
        toMillis = getIntent().getLongExtra(EXTRA_TO_MILLIS, -1);

        initViews();

        if (tvVehicleName != null && vehicleName != null && !vehicleName.trim().isEmpty()) {
            tvVehicleName.setText("Alert Report — " + vehicleName);
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
        View btnBack = findViewById(R.id.btnBackAlertReport);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        tvVehicleName = findViewById(R.id.tvAlertReportVehicleName);
        tvRange = findViewById(R.id.tvAlertReportRange);
        tvSummary = findViewById(R.id.tvAlertReportSummary);
        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        tvErrorBannerRetry = findViewById(R.id.tvErrorBannerRetry);
        progressBar = findViewById(R.id.progressAlertReport);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        rvAlerts = findViewById(R.id.rvAlertReport);

        if (tvErrorBannerRetry != null) tvErrorBannerRetry.setOnClickListener(v -> fetchReport());

        rvAlerts.setLayoutManager(new LinearLayoutManager(this));
        // No-op click listener -- see class doc: a report doesn't mutate
        // read/unread state.
        adapter = new AlertAdapter(new ArrayList<>(), alert -> { });
        rvAlerts.setAdapter(adapter);
    }

    private void fetchReport() {
        hideErrorBanner();
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);

        String fromIso = toIsoUtc(fromMillis);
        String toIso = toIsoUtc(toMillis);

        mainApiService.getAlertReport(vehicleId, fromIso, toIso).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        AlertReportResponse report = extractObject(body.string());
                        if (report != null) {
                            hideErrorBanner();
                            renderReport(report);
                        } else {
                            showErrorBanner("Couldn't load this report.", AlertReportActivity.this::fetchReport);
                        }
                    } else {
                        Log.w("AlertReport", "fetchReport failed, code " + response.code());
                        showErrorBanner("Couldn't load this report (code " + response.code() + ")", AlertReportActivity.this::fetchReport);
                    }
                } catch (Exception e) {
                    Log.e("AlertReport", "fetchReport parse error", e);
                    showErrorBanner("Something went wrong loading this report.", AlertReportActivity.this::fetchReport);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                Log.e("AlertReport", "fetchReport network error", t);
                showErrorBanner("Network error — check your connection.", AlertReportActivity.this::fetchReport);
            }
        });
    }

    private void renderReport(AlertReportResponse report) {
        List<AlertResponse> alerts = report.getAlerts();
        if (alerts == null || alerts.isEmpty()) {
            if (rvAlerts != null) rvAlerts.setVisibility(View.GONE);
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
            if (tvSummary != null) tvSummary.setText("0 alerts in this period.");
            return;
        }

        if (rvAlerts != null) rvAlerts.setVisibility(View.VISIBLE);
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
        adapter.updateAlerts(alerts);

        if (tvSummary != null) tvSummary.setText(buildSummaryLine(report));
    }

    // "3 total — Overspeed: 2, Low Battery: 1" -- a compact, honest
    // count summary rather than a chart, matching how tvOverspeedSummary
    // already does this for VehicleStats elsewhere in the app.
    private String buildSummaryLine(AlertReportResponse report) {
        Map<String, Integer> counts = report.getCountsByType();
        StringBuilder sb = new StringBuilder();
        sb.append(report.getTotalCount()).append(report.getTotalCount() == 1 ? " alert" : " alerts").append(" total");

        if (counts != null && !counts.isEmpty()) {
            // LinkedHashMap preserves the server's own insertion order, but
            // Gson deserializes a JSON object into a plain HashMap by
            // default -- sort by descending count instead so the breakdown
            // reads highest-first regardless of map implementation.
            Map<String, Integer> sorted = new LinkedHashMap<>();
            counts.entrySet().stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .forEach(e -> sorted.put(e.getKey(), e.getValue()));

            sb.append(" — ");
            boolean first = true;
            for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(formatAlertType(entry.getKey())).append(": ").append(entry.getValue());
                first = false;
            }
        }
        return sb.toString();
    }

    private String formatAlertType(String rawType) {
        if (rawType == null) return "Alert";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rawType.length(); i++) {
            char c = rawType.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) sb.append(' ');
            sb.append(c);
        }
        return sb.toString();
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

    private AlertReportResponse extractObject(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), AlertReportResponse.class);
            }
            return null;
        } catch (Exception e) {
            Log.e("AlertReport", "extractObject error", e);
            return null;
        }
    }
}