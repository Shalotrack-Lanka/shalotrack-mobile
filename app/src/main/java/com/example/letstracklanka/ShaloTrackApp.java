package com.example.letstracklanka;

import android.app.Application;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

public class ShaloTrackApp extends Application {
    
    public static final boolean TEST_MODE = false;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 1. Initialize Firebase
        FirebaseApp.initializeApp(this);

        // 2. Initialize App Check (Mandatory for real devices)
        try {
            FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
            firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance());
            Log.d("ShaloTrackApp", "App Check Provider Installed Successfully.");
        } catch (Exception e) {
            Log.e("ShaloTrackApp", "App Check initialization failed: " + e.getMessage());
        }
    }
}
