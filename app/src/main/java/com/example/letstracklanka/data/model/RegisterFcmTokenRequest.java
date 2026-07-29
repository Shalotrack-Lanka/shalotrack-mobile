package com.example.letstracklanka.data.model;

/** Request body for POST /api/Alerts/register-token. */
public class RegisterFcmTokenRequest {
    private String fcmToken;
    private String platform;

    public RegisterFcmTokenRequest(String fcmToken, String platform) {
        this.fcmToken = fcmToken;
        this.platform = platform;
    }
}