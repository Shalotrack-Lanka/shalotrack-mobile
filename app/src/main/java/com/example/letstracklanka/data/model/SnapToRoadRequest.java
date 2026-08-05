package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class SnapToRoadRequest {

    @SerializedName(value = "points", alternate = {"Points"})
    public List<Point> points = new ArrayList<>();

    public static class Point {
        @SerializedName(value = "latitude", alternate = {"Latitude"})
        public double latitude;

        @SerializedName(value = "longitude", alternate = {"Longitude"})
        public double longitude;

        public Point(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}