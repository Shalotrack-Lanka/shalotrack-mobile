package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

public class UpdateVehicleRequest {
    @SerializedName("vehicleNumber")
    private final String vehicleNumber;

    @SerializedName("chassisNumber")
    private final String chassisNumber;

    @SerializedName("engineNumber")
    private final String engineNumber;

    @SerializedName("make")
    private final String make;

    @SerializedName("model")
    private final String model;

    @SerializedName("year")
    private final int year;

    @SerializedName("color")
    private final String color;

    @SerializedName("vehicleType")
    private final String vehicleType;

    @SerializedName("fuelType")
    private final String fuelType;

    public UpdateVehicleRequest(String vehicleNumber, String chassisNumber, String engineNumber,
                                String make, String model, int year,
                                String color, String vehicleType, String fuelType) {
        this.vehicleNumber = vehicleNumber;
        this.chassisNumber = chassisNumber;
        this.engineNumber = engineNumber;
        this.make = make;
        this.model = model;
        this.year = year;
        this.color = color;
        this.vehicleType = vehicleType;
        this.fuelType = fuelType;
    }
}