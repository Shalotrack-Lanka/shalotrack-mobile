package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Payload for POST /api/DeviceAssignments/assign
 * Matches the camelCase naming convention from your API documentation.
 */
public class CreateDeviceAssignmentRequest {
    @SerializedName("vehicleId")
    private final String vehicleId;
    
    @SerializedName("deviceId")
    private final String deviceId;

    public CreateDeviceAssignmentRequest(String vehicleId, String deviceId) {
        this.vehicleId = vehicleId;
        this.deviceId = deviceId;
    }
}
