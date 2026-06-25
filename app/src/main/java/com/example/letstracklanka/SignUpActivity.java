package com.example.letstracklanka;

import android.content.Intent; // Import required for navigation
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import androidx.appcompat.app.AppCompatActivity;

public class SignUpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the UI design for the sign-up screen
        setContentView(R.layout.activity_sign_up);

        // Add languages to the dropdown menu
        String[] languages = {"English (US)", "Sinhala (LK)", "Tamil (LK)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                languages
        );

        AutoCompleteTextView dropdownLanguage = findViewById(R.id.dropdownLanguage);
        dropdownLanguage.setAdapter(adapter);

        // Go back to the previous screen when the back button is clicked
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Navigate to the OTP verification screen when 'Send Code' is clicked
        findViewById(R.id.btnSendCode).setOnClickListener(v -> {
            Intent intent = new Intent(SignUpActivity.this, OtpVerificationActivity.class);
            startActivity(intent);
        });
    }
}