package com.example.letstracklanka.data.remote;

import com.example.letstracklanka.data.model.CreateDeviceAssignmentRequest;
import com.example.letstracklanka.data.model.CreateVehicleRequest;
import com.example.letstracklanka.data.model.CustomerRequest;
import com.example.letstracklanka.data.model.VehicleResponse;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    @POST("api/Customers")
    Call<ResponseBody> createCustomer(@Body CustomerRequest request);

    // NEW: used by HomeActivity now. Resolves the caller's own profile from their
    // token -- works for any authenticated customer, not just staff.
    @GET("api/Customers/me")
    Call<ResponseBody> getMyProfile();

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

    // NEW — customer-safe single-device lookup by IMEI, used for device linking.
    // Replaces the staff-only getGpsDevices() full-list approach.
    @GET("api/GpsDevices/lookup/{imei}")
    Call<ResponseBody> lookupDeviceByImei(@Path("imei") String imei);

    // NEW — Alerts (Stage 2: real data for AlertsActivity)
    @GET("api/Alerts")
    Call<ResponseBody> getMyAlerts(@Query("page") int page, @Query("pageSize") int pageSize);

    @PATCH("api/Alerts/{alertId}/read")
    Call<ResponseBody> markAlertAsRead(@Path("alertId") long alertId);
}