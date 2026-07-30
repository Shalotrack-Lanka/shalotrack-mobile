package com.example.letstracklanka.ui.vehicles;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.DashboardVehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VehicleListAdapter extends RecyclerView.Adapter<VehicleListAdapter.VehicleViewHolder> {

    // Same threshold as Home/Vehicles -- GPS drift/multipath can report a
    // small non-zero speed even when genuinely stationary.
    private static final double MOVEMENT_SPEED_THRESHOLD_KMH = 7.0;
    private static final int SWIPE_REVEAL_WIDTH_DP = 84;

    private static final int COLOR_MOVING = 0xFF16A34A;
    private static final int COLOR_IDLE_PARKED = 0xFF0A2463;
    private static final int COLOR_NO_DEVICE = 0xFFE53935;   // red -- nothing assigned at all
    private static final int COLOR_OFFLINE = 0xFFF59E0B;     // amber -- device assigned but not currently reporting

    public interface OnVehicleClickListener {
        void onVehicleClick(DashboardVehicle vehicle);
    }

    public interface OnRemoveClickListener {
        void onRemoveClick(DashboardVehicle vehicle);
    }

    // "vehicles" is what's currently DISPLAYED (may be search-filtered).
    // "allVehicles" is the full, unfiltered set -- needed so filter() can
    // re-derive the displayed list without a fresh network fetch each
    // keystroke.
    private List<DashboardVehicle> vehicles;
    private List<DashboardVehicle> allVehicles;
    private final OnVehicleClickListener clickListener;
    private final OnRemoveClickListener removeListener;

    // Only one row's swipe reveal stays open at a time (standard swipe-
    // action UX). Tracked here at the adapter level since ViewHolders don't
    // know about each other directly.
    private VehicleRowSwipeController currentlyOpenController = null;

    public VehicleListAdapter(List<DashboardVehicle> vehicles, OnVehicleClickListener clickListener, OnRemoveClickListener removeListener) {
        this.vehicles = vehicles;
        this.allVehicles = vehicles;
        this.clickListener = clickListener;
        this.removeListener = removeListener;
    }

    public void updateVehicles(List<DashboardVehicle> newVehicles) {
        this.allVehicles = newVehicles;
        this.vehicles = newVehicles;
        notifyDataSetChanged();
    }

    /** Client-side filter by make, model, or plate number -- cheap, no
     * network call, matches the search bar shown in the Vehicles tab. */
    public void filter(String query) {
        if (query == null || query.trim().isEmpty()) {
            this.vehicles = allVehicles;
        } else {
            String q = query.trim().toLowerCase(Locale.getDefault());
            List<DashboardVehicle> filtered = new ArrayList<>();
            if (allVehicles != null) {
                for (DashboardVehicle v : allVehicles) {
                    String make = v.getMake() != null ? v.getMake() : "";
                    String model = v.getModel() != null ? v.getModel() : "";
                    String plate = v.getVehicleNumber() != null ? v.getVehicleNumber() : "";
                    String haystack = (make + " " + model + " " + plate).toLowerCase(Locale.getDefault());
                    if (haystack.contains(q)) filtered.add(v);
                }
            }
            this.vehicles = filtered;
        }
        notifyDataSetChanged();
    }

    /** Current, unfiltered vehicle list -- used by VehiclesActivity to
     * compute the Moving/Idle/Parked/Offline summary counts, which must
     * reflect ALL vehicles regardless of any active search filter. */
    public List<DashboardVehicle> getAllVehicles() {
        return allVehicles;
    }

    @NonNull
    @Override
    public VehicleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vehicle_list, parent, false);
        VehicleViewHolder holder = new VehicleViewHolder(view);

        // Wired here (not in the ViewHolder constructor) because the
        // single-open-at-a-time tracker needs access to this adapter
        // instance's currentlyOpenController field, and VehicleViewHolder
        // is a static nested class with no implicit outer-instance access.
        holder.swipeController = new VehicleRowSwipeController(
                holder.foregroundRow,
                SWIPE_REVEAL_WIDTH_DP,
                opening -> {
                    if (currentlyOpenController != null && currentlyOpenController != opening) {
                        currentlyOpenController.close();
                    }
                    currentlyOpenController = opening;
                });
        holder.foregroundRow.setOnTouchListener(holder.swipeController);

        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull VehicleViewHolder holder, int position) {
        DashboardVehicle vehicle = vehicles.get(position);

        // FIX: RecyclerView recycles ViewHolders -- an instant, non-animated
        // reset here (not close(), which animates) is correct on rebind:
        // this row now represents a different vehicle, so there's no user
        // action being "closed", just a clean slate.
        if (currentlyOpenController == holder.swipeController) {
            currentlyOpenController = null;
        }
        holder.swipeController.resetImmediate();

        holder.tvName.setText((vehicle.getMake() + " " + vehicle.getModel()).trim());

        String statusText;
        int statusColor;
        String gpsLinkText;

        if (!vehicle.hasDevice()) {
            statusText = "No Device";
            statusColor = COLOR_NO_DEVICE;
            gpsLinkText = "GPS Device Not Linked";
        } else if (!vehicle.isOnline()) {
            statusText = "Offline";
            statusColor = COLOR_OFFLINE;
            gpsLinkText = "GPS Device Linked (Offline)";
        } else if (vehicle.getSpeed() > MOVEMENT_SPEED_THRESHOLD_KMH) {
            statusText = "Moving " + (int) vehicle.getSpeed() + " km/h";
            statusColor = COLOR_MOVING;
            gpsLinkText = "GPS Device Linked";
        } else {
            statusText = vehicle.isIgnitionOn() ? "Idle" : "Parked";
            statusColor = COLOR_IDLE_PARKED;
            gpsLinkText = "GPS Device Linked";
        }

        holder.tvStatus.setText(statusText);
        holder.tvStatus.setTextColor(statusColor);

        // NOT a "last seen" timestamp -- DashboardVehicle carries no
        // timestamp field (checked the actual model, not guessed). Using
        // plate + GPS-link status instead, which we do have real data for.
        String plate = vehicle.getVehicleNumber() != null ? vehicle.getVehicleNumber() : "";
        holder.tvCaption.setText(plate + " \u2022 " + gpsLinkText);

        holder.foregroundRow.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onVehicleClick(vehicle);
        });

        holder.btnSwipeRemove.setOnClickListener(v -> {
            holder.swipeController.close();
            if (removeListener != null) removeListener.onRemoveClick(vehicle);
        });
    }

    @Override
    public int getItemCount() {
        return vehicles == null ? 0 : vehicles.size();
    }

    static class VehicleViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvStatus, tvCaption;
        ImageView imgFavorite;

        // Package-visible (not private): the adapter needs direct access
        // both to attach the touch listener at creation time and to close
        // an open row from the Remove click handler.
        View foregroundRow;
        View btnSwipeRemove;
        VehicleRowSwipeController swipeController;

        VehicleViewHolder(@NonNull View itemView) {
            super(itemView);
            foregroundRow = itemView.findViewById(R.id.layoutVehicleListForeground);
            btnSwipeRemove = itemView.findViewById(R.id.btnVehicleListSwipeRemove);

            tvName = itemView.findViewById(R.id.tvVehicleListName);
            tvStatus = itemView.findViewById(R.id.tvVehicleListStatus);
            tvCaption = itemView.findViewById(R.id.tvVehicleListCaption);
            imgFavorite = itemView.findViewById(R.id.imgVehicleListFavorite);
        }
    }
}