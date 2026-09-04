package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

public class CreateGeofenceRequest {
    @SerializedName("vehicleId")
    private final String vehicleId;

    @SerializedName("name")
    private final String name;

    @SerializedName("latitude")
    private final double latitude;

    @SerializedName("longitude")
    private final double longitude;

    @SerializedName("radiusMeters")
    private final int radiusMeters;

    @SerializedName("alertOnEnter")
    private final Boolean alertOnEnter;

    @SerializedName("alertOnExit")
    private final Boolean alertOnExit;

    public CreateGeofenceRequest(String vehicleId, String name, double latitude, double longitude,
                                 int radiusMeters, Boolean alertOnEnter, Boolean alertOnExit) {
        this.vehicleId = vehicleId;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters;
        this.alertOnEnter = alertOnEnter;
        this.alertOnExit = alertOnExit;
    }
}