package com.example.letstracklanka.data.model;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/**
 * Robust model for CurrentLocations.
 * Based on your SQL: 'Latitude', 'Longitude', 'Speed', 'IgnitionStatus' are stored as Strings/Text.
 */
@SuppressWarnings("unused")
public class LocationResponse {
    
    @SerializedName("VehicleId")
    private String vehicleId;

    @SerializedName("vehicleId")
    private String vehicleIdLower;

    @SerializedName("Latitude")
    private JsonElement latitude;

    @SerializedName("Longitude")
    private JsonElement longitude;

    @SerializedName("Speed")
    private JsonElement speed;

    @SerializedName("IgnitionStatus")
    private JsonElement ignitionStatus;

    public String getVehicleId() {
        return vehicleId != null ? vehicleId : vehicleIdLower;
    }

    public double getLatitude() { 
        return parseToDouble(latitude);
    }
    
    public double getLongitude() { 
        return parseToDouble(longitude);
    }

    public double getSpeed() {
        return parseToDouble(speed);
    }

    public boolean isIgnitionOn() {
        if (ignitionStatus == null || ignitionStatus.isJsonNull()) return false;
        try {
            if (ignitionStatus.isJsonPrimitive()) {
                if (ignitionStatus.getAsJsonPrimitive().isBoolean()) return ignitionStatus.getAsBoolean();
                if (ignitionStatus.getAsJsonPrimitive().isString()) {
                    return "true".equalsIgnoreCase(ignitionStatus.getAsString().trim());
                }
            }
        } catch (Exception e) { return false; }
        return false;
    }

    private double parseToDouble(JsonElement element) {
        if (element == null || element.isJsonNull()) return 0;
        try {
            if (element.isJsonPrimitive()) {
                if (element.getAsJsonPrimitive().isNumber()) return element.getAsDouble();
                if (element.getAsJsonPrimitive().isString()) {
                    String val = element.getAsString().trim();
                    return val.isEmpty() ? 0 : Double.parseDouble(val);
                }
            }
        } catch (Exception e) { return 0; }
        return 0;
    }
}
