package com.example.letstracklanka.data.remote;

import okhttp3.ConnectionSpec;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import java.util.Collections;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.TimeUnit;

public class ApiClient {
    private static final String BASE_URL = "https://api.shalotrack.com/";
    private static Retrofit retrofit = null;
    private static final String API_HOST = "api.shalotrack.com";
    private static boolean debuggable = false;

    /** True only for https requests to our own API host: the one place the login token may be sent. */
    public static boolean isTrustedApiUrl(HttpUrl url) {
        return url != null && url.isHttps() && API_HOST.equalsIgnoreCase(url.host());
    }

    /** Called once from ShaloTrackApp. Full HTTP logging is only ever enabled on debuggable builds. */
    public static void setDebuggable(boolean value) {
        debuggable = value;
    }

    public static Retrofit getClient() {
        if (retrofit == null) {

            // 1. HTTP logger. Level.BODY prints the Bearer token, customer data and (now) payment slips into
            //    logcat, so it is limited to debuggable builds, the token header is always redacted, and a
            //    release build logs nothing at all.
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.redactHeader("Authorization");
            logging.setLevel(debuggable
                    ? HttpLoggingInterceptor.Level.BASIC
                    : HttpLoggingInterceptor.Level.NONE);

            // 2. Build the client with the logger and 30-second timeouts
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(new TrafficGuardInterceptor())
                    .addInterceptor(new AuthInterceptor())
                    .addInterceptor(new RenewalRequiredInterceptor())
                    .addInterceptor(new PendingDeletionInterceptor())
                    .addInterceptor(logging)
                    // TLS 1.2+ with modern cipher suites only; no plain HTTP fallback.
                    .connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS))
                    // A redirect could move a request (and its Authorization header) to another host.
                    // Our API never redirects, so refuse to follow any.
                    .followRedirects(false)
                    .followSslRedirects(false)
                    // OkHttp's default is CookieJar.NO_COOKIES. Stated here because it is a security
                    // property: the app authenticates with a Bearer header only, so there are no
                    // cookies for a CSRF attack to ride on.
                    .cookieJar(okhttp3.CookieJar.NO_COOKIES)
                    .callTimeout(60, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

            // 3. Attach the client to Retrofit
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}