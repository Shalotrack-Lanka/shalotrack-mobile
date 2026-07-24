package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
public class CustomerResponse {

    @SerializedName("customerId")
    private String customerId;

    @SerializedName("CustomerId")
    private String customerIdUpper;

    @SerializedName("fullName")
    private String fullName;

    @SerializedName("FullName")
    private String fullNameUpper;

    @SerializedName("email")
    private String email;

    @SerializedName("Email")
    private String emailUpper;

    // NEW -- the API response has always included these (confirmed from a real
    // GET /api/Customers/me response earlier tonight), the model just never had
    // fields for them until now.
    @SerializedName("phoneNumber")
    private String phoneNumber;

    @SerializedName("PhoneNumber")
    private String phoneNumberUpper;

    @SerializedName("address")
    private String address;

    @SerializedName("Address")
    private String addressUpper;

    @SerializedName("profileImage")
    private String profileImage;

    @SerializedName("ProfileImage")
    private String profileImageUpper;

    public String getCustomerId() {
        return customerIdUpper != null ? customerIdUpper : customerId;
    }

    public String getFullName() {
        return fullNameUpper != null ? fullNameUpper : fullName;
    }

    public String getEmail() {
        return emailUpper != null ? emailUpper : email;
    }

    public String getPhoneNumber() {
        return phoneNumberUpper != null ? phoneNumberUpper : phoneNumber;
    }

    public String getAddress() {
        return addressUpper != null ? addressUpper : address;
    }

    public String getProfileImage() {
        return profileImageUpper != null ? profileImageUpper : profileImage;
    }
}