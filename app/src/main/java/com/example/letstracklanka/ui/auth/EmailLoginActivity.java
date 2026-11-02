package com.example.letstracklanka.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.utils.InputValidator;
import com.example.letstracklanka.utils.SecureScreen;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

/**
 * Optional email + password sign-in (for customers who added a password after registering). The phone code
 * stays the identity proof and the recovery path, so this screen never shows whether an email exists.
 *
 * Abuse protection (on top of Firebase's own limits):
 * - 5 wrong attempts lock the form for 60 seconds, and the lock survives leaving and re-opening the screen.
 * - Every wrong email or password shows the same sentence, so the screen cannot be used to find out which
 *   emails are registered; "Forgot password" also gives the same answer whether or not the email exists.
 * - Reset e-mails can be requested once every 30 seconds.
 */
public class EmailLoginActivity extends AppCompatActivity {

    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MS = 60_000L;
    private static final long RESET_COOLDOWN_MS = 30_000L;

    // static so leaving and coming back does not clear the lock
    private static int failures = 0;
    private static long lockedUntil = 0L;
    private static long resetAllowedAt = 0L;

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private TextView tvError;
    private MaterialButton btnSignIn, btnForgot;
    private CountDownTimer lockTimer;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SecureScreen.enable(this);
        setContentView(R.layout.activity_email_login);
        AuthUi.edgeToEdge(this, findViewById(R.id.main));
        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());

        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvError = findViewById(R.id.tvError);
        btnSignIn = findViewById(R.id.btnSignIn);
        btnForgot = findViewById(R.id.btnForgot);

        etPassword.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { btnSignIn.performClick(); return true; }
            return false;
        });
        btnSignIn.setOnClickListener(v -> signIn());
        btnForgot.setOnClickListener(v -> sendReset());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLock();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (lockTimer != null) { lockTimer.cancel(); lockTimer = null; }
    }

    private void signIn() {
        if (busy || isLocked()) return;
        tilEmail.setError(null);
        tilPassword.setError(null);
        tvError.setVisibility(View.GONE);

        String email = InputValidator.cleanLine(text(etEmail));
        String pw = text(etPassword);

        String emailError = InputValidator.email(email);
        if (emailError != null) { tilEmail.setError(emailError); return; }
        if (pw.isEmpty() || pw.length() > 128) { tilPassword.setError(getString(R.string.emaillogin_wrong)); return; }

        setBusy(true);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, pw).addOnCompleteListener(this, task -> {
            if (isFinishing() || isDestroyed()) return;
            if (task.isSuccessful()) {
                failures = 0;
                // The server decides whether this account already has a profile, exactly like after a phone code.
                Intent intent = new Intent(this, RouteAfterLoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return;
            }
            setBusy(false);
            Exception e = task.getException();
            if (e instanceof FirebaseNetworkException) { showError(getString(R.string.auth_network)); return; }
            if (e instanceof FirebaseAuthException
                    && "ERROR_USER_DISABLED".equals(((FirebaseAuthException) e).getErrorCode())) {
                showError(getString(R.string.emaillogin_disabled));
                return;
            }
            if (e instanceof FirebaseAuthException
                    && "ERROR_TOO_MANY_REQUESTS".equals(((FirebaseAuthException) e).getErrorCode())) {
                showError(getString(R.string.auth_too_many));
                return;
            }
            // Wrong password, unknown email, malformed credential: one answer for all of them.
            if (e instanceof FirebaseAuthInvalidCredentialsException || e instanceof FirebaseAuthInvalidUserException
                    || e instanceof FirebaseAuthException) {
                registerFailure();
                showError(getString(R.string.emaillogin_wrong));
            } else {
                showError(getString(R.string.emaillogin_wrong));
            }
        });
    }

    private void sendReset() {
        if (busy) return;
        tilEmail.setError(null);
        tvError.setVisibility(View.GONE);

        String email = InputValidator.cleanLine(text(etEmail));
        if (email.isEmpty()) { showError(getString(R.string.emaillogin_enter_email_first)); return; }
        String emailError = InputValidator.email(email);
        if (emailError != null) { tilEmail.setError(emailError); return; }

        long wait = resetAllowedAt - SystemClock.elapsedRealtime();
        if (wait > 0) {
            showError(getString(R.string.emaillogin_locked, (int) ((wait + 999) / 1000)));
            return;
        }
        resetAllowedAt = SystemClock.elapsedRealtime() + RESET_COOLDOWN_MS;

        setBusy(true);
        FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener(this, task -> {
            if (isFinishing() || isDestroyed()) return;
            setBusy(false);
            Exception e = task.getException();
            if (e instanceof FirebaseNetworkException) {
                resetAllowedAt = 0L;      // nothing was sent, let them retry straight away
                showError(getString(R.string.auth_network));
                return;
            }
            // Same sentence whether or not the email has an account (no way to probe for registered emails).
            showInfo(getString(R.string.emaillogin_reset_sent));
        });
    }

    // ---- local lock ---------------------------------------------------------------------------

    private void registerFailure() {
        failures++;
        if (failures >= MAX_FAILURES) {
            lockedUntil = SystemClock.elapsedRealtime() + LOCK_MS;
            failures = 0;
            refreshLock();
        }
    }

    private boolean isLocked() {
        return lockedUntil - SystemClock.elapsedRealtime() > 0;
    }

    private void refreshLock() {
        if (lockTimer != null) { lockTimer.cancel(); lockTimer = null; }
        long remaining = lockedUntil - SystemClock.elapsedRealtime();
        if (remaining <= 0) {
            btnSignIn.setEnabled(!busy);
            btnSignIn.setText(R.string.emaillogin_sign_in);
            return;
        }
        btnSignIn.setEnabled(false);
        showError(getString(R.string.emaillogin_locked, (int) ((remaining + 999) / 1000)));
        lockTimer = new CountDownTimer(remaining, 1000L) {
            @Override public void onTick(long left) {
                showError(getString(R.string.emaillogin_locked, (int) ((left + 999) / 1000)));
            }
            @Override public void onFinish() {
                tvError.setVisibility(View.GONE);
                btnSignIn.setEnabled(!busy);
            }
        }.start();
    }

    private void setBusy(boolean value) {
        busy = value;
        btnSignIn.setEnabled(!value && !isLocked());
        btnForgot.setEnabled(!value);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void showInfo(String message) {
        tilEmail.setHelperText(message);
    }

    private static String text(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString() : "";
    }
}
