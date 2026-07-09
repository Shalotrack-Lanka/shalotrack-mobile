package com.example.letstracklanka;

public class CustomerRequest {
    private String fullName;
    private String email;
    private String phoneNumber;
    private String nicNumber;
    private String address;

    public CustomerRequest(String fullName, String email, String phoneNumber, String nicNumber, String address) {
        this.fullName = fullName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.nicNumber = nicNumber;
        this.address = address;
    }

    // Getters and Setters (Optional, but good practice)
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getNicNumber() { return nicNumber; }
    public String getAddress() { return address; }
}