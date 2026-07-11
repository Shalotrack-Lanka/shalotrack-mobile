package com.example.letstracklanka.data.model;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/**
 * Robust model for CurrentLocations.
 *
 * FIX: the real API returns camelCase JSON keys (latitude, longitude, speed,
 * ignitionStatus) but this model was only listening for PascalCase (Latitude,
 * Longitude, Speed, IgnitionStatus). Gson is case-sensitive, so every one of
 * those fields silently parsed to null -> 0, meaning the map marker never
 * appeared even though the API was returning correct, real coordinates.
 *
 * Using @SerializedName's "alternate" list so both casings are accepted,
 * the same defensive pattern already used for vehicleId below.
 */
@SuppressWarnings("unused")
public class LocationResponse {

    @SerializedName(value = "VehicleId", alternate = {"vehicleId"})
    private String vehicleId;

    @SerializedName(value = "Latitude", alternate = {"latitude"})
    private JsonElement latitude;

    @SerializedName(value = "Longitude", alternate = {"longitude"})
    private JsonElement longitude;

    @SerializedName(value = "Speed", alternate = {"speed"})
    private JsonElement speed;

    @SerializedName(value = "IgnitionStatus", alternate = {"ignitionStatus"})
    private JsonElement ignitionStatus;

    public String getVehicleId() {
        return vehicleId;
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