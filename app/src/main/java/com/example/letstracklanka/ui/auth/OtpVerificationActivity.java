package com.example.letstracklanka.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
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

    private EditText[] otpBoxes;
    private MaterialButton btnVerifyCode;
    private String verificationId;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        mAuth = FirebaseAuth.getInstance();
        verificationId = getIntent().getStringExtra("backend_verification_id");

        btnVerifyCode = findViewById(R.id.btnVerifyCode);
        ImageView btnBack = findViewById(R.id.btnBack);

        otpBoxes = new EditText[]{
                findViewById(R.id.etOtp1), findViewById(R.id.etOtp2),
                findViewById(R.id.etOtp3), findViewById(R.id.etOtp4),
                findViewById(R.id.etOtp5), findViewById(R.id.etOtp6)
        };

        setupOtpInputs();
        btnBack.setOnClickListener(v -> finish());
        
        btnVerifyCode.setOnClickListener(v -> {
            String code = getOtpString();
            if (code.length() < 6) {
                Toast.makeText(this, "Enter 6 digits", Toast.LENGTH_SHORT).show();
                return;
            }
            verifyOtp(code);
        });
    }

    private void setupOtpInputs() {
        for (int i = 0; i < otpBoxes.length; i++) {
            final int currentIndex = i;
            otpBoxes[i].addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1 && currentIndex < otpBoxes.length - 1) otpBoxes[currentIndex + 1].requestFocus();
                }
                @Override public void afterTextChanged(Editable s) {}
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

    private String getOtpString() {
        StringBuilder otp = new StringBuilder();
        for (EditText box : otpBoxes) otp.append(box.getText().toString().trim());
        return otp.toString();
    }

    private void verifyOtp(String code) {
        btnVerifyCode.setEnabled(false);
        btnVerifyCode.setText("Verifying...");
        
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                // Success: Move to Details screen to ensure they are in your DB
                Intent intent = new Intent(this, EmailInputActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                btnVerifyCode.setEnabled(true);
                btnVerifyCode.setText("Verify Code");
                Toast.makeText(this, "Invalid Code", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
