package com.example.letstracklanka.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * The API's standard envelope ({ success, statusCode, message, data }). For /request the data is an
 * empty object; for /verify it carries the Firebase custom token.
 */
public class OtpResponse {
    @SerializedName(value = "success", alternate = {"Success"})
    public boolean success;

    @SerializedName(value = "statusCode", alternate = {"StatusCode"})
    public int statusCode;

    @SerializedName(value = "message", alternate = {"Message"})
    public String message;

    @SerializedName(value = "data", alternate = {"Data"})
    public Data data;

    public static class Data {
        @SerializedName(value = "customToken", alternate = {"CustomToken"})
        public String customToken;

        @SerializedName(value = "isNewCustomer", alternate = {"IsNewCustomer"})
        public boolean isNewCustomer;
    }
}