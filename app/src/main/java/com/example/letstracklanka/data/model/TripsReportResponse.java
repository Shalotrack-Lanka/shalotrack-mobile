package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

@SuppressWarnings("unused")
public class TripsReportResponse {

    @SerializedName(value = "tripCount", alternate = {"TripCount"})
    private int tripCount;

    @SerializedName(value = "stopCount", alternate = {"StopCount"})
    private int stopCount;

    @SerializedName(value = "trips", alternate = {"Trips"})
    private List<TripSummary> trips;

    public int getTripCount() { return tripCount; }
    public int getStopCount() { return stopCount; }
    public List<TripSummary> getTrips() { return trips; }
}