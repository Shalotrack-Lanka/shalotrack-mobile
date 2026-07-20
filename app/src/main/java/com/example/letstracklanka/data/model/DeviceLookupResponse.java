package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

/** Result of GET /api/GpsDevices/lookup/{imei} -- used only during device linking. */
@SuppressWarnings("unused")
public class DeviceLookupResponse {

    @SerializedName(value = "deviceId", alternate = {"DeviceId"})
    private String deviceId;

    @SerializedName(value = "imeiNumber", alternate = {"ImeiNumber"})
    private String imeiNumber;

    public String getDeviceId() {
        return deviceId;
    }

    public String getImeiNumber() {
        return imeiNumber;
    }
}