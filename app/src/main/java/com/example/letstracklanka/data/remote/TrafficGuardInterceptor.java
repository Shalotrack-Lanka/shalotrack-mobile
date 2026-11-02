package com.example.letstracklanka.data.remote;

import android.os.SystemClock;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * The app's own protection against hammering our API (and against being hammered back):
 *
 * 1. Request budget: at most {@value #MAX_PER_WINDOW} requests per second (a busy screen such as Home legitimately
 *    sends a burst of calls at start-up). A runaway loop or a stuck retry would otherwise generate hundreds a second.
 * 2. Server push-back: after a 429 (too many requests) or 503 the app stops calling THAT ENDPOINT for the time the
 *    server asked for (Retry-After, default 30 s, capped at 5 minutes). It is per endpoint on purpose: one
 *    rate-limited call (for example asking for a second SMS code) must never block the rest of the app,
 *    including the call that verifies the code. Extra calls to that endpoint fail instantly on the phone.
 * 3. Response size cap: a body that declares more than 8 MB is refused before it is read, so a hostile or
 *    broken response cannot exhaust the phone's memory.
 *
 * It does NOT replace server-side rate limiting; it keeps a well-behaved app from becoming part of the problem.
 */
public class TrafficGuardInterceptor implements Interceptor {

    static final int MAX_PER_WINDOW = 40;
    static final long WINDOW_MS = 1000L;
    static final long DEFAULT_BACKOFF_MS = 30_000L;
    static final long MAX_BACKOFF_MS = 300_000L;
    static final long MAX_RESPONSE_BYTES = 8L * 1024 * 1024;

    private final Object lock = new Object();
    private final long[] stamps = new long[MAX_PER_WINDOW];
    private int next = 0;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> blockedUntil =
            new java.util.concurrent.ConcurrentHashMap<>();

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        long now = SystemClock.elapsedRealtime();

        String key = request.method() + " " + request.url().encodedPath();
        Long until = blockedUntil.get(key);
        long wait = until == null ? 0 : until - now;
        if (until != null && wait <= 0) blockedUntil.remove(key);
        if (wait > 0) {
            throw new IOException("The server asked us to slow down. Try again in " + ((wait + 999) / 1000) + " s.");
        }

        synchronized (lock) {
            long oldest = stamps[next];
            if (oldest != 0 && now - oldest < WINDOW_MS) {
                throw new IOException("Too many requests from this device. Please wait a moment.");
            }
            stamps[next] = now;
            next = (next + 1) % MAX_PER_WINDOW;
        }

        Response response = chain.proceed(request);

        int code = response.code();
        if (code == 429 || code == 503) {
            long ms = parseRetryAfterMs(response.header("Retry-After"));
            blockedUntil.put(key, SystemClock.elapsedRealtime() + ms);
        }

        long declared = response.body() != null ? response.body().contentLength() : -1L;
        if (declared > MAX_RESPONSE_BYTES) {
            response.close();
            throw new IOException("Response too large");
        }
        return response;
    }

    static long parseRetryAfterMs(String header) {
        if (header == null) return DEFAULT_BACKOFF_MS;
        try {
            long seconds = Long.parseLong(header.trim());
            if (seconds <= 0) return DEFAULT_BACKOFF_MS;
            return Math.min(seconds * 1000L, MAX_BACKOFF_MS);
        } catch (NumberFormatException e) {
            return DEFAULT_BACKOFF_MS;   // HTTP-date form: not worth parsing, use the safe default
        }
    }
}
