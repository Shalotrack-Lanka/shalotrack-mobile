package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
public class CustomerRequest {

    @SerializedName("fullName")
    private final String fullName;

    @SerializedName("email")
    private final String email;

    @SerializedName("phoneNumber")
    private final String phoneNumber;

    @SerializedName("nicNumber")
    private final String nicNumber;

    @SerializedName("address")
    private final String address;

    public CustomerRequest(String fullName, String email, String phoneNumber, String nicNumber, String address) {
        this.fullName = fullName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.nicNumber = nicNumber;
        this.address = address;
    }

    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getNicNumber() { return nicNumber; }
    public String getAddress() { return address; }
}
