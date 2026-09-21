package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Matches ShaloTrack_API.DTOs.Complaint.ComplaintResponseDto. category/
 * status are raw ints matching the C# enums exactly — there is no
 * JsonStringEnumConverter configured on the API side, so the ordinal
 * values below MUST stay in lockstep with:
 *   ComplaintCategory: DeviceIssue=0, Billing=1, AppBug=2, Other=3
 *   ComplaintStatus:   WithDealer=0, WithAdmin=1, Resolved=2, Closed=3
 * (Same fragile-but-intentional pattern already used on the admin side's
 * Blade views for this exact same feature — see dealer/complaints.blade.php
 * and admin/complaints/admin_complaints_index.blade.php.)
 */
public class ComplaintResponse {

    @SerializedName(value = "complaintId", alternate = {"ComplaintId"})
    private String complaintId;

    @SerializedName(value = "vehicleId", alternate = {"VehicleId"})
    private String vehicleId;

    @SerializedName(value = "vehicleNumber", alternate = {"VehicleNumber"})
    private String vehicleNumber;

    @SerializedName(value = "make", alternate = {"Make"})
    private String make;

    @SerializedName(value = "model", alternate = {"Model"})
    private String model;

    @SerializedName(value = "category", alternate = {"Category"})
    private int category;

    @SerializedName(value = "description", alternate = {"Description"})
    private String description;

    @SerializedName(value = "status", alternate = {"Status"})
    private int status;

    @SerializedName(value = "dealerId", alternate = {"DealerId"})
    private Integer dealerId;

    @SerializedName(value = "dealerName", alternate = {"DealerName"})
    private String dealerName;

    @SerializedName(value = "createdAt", alternate = {"CreatedAt"})
    private String createdAt;

    @SerializedName(value = "updatedAt", alternate = {"UpdatedAt"})
    private String updatedAt;

    @SerializedName(value = "escalatedAt", alternate = {"EscalatedAt"})
    private String escalatedAt;

    @SerializedName(value = "resolvedAt", alternate = {"ResolvedAt"})
    private String resolvedAt;

    @SerializedName(value = "replies", alternate = {"Replies"})
    private List<ComplaintReplyResponse> replies;

    public String getComplaintId() { return complaintId; }
    public String getVehicleId() { return vehicleId; }
    public String getVehicleNumber() { return vehicleNumber; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public int getCategory() { return category; }
    public String getDescription() { return description; }
    public int getStatus() { return status; }
    public Integer getDealerId() { return dealerId; }
    public String getDealerName() { return dealerName; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public String getEscalatedAt() { return escalatedAt; }
    public String getResolvedAt() { return resolvedAt; }

    public List<ComplaintReplyResponse> getReplies() {
        return replies == null ? new ArrayList<>() : replies;
    }

    public String getVehicleLabel() {
        String name = (safe(make) + " " + safe(model)).trim();
        if (vehicleNumber != null && !vehicleNumber.isEmpty()) {
            return name.isEmpty() ? vehicleNumber : name + " (" + vehicleNumber + ")";
        }
        return name.isEmpty() ? "Vehicle" : name;
    }

    public String getCategoryLabel() {
        switch (category) {
            case 0: return "Device Issue";
            case 1: return "Billing";
            case 2: return "App Bug";
            default: return "Other";
        }
    }

    public String getStatusLabel() {
        switch (status) {
            case 0: return "With Dealer";
            case 1: return "With Admin";
            case 2: return "Resolved";
            case 3: return "Closed";
            default: return "Unknown";
        }
    }

    /** true once a customer can no longer reply (Resolved or Closed). */
    public boolean isClosedForReplies() {
        return status == 2 || status == 3;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}