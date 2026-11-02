package com.example.letstracklanka.ui.stats;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.model.VehicleStatsResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.auth.AuthUi;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.VehicleLoader;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Compare 2 or 3 vehicles side by side (same idea as the Fleet portal's /stats/compare). It reuses the
 * per-vehicle stats endpoint the Stats screen already uses, one call per picked vehicle, so the server's
 * ownership checks apply to every call and there is no new data path.
 *
 * Abuse limits: at most 3 vehicles, picks are debounced by 400 ms (tapping chips quickly sends one round of
 * calls, not one per tap), and an answer from an out-of-date selection is thrown away.
 */
public class StatsCompareActivity extends AppCompatActivity {

    private static final String TAG = "StatsCompare";
    private static final int MAX_VEHICLES = 3;

    private ApiService api;
    private final List<VehicleResponse> vehicles = new ArrayList<>();
    private final List<String> picked = new ArrayList<>();
    private String period = "week";
    private int serial = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable loadRunnable = this::loadStats;

    private ChipGroup chipsVehicles;
    private ProgressBar progress;
    private TextView tvMessage;
    private LinearLayout table;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats_compare);
        AuthUi.edgeToEdge(this, findViewById(R.id.main));
        api = ApiClient.getClient().create(ApiService.class);

        ((TextView) findViewById(R.id.tvHeaderTitle)).setText(R.string.cmp_title);
        findViewById(R.id.btnHeaderBack).setOnClickListener(v -> finish());

        chipsVehicles = findViewById(R.id.chipsVehicles);
        progress = findViewById(R.id.progress);
        tvMessage = findViewById(R.id.tvMessage);
        table = findViewById(R.id.tableCompare);

        ChipGroup periods = findViewById(R.id.chipsPeriod);
        periods.setOnCheckedStateChangeListener((g, ids) -> {
            period = ids.contains(R.id.chipToday) ? "today" : ids.contains(R.id.chipMonth) ? "month" : "week";
            scheduleLoad();
        });

        showMessage(getString(R.string.freq_loading_vehicles));
        VehicleLoader.load(new VehicleLoader.Callback2() {
            @Override public void onLoaded(List<VehicleResponse> loaded) {
                if (isFinishing() || isDestroyed()) return;
                vehicles.clear();
                vehicles.addAll(loaded);
                if (vehicles.size() < 2) { showMessage(getString(R.string.cmp_need_two)); return; }
                buildChips();
                showMessage(getString(R.string.cmp_pick_prompt));
            }

            @Override public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                showMessage(message);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(loadRunnable);
    }

    private void buildChips() {
        chipsVehicles.removeAllViews();
        for (VehicleResponse v : vehicles) {
            Chip chip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
            chip.setText(v.getVehicleNumber());
            chip.setCheckable(true);
            chip.setCheckedIconVisible(true);
            chip.setOnCheckedChangeListener((button, checked) -> {
                String id = v.getVehicleId();
                if (checked) {
                    if (picked.size() >= MAX_VEHICLES) {
                        button.setChecked(false);
                        showMessage(getString(R.string.cmp_max, MAX_VEHICLES));
                        return;
                    }
                    if (!picked.contains(id)) picked.add(id);
                } else {
                    picked.remove(id);
                }
                scheduleLoad();
            });
            chipsVehicles.addView(chip);
        }
    }

    private void scheduleLoad() {
        handler.removeCallbacks(loadRunnable);
        handler.postDelayed(loadRunnable, 400);
    }

    private void loadStats() {
        final int mySerial = ++serial;
        table.setVisibility(View.GONE);
        if (picked.size() < 2) {
            progress.setVisibility(View.GONE);
            showMessage(getString(R.string.cmp_pick_prompt));
            return;
        }
        tvMessage.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);

        final List<String> ids = new ArrayList<>(picked);
        final VehicleStatsResponse[] results = new VehicleStatsResponse[ids.size()];
        final int[] pending = {ids.size()};
        final boolean[] failed = {false};

        for (int i = 0; i < ids.size(); i++) {
            final int index = i;
            api.getVehicleStats(ids.get(i), period).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    try (ResponseBody body = response.body()) {
                        if (response.isSuccessful() && body != null) {
                            results[index] = ApiJson.object(body.string(), VehicleStatsResponse.class);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "parse error", e);
                    }
                    if (results[index] == null) failed[0] = true;
                    done();
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    failed[0] = true;
                    done();
                }

                private void done() {
                    if (--pending[0] > 0) return;
                    if (mySerial != serial || isFinishing() || isDestroyed()) return;
                    progress.setVisibility(View.GONE);
                    if (failed[0]) { showMessage(getString(R.string.cmp_failed)); return; }
                    render(ids, results);
                }
            });
        }
    }

    // ---- table --------------------------------------------------------------------------------

    private interface Metric { double of(VehicleStatsResponse s); }

    private void render(List<String> ids, VehicleStatsResponse[] stats) {
        table.removeAllViews();

        List<String> plates = new ArrayList<>();
        for (String id : ids) plates.add(plateOf(id));
        table.addView(row(getString(R.string.cmp_metric), plates, -1, true));

        addMetric(stats, plates, R.string.cmp_distance, s -> s.getTotalDistanceKm(), "km");
        addMetric(stats, plates, R.string.cmp_trips, s -> s.getTotalTripCount(), "");
        addMetric(stats, plates, R.string.cmp_stops, s -> s.getTotalStopCount(), "");
        addMetric(stats, plates, R.string.cmp_driving, s -> s.getTotalDrivingMinutes(), "min");
        addMetric(stats, plates, R.string.cmp_idle, s -> s.getTotalIdleMinutes(), "min");
        addMetric(stats, plates, R.string.cmp_avg_speed, s -> s.getAverageSpeed(), "km/h");
        addMetric(stats, plates, R.string.cmp_max_speed, s -> s.getMaxSpeed(), "km/h");
        addMetric(stats, plates, R.string.cmp_overspeed, s -> s.getOverspeedIncidentCount(), "");

        table.setVisibility(View.VISIBLE);
    }

    private void addMetric(VehicleStatsResponse[] stats, List<String> plates, int labelRes, Metric m, String unit) {
        double best = -1;
        int bestIndex = -1;
        List<String> values = new ArrayList<>();
        for (int i = 0; i < stats.length; i++) {
            double v = m.of(stats[i]);
            values.add(format(v, unit));
            if (v > best) { best = v; bestIndex = i; }
        }
        // Highlight the highest value only when it is a real difference.
        boolean allSame = true;
        for (VehicleStatsResponse s : stats) if (m.of(s) != m.of(stats[0])) allSame = false;
        table.addView(row(getString(labelRes), values, allSame || best <= 0 ? -1 : bestIndex, false));
    }

    private View row(String label, List<String> cells, int highlight, boolean header) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(10);
        row.setPadding(pad, pad, pad, pad);
        if (!header) {
            row.setBackgroundResource(android.R.color.transparent);
        }

        TextView l = cell(label, 1.5f, header, false);
        l.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.addView(l);
        for (int i = 0; i < cells.size(); i++) {
            row.addView(cell(cells.get(i), 1f, header, i == highlight));
        }
        return row;
    }

    private TextView cell(String text, float weight, boolean header, boolean highlight) {
        TextView t = new TextView(this);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(header ? 13 : 14);
        t.setMinHeight(dp(36));
        t.setGravity(Gravity.CENTER);
        t.setTextColor(ContextCompat.getColor(this,
                header || highlight ? R.color.brand_primary : R.color.text_primary));
        t.setTypeface(null, header || highlight ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private String plateOf(String vehicleId) {
        for (VehicleResponse v : vehicles) if (vehicleId.equals(v.getVehicleId())) return v.getVehicleNumber();
        return "-";
    }

    private static String format(double v, String unit) {
        String n = v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.1f", v);
        return unit.isEmpty() ? n : n + " " + unit;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void showMessage(String text) {
        tvMessage.setText(text);
        tvMessage.setVisibility(View.VISIBLE);
    }
}
