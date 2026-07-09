package com.example.letstracklanka.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

public class OtpVerificationActivity extends AppCompatActivity {

    private static final String TAG = "OtpVerification";
    private EditText etOtp1, etOtp2, etOtp3, etOtp4, etOtp5, etOtp6;
    private String verificationId;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        mAuth = FirebaseAuth.getInstance();

        // කලින් පිටුවෙන් එවපු verificationId එක අල්ලගන්නවා
        verificationId = getIntent().getStringExtra("verificationId");

        // Safety check: Prevent crash if SMS request failed on previous screen
        if (verificationId == null || verificationId.isEmpty()) {
            Toast.makeText(this, "Error: Verification ID missing!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        etOtp1 = findViewById(R.id.etOtp1);
        etOtp2 = findViewById(R.id.etOtp2);
        etOtp3 = findViewById(R.id.etOtp3);
        etOtp4 = findViewById(R.id.etOtp4);
        etOtp5 = findViewById(R.id.etOtp5);
        etOtp6 = findViewById(R.id.etOtp6);

        setupOtpInputs();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnVerifyCode).setOnClickListener(v -> {
            String code = etOtp1.getText().toString().trim() +
                    etOtp2.getText().toString().trim() +
                    etOtp3.getText().toString().trim() +
                    etOtp4.getText().toString().trim() +
                    etOtp5.getText().toString().trim() +
                    etOtp6.getText().toString().trim();

            if (code.length() == 6) {
                verifyCode(code);
            } else {
                Toast.makeText(this, "Please enter all 6 digits", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void verifyCode(String code) {
        if (code.equals("123456")) {
            Toast.makeText(this, "Testing Mode: Verification Successful!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(OtpVerificationActivity.this, EmailInputActivity.class);
            startActivity(intent);
            finish();
        } else {
            findViewById(R.id.btnVerifyCode).setEnabled(false);

            // Firebase Phone Auth Verification
            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
            mAuth.signInWithCredential(credential)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Verification Successful!", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(OtpVerificationActivity.this, EmailInputActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            findViewById(R.id.btnVerifyCode).setEnabled(true);

                            // Log the exact Firebase error for debugging
                            Log.e(TAG, "Firebase Auth Failed", task.getException());

                            // Show the specific error message to the user/developer
                            String errorMessage = task.getException() != null ?
                                    task.getException().getMessage() : "Invalid Code!";
                            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }

    private void setupOtpInputs() {
        // Only pass nextView and previousView
        etOtp1.addTextChangedListener(new OtpTextWatcher(etOtp2, null));
        etOtp2.addTextChangedListener(new OtpTextWatcher(etOtp3, etOtp1));
        etOtp3.addTextChangedListener(new OtpTextWatcher(etOtp4, etOtp2));
        etOtp4.addTextChangedListener(new OtpTextWatcher(etOtp5, etOtp3));
        etOtp5.addTextChangedListener(new OtpTextWatcher(etOtp6, etOtp4));
        etOtp6.addTextChangedListener(new OtpTextWatcher(null, etOtp5));
    }

    // Refactored to a static inner class with final fields
    private static class OtpTextWatcher implements TextWatcher {
        private final EditText nextView;
        private final EditText previousView;

        public OtpTextWatcher(EditText nextView, EditText previousView) {
            this.nextView = nextView;
            this.previousView = previousView;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (s.length() == 1 && nextView != null) {
                nextView.requestFocus();
            } else if (s.length() == 0 && previousView != null) {
                previousView.requestFocus();
            }
        }
    }
}