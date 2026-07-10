package com.example.letstracklanka;

import android.app.Application;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.auth.FirebaseAuth;

public class ShaloTrackApp extends Application {
    
    // Set to 'false' for final submission. Using 'true' only if you are stuck on Error 39.
    public static final boolean TEST_MODE = false;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 1. Initialize Firebase
        FirebaseApp.initializeApp(this);

        // 2. Initialize App Check (Optimized)
        try {
            FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
            firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance());
            Log.d("ShaloTrackApp", "App Check Ready");
        } catch (Exception e) {
            Log.e("ShaloTrackApp", "App Check initialization failed: " + e.getMessage());
        }
    }
}
