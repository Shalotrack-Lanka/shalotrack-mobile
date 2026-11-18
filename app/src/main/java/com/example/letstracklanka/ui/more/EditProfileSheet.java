package com.example.letstracklanka.ui.more;

import android.app.Activity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.UpdateCustomerRequest;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
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
 * Edit name (bottom_sheet_edit_profile). Works on the profile MoreActivity already loaded, so saving is
 * one request, not a re-read and a write. The phone number sent is always the one on the account: the
 * server refuses any other (it is the SMS-verified sign-in), and the field is not editable here.
 */
public final class EditProfileSheet {

    public interface Listener {
        void onSaved(@NonNull String fullName);
    }

    private EditProfileSheet() {}

    @android.annotation.SuppressLint("InflateParams") // dialog content has no parent to measure against
    public static void show(@NonNull Activity activity, @NonNull CustomerResponse customer, @NonNull Listener listener) {
        BottomSheetDialog sheet = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_edit_profile, null);
        sheet.setContentView(view);

        TextInputLayout tilFirst = view.findViewById(R.id.tilFirstName);
        TextInputLayout tilSurname = view.findViewById(R.id.tilSurname);
        TextInputEditText etFirst = view.findViewById(R.id.etFirstName);
        TextInputEditText etSurname = view.findViewById(R.id.etSurname);
        TextView tvPhone = view.findViewById(R.id.tvEditProfilePhone);
        TextView tvError = view.findViewById(R.id.tvEditProfileError);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveProfile);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancelProfile);

        String full = customer.getFullName() != null ? customer.getFullName().trim() : "";
        int space = full.indexOf(' ');
        etFirst.setText(space > 0 ? full.substring(0, space) : full);
        etSurname.setText(space > 0 ? full.substring(space + 1).trim() : "");
        tvPhone.setText(ProfileText.phone(customer.getPhoneNumber()));

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        view.findViewById(R.id.btnChangePhone).setOnClickListener(v -> {
            sheet.dismiss();
            PhoneChangeSheet.show(activity, customer.getPhoneNumber());
        });
        etSurname.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnSave.performClick();
                return true;
            }
            return false;
        });

        btnSave.setOnClickListener(v -> {
            tilFirst.setError(null);
            tilSurname.setError(null);
            tvError.setVisibility(View.GONE);

            String first = InputValidator.cleanLine(text(etFirst));
            String surname = InputValidator.cleanLine(text(etSurname));
            String fullName = surname.isEmpty() ? first : first + " " + surname;
            String nameError = InputValidator.name(fullName);
            if (nameError != null) {
                tilFirst.setError(nameError);
                etFirst.requestFocus();
                return;
            }
            if (fullName.equals(full)) {
                sheet.dismiss();
                return;
            }

            setBusy(btnSave, btnCancel, true);
            UpdateCustomerRequest request = new UpdateCustomerRequest(fullName,
                    customer.getPhoneNumber() != null ? customer.getPhoneNumber() : "",
                    customer.getAddress(), customer.getProfileImage());
            ApiService api = ApiClient.getClient().create(ApiService.class);
            api.updateCustomer(customer.getCustomerId(), request).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    if (response.body() != null) response.body().close();
                    if (!sheet.isShowing()) return;
                    setBusy(btnSave, btnCancel, false);
                    if (response.isSuccessful()) {
                        sheet.dismiss();
                        listener.onSaved(fullName);
                        return;
                    }
                    String message = activity.getString(R.string.st_profile_save_failed);
                    if (response.code() >= 400 && response.code() < 500) {
                        try (ResponseBody err = response.errorBody()) {
                            message = ApiJson.message(err != null ? err.string() : null, message);
                        } catch (Exception ignored) {
                            // keep the generic message
                        }
                    }
                    showError(tvError, message);
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    if (!sheet.isShowing()) return;
                    setBusy(btnSave, btnCancel, false);
                    showError(tvError, activity.getString(R.string.st_error_network));
                }
            });
        });

        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.show();
    }

    private static void setBusy(MaterialButton save, MaterialButton cancel, boolean busy) {
        save.setEnabled(!busy);
        cancel.setEnabled(!busy);
        save.setText(busy ? R.string.st_action_saving : R.string.st_action_save);
    }

    private static void showError(TextView tv, String message) {
        tv.setText(message);
        tv.setVisibility(View.VISIBLE);
    }

    private static String text(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString() : "";
    }
}
