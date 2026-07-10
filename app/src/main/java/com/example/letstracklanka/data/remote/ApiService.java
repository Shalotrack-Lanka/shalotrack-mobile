package com.example.letstracklanka.data.remote;

import com.example.letstracklanka.data.model.CustomerRequest;
import com.example.letstracklanka.data.model.CustomerResponse;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {
    @POST("api/Customers")
    Call<ResponseBody> createCustomer(@Body CustomerRequest request);

    @GET("api/Customers")
    Call<List<CustomerResponse>> getCustomerByEmail(@Query("email") String email);
}
