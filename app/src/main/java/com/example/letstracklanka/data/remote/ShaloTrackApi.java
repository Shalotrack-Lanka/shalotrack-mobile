package com.example.letstracklanka.data.remote;

import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.model.StatusResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface ShaloTrackApi {

    // Device ID එක දීලා දැනට තියෙන ලොකේෂන් එක ගැනීම
    @GET("api/CurrentLocations/device/{deviceId}")
    Call<LocationResponse> getCurrentLocation(@Path("deviceId") String deviceId);

    // Device ID එක දීලා Speed, ACC Status වගේ දේවල් ගැනීම
    @GET("api/DeviceStatus/device/{deviceId}")
    Call<StatusResponse> getDeviceStatus(@Path("deviceId") String deviceId);
}