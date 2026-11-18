package com.example.letstracklanka.ui.more;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.auth.PhoneNumbers;
import com.example.letstracklanka.utils.ApiErrors;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.SignOut;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Change the sign-in number (bottom_sheet_phone_change), Fleet parity. Three steps in one sheet:
 * new number, then the 6-digit code texted to it, then done. All the protection is on the server
 * (recent sign-in, one change a day, hashed single-use code, old number told, every session revoked);
 * this sheet only explains what happens and handles each answer. After a change the phone signs out,
 * because the server has revoked this session.
 */
public final class PhoneChangeSheet {

    private enum Step { NUMBER, CODE, DONE, REAUTH }

    private final Activity activity;
    private final ApiService api = ApiClient.getClient().create(ApiService.class);
    private final BottomSheetDialog sheet;
    private final TextView tvTitle, tvBody, tvError;
    private final TextInputLayout tilPhone, tilCode;
    private final TextInputEditText etPhone, etCode;
    private final MaterialButton btnAction, btnSecondary;
    @Nullable private final String currentPhone;
    private Step step = Step.NUMBER;
    @Nullable private String newE164;
    @Nullable private CountDownTimer resendTimer;
    private boolean busy;

    @SuppressLint("InflateParams") // dialog content has no parent to measure against
    private PhoneChangeSheet(Activity activity, @Nullable String currentPhone) {
        this.activity = activity;
        this.currentPhone = currentPhone;
        sheet = new BottomSheetDialog(activity);
        View v = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_phone_change, null);
        sheet.setContentView(v);
        tvTitle = v.findViewById(R.id.tvPhoneChangeTitle);
        tvBody = v.findViewById(R.id.tvPhoneChangeBody);
        tvError = v.findViewById(R.id.tvPhoneChangeError);
        tilPhone = v.findViewById(R.id.tilNewPhone);
        tilCode = v.findViewById(R.id.tilPhoneCode);
        etPhone = v.findViewById(R.id.etNewPhone);
        etCode = v.findViewById(R.id.etPhoneCode);
        btnAction = v.findViewById(R.id.btnPhoneChangeAction);
        btnSecondary = v.findViewById(R.id.btnPhoneChangeSecondary);
        btnAction.setOnClickListener(x -> onAction());
        btnSecondary.setOnClickListener(x -> onSecondary());
        sheet.setOnDismissListener(d -> {
            if (resendTimer != null) resendTimer.cancel();
            // The server revoked this session: the only safe way on is a fresh sign-in.
            if (step == Step.DONE) SignOut.perform(activity);
        });
    }

    public static void show(@NonNull Activity activity, @Nullable String currentPhone) {
        PhoneChangeSheet s = new PhoneChangeSheet(activity, currentPhone);
        s.sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        s.sheet.getBehavior().setSkipCollapsed(true);
        s.sheet.show();
    }

    private void onAction() {
        if (busy) return;
        switch (step) {
            case NUMBER: requestCode(); break;
            case CODE: verify(); break;
            case DONE:
            case REAUTH:
                // Both end in a fresh sign-in.
                step = Step.DONE;
                sheet.dismiss();
                break;
        }
    }

    private void onSecondary() {
        if (busy) return;
        if (step == Step.CODE) {
            requestCode();   // "Send again" once the cooldown ends
        } else {
            sheet.dismiss();
        }
    }

    private void requestCode() {
        tvError.setVisibility(View.GONE);
        tilPhone.setError(null);
        String raw = step == Step.NUMBER ? text(etPhone) : null;
        String e164 = step == Step.NUMBER ? PhoneNumbers.toE164(raw) : newE164;
        if (e164 == null) {
            tilPhone.setError(activity.getString(R.string.st_phone_change_invalid));
            return;
        }
        if (currentPhone != null && e164.equals(PhoneNumbers.toE164(currentPhone))) {
            tilPhone.setError(activity.getString(R.string.st_phone_change_same));
            return;
        }
        newE164 = e164;
        setBusy(true, R.string.st_action_sending);
        JsonObject body = new JsonObject();
        body.addProperty("phoneNumber", e164);
        api.requestPhoneChange(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!sheet.isShowing()) {
                    ApiErrors.close(response);
                    return;
                }
                busy = false;
                if (!response.isSuccessful()) {
                    fail(ApiErrors.read(response, activity.getString(R.string.st_phone_change_send_failed)));
                    return;
                }
                int wait = 45;
                try (ResponseBody b = response.body()) {
                    JsonObject d = b != null ? ApiJson.dataObject(b.string()) : null;
                    JsonElement e = d != null ? ApiJson.get(d, "resendAfterSeconds") : null;
                    if (e != null && e.isJsonPrimitive()) wait = Math.max(10, e.getAsInt());
                } catch (Exception ignored) {
                    // keep the default wait
                }
                showCodeStep(wait);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!sheet.isShowing()) return;
                busy = false;
                restoreButtons();
                showError(activity.getString(R.string.st_error_network));
            }
        });
    }

    private void showCodeStep(int resendAfterSeconds) {
        step = Step.CODE;
        tvTitle.setText(R.string.st_phone_change_code_title);
        tvBody.setText(activity.getString(R.string.st_phone_change_code_body, pretty(newE164)));
        tilPhone.setVisibility(View.GONE);
        tilCode.setVisibility(View.VISIBLE);
        etCode.requestFocus();
        btnAction.setEnabled(true);
        btnAction.setText(R.string.st_phone_change_confirm);
        startResendCountdown(resendAfterSeconds);
    }

    private void startResendCountdown(int seconds) {
        if (resendTimer != null) resendTimer.cancel();
        btnSecondary.setEnabled(false);
        resendTimer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long left) {
                btnSecondary.setText(activity.getString(R.string.st_phone_change_resend_in, (int) Math.ceil(left / 1000.0)));
            }

            @Override
            public void onFinish() {
                btnSecondary.setEnabled(true);
                btnSecondary.setText(R.string.st_phone_change_resend);
            }
        }.start();
    }

    private void verify() {
        tvError.setVisibility(View.GONE);
        tilCode.setError(null);
        String code = text(etCode).replaceAll("\\D", "");
        if (code.length() != 6) {
            tilCode.setError(activity.getString(R.string.st_phone_change_code_invalid));
            return;
        }
        setBusy(true, R.string.st_phone_change_checking);
        JsonObject body = new JsonObject();
        body.addProperty("phoneNumber", newE164);
        body.addProperty("code", code);
        api.verifyPhoneChange(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!sheet.isShowing()) {
                    ApiErrors.close(response);
                    return;
                }
                busy = false;
                if (response.isSuccessful()) {
                    ApiErrors.close(response);
                    showDone();
                    return;
                }
                fail(ApiErrors.read(response, activity.getString(R.string.st_phone_change_verify_failed)));
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!sheet.isShowing()) return;
                busy = false;
                restoreButtons();
                showError(activity.getString(R.string.st_error_network));
            }
        });
    }

    private void showDone() {
        step = Step.DONE;
        if (resendTimer != null) resendTimer.cancel();
        tvTitle.setText(R.string.st_phone_change_done_title);
        tvBody.setText(activity.getString(R.string.st_phone_change_done_body, pretty(newE164)));
        tilCode.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
        btnAction.setEnabled(true);
        btnAction.setText(R.string.st_phone_change_sign_in);
        btnSecondary.setVisibility(View.GONE);
        sheet.setCancelable(false);
    }

    private void fail(ApiErrors.Failure f) {
        if (f.reauthRequired) {
            step = Step.REAUTH;
            if (resendTimer != null) resendTimer.cancel();
            tilPhone.setVisibility(View.GONE);
            tilCode.setVisibility(View.GONE);
            tvTitle.setText(R.string.st_phone_change_reauth_title);
            tvBody.setText(R.string.st_phone_change_reauth_body);
            btnAction.setEnabled(true);
            btnAction.setText(R.string.st_phone_change_sign_in);
            btnSecondary.setEnabled(true);
            btnSecondary.setText(R.string.st_action_cancel);
            tvError.setVisibility(View.GONE);
            return;
        }
        restoreButtons();
        if (step == Step.CODE) tilCode.setError(f.message);
        else showError(f.message);
    }

    private void setBusy(boolean b, int label) {
        busy = b;
        btnAction.setEnabled(!b);
        btnAction.setText(label);
    }

    private void restoreButtons() {
        btnAction.setEnabled(true);
        btnAction.setText(step == Step.CODE ? R.string.st_phone_change_confirm : R.string.st_phone_change_send);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private static String text(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString() : "";
    }

    private static String pretty(@Nullable String e164) {
        return ProfileText.phone(e164);
    }
}
