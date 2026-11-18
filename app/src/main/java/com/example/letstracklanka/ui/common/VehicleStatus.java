package com.example.letstracklanka.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.DashboardVehicle;

/**
 * The ONE place that decides a vehicle's status and how it looks. Every screen (map, lists, detail, alerts)
 * uses this, so a vehicle can never be "Moving" on one screen and "Offline" on another.
 *
 * Rules (same as the Fleet portal):
 *   NO_DEVICE  no tracker linked to the vehicle
 *   OFFLINE    tracker linked but not connected to the gateway (the API's "online" flag, which is
 *              gateway IsOnline + a heartbeat/position within 30 minutes)
 *   MOVING     connected and faster than 7 km/h (below that is GPS drift, not movement)
 *   IDLING     connected, engine on, standing still
 *   PARKED     connected, engine off
 */
public enum VehicleStatus {

    MOVING(R.string.st_status_moving, R.color.st_status_moving, R.color.st_status_moving_text, R.color.st_status_moving_bg),
    IDLING(R.string.st_status_idling, R.color.st_status_idling, R.color.st_status_idling_text, R.color.st_status_idling_bg),
    PARKED(R.string.st_status_parked, R.color.st_status_parked, R.color.st_status_parked_text, R.color.st_status_parked_bg),
    OFFLINE(R.string.st_status_offline, R.color.st_status_offline, R.color.st_status_offline_text, R.color.st_status_offline_bg),
    NO_DEVICE(R.string.st_status_no_device, R.color.st_status_nodevice, R.color.st_status_nodevice_text, R.color.st_status_nodevice_bg);

    /** GPS drift on a parked vehicle can report a few km/h; anything at or under this is "not moving". */
    public static final double MOVING_THRESHOLD_KMH = 7.0;

    @StringRes public final int label;
    @ColorRes public final int dot;
    @ColorRes public final int text;
    @ColorRes public final int background;

    VehicleStatus(@StringRes int label, @ColorRes int dot, @ColorRes int text, @ColorRes int background) {
        this.label = label;
        this.dot = dot;
        this.text = text;
        this.background = background;
    }

    public static VehicleStatus of(DashboardVehicle v) {
        if (v == null || !v.hasDevice()) return NO_DEVICE;
        if (!v.isOnline()) return OFFLINE;
        if (v.getSpeed() > MOVING_THRESHOLD_KMH) return MOVING;
        return v.isIgnitionOn() ? IDLING : PARKED;
    }

    /** For a live position push (no online flag in the payload: a push means the tracker is connected). */
    public static VehicleStatus ofLive(double speedKmh, boolean ignitionOn) {
        if (speedKmh > MOVING_THRESHOLD_KMH) return MOVING;
        return ignitionOn ? IDLING : PARKED;
    }

    public boolean isReporting() {
        return this != OFFLINE && this != NO_DEVICE;
    }

    /** Styles a TextView that uses Widget.ST.StatusPill: label, colours and the leading dot. */
    public void applyToPill(TextView pill) {
        Context c = pill.getContext();
        pill.setText(label);
        pill.setTextColor(ContextCompat.getColor(c, text));
        pill.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, background)));
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(dotDrawable(c, 7), null, null, null);
    }

    /** A small filled circle in this status colour (for pills, chips and list markers). */
    public GradientDrawable dotDrawable(Context c, int sizeDp) {
        int px = Math.round(sizeDp * c.getResources().getDisplayMetrics().density);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(ContextCompat.getColor(c, dot));
        d.setSize(px, px);
        return d;
    }

    public int dotColor(Context c) {
        return ContextCompat.getColor(c, dot);
    }
}
