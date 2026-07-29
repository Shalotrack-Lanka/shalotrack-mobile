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

    // NEW -- was already present in the underlying database column and the
    // API's SQL SELECT this whole night, just never actually exposed to the
    // Android model. Needed to check data freshness (e.g. distinguishing a
    // genuinely moving vehicle from stale "Moving" data from 20 minutes ago).
    @SerializedName(value = "LastUpdate", alternate = {"lastUpdate"})
    private String lastUpdate;

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

    /** Raw ISO-8601 UTC timestamp string, e.g. "2026-07-28T18:04:52Z". Null if not present. */
    public String getLastUpdate() {
        return lastUpdate;
    }

    /**
     * Minutes since this location was last actually reported, comparing
     * against real current UTC time. Returns Long.MAX_VALUE if the timestamp
     * is missing/unparseable, so a freshness check safely treats it as "not
     * fresh" rather than crashing or defaulting to "fresh".
     */
    public long getMinutesSinceUpdate() {
        if (lastUpdate == null || lastUpdate.trim().isEmpty()) return Long.MAX_VALUE;
        try {
            java.time.Instant then = java.time.Instant.parse(lastUpdate);
            java.time.Instant now = java.time.Instant.now();
            return java.time.Duration.between(then, now).toMinutes();
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
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