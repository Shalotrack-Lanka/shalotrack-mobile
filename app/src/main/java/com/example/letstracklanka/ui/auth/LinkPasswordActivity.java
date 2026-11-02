package com.example.letstracklanka.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.ui.main.HomeActivity;
import com.example.letstracklanka.utils.InputValidator;
import com.example.letstracklanka.utils.SecureScreen;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;

/**
 * Step after registration: optionally attach an email + password to the signed-in account, so next time the
 * customer can skip the SMS code (cheaper for us, faster for them). Same idea as the Fleet portal's
 * "Create your password" page.
 *
 * Rules that matter:
 * - It runs only AFTER the profile exists (see ProcessingActivity). Linking an email earlier would make
 *   SplashActivity skip the profile check on the next launch.
 * - The email is not verified and proves nothing. The phone code remains the identity proof and the recovery path.
 * - Skip is always possible and never loses the account.
 * - The screen is FLAG_SECURE; the password is read, sent to Firebase and never stored or logged by us.
 */
public class LinkPasswordActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL = "extra_email";

    private TextInputLayout tilPassword, tilConfirm;
    private TextInputEditText etPassword, etConfirm;
    private TextView tvError;
    private MaterialButton btnSave, btnSkip;
    private String email;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SecureScreen.enable(this);
        setContentView(R.layout.activity_link_password);
        AuthUi.edgeToEdge(this, findViewById(R.id.main));

        email = getIntent().getStringExtra(EXTRA_EMAIL);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || email == null || InputValidator.email(email) != null) {
            goHome();      // nothing sensible to link: never trap the customer on this screen
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(null);   // no back: the profile is created, Skip is the way forward
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirm = findViewById(R.id.tilConfirm);
        etPassword = findViewById(R.id.etPassword);
        etConfirm = findViewById(R.id.etConfirm);
        tvError = findViewById(R.id.tvError);
        btnSave = findViewById(R.id.btnSavePassword);
        btnSkip = findViewById(R.id.btnSkipPassword);

        etConfirm.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { btnSave.performClick(); return true; }
            return false;
        });
        btnSave.setOnClickListener(v -> save(user));
        btnSkip.setOnClickListener(v -> { if (!busy) goHome(); });
    }

    private void save(FirebaseUser user) {
        if (busy) return;
        tilPassword.setError(null);
        tilConfirm.setError(null);
        tvError.setVisibility(View.GONE);

        String pw = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String confirm = etConfirm.getText() != null ? etConfirm.getText().toString() : "";

        String problem = InputValidator.password(pw, email);
        if (problem != null) { tilPassword.setError(problem); return; }
        if (!pw.equals(confirm)) { tilConfirm.setError(getString(R.string.link_mismatch)); return; }

        setBusy(true);
        AuthCredential credential = EmailAuthProvider.getCredential(email, pw);
        user.linkWithCredential(credential).addOnCompleteListener(this, task -> {
            if (isFinishing() || isDestroyed()) return;
            if (task.isSuccessful()) {
                goHome();
                return;
            }
            setBusy(false);
            showError(messageFor(task.getException()));
        });
    }

    private String messageFor(Exception e) {
        if (e instanceof FirebaseNetworkException) return getString(R.string.auth_network);
        if (e instanceof FirebaseAuthException) {
            switch (((FirebaseAuthException) e).getErrorCode()) {
                case "ERROR_EMAIL_ALREADY_IN_USE":
                case "ERROR_CREDENTIAL_ALREADY_IN_USE":
                    return getString(R.string.link_email_taken);
                case "ERROR_WEAK_PASSWORD":
                    return getString(R.string.link_rules);
                case "ERROR_TOO_MANY_REQUESTS":
                    return getString(R.string.auth_too_many);
                default:
                    break;
            }
        }
        return getString(R.string.link_failed);
    }

    private void setBusy(boolean value) {
        busy = value;
        btnSave.setEnabled(!value);
        btnSkip.setEnabled(!value);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void goHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
