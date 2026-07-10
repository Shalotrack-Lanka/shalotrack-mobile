package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
public class StatusResponse {
    @SerializedName("speed")
    private double speed;

    @SerializedName("accStatus")
    private boolean accStatus;

    // Getters
    public double getSpeed() { return speed; }
    public boolean isAccStatus() { return accStatus; }
}