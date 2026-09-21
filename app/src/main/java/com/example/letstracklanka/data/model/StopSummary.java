package com.example.letstracklanka.data.model;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/**
 * NEW -- report generation feature (Stop Alert report). Mirrors
 * StopSummaryDto on the API side exactly. This data has always been
 * returned by GET api/GpsTracking/trips (the "stops" array alongside
 * "trips"), but until now nothing on the Android side ever deserialized
 * it -- see the NEW field added to TripsReportResponse.java in this same
 * change.
 */
@SuppressWarnings("unused")
public class StopSummary {

    @SerializedName(value = "startTime", alternate = {"StartTime"})
    private String startTime;

    @SerializedName(value = "endTime", alternate = {"EndTime"})
    private String endTime;

    @SerializedName(value = "latitude", alternate = {"Latitude"})
    private JsonElement latitude;

    @SerializedName(value = "longitude", alternate = {"Longitude"})
    private JsonElement longitude;

    @SerializedName(value = "durationMinutes", alternate = {"DurationMinutes"})
    private JsonElement durationMinutes;

    @SerializedName(value = "inProgress", alternate = {"InProgress"})
    private Boolean inProgress;

    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public double getLatitude() { return parseToDouble(latitude); }
    public double getLongitude() { return parseToDouble(longitude); }
    public double getDurationMinutes() { return parseToDouble(durationMinutes); }
    public boolean isInProgress() { return inProgress != null && inProgress; }

    private double parseToDouble(JsonElement element) {
        if (element == null || element.isJsonNull()) return 0;
        try {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return element.getAsDouble();
            }
        } catch (Exception ignored) { }
        return 0;
    }
}