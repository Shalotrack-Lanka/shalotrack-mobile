package com.example.letstracklanka.ui.main.reports;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.StopSummary;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * NEW -- Stop Alert Report. A flat, most-recent-first list, deliberately
 * NOT day-grouped like AlertAdapter: the 90-day report cap
 * (MAX_REPORT_RANGE_DAYS in ReportFilterBottomSheet, mirrored server-side
 * as MaxTripReportDays) keeps this list bounded enough that day headers
 * would be ceremony, not clarity -- adding that grouping later is a
 * strict layout-level addition if the data ever shows it's needed.
 */
public class StopReportAdapter extends RecyclerView.Adapter<StopReportAdapter.StopViewHolder> {

    private final List<StopSummary> stops = new ArrayList<>();
    private final SimpleDateFormat isoParser;
    private final SimpleDateFormat timeFormat;

    public StopReportAdapter(List<StopSummary> initial) {
        isoParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        isoParser.setTimeZone(TimeZone.getTimeZone("UTC"));
        timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        if (initial != null) stops.addAll(initial);
    }

    public void updateStops(List<StopSummary> newStops) {
        stops.clear();
        if (newStops != null) stops.addAll(newStops);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StopViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stop_report, parent, false);
        return new StopViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StopViewHolder holder, int position) {
        StopSummary stop = stops.get(position);

        double minutes = stop.getDurationMinutes();
        String durationLabel = minutes >= 60
                ? String.format(Locale.US, "Stopped for %dh %dm", (int) (minutes / 60), (int) (minutes % 60))
                : String.format(Locale.US, "Stopped for %d min", (int) minutes);
        holder.tvDuration.setText(durationLabel);

        holder.tvTimeRange.setText(formatTime(stop.getStartTime()) + " – "
                + (stop.isInProgress() ? "now" : formatTime(stop.getEndTime())));

        holder.tvCoordinates.setText(String.format(Locale.US, "%.4f, %.4f", stop.getLatitude(), stop.getLongitude()));

        holder.tvInProgress.setVisibility(stop.isInProgress() ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return stops.size();
    }

    private String formatTime(String iso) {
        if (iso == null) return "--";
        try {
            Date d = isoParser.parse(iso);
            return d != null ? timeFormat.format(d) : "--";
        } catch (Exception e) {
            return "--";
        }
    }

    static class StopViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDuration, tvTimeRange, tvCoordinates, tvInProgress;

        StopViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDuration = itemView.findViewById(R.id.tvStopDuration);
            tvTimeRange = itemView.findViewById(R.id.tvStopTimeRange);
            tvCoordinates = itemView.findViewById(R.id.tvStopCoordinates);
            tvInProgress = itemView.findViewById(R.id.tvStopInProgress);
        }
    }
}