package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
public class AlertResponse {

    @SerializedName(value = "alertId", alternate = {"AlertId"})
    private long alertId;

    @SerializedName(value = "vehicleId", alternate = {"VehicleId"})
    private String vehicleId;

    @SerializedName(value = "vehicleNumber", alternate = {"VehicleNumber"})
    private String vehicleNumber;

    @SerializedName(value = "alertType", alternate = {"AlertType"})
    private String alertType;

    @SerializedName(value = "message", alternate = {"Message"})
    private String message;

    @SerializedName(value = "triggeredAt", alternate = {"TriggeredAt"})
    private String triggeredAt;

    @SerializedName(value = "isRead", alternate = {"IsRead"})
    private boolean isRead;

    public long getAlertId() { return alertId; }
    public String getVehicleId() { return vehicleId; }
    public String getVehicleNumber() { return vehicleNumber; }
    public String getAlertType() { return alertType; }
    public String getMessage() { return message; }
    public String getTriggeredAt() { return triggeredAt; }
    public boolean isRead() { return isRead; }
}