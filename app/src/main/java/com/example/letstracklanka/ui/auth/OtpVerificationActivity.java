package com.example.letstracklanka.ui.auth;

import com.example.letstracklanka.utils.InputValidator;
import com.example.letstracklanka.utils.SecureScreen;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.os.CountDownTimer;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.letstracklanka.R;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OtpVerificationActivity extends AppCompatActivity {

    /** The number the code was sent to (E.164), passed in by LoginActivity. */
    public static final String EXTRA_PHONE = "phone_number";

    private static final String TAG = "OtpVerification";
    // Matches a standalone 6-digit sequence anywhere in the SMS body.
    // Deliberately not tied to any specific surrounding text (like a
    // "<#>" prefix or app-signature hash), since the User Consent API
    // works with whatever format Firebase's own SMS template actually
    // uses -- unlike the full SMS Retriever API, this one doesn't
    // require us to control that format at all.
    private static final Pattern OTP_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

    private static final long RESEND_COOLDOWN_MS = 45_000L;

    private EditText etOtp;
    private TextInputLayout otpInput;
    private TextView tvOtpError;
    private MaterialButton btnResend;
    private CountDownTimer resendTimer;
    private MaterialButton btnVerifyCode;
    private String phone;
    private boolean verifying = false;
    private FirebaseAuth mAuth;

    private BroadcastReceiver smsConsentReceiver;
    private ActivityResultLauncher<Intent> smsConsentLauncher;
    private boolean autoFillHandled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SecureScreen.enable(this);
        setContentView(R.layout.activity_otp_verification);

        mAuth = FirebaseAuth.getInstance();
        phone = getIntent().getStringExtra(EXTRA_PHONE);
        if (phone == null || phone.isEmpty()) {
            // Opened without a number (should never happen): nothing to verify, go back to sign-in.
            finish();
            return;
        }

        AuthUi.edgeToEdge(this, findViewById(R.id.main));
        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());

        btnVerifyCode = findViewById(R.id.btnVerifyCode);
        btnResend = findViewById(R.id.btnResend);
        etOtp = findViewById(R.id.etOtp);
        otpInput = findViewById(R.id.otpInput);
        tvOtpError = findViewById(R.id.tvOtpError);
        ((TextView) findViewById(R.id.tvOtpSubtitle)).setText(getString(R.string.otp_subtitle, phone));

        etOtp.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                otpInput.setError(null);
                hideError();
                // Auto-submit on the 6th digit: typed, pasted or autofilled all take the same path.
                if (InputValidator.otp(s.toString()) == null) verifyOtp(s.toString());
            }
        });
        etOtp.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { btnVerifyCode.performClick(); return true; }
            return false;
        });
        etOtp.requestFocus();

        btnVerifyCode.setOnClickListener(v -> {
            String code = etOtp.getText() != null ? etOtp.getText().toString().trim() : "";
            if (InputValidator.otp(code) != null) {
                otpInput.setError(getString(R.string.otp_incomplete));
                return;
            }
            verifyOtp(code);
        });
        btnResend.setOnClickListener(v -> resend());
        startResendCountdown(RESEND_COOLDOWN_MS);

        // NEW -- real OTP box auto-fill, not just the existing silent
        // auto-sign-in path (which is Firebase's own onVerificationCompleted()
        // in LoginActivity, and skips this screen entirely rather than
        // visibly filling it in). This is a genuinely separate mechanism:
        // the SMS User Consent API, registered here so it's listening for
        // the whole time this screen is up.
        setupSmsConsentLauncher();
        startSmsUserConsent();
    }

    private void setupSmsConsentLauncher() {
        smsConsentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String message = result.getData().getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE);
                        handleSmsMessage(message);
                    }
                    // A denied/cancelled consent dialog just leaves the
                    // user to type the code manually -- no error needed,
                    // that's the normal fallback, not a failure.
                });
    }

    private void startSmsUserConsent() {
        // null = listen for a code from any sender, since we don't
        // control (and can't rely on) which sender ID Firebase's SMS
        // gateway actually uses for a given region/carrier.
        SmsRetriever.getClient(this).startSmsUserConsent(null);

        smsConsentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) return;

                Bundle extras = intent.getExtras();
                if (extras == null) return;

                Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);
                if (status == null) return;

                if (status.getStatusCode() == CommonStatusCodes.SUCCESS) {
                    Intent consentIntent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ? extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT, Intent.class)
                            : getConsentIntentLegacy(extras);
                    if (consentIntent != null) {
                        try {
                            // Launches the system's one-tap consent
                            // dialog -- required by the API before the
                            // app is allowed to actually read the SMS
                            // body, regardless of who sent it.
                            smsConsentLauncher.launch(consentIntent);
                        } catch (Exception e) {
                            Log.e(TAG, "Couldn't launch SMS consent dialog", e);
                        }
                    }
                } else if (status.getStatusCode() == CommonStatusCodes.TIMEOUT) {
                    Log.d(TAG, "SMS User Consent timed out -- user can still type the code manually.");
                }
            }
        };

        IntentFilter filter = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);
        // RECEIVER_EXPORTED, not RECEIVER_NOT_EXPORTED -- this broadcast
        // comes from the Google Play Services system component, not from
        // within this app's own process. Required explicitly on API 33+
        // (targetSdk 36 here) or registerReceiver() throws at runtime.
        ContextCompat.registerReceiver(this, smsConsentReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
    }

    @SuppressWarnings("deprecation")
    private Intent getConsentIntentLegacy(Bundle extras) {
        return extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT);
    }

    private void handleSmsMessage(String message) {
        if (message == null || autoFillHandled) return;
        // Any sender can reach the consent dialog, so only accept OUR message. A forged SMS with some
        // other 6 digits would otherwise burn one of the code's 5 attempts.
        if (!message.contains("Shalotrack")) return;

        Matcher matcher = OTP_PATTERN.matcher(message);
        if (!matcher.find()) return;

        String code = matcher.group(1);
        autoFillHandled = true;
        etOtp.setText(code);   // the TextWatcher submits on the 6th digit
    }

    private void resend() {
        hideError();
        otpInput.setHelperText(null);
        btnResend.setEnabled(false);
        OtpRepository.requestOtp(phone, new OtpRepository.RequestCallback() {
            @Override
            public void onSent() {
                if (isFinishing() || isDestroyed()) return;
                autoFillHandled = false;
                startSmsUserConsent();
                startResendCountdown(RESEND_COOLDOWN_MS);
                otpInput.setHelperText(getString(R.string.otp_resent));
            }

            @Override
            public void onFailure(String message, long retryAfterSeconds) {
                if (isFinishing() || isDestroyed()) return;
                showError(message);
                startResendCountdown(retryAfterSeconds > 0 ? retryAfterSeconds * 1000L : 5_000L);
            }
        });
    }

    private void startResendCountdown(long millis) {
        if (resendTimer != null) resendTimer.cancel();
        btnResend.setEnabled(false);
        resendTimer = new CountDownTimer(millis, 1000L) {
            @Override public void onTick(long left) {
                long sec = (left + 999L) / 1000L;
                btnResend.setText(getString(R.string.otp_resend_in, sec / 60, sec % 60));
            }
            @Override public void onFinish() {
                btnResend.setText(R.string.otp_resend);
                btnResend.setEnabled(true);
            }
        }.start();
    }

    private void showError(String message) {
        tvOtpError.setText(message);
        tvOtpError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvOtpError.setVisibility(View.GONE);
    }

    private void verifyOtp(String code) {
        if (verifying) return;
        verifying = true;
        btnVerifyCode.setEnabled(false);
        btnVerifyCode.setText(R.string.otp_verifying);

        OtpRepository.verifyOtp(phone, code, new OtpRepository.VerifyCallback() {
            @Override
            public void onVerified(String customToken) {
                if (isFinishing() || isDestroyed()) return;
                // The code was right: the API vouches for this phone with a Firebase custom token.
                mAuth.signInWithCustomToken(customToken).addOnCompleteListener(OtpVerificationActivity.this, task -> {
                    if (task.isSuccessful()) {
                        // Ask the server whether this phone already has a profile. Existing customers go
                        // straight in; only genuinely new ones see the registration form.
                        Intent intent = new Intent(OtpVerificationActivity.this, RouteAfterLoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        failVerify(getString(R.string.otp_signin_failed), false);
                    }
                });
            }

            @Override
            public void onFailure(String message, boolean codeRejected) {
                if (isFinishing() || isDestroyed()) return;
                failVerify(message, codeRejected);
            }
        });
    }

    private void failVerify(String message, boolean clearCode) {
        verifying = false;
        btnVerifyCode.setEnabled(true);
        btnVerifyCode.setText(R.string.otp_verify);
        autoFillHandled = false;
        showError(message);
        if (clearCode) {
            etOtp.setText("");
            etOtp.requestFocus();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendTimer != null) resendTimer.cancel();
        if (smsConsentReceiver != null) {
            try {
                unregisterReceiver(smsConsentReceiver);
            } catch (IllegalArgumentException e) {
                // Already unregistered or never successfully registered -- safe to ignore.
            }
        }
    }
}