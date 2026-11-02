package com.example.letstracklanka.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.repository.OtpRepository;

/**
 * "Get started" phone screen. Same flow as LoginActivity: our API sends the code (Text.lk), the code
 * screen verifies it, and RouteAfterLoginActivity then decides between the app and the registration form.
 */
public class SignUpActivity extends AppCompatActivity {

    private EditText etPhoneNumber;
    private View btnSendCode;
    private boolean requestInFlight = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        etPhoneNumber = findViewById(R.id.etPhoneNumber);
        btnSendCode = findViewById(R.id.btnSendCode);

        String[] languages = {"English (US)", "Sinhala (LK)", "Tamil (LK)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, languages);
        AutoCompleteTextView dropdownLanguage = findViewById(R.id.dropdownLanguage);
        dropdownLanguage.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnSendCode.setOnClickListener(v -> {
            if (requestInFlight) return;
            String e164 = PhoneNumbers.toE164(etPhoneNumber.getText().toString().trim());
            if (e164 == null) {
                Toast.makeText(this, "Enter a valid mobile number, for example 0771234567", Toast.LENGTH_SHORT).show();
                return;
            }

            requestInFlight = true;
            btnSendCode.setEnabled(false);
            OtpRepository.requestOtp(e164, new OtpRepository.RequestCallback() {
                @Override
                public void onSent() {
                    requestInFlight = false;
                    if (isFinishing() || isDestroyed()) return;
                    btnSendCode.setEnabled(true);
                    Intent intent = new Intent(SignUpActivity.this, OtpVerificationActivity.class);
                    intent.putExtra(OtpVerificationActivity.EXTRA_PHONE, e164);
                    startActivity(intent);
                }

                @Override
                public void onFailure(String message, long retryAfterSeconds) {
                    requestInFlight = false;
                    if (isFinishing() || isDestroyed()) return;
                    btnSendCode.setEnabled(true);
                    Toast.makeText(SignUpActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}