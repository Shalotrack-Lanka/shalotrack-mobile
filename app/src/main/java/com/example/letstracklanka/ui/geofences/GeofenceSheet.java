package com.example.letstracklanka.ui.geofences;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateGeofenceRequest;
import com.example.letstracklanka.data.model.GeofenceResponse;
import com.example.letstracklanka.data.model.UpdateGeofenceRequest;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.ui.common.VehiclePickerSheet;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.InputValidator;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * New / edit geofence (bottom_sheet_geofence). The size slider runs 50 m to 5 km in 50 m steps. A
 * geofence made in Fleet can be bigger or off that grid; the slider then becomes continuous up to its
 * size so it can be shown without a crash, and its exact size is kept unless the user moves the slider.
 */
final class GeofenceSheet {

    static final int MIN_RADIUS = 50;
    static final int MAX_RADIUS = 5000;
    static final int STEP = 50;
    static final int DEFAULT_RADIUS = 200;

    interface Host {
        /** Live preview of the circle while the sheet is open. */
        void onPreview(@NonNull LatLng center, int radiusMeters);
        void onPreviewEnd();
        void onSaved();
        void onRemoved();
    }

    private final Activity activity;
    private final Host host;
    private final ApiService api = ApiClient.getClient().create(ApiService.class);
    private final BottomSheetDialog sheet;

    @Nullable private final GeofenceResponse existing;
    private final LatLng center;
    @Nullable private String scopeVehicleId;
    private boolean radiusTouched;
    private int radius;

    private final View form, confirm;
    private final TextInputLayout tilName;
    private final TextInputEditText etName;
    private final TextView tvRadius, tvScope, tvError, tvRemoveError;
    private final Slider slider;
    private final MaterialSwitch swEnter, swExit;
    private final MaterialButton btnSave, btnRemove, btnCancel, btnConfirmRemove, btnKeep;

    static void create(@NonNull Activity activity, @NonNull LatLng center, @Nullable String vehicleId,
                       @Nullable String vehiclePlate, @NonNull Host host) {
        new GeofenceSheet(activity, host, null, center, vehicleId, vehiclePlate).show();
    }

    static void edit(@NonNull Activity activity, @NonNull GeofenceResponse g, @NonNull Host host) {
        new GeofenceSheet(activity, host, g, new LatLng(g.getLatitude(), g.getLongitude()),
                g.getVehicleId(), g.getVehicleNumber()).show();
    }

    private GeofenceSheet(Activity activity, Host host, @Nullable GeofenceResponse existing, LatLng center,
                          @Nullable String vehicleId, @Nullable String vehiclePlate) {
        this.activity = activity;
        this.host = host;
        this.existing = existing;
        this.center = center;
        this.scopeVehicleId = vehicleId;

        sheet = new BottomSheetDialog(activity);
        View v = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_geofence, null);
        sheet.setContentView(v);
        form = v.findViewById(R.id.geofenceForm);
        confirm = v.findViewById(R.id.geofenceRemoveConfirm);
        tilName = v.findViewById(R.id.tilGeofenceName);
        etName = v.findViewById(R.id.etGeofenceName);
        tvRadius = v.findViewById(R.id.tvRadiusValue);
        tvScope = v.findViewById(R.id.tvGeofenceScope);
        tvError = v.findViewById(R.id.tvGeofenceError);
        tvRemoveError = v.findViewById(R.id.tvRemoveGeofenceError);
        slider = v.findViewById(R.id.sliderRadius);
        swEnter = v.findViewById(R.id.switchAlertOnEnter);
        swExit = v.findViewById(R.id.switchAlertOnExit);
        btnSave = v.findViewById(R.id.btnSaveGeofence);
        btnRemove = v.findViewById(R.id.btnRemoveGeofence);
        btnCancel = v.findViewById(R.id.btnCancelGeofence);
        btnConfirmRemove = v.findViewById(R.id.btnConfirmRemoveGeofence);
        btnKeep = v.findViewById(R.id.btnKeepGeofence);

        ((TextView) v.findViewById(R.id.tvGeofenceSheetTitle)).setText(
                existing == null ? R.string.st_geofence_new_title : R.string.st_geofence_edit_title);
        if (existing != null) {
            etName.setText(existing.getName());
            swEnter.setChecked(existing.isAlertOnEnter());
            swExit.setChecked(existing.isAlertOnExit());
            btnRemove.setVisibility(View.VISIBLE);
            ((TextView) v.findViewById(R.id.tvRemoveGeofenceTitle)).setText(
                    activity.getString(R.string.st_geofence_remove_title, existing.getName()));
        }
        showScope(vehiclePlate);
        setUpSlider(existing != null ? existing.getRadiusMeters() : DEFAULT_RADIUS);

        v.findViewById(R.id.rowGeofenceScope).setOnClickListener(x -> VehiclePickerSheet.show(activity, true, true,
                R.string.st_geofence_applies_to, R.string.st_geofence_scope_all, picked -> {
                    scopeVehicleId = picked != null ? picked.getVehicleId() : null;
                    showScope(picked != null ? picked.getVehicleNumber() : null);
                }));
        btnCancel.setOnClickListener(x -> sheet.dismiss());
        btnSave.setOnClickListener(x -> save());
        btnRemove.setOnClickListener(x -> {
            form.setVisibility(View.GONE);
            confirm.setVisibility(View.VISIBLE);
            tvRemoveError.setVisibility(View.GONE);
        });
        btnKeep.setOnClickListener(x -> {
            confirm.setVisibility(View.GONE);
            form.setVisibility(View.VISIBLE);
        });
        btnConfirmRemove.setOnClickListener(x -> remove());
        sheet.setOnDismissListener(d -> host.onPreviewEnd());
    }

    private void show() {
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.show();
        host.onPreview(center, radius);
    }

    private void setUpSlider(int initial) {
        radius = initial;
        boolean onGrid = initial >= MIN_RADIUS && initial <= MAX_RADIUS && (initial - MIN_RADIUS) % STEP == 0;
        if (onGrid) {
            slider.setValue(initial);
        } else {
            // Made elsewhere (Fleet allows up to 50 km): show it as it is.
            slider.setStepSize(0f);
            slider.setValueFrom(Math.min(MIN_RADIUS, initial));
            slider.setValueTo(Math.max(MAX_RADIUS, initial));
            slider.setValue(initial);
        }
        tvRadius.setText(GeofenceText.radius(initial));
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (!fromUser) return;
            radiusTouched = true;
            radius = onGrid ? Math.round(value) : Math.max(MIN_RADIUS, Math.round(value / 10f) * 10);
            tvRadius.setText(GeofenceText.radius(radius));
            host.onPreview(center, radius);
        });
    }

    private void showScope(@Nullable String plate) {
        if (scopeVehicleId == null) tvScope.setText(R.string.st_geofence_scope_all);
        else tvScope.setText(plate != null && !plate.trim().isEmpty() ? Plates.format(activity, plate)
                : activity.getString(R.string.st_geofence_scope_one));
    }

    private void save() {
        tilName.setError(null);
        tvError.setVisibility(View.GONE);
        String name = InputValidator.cleanLine(etName.getText() != null ? etName.getText().toString() : "");
        String nameError = InputValidator.label(name, 1, 50, activity.getString(R.string.st_geofence_name_field));
        if (nameError != null) {
            tilName.setError(nameError);
            etName.requestFocus();
            return;
        }
        int r = existing != null && !radiusTouched ? existing.getRadiusMeters() : radius;
        busy(true);
        Call<ResponseBody> call = existing == null
                ? api.addGeofence(new CreateGeofenceRequest(scopeVehicleId, name, center.latitude, center.longitude,
                    r, swEnter.isChecked(), swExit.isChecked()))
                : api.updateGeofence(existing.getGeofenceId(), new UpdateGeofenceRequest(scopeVehicleId, name,
                    existing.getLatitude(), existing.getLongitude(), r, swEnter.isChecked(), swExit.isChecked(),
                    existing.isActive()));
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> c, @NonNull Response<ResponseBody> response) {
                if (response.body() != null) response.body().close();
                if (!sheet.isShowing()) return;
                busy(false);
                if (response.isSuccessful()) {
                    sheet.dismiss();
                    host.onSaved();
                    return;
                }
                error(tvError, clientMessage(response, R.string.st_geofence_save_failed));
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> c, @NonNull Throwable t) {
                if (!sheet.isShowing()) return;
                busy(false);
                error(tvError, activity.getString(R.string.st_error_network));
            }
        });
    }

    private void remove() {
        if (existing == null) return;
        btnConfirmRemove.setEnabled(false);
        btnKeep.setEnabled(false);
        btnConfirmRemove.setText(R.string.st_action_removing);
        api.deleteGeofence(existing.getGeofenceId()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> c, @NonNull Response<ResponseBody> response) {
                if (response.body() != null) response.body().close();
                if (!sheet.isShowing()) return;
                if (response.isSuccessful() || response.code() == 404) {
                    // 404: already gone (removed elsewhere). Either way it is not there any more.
                    sheet.dismiss();
                    host.onRemoved();
                    return;
                }
                resetRemove();
                error(tvRemoveError, clientMessage(response, R.string.st_geofence_remove_failed));
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> c, @NonNull Throwable t) {
                if (!sheet.isShowing()) return;
                resetRemove();
                error(tvRemoveError, activity.getString(R.string.st_error_network));
            }
        });
    }

    private void resetRemove() {
        btnConfirmRemove.setEnabled(true);
        btnKeep.setEnabled(true);
        btnConfirmRemove.setText(R.string.st_geofence_remove);
    }

    private void busy(boolean b) {
        btnSave.setEnabled(!b);
        btnCancel.setEnabled(!b);
        btnRemove.setEnabled(!b);
        btnSave.setText(b ? R.string.st_action_saving : R.string.st_action_save);
        sheet.setCancelable(!b);
    }

    private String clientMessage(Response<ResponseBody> response, int fallback) {
        String message = activity.getString(fallback);
        if (response.code() >= 400 && response.code() < 500) {
            try (ResponseBody err = response.errorBody()) {
                message = ApiJson.message(err != null ? err.string() : null, message);
            } catch (Exception ignored) {
                // keep the generic message
            }
        }
        return message;
    }

    private static void error(TextView tv, String message) {
        tv.setText(message);
        tv.setVisibility(View.VISIBLE);
    }
}
