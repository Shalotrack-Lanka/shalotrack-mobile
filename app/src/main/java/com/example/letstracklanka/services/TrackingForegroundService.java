package com.example.letstracklanka.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.example.letstracklanka.R;

public class TrackingForegroundService extends Service {

    private static final String CHANNEL_ID = "letstrack_foreground_service";
    private static final int NOTIFICATION_ID = 1001;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable backgroundRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, getStickyNotification());
        startBackgroundTrackingLoop();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void startBackgroundTrackingLoop() {
        backgroundRunnable = new Runnable() {
            @Override
            public void run() {
                // Background Tracking Call
                handler.postDelayed(this, 5000);
            }
        };
        handler.post(backgroundRunnable);
    }

    private Notification getStickyNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("LetsTrack Lanka Active")
                .setContentText("Real-time GPS tracking is running in background")
                .setSmallIcon(R.drawable.ic_car)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Background Tracking Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (backgroundRunnable != null) {
            handler.removeCallbacks(backgroundRunnable);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}