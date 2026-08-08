package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

public class EmergencyContactResponse {

    @SerializedName(value = "emergencyContactId", alternate = {"EmergencyContactId"})
    private String emergencyContactId;

    @SerializedName(value = "name", alternate = {"Name"})
    private String name;

    @SerializedName(value = "phoneNumber", alternate = {"PhoneNumber"})
    private String phoneNumber;

    @SerializedName(value = "relationship", alternate = {"Relationship"})
    private String relationship;

    @SerializedName(value = "createdAt", alternate = {"CreatedAt"})
    private String createdAt;

    public String getEmergencyContactId() { return emergencyContactId; }
    public String getName() { return name; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getRelationship() { return relationship; }
    public String getCreatedAt() { return createdAt; }
}