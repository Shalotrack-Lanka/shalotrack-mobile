package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Robust model for GPS Device response.
 * Handles different casing for DeviceId and IMEI from the backend.
 */
@SuppressWarnings("unused")
public class GpsDeviceResponse {
    @SerializedName("deviceId")
    private String deviceId;

    @SerializedName("DeviceId")
    private String deviceIdUpper;
    
    @SerializedName("imeiNumber")
    private String imeiNumber;

    @SerializedName("ImeiNumber")
    private String imeiNumberUpper;
    
    @SerializedName("IMEINumber")
    private String imeiNumberAllUpper;

    public String getDeviceId() { 
        return deviceIdUpper != null ? deviceIdUpper : deviceId; 
    }
    
    public String getImeiNumber() { 
        if (imeiNumberUpper != null) return imeiNumberUpper;
        if (imeiNumberAllUpper != null) return imeiNumberAllUpper;
        return imeiNumber;
    }
}
