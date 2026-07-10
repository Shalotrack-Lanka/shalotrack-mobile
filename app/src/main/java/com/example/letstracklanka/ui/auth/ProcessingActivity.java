package com.example.letstracklanka.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.letstracklanka.R;
import com.example.letstracklanka.ui.main.MainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ProcessingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Session expired. Please restart.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // 1. Get real data passed from the previous activity's Intent
        String name = getIntent().getStringExtra("EXTRA_NAME");
        String nic = getIntent().getStringExtra("EXTRA_NIC");
        String address = getIntent().getStringExtra("EXTRA_ADDRESS");

        // CRITICAL FIX: Extract the email from the Intent, fallback to Firebase if missing
        String email = getIntent().getStringExtra("EXTRA_EMAIL");
        if (email == null || email.trim().isEmpty()) {
            email = currentUser.getEmail();
        }

        // Get phone from Intent first, fallback to Firebase
        String phone = getIntent().getStringExtra("EXTRA_PHONE");
        if (phone == null || phone.trim().isEmpty()) {
            phone = currentUser.getPhoneNumber();
        }

        // 2. Final safety check before calling the API
        if (email == null || email.trim().isEmpty() || phone == null || phone.trim().isEmpty()) {
            Toast.makeText(this, "Error: Email and Phone are strictly required.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        ProgressBar progressBar = findViewById(R.id.progressBar);
        AuthViewModel viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // 3. Execute registration
        progressBar.setVisibility(View.VISIBLE);
        viewModel.performRegistration(name, email, phone, nic, address)
                .observe(this, success -> {
                    progressBar.setVisibility(View.GONE);
                    if (success) {
                        Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(this, "Registration Failed. Check API.", Toast.LENGTH_LONG).show();
                        // Do not proceed to MainActivity if registration fails
                    }
                });
    }
}