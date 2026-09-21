package com.example.letstracklanka.ui.main.reports;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.DailyStatResponse;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * NEW -- KM Report's day-by-day detail list. One row per calendar day in
 * the picked range (server-side DailyBreakdown, same data ValueActivity's
 * bar chart already consumes) -- this is the "detailed" view the chart
 * alone couldn't give: exact per-day distance/trips/stops/speed/ignition
 * time, not just a bar height.
 */
public class KmReportDailyAdapter extends RecyclerView.Adapter<KmReportDailyAdapter.DayViewHolder> {

    private final List<DailyStatResponse> days = new ArrayList<>();
    private final SimpleDateFormat isoParser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("EEE, d MMM yyyy", Locale.US);

    public void updateDays(List<DailyStatResponse> newDays) {
        days.clear();
        if (newDays != null) days.addAll(newDays);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_km_report_day, parent, false);
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        DailyStatResponse day = days.get(position);

        holder.tvDate.setText(formatDate(day.getDate()));
        holder.tvDistance.setText(String.format(Locale.US, "%.1f km", day.getDistanceKm()));
        holder.tvTrips.setText(day.getTripCount() + (day.getTripCount() == 1 ? " trip" : " trips"));
        holder.tvStops.setText(day.getStopCount() + (day.getStopCount() == 1 ? " stop" : " stops"));
        holder.tvSpeed.setText(String.format(Locale.US, "Avg %.0f km/h · Max %.0f km/h", day.getAverageSpeed(), day.getMaxSpeed()));
        holder.tvIgnitionOn.setText(formatMinutes(day.getIgnitionOnMinutes()));
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    private String formatDate(String iso) {
        if (iso == null) return "--";
        try {
            java.util.Date d = isoParser.parse(iso.length() >= 10 ? iso.substring(0, 10) : iso);
            return d != null ? displayFormat.format(d) : iso;
        } catch (ParseException e) {
            return iso;
        }
    }

    private String formatMinutes(double totalMinutes) {
        int hours = (int) (totalMinutes / 60);
        int minutes = (int) (totalMinutes % 60);
        if (hours == 0) return minutes + "m ignition on";
        return hours + "h " + minutes + "m ignition on";
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDate, tvDistance, tvTrips, tvStops, tvSpeed, tvIgnitionOn;

        DayViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvKmDayDate);
            tvDistance = itemView.findViewById(R.id.tvKmDayDistance);
            tvTrips = itemView.findViewById(R.id.tvKmDayTrips);
            tvStops = itemView.findViewById(R.id.tvKmDayStops);
            tvSpeed = itemView.findViewById(R.id.tvKmDaySpeed);
            tvIgnitionOn = itemView.findViewById(R.id.tvKmDayIgnitionOn);
        }
    }
}