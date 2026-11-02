package com.example.letstracklanka.ui.devices;

import com.example.letstracklanka.utils.SecureScreen;
import com.example.letstracklanka.utils.InputValidator;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateDeviceAssignmentRequest;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.DeviceLookupResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.utils.ApiJson;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Settings -> Devices. One card per vehicle the customer owns, with a link or unlink action.
 *
 * Link:   GET api/GpsDevices/lookup/{imei} -> POST api/DeviceAssignments/assign
 * Unlink: GET api/DeviceAssignments/vehicle/{id} (find the active assignment)
 *         -> PATCH api/DeviceAssignments/{assignmentId}/unassign
 *
 * The server checks that the caller owns the vehicle (and refuses the shared demo vehicle).
 * Unlinking keeps the subscription on the device. All forms, confirmations and results are
 * inline: no Toast, Snackbar or dialog.
 */
public class DevicesActivity extends AppCompatActivity {

    private static final String TAG = "DevicesActivity";

    private ApiService api;

    private ProgressBar progress;
    private View groupError;
    private TextView tvError, tvEmpty;
    private LinearLayout list;

    /** vehicleId -> note to show on that card after the next reload (e.g. "Device linked."). */
    private final Map<String, String> pendingNotes = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SecureScreen.enable(this);
        setContentView(R.layout.activity_devices);

        api = ApiClient.getClient().create(ApiService.class);

        progress = findViewById(R.id.progressDevices);
        groupError = findViewById(R.id.groupDevicesError);
        tvError = findViewById(R.id.tvDevicesError);
        tvEmpty = findViewById(R.id.tvDevicesEmpty);
        list = findViewById(R.id.listDevices);

        findViewById(R.id.btnBackDevices).setOnClickListener(v -> finish());
        findViewById(R.id.btnDevicesRetry).setOnClickListener(v -> loadAll());

        loadAll();
    }

    // ---- loading ---------------------------------------------------------------------------

    private void loadAll() {
        progress.setVisibility(View.VISIBLE);
        groupError.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        list.removeAllViews();

        api.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) return;
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        CustomerResponse customer = ApiJson.object(body.string(), CustomerResponse.class);
                        if (customer != null && customer.getCustomerId() != null) {
                            loadVehicles(customer.getCustomerId());
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "profile parse error", e);
                }
                showLoadError("We couldn't load your vehicles.");
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                Log.e(TAG, "profile network error", t);
                showLoadError("No connection. Check your internet and try again.");
            }
        });
    }

    private void loadVehicles(String customerId) {
        api.getVehiclesByCustomer(customerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) return;
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<VehicleResponse> vehicles = ApiJson.list(body.string(), VehicleResponse.class);
                        if (vehicles != null) {
                            showVehicles(vehicles);
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "vehicles parse error", e);
                }
                showLoadError("We couldn't load your vehicles.");
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                Log.e(TAG, "vehicles network error", t);
                showLoadError("No connection. Check your internet and try again.");
            }
        });
    }

    private void showVehicles(List<VehicleResponse> vehicles) {
        progress.setVisibility(View.GONE);
        list.removeAllViews();
        if (vehicles.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        for (VehicleResponse vehicle : vehicles) {
            if (vehicle.getVehicleId() == null) continue;
            new Row(vehicle, list);
        }
    }

    private void showLoadError(String message) {
        progress.setVisibility(View.GONE);
        tvError.setText(message);
        groupError.setVisibility(View.VISIBLE);
    }

    private static String readClientError(Response<?> response) {
        int code = response.code();
        if (code < 400 || code >= 500) return null;
        try (ResponseBody err = response.errorBody()) {
            return err != null ? err.string() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- one vehicle card --------------------------------------------------------------------

    private final class Row {
        private final VehicleResponse vehicle;
        private final String vehicleId;

        private final View root;
        private final TextView tvState, tvMessage;
        private final MaterialButton btnAction, btnLinkConfirm, btnLinkCancel, btnUnlinkConfirm, btnUnlinkCancel;
        private final View groupLink, groupUnlink;
        private final TextInputLayout layoutImei;
        private final TextInputEditText etImei;

        Row(VehicleResponse vehicle, ViewGroup parent) {
            this.vehicle = vehicle;
            this.vehicleId = vehicle.getVehicleId();

            root = getLayoutInflater().inflate(R.layout.item_device_vehicle, parent, false);
            parent.addView(root);

            TextView tvName = root.findViewById(R.id.tvDeviceVehicleName);
            TextView tvSub = root.findViewById(R.id.tvDeviceVehicleSub);
            tvState = root.findViewById(R.id.tvDeviceState);
            tvMessage = root.findViewById(R.id.tvDeviceRowMessage);
            btnAction = root.findViewById(R.id.btnDeviceAction);
            groupLink = root.findViewById(R.id.groupLinkForm);
            layoutImei = root.findViewById(R.id.layoutImei);
            etImei = root.findViewById(R.id.etImei);
            btnLinkConfirm = root.findViewById(R.id.btnLinkConfirm);
            btnLinkCancel = root.findViewById(R.id.btnLinkCancel);
            groupUnlink = root.findViewById(R.id.groupUnlinkConfirm);
            btnUnlinkConfirm = root.findViewById(R.id.btnUnlinkConfirm);
            btnUnlinkCancel = root.findViewById(R.id.btnUnlinkCancel);

            String number = vehicle.getVehicleNumber() != null ? vehicle.getVehicleNumber() : "Vehicle";
            tvName.setText(number);

            StringBuilder sub = new StringBuilder();
            if (vehicle.getMake() != null && !vehicle.getMake().isEmpty()) sub.append(vehicle.getMake());
            if (vehicle.getModel() != null && !vehicle.getModel().isEmpty()) {
                if (sub.length() > 0) sub.append(' ');
                sub.append(vehicle.getModel());
            }
            tvSub.setText(sub);
            tvSub.setVisibility(sub.length() == 0 ? View.GONE : View.VISIBLE);

            if (vehicle.hasGpsDevice()) {
                String imei = vehicle.getImei();
                String tail = imei != null && imei.length() >= 4 ? imei.substring(imei.length() - 4) : null;
                tvState.setText(tail != null ? "GPS device linked (IMEI ending " + tail + ")" : "GPS device linked");
                btnAction.setText("Unlink device");
                btnAction.setOnClickListener(v -> showUnlinkConfirm());
            } else {
                tvState.setText("No GPS device linked");
                btnAction.setText("Link a device");
                btnAction.setOnClickListener(v -> showLinkForm());
            }

            btnLinkCancel.setOnClickListener(v -> resetActions());
            btnLinkConfirm.setOnClickListener(v -> link());
            btnUnlinkCancel.setOnClickListener(v -> resetActions());
            btnUnlinkConfirm.setOnClickListener(v -> unlink());

            String note = pendingNotes.remove(vehicleId);
            if (note != null) showMessage(note, true);
        }

        // -- states --

        private void resetActions() {
            groupLink.setVisibility(View.GONE);
            groupUnlink.setVisibility(View.GONE);
            btnAction.setVisibility(View.VISIBLE);
            layoutImei.setError(null);
            showMessage(null, false);
        }

        private void showLinkForm() {
            showMessage(null, false);
            btnAction.setVisibility(View.GONE);
            groupLink.setVisibility(View.VISIBLE);
            etImei.requestFocus();
        }

        private void showUnlinkConfirm() {
            showMessage(null, false);
            btnAction.setVisibility(View.GONE);
            groupUnlink.setVisibility(View.VISIBLE);
        }

        private void setBusy(boolean busy, boolean linking) {
            btnLinkConfirm.setEnabled(!busy);
            btnLinkCancel.setEnabled(!busy);
            btnUnlinkConfirm.setEnabled(!busy);
            btnUnlinkCancel.setEnabled(!busy);
            btnLinkConfirm.setText(busy && linking ? "Linking..." : "Link device");
            btnUnlinkConfirm.setText(busy && !linking ? "Unlinking..." : "Unlink device");
        }

        private void showMessage(String message, boolean success) {
            if (message == null) {
                tvMessage.setVisibility(View.GONE);
                return;
            }
            tvMessage.setText(message);
            if (success) {
                tvMessage.setBackgroundColor(Color.parseColor("#F0FDF4"));
                tvMessage.setTextColor(ContextCompat.getColor(DevicesActivity.this, R.color.status_success));
            } else {
                tvMessage.setBackgroundColor(ContextCompat.getColor(DevicesActivity.this, R.color.danger_banner_bg));
                tvMessage.setTextColor(ContextCompat.getColor(DevicesActivity.this, R.color.danger_banner_text));
            }
            tvMessage.setVisibility(View.VISIBLE);
        }

        private boolean alive() {
            return !isFinishing() && !isDestroyed();
        }

        // -- link --

        private void link() {
            layoutImei.setError(null);
            showMessage(null, false);

            String imei = etImei.getText() != null ? etImei.getText().toString().replaceAll("\\D", "") : "";
            String imeiError = InputValidator.imei(imei);
            if (imeiError != null) {
                layoutImei.setError("Enter the 15-digit IMEI from the device label (check for a typo)");
                return;
            }

            setBusy(true, true);
            api.lookupDeviceByImei(imei).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    if (!alive()) return;
                    String deviceId = null;
                    try (ResponseBody body = response.body()) {
                        if (response.isSuccessful() && body != null) {
                            DeviceLookupResponse device = ApiJson.object(body.string(), DeviceLookupResponse.class);
                            deviceId = device != null ? device.getDeviceId() : null;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "lookup parse error", e);
                    }
                    if (deviceId != null) {
                        assign(deviceId);
                        return;
                    }
                    setBusy(false, true);
                    if (response.code() == 404 || response.isSuccessful()) {
                        showMessage("We couldn't find a device with that IMEI. Check the number on the label and try again.", false);
                    } else if (response.code() == 401) {
                        showMessage("Your sign-in has expired. Please sign out and sign in again.", false);
                    } else {
                        Log.w(TAG, "lookup failed, code " + response.code());
                        showMessage("We couldn't check that device. Please try again.", false);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    if (!alive()) return;
                    Log.e(TAG, "lookup network error", t);
                    setBusy(false, true);
                    showMessage("No connection. Check your internet and try again.", false);
                }
            });
        }

        private void assign(String deviceId) {
            api.assignDevice(new CreateDeviceAssignmentRequest(vehicleId, deviceId)).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    if (!alive()) return;
                    if (response.isSuccessful()) {
                        pendingNotes.put(vehicleId, "Device linked.");
                        loadAll();
                        return;
                    }
                    setBusy(false, true);
                    int code = response.code();
                    if (code == 400 || code == 403 || code == 409 || code == 422) {
                        showMessage(ApiJson.message(readClientError(response), "This device couldn't be linked."), false);
                    } else if (code == 401) {
                        showMessage("Your sign-in has expired. Please sign out and sign in again.", false);
                    } else {
                        Log.w(TAG, "assign failed, code " + code);
                        showMessage("We couldn't link the device. Please try again.", false);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    if (!alive()) return;
                    Log.e(TAG, "assign network error", t);
                    setBusy(false, true);
                    showMessage("No connection. Check your internet and try again.", false);
                }
            });
        }

        // -- unlink --

        private void unlink() {
            showMessage(null, false);
            setBusy(true, false);

            api.getDeviceAssignmentsForVehicle(vehicleId).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    if (!alive()) return;
                    String assignmentId = null;
                    boolean readOk = false;
                    try (ResponseBody body = response.body()) {
                        if (response.isSuccessful() && body != null) {
                            JsonArray all = ApiJson.dataArray(body.string());
                            if (all != null) {
                                readOk = true;
                                assignmentId = findActiveAssignmentId(all);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "assignments parse error", e);
                    }
                    if (assignmentId != null) {
                        unassign(assignmentId);
                        return;
                    }
                    setBusy(false, false);
                    if (readOk) {
                        // Nothing active any more (changed elsewhere): show the real state.
                        pendingNotes.put(vehicleId, "This vehicle has no linked device any more.");
                        loadAll();
                    } else {
                        showMessage("We couldn't check this vehicle's device. Please try again.", false);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    if (!alive()) return;
                    Log.e(TAG, "assignments network error", t);
                    setBusy(false, false);
                    showMessage("No connection. Check your internet and try again.", false);
                }
            });
        }

        private void unassign(String assignmentId) {
            api.unassignDevice(assignmentId).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    if (!alive()) return;
                    if (response.isSuccessful()) {
                        pendingNotes.put(vehicleId, "Device unlinked. Its subscription stays with the device.");
                        loadAll();
                        return;
                    }
                    setBusy(false, false);
                    int code = response.code();
                    if (code == 400 || code == 403 || code == 404 || code == 409) {
                        showMessage(ApiJson.message(readClientError(response), "This device couldn't be unlinked."), false);
                    } else if (code == 401) {
                        showMessage("Your sign-in has expired. Please sign out and sign in again.", false);
                    } else {
                        Log.w(TAG, "unassign failed, code " + code);
                        showMessage("We couldn't unlink the device. Please try again.", false);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    if (!alive()) return;
                    Log.e(TAG, "unassign network error", t);
                    setBusy(false, false);
                    showMessage("No connection. Check your internet and try again.", false);
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