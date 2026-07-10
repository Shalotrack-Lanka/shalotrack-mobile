package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused") // Ignores the "Method is never used" warning
public class CustomerRequest {

    @SerializedName("FullName")
    private final String fullName; // Added 'final'

    @SerializedName("Email")
    private final String email; // Added 'final'

    @SerializedName("PhoneNumber")
    private final String phoneNumber; // Added 'final'

    @SerializedName("NicNumber")
    private final String nicNumber; // Added 'final'

    @SerializedName("Address")
    private final String address; // Added 'final'

    public CustomerRequest(String fullName, String email, String phoneNumber, String nicNumber, String address) {
        this.fullName = fullName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.nicNumber = nicNumber;
        this.address = address;
    }

    // Getters
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getNicNumber() { return nicNumber; }
    public String getAddress() { return address; }
}