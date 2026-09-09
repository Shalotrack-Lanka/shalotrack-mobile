package com.example.letstracklanka.ui.auth;

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
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.letstracklanka.R;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OtpVerificationActivity extends AppCompatActivity {

    private static final String TAG = "OtpVerification";
    // Matches a standalone 6-digit sequence anywhere in the SMS body.
    // Deliberately not tied to any specific surrounding text (like a
    // "<#>" prefix or app-signature hash), since the User Consent API
    // works with whatever format Firebase's own SMS template actually
    // uses -- unlike the full SMS Retriever API, this one doesn't
    // require us to control that format at all.
    private static final Pattern OTP_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

    private EditText[] otpBoxes;
    private MaterialButton btnVerifyCode;
    private String verificationId;
    private FirebaseAuth mAuth;

    private BroadcastReceiver smsConsentReceiver;
    private ActivityResultLauncher<Intent> smsConsentLauncher;
    private boolean autoFillHandled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        mAuth = FirebaseAuth.getInstance();
        verificationId = getIntent().getStringExtra("backend_verification_id");

        btnVerifyCode = findViewById(R.id.btnVerifyCode);
        ImageView btnBack = findViewById(R.id.btnBack);

        otpBoxes = new EditText[]{
                findViewById(R.id.etOtp1), findViewById(R.id.etOtp2),
                findViewById(R.id.etOtp3), findViewById(R.id.etOtp4),
                findViewById(R.id.etOtp5), findViewById(R.id.etOtp6)
        };

        setupOtpInputs();
        btnBack.setOnClickListener(v -> finish());

        btnVerifyCode.setOnClickListener(v -> {
            String code = getOtpString();
            if (code.length() < 6) {
                Toast.makeText(this, "Enter 6 digits", Toast.LENGTH_SHORT).show();
                return;
            }
            verifyOtp(code);
        });

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

        Matcher matcher = OTP_PATTERN.matcher(message);
        if (!matcher.find()) return;

        String code = matcher.group(1);
        autoFillHandled = true;
        fillOtpBoxes(code);
        verifyOtp(code);
    }

    private void fillOtpBoxes(String code) {
        for (int i = 0; i < otpBoxes.length && i < code.length(); i++) {
            otpBoxes[i].setText(String.valueOf(code.charAt(i)));
        }
    }

    private void setupOtpInputs() {
        for (int i = 0; i < otpBoxes.length; i++) {
            final int currentIndex = i;
            otpBoxes[i].addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1 && currentIndex < otpBoxes.length - 1) otpBoxes[currentIndex + 1].requestFocus();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            otpBoxes[i].setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (otpBoxes[currentIndex].getText().toString().isEmpty() && currentIndex > 0) {
                        otpBoxes[currentIndex - 1].requestFocus();
                        otpBoxes[currentIndex - 1].setText("");
                        return true;
                    }
                }
                return false;
            });
        }
    }

    private String getOtpString() {
        StringBuilder otp = new StringBuilder();
        for (EditText box : otpBoxes) otp.append(box.getText().toString().trim());
        return otp.toString();
    }

    private void verifyOtp(String code) {
        btnVerifyCode.setEnabled(false);
        btnVerifyCode.setText("Verifying...");

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                Intent intent = new Intent(this, EmailInputActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                btnVerifyCode.setEnabled(true);
                btnVerifyCode.setText("Verify Code");
                Toast.makeText(this, "Invalid Code", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (smsConsentReceiver != null) {
            try {
                unregisterReceiver(smsConsentReceiver);
            } catch (IllegalArgumentException e) {
                // Already unregistered or never successfully registered -- safe to ignore.
            }
        }
    }
}