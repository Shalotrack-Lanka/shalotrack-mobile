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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class EmailInputActivity extends AppCompatActivity {

    private TextInputEditText etEmail;
    private CountDownTimer countDownTimer;
    private Dialog timerDialog;
    private FirebaseAuth mAuth;

    // Background polling සඳහා
    private Handler verificationHandler;
    private Runnable verificationRunnable;
    private boolean isCheckingVerification = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_input);

        mAuth = FirebaseAuth.getInstance();
        etEmail = findViewById(R.id.etEmail);
        MaterialButton btnContinue = findViewById(R.id.btnContinue);
        verificationHandler = new Handler(Looper.getMainLooper());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnContinue.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();

            if (email.isEmpty()) {
                Toast.makeText(EmailInputActivity.this, "Please enter your email", Toast.LENGTH_SHORT).show();
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(EmailInputActivity.this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            } else {
                sendVerificationEmail(email);
            }
        });
    }

    private void sendVerificationEmail(String email) {
        FirebaseUser user = mAuth.getCurrentUser();

        if (user != null) {
            // ඇත්තම Firebase ක්‍රියාවලිය
            Toast.makeText(this, "Sending verification email...", Toast.LENGTH_SHORT).show();
            user.verifyBeforeUpdateEmail(email).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    showVerificationDialog();
                    startCheckingEmailVerification();
                } else {
                    Toast.makeText(EmailInputActivity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } else {
            // Test Mode එක (Phone Auth Bypass කළා නම් මේක වැඩ කරයි)
            Toast.makeText(this, "Test Mode: Simulating Verification...", Toast.LENGTH_SHORT).show();
            showVerificationDialog();
            simulateTestModeVerification();
        }
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
                stopCheckingEmailVerification();
            }
        }.start();

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

        btnCancel.setOnClickListener(v -> {
            stopCheckingEmailVerification();
            if (countDownTimer != null) countDownTimer.cancel();
            timerDialog.dismiss();
        });

        timerDialog.show();
    }

    // Real-time අදින්න (Polling) ලියපු කේතය
    private void startCheckingEmailVerification() {
        isCheckingVerification = true;
        verificationRunnable = new Runnable() {
            @Override
            public void run() {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null && isCheckingVerification) {
                    user.reload().addOnCompleteListener(task -> {
                        if (user.isEmailVerified()) {
                            // යූසර් ලින්ක් එක ක්ලික් කරලා!
                            stopCheckingEmailVerification();
                            if (timerDialog != null && timerDialog.isShowing()) timerDialog.dismiss();
                            if (countDownTimer != null) countDownTimer.cancel();
                            showSuccessDialog();
                        } else {
                            // තාම ක්ලික් කරලා නෑ, තව තත්පර 3කින් ආයෙත් අහනවා
                            verificationHandler.postDelayed(this, 3000);
                        }
                    });
                }
            }
        };
        // පළවෙනි පරීක්ෂාව තත්පර 3කින් පටන් ගන්නවා
        verificationHandler.postDelayed(verificationRunnable, 3000);
    }

    private void stopCheckingEmailVerification() {
        isCheckingVerification = false;
        if (verificationHandler != null && verificationRunnable != null) {
            verificationHandler.removeCallbacks(verificationRunnable);
        }
    }

    // Test mode එකේදී තත්පර 10කින් ඉබේම Verify වෙන්න හැදුවා
    private void simulateTestModeVerification() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (timerDialog != null && timerDialog.isShowing()) {
                timerDialog.dismiss();
                if (countDownTimer != null) countDownTimer.cancel();
                showSuccessDialog();
            }
        }, 10000); // තත්පර 10යි
    }

    private void showSuccessDialog() {
        Dialog successDialog = new Dialog(this);
        successDialog.setContentView(R.layout.dialog_email_success);
        successDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        successDialog.setCancelable(false);
        successDialog.show();

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
        stopCheckingEmailVerification();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}