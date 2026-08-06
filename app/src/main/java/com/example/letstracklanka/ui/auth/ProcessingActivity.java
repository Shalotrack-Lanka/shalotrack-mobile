package com.example.letstracklanka.ui.auth;

import android.app.AlertDialog;
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
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;

public class ProcessingActivity extends AppCompatActivity {

    private String name, nic, address, email;
    private ProgressBar progressBar;
    private AuthViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Retrieve data passed from EmailInputActivity
        name = getIntent().getStringExtra("EXTRA_NAME");
        nic = getIntent().getStringExtra("EXTRA_NIC");
        address = getIntent().getStringExtra("EXTRA_ADDRESS");
        email = getIntent().getStringExtra("EXTRA_EMAIL");

        progressBar = findViewById(R.id.progressBar);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        registerCustomer();
    }

    private void registerCustomer() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Retrieve verified phone number from Firebase session
        String phone = currentUser.getPhoneNumber();

        progressBar.setVisibility(View.VISIBLE);

        // API CALL: POST /api/Customers
        // This ensures the information is inserted into the DB via the Shalotrack API
        viewModel.performRegistration(name, email, phone, nic, address, currentUser.getUid())
                .observe(this, result -> {
                    progressBar.setVisibility(View.GONE);
                    if ("SUCCESS".equals(result)) {
                        Toast.makeText(this, "Profile Synced to Cloud!", Toast.LENGTH_SHORT).show();
                        goToDashboard();
                    } else {
                        Log.e("REG_ERROR", "Database Sync Error: " + result);
                        showRegistrationFailedDialog(result);
                    }
                });
    }

    private void goToDashboard() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showRegistrationFailedDialog(String rawResult) {
        // "already exists" means a field the user entered (NIC/email/phone) collides
        // with a DIFFERENT customer's record — retrying with the same data will just
        // fail again, so send them back to fix it instead of offering a blind Retry.
        boolean isDataConflict = rawResult != null
                && rawResult.toLowerCase(Locale.ROOT).contains("already exists");

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Registration Failed")
                .setMessage(extractMessage(rawResult))
                .setCancelable(false);

        if (isDataConflict) {
            builder.setPositiveButton("Edit Details", (dialog, which) -> {
                Intent intent = new Intent(this, EmailInputActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        } else {
            builder.setPositiveButton("Retry", (dialog, which) -> registerCustomer());
        }

        builder.setNegativeButton("Sign Out", (dialog, which) -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }).show();
    }

    private String extractMessage(String rawResult) {
        String prefix = "Server Error: ";
        if (rawResult != null && rawResult.startsWith(prefix)) {
            try {
                JSONObject json = new JSONObject(rawResult.substring(prefix.length()));
                if (json.has("message")) {
                    return json.getString("message");
                }
            } catch (JSONException ignored) {}
        }
        return "We couldn't save your profile to the server. Please check your connection and try again.";
    }
}
