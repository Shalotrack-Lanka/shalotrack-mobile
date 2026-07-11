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
import com.example.letstracklanka.ui.main.HomeActivity;
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

        // Retrieve data passed from EmailInputActivity
        String name = getIntent().getStringExtra("EXTRA_NAME");
        String nic = getIntent().getStringExtra("EXTRA_NIC");
        String address = getIntent().getStringExtra("EXTRA_ADDRESS");
        String email = getIntent().getStringExtra("EXTRA_EMAIL");
        
        // Retrieve verified phone number from Firebase session
        String phone = currentUser.getPhoneNumber();

        ProgressBar progressBar = findViewById(R.id.progressBar);
        AuthViewModel viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        progressBar.setVisibility(View.VISIBLE);
        
        // API CALL: POST /api/Customers
        // This ensures the information is inserted into your PostgreSQL DB via your backend
        viewModel.performRegistration(name, email, phone, nic, address)
                .observe(this, result -> {
                    progressBar.setVisibility(View.GONE);
                    if ("SUCCESS".equals(result)) {
                        Toast.makeText(this, "Profile Synced to Cloud!", Toast.LENGTH_SHORT).show();
                        
                        // LANDING: Move to Dashboard (HomeActivity)
                        Intent intent = new Intent(this, HomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Log.e("REG_ERROR", "Database Sync Error: " + result);
                        
                        // Error handling: If conflict (user exists), proceed. Otherwise, notify user.
                        if (result.contains("409") || result.contains("exists")) {
                             startActivity(new Intent(this, HomeActivity.class));
                             finish();
                        } else {
                             Toast.makeText(this, "Profile link failed, but account created. Moving to dashboard.", Toast.LENGTH_LONG).show();
                             startActivity(new Intent(this, HomeActivity.class));
                             finish();
                        }
                    }
                });
    }
}
