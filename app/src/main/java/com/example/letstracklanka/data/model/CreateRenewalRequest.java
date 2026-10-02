package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Body for POST /api/Renewals. Unlike complaints, the renewal endpoints use enum NAMES (strings), not
 * ordinals: duration is one of ThreeMonths | SixMonths | OneYear | TwoYears | ThreeYears.
 * paymentMethod is left out on purpose (the server defaults to BankSlip; online payment is not
 * available yet). No price is sent: the customer never chooses or sends an amount.
 */
public class CreateRenewalRequest {

    @SerializedName("vehicleId")
    private final String vehicleId;

    @SerializedName("duration")
    private final String duration;

    @SerializedName("paymentReference")
    private final String paymentReference;

    public CreateRenewalRequest(String vehicleId, String duration, String paymentReference) {
        this.vehicleId = vehicleId;
        this.duration = duration;
        this.paymentReference = paymentReference;
    }
}