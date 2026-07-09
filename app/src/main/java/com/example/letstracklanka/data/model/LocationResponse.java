package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

public class LocationResponse {
    @SerializedName("latitude") // Swagger එකේ තියෙන නම
    private double latitude;

    @SerializedName("longitude")
    private double longitude;

    // Getters
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}