package com.example.letstracklanka.data.model;

/** Request body for PUT /api/Customers/{customerId}. */
public class UpdateCustomerRequest {
    private String fullName;
    private String phoneNumber;
    private String address;
    private String profileImage;

    public UpdateCustomerRequest(String fullName, String phoneNumber, String address, String profileImage) {
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.profileImage = profileImage;
    }
}