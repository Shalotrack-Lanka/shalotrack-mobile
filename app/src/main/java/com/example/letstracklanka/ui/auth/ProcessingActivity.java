package com.example.letstracklanka.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        String name = getIntent().getStringExtra("EXTRA_NAME");
        String nic = getIntent().getStringExtra("EXTRA_NIC");
        String address = getIntent().getStringExtra("EXTRA_ADDRESS");
        String email = getIntent().getStringExtra("EXTRA_EMAIL");

        // Use Firebase phone number (it's verified)
        String phone = currentUser.getPhoneNumber();
        if (phone != null) {
            phone = phone.replace("+", ""); // Clean for API
        }

        ProgressBar progressBar = findViewById(R.id.progressBar);
        AuthViewModel viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        progressBar.setVisibility(View.VISIBLE);
        
        // Final Registration Call
        viewModel.performRegistration(name, email, phone, nic, address)
                .observe(this, result -> {
                    progressBar.setVisibility(View.GONE);
                    if ("SUCCESS".equals(result)) {
                        Toast.makeText(this, "Welcome to ShaloTrack!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        // If registration fails (e.g., user already exists in DB), 
                        // we still let them in because they passed Firebase Phone Auth.
                        Log.e("REG_ERROR", "API Error: " + result);
                        
                        // If it's a conflict (user already exists), we should proceed.
                        if (result.contains("409") || result.contains("exists")) {
                             startActivity(new Intent(this, MainActivity.class));
                             finish();
                        } else {
                             Toast.makeText(this, "Database Sync Failed. Please try again.", Toast.LENGTH_LONG).show();
                             finish(); // Go back and try again
                        }
                    }
                });
    }
}
