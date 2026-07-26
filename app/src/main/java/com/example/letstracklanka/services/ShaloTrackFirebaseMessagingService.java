package com.example.letstracklanka.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.letstracklanka.data.model.RegisterFcmTokenRequest;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.main.AlertsActivity;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Receives FCM tokens and incoming alert push notifications.
 *
 * IMPORTANT, standard FCM behavior worth understanding: when the app is
 * backgrounded or killed, Android's system tray displays the notification
 * automatically using the "notification" payload our API sends -- this
 * class's onMessageReceived() is NOT even called in that case. It only fires
 * when the app is in the FOREGROUND, at which point Android does nothing
 * automatically and we have to build and show the notification ourselves --
 * which is exactly what this does below.
 */
public class ShaloTrackFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "ShaloTrackFCM";
    private static final String CHANNEL_ID = "shalotrack_alerts";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token received, registering with API");
        registerToken(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = "ShaloTrack Alert";
        String body = "You have a new alert";

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        }

        showNotification(title, body);
    }

    private void showNotification(String title, String body) {
        createNotificationChannelIfNeeded();

        // NEW -- tapping the notification now opens AlertsActivity. A system
        // notification is delivered outside any Activity's task context, so the
        // intent MUST carry FLAG_ACTIVITY_NEW_TASK or Android will silently do
        // nothing when tapped.
        Intent intent = new Intent(this, AlertsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;   // required on Android 12+
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),   // unique per notification, so multiple don't overwrite each other's tap target
                intent,
                pendingIntentFlags
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)   // TODO: replace with the app's real notification icon
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        try {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS permission not granted (Android 13+) -- fails
            // safely rather than crashing the app over a missing notification.
            Log.w(TAG, "Notification permission not granted, could not show alert", e);
        }
    }

    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "ShaloTrack Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Ignition, overspeed, power, and battery alerts for your vehicle");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void registerToken(String token) {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.registerFcmToken(new RegisterFcmTokenRequest(token, "android"))
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "FCM token registered successfully");
                        } else {
                            Log.w(TAG, "FCM token registration failed, code " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        Log.w(TAG, "FCM token registration network error", t);
                    }
                });
    }
}