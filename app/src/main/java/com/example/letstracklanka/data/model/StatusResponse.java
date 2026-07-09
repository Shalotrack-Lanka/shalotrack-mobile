package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

public class StatusResponse {
    @SerializedName("speed")
    private double speed;

    @SerializedName("accStatus") // Ignition on/off ද කියන එක
    private boolean accStatus;

    // Getters
    public double getSpeed() { return speed; }
    public boolean isAccStatus() { return accStatus; }
}