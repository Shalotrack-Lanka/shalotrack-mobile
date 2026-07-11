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

    public String getCustomerId() { 
        return customerIdUpper != null ? customerIdUpper : customerId; 
    }
    
    public String getFullName() { 
        return fullNameUpper != null ? fullNameUpper : fullName; 
    }
    
    public String getEmail() { 
        return emailUpper != null ? emailUpper : email; 
    }
}
