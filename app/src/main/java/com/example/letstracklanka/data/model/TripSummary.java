package com.example.letstracklanka.data.model;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
public class TripSummary {

    @SerializedName(value = "startTime", alternate = {"StartTime"})
    private String startTime;

    @SerializedName(value = "endTime", alternate = {"EndTime"})
    private String endTime;

    @SerializedName(value = "startLatitude", alternate = {"StartLatitude"})
    private JsonElement startLatitude;

    @SerializedName(value = "startLongitude", alternate = {"StartLongitude"})
    private JsonElement startLongitude;

    @SerializedName(value = "endLatitude", alternate = {"EndLatitude"})
    private JsonElement endLatitude;

    @SerializedName(value = "endLongitude", alternate = {"EndLongitude"})
    private JsonElement endLongitude;

    @SerializedName(value = "durationMinutes", alternate = {"DurationMinutes"})
    private JsonElement durationMinutes;

    @SerializedName(value = "distanceKm", alternate = {"DistanceKm"})
    private JsonElement distanceKm;

    @SerializedName(value = "inProgress", alternate = {"InProgress"})
    private Boolean inProgress;

    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public double getStartLatitude() { return parseToDouble(startLatitude); }
    public double getStartLongitude() { return parseToDouble(startLongitude); }
    public double getEndLatitude() { return parseToDouble(endLatitude); }
    public double getEndLongitude() { return parseToDouble(endLongitude); }
    public double getDurationMinutes() { return parseToDouble(durationMinutes); }
    public double getDistanceKm() { return parseToDouble(distanceKm); }
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