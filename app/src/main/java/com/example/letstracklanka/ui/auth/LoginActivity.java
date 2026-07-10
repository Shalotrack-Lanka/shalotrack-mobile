package com.example.letstracklanka.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "PhoneLogin";

    private TextInputLayout phoneInput;
    private MaterialButton btnSendCode;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 1. Initialize Firebase and UI
        mAuth = FirebaseAuth.getInstance();
        phoneInput = findViewById(R.id.phoneInput);
        btnSendCode = findViewById(R.id.btnSendCode);
        progressBar = findViewById(R.id.progressBar);

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

    private void sendVerificationCode(String phoneNumber) {
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
                // FIREBASE READ THE SMS AUTOMATICALLY!
                progressBar.setVisibility(View.GONE);
                Toast.makeText(LoginActivity.this, "Auto-Verified! Logging in...", Toast.LENGTH_SHORT).show();

                // Sign the user in immediately
                mAuth.signInWithCredential(credential).addOnCompleteListener(LoginActivity.this, task -> {
                    if (task.isSuccessful()) {
                        // Success! Go straight to EmailInputActivity (skipping the OTP screen)
                        Intent intent = new Intent(LoginActivity.this, EmailInputActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    } else {
                        Toast.makeText(LoginActivity.this, "Auto-login failed.", Toast.LENGTH_SHORT).show();
                        btnSendCode.setEnabled(true);
                    }
                });
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                // Triggered if the phone number is invalid, or if the SMS quota is exceeded
                progressBar.setVisibility(View.GONE);
                btnSendCode.setEnabled(true);
                Log.e(TAG, "Verification failed", e);
                Toast.makeText(LoginActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onCodeSent(@NonNull String verificationId,
                                   @NonNull PhoneAuthProvider.ForceResendingToken token) {
                // SMS SENT, BUT NOT AUTO-READ. Move to the OTP Screen so they can type it.
                progressBar.setVisibility(View.GONE);
                btnSendCode.setEnabled(true);

                Toast.makeText(LoginActivity.this, "Code sent!", Toast.LENGTH_SHORT).show();

                // Move to the screen where the user types in the 6-digit OTP
                Intent intent = new Intent(LoginActivity.this, OtpVerificationActivity.class);

                // Pass the secret verification ID to the next screen (required to confirm the OTP)
                intent.putExtra("backend_verification_id", verificationId);
                startActivity(intent);
            }
        };
    }
}