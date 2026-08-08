package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

public class CreateEmergencyContactRequest {

    @SerializedName(value = "name", alternate = {"Name"})
    private String name;

    @SerializedName(value = "phoneNumber", alternate = {"PhoneNumber"})
    private String phoneNumber;

    @SerializedName(value = "relationship", alternate = {"Relationship"})
    private String relationship;

    public CreateEmergencyContactRequest(String name, String phoneNumber, String relationship) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.relationship = relationship;
    }
}