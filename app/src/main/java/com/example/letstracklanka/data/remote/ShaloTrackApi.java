package com.example.letstracklanka.data.remote;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface ShaloTrackApi {

    // FIX: all methods now return Call<ResponseBody> instead of typed
    // Call<LocationResponse>/Call<StatusResponse>. The real API wraps every response in
    // an envelope: {"success":true,"data":{...},...}. Deserializing straight into the
    // typed model (as before) silently produced an object with every field null, because
    // Gson doesn't know to reach into "data" on its own. Returning ResponseBody lets the
    // caller manually unwrap the envelope (see HomeActivity.extractLocation()) before
    // mapping into the real model — same pattern already used for getAllCurrentLocations()
    // below, which was already correct.

    @GET("api/CurrentLocations/device/{deviceId}")
    Call<ResponseBody> getCurrentLocation(@Path("deviceId") String deviceId);

    @GET("api/DeviceStatus/device/{deviceId}")
    Call<ResponseBody> getDeviceStatus(@Path("deviceId") String deviceId);

    @GET("api/CurrentLocations/vehicle/{vehicleId}")
    Call<ResponseBody> getVehicleLocation(@Path("vehicleId") String vehicleId);

    @GET("api/DeviceStatus/vehicle/{vehicleId}")
    Call<ResponseBody> getVehicleStatus(@Path("vehicleId") String vehicleId);

    // Staff-only on the server (Admin/Dealer role). A regular customer token will get a
    // 403 here — that's correct and expected. Kept for any staff-facing screen; do not
    // use this for a customer's own tracking view (use getVehicleLocation instead).
    @GET("api/CurrentLocations")
    Call<ResponseBody> getAllCurrentLocations();
}