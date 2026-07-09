package com.example.letstracklanka.ui.auth;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class EmailInputActivity extends AppCompatActivity {

    private static final String TAG = "EmailVerification";
    private TextInputEditText etEmail;
    private MaterialButton btnContinue;
    private CountDownTimer countDownTimer;
    private Dialog timerDialog;
    private FirebaseAuth mAuth;

    // Background polling variables
    private Handler verificationHandler;
    private Runnable verificationRunnable;
    private boolean isCheckingVerification = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_input);

        mAuth = FirebaseAuth.getInstance();
        etEmail = findViewById(R.id.etEmail);
        btnContinue = findViewById(R.id.btnContinue);
        verificationHandler = new Handler(Looper.getMainLooper());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnContinue.setOnClickListener(v -> {
            String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            } else {
                sendVerificationEmail(email);
            }
        });
    }

    private void sendVerificationEmail(String email) {
        FirebaseUser user = mAuth.getCurrentUser();

        // Safety check: The user MUST be logged in from the OTP step
        if (user == null) {
            Toast.makeText(this, "Authentication error. Please restart the app.", Toast.LENGTH_LONG).show();
            return;
        }

        btnContinue.setEnabled(false); // Prevent multiple clicks
        Toast.makeText(this, "Sending verification email...", Toast.LENGTH_SHORT).show();

        // Real Firebase Process
        user.verifyBeforeUpdateEmail(email).addOnCompleteListener(task -> {
            btnContinue.setEnabled(true);
            if (task.isSuccessful()) {
                showVerificationDialog();
                startCheckingEmailVerification();
            } else {
                Log.e(TAG, "Email sending failed", task.getException());
                String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error occurred";
                Toast.makeText(this, "Error: " + errorMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showVerificationDialog() {
        timerDialog = new Dialog(this);
        timerDialog.setContentView(R.layout.dialog_email_verification);
        if (timerDialog.getWindow() != null) {
            timerDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        timerDialog.setCancelable(false);

        TextView txtTimer = timerDialog.findViewById(R.id.txtTimer);
        MaterialButton btnOpenEmail = timerDialog.findViewById(R.id.btnOpenEmail);
        TextView btnCancel = timerDialog.findViewById(R.id.btnCancel);

        // 2 minutes and 54 seconds timer
        countDownTimer = new CountDownTimer(174000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                txtTimer.setText(String.valueOf(millisUntilFinished / 1000));
            }
            @Override
            public void onFinish() {
                txtTimer.setText("0");
                stopCheckingEmailVerification();
                Toast.makeText(EmailInputActivity.this, "Verification timed out.", Toast.LENGTH_SHORT).show();
                if (timerDialog.isShowing()) timerDialog.dismiss();
            }
        }.start();

        btnOpenEmail.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_APP_EMAIL);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> {
            stopCheckingEmailVerification();
            if (countDownTimer != null) countDownTimer.cancel();
            timerDialog.dismiss();
        });

        timerDialog.show();
    }

    private void startCheckingEmailVerification() {
        isCheckingVerification = true;
        verificationRunnable = new Runnable() {
            @Override
            public void run() {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null && isCheckingVerification) {
                    // Reload is mandatory to fetch the latest verification status from Firebase servers
                    user.reload().addOnCompleteListener(task -> {
                        if (task.isSuccessful() && user.isEmailVerified()) {
                            // User clicked the link!
                            stopCheckingEmailVerification();
                            if (timerDialog != null && timerDialog.isShowing()) timerDialog.dismiss();
                            if (countDownTimer != null) countDownTimer.cancel();
                            showSuccessDialog();
                        } else {
                            // Not verified yet, check again in 3 seconds
                            verificationHandler.postDelayed(this, 3000);
                        }
                    });
                }
            }
        };
        verificationHandler.postDelayed(verificationRunnable, 3000);
    }

    private void stopCheckingEmailVerification() {
        isCheckingVerification = false;
        if (verificationHandler != null && verificationRunnable != null) {
            verificationHandler.removeCallbacks(verificationRunnable);
        }
    }

    private void showSuccessDialog() {
        Dialog successDialog = new Dialog(this);
        successDialog.setContentView(R.layout.dialog_email_success);
        if (successDialog.getWindow() != null) {
            successDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        successDialog.setCancelable(false);
        successDialog.show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (successDialog.isShowing()) successDialog.dismiss();
            Intent intent = new Intent(EmailInputActivity.this, ProcessingActivity.class);
            startActivity(intent);
            finish();
        }, 2000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCheckingEmailVerification();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        if (timerDialog != null && timerDialog.isShowing()) {
            timerDialog.dismiss();
        }
    }
}