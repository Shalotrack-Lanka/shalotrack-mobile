package com.example.letstracklanka.data.remote;

import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.model.StatusResponse;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface ShaloTrackApi {

    @GET("api/CurrentLocations/device/{deviceId}")
    Call<LocationResponse> getCurrentLocation(@Path("deviceId") String deviceId);

    @GET("api/DeviceStatus/device/{deviceId}")
    Call<StatusResponse> getDeviceStatus(@Path("deviceId") String deviceId);

    @GET("api/CurrentLocations/vehicle/{vehicleId}")
    Call<LocationResponse> getVehicleLocation(@Path("vehicleId") String vehicleId);

    @GET("api/DeviceStatus/vehicle/{vehicleId}")
    Call<StatusResponse> getVehicleStatus(@Path("vehicleId") String vehicleId);

    // Robust endpoint for all locations
    @GET("api/CurrentLocations")
    Call<ResponseBody> getAllCurrentLocations();
}
