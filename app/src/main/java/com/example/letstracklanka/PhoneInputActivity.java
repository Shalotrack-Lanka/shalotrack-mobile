package com.example.letstracklanka; // ඔයාගේ package name එක මෙතනට දාන්න

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp; // මේක අලුතින් ආවා
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class PhoneInputActivity extends AppCompatActivity {

    private EditText etPhoneNumber;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_input); // XML ෆයිල් එකේ නම

        // ---- මෙන්න මේ පේළිය අනිවාර්යයෙන්ම තියෙන්න ඕනේ! ----
        FirebaseApp.initializeApp(this);
        // ----------------------------------------------------

        // Firebase Auth එකට සම්බන්ධ වීම
        mAuth = FirebaseAuth.getInstance();

        etPhoneNumber = findViewById(R.id.etPhoneNumber);

        // Back බටන් එක එබුවාම කලින් තිරයට යන්න
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Send Code බටන් එක එබුවාම
        findViewById(R.id.btnSendCode).setOnClickListener(v -> {
            String number = etPhoneNumber.getText().toString().trim();

            // නම්බර් එකේ ඉලක්කම් 9කට වඩා අඩුවෙන් තියෙනවද බලනවා
            if (number.isEmpty() || number.length() < 9) {
                Toast.makeText(this, "Enter a valid phone number", Toast.LENGTH_SHORT).show();
                return;
            }

            // ලංකාවේ නම්බර් එකක් විදිහට හදාගන්න (+94)
            if (number.startsWith("0")) {
                number = number.substring(1);
            }
            String fullPhoneNumber = "+94" + number;

            Toast.makeText(this, "Sending OTP...", Toast.LENGTH_SHORT).show();

            // Firebase එකෙන් SMS එක යවන කොටස
            PhoneAuthOptions options =
                    PhoneAuthOptions.newBuilder(mAuth)
                            .setPhoneNumber(fullPhoneNumber)       // යවන්න ඕනේ නම්බර් එක
                            .setTimeout(60L, TimeUnit.SECONDS)     // Timeout එක තත්පර 60යි
                            .setActivity(this)
                            .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                                @Override
                                public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                                    // සමහර ෆෝන් වලට SMS එක ආව ගමන් ඉබේම Verify වෙනවා
                                }

                                @Override
                                public void onVerificationFailed(@NonNull FirebaseException e) {
                                    Toast.makeText(PhoneInputActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }

                                @Override
                                public void onCodeSent(@NonNull String verificationId,
                                                       @NonNull PhoneAuthProvider.ForceResendingToken token) {
                                    // SMS එක සාර්ථකව යැව්වාම OTP තිරයට යනවා
                                    Intent intent = new Intent(PhoneInputActivity.this, OtpVerificationActivity.class);

                                    // අර ආපු කේතය (verificationId) ඊළඟ තිරයට යවනවා
                                    intent.putExtra("verificationId", verificationId);
                                    startActivity(intent);
                                }
                            })
                            .build();
            PhoneAuthProvider.verifyPhoneNumber(options);
        });
    }
}