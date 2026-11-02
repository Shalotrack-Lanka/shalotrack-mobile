package com.example.letstracklanka.data.remote;

import com.example.letstracklanka.data.model.OtpRequestBody;
import com.example.letstracklanka.data.model.OtpResponse;
import com.example.letstracklanka.data.model.OtpVerifyBody;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/** Our own phone-OTP sign-in (SMS via Text.lk), replacing Firebase Phone Auth's SMS. */
public interface AuthApi {
    @POST("api/auth/otp/request")
    Call<OtpResponse> requestOtp(@Body OtpRequestBody body);

    @POST("api/auth/otp/verify")
    Call<OtpResponse> verifyOtp(@Body OtpVerifyBody body);
}