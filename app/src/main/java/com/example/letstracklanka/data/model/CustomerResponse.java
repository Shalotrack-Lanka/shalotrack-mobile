package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

// Add this line to tell Android Studio that Gson handles this class!
@SuppressWarnings("unused")
public class CustomerResponse {

    @SerializedName("customerId")
    private String customerId;

    @SerializedName("fullName")
    private String fullName;

    @SerializedName("email")
    private String email;

    @SerializedName("phoneNumber")
    private String phoneNumber;

    @SerializedName("nicNumber")
    private String nicNumber;

    @SerializedName("address")
    private String address;

    // Your getters remain here
    public String getCustomerId() { return customerId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getNicNumber() { return nicNumber; }
    public String getAddress() { return address; }
}