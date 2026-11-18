package com.example.letstracklanka.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;

import com.example.letstracklanka.data.model.RegisterFcmTokenRequest;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.auth.LoginActivity;
import com.example.letstracklanka.ui.common.BottomNav;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The ONLY way the app signs a customer out. Every sign-out button goes through here.
 *
 * Before this, the drawer's "Log out" only called FirebaseAuth.signOut(). It left the customer id,
 * selected vehicle, favourites and alert settings on the phone, and kept the phone's push token on the
 * account, so after signing out the phone kept showing that customer's vehicle alerts, and (because the
 * API did not move an existing token) the next person to sign in on the phone received them too.
 *
 * Order matters:
 *   1. tell the API to drop this phone's push token (needs the still-valid sign-in; 4 s limit),
 *   2. sign out of Firebase and wipe every customer-specific file and notification,
 *   3. delete the push token on the phone. Done AFTER sign-out on purpose: Firebase immediately issues a
 *      new token, and the app must not be signed in when that new token tries to register itself.
 *      Any push still aimed at the old token is rejected by Firebase from here on, even if step 1 failed.
 */
public final class SignOut {

    /** Every SharedPreferences file that holds anything about the signed-in customer. */
    private static final String[] CUSTOMER_PREFS = {
            "LetsTrackSession",              // customer id, sign-up step
            "ShaloTrackVehiclePrefs",        // selected vehicle
            "ShaloTrackVehicleFavorites",    // favourite vehicles (older builds)
            "ShaloTrackNotificationPrefs",   // alert toggles
            "ShaloTrackMapPrefs",            // map type
    };

    private static final long SERVER_STEP_TIMEOUT_MS = 4000;

    private SignOut() {}

    public static void perform(@NonNull Activity activity) {
        Context app = activity.getApplicationContext();
        AtomicBoolean done = new AtomicBoolean(false);
        Handler main = new Handler(Looper.getMainLooper());
        Runnable finish = () -> {
            if (done.compareAndSet(false, true)) completeLocally(activity, app);
        };
        main.postDelayed(finish, SERVER_STEP_TIMEOUT_MS);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            main.post(finish);
            return;
        }

        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                String token = task.isSuccessful() ? task.getResult() : null;
                if (token == null || token.isEmpty()) {
                    main.post(finish);
                    return;
                }
                ApiService api = ApiClient.getClient().create(ApiService.class);
                api.unregisterFcmToken(new RegisterFcmTokenRequest(token, "android")).enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        if (response.body() != null) response.body().close();
                        main.post(finish);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        main.post(finish);
                    }
                });
            });
        } catch (Exception e) {
            main.post(finish);
        }
    }

    /** Wipes local customer data without touching the network (also used after the account is deleted). */
    public static void clearLocalData(Context context) {
        Context app = context.getApplicationContext();
        for (String name : CUSTOMER_PREFS) {
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply();
        }
        NotificationManagerCompat.from(app).cancelAll();
        BottomNav.reset();
    }

    private static void completeLocally(Activity activity, Context app) {
        FirebaseAuth.getInstance().signOut();
        clearLocalData(app);
        try {
            FirebaseMessaging.getInstance().deleteToken();
        } catch (Exception ignored) {
            // the server step already dropped the token in the normal case
        }
        Intent intent = new Intent(app, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        app.startActivity(intent);
        if (!activity.isFinishing()) activity.finishAffinity();
    }
}
