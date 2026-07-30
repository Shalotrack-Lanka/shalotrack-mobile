package com.example.letstracklanka.ui.history;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.example.letstracklanka.ui.main.AddressResolver;
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
    private String selectedVehicleName = "LT Demo Device";

    private TextView tvDeviceName;
    private RecyclerView rvTripHistory;
    private View errorBanner;
    private TextView tvErrorBannerMessage, tvErrorBannerRetry;

    private TripHistoryAdapter adapter;
    private final List<Object> historyItems = new ArrayList<>();
    private ConnectivityManager.NetworkCallback networkCallback;
    private AddressResolver addressResolver;

    private Date rangeFrom;
    private Date rangeTo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_history);

        mainApiService = ApiClient.getClient().create(ApiService.class);
        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);
        addressResolver = new AddressResolver(this);

        if (getIntent() != null) {
            if (getIntent().hasExtra("vehicleId")) {
                selectedVehicleId = getIntent().getStringExtra("vehicleId");
            }
            if (getIntent().hasExtra("vehicleName")) {
                selectedVehicleName = getIntent().getStringExtra("vehicleName");
            }
        }

        initViews();
        registerNetworkMonitor();
        setRangeToday();
        loadUserData();
    }

    private void initViews() {
        tvDeviceName = findViewById(R.id.tvHistoryDeviceName);
        if (tvDeviceName != null) tvDeviceName.setText(selectedVehicleName);

        ImageView btnBack = findViewById(R.id.btnBackHistory);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        ImageView btnCalendar = findViewById(R.id.btnCalendarPicker);
        if (btnCalendar != null) {
            btnCalendar.setOnClickListener(v -> showDatePicker());
        }

        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        tvErrorBannerRetry = findViewById(R.id.tvErrorBannerRetry);

        rvTripHistory = findViewById(R.id.rvTripHistory);
        rvTripHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TripHistoryAdapter(historyItems);
        rvTripHistory.setAdapter(adapter);
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar fromCal = Calendar.getInstance();
            fromCal.set(year, month, dayOfMonth, 0, 0, 0);
            rangeFrom = fromCal.getTime();

            Calendar toCal = Calendar.getInstance();
            toCal.set(year, month, dayOfMonth, 23, 59, 59);
            rangeTo = toCal.getTime();

            String selectedDate = String.format(Locale.US, "%d-%02d-%02d", year, month + 1, dayOfMonth);
            Toast.makeText(this, "Loading trips for: " + selectedDate, Toast.LENGTH_SHORT).show();
            fetchTrips();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void setRangeToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        rangeFrom = cal.getTime();
        rangeTo = new Date();
    }

    // ---- Backend API integration ----

    private void loadUserData() {
        hideErrorBanner();
        mainApiService.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        CustomerResponse customer = extractObject(body.string(), CustomerResponse.class);
                        if (customer != null && customer.getCustomerId() != null) {
                            currentCustomerId = customer.getCustomerId();
                            fetchVehicleThenTrips();
                        } else {
                            showRetryBanner("Couldn't load your profile.");
                        }
                    } else {
                        Log.w("TripHistory", "getMyProfile failed with code " + response.code());
                        showRetryBanner("Couldn't load your profile.");
                    }
                } catch (Exception e) {
                    Log.e("TripHistory", "loadUserData error", e);
                    showRetryBanner("Something went wrong loading your profile.");
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("TripHistory", "loadUserData network error", t);
                showRetryBanner("Network error — couldn't load your profile.");
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
                            if (selectedVehicleId == null) {
                                selectedVehicleId = list.get(list.size() - 1).getVehicleId();
                                selectedVehicleName = list.get(list.size() - 1).getMake() + " " + list.get(list.size() - 1).getModel();
                                if (tvDeviceName != null) tvDeviceName.setText(selectedVehicleName);
                            }
                            fetchTrips();
                        } else {
                            showRetryBanner("No vehicle linked yet.");
                        }
                    } else {
                        Log.w("TripHistory", "fetchVehicleThenTrips failed with code " + response.code());
                        showRetryBanner("Couldn't load your vehicle.");
                    }
                } catch (Exception e) {
                    Log.e("TripHistory", "fetchVehicle error", e);
                    showRetryBanner("Something went wrong loading your vehicle.");
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("TripHistory", "fetchVehicleThenTrips network error", t);
                showRetryBanner("Network error — couldn't load your vehicle.");
            }
        });
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
                        if (report != null && report.getTrips() != null && !report.getTrips().isEmpty()) {
                            renderReport(report);
                        } else {
                            showRetryBanner("No trips found for this period.");
                        }
                    } else {
                        showRetryBanner("Could not load trips (code " + response.code() + ")");
                    }
                } catch (Exception e) {
                    Log.e("TripHistory", "fetchTrips parse error", e);
                    showRetryBanner("Something went wrong loading trips.");
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                showRetryBanner("Network error — check your connection.");
            }
        });
    }

    private void renderReport(TripsReportResponse report) {
        historyItems.clear();
        List<TripSummary> trips = report.getTrips();

        double totalDistance = 0;
        double totalMinutes = 0;
        for (TripSummary t : trips) {
            totalDistance += t.getDistanceKm();
            totalMinutes += t.getDurationMinutes();
        }

        // Add Day Header Summary
        String dayHeaderTitle = displayDayHeader(rangeFrom) + " - " + trips.size() + " Trips";
        String daySummary = String.format(Locale.US, "Total - %.0f km in %s", totalDistance, formatDuration(totalMinutes));
        historyItems.add(new DayHeaderItem(dayHeaderTitle, daySummary));

        // Add Trips as Timeline Cards
        for (TripSummary trip : trips) {
            String distText = String.format(Locale.US, "%.0f km Trip", trip.getDistanceKm());
            String timeDuration = formatTimeRange(trip.getStartTime(), trip.getEndTime()) + " (" + formatDuration(trip.getDurationMinutes()) + ")";
            String topSpeedText = String.format(Locale.US, "%.0f kph", trip.getMaxSpeed());

            // Add real trip item (AddressResolver will resolve coordinates if string address is empty)
            historyItems.add(new TripCardItem(
                    distText,
                    "Loading route address...",
                    timeDuration,
                    topSpeedText,
                    "Start Location",
                    "End Location",
                    trip
            ));
        }

        adapter.notifyDataSetChanged();
    }

    private void showRetryBanner(String message) {
        if (errorBanner == null) return;
        tvErrorBannerMessage.setText(message);
        tvErrorBannerRetry.setOnClickListener(v -> loadUserData());
        errorBanner.setVisibility(View.VISIBLE);
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
                runOnUiThread(() -> showRetryBanner("No internet connection."));
            }

            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    hideErrorBanner();
                    loadUserData();
                });
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

    // ---- Date / Time formatting helpers ----

    private String formatDuration(double totalMinutes) {
        int hours = (int) (totalMinutes / 60);
        int minutes = (int) (totalMinutes % 60);
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private String displayDayHeader(Date date) {
        return new SimpleDateFormat("EEE", Locale.US).format(date);
    }

    private String formatTimeRange(String fromIso, String toIso) {
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date fromDate = isoFormat.parse(fromIso);
            Date toDate = isoFormat.parse(toIso);

            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
            return timeFormat.format(fromDate) + " - " + timeFormat.format(toDate);
        } catch (Exception e) {
            return "Trip Time";
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

    // ==========================================
    // DATA MODELS FOR RECYCLERVIEW
    // ==========================================
    private static class DayHeaderItem {
        String dayTitle;
        String summary;

        DayHeaderItem(String dayTitle, String summary) {
            this.dayTitle = dayTitle;
            this.summary = summary;
        }
    }

    private static class TripCardItem {
        String distanceText;
        String fullAddress;
        String timeDuration;
        String topSpeed;
        String startPlace;
        String endPlace;
        TripSummary tripSummary;

        TripCardItem(String distanceText, String fullAddress, String timeDuration,
                     String topSpeed, String startPlace, String endPlace, TripSummary tripSummary) {
            this.distanceText = distanceText;
            this.fullAddress = fullAddress;
            this.timeDuration = timeDuration;
            this.topSpeed = topSpeed;
            this.startPlace = startPlace;
            this.endPlace = endPlace;
            this.tripSummary = tripSummary;
        }
    }

    // ==========================================
    // RECYCLERVIEW ADAPTER FOR TIMELINE UI
    // ==========================================
    private class TripHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_TRIP = 1;

        private final List<Object> items;

        TripHistoryAdapter(List<Object> items) {
            this.items = items;
        }

        @Override
        public int getItemViewType(int position) {
            if (items.get(position) instanceof DayHeaderItem) {
                return TYPE_HEADER;
            }
            return TYPE_TRIP;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trip_history_day_header, parent, false);
                return new HeaderViewHolder(v);
            } else {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trip_history_card, parent, false);
                return new TripViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                DayHeaderItem header = (DayHeaderItem) items.get(position);
                ((HeaderViewHolder) holder).tvDayTitle.setText(header.dayTitle);
                ((HeaderViewHolder) holder).tvDaySummary.setText(header.summary);
            } else if (holder instanceof TripViewHolder) {
                TripCardItem tripItem = (TripCardItem) items.get(position);
                TripViewHolder th = (TripViewHolder) holder;

                th.tvDistance.setText(tripItem.distanceText);
                th.tvTime.setText(tripItem.timeDuration);
                th.tvTopSpeed.setText("Top Speed: " + tripItem.topSpeed);

                // Resolve real start/end addresses from AddressResolver
                if (tripItem.tripSummary != null) {
                    addressResolver.resolveAddress(0.0, 0.0, address -> {
                        tripItem.fullAddress = address;
                        th.tvAddresses.setText(address);
                    });
                } else {
                    th.tvAddresses.setText(tripItem.fullAddress);
                }

                th.tvStartPlaceName.setText(tripItem.startPlace);
                th.tvEndPlaceName.setText(tripItem.endPlace);

                th.btnSaveStart.setOnClickListener(v ->
                        Toast.makeText(TripHistoryActivity.this, "Saved Place: " + tripItem.startPlace, Toast.LENGTH_SHORT).show());
                th.btnSaveEnd.setOnClickListener(v ->
                        Toast.makeText(TripHistoryActivity.this, "Saved Place: " + tripItem.endPlace, Toast.LENGTH_SHORT).show());
                th.btnPost.setOnClickListener(v ->
                        Toast.makeText(TripHistoryActivity.this, "Posting Trip...", Toast.LENGTH_SHORT).show());
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView tvDayTitle, tvDaySummary;

            HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDayTitle = itemView.findViewById(R.id.tvDayTitle);
                tvDaySummary = itemView.findViewById(R.id.tvDaySummary);
            }
        }

        class TripViewHolder extends RecyclerView.ViewHolder {
            TextView tvDistance, tvAddresses, tvTime, tvTopSpeed, tvStartPlaceName, tvEndPlaceName;
            View btnSaveStart, btnSaveEnd, btnPost;

            TripViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDistance = itemView.findViewById(R.id.tvTripDistance);
                tvAddresses = itemView.findViewById(R.id.tvTripAddresses);
                tvTime = itemView.findViewById(R.id.tvTripTime);
                tvTopSpeed = itemView.findViewById(R.id.tvTopSpeed);
                tvStartPlaceName = itemView.findViewById(R.id.tvStartPlaceName);
                tvEndPlaceName = itemView.findViewById(R.id.tvEndPlaceName);
                btnSaveStart = itemView.findViewById(R.id.btnSavePlaceStart);
                btnSaveEnd = itemView.findViewById(R.id.btnSavePlaceEnd);
                btnPost = itemView.findViewById(R.id.btnPostTrip);
            }
        }
    }
}