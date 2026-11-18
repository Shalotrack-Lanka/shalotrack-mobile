package com.example.letstracklanka.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Reading a failed reply once, the same way everywhere: the server's own message for client errors (4xx,
 * written for the customer), a fixed message for anything else (5xx text is never shown). Closes the body.
 */
public final class ApiErrors {

    /** Sent by the API when an action needs a sign-in from the last 10 minutes. */
    public static final String REAUTH_CODE = "REAUTH_REQUIRED";

    private ApiErrors() {}

    public static final class Failure {
        @NonNull public final String message;
        public final int code;
        public final boolean reauthRequired;

        Failure(@NonNull String message, int code, boolean reauthRequired) {
            this.message = message;
            this.code = code;
            this.reauthRequired = reauthRequired;
        }
    }

    @NonNull
    public static Failure read(@NonNull Response<?> response, @NonNull String fallback) {
        String body = null;
        try (ResponseBody err = response.errorBody()) {
            if (err != null) body = err.string();
        } catch (Exception ignored) {
            body = null;
        }
        int code = response.code();
        boolean reauth = code == 403 && body != null && body.contains(REAUTH_CODE);
        String message = code >= 400 && code < 500 ? ApiJson.message(body, fallback) : fallback;
        return new Failure(message, code, reauth);
    }

    /** Closes an unread reply, success or error body, so its pooled connection goes back to OkHttp. */
    public static void close(@Nullable Response<?> response) {
        if (response == null) return;
        Object b = response.body();
        if (b instanceof ResponseBody) ((ResponseBody) b).close();
        ResponseBody err = response.errorBody();
        if (err != null) err.close();
    }
}
