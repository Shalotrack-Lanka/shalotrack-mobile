package com.example.letstracklanka.ui.devices;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateDeviceAssignmentRequest;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.DeviceLookupResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.ConfirmSheet;
import com.example.letstracklanka.ui.common.EdgeToEdge;
import com.example.letstracklanka.ui.common.EmptyState;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.Pill;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.ui.common.TopBar;
import com.example.letstracklanka.ui.vehicles.VehiclesActivity;
import com.example.letstracklanka.utils.ApiErrors;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.InputValidator;
import com.example.letstracklanka.utils.SecureScreen;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Trackers. One card per vehicle the customer owns, with Link or Unlink.
 *
 * Link:   GET api/GpsDevices/lookup/{imei} (rate limited per account) -> POST api/DeviceAssignments/assign
 * Unlink: GET api/DeviceAssignments/vehicle/{id} (find the active assignment)
 *         -> PATCH api/DeviceAssignments/{assignmentId}/unassign
 *
 * The server checks that the caller owns the vehicle (and refuses the shared demo vehicle). Unlinking
 * keeps the subscription on the device. Opened from a vehicle's page (EXTRA_VEHICLE_ID), it opens that
 * vehicle's form. Screens that matter are protected from overlays (SecureScreen). No Toast or dialog.
 */
public class DevicesActivity extends AppCompatActivity {

    private static final String TAG = "DevicesActivity";

    private ApiService api;

    private SwipeRefreshLayout swipe;
    private NestedScrollView scroll;
    private ProgressBar progress;
    private InlineBanner banner;
    private EmptyState empty;
    private LinearLayout list;

    /** Vehicle whose link form opens on first load (from a vehicle's page), then cleared. */
    @Nullable private String focusVehicleId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        SecureScreen.enable(this);
        setContentView(R.layout.activity_devices);
        TopBar.bind(this, R.string.st_trackers_title);
        EdgeToEdge.padBottom(findViewById(R.id.devicesContent));

        api = ApiClient.getClient().create(ApiService.class);
        swipe = findViewById(R.id.swipeDevices);
        scroll = findViewById(R.id.scrollDevices);
        progress = findViewById(R.id.progressDevices);
        banner = InlineBanner.in(this);
        empty = new EmptyState(findViewById(R.id.emptyState));
        list = findViewById(R.id.listDevices);
        focusVehicleId = getIntent().getStringExtra(VehiclesActivity.EXTRA_VEHICLE_ID);

        swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.st_navy));
        swipe.setOnRefreshListener(this::loadAll);

        progress.setVisibility(View.VISIBLE);
        loadAll();
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed();
    }

    // ---- loading ---------------------------------------------------------------------------

    private void loadAll() {
        api.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!alive()) {
                    ApiErrors.close(response);
                    return;
                }
                CustomerResponse customer = null;
                if (response.isSuccessful()) {
                    try (ResponseBody body = response.body()) {
                        if (body != null) customer = ApiJson.object(body.string(), CustomerResponse.class);
                    } catch (Exception e) {
                        Log.e(TAG, "profile parse error", e);
                    }
                } else {
                    ApiErrors.close(response);
                }
                if (customer != null && customer.getCustomerId() != null) {
                    loadVehicles(customer.getCustomerId());
                    return;
                }
                loadFailed(response.code() == 401 ? R.string.st_error_session_expired : R.string.st_trackers_load_failed);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!alive()) return;
                loadFailed(R.string.st_error_network);
            }
        });
    }

    private void loadVehicles(String customerId) {
        api.getVehiclesByCustomer(customerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!alive()) {
                    ApiErrors.close(response);
                    return;
                }
                List<VehicleResponse> vehicles = null;
                if (response.isSuccessful()) {
                    try (ResponseBody body = response.body()) {
                        if (body != null) vehicles = ApiJson.list(body.string(), VehicleResponse.class);
                    } catch (Exception e) {
                        Log.e(TAG, "vehicles parse error", e);
                    }
                } else {
                    ApiErrors.close(response);
                }
                if (vehicles == null) {
                    loadFailed(response.code() == 401 ? R.string.st_error_session_expired : R.string.st_trackers_load_failed);
                    return;
                }
                showVehicles(vehicles);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!alive()) return;
                loadFailed(R.string.st_error_network);
            }
        });
    }

    private void showVehicles(List<VehicleResponse> vehicles) {
        progress.setVisibility(View.GONE);
        swipe.setRefreshing(false);
        list.removeAllViews();
        Row focus = null;
        for (VehicleResponse vehicle : vehicles) {
            if (vehicle == null || vehicle.getVehicleId() == null) continue;
            Row row = new Row(vehicle, list);
            if (vehicle.getVehicleId().equals(focusVehicleId)) focus = row;
        }
        if (list.getChildCount() == 0) {
            empty.show(getString(R.string.st_trackers_empty_title), getString(R.string.st_trackers_empty_body), null, null);
        } else {
            empty.hide();
        }
        if (focus != null) {
            focusVehicleId = null;
            focus.focus();
        }
    }

    private void loadFailed(int message) {
        progress.setVisibility(View.GONE);
        swipe.setRefreshing(false);
        banner.showError(message, () -> {
            progress.setVisibility(list.getChildCount() == 0 ? View.VISIBLE : View.GONE);
            loadAll();
        });
    }

    // ---- one vehicle card --------------------------------------------------------------------

    private final class Row {
        private final VehicleResponse vehicle;
        private final String vehicleId;
        private final String plate;

        private final View root;
        private final TextView tvError;
        private final MaterialButton btnShowLink, btnLinkConfirm, btnLinkCancel;
        private final View groupLink;
        private final TextInputLayout layoutImei;
        private final TextInputEditText etImei;

        Row(VehicleResponse vehicle, ViewGroup parent) {
            this.vehicle = vehicle;
            this.vehicleId = vehicle.getVehicleId();
            this.plate = Plates.plain(vehicle.getVehicleNumber());

            root = getLayoutInflater().inflate(R.layout.item_device_vehicle, parent, false);
            parent.addView(root);

            ((TextView) root.findViewById(R.id.tvDevicePlate)).setText(Plates.format(DevicesActivity.this, vehicle.getVehicleNumber()));
            String make = vehicle.getMake() == null ? "" : vehicle.getMake();
            String model = vehicle.getModel() == null ? "" : vehicle.getModel();
            ((TextView) root.findViewById(R.id.tvDeviceVehicle)).setText((make + " " + model).trim());

            TextView pill = root.findViewById(R.id.tvDeviceState);
            tvError = root.findViewById(R.id.tvDeviceRowError);
            btnShowLink = root.findViewById(R.id.btnShowLink);
            groupLink = root.findViewById(R.id.groupLinkForm);
            layoutImei = root.findViewById(R.id.layoutImei);
            etImei = root.findViewById(R.id.etImei);
            btnLinkConfirm = root.findViewById(R.id.btnLinkConfirm);
            btnLinkCancel = root.findViewById(R.id.btnLinkCancel);

            if (vehicle.hasGpsDevice()) {
                Pill.apply(pill, getString(R.string.st_tracker_linked), Pill.Tone.GREEN);
                String imei = vehicle.getImei();
                String tail = imei != null && imei.length() >= 4 ? imei.substring(imei.length() - 4) : null;
                ((TextView) root.findViewById(R.id.tvDeviceImei)).setText(tail != null
                        ? getString(R.string.st_tracker_ending, tail) : getString(R.string.st_tracker_linked_plain));
                root.findViewById(R.id.groupLinked).setVisibility(View.VISIBLE);
                MaterialButton unlink = root.findViewById(R.id.btnUnlink);
                unlink.setContentDescription(getString(R.string.st_tracker_unlink_title, plate));
                unlink.setOnClickListener(v -> confirmUnlink());
            } else {
                Pill.apply(pill, getString(R.string.st_tracker_none), Pill.Tone.GREY);
                btnShowLink.setVisibility(View.VISIBLE);
                btnShowLink.setOnClickListener(v -> showLinkForm());
            }

            btnLinkCancel.setOnClickListener(v -> hideLinkForm());
            btnLinkConfirm.setOnClickListener(v -> link());
            etImei.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    link();
                    return true;
                }
                return false;
            });
        }

        /** Opened from a vehicle's page: show its form (or its card, when it already has a tracker). */
        void focus() {
            if (!vehicle.hasGpsDevice()) showLinkForm();
            scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, root.getTop() - 24)));
        }

        private void showError(@Nullable CharSequence message) {
            if (message == null) {
                tvError.setVisibility(View.GONE);
                return;
            }
            tvError.setText(message);
            tvError.setVisibility(View.VISIBLE);
        }

        private void showLinkForm() {
            showError(null);
            btnShowLink.setVisibility(View.GONE);
            groupLink.setVisibility(View.VISIBLE);
            etImei.requestFocus();
        }

        private void hideLinkForm() {
            showError(null);
            layoutImei.setError(null);
            groupLink.setVisibility(View.GONE);
            btnShowLink.setVisibility(View.VISIBLE);
        }

        private void setBusy(boolean busy) {
            btnLinkConfirm.setEnabled(!busy);
            btnLinkCancel.setEnabled(!busy);
            etImei.setEnabled(!busy);
            btnLinkConfirm.setText(busy ? R.string.st_tracker_linking : R.string.st_tracker_check_link);
        }

        // -- link --

        private void link() {
            if (!btnLinkConfirm.isEnabled()) return;
            layoutImei.setError(null);
            showError(null);

            String imei = etImei.getText() != null ? etImei.getText().toString().replaceAll("\\D", "") : "";
            if (InputValidator.imei(imei) != null) {
                layoutImei.setError(getString(R.string.st_tracker_imei_invalid));
                return;
            }

            setBusy(true);
            api.lookupDeviceByImei(imei).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    if (!alive()) {
                        ApiErrors.close(response);
                        return;
                    }
                    String deviceId = null;
                    if (response.isSuccessful()) {
                        try (ResponseBody body = response.body()) {
                            DeviceLookupResponse device = body != null
                                    ? ApiJson.object(body.string(), DeviceLookupResponse.class) : null;
                            deviceId = device != null ? device.getDeviceId() : null;
                        } catch (Exception e) {
                            Log.e(TAG, "lookup parse error", e);
                        }
                    } else {
                        ApiErrors.close(response);
                    }
                    if (deviceId != null) {
                        assign(deviceId);
                        return;
                    }
                    setBusy(false);
                    int code = response.code();
                    if (code == 404 || response.isSuccessful()) showError(getString(R.string.st_tracker_not_found));
                    else if (code == 429) showError(getString(R.string.st_addv_imei_too_many));
                    else if (code == 401) showError(getString(R.string.st_error_session_expired));
                    else showError(getString(R.string.st_tracker_lookup_failed));
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    if (!alive()) return;
                    setBusy(false);
                    showError(getString(R.string.st_error_network));
                }
            });
        }

        private void assign(String deviceId) {
            api.assignDevice(new CreateDeviceAssignmentRequest(vehicleId, deviceId)).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    if (!alive()) {
                        ApiErrors.close(response);
                        return;
                    }
                    if (response.isSuccessful()) {
                        ApiErrors.close(response);
                        setResult(RESULT_OK);
                        banner.showSuccess(getString(R.string.st_tracker_linked_done, plate));
                        loadAll();
                        return;
                    }
                    setBusy(false);
                    int code = response.code();
                    if (code == 401) {
                        ApiErrors.close(response);
                        showError(getString(R.string.st_error_session_expired));
                    } else {
                        // 4xx carry the server's own words ("already linked to another vehicle"); 5xx never shown.
                        showError(ApiErrors.read(response, getString(R.string.st_tracker_link_failed)).message);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    if (!alive()) return;
                    setBusy(false);
                    showError(getString(R.string.st_error_network));
                }
            });
        }

        // -- unlink --

        private void confirmUnlink() {
            showError(null);
            ConfirmSheet.show(DevicesActivity.this, getString(R.string.st_tracker_unlink_title, plate),
                    getString(R.string.st_tracker_unlink_body), getString(R.string.st_tracker_unlink_action),
                    getString(R.string.st_tracker_unlink_busy), this::unlink);
        }

        private void unlink(ConfirmSheet.Done done) {
            api.getDeviceAssignmentsForVehicle(vehicleId).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    String assignmentId = null;
                    boolean readOk = false;
                    if (response.isSuccessful()) {
                        try (ResponseBody body = response.body()) {
                            JsonArray all = body != null ? ApiJson.dataArray(body.string()) : null;
                            if (all != null) {
                                readOk = true;
                                assignmentId = findActiveAssignmentId(all);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "assignments parse error", e);
                        }
                    } else {
                        ApiErrors.close(response);
                    }
                    if (assignmentId != null) {
                        unassign(assignmentId, done);
                        return;
                    }
                    if (readOk) {
                        // Nothing active any more (changed elsewhere): show the real state.
                        done.succeeded();
                        if (!alive()) return;
                        banner.showSuccess(getString(R.string.st_tracker_gone, plate));
                        loadAll();
                        return;
                    }
                    done.failed(getString(response.code() == 401
                            ? R.string.st_error_session_expired : R.string.st_tracker_unlink_failed));
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    done.failed(getString(R.string.st_error_network));
                }
            });
        }

        private void unassign(String assignmentId, ConfirmSheet.Done done) {
            api.unassignDevice(assignmentId).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        ApiErrors.close(response);
                        done.succeeded();
                        if (!alive()) return;
                        setResult(RESULT_OK);
                        banner.showSuccess(getString(R.string.st_tracker_unlinked, plate));
                        loadAll();
                        return;
                    }
                    if (response.code() == 401) {
                        ApiErrors.close(response);
                        done.failed(getString(R.string.st_error_session_expired));
                        return;
                    }
                    done.failed(ApiErrors.read(response, getString(R.string.st_tracker_unlink_failed)).message);
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    done.failed(getString(R.string.st_error_network));
                }
            });
        }
    }

    /**
     * The API serialises the status enum as a number (Inactive 0, Active 1, Removed 2); accept the
     * name too so a serializer change can't silently break unlinking.
     */
    private static String findActiveAssignmentId(JsonArray assignments) {
        for (JsonElement element : assignments) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (ApiJson.get(item, "removedAt") != null) continue;

            JsonElement status = ApiJson.get(item, "status");
            boolean active = false;
            if (status != null && status.isJsonPrimitive()) {
                if (status.getAsJsonPrimitive().isNumber()) {
                    active = status.getAsInt() == 1;
                } else {
                    active = "Active".equalsIgnoreCase(status.getAsString());
                }
            }
            if (!active) continue;

            String id = ApiJson.string(item, "assignmentId");
            if (id != null && !id.isEmpty()) return id;
        }
        return null;
    }
}
