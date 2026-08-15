package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

public class CreateSavedPlaceRequest {
    @SerializedName("name")
    private final String name;

    @SerializedName("latitude")
    private final double latitude;

    @SerializedName("longitude")
    private final double longitude;

    public CreateSavedPlaceRequest(String name, double latitude, double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}