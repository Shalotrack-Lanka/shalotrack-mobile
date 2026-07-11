package com.example.letstracklanka.data.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.example.letstracklanka.data.model.CustomerRequest;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {
    private final ApiService apiService = ApiClient.getClient().create(ApiService.class);

    public MutableLiveData<Boolean> registerCustomer(CustomerRequest request) {
        MutableLiveData<Boolean> status = new MutableLiveData<>();

        apiService.createCustomer(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                status.setValue(response.isSuccessful());
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                status.setValue(false);
            }
        });
        return status;
    }

    public void verifyCustomerBackend(String email, Callback<ResponseBody> callback) {
        apiService.getCustomerByEmail(email).enqueue(callback);
    }
}
