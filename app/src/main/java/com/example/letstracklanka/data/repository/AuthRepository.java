package com.example.letstracklanka.data.repository;

import androidx.lifecycle.MutableLiveData;
import com.example.letstracklanka.data.model.CustomerRequest;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {
    private final ApiService apiService = ApiClient.getClient().create(ApiService.class);

    // Existing Registration Logic
    public MutableLiveData<Boolean> registerCustomer(CustomerRequest request) {
        MutableLiveData<Boolean> status = new MutableLiveData<>();
        apiService.createCustomer(request).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                status.setValue(response.isSuccessful());
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                status.setValue(false);
            }
        });
        return status;
    }

    // New Verification Logic for Login
    public void verifyCustomerBackend(String email, Callback<ResponseBody> callback) {
        apiService.getCustomerByEmail(email).enqueue(callback);
    }
}