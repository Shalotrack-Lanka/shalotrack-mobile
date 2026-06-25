package com.example.letstracklanka;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class EmailInputActivity extends AppCompatActivity {

    private TextInputEditText etEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_input);

        etEmail = findViewById(R.id.etEmail);
        MaterialButton btnContinue = findViewById(R.id.btnContinue);

        // Close this screen and go back to the previous screen when the back button is clicked
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Handle the Continue button click
        btnContinue.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();

            // Check if the email field is empty
            if (email.isEmpty()) {
                Toast.makeText(EmailInputActivity.this, "Please enter your email", Toast.LENGTH_SHORT).show();
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                // Check if the email format is valid (e.g., contains @ and domain)
                Toast.makeText(EmailInputActivity.this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            } else {
                // If the email is valid, navigate to the MainActivity
                Intent intent = new Intent(EmailInputActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}