package com.example.letstracklanka.ui.places;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateSavedPlaceRequest;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.ui.common.TimeText;
import com.example.letstracklanka.ui.common.VehicleField;
import com.example.letstracklanka.ui.main.AddressResolver;
import com.example.letstracklanka.utils.ApiErrors;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.InputValidator;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * "Save a place": where the chosen vehicle is right now, read from its tracker (api/CurrentLocations),
 * never the phone's GPS. The position is fetched when the vehicle is chosen and shown before saving, so
 * the customer sees what they are naming.
 */
final class SavePlaceSheet {

    interface Listener {
        void onSaved(@NonNull String name);
    }

    private final Activity activity;
    private final Listener listener;
    private final BottomSheetDialog sheet;
    private final InlineBanner banner;
    private final TextInputLayout tilName;
    private final TextInputEditText etName;
    private final MaterialButton btnSave;
    private final MaterialButton btnCancel;
    private final VehicleField vehicleField;

    /** Position of the chosen vehicle, once read. */
    @Nullable private double[] fix;
    /** Bumped on every vehicle change so a slow reply for the previous vehicle is ignored. */
    private int request;

    @SuppressLint("InflateParams") // dialog content has no parent to measure against
    private SavePlaceSheet(@NonNull Activity activity, @NonNull Listener listener) {
        this.activity = activity;
        this.listener = listener;
        sheet = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_save_place, null);
        sheet.setContentView(view);

        banner = new InlineBanner(view.findViewById(R.id.inlineBanner));
        tilName = view.findViewById(R.id.tilPlaceName);
        etName = view.findViewById(R.id.etPlaceName);
        btnSave = view.findViewById(R.id.btnSavePlaceConfirm);
        btnCancel = view.findViewById(R.id.btnSavePlaceCancel);
        vehicleField = new VehicleField(activity, view.findViewById(R.id.stVehicleField), false);
        vehicleField.setListener(this::locate);

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        btnSave.setOnClickListener(v -> save());
        etName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && btnSave.isEnabled()) {
                btnSave.performClick();
                return true;
            }
            return false;
        });
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
    }

    static void show(@NonNull Activity activity, @NonNull Listener listener) {
        SavePlaceSheet s = new SavePlaceSheet(activity, listener);
        s.sheet.show();
        s.vehicleField.load(null);
    }

    private void locate(@Nullable DashboardVehicle vehicle) {
        fix = null;
        btnSave.setEnabled(false);
        banner.hide();
        final int mine = ++request;
        if (vehicle == null) return;
        String plate = Plates.plain(vehicle.getVehicleNumber());
        banner.showInfo(activity.getString(R.string.st_place_locating, plate), null, null);

        ApiClient.getClient().create(ShaloTrackApi.class).getVehicleLocation(vehicle.getVehicleId())
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        if (mine != request || !sheet.isShowing()) {
                            ApiErrors.close(response);
                            return;
                        }
                        LocationResponse loc = null;
                        if (response.isSuccessful()) {
                            try (ResponseBody body = response.body()) {
                                if (body != null) loc = ApiJson.object(body.string(), LocationResponse.class);
                            } catch (Exception ignored) {
                                loc = null;
                            }
                        } else {
                            ApiErrors.close(response);
                        }
                        if (loc == null || !valid(loc.getLatitude(), loc.getLongitude())) {
                            boolean noFix = response.isSuccessful() || response.code() == 404;
                            if (noFix) {
                                banner.showError(activity.getString(R.string.st_place_no_fix, plate), null);
                            } else {
                                banner.showError(activity.getString(R.string.st_place_fix_failed), () -> locate(vehicle));
                            }
                            return;
                        }
                        double lat = loc.getLatitude();
                        double lng = loc.getLongitude();
                        String reported = TimeText.ago(activity, loc.getLastUpdate());
                        fix = new double[]{lat, lng};
                        btnSave.setEnabled(true);
                        banner.showInfo(activity.getString(R.string.st_place_now_at_coords, reported), null, null);
                        AddressResolver.resolveCached(activity, lat, lng, address -> {
                            if (mine == request && sheet.isShowing()) {
                                banner.showInfo(activity.getString(R.string.st_place_now_at, address, reported), null, null);
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        if (mine != request || !sheet.isShowing()) return;
                        banner.showError(activity.getString(R.string.st_error_network), () -> locate(vehicle));
                    }
                });
    }

    private void save() {
        tilName.setError(null);
        if (fix == null) return;
        String name = InputValidator.cleanLine(etName.getText() != null ? etName.getText().toString() : "");
        String error = InputValidator.label(name, 1, 100, "a name for this place");
        if (error != null) {
            tilName.setError(error);
            etName.requestFocus();
            return;
        }
        setBusy(true);
        ApiClient.getClient().create(ApiService.class)
                .addPlace(new CreateSavedPlaceRequest(name, fix[0], fix[1]))
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        if (!sheet.isShowing()) {
                            ApiErrors.close(response);
                            return;
                        }
                        setBusy(false);
                        if (response.isSuccessful()) {
                            ApiErrors.close(response);
                            sheet.dismiss();
                            listener.onSaved(name);
                            return;
                        }
                        ApiErrors.Failure f = ApiErrors.read(response, activity.getString(R.string.st_place_save_failed));
                        banner.showError(f.code == 401 ? activity.getString(R.string.st_error_session_expired) : f.message, null);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        if (!sheet.isShowing()) return;
                        setBusy(false);
                        banner.showError(activity.getString(R.string.st_error_network), null);
                    }
                });
    }

    private void setBusy(boolean busy) {
        btnSave.setEnabled(!busy && fix != null);
        btnCancel.setEnabled(!busy);
        vehicleField.setEnabled(!busy);
        sheet.setCancelable(!busy);
        btnSave.setText(busy ? R.string.st_action_saving : R.string.st_place_save);
    }

    private static boolean valid(double lat, double lng) {
        return !(lat == 0 && lng == 0) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }
}
