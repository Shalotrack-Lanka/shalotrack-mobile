package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class VehicleStatsResponse {
    @SerializedName(value = "vehicleId", alternate = "VehicleId")
    private String vehicleId;

    @SerializedName(value = "period", alternate = "Period")
    private String period;

    @SerializedName(value = "periodFrom", alternate = "PeriodFrom")
    private String periodFrom;

    @SerializedName(value = "periodTo", alternate = "PeriodTo")
    private String periodTo;

    @SerializedName(value = "totalDistanceKm", alternate = "TotalDistanceKm")
    private double totalDistanceKm;

    @SerializedName(value = "totalTripCount", alternate = "TotalTripCount")
    private int totalTripCount;

    @SerializedName(value = "totalStopCount", alternate = "TotalStopCount")
    private int totalStopCount;

    @SerializedName(value = "totalIdleMinutes", alternate = "TotalIdleMinutes")
    private double totalIdleMinutes;

    @SerializedName(value = "totalDrivingMinutes", alternate = "TotalDrivingMinutes")
    private double totalDrivingMinutes;

    @SerializedName(value = "totalIgnitionOnMinutes", alternate = "TotalIgnitionOnMinutes")
    private double totalIgnitionOnMinutes;

    @SerializedName(value = "averageSpeed", alternate = "AverageSpeed")
    private double averageSpeed;

    @SerializedName(value = "maxSpeed", alternate = "MaxSpeed")
    private double maxSpeed;

    @SerializedName(value = "overspeedIncidentCount", alternate = "OverspeedIncidentCount")
    private int overspeedIncidentCount;

    @SerializedName(value = "dailyBreakdown", alternate = "DailyBreakdown")
    private List<DailyStatResponse> dailyBreakdown;

    public String getVehicleId() { return vehicleId; }
    public String getPeriod() { return period; }
    public String getPeriodFrom() { return periodFrom; }
    public String getPeriodTo() { return periodTo; }
    public double getTotalDistanceKm() { return totalDistanceKm; }
    public int getTotalTripCount() { return totalTripCount; }
    public int getTotalStopCount() { return totalStopCount; }
    public double getTotalIdleMinutes() { return totalIdleMinutes; }
    public double getTotalDrivingMinutes() { return totalDrivingMinutes; }
    public double getTotalIgnitionOnMinutes() { return totalIgnitionOnMinutes; }
    public double getAverageSpeed() { return averageSpeed; }
    public double getMaxSpeed() { return maxSpeed; }
    public int getOverspeedIncidentCount() { return overspeedIncidentCount; }
    public List<DailyStatResponse> getDailyBreakdown() { return dailyBreakdown; }
}