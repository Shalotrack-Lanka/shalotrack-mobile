package com.example.letstracklanka.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "LetsTrackSession";
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    public void setSignupStep(String step) {
        editor.putString("signup_step", step);
        editor.apply();
    }

    public String getSignupStep() {
        return pref.getString("signup_step", "NONE");
    }
}