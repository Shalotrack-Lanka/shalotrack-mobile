package com.example.letstracklanka.ui.auth;

import android.util.Log; // Required for tracking the error

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.letstracklanka.data.model.CustomerRequest;
import com.example.letstracklanka.data.model.StatusResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthViewModel extends ViewModel {

    // Initialize the API service using your ApiClient
    private final ApiService apiService = ApiClient.getClient().create(ApiService.class);

    public LiveData<Boolean> performRegistration(String name, String email, String phone, String nic, String address) {
        MutableLiveData<Boolean> result = new MutableLiveData<>();

        // 1. THIS WILL SHOW YOU EXACTLY WHAT IS BEING SENT
        Log.d("API_TRACKING", "Attempting to register -> Name: " + name + " | Email: " + email + " | Phone: " + phone);

        // Create the Request Object
        CustomerRequest request = new CustomerRequest(name, email, phone, nic, address);

        // Call your Retrofit API
        apiService.createCustomer(request).enqueue(new Callback<StatusResponse>() {
            @Override
            public void onResponse(@NonNull Call<StatusResponse> call, @NonNull Response<StatusResponse> response) {

                // 2. THIS WILL PRINT THE EXACT C# BACKEND ERROR TO YOUR LOGCAT
                if (!response.isSuccessful()) {
                    try {
                        Log.e("API_TRACKING", "Backend rejected request. Error: " + response.errorBody().string());
                    } catch (Exception ignored) {}
                }

                // Check if API returned 200/201 Success
                result.setValue(response.isSuccessful());
            }

            @Override
            public void onFailure(@NonNull Call<StatusResponse> call, @NonNull Throwable t) {
                // 3. THIS WILL PRINT IF THE SERVER IS DOWN
                Log.e("API_TRACKING", "Network Request Failed: " + t.getMessage());
                result.setValue(false);
            }
        });

        return result;
    }
}