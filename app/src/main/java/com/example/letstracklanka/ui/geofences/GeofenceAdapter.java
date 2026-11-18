package com.example.letstracklanka.ui.geofences;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.GeofenceResponse;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

/** Geofence list (item_geofence). The switch is the owner's on/off; others see it disabled. */
public class GeofenceAdapter extends RecyclerView.Adapter<GeofenceAdapter.Holder> {

    public interface Listener {
        void onToggle(GeofenceResponse geofence, boolean active);
        void onOpen(GeofenceResponse geofence);
    }

    private final List<GeofenceResponse> items = new ArrayList<>();
    private final Listener listener;

    public GeofenceAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<GeofenceResponse> geofences) {
        items.clear();
        if (geofences != null) items.addAll(geofences);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_geofence, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        GeofenceResponse g = items.get(position);
        Context c = h.itemView.getContext();
        boolean live = g.isActive();

        h.name.setText(g.getName());
        h.name.setTextColor(ContextCompat.getColor(c, live ? R.color.st_text_1 : R.color.st_text_2));
        h.caption.setText(GeofenceText.caption(c, g));
        h.icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(c, live ? R.color.st_navy : R.color.st_text_3)));
        h.tile.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, live ? R.color.st_navy_100 : R.color.st_surface_sunken)));

        h.toggle.setOnCheckedChangeListener(null);
        h.toggle.setChecked(live);
        h.toggle.setEnabled(g.isOwner());
        h.toggle.setContentDescription(c.getString(R.string.st_geofence_toggle_a11y, g.getName()));
        // Listener is detached above while the state is set from data, so this only fires for the user.
        h.toggle.setOnCheckedChangeListener((b, checked) -> listener.onToggle(g, checked));

        h.clickable.setContentDescription(g.getName() + ", " + GeofenceText.caption(c, g));
        h.clickable.setOnClickListener(v -> listener.onOpen(g));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View clickable, tile;
        final ImageView icon;
        final TextView name, caption;
        final MaterialSwitch toggle;

        Holder(@NonNull View v) {
            super(v);
            clickable = v.findViewById(R.id.geofenceRowClickable);
            tile = v.findViewById(R.id.geofenceIconTile);
            icon = v.findViewById(R.id.imgGeofenceIcon);
            name = v.findViewById(R.id.tvGeofenceName);
            caption = v.findViewById(R.id.tvGeofenceCaption);
            toggle = v.findViewById(R.id.switchGeofenceActive);
        }
    }
}
