package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Combined model for the Customer Dashboard response.
 */
@SuppressWarnings("unused")
public class DashboardResponse {
    
    @SerializedName("VehicleId")
    private String vehicleId;
    
    @SerializedName("vehicleId")
    private String vehicleIdLower;

    @SerializedName("Make")
    private String make;

    @SerializedName("Model")
    private String model;

    @SerializedName("VehicleNumber")
    private String vehicleNumber;

    @SerializedName("Latitude")
    private double latitude;

    @SerializedName("Longitude")
    private double longitude;

    @SerializedName("Speed")
    private double speed;

    @SerializedName("IgnitionStatus")
    private boolean ignitionStatus;

    public String getVehicleId() {
        return vehicleId != null ? vehicleId : vehicleIdLower;
    }

    public String getDisplayName() {
        return (make != null ? make : "") + " " + (model != null ? model : "");
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getSpeed() { return speed; }
    public boolean isIgnitionOn() { return ignitionStatus; }
}
