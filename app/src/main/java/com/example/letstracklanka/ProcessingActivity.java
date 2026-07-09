package com.example.letstracklanka;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProcessingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        registerUserToBackend();
    }

    private void registerUserToBackend() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            CustomerRequest request = buildCustomerRequest(user);
            ApiService apiService = ApiClient.getClient().create(ApiService.class);

            apiService.createCustomer(request).enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(ProcessingActivity.this, "Customer Created!", Toast.LENGTH_SHORT).show();
                    } else {
                        // FIX 1: If server rejects (e.g. email already exists), log it but still move forward
                        Log.e("API_ERROR", "Server Code: " + response.code());
                        Toast.makeText(ProcessingActivity.this, "Server error, but bypassing to Main...", Toast.LENGTH_SHORT).show();
                    }

                    // Always move to the next screen regardless of success or server error
                    moveToMainActivity();
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    // FIX 2: If the network times out, log it but still move forward
                    Log.e("API_FAILURE", "Error: " + t.getMessage(), t);
                    Toast.makeText(ProcessingActivity.this, "Network Timeout! Bypassing to Main...", Toast.LENGTH_LONG).show();
                }
            });
        } else {
            Toast.makeText(this, "Session expired. Please restart registration.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // Extracted method to keep the jump logic clean
    private void moveToMainActivity() {
        Intent intent = new Intent(ProcessingActivity.this, MainActivity.class);
        startActivity(intent);
        finish(); // Closes the Processing screen so they can't hit back
    }

    @NonNull
    private CustomerRequest buildCustomerRequest(@NonNull FirebaseUser user) {
        String verifiedPhone = user.getPhoneNumber();
        String verifiedEmail = user.getEmail();

        // Placeholder testing data
        String testName = "John Doe";
        String testNic = "199512345V";
        String testAddress = "Colombo, Sri Lanka";

        return new CustomerRequest(testName, verifiedEmail, verifiedPhone, testNic, testAddress);
    }
}