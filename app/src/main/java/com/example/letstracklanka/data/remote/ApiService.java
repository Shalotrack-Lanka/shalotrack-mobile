package com.example.letstracklanka.data.remote;

import com.example.letstracklanka.data.model.CustomerRequest;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface ApiService {
    @POST("api/Customers")
    Call<ResponseBody> createCustomer(@Body CustomerRequest request);

    @GET("api/Customers/{email}") // Ensure this matches your API route exactly
    Call<ResponseBody> getCustomerByEmail(@Path("email") String email);
}