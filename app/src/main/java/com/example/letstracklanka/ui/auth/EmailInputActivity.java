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

    // UI Elements
    private TextInputEditText etFullName;
    private TextInputEditText etNicNumber;
    private TextInputEditText etAddress;
    private TextInputEditText etEmail;
    private MaterialButton btnContinue;

    // Timer & Verification
    private CountDownTimer countDownTimer;
    private Dialog timerDialog;
    private FirebaseAuth mAuth;
    private Handler verificationHandler;
    private Runnable verificationRunnable;
    private boolean isCheckingVerification = false;

    // Variables to hold verified data before sending to ProcessingActivity
    private String verifiedName = "";
    private String verifiedNic = "";
    private String verifiedAddress = "";
    private String verifiedEmail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_input);

        mAuth = FirebaseAuth.getInstance();

        // Initialize all input fields
        etFullName = findViewById(R.id.etFullName);
        etNicNumber = findViewById(R.id.etNicNumber);
        etAddress = findViewById(R.id.etAddress);
        etEmail = findViewById(R.id.etEmail);
        btnContinue = findViewById(R.id.btnContinue);
        verificationHandler = new Handler(Looper.getMainLooper());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnContinue.setOnClickListener(v -> {
            // 1. Read values securely
            String name = etFullName.getText() != null ? etFullName.getText().toString().trim() : "";
            String nic = etNicNumber.getText() != null ? etNicNumber.getText().toString().trim() : "";
            String address = etAddress.getText() != null ? etAddress.getText().toString().trim() : "";
            String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

            // 2. Validate all fields
            if (name.isEmpty()) {
                etFullName.setError("Full Name is required");
                etFullName.requestFocus();
                return;
            }
            if (nic.isEmpty()) {
                etNicNumber.setError("NIC is required");
                etNicNumber.requestFocus();
                return;
            }
            if (address.isEmpty()) {
                etAddress.setError("Address is required");
                etAddress.requestFocus();
                return;
            }
            if (email.isEmpty()) {
                etEmail.setError("Email is required");
                etEmail.requestFocus();
                return;
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError("Please enter a valid email address");
                etEmail.requestFocus();
                return;
            }

            // 3. Save to variables so we can pass them later
            verifiedName = name;
            verifiedNic = nic;
            verifiedAddress = address;
            verifiedEmail = email;

            // 4. Proceed to Firebase Verification
            sendVerificationEmail(verifiedEmail);
        });
    }

    private void sendVerificationEmail(String email) {
        FirebaseUser user = mAuth.getCurrentUser();

        // TEST MODE (for local testing without Firebase)
        if (user == null) {
            Toast.makeText(this, "Simulating Email Verification...", Toast.LENGTH_SHORT).show();
            showVerificationDialog();
            new Handler(Looper.getMainLooper()).postDelayed(this::showSuccessDialog, 3000);
            return;
        }

        btnContinue.setEnabled(false);
        Toast.makeText(this, "Sending verification email...", Toast.LENGTH_SHORT).show();

        user.verifyBeforeUpdateEmail(email).addOnCompleteListener(task -> {
            btnContinue.setEnabled(true);
            if (task.isSuccessful()) {
                showVerificationDialog();
                startCheckingEmailVerification();
            } else {
                String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error occurred.";
                Log.e(TAG, "Email verification process failed", task.getException());
                Toast.makeText(this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
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

        countDownTimer = new CountDownTimer(174000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                txtTimer.setText(String.valueOf(millisUntilFinished / 1000));
            }
            @Override
            public void onFinish() {
                txtTimer.setText("0");
                stopCheckingEmailVerification();
                if (timerDialog.isShowing()) timerDialog.dismiss();
                Toast.makeText(EmailInputActivity.this, "Verification timed out.", Toast.LENGTH_SHORT).show();
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
                    user.reload().addOnCompleteListener(task -> {
                        if (user.isEmailVerified()) {
                            stopCheckingEmailVerification();
                            if (timerDialog != null && timerDialog.isShowing()) timerDialog.dismiss();
                            if (countDownTimer != null) countDownTimer.cancel();
                            showSuccessDialog();
                        } else {
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
            intent.putExtra("EXTRA_NAME", verifiedName);
            intent.putExtra("EXTRA_NIC", verifiedNic);
            intent.putExtra("EXTRA_EMAIL", verifiedEmail);
            intent.putExtra("EXTRA_ADDRESS", verifiedAddress);

            startActivity(intent);
            finish();
        }, 2000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCheckingEmailVerification();
        if (countDownTimer != null) countDownTimer.cancel();
        if (timerDialog != null && timerDialog.isShowing()) timerDialog.dismiss();
    }
}
