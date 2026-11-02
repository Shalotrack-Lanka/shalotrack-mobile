package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

/** POST api/auth/otp/request. The server normalises the number (+94...). */
public class OtpRequestBody {
    @SerializedName("phoneNumber")
    private final String phoneNumber;

    public OtpRequestBody(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}