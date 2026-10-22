package com.example.letstracklanka.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "PhoneLogin";

    /** After a code has really been sent, the button waits this long before it can send another. */
    private static final long COOLDOWN_AFTER_SEND_MS = 60_000L;
    /** Firebase has blocked this device for a while; retrying only prolongs that block. */
    private static final long COOLDOWN_AFTER_BLOCK_MS = 5 * 60_000L;

    // static so the wait survives going to the code screen and pressing Back
    private static long cooldownEndsAt = 0L;

    private TextInputLayout phoneInput;
    private MaterialButton btnSendCode;
    private ProgressBar progressBar;
    private TextView tvLoginError;

    private FirebaseAuth mAuth;
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;
    private CountDownTimer cooldownTimer;
    private boolean requestInFlight = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 1. Initialize Firebase and UI
        mAuth = FirebaseAuth.getInstance();
        phoneInput = findViewById(R.id.phoneInput);
        btnSendCode = findViewById(R.id.btnSendCode);
        progressBar = findViewById(R.id.progressBar);
        tvLoginError = findViewById(R.id.tvLoginError);

        // 2. Prepare the Firebase SMS Callbacks
        setupFirebaseCallbacks();

        // 3. Handle Button Click
        btnSendCode.setOnClickListener(v -> {
            // Get text, avoiding NullPointerExceptions
            String phone = phoneInput.getEditText() != null ?
                    phoneInput.getEditText().getText().toString().trim() : "";

            if (phone.isEmpty() || phone.length() < 9) {
                phoneInput.setError("Please enter a valid phone number");
                return;
            }
            phoneInput.setError(null);

            // Format for Sri Lanka (+94) if the user types '071...' instead of '+9471...'
            if (phone.startsWith("0")) {
                phone = "+94" + phone.substring(1);
            } else if (!phone.startsWith("+")) {
                phone = "+94" + phone;
            }

            sendVerificationCode(phone);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSendButton();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCooldownTimer();
    }

    private void sendVerificationCode(String phoneNumber) {
        hideError();
        requestInFlight = true;
        progressBar.setVisibility(View.VISIBLE);
        btnSendCode.setEnabled(false);

        // Tell Firebase to send the SMS
        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(mAuth)
                        .setPhoneNumber(phoneNumber)       // The formatted phone number
                        .setTimeout(60L, TimeUnit.SECONDS) // Wait time before allowing resend
                        .setActivity(this)                 // Activity context
                        .setCallbacks(mCallbacks)          // The callbacks defined below
                        .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void setupFirebaseCallbacks() {
        mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                // FIREBASE READ THE SMS AUTOMATICALLY! Keep the spinner up while we sign in.
                progressBar.setVisibility(View.VISIBLE);

                mAuth.signInWithCredential(credential).addOnCompleteListener(LoginActivity.this, task -> {
                    if (task.isSuccessful()) {
                        // Auto-verified (OTP screen skipped). The server decides where this phone
                        // goes: existing customer -> app, new customer -> registration.
                        Intent intent = new Intent(LoginActivity.this, RouteAfterLoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    } else {
                        requestInFlight = false;
                        progressBar.setVisibility(View.GONE);
                        showError("We couldn't sign you in automatically. Please try again.");
                        refreshSendButton();
                    }
                });
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                // Invalid number, no network, quota exceeded, or Firebase blocked this device.
                requestInFlight = false;
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Verification failed", e);

                if (e instanceof FirebaseTooManyRequestsException) {
                    startCooldown(COOLDOWN_AFTER_BLOCK_MS);
                }
                showError(friendlyMessage(e));
                refreshSendButton();
            }

            @Override
            public void onCodeSent(@NonNull String verificationId,
                                   @NonNull PhoneAuthProvider.ForceResendingToken token) {
                // SMS SENT, BUT NOT AUTO-READ. Move to the OTP Screen so they can type it.
                requestInFlight = false;
                progressBar.setVisibility(View.GONE);
                startCooldown(COOLDOWN_AFTER_SEND_MS);

                // Move to the screen where the user types in the 6-digit OTP
                Intent intent = new Intent(LoginActivity.this, OtpVerificationActivity.class);

                // Pass the secret verification ID to the next screen (required to confirm the OTP)
                intent.putExtra("backend_verification_id", verificationId);
                startActivity(intent);
            }
        };
    }

    /** Plain-language message for the customer. Raw Firebase text is only ever written to the log. */
    private static String friendlyMessage(Exception e) {
        if (e instanceof FirebaseTooManyRequestsException) {
            return "Too many attempts from this phone. Please wait a few minutes and try again.";
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "That phone number doesn't look right. Check it and try again.";
        }
        if (e instanceof FirebaseNetworkException) {
            return "No connection. Check your internet and try again.";
        }
        return "We couldn't send the code. Please try again in a moment.";
    }

    private void showError(String message) {
        tvLoginError.setText(message);
        tvLoginError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvLoginError.setVisibility(View.GONE);
    }

    // ---- send-code cooldown -------------------------------------------------------------------

    private void startCooldown(long millis) {
        cooldownEndsAt = SystemClock.elapsedRealtime() + millis;
    }

    /** Enables the button, or shows a countdown while a cooldown is still running. */
    private void refreshSendButton() {
        stopCooldownTimer();
        if (requestInFlight) {
            btnSendCode.setEnabled(false);
            return;
        }

        long remaining = cooldownEndsAt - SystemClock.elapsedRealtime();
        if (remaining <= 0) {
            btnSendCode.setEnabled(true);
            btnSendCode.setText(R.string.btn_send_code);
            return;
        }

        btnSendCode.setEnabled(false);
        showRemaining(remaining);
        cooldownTimer = new CountDownTimer(remaining, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                showRemaining(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                refreshSendButton();
            }
        }.start();
    }

    private void showRemaining(long millis) {
        long seconds = (millis + 999L) / 1000L;
        btnSendCode.setText(String.format(Locale.US, "Try again in %d:%02d", seconds / 60, seconds % 60));
    }

    private void stopCooldownTimer() {
        if (cooldownTimer != null) {
            cooldownTimer.cancel();
            cooldownTimer = null;
        }
    }
}