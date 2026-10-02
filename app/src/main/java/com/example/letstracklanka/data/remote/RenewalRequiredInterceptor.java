package com.example.letstracklanka.data.remote;

import androidx.annotation.NonNull;

import com.example.letstracklanka.ui.renewals.RenewalPrompt;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Watches every API response for the "renewal required" signal (HTTP 402 carrying the error code
 * SUBSCRIPTION_RENEWAL_REQUIRED) and asks RenewalPrompt to tell the customer, from whatever screen
 * they are on, instead of each screen showing its own generic "failed" toast.
 *
 * The response is never altered: the calling screen still receives the 402 and decides what to
 * show. peekBody reads a small copy, so the real body stays intact for Retrofit.
 */
public class RenewalRequiredInterceptor implements Interceptor {

    static final String RENEWAL_REQUIRED_CODE = "SUBSCRIPTION_RENEWAL_REQUIRED";
    private static final long PEEK_BYTES = 4096;

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        if (response.code() == 402) {
            try {
                ResponseBody peek = response.peekBody(PEEK_BYTES);
                if (peek.string().contains(RENEWAL_REQUIRED_CODE)) {
                    RenewalPrompt.onRenewalRequired();
                }
            } catch (IOException | RuntimeException ignored) {
                // Never let the prompt logic break a request.
            }
        }
        return response;
    }
}