package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * One renewal request as returned by api/Renewals (data object or list item). Status and duration
 * arrive as enum names. The *Label helpers turn them into customer-facing text in ONE place.
 */
public class RenewalResponse {

    public static final String STATUS_AWAITING_SLIP = "AwaitingSlip";
    public static final String STATUS_PENDING_REVIEW = "PendingReview";
    public static final String STATUS_APPROVED = "Approved";
    public static final String STATUS_REJECTED = "Rejected";
    public static final String STATUS_CANCELLED = "Cancelled";

    /** Index-matched to DURATION_NAMES: the label's position IS the value sent. */
    public static final String[] DURATION_LABELS = {"3 Months", "6 Months", "1 Year", "2 Years", "3 Years"};
    public static final String[] DURATION_NAMES = {"ThreeMonths", "SixMonths", "OneYear", "TwoYears", "ThreeYears"};

    @SerializedName("renewalRequestId")
    private String renewalRequestId;
    @SerializedName("vehicleId")
    private String vehicleId;
    @SerializedName("vehicleNumber")
    private String vehicleNumber;
    @SerializedName("duration")
    private String duration;
    @SerializedName("status")
    private String status;
    @SerializedName("hasSlip")
    private Boolean hasSlip;
    @SerializedName("paymentReference")
    private String paymentReference;
    @SerializedName("decisionReason")
    private String decisionReason;
    @SerializedName("createdAt")
    private String createdAt;
    @SerializedName("instructionsMessage")
    private String instructionsMessage;

    public String getRenewalRequestId() { return renewalRequestId; }
    public String getVehicleId() { return vehicleId; }
    public String getVehicleNumber() { return vehicleNumber; }
    public String getDuration() { return duration; }
    public String getStatus() { return status; }
    public boolean hasSlip() { return hasSlip != null && hasSlip; }
    public String getPaymentReference() { return paymentReference; }
    public String getDecisionReason() { return decisionReason; }
    public String getCreatedAt() { return createdAt; }
    public String getInstructionsMessage() { return instructionsMessage; }

    /** True while the request can still take a (new) slip or be cancelled. */
    public boolean isOpen() {
        return STATUS_AWAITING_SLIP.equals(status) || STATUS_PENDING_REVIEW.equals(status);
    }

    public String getDurationLabel() {
        for (int i = 0; i < DURATION_NAMES.length; i++) {
            if (DURATION_NAMES[i].equals(duration)) return DURATION_LABELS[i];
        }
        return duration == null ? "" : duration;
    }

    public String getStatusLabel() {
        if (STATUS_AWAITING_SLIP.equals(status)) return "Waiting for your slip";
        if (STATUS_PENDING_REVIEW.equals(status)) return "Under review";
        if (STATUS_APPROVED.equals(status)) return "Approved";
        if (STATUS_REJECTED.equals(status)) return "Not approved";
        if (STATUS_CANCELLED.equals(status)) return "Cancelled";
        return status == null ? "" : status;
    }
}