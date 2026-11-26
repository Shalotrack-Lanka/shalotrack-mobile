package com.example.letstracklanka.ui.common;

import android.app.Activity;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.ui.vehicles.VehicleListActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Vehicle" field in a form (include_st_vehicle_field): shows the chosen vehicle as plate + name and
 * opens VehiclePickerSheet on tap. Starts on the vehicle selected elsewhere in the app when it is in the
 * list, else the first one.
 *
 * ownedOnly: only the customer's own vehicles (no shared, no demo), for actions only an owner may take
 * (share, complain, renew). The server refuses the others anyway; this just never offers them.
 */
public final class VehicleField {

    public interface Listener {
        void onChanged(@Nullable DashboardVehicle vehicle);
    }

    private final Activity activity;
    private final boolean ownedOnly;
    private final View box;
    private final TextView plate;
    private final TextView name;
    private final ProgressBar progress;
    @Nullable private Listener listener;
    @Nullable private DashboardVehicle chosen;
    private final List<DashboardVehicle> vehicles = new ArrayList<>();
    private boolean failed;

    public VehicleField(@NonNull Activity activity, @NonNull View root, boolean ownedOnly) {
        this.activity = activity;
        this.ownedOnly = ownedOnly;
        this.box = root.findViewById(R.id.stVehicleFieldBox);
        this.plate = root.findViewById(R.id.tvVehicleFieldPlate);
        this.name = root.findViewById(R.id.tvVehicleFieldName);
        this.progress = root.findViewById(R.id.progressVehicleField);
        box.setOnClickListener(v -> {
            if (failed) {
                load(preferredId());
                return;
            }
            VehiclePickerSheet.show(activity, false, ownedOnly, R.string.st_field_vehicle_pick,
                    R.string.st_picker_all_vehicles, this::choose);
        });
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /** Loads the list and picks preferredVehicleId (or the app's selected vehicle, or the first). */
    public void load(@Nullable String preferredVehicleId) {
        failed = false;
        progress.setVisibility(View.VISIBLE);
        plate.setVisibility(View.GONE);
        name.setText(R.string.st_field_vehicle_loading);
        VehiclePickerSheet.load(activity, new VehiclePickerSheet.LoadCallback() {
            @Override
            public void onLoaded(List<DashboardVehicle> loaded) {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                progress.setVisibility(View.GONE);
                vehicles.clear();
                for (DashboardVehicle v : loaded) {
                    if (ownedOnly && (v.isShared() || v.isDemo())) continue;
                    vehicles.add(v);
                }
                if (vehicles.isEmpty()) {
                    choose(null);
                    name.setText(R.string.st_field_vehicle_none_owned);
                    return;
                }
                DashboardVehicle pick = find(preferredVehicleId);
                if (pick == null) pick = find(VehicleListActivity.selectedVehicleId(activity));
                choose(pick != null ? pick : vehicles.get(0));
            }

            @Override
            public void onFailed(int httpCode) {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                progress.setVisibility(View.GONE);
                failed = true;
                chosen = null;
                plate.setVisibility(View.GONE);
                name.setText(R.string.st_field_vehicle_failed);
                if (listener != null) listener.onChanged(null);
            }
        });
    }

    @Nullable
    public DashboardVehicle get() {
        return chosen;
    }

    /** True once the list loaded and there is nothing to choose from. */
    public boolean hasNoVehicles() {
        return !failed && progress.getVisibility() != View.VISIBLE && vehicles.isEmpty();
    }

    public void setEnabled(boolean enabled) {
        box.setEnabled(enabled);
    }

    private void choose(@Nullable DashboardVehicle v) {
        chosen = v;
        if (v == null) {
            plate.setVisibility(View.GONE);
            name.setText(R.string.st_field_vehicle_pick);
        } else {
            plate.setText(Plates.format(activity, v.getVehicleNumber()));
            plate.setVisibility(View.VISIBLE);
            String label = (safe(v.getMake()) + " " + safe(v.getModel())).trim();
            name.setText(label);
            box.setContentDescription(activity.getString(R.string.st_field_vehicle) + ", "
                    + Plates.plain(v.getVehicleNumber()) + (label.isEmpty() ? "" : ", " + label));
        }
        if (listener != null) listener.onChanged(v);
    }

    @Nullable
    private DashboardVehicle find(@Nullable String id) {
        if (id == null) return null;
        for (DashboardVehicle v : vehicles) if (id.equals(v.getVehicleId())) return v;
        return null;
    }

    @Nullable
    private String preferredId() {
        return chosen != null ? chosen.getVehicleId() : null;
    }

    private static String safe(@Nullable String s) {
        return s == null ? "" : s;
    }
}
