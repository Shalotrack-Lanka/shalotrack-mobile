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

    @SerializedName("hasGpsDevice")
    private Boolean hasGpsDevice;

    @SerializedName("HasGpsDevice")
    private Boolean hasGpsDeviceUpper;

    @SerializedName("imei")
    private String imei;

    @SerializedName("Imei")
    private String imeiUpper;

    // NEW -- vehicle fields the backend was already sending, but this
    // model never had a field for. Confirmed directly against the real
    // VehicleResponseDto.cs before adding, not guessed at.
    @SerializedName("chassisNumber")
    private String chassisNumber;
    @SerializedName("ChassisNumber")
    private String chassisNumberUpper;

    @SerializedName("engineNumber")
    private String engineNumber;
    @SerializedName("EngineNumber")
    private String engineNumberUpper;

    @SerializedName("year")
    private Integer year;
    @SerializedName("Year")
    private Integer yearUpper;

    @SerializedName("color")
    private String color;
    @SerializedName("Color")
    private String colorUpper;

    @SerializedName("vehicleType")
    private String vehicleType;
    @SerializedName("VehicleType")
    private String vehicleTypeUpper;

    @SerializedName("fuelType")
    private String fuelType;
    @SerializedName("FuelType")
    private String fuelTypeUpper;

    // NEW -- full GPS device details, for the redesigned Details screen.
    @SerializedName("simNumber")
    private String simNumber;
    @SerializedName("SimNumber")
    private String simNumberUpper;

    @SerializedName("deviceModel")
    private String deviceModel;
    @SerializedName("DeviceModel")
    private String deviceModelUpper;

    @SerializedName("networkProvider")
    private String networkProvider;
    @SerializedName("NetworkProvider")
    private String networkProviderUpper;

    @SerializedName("firmwareVersion")
    private String firmwareVersion;
    @SerializedName("FirmwareVersion")
    private String firmwareVersionUpper;

    @SerializedName("activationStatus")
    private String activationStatus;
    @SerializedName("ActivationStatus")
    private String activationStatusUpper;

    // Dates come through as ISO-8601 strings, matching the same
    // string-then-parse-in-the-UI-layer convention used throughout this
    // app for every other date field.
    @SerializedName("warrantyExpiryDate")
    private String warrantyExpiryDate;
    @SerializedName("WarrantyExpiryDate")
    private String warrantyExpiryDateUpper;

    @SerializedName("installedAt")
    private String installedAt;
    @SerializedName("InstalledAt")
    private String installedAtUpper;

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

    public String getChassisNumber() {
        return chassisNumberUpper != null ? chassisNumberUpper : chassisNumber;
    }

    public String getEngineNumber() {
        return engineNumberUpper != null ? engineNumberUpper : engineNumber;
    }

    public Integer getYear() {
        return yearUpper != null ? yearUpper : year;
    }

    public String getColor() {
        return colorUpper != null ? colorUpper : color;
    }

    public String getVehicleType() {
        return vehicleTypeUpper != null ? vehicleTypeUpper : vehicleType;
    }

    public String getFuelType() {
        return fuelTypeUpper != null ? fuelTypeUpper : fuelType;
    }

    public String getSimNumber() {
        return simNumberUpper != null ? simNumberUpper : simNumber;
    }

    public String getDeviceModel() {
        return deviceModelUpper != null ? deviceModelUpper : deviceModel;
    }

    public String getNetworkProvider() {
        return networkProviderUpper != null ? networkProviderUpper : networkProvider;
    }

    public String getFirmwareVersion() {
        return firmwareVersionUpper != null ? firmwareVersionUpper : firmwareVersion;
    }

    public String getActivationStatus() {
        return activationStatusUpper != null ? activationStatusUpper : activationStatus;
    }

    public String getWarrantyExpiryDate() {
        return warrantyExpiryDateUpper != null ? warrantyExpiryDateUpper : warrantyExpiryDate;
    }

    public String getInstalledAt() {
        return installedAtUpper != null ? installedAtUpper : installedAt;
    }
}