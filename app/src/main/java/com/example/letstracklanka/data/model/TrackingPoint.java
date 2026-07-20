package com.example.letstracklanka.data.model;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/**
 * One breadcrumb point from GET /api/GpsTracking.
 * Uses the same "alternate" defense as LocationResponse — the API serializes with
 * camelCase (System.Text.Json default), so both casings are accepted here to avoid
 * repeating tonight's silent-zero bug.
 */
@SuppressWarnings("unused")
public class TrackingPoint {

    @SerializedName(value = "VehicleId", alternate = {"vehicleId"})
    private String vehicleId;

    @SerializedName(value = "Latitude", alternate = {"latitude"})
    private JsonElement latitude;

    @SerializedName(value = "Longitude", alternate = {"longitude"})
    private JsonElement longitude;

    @SerializedName(value = "Speed", alternate = {"speed"})
    private JsonElement speed;

    @SerializedName(value = "EventTime", alternate = {"eventTime"})
    private String eventTime;   // ISO-8601 string; kept raw, only used for ordering/debug

    public String getVehicleId() { return vehicleId; }
    public String getEventTime() { return eventTime; }

    public double getLatitude() { return parseToDouble(latitude); }
    public double getLongitude() { return parseToDouble(longitude); }
    public double getSpeed() { return parseToDouble(speed); }

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