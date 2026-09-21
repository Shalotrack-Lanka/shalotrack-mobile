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

    // NEW -- the API has always sent this ("stops" alongside "trips"), but
    // this model had no field for it, so Gson silently dropped it on every
    // parse. Needed now for the Stop Alert report, which reads this array
    // directly rather than deriving stops from trips.
    @SerializedName(value = "stops", alternate = {"Stops"})
    private List<StopSummary> stops;

    public int getTripCount() { return tripCount; }
    public int getStopCount() { return stopCount; }
    public List<TripSummary> getTrips() { return trips; }
    public List<StopSummary> getStops() { return stops; }
}