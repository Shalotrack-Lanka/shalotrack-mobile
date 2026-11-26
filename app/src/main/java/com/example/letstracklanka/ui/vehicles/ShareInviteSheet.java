package com.example.letstracklanka.ui.vehicles;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.data.model.InviteVehicleShareRequest;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.VehicleField;
import com.example.letstracklanka.ui.more.ProfileText;
import com.example.letstracklanka.utils.ApiErrors;
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
 * "Share a vehicle" (POST api/VehicleShares/invite). Only the customer's own vehicles are offered. The API
 * answers the same whether or not the number has a ShaloTrack account and is rate limited per account, so
 * this sheet can't be used to find out who uses ShaloTrack: on success it says "if this number uses
 * ShaloTrack, they'll see it".
 */
final class ShareInviteSheet {

    interface Listener {
        void onSent(@NonNull String phoneShown);
    }

    private ShareInviteSheet() {}

    @SuppressLint("InflateParams") // dialog content has no parent to measure against
    static void show(@NonNull Activity activity, @Nullable String vehicleId, @NonNull Listener listener) {
        BottomSheetDialog sheet = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_share_invite, null);
        sheet.setContentView(view);

        VehicleField vehicle = new VehicleField(activity, view.findViewById(R.id.stVehicleField), true);
        TextInputLayout tilPhone = view.findViewById(R.id.tilSharePhone);
        TextInputEditText etPhone = view.findViewById(R.id.etSharePhone);
        InlineBanner banner = new InlineBanner(view.findViewById(R.id.inlineBanner));
        MaterialButton send = view.findViewById(R.id.btnShareSend);
        MaterialButton cancel = view.findViewById(R.id.btnShareCancel);

        cancel.setOnClickListener(v -> sheet.dismiss());
        etPhone.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                send.performClick();
                return true;
            }
            return false;
        });

        send.setOnClickListener(v -> {
            tilPhone.setError(null);
            banner.hide();
            DashboardVehicle chosen = vehicle.get();
            if (chosen == null) {
                banner.showError(activity.getString(vehicle.hasNoVehicles()
                        ? R.string.st_field_vehicle_none_owned : R.string.st_field_vehicle_pick), null);
                return;
            }
            String phone = InputValidator.cleanLine(etPhone.getText() != null ? etPhone.getText().toString() : "");
            String phoneError = InputValidator.phone(phone);
            if (phoneError != null) {
                tilPhone.setError(phoneError);
                etPhone.requestFocus();
                return;
            }
            String e164 = InputValidator.toE164Any(phone);

            setBusy(sheet, send, cancel, vehicle, true);
            ApiClient.getClient().create(ApiService.class)
                    .inviteVehicleShare(new InviteVehicleShareRequest(chosen.getVehicleId(), e164))
                    .enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                            if (!sheet.isShowing()) {
                                ApiErrors.close(response);
                                return;
                            }
                            setBusy(sheet, send, cancel, vehicle, false);
                            if (response.isSuccessful()) {
                                ApiErrors.close(response);
                                sheet.dismiss();
                                listener.onSent(ProfileText.phone(e164));
                                return;
                            }
                            String message;
                            if (response.code() == 401) {
                                ApiErrors.close(response);
                                message = activity.getString(R.string.st_error_session_expired);
                            } else if (response.code() == 429) {
                                ApiErrors.close(response);
                                message = activity.getString(R.string.st_share_rate_limited);
                            } else {
                                message = ApiErrors.read(response, activity.getString(R.string.st_share_send_failed)).message;
                            }
                            banner.showError(message, null);
                        }

                        @Override
                        public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                            if (!sheet.isShowing()) return;
                            setBusy(sheet, send, cancel, vehicle, false);
                            banner.showError(activity.getString(R.string.st_error_network), null);
                        }
                    });
        });

        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.show();
        vehicle.load(vehicleId);
    }

    private static void setBusy(BottomSheetDialog sheet, MaterialButton send, MaterialButton cancel,
                                VehicleField vehicle, boolean busy) {
        send.setEnabled(!busy);
        cancel.setEnabled(!busy);
        vehicle.setEnabled(!busy);
        sheet.setCancelable(!busy);
        send.setText(busy ? R.string.st_share_sending : R.string.st_share_send);
    }
}
