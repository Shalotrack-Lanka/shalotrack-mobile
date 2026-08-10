package com.example.letstracklanka.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "LetsTrackSession";

    // Key constants to prevent typos
    private static final String KEY_SIGNUP_STEP = "signup_step";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_CUSTOMER_ID = "customer_id";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_PHONE = "user_phone";

    private final SharedPreferences pref;
    private final SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    // --- Signup Flow ---
    public void setSignupStep(String step) {
        editor.putString(KEY_SIGNUP_STEP, step);
        editor.apply();
    }

    public String getSignupStep() {
        return pref.getString(KEY_SIGNUP_STEP, "NONE");
    }

    // --- Login & Authentication ---
    public void createLoginSession(String customerId, String name, String email, String phone) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_CUSTOMER_ID, customerId);
        editor.putString(KEY_USER_NAME, name);
        editor.putString(KEY_USER_EMAIL, email);
        editor.putString(KEY_USER_PHONE, phone);
        editor.apply();
    }

    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getCustomerId() {
        return pref.getString(KEY_CUSTOMER_ID, null);
    }

    public String getUserName() {
        return pref.getString(KEY_USER_NAME, "Unknown User");
    }

    public String getUserEmail() {
        return pref.getString(KEY_USER_EMAIL, "No Email");
    }

    public String getUserPhone() {
        return pref.getString(KEY_USER_PHONE, "No Phone Number");
    }

    public void updateUserDetails(String name, String email, String phone) {
        editor.putString(KEY_USER_NAME, name);
        editor.putString(KEY_USER_EMAIL, email);
        editor.putString(KEY_USER_PHONE, phone);
        editor.apply();
    }

    // --- Logout ---
    public void logoutUser() {
        // Clears all session data
        editor.clear();
        editor.apply();
    }
}