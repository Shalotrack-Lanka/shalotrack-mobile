package com.example.letstracklanka.ui.geofences;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.GeofenceResponse;
import com.example.letstracklanka.ui.common.Plates;

import java.util.Locale;

/** Wording for geofences, shared by the list and the edit sheet. */
final class GeofenceText {

    private GeofenceText() {}

    /** "200 m", "1.5 km", "12 km". */
    static String radius(int meters) {
        if (meters < 1000) return String.format(Locale.getDefault(), "%d m", meters);
        double km = meters / 1000.0;
        return km == Math.rint(km) || km >= 10
                ? String.format(Locale.getDefault(), "%.0f km", km)
                : String.format(Locale.getDefault(), "%.1f km", km);
    }

    static String alerts(@NonNull Context c, boolean enter, boolean exit) {
        if (enter && exit) return c.getString(R.string.st_geofence_alerts_both);
        if (enter) return c.getString(R.string.st_geofence_alerts_enter);
        if (exit) return c.getString(R.string.st_geofence_alerts_exit);
        return c.getString(R.string.st_geofence_alerts_none);
    }

    static CharSequence scope(@NonNull Context c, @NonNull GeofenceResponse g) {
        if (g.getVehicleId() == null) return c.getString(R.string.st_geofence_scope_all);
        String plate = g.getVehicleNumber();
        return plate != null && !plate.trim().isEmpty() ? Plates.plain(plate) : c.getString(R.string.st_geofence_scope_one);
    }

    /** "200 m · All my vehicles · Arrive and leave", plus "Set by the owner" / "Off". */
    static String caption(@NonNull Context c, @NonNull GeofenceResponse g) {
        StringBuilder sb = new StringBuilder()
                .append(radius(g.getRadiusMeters())).append(" · ")
                .append(scope(c, g)).append(" · ")
                .append(alerts(c, g.isAlertOnEnter(), g.isAlertOnExit()));
        if (!g.isOwner()) sb.append(" · ").append(c.getString(R.string.st_geofence_set_by_owner));
        else if (!g.isActive()) sb.append(" · ").append(c.getString(R.string.st_geofence_off));
        return sb.toString();
    }
}
