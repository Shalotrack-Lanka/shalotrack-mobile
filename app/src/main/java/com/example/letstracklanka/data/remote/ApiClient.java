package com.example.letstracklanka.data.remote;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.TimeUnit;

public class ApiClient {
    private static final String BASE_URL = "https://api.shalotrack.com/";
    private static Retrofit retrofit = null;
    private static boolean debuggable = false;

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
                    .addInterceptor(new AuthInterceptor())
                    .addInterceptor(new RenewalRequiredInterceptor())
                    .addInterceptor(logging)
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