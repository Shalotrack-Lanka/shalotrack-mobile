package com.example.letstracklanka.ui.vehicles;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.DailyStatResponse;
import com.example.letstracklanka.data.model.VehicleStatsResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Value screen -- rebuilt to match the real Letstrack app's own Statistics
 * screen (reviewed directly via reference screenshots, not guessed at):
 * a period selector, a 2x3 grid of tappable metric cards (one always
 * selected/highlighted), and a per-day bar chart for whichever metric is
 * currently selected. Chart is built entirely from plain Views with
 * programmatically-set heights -- no charting library dependency added.
 */
public class ValueActivity extends AppCompatActivity {

    public static final String EXTRA_VEHICLE_ID = "extra_vehicle_id";

    private enum Metric { IGNITION_ON, STOPS, TRIPS, AVG_SPEED, DISTANCE, MAX_SPEED }

    private ApiService mainApiService;
    private String vehicleId;
    private String currentPeriod = "today";
    private Metric selectedMetric = Metric.DISTANCE; // matches Letstrack's own default in the reference screenshot
    private VehicleStatsResponse currentStats;

    private View errorBanner;
    private TextView tvErrorBannerMessage;
    private NestedScrollView scrollContent;
    private ProgressBar progressValue;
    private View layoutEmptyState;

    private LinearLayout btnPeriodSelector;
    private TextView tvPeriodLabel;
    private TextView tvOverspeedSummary, tvChartTitle;
    private LinearLayout chartBarsContainer;
    private LinearLayout yAxisLabels;
    private LinearLayout gridlinesContainer;

    private LinearLayout cardIgnitionOn, cardStops, cardTrips, cardAvgSpeed, cardDistance, cardMaxSpeed;
    private TextView tvIgnitionOnValue, tvStopsValue, tvTripsValue, tvAvgSpeedValue, tvDistanceValue, tvMaxSpeedValue;
    private android.widget.ImageView ivIgnitionOn, ivStops, ivTrips, ivAvgSpeed, ivDistance, ivMaxSpeed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_value);

        mainApiService = ApiClient.getClient().create(ApiService.class);
        vehicleId = getIntent().getStringExtra(EXTRA_VEHICLE_ID);

        initViews();

        if (vehicleId == null || vehicleId.isEmpty()) {
            showError("No vehicle selected.");
            return;
        }
        fetchStats();
    }

    private void initViews() {
        View btnBack = findViewById(R.id.btnBackValue);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        scrollContent = findViewById(R.id.scrollValueContent);
        progressValue = findViewById(R.id.progressValue);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);

        btnPeriodSelector = findViewById(R.id.btnPeriodSelector);
        tvPeriodLabel = findViewById(R.id.tvPeriodLabel);
        tvOverspeedSummary = findViewById(R.id.tvOverspeedSummary);
        tvChartTitle = findViewById(R.id.tvChartTitle);
        chartBarsContainer = findViewById(R.id.chartBarsContainer);

        cardIgnitionOn = findViewById(R.id.cardIgnitionOn);
        cardStops = findViewById(R.id.cardStops);
        cardTrips = findViewById(R.id.cardTrips);
        cardAvgSpeed = findViewById(R.id.cardAvgSpeed);
        cardDistance = findViewById(R.id.cardDistance);
        cardMaxSpeed = findViewById(R.id.cardMaxSpeed);

        tvIgnitionOnValue = findViewById(R.id.tvIgnitionOnValue);
        tvStopsValue = findViewById(R.id.tvStopsValue);
        tvTripsValue = findViewById(R.id.tvTripsValue);
        tvAvgSpeedValue = findViewById(R.id.tvAvgSpeedValue);
        tvDistanceValue = findViewById(R.id.tvDistanceValue);
        tvMaxSpeedValue = findViewById(R.id.tvMaxSpeedValue);

        ivIgnitionOn = findViewById(R.id.ivIgnitionOn);
        ivStops = findViewById(R.id.ivStops);
        ivTrips = findViewById(R.id.ivTrips);
        ivAvgSpeed = findViewById(R.id.ivAvgSpeed);
        ivDistance = findViewById(R.id.ivDistance);
        ivMaxSpeed = findViewById(R.id.ivMaxSpeed);

        yAxisLabels = findViewById(R.id.yAxisLabels);
        gridlinesContainer = findViewById(R.id.gridlinesContainer);

        if (btnPeriodSelector != null) btnPeriodSelector.setOnClickListener(this::showPeriodMenu);

        if (cardIgnitionOn != null) cardIgnitionOn.setOnClickListener(v -> selectMetric(Metric.IGNITION_ON));
        if (cardStops != null) cardStops.setOnClickListener(v -> selectMetric(Metric.STOPS));
        if (cardTrips != null) cardTrips.setOnClickListener(v -> selectMetric(Metric.TRIPS));
        if (cardAvgSpeed != null) cardAvgSpeed.setOnClickListener(v -> selectMetric(Metric.AVG_SPEED));
        if (cardDistance != null) cardDistance.setOnClickListener(v -> selectMetric(Metric.DISTANCE));
        if (cardMaxSpeed != null) cardMaxSpeed.setOnClickListener(v -> selectMetric(Metric.MAX_SPEED));
    }

    private void showPeriodMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 0, 0, "Today");
        menu.getMenu().add(0, 1, 1, "This Week");
        menu.getMenu().add(0, 2, 2, "This Month");
        menu.getMenu().add(0, 3, 3, "All Time");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: currentPeriod = "today"; tvPeriodLabel.setText("Today"); break;
                case 1: currentPeriod = "week"; tvPeriodLabel.setText("This Week"); break;
                case 2: currentPeriod = "month"; tvPeriodLabel.setText("This Month"); break;
                case 3: currentPeriod = "all"; tvPeriodLabel.setText("All Time"); break;
                default: break;
            }
            fetchStats();
            return true;
        });
        menu.show();
    }

    private void fetchStats() {
        hideError();
        setLoading(true);

        mainApiService.getVehicleStats(vehicleId, currentPeriod).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                setLoading(false);
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        showError("Couldn't load stats (code " + response.code() + ")");
                        return;
                    }
                    VehicleStatsResponse stats = extractObject(body.string(), VehicleStatsResponse.class);
                    if (stats == null) {
                        showError("Couldn't load stats.");
                        return;
                    }
                    if (stats.getTotalTripCount() == 0 && stats.getTotalStopCount() == 0) {
                        showEmptyState();
                        return;
                    }
                    currentStats = stats;
                    displayStats(stats);
                } catch (Exception e) {
                    Log.e("ValueActivity", "fetchStats parse error", e);
                    showError("Something went wrong loading stats.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                setLoading(false);
                Log.e("ValueActivity", "fetchStats network error", t);
                showError("Network error \u2014 check your connection.");
            }
        });
    }

    private void displayStats(VehicleStatsResponse stats) {
        if (scrollContent != null) scrollContent.setVisibility(View.VISIBLE);
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);

        if (tvIgnitionOnValue != null) tvIgnitionOnValue.setText(formatMinutes(stats.getTotalIgnitionOnMinutes()));
        if (tvStopsValue != null) tvStopsValue.setText(String.valueOf(stats.getTotalStopCount()));
        if (tvTripsValue != null) tvTripsValue.setText(String.valueOf(stats.getTotalTripCount()));
        if (tvAvgSpeedValue != null) tvAvgSpeedValue.setText(String.format(Locale.US, "%.0fkm/h", stats.getAverageSpeed()));
        if (tvDistanceValue != null) tvDistanceValue.setText(String.format(Locale.US, "%.1fkm", stats.getTotalDistanceKm()));
        if (tvMaxSpeedValue != null) tvMaxSpeedValue.setText(String.format(Locale.US, "%.0fkm/h", stats.getMaxSpeed()));

        if (tvOverspeedSummary != null) {
            int count = stats.getOverspeedIncidentCount();
            tvOverspeedSummary.setText((count == 1 ? "1 overspeed incident" : count + " overspeed incidents") + " in this period");
        }

        applyCardSelectionStyles();
        renderChart(stats);
    }

    private void selectMetric(Metric metric) {
        selectedMetric = metric;
        applyCardSelectionStyles();
        if (currentStats != null) renderChart(currentStats);
    }

    // Resets all six cards to the unselected style, then highlights only
    // the currently selected one -- matching Letstrack's own "Distance"
    // card being solid blue while the rest stay neutral. Each card is
    // explicitly paired with its own icon (not accessed by child index),
    // so background drawable and icon tint always match correctly.
    private void applyCardSelectionStyles() {
        styleCard(cardIgnitionOn, ivIgnitionOn, selectedMetric == Metric.IGNITION_ON);
        styleCard(cardStops, ivStops, selectedMetric == Metric.STOPS);
        styleCard(cardTrips, ivTrips, selectedMetric == Metric.TRIPS);
        styleCard(cardAvgSpeed, ivAvgSpeed, selectedMetric == Metric.AVG_SPEED);
        styleCard(cardDistance, ivDistance, selectedMetric == Metric.DISTANCE);
        styleCard(cardMaxSpeed, ivMaxSpeed, selectedMetric == Metric.MAX_SPEED);
    }

    private void styleCard(LinearLayout card, android.widget.ImageView icon, boolean selected) {
        if (card == null) return;
        card.setBackgroundResource(selected ? R.drawable.bg_stat_card_selected : R.drawable.bg_stat_card);

        int textColor = selected ? Color.WHITE : ContextCompat.getColor(this, R.color.text_primary);
        int labelColor = selected ? Color.WHITE : ContextCompat.getColor(this, R.color.text_secondary);
        int iconColor = selected ? Color.WHITE : ContextCompat.getColor(this, R.color.brand_accent);

        if (icon != null) icon.setColorFilter(iconColor);

        for (int i = 0; i < card.getChildCount(); i++) {
            View child = card.getChildAt(i);
            if (child instanceof TextView) {
                // Label is always the first TextView (index 1, after the
                // icon at index 0); value is the second (index 2).
                boolean isLabel = i == 1;
                ((TextView) child).setTextColor(isLabel ? labelColor : textColor);
            }
        }
    }

    // Builds the bar chart entirely from plain Views -- one vertical
    // LinearLayout column per day (value label, a View whose height is set
    // programmatically as a proportion of the max value that day, and a
    // date label), added inside a HorizontalScrollView's container so a
    // full month of days scrolls rather than being squeezed unreadably
    // narrow.
    private void renderChart(VehicleStatsResponse stats) {
        List<DailyStatResponse> daily = stats.getDailyBreakdown();
        if (chartBarsContainer == null || daily == null || daily.isEmpty()) return;

        chartBarsContainer.removeAllViews();
        if (tvChartTitle != null) tvChartTitle.setText(chartTitleFor(selectedMetric, stats.getPeriod()));

        double maxValue = 0;
        for (DailyStatResponse day : daily) {
            maxValue = Math.max(maxValue, valueFor(day, selectedMetric));
        }
        if (maxValue <= 0) maxValue = 1; // avoid dividing by zero when every day is empty

        renderYAxis(maxValue);
        renderGridlines();

        int maxBarHeightPx = (int) (140 * getResources().getDisplayMetrics().density);
        int barWidthPx = (int) (36 * getResources().getDisplayMetrics().density);
        int barMarginPx = (int) (6 * getResources().getDisplayMetrics().density);

        SimpleDateFormat isoParser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat dayLabelFormat = new SimpleDateFormat("dd/MM", Locale.US);

        for (DailyStatResponse day : daily) {
            double value = valueFor(day, selectedMetric);
            int barHeightPx = (int) Math.max(2, (value / maxValue) * maxBarHeightPx);

            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                    barWidthPx + barMarginPx * 2, LinearLayout.LayoutParams.MATCH_PARENT);
            column.setLayoutParams(columnParams);

            TextView valueLabel = new TextView(this);
            valueLabel.setText(formatValueLabel(value, selectedMetric));
            valueLabel.setTextSize(10);
            valueLabel.setGravity(Gravity.CENTER);
            valueLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            column.addView(valueLabel);

            View bar = new View(this);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(barWidthPx, barHeightPx);
            barParams.setMargins(barMarginPx, 4, barMarginPx, 4);
            bar.setLayoutParams(barParams);
            bar.setBackgroundColor(ContextCompat.getColor(this, R.color.brand_accent));
            column.addView(bar);

            String dateLabel = day.getDate();
            try {
                if (day.getDate() != null) {
                    dateLabel = dayLabelFormat.format(isoParser.parse(day.getDate().substring(0, 10)));
                }
            } catch (ParseException ignored) { }
            TextView dateText = new TextView(this);
            dateText.setText(dateLabel);
            dateText.setTextSize(10);
            dateText.setGravity(Gravity.CENTER);
            dateText.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            column.addView(dateText);

            chartBarsContainer.addView(column);
        }
    }

    // Real Y-axis scale, matching the actual Letstrack reference rather
    // than a bare bar with no context. 5 evenly-spaced marks from the max
    // value down to 0, top-aligned within each equal-weight slot so they
    // line up with the bar heights below.
    private void renderYAxis(double maxValue) {
        if (yAxisLabels == null) return;
        yAxisLabels.removeAllViews();

        int steps = 4;
        for (int i = 0; i <= steps; i++) {
            double value = maxValue - (maxValue / steps) * i;
            TextView label = new TextView(this);
            label.setText(formatValueLabel(value, selectedMetric));
            label.setTextSize(9);
            label.setGravity(Gravity.END | Gravity.TOP);
            label.setPadding(0, 0, (int) (6 * getResources().getDisplayMetrics().density), 0);
            label.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            label.setLayoutParams(params);
            yAxisLabels.addView(label);
        }
    }

    // Real horizontal gridlines behind the bars, aligned to the exact
    // same 5-level spacing as the Y-axis labels (drawn behind the bars in
    // the FrameLayout, since gridlinesContainer is added before the
    // scrolling bars in the layout).
    private void renderGridlines() {
        if (gridlinesContainer == null) return;
        gridlinesContainer.removeAllViews();

        int steps = 4;
        for (int i = 0; i <= steps; i++) {
            LinearLayout slot = new LinearLayout(this);
            LinearLayout.LayoutParams slotParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            slot.setLayoutParams(slotParams);
            slot.setOrientation(LinearLayout.VERTICAL);

            View line = new View(this);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            line.setLayoutParams(lineParams);
            line.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_stroke));
            slot.addView(line);

            gridlinesContainer.addView(slot);
        }
    }

    private double valueFor(DailyStatResponse day, Metric metric) {
        switch (metric) {
            case IGNITION_ON: return day.getIgnitionOnMinutes();
            case STOPS: return day.getStopCount();
            case TRIPS: return day.getTripCount();
            case AVG_SPEED: return day.getAverageSpeed();
            case MAX_SPEED: return day.getMaxSpeed();
            case DISTANCE:
            default: return day.getDistanceKm();
        }
    }

    private String formatValueLabel(double value, Metric metric) {
        switch (metric) {
            case IGNITION_ON: return formatMinutes(value);
            case STOPS:
            case TRIPS: return String.valueOf((int) value);
            case AVG_SPEED:
            case MAX_SPEED: return String.format(Locale.US, "%.0f", value);
            case DISTANCE:
            default: return String.format(Locale.US, "%.1f", value);
        }
    }

    private String chartTitleFor(Metric metric, String period) {
        String periodLabel = period != null && !period.isEmpty()
                ? period.substring(0, 1).toUpperCase(Locale.US) + period.substring(1) : "";
        String metricLabel;
        switch (metric) {
            case IGNITION_ON: metricLabel = "Ignition On Time"; break;
            case STOPS: metricLabel = "Stops"; break;
            case TRIPS: metricLabel = "Trips"; break;
            case AVG_SPEED: metricLabel = "Average Speed (km/h)"; break;
            case MAX_SPEED: metricLabel = "Max Speed (km/h)"; break;
            case DISTANCE:
            default: metricLabel = "Total Distance (km)"; break;
        }
        return metricLabel + " \u2014 " + periodLabel;
    }

    private String formatMinutes(double totalMinutes) {
        int hours = (int) (totalMinutes / 60);
        int minutes = (int) (totalMinutes % 60);
        if (hours == 0) return minutes + "m";
        return hours + "h " + minutes + "m";
    }

    private void showEmptyState() {
        if (scrollContent != null) scrollContent.setVisibility(View.GONE);
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        if (progressValue != null) progressValue.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        if (errorBanner == null) return;
        errorBanner.setVisibility(View.VISIBLE);
        if (tvErrorBannerMessage != null) tvErrorBannerMessage.setText(message);
    }

    private void hideError() {
        if (errorBanner != null) errorBanner.setVisibility(View.GONE);
    }

    private <T> T extractObject(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), clazz);
            }
        } catch (Exception e) {
            Log.e("ValueActivity", "extractObject error", e);
        }
        return null;
    }
}