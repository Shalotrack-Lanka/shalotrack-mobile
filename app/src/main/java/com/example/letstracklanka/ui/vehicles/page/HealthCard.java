package com.example.letstracklanka.ui.vehicles.page;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.letstracklanka.R;
import com.google.gson.JsonObject;

/** Fills the vehicle page's health card (healthCard in activity_vehicles) from the tracker status. */
public final class HealthCard {

    private final View root;
    private final ImageView icon;
    private final TextView summary, footer;
    private final ViewGroup rows;

    public HealthCard(@NonNull View root) {
        this.root = root;
        icon = root.findViewById(R.id.imgHealth);
        summary = root.findViewById(R.id.tvHealthSummary);
        footer = root.findViewById(R.id.tvHealthFooter);
        rows = root.findViewById(R.id.healthRows);
    }

    public void show(@NonNull Context c, @NonNull JsonObject status) {
        VehicleHealth h = VehicleHealth.from(c, status);
        summary.setText(h.headline);
        summary.setTextColor(ContextCompat.getColor(c, h.overall == VehicleHealth.Level.OK ? R.color.st_text_1 : h.overall.text));
        icon.setImageResource(h.overall == VehicleHealth.Level.OK ? R.drawable.ic_st_shield : R.drawable.ic_st_alert_circle);
        icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(c, h.overall.dot)));

        rows.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(c);
        for (VehicleHealth.Fact f : h.facts) {
            View row = inf.inflate(R.layout.item_kv_row, rows, false);
            ((TextView) row.findViewById(R.id.tvKvLabel)).setText(f.label);
            TextView value = row.findViewById(R.id.tvKvValue);
            value.setText(f.value);
            value.setTextColor(ContextCompat.getColor(c, f.level == VehicleHealth.Level.INFO || f.level == VehicleHealth.Level.OK
                    ? R.color.st_text_1 : f.level.text));
            View dot = row.findViewById(R.id.kvDot);
            if (f.level != VehicleHealth.Level.INFO) {
                dot.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, f.level.dot)));
                dot.setVisibility(View.VISIBLE);
            }
            row.setContentDescription(f.label + ", " + f.value);
            rows.addView(row);
        }
        rows.setVisibility(View.VISIBLE);

        String contact = h.lastContact != null ? c.getString(R.string.st_health_last_contact, h.lastContact) : null;
        String imei = h.imeiTail != null ? c.getString(R.string.st_health_imei_tail, h.imeiTail) : null;
        String text = contact != null && imei != null ? contact + " · " + imei : contact != null ? contact : imei;
        footer.setText(text != null ? text : "");
        footer.setVisibility(text != null ? View.VISIBLE : View.GONE);
        root.setVisibility(View.VISIBLE);
    }

    public void showUnavailable(@NonNull String why) {
        Context c = root.getContext();
        summary.setText(why);
        summary.setTextColor(ContextCompat.getColor(c, R.color.st_text_2));
        icon.setImageResource(R.drawable.ic_st_info);
        icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(c, R.color.st_text_3)));
        rows.removeAllViews();
        rows.setVisibility(View.GONE);
        footer.setVisibility(View.GONE);
    }
}
