package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
public class VehicleResponse {
    @SerializedName("vehicleId")
    private String vehicleId;

    @SerializedName("VehicleId")
    private String vehicleIdUpper;

    @SerializedName("vehicleNumber")
    private String vehicleNumber;

    @SerializedName("VehicleNumber")
    private String vehicleNumberUpper;

    @SerializedName("make")
    private String make;

    @SerializedName("Make")
    private String makeUpper;

    @SerializedName("model")
    private String model;

    @SerializedName("Model")
    private String modelUpper;

    // NEW — for the Vehicle Information screen.
    @SerializedName("hasGpsDevice")
    private Boolean hasGpsDevice;

    @SerializedName("HasGpsDevice")
    private Boolean hasGpsDeviceUpper;

    @SerializedName("imei")
    private String imei;

    @SerializedName("Imei")
    private String imeiUpper;

    public String getVehicleId() {
        return vehicleIdUpper != null ? vehicleIdUpper : vehicleId;
    }

    public String getVehicleNumber() {
        return vehicleNumberUpper != null ? vehicleNumberUpper : vehicleNumber;
    }

    public String getMake() {
        return makeUpper != null ? makeUpper : make;
    }

    public String getModel() {
        return modelUpper != null ? modelUpper : model;
    }

    public boolean hasGpsDevice() {
        Boolean v = hasGpsDeviceUpper != null ? hasGpsDeviceUpper : hasGpsDevice;
        return v != null && v;
    }

    /** Returns the IMEI, or null if no device is currently assigned to this vehicle. */
    public String getImei() {
        return imeiUpper != null ? imeiUpper : imei;
    }
}