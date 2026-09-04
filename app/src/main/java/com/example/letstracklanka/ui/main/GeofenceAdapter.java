package com.example.letstracklanka.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.GeofenceResponse;

import java.util.ArrayList;
import java.util.List;

public class GeofenceAdapter extends RecyclerView.Adapter<GeofenceAdapter.GeofenceViewHolder> {

    public interface OnGeofenceActionListener {
        void onToggleActive(GeofenceResponse geofence, boolean newIsActive);
        void onRowClick(GeofenceResponse geofence);
    }

    private final List<GeofenceResponse> geofences = new ArrayList<>();
    private final OnGeofenceActionListener listener;

    public GeofenceAdapter(OnGeofenceActionListener listener) {
        this.listener = listener;
    }

    public void updateGeofences(List<GeofenceResponse> newGeofences) {
        geofences.clear();
        if (newGeofences != null) geofences.addAll(newGeofences);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GeofenceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_geofence, parent, false);
        return new GeofenceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GeofenceViewHolder holder, int position) {
        GeofenceResponse geofence = geofences.get(position);

        holder.tvName.setText(geofence.getName());

        String scope = geofence.getVehicleId() != null
                ? (geofence.getVehicleNumber() != null ? geofence.getVehicleNumber() : "1 vehicle")
                : "All vehicles";
        String behavior = geofence.isAlertOnEnter() && geofence.isAlertOnExit() ? "Enter + Exit"
                : geofence.isAlertOnEnter() ? "Enter only"
                : geofence.isAlertOnExit() ? "Exit only" : "No alerts";
        holder.tvCaption.setText(geofence.getRadiusMeters() + "m radius \u2022 " + scope + " \u2022 " + behavior);

        // Real MDI-verified icon color differs slightly by scope, matching
        // the confirmed mockup's blue/red distinction between an
        // all-vehicles geofence and a vehicle-specific one.
        int iconColor = geofence.getVehicleId() != null
                ? ContextCompat.getColor(holder.itemView.getContext(), R.color.status_danger)
                : ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_accent);
        holder.imgIcon.setColorFilter(iconColor);

        // Avoid firing onToggleActive while we're just setting the
        // initial state from data, only when the user actually taps it.
        holder.switchActive.setOnCheckedChangeListener(null);
        holder.switchActive.setChecked(geofence.isActive());

        // NEW -- shared-vehicle geofences are read-only (IsOwner=false
        // from the backend), matching the same "full view access, no
        // structural changes" boundary already used everywhere else for
        // shared vehicles.
        holder.switchActive.setEnabled(geofence.isOwner());

        holder.switchActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) listener.onToggleActive(geofence, isChecked);
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onRowClick(geofence);
        });
    }

    @Override
    public int getItemCount() {
        return geofences.size();
    }

    static class GeofenceViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCaption;
        ImageView imgIcon;
        SwitchCompat switchActive;

        GeofenceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvGeofenceName);
            tvCaption = itemView.findViewById(R.id.tvGeofenceCaption);
            imgIcon = itemView.findViewById(R.id.imgGeofenceIcon);
            switchActive = itemView.findViewById(R.id.switchGeofenceActive);
        }
    }
}