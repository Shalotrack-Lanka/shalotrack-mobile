package com.example.letstracklanka.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class SignUpActivity extends AppCompatActivity {

    private EditText etPhoneNumber;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        mAuth = FirebaseAuth.getInstance();
        etPhoneNumber = findViewById(R.id.etPhoneNumber);

        String[] languages = {"English (US)", "Sinhala (LK)", "Tamil (LK)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, languages);
        AutoCompleteTextView dropdownLanguage = findViewById(R.id.dropdownLanguage);
        dropdownLanguage.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnSendCode).setOnClickListener(v -> {
            String number = etPhoneNumber.getText().toString().trim();
            if (number.isEmpty() || number.length() < 9) {
                Toast.makeText(this, "Enter a valid phone number", Toast.LENGTH_SHORT).show();
                return;
            }

            // CLEAN PHONE NUMBER FORMATTING
            if (number.startsWith("0")) number = number.substring(1);
            String fullPhoneNumber = "+94" + number;

            Toast.makeText(this, "Sending OTP...", Toast.LENGTH_SHORT).show();
            findViewById(R.id.btnSendCode).setEnabled(false);
            
            // REAL FIREBASE PHONE AUTH
            PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                    .setPhoneNumber(fullPhoneNumber)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(this)
                    .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        @Override
                        public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                            mAuth.signInWithCredential(credential).addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    // Already in DB? Splash will handle redirect. New user? Go to details.
                                    startActivity(new Intent(SignUpActivity.this, EmailInputActivity.class));
                                    finish();
                                }
                            });
                        }

                        @Override
                        public void onVerificationFailed(@NonNull FirebaseException e) {
                            findViewById(R.id.btnSendCode).setEnabled(true);
                            Toast.makeText(SignUpActivity.this, "Failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                            findViewById(R.id.btnSendCode).setEnabled(true);
                            Intent intent = new Intent(SignUpActivity.this, OtpVerificationActivity.class);
                            intent.putExtra("backend_verification_id", verificationId);
                            startActivity(intent);
                        }
                    }).build();
            PhoneAuthProvider.verifyPhoneNumber(options);
        });
    }
}
