package com.example.letstracklanka;

import android.app.Application;
import android.content.pm.ApplicationInfo;

import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.ui.account.PendingDeletionRouter;
import com.example.letstracklanka.ui.renewals.RenewalPrompt;
import com.google.firebase.FirebaseApp;

public class ShaloTrackApp extends Application {

    public static final boolean TEST_MODE = false;

    @Override
    public void onCreate() {
        super.onCreate();

        // Must run before the first ApiClient.getClient() call anywhere in the app.
        ApiClient.setDebuggable((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
        RenewalPrompt.install(this);
        PendingDeletionRouter.install(this);

        FirebaseApp.initializeApp(this);

        // Debug builds -> Debug provider; release builds -> Play Integrity
        // (two different AppCheckInstaller classes in src/debug and src/release).
        try {
            AppCheckInstaller.install();
        } catch (Exception ignored) {
            // App Check must never crash app start
        }
    }
}