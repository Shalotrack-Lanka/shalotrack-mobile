package com.example.letstracklanka.ui.main;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.AlertResponse;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.AlertViewHolder> {

    public interface OnAlertClickListener {
        void onAlertClick(AlertResponse alert);
    }

    private List<AlertResponse> alerts;
    private final OnAlertClickListener listener;

    public AlertAdapter(List<AlertResponse> alerts, OnAlertClickListener listener) {
        this.alerts = alerts;
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateAlerts(List<AlertResponse> newAlerts) {
        this.alerts = newAlerts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AlertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alert, parent, false);
        return new AlertViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlertViewHolder holder, int position) {
        AlertResponse alert = alerts.get(position);

        holder.tvAlertType.setText(formatAlertType(alert.getAlertType()));
        holder.tvAlertMessage.setText(alert.getMessage());
        holder.tvAlertVehicle.setText(alert.getVehicleNumber());
        holder.tvAlertTime.setText(formatTime(alert.getTriggeredAt()));

        // Unread dot -- shape/color set in code, see item_alert.xml note.
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(alert.isRead() ? Color.TRANSPARENT : Color.parseColor("#E53935"));
        holder.dotUnread.setBackground(dot);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAlertClick(alert);
        });
    }

    @Override
    public int getItemCount() {
        return alerts == null ? 0 : alerts.size();
    }

    private String formatAlertType(String rawType) {
        if (rawType == null) return "Alert";
        // "IgnitionOn" -> "Ignition On" (simple camelCase splitter, no new deps)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rawType.length(); i++) {
            char c = rawType.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) sb.append(' ');
            sb.append(c);
        }
        return sb.toString();
    }

    private String formatTime(String isoUtc) {
        if (isoUtc == null) return "";
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            // API timestamps may include fractional seconds; trim to match the parser.
            String trimmed = isoUtc.length() > 19 ? isoUtc.substring(0, 19) : isoUtc;
            Date date = parser.parse(trimmed);
            SimpleDateFormat display = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            display.setTimeZone(TimeZone.getDefault());
            return date != null ? display.format(date) : "";
        } catch (ParseException e) {
            return "";
        }
    }

    static class AlertViewHolder extends RecyclerView.ViewHolder {
        View dotUnread;
        TextView tvAlertType, tvAlertMessage, tvAlertVehicle, tvAlertTime;

        AlertViewHolder(@NonNull View itemView) {
            super(itemView);
            dotUnread = itemView.findViewById(R.id.dotUnread);
            tvAlertType = itemView.findViewById(R.id.tvAlertType);
            tvAlertMessage = itemView.findViewById(R.id.tvAlertMessage);
            tvAlertVehicle = itemView.findViewById(R.id.tvAlertVehicle);
            tvAlertTime = itemView.findViewById(R.id.tvAlertTime);
        }
    }
}