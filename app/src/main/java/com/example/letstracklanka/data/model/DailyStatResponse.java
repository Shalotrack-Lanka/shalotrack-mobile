package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

public class DailyStatResponse {
    @SerializedName(value = "date", alternate = "Date")
    private String date;

    @SerializedName(value = "distanceKm", alternate = "DistanceKm")
    private double distanceKm;

    @SerializedName(value = "averageSpeed", alternate = "AverageSpeed")
    private double averageSpeed;

    @SerializedName(value = "maxSpeed", alternate = "MaxSpeed")
    private double maxSpeed;

    @SerializedName(value = "tripCount", alternate = "TripCount")
    private int tripCount;

    @SerializedName(value = "stopCount", alternate = "StopCount")
    private int stopCount;

    @SerializedName(value = "ignitionOnMinutes", alternate = "IgnitionOnMinutes")
    private double ignitionOnMinutes;

    public String getDate() { return date; }
    public double getDistanceKm() { return distanceKm; }
    public double getAverageSpeed() { return averageSpeed; }
    public double getMaxSpeed() { return maxSpeed; }
    public int getTripCount() { return tripCount; }
    public int getStopCount() { return stopCount; }
    public double getIgnitionOnMinutes() { return ignitionOnMinutes; }
}