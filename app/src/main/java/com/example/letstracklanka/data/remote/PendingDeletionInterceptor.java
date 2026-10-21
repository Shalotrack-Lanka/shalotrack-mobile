package com.example.letstracklanka.data.remote;

import androidx.annotation.NonNull;

import com.example.letstracklanka.ui.account.PendingDeletionRouter;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * While an account is waiting for deletion the API answers every call except api/Account/* with
 * HTTP 403 carrying the error code ACCOUNT_PENDING_DELETION. Instead of every screen showing its
 * own generic failure, this tells PendingDeletionRouter, which opens the one screen that can fix it
 * (see the status, keep the account).
 *
 * The response is never altered: peekBody reads a small copy, so Retrofit still gets the real body.
 */
public class PendingDeletionInterceptor implements Interceptor {

    static final String PENDING_CODE = "ACCOUNT_PENDING_DELETION";
    private static final long PEEK_BYTES = 4096;

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        if (response.code() == 403) {
            try {
                ResponseBody peek = response.peekBody(PEEK_BYTES);
                if (peek.string().contains(PENDING_CODE)) {
                    PendingDeletionRouter.onPendingDeletion();
                }
            } catch (IOException | RuntimeException ignored) {
                // Never let routing logic break a request.
            }
        }
        return response;
    }
}