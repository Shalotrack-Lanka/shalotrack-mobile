package com.example.letstracklanka.ui.main;

import com.google.gson.annotations.SerializedName;

/** Mirrors LocationNotificationPayload from the API's SignalR push. */
public class RealtimeLocationPayload {

    @SerializedName("vehicleId")
    private String vehicleId;

    @SerializedName("deviceId")
    private String deviceId;

    @SerializedName("latitude")
    private double latitude;

    @SerializedName("longitude")
    private double longitude;

    @SerializedName("speed")
    private double speed;

    @SerializedName("heading")
    private double heading;

    @SerializedName("ignitionStatus")
    private boolean ignitionStatus;

    @SerializedName("movementStatus")
    private boolean movementStatus;

    @SerializedName("lastUpdate")
    private String lastUpdate;

    public String getVehicleId() { return vehicleId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getSpeed() { return speed; }
    public double getHeading() { return heading; }
    public boolean isIgnitionOn() { return ignitionStatus; }
}