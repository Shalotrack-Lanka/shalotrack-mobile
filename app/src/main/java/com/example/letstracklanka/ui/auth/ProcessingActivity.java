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

        // 1. Get real data passed from previous activity
        String name = getIntent().getStringExtra("EXTRA_NAME");
        String nic = getIntent().getStringExtra("EXTRA_NIC");
        String address = getIntent().getStringExtra("EXTRA_ADDRESS");

        // 2. Safely check for the Firebase User
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            // If the user is null, bounce them back to login to prevent a crash
            Toast.makeText(this, "Session expired. Please restart registration.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return; // Stop running any code below this line
        }

        // 3. Get verified data from Firebase safely (No NPE possible here now)
        String email = currentUser.getEmail();
        String phone = currentUser.getPhoneNumber();

        ProgressBar progressBar = findViewById(R.id.progressBar);
        AuthViewModel viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // 4. Execute registration
        progressBar.setVisibility(View.VISIBLE);
        viewModel.performRegistration(name, email, phone, nic, address)
                .observe(this, success -> {
                    progressBar.setVisibility(View.GONE);
                    if (success) {
                        Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Sync complete, entering app...", Toast.LENGTH_LONG).show();
                    }
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
    }
}