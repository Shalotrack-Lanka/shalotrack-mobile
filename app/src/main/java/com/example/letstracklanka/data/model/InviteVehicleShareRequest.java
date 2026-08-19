package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

public class InviteVehicleShareRequest {
    @SerializedName("vehicleId")
    private final String vehicleId;

    @SerializedName("phoneNumber")
    private final String phoneNumber;

    public InviteVehicleShareRequest(String vehicleId, String phoneNumber) {
        this.vehicleId = vehicleId;
        this.phoneNumber = phoneNumber;
    }
}