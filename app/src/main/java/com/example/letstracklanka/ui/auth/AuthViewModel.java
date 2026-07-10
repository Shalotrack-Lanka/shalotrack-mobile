package com.example.letstracklanka.ui.auth;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.letstracklanka.data.model.CustomerRequest;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.google.gson.Gson;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthViewModel extends ViewModel {

    private final ApiService apiService = ApiClient.getClient().create(ApiService.class);

    public LiveData<String> performRegistration(String name, String email, String phone, String nic, String address) {
        MutableLiveData<String> result = new MutableLiveData<>();

        // Create the Request Object
        CustomerRequest request = new CustomerRequest(name, email, phone, nic, address);
        
        // LOG THE JSON SO YOU CAN CHECK IT MANUALLY IN POSTMAN IF IT FAILS
        String jsonPayload = new Gson().toJson(request);
        Log.d("API_TRACKING", "POSTing to /api/Customers: " + jsonPayload);

        apiService.createCustomer(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.d("API_TRACKING", "Registration SUCCESS (200/201)");
                    result.setValue("SUCCESS");
                } else {
                    String errorMsg = "Registration Failed (Code: " + response.code() + ")";
                    try {
                        if (response.errorBody() != null) {
                            String serverError = response.errorBody().string();
                            Log.e("API_TRACKING", "Backend Error Body: " + serverError);
                            errorMsg = "Server Error: " + serverError;
                        }
                    } catch (Exception ignored) {}
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
}
