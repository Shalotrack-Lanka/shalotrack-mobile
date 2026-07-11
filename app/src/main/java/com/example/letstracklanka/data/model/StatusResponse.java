package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
public class StatusResponse {
    @SerializedName("speed")
    private double speed;

    @SerializedName("Speed")
    private double speedUpper;

    @SerializedName("accStatus")
    private boolean accStatus;

    @SerializedName("IgnitionStatus")
    private boolean ignitionStatus;

    public double getSpeed() { 
        return speedUpper != 0 ? speedUpper : speed; 
    }
    
    public boolean isAccStatus() { 
        return accStatus || ignitionStatus; 
    }
}
