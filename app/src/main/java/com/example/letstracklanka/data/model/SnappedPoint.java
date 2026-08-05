package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

public class SnappedPoint {

    // Dual @SerializedName mapping, same defensive pattern already used in
    // DashboardVehicle.java/VehicleResponse.java in this project -- the C#
    // DTO's real casing (camelCase by default under ASP.NET Core's
    // System.Text.Json) isn't being assumed here, both are covered.
    @SerializedName(value = "latitude", alternate = {"Latitude"})
    private double latitude;

    @SerializedName(value = "longitude", alternate = {"Longitude"})
    private double longitude;

    // Maps back to which input point this corresponds to -- Google can
    // return fewer snapped points than requested (points far from any
    // known road get dropped), so this is required to realign results
    // with the original input sequence, not assumed to be 1:1 positional.
    @SerializedName(value = "originalIndex", alternate = {"OriginalIndex"})
    private Integer originalIndex;

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Integer getOriginalIndex() { return originalIndex; }
}