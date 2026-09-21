package com.example.letstracklanka.ui.main;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.history.TripHistoryActivity;
import com.example.letstracklanka.ui.main.reports.AlertReportActivity;
import com.example.letstracklanka.ui.main.reports.KmReportActivity;
import com.example.letstracklanka.ui.main.reports.StopReportActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Report generation feature -- the single reusable "pick a vehicle + date
 * range, then go" filter step launched by the 4 real report cards in
 * bottom_sheet_reports_menu (KM/Trip/Alert/Stop Alert). Fuel/Fuel
 * Graph/Temperature stay on their existing "Coming soon" stub in
 * DrawerMenuHelper -- there is no sensor data behind them anywhere in this
 * system yet, so wiring a filter sheet to them would just be a prettier
 * dead end.
 *
 * Deliberately reuses bottom_sheet_report_filter.xml, which existed
 * already but was never inflated by anything (dead layout) -- see that
 * file's own header comment for the two real bugs found while bringing it
 * to life.
 *
 * Each report type is handed off to whichever existing screen already
 * knows how to show that data, rather than building four parallel result
 * screens from scratch:
 *   - Trip Report  -> TripHistoryActivity (already built, day-grouped list)
 *   - KM Report    -> KmReportActivity (period totals + a full day-by-day
 *                     breakdown -- not ValueActivity, which only shows one
 *                     chart metric at a time with no itemized daily detail)
 *   - Alert Report -> new AlertReportActivity (nothing existing shows a
 *                     date-ranged, per-type alert summary -- the drawer's
 *                     Alerts tab is a live, unranged notification feed)
 *   - Stop Alert   -> new StopReportActivity (same reasoning as Alert Report)
 */
public class ReportFilterBottomSheet {

    public enum ReportType {
        KM("KM Report"),
        TRIP("Trip Report"),
        ALERT("Alert Report"),
        STOP_ALERT("Stop Alert Report");

        final String title;
        ReportType(String title) { this.title = title; }
    }

    // Same 90-day ceiling every report endpoint on the API side enforces
    // (GpsTrackingService/VehicleStatsService/AlertService) -- checked here
    // too so the user gets an immediate, specific message instead of a
    // generic "couldn't load" after a round trip that was always going to
    // be rejected.
    private static final int MAX_REPORT_RANGE_DAYS = 90;

    public static void show(AppCompatActivity activity, ReportType type) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_report_filter, null);
        dialog.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvSelectedReportTitle);
        if (tvTitle != null) tvTitle.setText(type.title);

        ImageView btnClose = view.findViewById(R.id.btnCloseFilter);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        Spinner spinnerVehicles = view.findViewById(R.id.spinnerDevices);
        TextView tvFromDate = view.findViewById(R.id.tvFromDate);
        TextView tvToDate = view.findViewById(R.id.tvToDate);
        TextView tvFilterError = view.findViewById(R.id.tvFilterError);
        View btnSubmit = view.findViewById(R.id.btnSubmitReport);

        List<DashboardVehicle> vehicles = new ArrayList<>();
        ArrayAdapter<String> vehicleAdapter = new ArrayAdapter<>(
                activity, android.R.layout.simple_spinner_item, new ArrayList<>());
        vehicleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spinnerVehicles != null) spinnerVehicles.setAdapter(vehicleAdapter);

        // Calendars default to today (To) and 30 days ago (From) -- a
        // sensible starting point the user can still change, matching
        // TripHistoryActivity's own INITIAL_RANGE_DAYS default.
        Calendar calFrom = Calendar.getInstance();
        calFrom.add(Calendar.DAY_OF_YEAR, -30);
        Calendar calTo = Calendar.getInstance();

        java.text.SimpleDateFormat displayFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
        if (tvFromDate != null) tvFromDate.setText(displayFormat.format(calFrom.getTime()));
        if (tvToDate != null) tvToDate.setText(displayFormat.format(calTo.getTime()));

        if (tvFromDate != null) {
            tvFromDate.setOnClickListener(v -> showDatePicker(activity, calFrom, () ->
                    tvFromDate.setText(displayFormat.format(calFrom.getTime()))));
        }
        if (tvToDate != null) {
            tvToDate.setOnClickListener(v -> showDatePicker(activity, calTo, () ->
                    tvToDate.setText(displayFormat.format(calTo.getTime()))));
        }

        loadVehicles(activity, vehicles, vehicleAdapter);

        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                if (tvFilterError != null) tvFilterError.setVisibility(View.GONE);

                if (vehicles.isEmpty() || spinnerVehicles == null
                        || spinnerVehicles.getSelectedItemPosition() < 0
                        || spinnerVehicles.getSelectedItemPosition() >= vehicles.size()) {
                    showFilterError(tvFilterError, "Select a vehicle first.");
                    return;
                }

                // Normalize to the full selected day in each direction --
                // From at 00:00:00, To at 23:59:59 -- so picking the same
                // day for both fields still yields a valid, non-empty
                // report window rather than a zero-second range.
                Calendar rangeFrom = (Calendar) calFrom.clone();
                rangeFrom.set(Calendar.HOUR_OF_DAY, 0);
                rangeFrom.set(Calendar.MINUTE, 0);
                rangeFrom.set(Calendar.SECOND, 0);

                Calendar rangeTo = (Calendar) calTo.clone();
                rangeTo.set(Calendar.HOUR_OF_DAY, 23);
                rangeTo.set(Calendar.MINUTE, 59);
                rangeTo.set(Calendar.SECOND, 59);

                if (rangeTo.getTimeInMillis() <= rangeFrom.getTimeInMillis()) {
                    showFilterError(tvFilterError, "'To' date must be after 'From' date.");
                    return;
                }

                long rangeDays = (rangeTo.getTimeInMillis() - rangeFrom.getTimeInMillis()) / (24L * 60 * 60 * 1000);
                if (rangeDays > MAX_REPORT_RANGE_DAYS) {
                    showFilterError(tvFilterError, "Reports are limited to " + MAX_REPORT_RANGE_DAYS + " days. Pick a shorter range.");
                    return;
                }

                DashboardVehicle selectedVehicle = vehicles.get(spinnerVehicles.getSelectedItemPosition());
                dialog.dismiss();
                launchReport(activity, type, selectedVehicle, rangeFrom.getTimeInMillis(), rangeTo.getTimeInMillis());
            });
        }

        dialog.show();
    }

    private static void showDatePicker(AppCompatActivity activity, Calendar target, Runnable onPicked) {
        new DatePickerDialog(activity, (view, year, month, dayOfMonth) -> {
            target.set(Calendar.YEAR, year);
            target.set(Calendar.MONTH, month);
            target.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            onPicked.run();
        }, target.get(Calendar.YEAR), target.get(Calendar.MONTH), target.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static void showFilterError(TextView tvFilterError, String message) {
        if (tvFilterError == null) return;
        tvFilterError.setText(message);
        tvFilterError.setVisibility(View.VISIBLE);
    }

    // FIX #1 (endpoint): was getVehiclesByCustomer() -- GET
    // api/Vehicles/customer/{customerId}, which VehicleRepository
    // .GetByCustomerAsync (API side) filters to `CustomerId == customerId`,
    // i.e. OWNED vehicles only. Shared and demo vehicles never appeared in
    // this spinner. HomeActivity/VehiclesActivity already abandoned that
    // endpoint for their vehicle lists in favor of the dashboard endpoint
    // used below, which is the confirmed-complete source (owned + accepted
    // shares + the shared demo vehicle).
    //
    // FIX #2 (customer id source -- the actual reason the spinner was
    // showing NOTHING, not just an incomplete list): this previously read
    // customerId from SessionManager.getCustomerId(), a SharedPreferences
    // value written only by SessionManager.createLoginSession() -- which is
    // dead code, never called anywhere in this app. getCustomerId() has
    // therefore always returned null here, which hit loadVehicles()'s old
    // early-return before any network call was made -- silently, no log, no
    // toast, exactly the empty-box-no-arrow symptom reported. HomeActivity
    // never had this problem because it resolves the customer fresh from
    // the signed-in Firebase user via GET api/Customers/me (see
    // HomeActivity.loadUserData()/extractCustomer()) and keeps it in a
    // local field -- not SessionManager. This method now does the same
    // resolution itself, since ReportFilterBottomSheet.show() is called
    // from five different activities' drawers (DrawerMenuHelper.wireDrawer:
    // HomeActivity, VehiclesActivity, TagsActivity, AlertsActivity,
    // CirclesActivity) and not all of them already hold a resolved
    // customerId in memory to hand down as a parameter.
    private static void loadVehicles(
            AppCompatActivity activity, List<DashboardVehicle> outVehicles, ArrayAdapter<String> adapter) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        Log.w("ReportFilterSheet", "getMyProfile failed, code " + response.code());
                        Toast.makeText(activity, "Couldn't load your profile.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    CustomerResponse customer = extractCustomer(body.string());
                    if (customer == null || customer.getCustomerId() == null) {
                        Log.w("ReportFilterSheet", "getMyProfile returned no customer id");
                        Toast.makeText(activity, "Couldn't load your profile.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    fetchDashboardVehicles(activity, customer.getCustomerId(), api, outVehicles, adapter);
                } catch (Exception e) {
                    Log.e("ReportFilterSheet", "getMyProfile parse error", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("ReportFilterSheet", "getMyProfile network error", t);
                Toast.makeText(activity, "Network error loading your profile.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void fetchDashboardVehicles(
            AppCompatActivity activity, String customerId, ApiService api,
            List<DashboardVehicle> outVehicles, ArrayAdapter<String> adapter) {
        api.getCustomerDashboard(customerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<DashboardVehicle> parsed = parseVehicles(body.string());
                        outVehicles.clear();
                        outVehicles.addAll(parsed);

                        List<String> labels = new ArrayList<>();
                        for (DashboardVehicle v : parsed) {
                            String label = v.getVehicleNumber() != null ? v.getVehicleNumber() : "Vehicle";
                            if (v.isShared()) label += " (Shared)";
                            labels.add(label);
                        }
                        adapter.clear();
                        adapter.addAll(labels);
                        adapter.notifyDataSetChanged();
                    } else {
                        Log.w("ReportFilterSheet", "loadVehicles failed, code " + response.code());
                        Toast.makeText(activity, "Couldn't load your vehicles.", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e("ReportFilterSheet", "loadVehicles parse error", e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("ReportFilterSheet", "loadVehicles network error", t);
                Toast.makeText(activity, "Network error loading your vehicles.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Same "data.<object>" envelope HomeActivity.extractCustomer()/
    // extractObject() already parse -- duplicated here in miniature rather
    // than pulled into a shared utility class, matching this file's
    // existing parseVehicles() precedent below.
    private static CustomerResponse extractCustomer(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), CustomerResponse.class);
            }
            return gson.fromJson(json, CustomerResponse.class);
        } catch (Exception e) {
            Log.e("ReportFilterSheet", "extractCustomer parse error", e);
            return null;
        }
    }

    // Same "data.vehicles" envelope HomeActivity already parses for the
    // dashboard endpoint.
    private static List<DashboardVehicle> parseVehicles(String json) {
        List<DashboardVehicle> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null || !root.has("data") || root.get("data").isJsonNull()) return list;
            JsonObject data = root.getAsJsonObject("data");
            if (data == null || !data.has("vehicles") || !data.get("vehicles").isJsonArray()) return list;

            Type listType = new TypeToken<List<DashboardVehicle>>() {}.getType();
            List<DashboardVehicle> parsed = gson.fromJson(data.getAsJsonArray("vehicles"), listType);
            if (parsed != null) list = parsed;
        } catch (Exception e) {
            Log.e("ReportFilterSheet", "parseVehicles error", e);
        }
        return list;
    }

    private static void launchReport(
            AppCompatActivity activity, ReportType type, DashboardVehicle vehicle, long fromMillis, long toMillis) {
        String vehicleId = vehicle.getVehicleId();
        String vehicleName = vehicle.getVehicleNumber();

        switch (type) {
            case TRIP: {
                Intent intent = new Intent(activity, TripHistoryActivity.class);
                intent.putExtra(TripHistoryActivity.EXTRA_VEHICLE_ID, vehicleId);
                intent.putExtra(TripHistoryActivity.EXTRA_VEHICLE_NAME, vehicleName);
                intent.putExtra(TripHistoryActivity.EXTRA_REPORT_FROM_MILLIS, fromMillis);
                intent.putExtra(TripHistoryActivity.EXTRA_REPORT_TO_MILLIS, toMillis);
                activity.startActivity(intent);
                break;
            }
            case KM: {
                // FIX: was ValueActivity (the live Today/Week/Month/All stats
                // screen) with a fixed range bolted on -- one chart metric at a
                // time, no itemized daily detail. KmReportActivity is a
                // dedicated report screen: period totals plus a full,
                // scrollable day-by-day breakdown, built for exactly this.
                Intent intent = new Intent(activity, KmReportActivity.class);
                intent.putExtra(KmReportActivity.EXTRA_VEHICLE_ID, vehicleId);
                intent.putExtra(KmReportActivity.EXTRA_VEHICLE_NAME, vehicleName);
                intent.putExtra(KmReportActivity.EXTRA_FROM_MILLIS, fromMillis);
                intent.putExtra(KmReportActivity.EXTRA_TO_MILLIS, toMillis);
                activity.startActivity(intent);
                break;
            }
            case ALERT: {
                Intent intent = new Intent(activity, AlertReportActivity.class);
                intent.putExtra(AlertReportActivity.EXTRA_VEHICLE_ID, vehicleId);
                intent.putExtra(AlertReportActivity.EXTRA_VEHICLE_NAME, vehicleName);
                intent.putExtra(AlertReportActivity.EXTRA_FROM_MILLIS, fromMillis);
                intent.putExtra(AlertReportActivity.EXTRA_TO_MILLIS, toMillis);
                activity.startActivity(intent);
                break;
            }
            case STOP_ALERT: {
                Intent intent = new Intent(activity, StopReportActivity.class);
                intent.putExtra(StopReportActivity.EXTRA_VEHICLE_ID, vehicleId);
                intent.putExtra(StopReportActivity.EXTRA_VEHICLE_NAME, vehicleName);
                intent.putExtra(StopReportActivity.EXTRA_FROM_MILLIS, fromMillis);
                intent.putExtra(StopReportActivity.EXTRA_TO_MILLIS, toMillis);
                activity.startActivity(intent);
                break;
            }
        }
    }
}