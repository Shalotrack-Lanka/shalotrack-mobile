package com.example.letstracklanka;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

public class OtpVerificationActivity extends AppCompatActivity {

    private EditText etOtp1, etOtp2, etOtp3, etOtp4, etOtp5, etOtp6;
    private String verificationId;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        mAuth = FirebaseAuth.getInstance();
        // කලින් තිරයෙන් එවපු ID එක ගන්නවා
        verificationId = getIntent().getStringExtra("verificationId");

        etOtp1 = findViewById(R.id.etOtp1);
        etOtp2 = findViewById(R.id.etOtp2);
        etOtp3 = findViewById(R.id.etOtp3);
        etOtp4 = findViewById(R.id.etOtp4);
        etOtp5 = findViewById(R.id.etOtp5);
        etOtp6 = findViewById(R.id.etOtp6);

        setupOtpInputs();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Verify බටන් එක එබුවාම
        findViewById(R.id.btnVerifyCode).setOnClickListener(v -> {
            String code = etOtp1.getText().toString() + etOtp2.getText().toString() +
                    etOtp3.getText().toString() + etOtp4.getText().toString() +
                    etOtp5.getText().toString() + etOtp6.getText().toString();

            if (code.length() == 6) {
                verifyCode(code);
            } else {
                Toast.makeText(this, "Please enter all 6 digits", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void verifyCode(String code) {
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // කෝඩ් එක හරි! ඊළඟ තිරයට යන්න.
                        Toast.makeText(this, "Verification Successful!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(OtpVerificationActivity.this, EmailInputActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        // කෝඩ් එක වැරදියි
                        Toast.makeText(this, "Invalid Code!", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupOtpInputs() {
        etOtp1.addTextChangedListener(new OtpTextWatcher(etOtp1, etOtp2));
        etOtp2.addTextChangedListener(new OtpTextWatcher(etOtp2, etOtp3));
        etOtp3.addTextChangedListener(new OtpTextWatcher(etOtp3, etOtp4));
        etOtp4.addTextChangedListener(new OtpTextWatcher(etOtp4, etOtp5));
        etOtp5.addTextChangedListener(new OtpTextWatcher(etOtp5, etOtp6));
        etOtp6.addTextChangedListener(new OtpTextWatcher(etOtp6, null));
    }

    private class OtpTextWatcher implements TextWatcher {
        private EditText currentView, nextView;

        public OtpTextWatcher(EditText currentView, EditText nextView) {
            this.currentView = currentView;
            this.nextView = nextView;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (s.length() == 1 && nextView != null) {
                nextView.requestFocus();
            }
        }
    }
}