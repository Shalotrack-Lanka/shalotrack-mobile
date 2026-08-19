package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

public class RespondToVehicleShareRequest {
    @SerializedName("accept")
    private final boolean accept;

    public RespondToVehicleShareRequest(boolean accept) {
        this.accept = accept;
    }
}