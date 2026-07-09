package com.example.letstracklanka;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("api/Customers")
    Call<ResponseBody> createCustomer(@Body CustomerRequest request);
}