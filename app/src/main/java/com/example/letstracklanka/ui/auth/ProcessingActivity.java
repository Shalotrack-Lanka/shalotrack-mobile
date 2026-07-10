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

        // CLEAN PHONE NUMBER: 
        // Some DB entries have +94, some don't. 
        // Based on your DB dump, let's keep it consistent with what most APIs expect.
        String phone = currentUser.getPhoneNumber();

        ProgressBar progressBar = findViewById(R.id.progressBar);
        AuthViewModel viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        progressBar.setVisibility(View.VISIBLE);
        
        viewModel.performRegistration(name, email, phone, nic, address)
                .observe(this, result -> {
                    progressBar.setVisibility(View.GONE);
                    if ("SUCCESS".equals(result)) {
                        Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Log.e("REG_ERROR", "Backend Error: " + result);
                        
                        // If it's a conflict/already exists (Code 409), we should proceed
                        if (result.contains("409")) {
                             startActivity(new Intent(this, MainActivity.class));
                             finish();
                        } else {
                             // Show the actual server error to help debugging
                             Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                             // Do not finish() so user can see the error
                        }
                    }
                });
    }
}
