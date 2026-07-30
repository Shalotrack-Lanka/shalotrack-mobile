package com.example.letstracklanka.data.model;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/**
 * Matches the shape of each item in GET /api/Customers/{id}/dashboard's
 * "vehicles" array -- distinct from VehicleResponse, since this one carries
 * live status (speed/online/ignition) that the plain vehicle model doesn't.
 */
@SuppressWarnings("unused")
public class DashboardVehicle {

    @SerializedName("vehicleId")
    private String vehicleId;

    @SerializedName("vehicleNumber")
    private String vehicleNumber;

    @SerializedName("make")
    private String make;

    @SerializedName("model")
    private String model;

    @SerializedName("deviceId")
    private String deviceId;

    @SerializedName("latitude")
    private JsonElement latitude;

    @SerializedName("longitude")
    private JsonElement longitude;

    @SerializedName("speed")
    private JsonElement speed;

    @SerializedName("online")
    private Boolean online;

    @SerializedName("ignition")
    private Boolean ignition;

    public String getVehicleId() { return vehicleId; }
    public String getVehicleNumber() { return vehicleNumber; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public String getDeviceId() { return deviceId; }
    public boolean hasDevice() { return deviceId != null; }
    public boolean isOnline() { return online != null && online; }
    public boolean isIgnitionOn() { return ignition != null && ignition; }

    public double getSpeed() {
        if (speed == null || speed.isJsonNull()) return 0;
        try { return speed.getAsDouble(); } catch (Exception e) { return 0; }
    }

    public double getLatitude() {
        if (latitude == null || latitude.isJsonNull()) return 0;
        try { return latitude.getAsDouble(); } catch (Exception e) { return 0; }
    }

    public double getLongitude() {
        if (longitude == null || longitude.isJsonNull()) return 0;
        try { return longitude.getAsDouble(); } catch (Exception e) { return 0; }
    }
}