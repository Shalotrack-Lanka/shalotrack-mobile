package com.example.letstracklanka.ui.vehicles;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.ui.common.TimeText;
import com.example.letstracklanka.ui.common.VehicleStatus;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vehicle rows for the Vehicles tab and for "Other vehicles" on the map (layout item_vehicle_list.xml).
 * Status comes from VehicleStatus, so the colours and words match the map and every other screen.
 *
 * Public API kept from the previous version (constructor, updateVehicles, filter, getAllVehicles) so
 * VehiclesActivity still compiles unchanged; new: setStatusFilter, setOnLinkTrackerListener, setExcludedVehicleId.
 */
public class VehicleListAdapter extends RecyclerView.Adapter<VehicleListAdapter.VehicleViewHolder> {

    private static final int SWIPE_REVEAL_WIDTH_DP = 84;

    /** Which statuses the Vehicles tab filter chips show. null = all. */
    public enum StatusFilter { MOVING, STOPPED, OFFLINE, NO_DEVICE }

    public interface OnVehicleClickListener {
        void onVehicleClick(DashboardVehicle vehicle);
    }

    public interface OnRemoveClickListener {
        void onRemoveClick(DashboardVehicle vehicle);
    }

    public interface OnLinkTrackerListener {
        void onLinkTracker(DashboardVehicle vehicle);
    }

    private List<DashboardVehicle> allVehicles = new ArrayList<>();
    private List<DashboardVehicle> vehicles = new ArrayList<>();
    private final OnVehicleClickListener clickListener;
    private final OnRemoveClickListener removeListener;
    @Nullable private OnLinkTrackerListener linkListener;

    private String query = "";
    @Nullable private StatusFilter statusFilter;
    @Nullable private String excludedVehicleId;

    // Only one row's swipe reveal stays open at a time.
    private VehicleRowSwipeController currentlyOpenController = null;

    public VehicleListAdapter(List<DashboardVehicle> vehicles, OnVehicleClickListener clickListener,
                              OnRemoveClickListener removeListener) {
        this.clickListener = clickListener;
        this.removeListener = removeListener;
        updateVehicles(vehicles);
    }

    public void setOnLinkTrackerListener(@Nullable OnLinkTrackerListener listener) {
        this.linkListener = listener;
    }

    public void updateVehicles(List<DashboardVehicle> newVehicles) {
        this.allVehicles = newVehicles != null ? new ArrayList<>(newVehicles) : new ArrayList<>();
        applyFilters();
    }

    /** Client-side search by plate, make or model. No network call per keystroke. */
    public void filter(String query) {
        this.query = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        applyFilters();
    }

    public void setStatusFilter(@Nullable StatusFilter filter) {
        this.statusFilter = filter;
        applyFilters();
    }

    /** The map's "Other vehicles" list leaves out the vehicle already shown in the sheet above it. */
    public void setExcludedVehicleId(@Nullable String vehicleId) {
        this.excludedVehicleId = vehicleId;
        applyFilters();
    }

    /** The full, unfiltered list (used for the summary counts, which must ignore the search box). */
    public List<DashboardVehicle> getAllVehicles() {
        return allVehicles;
    }

    public static boolean matches(DashboardVehicle v, @Nullable StatusFilter f) {
        if (f == null) return true;
        VehicleStatus s = VehicleStatus.of(v);
        switch (f) {
            case MOVING: return s == VehicleStatus.MOVING;
            case STOPPED: return s == VehicleStatus.IDLING || s == VehicleStatus.PARKED;
            case OFFLINE: return s == VehicleStatus.OFFLINE;
            case NO_DEVICE: return s == VehicleStatus.NO_DEVICE;
            default: return true;
        }
    }

    private void applyFilters() {
        List<DashboardVehicle> out = new ArrayList<>();
        for (DashboardVehicle v : allVehicles) {
            if (v == null) continue;
            if (excludedVehicleId != null && excludedVehicleId.equalsIgnoreCase(v.getVehicleId())) continue;
            if (!matches(v, statusFilter)) continue;
            if (!query.isEmpty()) {
                String hay = ((v.getMake() != null ? v.getMake() : "") + " "
                        + (v.getModel() != null ? v.getModel() : "") + " "
                        + (v.getVehicleNumber() != null ? v.getVehicleNumber() : ""))
                        .toLowerCase(Locale.getDefault());
                String compactHay = hay.replaceAll("[\\s\\-]", "");
                String compactQuery = query.replaceAll("[\\s\\-]", "");
                if (!hay.contains(query) && !compactHay.contains(compactQuery)) continue;
            }
            out.add(v);
        }
        this.vehicles = out;
        if (currentlyOpenController != null) currentlyOpenController = null;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VehicleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vehicle_list, parent, false);
        VehicleViewHolder holder = new VehicleViewHolder(view);
        holder.swipeController = new VehicleRowSwipeController(
                holder.foregroundRow,
                SWIPE_REVEAL_WIDTH_DP,
                opening -> {
                    if (currentlyOpenController != null && currentlyOpenController != opening) {
                        currentlyOpenController.close();
                    }
                    currentlyOpenController = opening;
                });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull VehicleViewHolder h, int position) {
        DashboardVehicle v = vehicles.get(position);
        Context c = h.itemView.getContext();
        VehicleStatus status = VehicleStatus.of(v);
        boolean ownedByMe = !v.isShared() && !v.isDemo();

        if (currentlyOpenController == h.swipeController) currentlyOpenController = null;
        h.swipeController.resetImmediate();

        // Icon in a ring of the status colour. Moving gets a solid navy disc so it stands out in a list.
        h.icon.setImageResource(iconForType(v.getVehicleType()));
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        int stroke = Math.round(2.5f * c.getResources().getDisplayMetrics().density);
        if (status == VehicleStatus.NO_DEVICE) {
            float dash = 4 * c.getResources().getDisplayMetrics().density;
            ring.setStroke(stroke, status.dotColor(c), dash, dash);
        } else {
            ring.setStroke(stroke, status.dotColor(c));
        }
        if (status == VehicleStatus.MOVING) {
            ring.setColor(ContextCompat.getColor(c, R.color.st_navy));
            h.icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(c, R.color.st_surface)));
        } else {
            ring.setColor(ContextCompat.getColor(c, R.color.st_surface));
            int tint = status.isReporting() ? R.color.st_navy : R.color.st_text_4;
            h.icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(c, tint)));
        }
        h.iconRing.setBackground(ring);

        h.plate.setText(Plates.format(c, v.getVehicleNumber()));
        h.caption.setText(caption(c, v, status));

        // Right side: a "Link tracker" button for owned vehicles with no tracker, otherwise status + time.
        boolean showLink = status == VehicleStatus.NO_DEVICE && ownedByMe && linkListener != null;
        h.linkButton.setVisibility(showLink ? View.VISIBLE : View.GONE);
        h.endColumn.setVisibility(showLink ? View.GONE : View.VISIBLE);
        if (showLink) {
            h.linkButton.setOnClickListener(x -> linkListener.onLinkTracker(v));
        } else {
            status.applyToPill(h.status);
            if (status == VehicleStatus.NO_DEVICE || v.getLastUpdate() == null) {
                h.time.setVisibility(View.GONE);
            } else {
                h.time.setVisibility(View.VISIBLE);
                h.time.setText(TimeText.ago(c, v.getLastUpdate()));
            }
        }

        // Note line: power cut (a real warning) or the demo vehicle explanation.
        if (v.isPowerCut() && status != VehicleStatus.NO_DEVICE) {
            showNote(h, c.getString(R.string.st_vehicle_power_cut), R.drawable.ic_st_plug_16,
                    R.color.st_danger_text, R.color.st_danger_bg);
        } else if (v.isDemo()) {
            showNote(h, c.getString(R.string.st_vehicle_demo_note), 0,
                    R.color.st_signal_text, R.color.st_signal_50);
        } else {
            h.note.setVisibility(View.GONE);
        }

        h.divider.setVisibility(position == getItemCount() - 1 ? View.GONE : View.VISIBLE);

        // Swipe-to-remove only for vehicles this customer owns (the API refuses the others anyway).
        h.swipeRemove.setVisibility(ownedByMe ? View.VISIBLE : View.GONE);
        h.foregroundRow.setOnTouchListener(ownedByMe && removeListener != null ? h.swipeController : null);
        h.swipeRemove.setOnClickListener(x -> {
            h.swipeController.close();
            if (removeListener != null) removeListener.onRemoveClick(v);
        });

        // The click sits on the foreground row itself (not a child), otherwise the child would swallow the
        // touch and the swipe-to-remove gesture on the row would never start. The inner row mirrors the
        // pressed state for its ripple (duplicateParentState in the layout).
        h.foregroundRow.setOnClickListener(x -> {
            if (clickListener != null) clickListener.onVehicleClick(v);
        });
        h.foregroundRow.setContentDescription(c.getString(R.string.st_vehicle_row_description,
                Plates.plain(v.getVehicleNumber()), c.getString(status.label)));
    }

    @Override
    public int getItemCount() {
        return vehicles == null ? 0 : vehicles.size();
    }

    private static void showNote(VehicleViewHolder h, String text, int iconRes, int textColor, int bgColor) {
        Context c = h.itemView.getContext();
        h.note.setText(text);
        h.note.setTextColor(ContextCompat.getColor(c, textColor));
        h.note.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, bgColor)));
        if (iconRes != 0) {
            h.note.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
            h.note.setCompoundDrawableTintList(ColorStateList.valueOf(ContextCompat.getColor(c, textColor)));
        } else {
            h.note.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
        h.note.setVisibility(View.VISIBLE);
    }

    private static String caption(Context c, DashboardVehicle v, VehicleStatus status) {
        String makeModel = ((v.getMake() != null ? v.getMake() : "") + " "
                + (v.getModel() != null ? v.getModel() : "")).trim();
        if (makeModel.isEmpty()) makeModel = c.getString(R.string.st_vehicle_unnamed);
        if (v.isDemo()) return c.getString(R.string.st_vehicle_caption_demo, makeModel);
        if (v.isShared()) {
            String owner = v.getOwnerName() != null && !v.getOwnerName().trim().isEmpty()
                    ? v.getOwnerName().trim() : c.getString(R.string.st_vehicle_someone);
            return c.getString(R.string.st_vehicle_caption_shared, owner);
        }
        if (status == VehicleStatus.NO_DEVICE) return c.getString(R.string.st_vehicle_caption_no_tracker, makeModel);
        if (status == VehicleStatus.MOVING) {
            return c.getString(R.string.st_vehicle_caption_speed, makeModel, Math.round(v.getSpeed()));
        }
        return makeModel;
    }

    /** Vehicle type (AddVehicleActivity: Car, SUV, Van, Truck, Bike, Tuk) to its design-system icon. */
    public static int iconForType(@Nullable String vehicleType) {
        if (vehicleType == null) return R.drawable.ic_st_car;
        switch (vehicleType.trim().toLowerCase(Locale.US)) {
            case "van": return R.drawable.ic_st_van;
            case "truck":
            case "lorry": return R.drawable.ic_st_truck;
            case "bike":
            case "motorbike": return R.drawable.ic_st_bike;
            case "tuk":
            case "tuk-tuk": return R.drawable.ic_st_tuk;
            default: return R.drawable.ic_st_car;
        }
    }

    static class VehicleViewHolder extends RecyclerView.ViewHolder {
        final View foregroundRow, clickable, iconRing, endColumn, divider, swipeRemove;
        final ImageView icon;
        final TextView plate, caption, status, time, note;
        final MaterialButton linkButton;
        VehicleRowSwipeController swipeController;

        VehicleViewHolder(@NonNull View itemView) {
            super(itemView);
            foregroundRow = itemView.findViewById(R.id.layoutVehicleListForeground);
            clickable = itemView.findViewById(R.id.vehicleRowClickable);
            iconRing = itemView.findViewById(R.id.vehicleIconRing);
            icon = itemView.findViewById(R.id.imgVehicleListIcon);
            plate = itemView.findViewById(R.id.tvVehicleListPlate);
            caption = itemView.findViewById(R.id.tvVehicleListCaption);
            status = itemView.findViewById(R.id.tvVehicleListStatus);
            time = itemView.findViewById(R.id.tvVehicleListTime);
            endColumn = itemView.findViewById(R.id.vehicleRowEnd);
            linkButton = itemView.findViewById(R.id.btnVehicleListLink);
            note = itemView.findViewById(R.id.tvVehicleListNote);
            divider = itemView.findViewById(R.id.vehicleRowDivider);
            swipeRemove = itemView.findViewById(R.id.btnVehicleListSwipeRemove);
        }
    }
}
