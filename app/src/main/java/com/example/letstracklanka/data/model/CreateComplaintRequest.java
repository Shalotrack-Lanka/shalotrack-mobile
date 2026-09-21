package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Body for POST /api/Complaints. category is sent as the raw integer
 * ordinal matching ShaloTrack_API.Enums.ComplaintCategory exactly
 * (DeviceIssue=0, Billing=1, AppBug=2, Other=3) — there is no
 * JsonStringEnumConverter configured on the API, so this must stay an
 * int, not a string, or the API will 400 on model binding.
 */
public class CreateComplaintRequest {

    @SerializedName("vehicleId")
    private String vehicleId;

    @SerializedName("category")
    private int category;

    @SerializedName("description")
    private String description;

    public CreateComplaintRequest(String vehicleId, int category, String description) {
        this.vehicleId = vehicleId;
        this.category = category;
        this.description = description;
    }
}