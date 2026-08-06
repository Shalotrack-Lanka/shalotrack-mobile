package com.example.letstracklanka.ui.auth;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.letstracklanka.data.model.CustomerRequest;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthViewModel extends ViewModel {

    private final ApiService apiService = ApiClient.getClient().create(ApiService.class);

    public LiveData<String> performRegistration(String name, String email, String phone, String nic, String address, String firebaseUid) {
        MutableLiveData<String> result = new MutableLiveData<>();

        // Create the Request Object
        CustomerRequest request = new CustomerRequest(name, email, phone, nic, address, firebaseUid);

        Log.d("API_TRACKING", "POSTing customer registration to /api/Customers");

        apiService.createCustomer(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.d("API_TRACKING", "Registration SUCCESS (200/201)");
                    result.setValue("SUCCESS");
                } else if (response.code() == 409) {
                    // A 409 here is ambiguous: it can mean this Firebase user already
                    // has a customer record (safe to proceed), OR that a unique field
                    // (e.g. NIC) collides with a DIFFERENT customer's record (a real
                    // validation failure that must NOT be treated as success).
                    // Resolve it by checking whether the signed-in user actually owns a profile.
                    String conflictBody = readErrorBody(response);
                    Log.e("API_TRACKING", "Create conflict (409), verifying own profile: " + conflictBody);
                    resolveConflict(result, conflictBody);
                } else {
                    String serverError = readErrorBody(response);
                    String errorMsg = serverError != null
                            ? "Server Error: " + serverError
                            : "Registration Failed (Code: " + response.code() + ")";
                    Log.e("API_TRACKING", "Backend Error Body: " + serverError);
                    result.setValue(errorMsg);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("API_TRACKING", "Network Failure: " + t.getMessage());
                result.setValue("Network Error: " + t.getMessage());
            }
        });

        return result;
    }

    private void resolveConflict(MutableLiveData<String> result, String conflictBody) {
        apiService.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.d("API_TRACKING", "Profile already exists for this account - treating as success");
                    result.setValue("SUCCESS");
                } else {
                    // No profile exists for this Firebase user, so the conflict was
                    // caused by another customer's record. This is a real failure.
                    result.setValue("Server Error: " + conflictBody);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                result.setValue("Server Error: " + conflictBody);
            }
        });
    }

    private String readErrorBody(Response<ResponseBody> response) {
        try {
            return response.errorBody() != null ? response.errorBody().string() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
