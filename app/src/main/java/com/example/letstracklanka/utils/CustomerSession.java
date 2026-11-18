package com.example.letstracklanka.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The signed-in customer's id, fetched once and remembered, instead of every screen calling
 * GET api/Customers/me before it can do anything (each tab used to make that call on every open).
 *
 * The id is stored together with the Firebase uid it belongs to and is only trusted while that same
 * Firebase user is signed in, so a different account on the same phone can never reuse it.
 * The id is not a secret (the API checks ownership on every call); caching it just saves requests.
 */
public final class CustomerSession {

    public interface Callback2 {
        void onCustomer(@NonNull String customerId);
        void onError(int httpCode);   // 0 = network error
    }

    static final String PREFS = "LetsTrackSession";
    private static final String KEY_CUSTOMER_ID = "customer_id";
    private static final String KEY_FIREBASE_UID = "customer_firebase_uid";

    private CustomerSession() {}

    @Nullable
    public static String cachedId(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return null;
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String uid = p.getString(KEY_FIREBASE_UID, null);
        String id = p.getString(KEY_CUSTOMER_ID, null);
        return user.getUid().equals(uid) ? id : null;
    }

    /** Calls back with the customer id: from the cache when valid, otherwise from GET api/Customers/me. */
    public static void get(Context context, Callback2 cb) {
        String cached = cachedId(context);
        if (cached != null) {
            cb.onCustomer(cached);
            return;
        }
        refresh(context, cb);
    }

    /** Ignores the cache (use after the API answered 403/404 for the cached id). */
    public static void refresh(Context context, Callback2 cb) {
        Context app = context.getApplicationContext();
        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        CustomerResponse c = ApiJson.object(body.string(), CustomerResponse.class);
                        if (c != null && c.getCustomerId() != null) {
                            remember(app, c.getCustomerId());
                            cb.onCustomer(c.getCustomerId());
                            return;
                        }
                    }
                    cb.onError(response.code());
                } catch (Exception e) {
                    cb.onError(response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                cb.onError(0);
            }
        });
    }

    public static void remember(Context context, String customerId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || customerId == null) return;
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_CUSTOMER_ID, customerId)
                .putString(KEY_FIREBASE_UID, user.getUid())
                .apply();
    }

    public static void forget(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_CUSTOMER_ID)
                .remove(KEY_FIREBASE_UID)
                .apply();
    }
}
