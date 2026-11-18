package com.example.letstracklanka.utils;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.example.letstracklanka.ui.main.SplashActivity;
import com.google.firebase.auth.FirebaseAuth;

/**
 * For screens that can be opened from outside the app (a notification tap). If nobody is signed in on
 * this phone (signed out, session revoked, data cleared) the screen must not open at all: send the user
 * through the normal start (Splash, then sign in) and close the screen before it inflates anything.
 */
public final class AuthGate {

    private AuthGate() {}

    /** @return true if the caller may continue; false if it was redirected and should return at once. */
    public static boolean requireSignedIn(@NonNull Activity activity) {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) return true;
        Intent start = new Intent(activity, SplashActivity.class);
        start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(start);
        activity.finish();
        return false;
    }
}
