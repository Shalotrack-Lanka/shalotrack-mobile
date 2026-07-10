package com.example.letstracklanka;

import android.app.Application;
import com.google.firebase.FirebaseApp;

public class ShaloTrackApp extends Application {
    
    // PRODUCTION SETTINGS: All bypasses turned OFF
    public static final boolean TEST_MODE = false;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize Firebase for the real production flow
        FirebaseApp.initializeApp(this);
    }
}
