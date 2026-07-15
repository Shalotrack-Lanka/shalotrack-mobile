package com.example.letstracklanka.ui.history;

import android.app.DatePickerDialog;
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
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.TripSummary;
import com.example.letstracklanka.data.model.TripsReportResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

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

public class TripHistoryActivity extends AppCompatActivity {

    private ApiService mainApiService;
    private ShaloTrackApi trackingApi;

    private String currentCustomerId = null;
    private String selectedVehicleId = null;

    private TextView tvRangeLabel, tvTripCount, tvStopCount, tvDistance, tvDrivingTime, tvEmptyState;
    private View chipToday, chipWeek, chipMonth, chipCustom, progressBar;
    private RecyclerView recyclerView;
    private TripAdapter adapter;

    // Current filter window, in UTC.
    private Date rangeFrom;
    private Date rangeTo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_history);

        mainApiService = ApiClient.getClient().create(ApiService.class);
        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);

        initViews();
        setupChips();

        // Default: today only, no filter applied yet.
        setRangeToday();
        loadUserData();
    }

    private void initViews() {
        tvRangeLabel = findViewById(R.id.tvRangeLabel);
        tvTripCount = findViewById(R.id.tvTripCount);
        tvStopCount = findViewById(R.id.tvStopCount);
        tvDistance = findViewById(R.id.tvDistance);
        tvDrivingTime = findViewById(R.id.tvDrivingTime);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        progressBar = findViewById(R.id.progressBar);

        chipToday = findViewById(R.id.chipToday);
        chipWeek = findViewById(R.id.chipWeek);
        chipMonth = findViewById(R.id.chipMonth);
        chipCustom = findViewById(R.id.chipCustom);

        recyclerView = findViewById(R.id.recyclerTrips);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TripAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    private void setupChips() {
        chipToday.setOnClickListener(v -> { setRangeToday(); fetchTrips(); });
        chipWeek.setOnClickListener(v -> { setRangeThisWeek(); fetchTrips(); });
        chipMonth.setOnClickListener(v -> { setRangeThisMonth(); fetchTrips(); });
        chipCustom.setOnClickListener(v -> showCustomRangePicker());
    }

    // ---- Date range presets ----

    private void setRangeToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        rangeFrom = cal.getTime();
        rangeTo = new Date();
        tvRangeLabel.setText("Today");
    }

    private void setRangeThisWeek() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        rangeFrom = cal.getTime();
        rangeTo = new Date();
        tvRangeLabel.setText("This Week");
    }

    private void setRangeThisMonth() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        rangeFrom = cal.getTime();
        rangeTo = new Date();
        tvRangeLabel.setText("This Month");
    }

    private void showCustomRangePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, fromYear, fromMonth, fromDay) -> {
            Calendar fromCal = Calendar.getInstance();
            fromCal.set(fromYear, fromMonth, fromDay, 0, 0, 0);
            Date pickedFrom = fromCal.getTime();

            new DatePickerDialog(this, (view2, toYear, toMonth, toDay) -> {
                Calendar toCal = Calendar.getInstance();
                toCal.set(toYear, toMonth, toDay, 23, 59, 59);
                rangeFrom = pickedFrom;
                rangeTo = toCal.getTime();
                tvRangeLabel.setText(displayDate(pickedFrom) + " - " + displayDate(rangeTo));
                fetchTrips();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();

        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ---- Data loading ----

    private void loadUserData() {
        mainApiService.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        CustomerResponse customer = extractObject(body.string(), CustomerResponse.class);
                        if (customer != null && customer.getCustomerId() != null) {
                            currentCustomerId = customer.getCustomerId();
                            fetchVehicleThenTrips();
                        }
                    }
                } catch (Exception e) {
                    Log.e("TripHistory", "loadUserData error", e);
                }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(TripHistoryActivity.this, "Could not load profile", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchVehicleThenTrips() {
        if (currentCustomerId == null) return;
        mainApiService.getVehiclesByCustomer(currentCustomerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<VehicleResponse> list = parseList(body.string(), VehicleResponse.class);
                        if (!list.isEmpty()) {
                            selectedVehicleId = list.get(list.size() - 1).getVehicleId();
                            fetchTrips();
                        } else {
                            showEmpty("No vehicle linked yet.");
                        }
                    }
                } catch (Exception e) {
                    Log.e("TripHistory", "fetchVehicle error", e);
                }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) { }
        });
    }

    private void fetchTrips() {
        if (selectedVehicleId == null || rangeFrom == null || rangeTo == null) return;

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        tvEmptyState.setVisibility(View.GONE);

        String fromIso = toIsoUtc(rangeFrom);
        String toIso = toIsoUtc(rangeTo);

        trackingApi.getTripsSummary(selectedVehicleId, fromIso, toIso).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        TripsReportResponse report = extractObject(body.string(), TripsReportResponse.class);
                        if (report != null) {
                            renderReport(report);
                        } else {
                            showEmpty("No trips found for this period.");
                        }
                    } else {
                        showEmpty("Could not load trips (code " + response.code() + ")");
                    }
                } catch (Exception e) {
                    Log.e("TripHistory", "fetchTrips parse error", e);
                    showEmpty("Something went wrong loading trips.");
                }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                showEmpty("Network error — check your connection.");
            }
        });
    }

    private void renderReport(TripsReportResponse report) {
        List<TripSummary> trips = report.getTrips();

        tvTripCount.setText(String.valueOf(report.getTripCount()));
        tvStopCount.setText(String.valueOf(report.getStopCount()));

        double totalDistance = 0;
        double totalMinutes = 0;
        if (trips != null) {
            for (TripSummary t : trips) {
                totalDistance += t.getDistanceKm();
                totalMinutes += t.getDurationMinutes();
            }
        }
        tvDistance.setText(String.format(Locale.getDefault(), "%.1f km", totalDistance));
        tvDrivingTime.setText(formatDuration(totalMinutes));

        if (trips == null || trips.isEmpty()) {
            showEmpty("No trips found for this period.");
            return;
        }

        recyclerView.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);
        adapter.updateTrips(trips);
    }

    private void showEmpty(String message) {
        recyclerView.setVisibility(View.GONE);
        tvEmptyState.setVisibility(View.VISIBLE);
        tvEmptyState.setText(message);
        tvTripCount.setText("0");
        tvStopCount.setText("0");
        tvDistance.setText("0.0 km");
        tvDrivingTime.setText("0m");
    }

    // ---- Helpers ----

    private String formatDuration(double totalMinutes) {
        int hours = (int) (totalMinutes / 60);
        int minutes = (int) (totalMinutes % 60);
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private String displayDate(Date date) {
        return new SimpleDateFormat("dd MMM", Locale.getDefault()).format(date);
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

    private <T> List<T> parseList(String json, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                list = gson.fromJson(root.getAsJsonArray("data"), TypeToken.getParameterized(List.class, clazz).getType());
            }
        } catch (Exception e) {
            Log.e("TripHistory", "parseList error", e);
        }
        return list;
    }
}