package com.example.letstracklanka;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class EmailInputActivity extends AppCompatActivity {

    private TextInputEditText etEmail;
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_input);

        etEmail = findViewById(R.id.etEmail);
        MaterialButton btnContinue = findViewById(R.id.btnContinue);

        // Close this screen when the back button is clicked
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Handle the Continue button click
        btnContinue.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();

            if (email.isEmpty()) {
                Toast.makeText(EmailInputActivity.this, "Please enter your email", Toast.LENGTH_SHORT).show();
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(EmailInputActivity.this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            } else {
                // Email is valid, show custom dialog
                showVerificationDialog();
            }
        });
    }

    private void showVerificationDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_email_verification);

        // Make the dialog background transparent to remove extra white space
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Don't close the dialog unless the user clicks the Cancel button
        dialog.setCancelable(false);

        TextView txtTimer = dialog.findViewById(R.id.txtTimer);
        MaterialButton btnOpenEmail = dialog.findViewById(R.id.btnOpenEmail);
        TextView btnCancel = dialog.findViewById(R.id.btnCancel);

        // Start countdown timer for 174 seconds
        countDownTimer = new CountDownTimer(174000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                txtTimer.setText(String.valueOf(millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                txtTimer.setText("0");
            }
        }.start();

        // Open the default Email app on the phone
        btnOpenEmail.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_APP_EMAIL);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(EmailInputActivity.this, "No email app found", Toast.LENGTH_SHORT).show();
            }
        });

        // Handle the Cancel button click
        btnCancel.setOnClickListener(v -> {
            if (countDownTimer != null) {
                countDownTimer.cancel(); // Stop timer
            }
            dialog.dismiss(); // Close dialog
        });

        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop the timer when the screen closes to prevent memory leaks
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}