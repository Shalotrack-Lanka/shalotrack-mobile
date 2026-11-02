package com.example.letstracklanka.data.remote;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TrafficGuardInterceptorTest {

    @Test public void retryAfter_secondsAreCapped() {
        assertEquals(10_000L, TrafficGuardInterceptor.parseRetryAfterMs("10"));
        assertEquals(TrafficGuardInterceptor.MAX_BACKOFF_MS, TrafficGuardInterceptor.parseRetryAfterMs("99999"));
    }

    @Test public void retryAfter_missingOrUnparseableUsesDefault() {
        assertEquals(TrafficGuardInterceptor.DEFAULT_BACKOFF_MS, TrafficGuardInterceptor.parseRetryAfterMs(null));
        assertEquals(TrafficGuardInterceptor.DEFAULT_BACKOFF_MS, TrafficGuardInterceptor.parseRetryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT"));
        assertEquals(TrafficGuardInterceptor.DEFAULT_BACKOFF_MS, TrafficGuardInterceptor.parseRetryAfterMs("-5"));
    }
}
