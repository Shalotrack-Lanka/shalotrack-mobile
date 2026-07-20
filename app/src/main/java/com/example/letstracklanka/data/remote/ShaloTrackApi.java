package com.example.letstracklanka.data.remote;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ShaloTrackApi {

    @GET("api/CurrentLocations/device/{deviceId}")
    Call<ResponseBody> getCurrentLocation(@Path("deviceId") String deviceId);

    @GET("api/DeviceStatus/device/{deviceId}")
    Call<ResponseBody> getDeviceStatus(@Path("deviceId") String deviceId);

    @GET("api/CurrentLocations/vehicle/{vehicleId}")
    Call<ResponseBody> getVehicleLocation(@Path("vehicleId") String vehicleId);

    @GET("api/DeviceStatus/vehicle/{vehicleId}")
    Call<ResponseBody> getVehicleStatus(@Path("vehicleId") String vehicleId);

    @GET("api/CurrentLocations")
    Call<ResponseBody> getAllCurrentLocations();

    // NEW -- trail history for the "moving blue line."
    // vehicleId is REQUIRED server-side now (see GpsTrackingService fix); omitting it
    // will 400. from/to are ISO-8601 UTC, e.g. "2026-07-11T00:00:00Z". pageSize capped
    // at 500 server-side regardless of what's requested here.
    @GET("api/GpsTracking")
    Call<ResponseBody> getTrackingHistory(
            @Query("vehicleId") String vehicleId,
            @Query("from") String fromIso,
            @Query("to") String toIso,
            @Query("pageSize") int pageSize
    );

    // NEW -- trip/stop summary report for the History screen.
    @GET("api/GpsTracking/trips")
    Call<ResponseBody> getTripsSummary(
            @Query("vehicleId") String vehicleId,
            @Query("from") String fromIso,
            @Query("to") String toIso
    );
}