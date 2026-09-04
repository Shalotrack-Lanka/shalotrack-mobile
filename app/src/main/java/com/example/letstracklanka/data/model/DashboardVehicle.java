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

    // NEW -- needed to show a type-specific icon (car/SUV/van/truck/bike/
    // tuk) instead of one generic icon for every vehicle.
    @SerializedName("vehicleType")
    private String vehicleType;

    public String getVehicleType() { return vehicleType; }

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

    // NEW -- Vehicle Sharing. isShared is false/absent for vehicles the
    // customer actually owns, true for entries merged in from an
    // Accepted share. Used to hide owner-only actions (delete, edit,
    // Immobilize) for shared vehicles -- "full access" for a shared
    // viewer means live tracking and alerts, not the ability to modify
    // or remove someone else's vehicle.
    @SerializedName("isShared")
    private Boolean isShared;

    @SerializedName("ownerName")
    private String ownerName;

    public boolean isShared() { return isShared != null && isShared; }
    public String getOwnerName() { return ownerName; }

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