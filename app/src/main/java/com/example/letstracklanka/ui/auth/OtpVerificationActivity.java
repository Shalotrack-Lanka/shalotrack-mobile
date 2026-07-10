package com.example.letstracklanka.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

public class OtpVerificationActivity extends AppCompatActivity {

    private static final String TAG = "OtpVerification";

    private EditText[] otpBoxes;
    private MaterialButton btnVerifyCode;
    private ImageView btnBack;
    private TextView txtResend;

    private FirebaseAuth mAuth;
    private String verificationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        mAuth = FirebaseAuth.getInstance();
        verificationId = getIntent().getStringExtra("backend_verification_id");

        if (verificationId == null) {
            Toast.makeText(this, "Error: Missing Verification ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize UI
        btnVerifyCode = findViewById(R.id.btnVerifyCode);
        btnBack = findViewById(R.id.btnBack);
        txtResend = findViewById(R.id.txtResend);

        otpBoxes = new EditText[]{
                findViewById(R.id.etOtp1),
                findViewById(R.id.etOtp2),
                findViewById(R.id.etOtp3),
                findViewById(R.id.etOtp4),
                findViewById(R.id.etOtp5),
                findViewById(R.id.etOtp6)
        };

        setupOtpInputs();

        // Handle Buttons
        btnBack.setOnClickListener(v -> finish());

        txtResend.setOnClickListener(v -> {
            Toast.makeText(this, "Requesting new code...", Toast.LENGTH_SHORT).show();
            // You can add logic here to trigger the phone auth again
        });

        btnVerifyCode.setOnClickListener(v -> {
            String code = getOtpString();
            if (code.length() < 6) {
                Toast.makeText(this, "Please enter all 6 digits", Toast.LENGTH_SHORT).show();
                return;
            }
            verifyOtpWithFirebase(code);
        });
    }

    // Automatically moves focus to the next/previous box as the user types
    private void setupOtpInputs() {
        for (int i = 0; i < otpBoxes.length; i++) {
            final int currentIndex = i;

            otpBoxes[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1 && currentIndex < otpBoxes.length - 1) {
                        otpBoxes[currentIndex + 1].requestFocus();
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
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

    // Combines all 6 boxes into a single String
    private String getOtpString() {
        StringBuilder otp = new StringBuilder();
        for (EditText box : otpBoxes) {
            otp.append(box.getText().toString().trim());
        }
        return otp.toString();
    }

    private void verifyOtpWithFirebase(String code) {
        btnVerifyCode.setEnabled(false);
        btnVerifyCode.setText("Verifying...");

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);

        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            btnVerifyCode.setEnabled(true);
            btnVerifyCode.setText("Verify Code");

            if (task.isSuccessful()) {
                Toast.makeText(this, "Phone Verified Successfully!", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(OtpVerificationActivity.this, EmailInputActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } else {
                String errorMessage = task.getException() != null ? task.getException().getMessage() : "Verification failed";
                Log.e(TAG, "OTP Verification Failed", task.getException());
                Toast.makeText(this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();

                // Clear boxes on failure
                for (EditText box : otpBoxes) box.setText("");
                otpBoxes[0].requestFocus();
            }
        });
    }
}