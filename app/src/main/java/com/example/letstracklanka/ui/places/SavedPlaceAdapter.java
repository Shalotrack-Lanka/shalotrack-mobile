package com.example.letstracklanka.ui.places;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.SavedPlaceResponse;
import com.example.letstracklanka.ui.main.AddressResolver;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class SavedPlaceAdapter extends RecyclerView.Adapter<SavedPlaceAdapter.PlaceViewHolder> {

    public interface OnDeleteClickListener {
        void onDeleteClick(SavedPlaceResponse place);
    }

    private List<SavedPlaceResponse> places = new ArrayList<>();
    private final AddressResolver addressResolver;
    private final OnDeleteClickListener deleteListener;

    public SavedPlaceAdapter(AddressResolver addressResolver, OnDeleteClickListener deleteListener) {
        this.addressResolver = addressResolver;
        this.deleteListener = deleteListener;
    }

    public void updatePlaces(List<SavedPlaceResponse> newPlaces) {
        this.places = newPlaces != null ? newPlaces : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_place, parent, false);
        return new PlaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaceViewHolder holder, int position) {
        SavedPlaceResponse place = places.get(position);

        holder.tvPlaceName.setText(place.getName());
        holder.tvPlaceAddress.setText("Resolving address...");

        // Address resolution is async -- guards against a recycled
        // ViewHolder showing a stale address for a different place if the
        // callback returns after this row has scrolled away and been
        // reused, by checking the tag still matches this exact place.
        holder.itemView.setTag(place.getPlaceId());
        addressResolver.resolveAddress(place.getLatitude(), place.getLongitude(), address -> {
            if (place.getPlaceId().equals(holder.itemView.getTag())) {
                holder.tvPlaceAddress.setText(address);
            }
        });

        int visits = place.getVisitCount();
        String visitText = visits == 1 ? "Visited once" : "Visited " + visits + " times";
        String lastVisitedText = formatLastVisited(place.getLastVisitedAt());
        holder.tvVisitSummary.setText(lastVisitedText != null ? visitText + " \u2022 " + lastVisitedText : visitText);

        holder.btnDeletePlace.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDeleteClick(place);
        });
    }

    @Override
    public int getItemCount() {
        return places.size();
    }

    // Backend sends UTC ISO-8601 timestamps (DateTime.UtcNow serialized by
    // System.Text.Json) -- parsed as UTC explicitly, not the device's
    // default timezone, then rendered as a relative "3 hours ago" style
    // string via Android's own DateUtils.
    private String formatLastVisited(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.trim().isEmpty()) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            String trimmed = isoTimestamp.length() > 19 ? isoTimestamp.substring(0, 19) : isoTimestamp;
            Date date = sdf.parse(trimmed);
            if (date == null) return null;
            return "Last visited " + DateUtils.getRelativeTimeSpanString(
                    date.getTime(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        } catch (Exception e) {
            return null;
        }
    }

    static class PlaceViewHolder extends RecyclerView.ViewHolder {
        TextView tvPlaceName, tvPlaceAddress, tvVisitSummary;
        View btnDeletePlace;

        PlaceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPlaceName = itemView.findViewById(R.id.tvPlaceName);
            tvPlaceAddress = itemView.findViewById(R.id.tvPlaceAddress);
            tvVisitSummary = itemView.findViewById(R.id.tvVisitSummary);
            btnDeletePlace = itemView.findViewById(R.id.btnDeletePlace);
        }
    }
}