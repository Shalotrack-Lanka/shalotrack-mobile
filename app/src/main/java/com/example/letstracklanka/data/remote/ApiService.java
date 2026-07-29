package com.example.letstracklanka.data.remote;

import com.example.letstracklanka.data.model.CreateDeviceAssignmentRequest;
import com.example.letstracklanka.data.model.CreateVehicleRequest;
import com.example.letstracklanka.data.model.CustomerRequest;
import com.example.letstracklanka.data.model.RegisterFcmTokenRequest;
import com.example.letstracklanka.data.model.UpdateCustomerRequest;
import com.example.letstracklanka.data.model.VehicleResponse;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    @POST("api/Customers")
    Call<ResponseBody> createCustomer(@Body CustomerRequest request);

    @GET("api/Customers/me")
    Call<ResponseBody> getMyProfile();

    @PUT("api/Customers/{customerId}")
    Call<ResponseBody> updateCustomer(@Path("customerId") String customerId, @Body UpdateCustomerRequest request);

    @GET("api/Vehicles/customer/{customerId}")
    Call<ResponseBody> getVehiclesByCustomer(@Path("customerId") String customerId);

    @POST("api/Vehicles")
    Call<VehicleResponse> createVehicle(@Body CreateVehicleRequest request);

    // NOTE: still points at the staff-only device list. A regular customer token will
    // get 403 here. Known limitation, deliberately not fixed tonight.
    @GET("api/GpsDevices")
    Call<ResponseBody> getGpsDevices();

    @POST("api/DeviceAssignments/assign")
    Call<ResponseBody> assignDevice(@Body CreateDeviceAssignmentRequest request);

    @GET("api/Customers/{customerId}/dashboard")
    Call<ResponseBody> getCustomerDashboard(@Path("customerId") String customerId);

    @GET("api/GpsDevices/lookup/{imei}")
    Call<ResponseBody> lookupDeviceByImei(@Path("imei") String imei);

    @GET("api/Alerts")
    Call<ResponseBody> getMyAlerts(@Query("page") int page, @Query("pageSize") int pageSize);

    @PATCH("api/Alerts/{alertId}/read")
    Call<ResponseBody> markAlertAsRead(@Path("alertId") long alertId);

    // NEW — was built on the API side during Alerts Stage 1 but never actually
    // wired on the mobile side until now.
    @POST("api/Alerts/register-token")
    Call<ResponseBody> registerFcmToken(@Body RegisterFcmTokenRequest request);

    // NEW — soft-deletes a vehicle (sets IsActive=false, frees its device's
    // IMEI for reassignment). See VehicleService.DeleteAsync() on the API side.
    @DELETE("api/Vehicles/{vehicleId}")
    Call<ResponseBody> deleteVehicle(@Path("vehicleId") String vehicleId);
}