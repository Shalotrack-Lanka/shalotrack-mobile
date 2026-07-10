package com.example.letstracklanka.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.example.letstracklanka.data.model.CustomerRequest;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.StatusResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {
    private final ApiService apiService = ApiClient.getClient().create(ApiService.class);

    // 1. Existing Registration Logic
    public MutableLiveData<Boolean> registerCustomer(CustomerRequest request) {
        MutableLiveData<Boolean> status = new MutableLiveData<>();

        apiService.createCustomer(request).enqueue(new Callback<StatusResponse>() {
            @Override
            public void onResponse(@NonNull Call<StatusResponse> call, @NonNull Response<StatusResponse> response) {
                status.setValue(response.isSuccessful());
            }

            @Override
            public void onFailure(@NonNull Call<StatusResponse> call, @NonNull Throwable t) {
                status.setValue(false);
            }
        });
        return status;
    }

    // 2. Verified Login Logic (Matches your LoginViewModel call)
    public void verifyCustomerBackend(String email, Callback<List<CustomerResponse>> callback) {
        apiService.getCustomerByEmail(email).enqueue(callback);
    }
}