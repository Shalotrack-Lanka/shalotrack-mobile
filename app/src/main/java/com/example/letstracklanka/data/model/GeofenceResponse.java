package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

public class GeofenceResponse {
    @SerializedName("geofenceId")
    private String geofenceId;

    @SerializedName("vehicleId")
    private String vehicleId;

    @SerializedName("vehicleNumber")
    private String vehicleNumber;

    @SerializedName("name")
    private String name;

    @SerializedName("latitude")
    private double latitude;

    @SerializedName("longitude")
    private double longitude;

    @SerializedName("radiusMeters")
    private int radiusMeters;

    @SerializedName("alertOnEnter")
    private boolean alertOnEnter;

    @SerializedName("alertOnExit")
    private boolean alertOnExit;

    @SerializedName("isActive")
    private boolean isActive;

    @SerializedName("createdAt")
    private String createdAt;

    // False when this geofence is visible only via an accepted vehicle
    // share, not because this account owns it -- used to hide edit/
    // delete controls for a shared vehicle's geofences.
    @SerializedName("isOwner")
    private boolean isOwner;

    public String getGeofenceId() { return geofenceId; }
    public String getVehicleId() { return vehicleId; }
    public String getVehicleNumber() { return vehicleNumber; }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public int getRadiusMeters() { return radiusMeters; }
    public boolean isAlertOnEnter() { return alertOnEnter; }
    public boolean isAlertOnExit() { return alertOnExit; }
    public boolean isActive() { return isActive; }
    public String getCreatedAt() { return createdAt; }
    public boolean isOwner() { return isOwner; }
}