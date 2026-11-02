package com.example.letstracklanka.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.letstracklanka.data.model.OtpRequestBody;
import com.example.letstracklanka.data.model.OtpResponse;
import com.example.letstracklanka.data.model.OtpVerifyBody;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.AuthApi;
import com.google.gson.Gson;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Phone OTP through our API. Never logs the phone number, the code or the token: the HTTP logger is
 * metadata-only and this class writes nothing to logcat.
 *
 * Messages shown to the customer come from the API only for 400 / 429 (we wrote them and they reveal
 * nothing); everything else gets a fixed friendly sentence.
 */
public final class OtpRepository {

    public interface RequestCallback {
        void onSent();

        /** @param retryAfterSeconds from the Retry-After header, 0 when the server did not say */
        void onFailure(@NonNull String message, long retryAfterSeconds);
    }

    public interface VerifyCallback {
        /** @param customToken exchange it with FirebaseAuth.signInWithCustomToken */
        void onVerified(@NonNull String customToken);

        /** @param codeRejected true when the server said the code is wrong or expired (HTTP 400) */
        void onFailure(@NonNull String message, boolean codeRejected);
    }

    private static final Gson GSON = new Gson();
    private static final long MAX_RETRY_AFTER_SECONDS = 15 * 60;

    private OtpRepository() {}

    private static AuthApi api() {
        return ApiClient.getClient().create(AuthApi.class);
    }

    public static void requestOtp(@NonNull String phoneNumber, @NonNull RequestCallback callback) {
        api().requestOtp(new OtpRequestBody(phoneNumber)).enqueue(new Callback<OtpResponse>() {
            @Override
            public void onResponse(@NonNull Call<OtpResponse> call, @NonNull Response<OtpResponse> response) {
                if (response.isSuccessful()) {
                    callback.onSent();
                    return;
                }
                callback.onFailure(
                        messageFor(response.code(), parseError(response.errorBody()), "We couldn't send the code. Please try again shortly."),
                        retryAfterSeconds(response));
            }

            @Override
            public void onFailure(@NonNull Call<OtpResponse> call, @NonNull Throwable t) {
                callback.onFailure("No connection. Check your internet and try again.", 0);
            }
        });
    }

    public static void verifyOtp(@NonNull String phoneNumber, @NonNull String code, @NonNull VerifyCallback callback) {
        api().verifyOtp(new OtpVerifyBody(phoneNumber, code)).enqueue(new Callback<OtpResponse>() {
            @Override
            public void onResponse(@NonNull Call<OtpResponse> call, @NonNull Response<OtpResponse> response) {
                if (response.isSuccessful()) {
                    OtpResponse body = response.body();
                    String token = body != null && body.data != null ? body.data.customToken : null;
                    if (token != null && !token.isEmpty()) {
                        callback.onVerified(token);
                    } else {
                        callback.onFailure("Something went wrong. Please try again.", false);
                    }
                    return;
                }
                callback.onFailure(
                        messageFor(response.code(), parseError(response.errorBody()), "We couldn't verify the code. Please try again."),
                        response.code() == 400);
            }

            @Override
            public void onFailure(@NonNull Call<OtpResponse> call, @NonNull Throwable t) {
                callback.onFailure("No connection. Check your internet and try again.", false);
            }
        });
    }

    @Nullable
    private static OtpResponse parseError(@Nullable ResponseBody errorBody) {
        if (errorBody == null) return null;
        try {
            return GSON.fromJson(errorBody.string(), OtpResponse.class);
        } catch (Exception e) {
            return null;   // not our JSON (a proxy page, for example)
        }
    }

    private static String messageFor(int httpCode, @Nullable OtpResponse error, String fallback) {
        boolean ownMessage = httpCode == 400 || httpCode == 429;
        if (ownMessage && error != null && error.message != null
                && !error.message.isEmpty() && error.message.length() < 200) {
            return error.message;
        }
        if (httpCode == 429) {
            return "Too many attempts. Please wait a few minutes and try again.";
        }
        return fallback;
    }

    private static long retryAfterSeconds(@NonNull Response<?> response) {
        String header = response.headers().get("Retry-After");
        if (header == null) return 0;
        try {
            long seconds = Long.parseLong(header.trim());
            return Math.max(0, Math.min(seconds, MAX_RETRY_AFTER_SECONDS));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}