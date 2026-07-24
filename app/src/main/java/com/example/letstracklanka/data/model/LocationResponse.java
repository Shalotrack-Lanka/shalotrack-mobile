package com.example.letstracklanka.data.model;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/**
 * Robust model for CurrentLocations.
 *
 * Handles the camelCase-vs-PascalCase mismatch (see earlier fix) via Gson's
 * "alternate" names. Added `heading` so the car marker can be rotated to face
 * the direction of travel.
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

    @SerializedName(value = "Heading", alternate = {"heading"})
    private JsonElement heading;

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

    /** Compass bearing in degrees (0-360), direction the vehicle is/was heading. */
    public float getHeading() {
        return (float) parseToDouble(heading);
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