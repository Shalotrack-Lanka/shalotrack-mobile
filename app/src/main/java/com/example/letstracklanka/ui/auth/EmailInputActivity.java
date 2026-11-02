package com.example.letstracklanka.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.utils.EmailTypoGuard;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Registration details for a NEW customer (name, NIC, address, email).
 *
 * There is NO email verification (owner decision, 2 Nov 2026): the phone number, proven by our own OTP,
 * is the only identity check. The email is a plain contact field, so nothing may use it for recovery or
 * trust. A domain typo (gamil.com) is caught here with a "Did you mean...?" question the customer can
 * dismiss. The profile itself is created by ProcessingActivity.
 */
public class EmailInputActivity extends AppCompatActivity {

    /** Sri Lankan NIC: 9 digits + V/X (old) or 12 digits (new). Same rule as the API. */
    private static final Pattern NIC = Pattern.compile("^([0-9]{9}[VX]|[0-9]{12})$");

    private TextInputEditText etFullName, etNicNumber, etAddress, etEmail;
    private TextInputLayout layoutFullName, layoutNicNumber, layoutAddress, layoutEmail;
    private MaterialButton btnContinue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_input);
        AuthUi.edgeToEdge(this, findViewById(R.id.main));

        etFullName = findViewById(R.id.etFullName);
        etNicNumber = findViewById(R.id.etNicNumber);
        etAddress = findViewById(R.id.etAddress);
        etEmail = findViewById(R.id.etEmail);
        layoutFullName = findViewById(R.id.layoutFullName);
        layoutNicNumber = findViewById(R.id.layoutNicNumber);
        layoutAddress = findViewById(R.id.layoutAddress);
        layoutEmail = findViewById(R.id.layoutEmailInput);
        btnContinue = findViewById(R.id.btnContinue);

        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());
        btnContinue.setOnClickListener(v -> onContinue());
        etEmail.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { onContinue(); return true; }
            return false;
        });
    }

    private static String text(EditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    private void onContinue() {
        String name = text(etFullName);
        // One canonical NIC form (no spaces, upper-case V/X) so the uniqueness check cannot be dodged.
        String nic = text(etNicNumber).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        String address = text(etAddress);
        String email = text(etEmail);

        layoutFullName.setError(null);
        layoutNicNumber.setError(null);
        layoutAddress.setError(null);
        layoutEmail.setError(null);

        boolean ok = true;
        if (name.isEmpty()) {
            layoutFullName.setError(getString(R.string.details_name_error));
            ok = false;
        }
        if (!NIC.matcher(nic).matches()) {
            layoutNicNumber.setError(getString(R.string.details_nic_error));
            ok = false;
        }
        if (address.isEmpty()) {
            layoutAddress.setError(getString(R.string.details_address_error));
            ok = false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            layoutEmail.setError(getString(R.string.details_email_error));
            ok = false;
        }
        if (!ok) return;

        String suggestion = EmailTypoGuard.suggest(email);
        if (suggestion != null) {
            askAboutTypo(name, nic, address, email, suggestion);
        } else {
            moveToProcessing(name, nic, address, email);
        }
    }

    private void askAboutTypo(String name, String nic, String address, String typed, String suggestion) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.details_typo_title)
                .setMessage(getString(R.string.details_typo_message, suggestion))
                .setPositiveButton(getString(R.string.details_typo_use, suggestion), (d, w) -> {
                    etEmail.setText(suggestion);
                    moveToProcessing(name, nic, address, suggestion);
                })
                .setNegativeButton(R.string.details_typo_keep, (d, w) -> moveToProcessing(name, nic, address, typed))
                .setNeutralButton(R.string.details_typo_edit, null)
                .show();
    }

    private void moveToProcessing(String name, String nic, String address, String email) {
        Intent intent = new Intent(this, ProcessingActivity.class);
        intent.putExtra("EXTRA_NAME", name);
        intent.putExtra("EXTRA_NIC", nic);
        intent.putExtra("EXTRA_EMAIL", email);
        intent.putExtra("EXTRA_ADDRESS", address);
        startActivity(intent);
        finish();
    }
}
