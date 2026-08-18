package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

public class SavedPlaceResponse {
    @SerializedName(value = "placeId", alternate = "PlaceId")
    private String placeId;

    @SerializedName(value = "name", alternate = "Name")
    private String name;

    @SerializedName(value = "latitude", alternate = "Latitude")
    private double latitude;

    @SerializedName(value = "longitude", alternate = "Longitude")
    private double longitude;

    @SerializedName(value = "radiusMeters", alternate = "RadiusMeters")
    private int radiusMeters;

    @SerializedName(value = "visitCount", alternate = "VisitCount")
    private int visitCount;

    @SerializedName(value = "lastVisitedAt", alternate = "LastVisitedAt")
    private String lastVisitedAt;

    @SerializedName(value = "createdAt", alternate = "CreatedAt")
    private String createdAt;

    public String getPlaceId() { return placeId; }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public int getRadiusMeters() { return radiusMeters; }
    public int getVisitCount() { return visitCount; }
    public String getLastVisitedAt() { return lastVisitedAt; }
    public String getCreatedAt() { return createdAt; }
}