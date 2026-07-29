package com.example.letstracklanka.ui.vehicles;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.DashboardVehicle;

import java.util.List;

public class VehicleListAdapter extends RecyclerView.Adapter<VehicleListAdapter.VehicleViewHolder> {

    // Same threshold as Home/Vehicles -- GPS drift/multipath can report a
    // small non-zero speed even when genuinely stationary.
    private static final double MOVEMENT_SPEED_THRESHOLD_KMH = 7.0;

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

    private List<DashboardVehicle> vehicles;
    private final OnVehicleClickListener clickListener;
    private final OnRemoveClickListener removeListener;

    public VehicleListAdapter(List<DashboardVehicle> vehicles, OnVehicleClickListener clickListener, OnRemoveClickListener removeListener) {
        this.vehicles = vehicles;
        this.clickListener = clickListener;
        this.removeListener = removeListener;
    }

    public void updateVehicles(List<DashboardVehicle> newVehicles) {
        this.vehicles = newVehicles;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VehicleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vehicle_list, parent, false);
        return new VehicleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VehicleViewHolder holder, int position) {
        DashboardVehicle vehicle = vehicles.get(position);

        holder.tvName.setText((vehicle.getMake() + " " + vehicle.getModel()).trim());
        holder.tvNumber.setText(vehicle.getVehicleNumber());

        String statusText;
        int statusColor;
        String imeiText;
        int imeiColor;

        if (!vehicle.hasDevice()) {
            // Genuinely nothing assigned -- the most urgent state, red.
            statusText = "No Device";
            statusColor = COLOR_NO_DEVICE;
            imeiText = "GPS Device Not Linked";
            imeiColor = COLOR_NO_DEVICE;
        } else if (!vehicle.isOnline()) {
            // A device IS assigned, but isn't currently reporting -- a
            // genuinely different problem than "no device", amber not red.
            statusText = "Offline";
            statusColor = COLOR_OFFLINE;
            imeiText = "GPS Device Linked (Offline)";
            imeiColor = COLOR_OFFLINE;
        } else if (vehicle.getSpeed() > MOVEMENT_SPEED_THRESHOLD_KMH) {
            statusText = "Moving " + (int) vehicle.getSpeed() + " km/h";
            statusColor = COLOR_MOVING;
            imeiText = "GPS Device Linked";
            imeiColor = COLOR_MOVING;
        } else {
            statusText = vehicle.isIgnitionOn() ? "Idle" : "Parked";
            statusColor = COLOR_IDLE_PARKED;
            imeiText = "GPS Device Linked";
            imeiColor = COLOR_MOVING;
        }

        holder.tvStatus.setText(statusText);
        holder.tvStatus.setTextColor(statusColor);
        holder.tvImei.setText(imeiText);
        holder.tvImei.setTextColor(imeiColor);

        // Tint the pill background to a light version of the status color,
        // and the small dot to the full status color -- gives each card a
        // real, colored status badge instead of bare text.
        Drawable pillBg = holder.badgeStatus.getBackground().mutate();
        pillBg.setTint(withAlpha(statusColor, 26));   // ~10% opacity tint for the pill fill
        holder.dotStatus.getBackground().mutate().setTint(statusColor);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onVehicleClick(vehicle);
        });

        holder.tvRemove.setOnClickListener(v -> {
            if (removeListener != null) removeListener.onRemoveClick(vehicle);
        });
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    @Override
    public int getItemCount() {
        return vehicles == null ? 0 : vehicles.size();
    }

    static class VehicleViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvNumber, tvStatus, tvImei, tvRemove;
        View badgeStatus, dotStatus;

        VehicleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvVehicleListName);
            tvNumber = itemView.findViewById(R.id.tvVehicleListNumber);
            tvStatus = itemView.findViewById(R.id.tvVehicleListStatus);
            tvImei = itemView.findViewById(R.id.tvVehicleListImei);
            tvRemove = itemView.findViewById(R.id.tvVehicleListRemove);
            badgeStatus = itemView.findViewById(R.id.badgeVehicleListStatus);
            dotStatus = itemView.findViewById(R.id.dotVehicleListStatus);
        }
    }
}