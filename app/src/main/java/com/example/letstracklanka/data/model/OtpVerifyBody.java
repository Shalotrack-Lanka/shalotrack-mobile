package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

/** POST api/auth/otp/verify. The code is exactly 6 digits. */
public class OtpVerifyBody {
    @SerializedName("phoneNumber")
    private final String phoneNumber;

    @SerializedName("code")
    private final String code;

    public OtpVerifyBody(String phoneNumber, String code) {
        this.phoneNumber = phoneNumber;
        this.code = code;
    }
}