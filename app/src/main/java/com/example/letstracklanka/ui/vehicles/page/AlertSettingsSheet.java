package com.example.letstracklanka.ui.vehicles.page;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.utils.ApiErrors;
import com.example.letstracklanka.utils.ApiJson;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Alert settings for one vehicle (bottom_sheet_alert_settings, AlertSettings board): over-speed limit, the
 * idling alert, and push on/off for power cut, ignition, over-speed, and low battery plus offline. The allowed
 * ranges come from the server with the current values, so the sliders can never offer a value the server would
 * refuse. Push switches are enforced by the server before it sends (a phone-side switch cannot stop a push
 * Android shows by itself); a muted alert is still saved and listed. SOS has no switch.
 */
public final class AlertSettingsSheet {

    public interface Listener {
        void onSaved(int speedLimitKmh, boolean idleEnabled, int idleMinutes);
    }

    private final Activity activity;
    private final String vehicleId;
    private final Listener listener;
    private final ApiService api = ApiClient.getClient().create(ApiService.class);
    private final BottomSheetDialog sheet;
    private final View form, progress, idleGroup;
    private final TextView tvSpeed, tvIdle, tvError;
    private final Slider sliderSpeed, sliderIdle;
    private final MaterialSwitch swIdle, swPowerCut, swIgnition, swOverspeed, swHealth;
    private final View powerCutWarning;
    private final MaterialButton btnSave;

    @SuppressLint("InflateParams") // dialog content has no parent to measure against
    private AlertSettingsSheet(Activity activity, String vehicleId, @Nullable String plate, Listener listener) {
        this.activity = activity;
        this.vehicleId = vehicleId;
        this.listener = listener;
        sheet = new BottomSheetDialog(activity);
        View v = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_alert_settings, null);
        sheet.setContentView(v);
        form = v.findViewById(R.id.alertSettingsForm);
        progress = v.findViewById(R.id.progressAlertSettings);
        idleGroup = v.findViewById(R.id.idleMinutesGroup);
        tvSpeed = v.findViewById(R.id.tvSpeedLimit);
        tvIdle = v.findViewById(R.id.tvIdleMinutes);
        tvError = v.findViewById(R.id.tvAlertSettingsError);
        sliderSpeed = v.findViewById(R.id.sliderSpeedLimit);
        sliderIdle = v.findViewById(R.id.sliderIdleMinutes);
        swIdle = v.findViewById(R.id.switchIdleAlert);
        swPowerCut = v.findViewById(R.id.switchPushPowerCut);
        swIgnition = v.findViewById(R.id.switchPushIgnition);
        swOverspeed = v.findViewById(R.id.switchPushOverspeed);
        swHealth = v.findViewById(R.id.switchPushHealth);
        powerCutWarning = v.findViewById(R.id.powerCutWarning);
        btnSave = v.findViewById(R.id.btnSaveAlertSettings);
        if (plate != null && !plate.trim().isEmpty()) {
            TextView forPlate = v.findViewById(R.id.tvAlertSettingsFor);
            forPlate.setText(activity.getString(R.string.st_alert_settings_for, Plates.plain(plate)));
            forPlate.setVisibility(View.VISIBLE);
        }
        v.findViewById(R.id.btnCancelAlertSettings).setOnClickListener(x -> sheet.dismiss());

        sliderSpeed.addOnChangeListener((s, value, fromUser) ->
                tvSpeed.setText(activity.getString(R.string.st_speed_kmh, Math.round(value))));
        sliderIdle.addOnChangeListener((s, value, fromUser) ->
                tvIdle.setText(activity.getString(R.string.st_duration_minutes, (long) Math.round(value))));
        swIdle.setOnCheckedChangeListener((b, on) -> idleGroup.setVisibility(on ? View.VISIBLE : View.GONE));
        // Power cut is the anti-theft signal: say so the moment it is switched off.
        swPowerCut.setOnCheckedChangeListener((b, on) -> powerCutWarning.setVisibility(on ? View.GONE : View.VISIBLE));
        btnSave.setOnClickListener(x -> save());
    }

    public static void show(@NonNull Activity activity, @NonNull String vehicleId, @Nullable String plate,
                            @NonNull Listener listener) {
        AlertSettingsSheet s = new AlertSettingsSheet(activity, vehicleId, plate, listener);
        s.sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        s.sheet.getBehavior().setSkipCollapsed(true);
        s.sheet.show();
        s.load();
    }

    private void load() {
        api.getVehicleAlertSettings(vehicleId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!sheet.isShowing()) {
                    ApiErrors.close(response);
                    return;
                }
                progress.setVisibility(View.GONE);
                if (!response.isSuccessful()) {
                    showError(ApiErrors.read(response, activity.getString(R.string.st_alert_settings_load_failed)).message);
                    return;
                }
                JsonObject d;
                try (ResponseBody b = response.body()) {
                    d = b != null ? ApiJson.dataObject(b.string()) : null;
                } catch (Exception e) {
                    d = null;
                }
                if (d == null) {
                    showError(activity.getString(R.string.st_alert_settings_load_failed));
                    return;
                }
                bind(d);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!sheet.isShowing()) return;
                progress.setVisibility(View.GONE);
                showError(activity.getString(R.string.st_error_network));
            }
        });
    }

    private void bind(JsonObject d) {
        int minSpeed = intOr(d, "minSpeedLimitKmh", 20);
        int maxSpeed = Math.max(minSpeed + 5, intOr(d, "maxSpeedLimitKmh", 160));
        int speed = clamp(intOr(d, "speedLimitKmh", intOr(d, "defaultSpeedLimitKmh", 80)), minSpeed, maxSpeed);
        int minIdle = intOr(d, "minIdleMinutes", 1);
        int maxIdle = Math.max(minIdle + 1, intOr(d, "maxIdleMinutes", 60));
        int idle = clamp(intOr(d, "idleAlertMinutes", 10), minIdle, maxIdle);
        JsonElement idleOn = ApiJson.get(d, "idleAlertEnabled");

        // Step 5 km/h only when the server's range sits on that grid; otherwise 1 km/h (a Slider throws on a
        // value or range that is off its step).
        boolean grid5 = (maxSpeed - minSpeed) % 5 == 0 && (speed - minSpeed) % 5 == 0;
        sliderSpeed.setStepSize(grid5 ? 5f : 1f);
        sliderSpeed.setValueFrom(minSpeed);
        sliderSpeed.setValueTo(maxSpeed);
        sliderSpeed.setValue(speed);
        sliderIdle.setStepSize(1f);
        sliderIdle.setValueFrom(minIdle);
        sliderIdle.setValueTo(maxIdle);
        sliderIdle.setValue(idle);
        tvSpeed.setText(activity.getString(R.string.st_speed_kmh, speed));
        tvIdle.setText(activity.getString(R.string.st_duration_minutes, (long) idle));
        boolean on = idleOn != null && idleOn.isJsonPrimitive() && idleOn.getAsBoolean();
        swIdle.setChecked(on);
        idleGroup.setVisibility(on ? View.VISIBLE : View.GONE);
        // Missing (an API from before the switches) reads as on: that is what the server does today.
        swPowerCut.setChecked(boolOr(d, "pushPowerCut", true));
        swIgnition.setChecked(boolOr(d, "pushIgnition", true));
        swOverspeed.setChecked(boolOr(d, "pushOverspeed", true));
        swHealth.setChecked(boolOr(d, "pushTrackerHealth", true));
        powerCutWarning.setVisibility(swPowerCut.isChecked() ? View.GONE : View.VISIBLE);
        form.setVisibility(View.VISIBLE);
        btnSave.setEnabled(true);
    }

    private void save() {
        tvError.setVisibility(View.GONE);
        btnSave.setEnabled(false);
        btnSave.setText(R.string.st_action_saving);
        final int speed = Math.round(sliderSpeed.getValue());
        final boolean idleOn = swIdle.isChecked();
        final int idle = Math.round(sliderIdle.getValue());
        JsonObject body = new JsonObject();
        body.addProperty("speedLimitKmh", speed);
        body.addProperty("idleAlertEnabled", idleOn);
        body.addProperty("idleAlertMinutes", idle);
        body.addProperty("pushPowerCut", swPowerCut.isChecked());
        body.addProperty("pushIgnition", swIgnition.isChecked());
        body.addProperty("pushOverspeed", swOverspeed.isChecked());
        body.addProperty("pushTrackerHealth", swHealth.isChecked());
        api.updateVehicleAlertSettings(vehicleId, body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!sheet.isShowing()) {
                    ApiErrors.close(response);
                    return;
                }
                btnSave.setText(R.string.st_action_save);
                btnSave.setEnabled(true);
                if (response.isSuccessful()) {
                    ApiErrors.close(response);
                    sheet.dismiss();
                    listener.onSaved(speed, idleOn, idle);
                    return;
                }
                showError(ApiErrors.read(response, activity.getString(R.string.st_alert_settings_save_failed)).message);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!sheet.isShowing()) return;
                btnSave.setText(R.string.st_action_save);
                btnSave.setEnabled(true);
                showError(activity.getString(R.string.st_error_network));
            }
        });
    }

    /** Reads the settings for the page row ("Speed limit 80 km/h · idling 10 min") without showing anything. */
    public static void loadSummary(@NonNull Activity activity, @NonNull String vehicleId, @NonNull Listener listener) {
        ApiClient.getClient().create(ApiService.class).getVehicleAlertSettings(vehicleId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    ApiErrors.close(response);
                    return;
                }
                try (ResponseBody b = response.body()) {
                    JsonObject d = b != null ? ApiJson.dataObject(b.string()) : null;
                    if (d == null || activity.isDestroyed()) return;
                    JsonElement on = ApiJson.get(d, "idleAlertEnabled");
                    listener.onSaved(intOr(d, "speedLimitKmh", 80), on != null && on.isJsonPrimitive() && on.getAsBoolean(),
                            intOr(d, "idleAlertMinutes", 10));
                } catch (Exception ignored) {
                    // the row keeps its neutral text
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                // the row keeps its neutral text
            }
        });
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private static int intOr(JsonObject o, String key, int fallback) {
        JsonElement e = ApiJson.get(o, key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static boolean boolOr(JsonObject o, String key, boolean fallback) {
        JsonElement e = ApiJson.get(o, key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
