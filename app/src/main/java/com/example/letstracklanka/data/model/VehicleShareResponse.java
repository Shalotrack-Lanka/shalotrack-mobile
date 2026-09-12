package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

public class VehicleShareResponse {
    @SerializedName(value = "shareId", alternate = "ShareId")
    private String shareId;

    @SerializedName(value = "status", alternate = "Status")
    private String status;

    @SerializedName(value = "invitedAt", alternate = "InvitedAt")
    private String invitedAt;

    @SerializedName(value = "respondedAt", alternate = "RespondedAt")
    private String respondedAt;

    @SerializedName(value = "vehicleId", alternate = "VehicleId")
    private String vehicleId;

    @SerializedName(value = "vehicleNumber", alternate = "VehicleNumber")
    private String vehicleNumber;

    @SerializedName(value = "make", alternate = "Make")
    private String make;

    @SerializedName(value = "model", alternate = "Model")
    private String model;

    @SerializedName(value = "otherPartyCustomerId", alternate = "OtherPartyCustomerId")
    private String otherPartyCustomerId;

    @SerializedName(value = "otherPartyName", alternate = "OtherPartyName")
    private String otherPartyName;

    @SerializedName(value = "otherPartyPhoneNumber", alternate = "OtherPartyPhoneNumber")
    private String otherPartyPhoneNumber;

    public String getShareId() { return shareId; }
    public String getStatus() { return status; }
    public String getInvitedAt() { return invitedAt; }
    public String getRespondedAt() { return respondedAt; }
    public String getVehicleId() { return vehicleId; }
    public String getVehicleNumber() { return vehicleNumber; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public String getOtherPartyCustomerId() { return otherPartyCustomerId; }
    public String getOtherPartyName() { return otherPartyName; }
    public String getOtherPartyPhoneNumber() { return otherPartyPhoneNumber; }
}