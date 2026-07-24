package com.example.letstracklanka.ui.history;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.TripSummary;
import com.example.letstracklanka.ui.main.AddressResolver;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    public interface OnTripClickListener {
        void onTripClick(TripSummary trip);
    }

    private List<TripSummary> trips;
    private final AddressResolver addressResolver;
    private final OnTripClickListener clickListener;

    public TripAdapter(List<TripSummary> trips, AddressResolver addressResolver, OnTripClickListener clickListener) {
        this.trips = trips;
        this.addressResolver = addressResolver;
        this.clickListener = clickListener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateTrips(List<TripSummary> newTrips) {
        this.trips = newTrips;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trip, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        TripSummary trip = trips.get(position);

        holder.tvTimeRange.setText(formatTime(trip.getStartTime()) + " - " + formatTime(trip.getEndTime()));
        holder.tvDuration.setText(formatDuration(trip.getDurationMinutes()));
        holder.tvDistance.setText(String.format(Locale.getDefault(), "%.1f km", trip.getDistanceKm()));

        // Show raw coordinates immediately as a fallback, then resolve real
        // addresses asynchronously. Each TextView is tagged with the exact trip
        // object it currently represents -- when the async lookup returns, we
        // only apply the result if the tag still matches. This is what makes it
        // safe against RecyclerView recycling: if this row gets reused for a
        // different trip before the network call finishes, the stale result is
        // silently dropped instead of overwriting the wrong row.
        holder.tvStartPoint.setText(String.format(Locale.getDefault(), "%.5f, %.5f", trip.getStartLatitude(), trip.getStartLongitude()));
        holder.tvEndPoint.setText(String.format(Locale.getDefault(), "%.5f, %.5f", trip.getEndLatitude(), trip.getEndLongitude()));

        holder.tvStartPoint.setTag(trip);
        holder.tvEndPoint.setTag(trip);

        addressResolver.resolveAddress(trip.getStartLatitude(), trip.getStartLongitude(), address -> {
            if (holder.tvStartPoint.getTag() == trip) {
                holder.tvStartPoint.setText(address);
            }
        });

        addressResolver.resolveAddress(trip.getEndLatitude(), trip.getEndLongitude(), address -> {
            if (holder.tvEndPoint.getTag() == trip) {
                holder.tvEndPoint.setText(address);
            }
        });

        holder.tvInProgress.setVisibility(trip.isInProgress() ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onTripClick(trip);
        });
    }

    @Override
    public int getItemCount() {
        return trips == null ? 0 : trips.size();
    }

    private String formatTime(String isoUtc) {
        if (isoUtc == null) return "--";
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = parser.parse(isoUtc);
            SimpleDateFormat display = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            display.setTimeZone(TimeZone.getDefault());
            return date != null ? display.format(date) : isoUtc;
        } catch (ParseException e) {
            return isoUtc;
        }
    }

    private String formatDuration(double totalMinutes) {
        int hours = (int) (totalMinutes / 60);
        int minutes = (int) (totalMinutes % 60);
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    static class TripViewHolder extends RecyclerView.ViewHolder {
        TextView tvTimeRange, tvDuration, tvDistance, tvStartPoint, tvEndPoint, tvInProgress;

        TripViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTimeRange = itemView.findViewById(R.id.tvTimeRange);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            tvDistance = itemView.findViewById(R.id.tvDistance);
            tvStartPoint = itemView.findViewById(R.id.tvStartPoint);
            tvEndPoint = itemView.findViewById(R.id.tvEndPoint);
            tvInProgress = itemView.findViewById(R.id.tvInProgress);
        }
    }
}