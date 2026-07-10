package com.example.letstracklanka.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.ShaloTrackApp;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.main.MainActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OtpVerificationActivity extends AppCompatActivity {

    private static final String TAG = "OtpVerification";

    private EditText[] otpBoxes;
    private MaterialButton btnVerifyCode;
    private ImageView btnBack;
    private TextView txtResend;

    private FirebaseAuth mAuth;
    private String verificationId;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        mAuth = FirebaseAuth.getInstance();
        apiService = ApiClient.getClient().create(ApiService.class);
        verificationId = getIntent().getStringExtra("backend_verification_id");

        if (verificationId == null && !ShaloTrackApp.TEST_MODE) {
            Toast.makeText(this, "Error: Missing Verification ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnVerifyCode = findViewById(R.id.btnVerifyCode);
        btnBack = findViewById(R.id.btnBack);
        txtResend = findViewById(R.id.txtResend);

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
                Toast.makeText(this, "Please enter all 6 digits", Toast.LENGTH_SHORT).show();
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
        if (ShaloTrackApp.TEST_MODE && code.equals("123456")) {
            checkUserRegistration();
            return;
        }

        btnVerifyCode.setEnabled(false);
        btnVerifyCode.setText("Verifying...");
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                checkUserRegistration();
            } else {
                btnVerifyCode.setEnabled(true);
                btnVerifyCode.setText("Verify Code");
                Toast.makeText(this, "Verification failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkUserRegistration() {
        String phone = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getPhoneNumber() : "";
        
        // This is a simplified check. Since your API uses Email for lookups, 
        // and at this point we might not have the email, we have two choices:
        // 1. Go to EmailInputActivity regardless (Safe)
        // 2. Try to find user by Phone if your API supports it.
        
        // Let's go to EmailInputActivity. If they are already in DB, the API in ProcessingActivity
        // will handle it or we can let them re-verify. 
        // For a seamless "Savior" experience, we'll go to EmailInputActivity to ensure we have all data.
        
        Intent intent = new Intent(this, EmailInputActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
