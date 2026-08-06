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

    // Firebase Auth UID, so the backend can link the DB row to the
    // signed-in Firebase user instead of relying solely on the Bearer token.
    @SerializedName("FirebaseUid")
    private final String firebaseUid;

    public CustomerRequest(String fullName, String email, String phoneNumber, String nicNumber, String address, String firebaseUid) {
        this.fullName = fullName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.nicNumber = nicNumber;
        this.address = address;
        this.firebaseUid = firebaseUid;
    }

    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getNicNumber() { return nicNumber; }
    public String getAddress() { return address; }
    public String getFirebaseUid() { return firebaseUid; }
}
