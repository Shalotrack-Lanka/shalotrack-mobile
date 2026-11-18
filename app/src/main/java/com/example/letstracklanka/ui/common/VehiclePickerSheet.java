package com.example.letstracklanka.ui.common;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.vehicles.VehicleListAdapter;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.CustomerSession;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Bottom sheet to choose one of the customer's vehicles (owned, shared and demo), with the same rows
 * as the Vehicles tab. Optional "All vehicles" row for screens that can show everything (Alerts).
 */
public final class VehiclePickerSheet {

    public interface Listener {
        /** vehicle is null when "All vehicles" was chosen. */
        void onPicked(@Nullable DashboardVehicle vehicle);
    }

    private VehiclePickerSheet() {}

    public static void show(@NonNull Activity activity, boolean offerAll, @NonNull Listener listener) {
        show(activity, offerAll, false, R.string.st_picker_title, R.string.st_picker_all_vehicles, listener);
    }

    /**
     * ownedOnly: hide vehicles shared with the customer and the demo vehicle (for settings only the owner
     * may make, such as a geofence scoped to one vehicle; the server refuses the others anyway).
     */
    public static void show(@NonNull Activity activity, boolean offerAll, boolean ownedOnly,
                            @StringRes int title, @StringRes int allLabel, @NonNull Listener listener) {
        BottomSheetDialog sheet = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_vehicle_picker, null);
        sheet.setContentView(view);

        ((TextView) view.findViewById(R.id.tvPickerTitle)).setText(title);
        View progress = view.findViewById(R.id.progressPicker);
        TextView error = view.findViewById(R.id.tvPickerError);
        RecyclerView list = view.findViewById(R.id.recyclerPicker);
        TextView all = view.findViewById(R.id.btnPickerAll);
        all.setText(allLabel);
        all.setVisibility(offerAll ? View.VISIBLE : View.GONE);
        all.setOnClickListener(v -> {
            sheet.dismiss();
            listener.onPicked(null);
        });

        VehicleListAdapter adapter = new VehicleListAdapter(new ArrayList<>(), v -> {
            sheet.dismiss();
            listener.onPicked(v);
        }, null);
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setAdapter(adapter);

        CustomerSession.get(activity, new CustomerSession.Callback2() {
            @Override
            public void onCustomer(@NonNull String customerId) {
                ApiService api = ApiClient.getClient().create(ApiService.class);
                api.getCustomerDashboard(customerId).enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        progress.setVisibility(View.GONE);
                        try (ResponseBody body = response.body()) {
                            if (!response.isSuccessful() || body == null) {
                                showError(error, R.string.st_picker_failed);
                                return;
                            }
                            List<DashboardVehicle> vehicles = parse(body.string());
                            if (ownedOnly) {
                                List<DashboardVehicle> owned = new ArrayList<>();
                                for (DashboardVehicle v : vehicles) if (!v.isShared() && !v.isDemo()) owned.add(v);
                                vehicles = owned;
                            }
                            if (vehicles.isEmpty()) {
                                showError(error, R.string.st_picker_empty);
                                return;
                            }
                            adapter.updateVehicles(vehicles);
                            list.setVisibility(View.VISIBLE);
                        } catch (Exception e) {
                            showError(error, R.string.st_picker_failed);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        progress.setVisibility(View.GONE);
                        showError(error, R.string.st_error_network);
                    }
                });
            }

            @Override
            public void onError(int httpCode) {
                progress.setVisibility(View.GONE);
                showError(error, httpCode == 0 ? R.string.st_error_network : R.string.st_picker_failed);
            }
        });
        sheet.show();
    }

    private static void showError(TextView error, int message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }

    static List<DashboardVehicle> parse(String json) {
        JsonObject data = ApiJson.dataObject(json);
        if (data == null) return new ArrayList<>();
        JsonArray arr = data.has("vehicles") && data.get("vehicles").isJsonArray() ? data.getAsJsonArray("vehicles") : null;
        if (arr == null) return new ArrayList<>();
        List<DashboardVehicle> parsed = new Gson().fromJson(arr, TypeToken.getParameterized(List.class, DashboardVehicle.class).getType());
        List<DashboardVehicle> out = new ArrayList<>();
        if (parsed != null) for (DashboardVehicle v : parsed) if (v != null && v.getVehicleId() != null) out.add(v);
        return out;
    }

    /** Loads the vehicle list without showing anything (for screens that need a default vehicle). */
    public static void load(@NonNull Activity activity, @NonNull LoadCallback cb) {
        CustomerSession.get(activity, new CustomerSession.Callback2() {
            @Override
            public void onCustomer(@NonNull String customerId) {
                ApiClient.getClient().create(ApiService.class).getCustomerDashboard(customerId).enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        try (ResponseBody body = response.body()) {
                            if (response.isSuccessful() && body != null) {
                                cb.onLoaded(parse(body.string()));
                                return;
                            }
                        } catch (Exception ignored) {
                            // fall through
                        }
                        cb.onFailed(response.code());
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        cb.onFailed(0);
                    }
                });
            }

            @Override
            public void onError(int httpCode) {
                cb.onFailed(httpCode);
            }
        });
    }

    public interface LoadCallback {
        void onLoaded(List<DashboardVehicle> vehicles);
        void onFailed(int httpCode);
    }
}
