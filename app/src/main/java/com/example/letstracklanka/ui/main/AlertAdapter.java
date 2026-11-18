package com.example.letstracklanka.ui.main;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.AlertResponse;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.ui.common.TimeText;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alert list (item_alert / item_alert_day_header): newest first, grouped by local day.
 * Each kind has one icon and one colour family, used the same way everywhere:
 *   red    safety and security (over speed, power cut, SOS)
 *   amber  warnings (low battery, tracker offline, idling)
 *   navy   information (ignition, geofence)
 * Vector icons only (the old list used emoji, which render differently on every phone).
 */
public class AlertAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_DAY = 0;
    private static final int VIEW_TYPE_ALERT = 1;

    public interface OnAlertClickListener {
        void onAlertClick(AlertResponse alert);
    }

    /** Alert kinds as the API names them (AlertType enum, sent as strings). */
    public enum Kind {
        OVERSPEED("Overspeed", R.string.st_alert_overspeed, R.drawable.ic_st_gauge, Tone.DANGER),
        POWER_CUT("PowerCut", R.string.st_alert_power_cut, R.drawable.ic_st_plug, Tone.DANGER),
        SOS("SOS", R.string.st_alert_sos, R.drawable.ic_st_sos, Tone.DANGER),
        LOW_BATTERY("LowBattery", R.string.st_alert_low_battery, R.drawable.ic_st_battery, Tone.WARNING),
        DEVICE_OFFLINE("DeviceOffline", R.string.st_alert_device_offline, R.drawable.ic_st_wifi_off, Tone.WARNING),
        IDLE("Idle", R.string.st_alert_idle, R.drawable.ic_st_clock, Tone.WARNING),
        IGNITION_ON("IgnitionOn", R.string.st_alert_ignition_on, R.drawable.ic_st_power, Tone.INFO),
        IGNITION_OFF("IgnitionOff", R.string.st_alert_ignition_off, R.drawable.ic_st_power, Tone.INFO),
        GEOFENCE_ENTER("GeofenceEnter", R.string.st_alert_geofence_enter, R.drawable.ic_st_geofence, Tone.INFO),
        GEOFENCE_EXIT("GeofenceExit", R.string.st_alert_geofence_exit, R.drawable.ic_st_geofence, Tone.INFO),
        OTHER("", R.string.st_alert_other, R.drawable.ic_st_bell, Tone.INFO);

        final String apiName;
        @StringRes final int title;
        @DrawableRes final int icon;
        final Tone tone;

        Kind(String apiName, int title, int icon, Tone tone) {
            this.apiName = apiName;
            this.title = title;
            this.icon = icon;
            this.tone = tone;
        }

        public static Kind of(String apiName) {
            if (apiName != null) for (Kind k : values()) if (k.apiName.equalsIgnoreCase(apiName)) return k;
            return OTHER;
        }
    }

    enum Tone {
        DANGER(R.color.st_danger_text, R.color.st_danger_bg),
        WARNING(R.color.st_status_offline_text, R.color.st_status_offline_bg),
        INFO(R.color.st_navy, R.color.st_navy_100);

        final int fg;
        final int bg;

        Tone(int fg, int bg) {
            this.fg = fg;
            this.bg = bg;
        }
    }

    private static final class Row {
        final String dayTitle;
        final int dayCount;
        final AlertResponse alert;

        Row(String dayTitle, int dayCount) {
            this.dayTitle = dayTitle;
            this.dayCount = dayCount;
            this.alert = null;
        }

        Row(AlertResponse alert) {
            this.dayTitle = null;
            this.dayCount = 0;
            this.alert = alert;
        }
    }

    private final OnAlertClickListener listener;
    private List<Row> rows = new ArrayList<>();

    public AlertAdapter(List<AlertResponse> alerts, OnAlertClickListener listener) {
        this.listener = listener;
    }

    public void updateAlerts(Context context, List<AlertResponse> alerts) {
        List<AlertResponse> sorted = new ArrayList<>();
        if (alerts != null) for (AlertResponse a : alerts) if (a != null && TimeText.parse(a.getTriggeredAt()) != null) sorted.add(a);
        Collections.sort(sorted, (a, b) -> {
            Instant ia = TimeText.parse(a.getTriggeredAt());
            Instant ib = TimeText.parse(b.getTriggeredAt());
            return ib.compareTo(ia);
        });
        Map<LocalDate, List<AlertResponse>> byDay = new LinkedHashMap<>();
        for (AlertResponse a : sorted) {
            LocalDate day = TimeText.localDay(a.getTriggeredAt());
            List<AlertResponse> l = byDay.get(day);
            if (l == null) {
                l = new ArrayList<>();
                byDay.put(day, l);
            }
            l.add(a);
        }
        List<Row> out = new ArrayList<>();
        for (Map.Entry<LocalDate, List<AlertResponse>> e : byDay.entrySet()) {
            out.add(new Row(TimeText.dayHeader(context, e.getKey()), e.getValue().size()));
            for (AlertResponse a : e.getValue()) out.add(new Row(a));
        }
        rows = out;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).alert == null ? VIEW_TYPE_DAY : VIEW_TYPE_ALERT;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_DAY) return new DayHolder(inf.inflate(R.layout.item_alert_day_header, parent, false));
        return new AlertHolder(inf.inflate(R.layout.item_alert, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        Context c = holder.itemView.getContext();
        if (holder instanceof DayHolder) {
            DayHolder h = (DayHolder) holder;
            h.title.setText(row.dayTitle);
            h.count.setText(c.getResources().getQuantityString(R.plurals.st_alerts_count, row.dayCount, row.dayCount));
            return;
        }
        AlertHolder h = (AlertHolder) holder;
        AlertResponse a = row.alert;
        Kind kind = Kind.of(a.getAlertType());

        h.icon.setImageResource(kind.icon);
        h.icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(c, kind.tone.fg)));
        h.tile.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, kind.tone.bg)));
        h.type.setText(kind.title);
        h.type.setTextColor(ContextCompat.getColor(c, a.isRead() ? R.color.st_text_2 : R.color.st_text_1));
        String msg = a.getMessage();
        h.message.setText(msg != null ? msg : "");
        h.message.setVisibility(msg == null || msg.trim().isEmpty() ? View.GONE : View.VISIBLE);
        if (a.getVehicleNumber() != null && !a.getVehicleNumber().trim().isEmpty()) {
            h.vehicle.setText(Plates.format(c, a.getVehicleNumber()));
            h.vehicle.setVisibility(View.VISIBLE);
        } else {
            h.vehicle.setVisibility(View.GONE);
        }
        h.time.setText(TimeText.clock(a.getTriggeredAt()));
        h.unread.setVisibility(a.isRead() ? View.GONE : View.VISIBLE);

        h.itemView.setContentDescription(c.getString(a.isRead() ? R.string.st_alert_row_read : R.string.st_alert_row_unread,
                c.getString(kind.title), Plates.plain(a.getVehicleNumber()), TimeText.clock(a.getTriggeredAt()),
                msg != null ? msg : ""));
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAlertClick(a);
        });
    }

    static final class DayHolder extends RecyclerView.ViewHolder {
        final TextView title, count;

        DayHolder(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tvAlertDayTitle);
            count = v.findViewById(R.id.tvAlertDayCount);
        }
    }

    static final class AlertHolder extends RecyclerView.ViewHolder {
        final View tile, unread;
        final ImageView icon;
        final TextView type, message, vehicle, time;

        AlertHolder(@NonNull View v) {
            super(v);
            tile = v.findViewById(R.id.alertIconTile);
            icon = v.findViewById(R.id.imgAlertIcon);
            type = v.findViewById(R.id.tvAlertType);
            message = v.findViewById(R.id.tvAlertMessage);
            vehicle = v.findViewById(R.id.tvAlertVehicle);
            time = v.findViewById(R.id.tvAlertTime);
            unread = v.findViewById(R.id.dotUnread);
        }
    }
}
