package com.example.letstracklanka.ui.auth;

import com.example.letstracklanka.utils.SecureScreen;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import com.google.android.material.appbar.MaterialToolbar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Phone sign-in. The code is sent by OUR API (Text.lk), not by Firebase Phone Auth: Dialog, Airtel and
 * Hutch were silently dropping Firebase's SMS. Firebase stays the session issuer: after the code is
 * verified (OtpVerificationActivity) the API returns a custom token that we exchange for a normal sign-in.
 */
public class LoginActivity extends AppCompatActivity {

    /** Matches the server's per-number resend cooldown (45 s), so the button never invites a guaranteed 429. */
    private static final long COOLDOWN_AFTER_SEND_MS = 45_000L;

    // static so the wait survives going to the code screen and pressing Back
    private static long cooldownEndsAt = 0L;

    private TextInputLayout phoneInput;
    private MaterialButton btnSendCode;
    private View progressBar;
    private TextView tvLoginError;

    private CountDownTimer cooldownTimer;
    private boolean requestInFlight = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SecureScreen.enable(this);
        setContentView(R.layout.activity_login);
        AuthUi.edgeToEdge(this, findViewById(R.id.main));
        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());

        phoneInput = findViewById(R.id.phoneInput);
        btnSendCode = findViewById(R.id.btnSendCode);
        progressBar = findViewById(R.id.progressBar);
        tvLoginError = findViewById(R.id.tvLoginError);

        if (phoneInput.getEditText() != null) {
            phoneInput.getEditText().setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) { btnSendCode.performClick(); return true; }
                return false;
            });
        }
        findViewById(R.id.btnEmailLogin).setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, EmailLoginActivity.class)));

        btnSendCode.setOnClickListener(v -> {
            if (requestInFlight) return;
            String raw = phoneInput.getEditText() != null
                    ? phoneInput.getEditText().getText().toString().trim() : "";
            String e164 = PhoneNumbers.toE164(raw);
            if (e164 == null) {
                phoneInput.setError(getString(R.string.login_phone_error));
                return;
            }
            phoneInput.setError(null);
            sendCode(e164);
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

    private void sendCode(String e164) {
        hideError();
        requestInFlight = true;
        progressBar.setVisibility(View.VISIBLE);
        btnSendCode.setEnabled(false);
        btnSendCode.setText("");

        OtpRepository.requestOtp(e164, new OtpRepository.RequestCallback() {
            @Override
            public void onSent() {
                requestInFlight = false;
                if (isFinishing() || isDestroyed()) return;
                progressBar.setVisibility(View.GONE);
                startCooldown(COOLDOWN_AFTER_SEND_MS);

                Intent intent = new Intent(LoginActivity.this, OtpVerificationActivity.class);
                intent.putExtra(OtpVerificationActivity.EXTRA_PHONE, e164);
                startActivity(intent);
            }

            @Override
            public void onFailure(String message, long retryAfterSeconds) {
                requestInFlight = false;
                if (isFinishing() || isDestroyed()) return;
                progressBar.setVisibility(View.GONE);
                if (retryAfterSeconds > 0) {
                    startCooldown(retryAfterSeconds * 1000L);
                }
                showError(message);
                refreshSendButton();
            }
        });
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
            btnSendCode.setText(R.string.login_send_code);
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
        btnSendCode.setText(getString(R.string.login_try_again_in, seconds / 60, seconds % 60));
    }

    private void stopCooldownTimer() {
        if (cooldownTimer != null) {
            cooldownTimer.cancel();
            cooldownTimer = null;
        }
    }
}
