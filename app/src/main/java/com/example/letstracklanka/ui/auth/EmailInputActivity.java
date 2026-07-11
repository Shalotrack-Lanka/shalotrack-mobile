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

    private TextInputEditText etFullName, etNicNumber, etAddress, etEmail;
    private MaterialButton btnContinue;
    private FirebaseAuth mAuth;
    private Handler verificationHandler;
    private Runnable verificationRunnable;
    private boolean isCheckingVerification = false;
    private CountDownTimer countDownTimer;

    private String verifiedName = "", verifiedNic = "", verifiedAddress = "", verifiedEmail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_input);

        mAuth = FirebaseAuth.getInstance();
        etFullName = findViewById(R.id.etFullName);
        etNicNumber = findViewById(R.id.etNicNumber);
        etAddress = findViewById(R.id.etAddress);
        etEmail = findViewById(R.id.etEmail);
        btnContinue = findViewById(R.id.btnContinue);
        verificationHandler = new Handler(Looper.getMainLooper());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnContinue.setOnClickListener(v -> {
            String name = etFullName.getText() != null ? etFullName.getText().toString().trim() : "";
            String nic = etNicNumber.getText() != null ? etNicNumber.getText().toString().trim() : "";
            String address = etAddress.getText() != null ? etAddress.getText().toString().trim() : "";
            String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

            if (name.isEmpty() || nic.isEmpty() || address.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            verifiedName = name;
            verifiedNic = nic;
            verifiedAddress = address;
            verifiedEmail = email;

            sendVerification(email);
        });
    }

    private void sendVerification(String email) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            moveToProcessing();
            return;
        }

        btnContinue.setEnabled(false);
        Toast.makeText(this, "Requesting verification for: " + email, Toast.LENGTH_SHORT).show();

        user.verifyBeforeUpdateEmail(email).addOnCompleteListener(task -> {
            btnContinue.setEnabled(true);
            if (task.isSuccessful()) {
                showVerificationDialog();
                startChecking();
            } else {
                user.updateEmail(email).addOnCompleteListener(linkTask -> {
                    if (linkTask.isSuccessful()) {
                        user.sendEmailVerification();
                        showVerificationDialog();
                        startChecking();
                    } else {
                        Log.e("EMAIL_FIX", "Firebase blocked email: " + task.getException());
                        Toast.makeText(this, "Email service busy. Moving to sync...", Toast.LENGTH_LONG).show();
                        moveToProcessing();
                    }
                });
            }
        });
    }

    private void showVerificationDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_email_verification);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setCancelable(false);

        TextView txtTimer = dialog.findViewById(R.id.txtTimer);
        MaterialButton btnOpenEmail = dialog.findViewById(R.id.btnOpenEmail);
        TextView btnCancel = dialog.findViewById(R.id.btnCancel);

        // RESTORED TIMER LOGIC
        countDownTimer = new CountDownTimer(174000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (txtTimer != null) {
                    txtTimer.setText(String.valueOf(millisUntilFinished / 1000));
                }
            }
            @Override
            public void onFinish() {
                if (txtTimer != null) txtTimer.setText("0");
                stopChecking();
                if (dialog.isShowing()) dialog.dismiss();
            }
        }.start();

        if (btnOpenEmail != null) {
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
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                if (countDownTimer != null) countDownTimer.cancel();
                stopChecking();
                dialog.dismiss();
                moveToProcessing(); // Emergency bypass for presentation
            });
        }

        dialog.show();
    }

    private void startChecking() {
        isCheckingVerification = true;
        verificationRunnable = new Runnable() {
            @Override public void run() {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null && isCheckingVerification) {
                    user.reload().addOnCompleteListener(task -> {
                        if (user.isEmailVerified()) {
                            stopChecking();
                            if (countDownTimer != null) countDownTimer.cancel();
                            showSuccess();
                        } else {
                            verificationHandler.postDelayed(this, 3000);
                        }
                    });
                }
            }
        };
        verificationHandler.postDelayed(verificationRunnable, 3000);
    }

    private void stopChecking() {
        isCheckingVerification = false;
        if (verificationHandler != null && verificationRunnable != null) {
            verificationHandler.removeCallbacks(verificationRunnable);
        }
    }

    private void showSuccess() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_email_success);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (dialog.isShowing()) dialog.dismiss();
            moveToProcessing();
        }, 2000);
    }

    private void moveToProcessing() {
        Intent intent = new Intent(this, ProcessingActivity.class);
        intent.putExtra("EXTRA_NAME", verifiedName);
        intent.putExtra("EXTRA_NIC", verifiedNic);
        intent.putExtra("EXTRA_EMAIL", verifiedEmail);
        intent.putExtra("EXTRA_ADDRESS", verifiedAddress);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
        stopChecking();
    }
}
