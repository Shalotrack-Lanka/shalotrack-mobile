package com.example.letstracklanka.ui.main;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.example.letstracklanka.R;
import com.example.letstracklanka.ui.contacts.EmergencyContactsActivity;

/**
 * Notification toggles are stored locally (SharedPreferences), not
 * server-side -- deliberately, so this didn't need any new backend work.
 * Real alerts still always reach the Alerts screen and get recorded either
 * way; a toggle here only controls whether THIS device shows/vibrates a
 * push notification for that alert type. SOS has no toggle at all --
 * a safety-critical alert type shouldn't be something a user can
 * accidentally silence on their own device.
 */
public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "ShaloTrackNotificationPrefs";
    public static final String KEY_IGNITION = "notif_ignition";
    public static final String KEY_OVERSPEED = "notif_overspeed";
    public static final String KEY_POWER_CUT = "notif_power_cut";
    public static final String KEY_LOW_BATTERY = "notif_low_battery";
    public static final String KEY_DEVICE_OFFLINE = "notif_device_offline";

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        View btnBack = findViewById(R.id.btnBackSettings);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        setupToggle(R.id.switchIgnition, KEY_IGNITION);
        setupToggle(R.id.switchOverspeed, KEY_OVERSPEED);
        setupToggle(R.id.switchPowerCut, KEY_POWER_CUT);
        setupToggle(R.id.switchLowBattery, KEY_LOW_BATTERY);
        setupToggle(R.id.switchDeviceOffline, KEY_DEVICE_OFFLINE);

        View rowEmergencyContacts = findViewById(R.id.rowEmergencyContacts);
        if (rowEmergencyContacts != null) {
            rowEmergencyContacts.setOnClickListener(v ->
                    startActivity(new Intent(SettingsActivity.this, EmergencyContactsActivity.class)));
        }

        TextView tvVersion = findViewById(R.id.tvAppVersion);
        if (tvVersion != null) {
            tvVersion.setText("Version " + getAppVersionName());
        }
    }

    // All five default to ON -- a fresh install shouldn't silently miss
    // real alerts because a preference happened to default off.
    private void setupToggle(int switchId, String prefKey) {
        SwitchCompat toggle = findViewById(switchId);
        if (toggle == null) return;
        toggle.setChecked(prefs.getBoolean(prefKey, true));
        toggle.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(prefKey, isChecked).apply());
    }

    // Read from the real package info, not hardcoded, so this can never
    // silently drift out of date with an actual release.
    private String getAppVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName != null ? info.versionName : "unknown";
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }
}