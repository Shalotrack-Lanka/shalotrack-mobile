package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Payload for POST /api/Vehicles
 * Matches your SQL structure EXACTLY (PascalCase).
 */
public class CreateVehicleRequest {
    @SerializedName("CustomerId")
    private final String customerId;
    
    @SerializedName("VehicleNumber")
    private final String vehicleNumber;

    @SerializedName("ChassisNumber")
    private final String chassisNumber;

    @SerializedName("EngineNumber")
    private final String engineNumber;
    
    @SerializedName("Make")
    private final String make;
    
    @SerializedName("Model")
    private final String model;
    
    @SerializedName("Year")
    private final int year;

    @SerializedName("Color")
    private final String color;

    @SerializedName("VehicleType")
    private final String vehicleType;

    @SerializedName("FuelType")
    private final String fuelType;

    public CreateVehicleRequest(String customerId, String vehicleNumber, String chassisNumber, 
                               String engineNumber, String make, String model, int year, 
                               String color, String vehicleType, String fuelType) {
        this.customerId = customerId;
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
