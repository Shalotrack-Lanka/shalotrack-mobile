package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * NEW -- report generation feature. Mirrors AlertReportResponseDto exactly.
 * Backs GET api/Alerts/report, distinct from the plain alert feed
 * (AlertResponse / GET api/Alerts) used by AlertsActivity's notification tab.
 */
@SuppressWarnings("unused")
public class AlertReportResponse {

    @SerializedName(value = "vehicleId", alternate = {"VehicleId"})
    private String vehicleId;

    @SerializedName(value = "totalCount", alternate = {"TotalCount"})
    private int totalCount;

    @SerializedName(value = "countsByType", alternate = {"CountsByType"})
    private Map<String, Integer> countsByType;

    @SerializedName(value = "alerts", alternate = {"Alerts"})
    private List<AlertResponse> alerts;

    public String getVehicleId() { return vehicleId; }
    public int getTotalCount() { return totalCount; }
    public Map<String, Integer> getCountsByType() { return countsByType; }
    public List<AlertResponse> getAlerts() { return alerts; }
}