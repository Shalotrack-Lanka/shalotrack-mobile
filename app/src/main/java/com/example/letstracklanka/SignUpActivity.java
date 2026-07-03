package com.example.letstracklanka;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

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

        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();

        etPhoneNumber = findViewById(R.id.etPhoneNumber);

        String[] languages = {"English (US)", "Sinhala (LK)", "Tamil (LK)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                languages
        );
        AutoCompleteTextView dropdownLanguage = findViewById(R.id.dropdownLanguage);
        dropdownLanguage.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnSendCode).setOnClickListener(v -> {
            String number = etPhoneNumber.getText().toString().trim();

            if (number.isEmpty() || number.length() < 9) {
                Toast.makeText(this, "Enter a valid phone number", Toast.LENGTH_SHORT).show();
                return;
            }

            // =======================================================================
            // Test Mode Bypass: ඔයාගේ නම්බර් එක ගැහුවොත් කෙළින්ම OTP තිරයට යනවා
            if (number.equals("758381698") || number.equals("0758381698")) {
                Toast.makeText(this, "Test Mode: Moving to OTP Screen", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(SignUpActivity.this, OtpVerificationActivity.class);
                intent.putExtra("verificationId", "test_id_123");
                startActivity(intent);
                return; // මෙතනින් නවතිනවා, පල්ලෙහා තියෙන Firebase කේතය රන් වෙන්නේ නෑ
            }
            // =======================================================================

            if (number.length() == 10 && number.startsWith("0")) {
                number = number.substring(1);
            }
            String fullPhoneNumber = "+94" + number;

            Toast.makeText(this, "Sending OTP...", Toast.LENGTH_SHORT).show();

            PhoneAuthOptions options =
                    PhoneAuthOptions.newBuilder(mAuth)
                            .setPhoneNumber(fullPhoneNumber)
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(this)
                            .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                                @Override
                                public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                                }

                                @Override
                                public void onVerificationFailed(@NonNull FirebaseException e) {
                                    Toast.makeText(SignUpActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }

                                @Override
                                public void onCodeSent(@NonNull String verificationId,
                                                       @NonNull PhoneAuthProvider.ForceResendingToken token) {
                                    Intent intent = new Intent(SignUpActivity.this, OtpVerificationActivity.class);
                                    intent.putExtra("verificationId", verificationId);
                                    startActivity(intent);
                                }
                            })
                            .build();
            PhoneAuthProvider.verifyPhoneNumber(options);
        });
    }
}