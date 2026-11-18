package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

/** One app plan from GET/POST api/Subscriptions (SubscriptionResponseDto). */
@SuppressWarnings("unused")
public class SubscriptionResponse {

    @SerializedName(value = "subscriptionId", alternate = {"SubscriptionId"})
    private String subscriptionId;
    /** Free | OneYear | TwoYears | ThreeYears */
    @SerializedName(value = "plan", alternate = {"Plan"})
    private String plan;
    @SerializedName(value = "priceLkr", alternate = {"PriceLkr"})
    private Double priceLkr;
    /** PendingPayment | Active | Expired | Cancelled */
    @SerializedName(value = "status", alternate = {"Status"})
    private String status;
    @SerializedName(value = "startDate", alternate = {"StartDate"})
    private String startDate;
    @SerializedName(value = "endDate", alternate = {"EndDate"})
    private String endDate;
    @SerializedName(value = "createdAt", alternate = {"CreatedAt"})
    private String createdAt;
    /** Only filled on the reply to a new request: how to pay. Written by the server for the customer. */
    @SerializedName(value = "instructionsMessage", alternate = {"InstructionsMessage"})
    private String instructionsMessage;

    public String getSubscriptionId() { return subscriptionId; }
    public String getPlan() { return plan; }
    public Double getPriceLkr() { return priceLkr; }
    public String getStatus() { return status; }
    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }
    public String getCreatedAt() { return createdAt; }
    public String getInstructionsMessage() { return instructionsMessage; }

    public boolean isActive() { return "Active".equalsIgnoreCase(status); }
    public boolean isPendingPayment() { return "PendingPayment".equalsIgnoreCase(status); }
}
