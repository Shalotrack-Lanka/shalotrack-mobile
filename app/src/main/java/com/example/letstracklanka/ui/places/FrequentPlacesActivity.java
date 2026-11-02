package com.example.letstracklanka.ui.places;

import com.example.letstracklanka.ui.main.AddressResolver;
import com.example.letstracklanka.utils.InputValidator;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.SavedPlaceResponse;
import com.example.letstracklanka.data.model.TripSummary;
import com.example.letstracklanka.data.model.TripsReportResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.ui.auth.AuthUi;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.FrequentPlaces;
import com.example.letstracklanka.utils.VehicleLoader;
import com.google.android.material.chip.ChipGroup;

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
 * Frequent places (same idea as the Fleet portal's /places): pick a vehicle and 30 or 90 days, and see where it
 * usually ends its trips. The grouping runs on the phone (utils.FrequentPlaces) from the trip list the app
 * already loads for Trip History, so no new server endpoint and no new data path is needed.
 *
 * Opening a place hands a plain geo: link to the system maps app; the coordinates are range-checked first.
 */
public class FrequentPlacesActivity extends AppCompatActivity {

    private static final String TAG = "FrequentPlaces";

    private ShaloTrackApi trackingApi;
    private ApiService api;

    private final List<VehicleResponse> vehicles = new ArrayList<>();
    private String selectedVehicleId;
    private int days = 30;
    private AddressResolver resolver;
    /** Newest request wins: a slow answer for a vehicle the user already left must not overwrite the screen. */
    private int requestSerial = 0;

    private AutoCompleteTextView dropdown;
    private ProgressBar progress;
    private TextView tvMessage;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_frequent_places);
        AuthUi.edgeToEdge(this, findViewById(R.id.main));

        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);
        resolver = new AddressResolver(this);
        api = ApiClient.getClient().create(ApiService.class);

        ((TextView) findViewById(R.id.tvHeaderTitle)).setText(R.string.freq_title);
        findViewById(R.id.btnHeaderBack).setOnClickListener(v -> finish());

        dropdown = findViewById(R.id.dropdownVehicle);
        progress = findViewById(R.id.progress);
        tvMessage = findViewById(R.id.tvMessage);
        list = findViewById(R.id.listPlaces);

        ChipGroup periods = findViewById(R.id.chipsPeriod);
        periods.setOnCheckedStateChangeListener((group, ids) -> {
            days = ids.contains(R.id.chip90) ? 90 : 30;
            loadPlaces();
        });

        dropdown.setOnItemClickListener((parent, view, position, id) -> {
            selectedVehicleId = vehicles.get(position).getVehicleId();
            loadPlaces();
        });

        showMessage(getString(R.string.freq_loading_vehicles));
        VehicleLoader.load(new VehicleLoader.Callback2() {
            @Override public void onLoaded(List<VehicleResponse> loaded) {
                if (isFinishing() || isDestroyed()) return;
                vehicles.clear();
                vehicles.addAll(loaded);
                if (vehicles.isEmpty()) { showMessage(getString(R.string.freq_no_vehicles)); return; }
                List<String> labels = new ArrayList<>();
                for (VehicleResponse v : vehicles) labels.add(v.getVehicleNumber());
                dropdown.setAdapter(new ArrayAdapter<>(FrequentPlacesActivity.this,
                        android.R.layout.simple_dropdown_item_1line, labels));
                dropdown.setText(labels.get(0), false);
                selectedVehicleId = vehicles.get(0).getVehicleId();
                loadPlaces();
            }

            @Override public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                showMessage(message);
            }
        });
    }

    private void loadPlaces() {
        if (selectedVehicleId == null) return;
        final int serial = ++requestSerial;
        list.removeAllViews();
        progress.setVisibility(View.VISIBLE);
        tvMessage.setVisibility(View.GONE);

        // The API rejects ranges over 90 days, so stay a few minutes inside the limit.
        Date to = new Date();
        Date from = new Date(to.getTime() - days * 24L * 60L * 60L * 1000L + 5L * 60L * 1000L);

        trackingApi.getTripsSummary(selectedVehicleId, iso(from), iso(to)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (serial != requestSerial || isFinishing() || isDestroyed()) return;
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        TripsReportResponse report = ApiJson.object(body.string(), TripsReportResponse.class);
                        List<TripSummary> trips = report != null && report.getTrips() != null
                                ? report.getTrips() : new ArrayList<>();
                        loadSavedThenShow(trips, serial);
                        return;
                    }
                    progress.setVisibility(View.GONE);
                    showMessage(response.code() == 402 ? getString(R.string.freq_expired)
                            : response.code() == 403 ? getString(R.string.freq_forbidden)
                            : getString(R.string.freq_failed));
                } catch (Exception e) {
                    Log.e(TAG, "parse error", e);
                    progress.setVisibility(View.GONE);
                    showMessage(getString(R.string.freq_failed));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (serial != requestSerial || isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                showMessage(getString(R.string.freq_network));
            }
        });
    }

    /** Saved places only add a label. If they can't be loaded the list still shows. */
    private void loadSavedThenShow(List<TripSummary> trips, int serial) {
        api.getMyPlaces().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                List<FrequentPlaces.Saved> saved = new ArrayList<>();
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<SavedPlaceResponse> places = ApiJson.list(body.string(), SavedPlaceResponse.class);
                        if (places != null) {
                            for (SavedPlaceResponse p : places) {
                                saved.add(new FrequentPlaces.Saved(p.getName(), p.getLatitude(), p.getLongitude()));
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "saved places unavailable", e);
                }
                show(trips, saved, serial);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                show(trips, new ArrayList<>(), serial);
            }
        });
    }

    private void show(List<TripSummary> trips, List<FrequentPlaces.Saved> saved, int serial) {
        if (serial != requestSerial || isFinishing() || isDestroyed()) return;
        progress.setVisibility(View.GONE);

        List<FrequentPlaces.Visit> visits = new ArrayList<>();
        for (TripSummary t : trips) {
            if (t.isInProgress()) continue;
            visits.add(new FrequentPlaces.Visit(t.getEndLatitude(), t.getEndLongitude(),
                    t.getEndTime(), (int) Math.round(t.getDurationMinutes())));
        }
        List<FrequentPlaces.Place> places = FrequentPlaces.build(visits, saved);

        list.removeAllViews();
        if (places.isEmpty()) {
            showMessage(getString(R.string.freq_empty, days));
            return;
        }
        tvMessage.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        int rank = 1;
        for (FrequentPlaces.Place p : places) {
            View row = inflater.inflate(R.layout.item_frequent_place, list, false);
            ((TextView) row.findViewById(R.id.tvRank)).setText(String.valueOf(rank));
            final String coords = String.format(Locale.US, "%.4f, %.4f", p.lat, p.lng);
            String title = p.savedName != null ? p.savedName : coords;
            final TextView tvTitle = (TextView) row.findViewById(R.id.tvPlaceTitle);
            tvTitle.setText(title);
            ((TextView) row.findViewById(R.id.tvPlaceMeta)).setText(getString(R.string.freq_meta,
                    p.visits, formatMinutes(p.totalMinutes), p.lastVisitIso.length() >= 10
                            ? p.lastVisitIso.substring(0, 10) : "-"));
            row.setContentDescription(getString(R.string.freq_open_map, title, p.visits));
            final double lat = p.lat, lng = p.lng;
            final String[] label = { title };
            row.setOnClickListener(v -> openInMaps(lat, lng, label[0]));
            list.addView(row);
            if (p.savedName == null) {
                // A street/area name is what people recognise; coordinates stay as the fallback.
                final int shown = rank, visitCount = p.visits;
                resolver.resolveAddress(lat, lng, address -> {
                    if (isFinishing() || isDestroyed() || serial != requestSerial) return;
                    String clean = InputValidator.cleanLine(address);
                    if (clean.isEmpty() || clean.equals(coords) || clean.matches("^-?\\d+\\.\\d+, ?-?\\d+\\.\\d+$")) return;
                    label[0] = clean;
                    tvTitle.setText(clean);
                    row.setContentDescription(getString(R.string.freq_open_map, clean, visitCount));
                });
            }
            rank++;
        }
    }

    private void openInMaps(double lat, double lng, String label) {
        if (!FrequentPlaces.validCoordinate(lat, lng)) return;
        Uri uri = Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)", lat, lng, lat, lng, Uri.encode(label)));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            showMessage(getString(R.string.freq_no_maps));
        }
    }

    private void showMessage(String text) {
        tvMessage.setText(text);
        tvMessage.setVisibility(View.VISIBLE);
    }

    private static String formatMinutes(int minutes) {
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }

    private static String iso(Date d) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(d);
    }
}
