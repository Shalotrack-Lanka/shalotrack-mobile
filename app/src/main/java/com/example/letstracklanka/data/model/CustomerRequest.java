package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Update: Reverted to PascalCase (FullName, Email, etc.) 
 * because the existing DB entries suggest the backend uses 
 * C# naming conventions for its JSON properties.
 */
@SuppressWarnings("unused")
public class CustomerRequest {

    @SerializedName("FullName")
    private final String fullName;

    @SerializedName("Email")
    private final String email;

    @SerializedName("PhoneNumber")
    private final String phoneNumber;

    @SerializedName("NicNumber")
    private final String nicNumber;

    @SerializedName("Address")
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
