package com.example.letstracklanka;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class EmailInputActivity extends AppCompatActivity {

    private TextInputEditText etEmail;
    private CountDownTimer countDownTimer;
    private Dialog timerDialog;
    private boolean isEmailAppOpened = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_input);

        etEmail = findViewById(R.id.etEmail);
        MaterialButton btnContinue = findViewById(R.id.btnContinue);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnContinue.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();

            if (email.isEmpty()) {
                Toast.makeText(EmailInputActivity.this, "Please enter your email", Toast.LENGTH_SHORT).show();
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(EmailInputActivity.this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            } else {
                showVerificationDialog();
            }
        });
    }

    private void showVerificationDialog() {
        timerDialog = new Dialog(this);
        timerDialog.setContentView(R.layout.dialog_email_verification);
        timerDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        timerDialog.setCancelable(false);

        TextView txtTimer = timerDialog.findViewById(R.id.txtTimer);
        MaterialButton btnOpenEmail = timerDialog.findViewById(R.id.btnOpenEmail);
        TextView btnCancel = timerDialog.findViewById(R.id.btnCancel);

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

        btnOpenEmail.setOnClickListener(v -> {
            isEmailAppOpened = true; // Mark that user went to email app
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_APP_EMAIL);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(EmailInputActivity.this, "No email app found", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> {
            if (countDownTimer != null) countDownTimer.cancel();
            timerDialog.dismiss();
            isEmailAppOpened = false; // Reset if cancelled
        });

        timerDialog.show();
    }

    // This runs when user comes back from the Email app
    @Override
    protected void onResume() {
        super.onResume();
        if (isEmailAppOpened) {
            isEmailAppOpened = false;

            // Close timer dialog
            if (timerDialog != null && timerDialog.isShowing()) {
                timerDialog.dismiss();
            }
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }

            // Show Success Dialog
            showSuccessDialog();
        }
    }

    private void showSuccessDialog() {
        Dialog successDialog = new Dialog(this);
        successDialog.setContentView(R.layout.dialog_email_success);
        successDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        successDialog.setCancelable(false);
        successDialog.show();

        // Wait 2 seconds, then go to Processing Screen
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            successDialog.dismiss();
            Intent intent = new Intent(EmailInputActivity.this, ProcessingActivity.class);
            startActivity(intent);
            finish();
        }, 2000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}