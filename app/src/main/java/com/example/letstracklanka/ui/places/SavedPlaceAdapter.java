package com.example.letstracklanka.ui.places;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.SavedPlaceResponse;
import com.example.letstracklanka.ui.common.TimeText;
import com.example.letstracklanka.ui.main.AddressResolver;

import java.util.ArrayList;
import java.util.List;

/** Saved places in one grouped card: pin tile, name, address, visits, remove. */
public class SavedPlaceAdapter extends RecyclerView.Adapter<SavedPlaceAdapter.PlaceViewHolder> {

    public interface OnDeleteClickListener {
        void onDeleteClick(SavedPlaceResponse place);
    }

    private List<SavedPlaceResponse> places = new ArrayList<>();
    private final OnDeleteClickListener deleteListener;

    public SavedPlaceAdapter(OnDeleteClickListener deleteListener) {
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
        Context c = holder.itemView.getContext();
        String name = place.getName() != null ? place.getName() : "";

        holder.tvPlaceName.setText(name);
        holder.tvPlaceAddress.setText(R.string.st_places_resolving);

        // The address arrives later; the tag stops a recycled row from showing another place's address.
        String id = place.getPlaceId();
        holder.itemView.setTag(id);
        AddressResolver.resolveCached(c, place.getLatitude(), place.getLongitude(), address -> {
            if (id != null && id.equals(holder.itemView.getTag())) {
                holder.tvPlaceAddress.setText(address);
                holder.textBlock.setContentDescription(name + ", " + address + ", " + holder.tvVisitSummary.getText());
            }
        });

        int visits = place.getVisitCount();
        String visitText = visits <= 0
                ? c.getString(R.string.st_places_no_visits)
                : c.getResources().getQuantityString(R.plurals.st_places_visits, visits, visits);
        String last = place.getLastVisitedAt();
        holder.tvVisitSummary.setText(visits > 0 && last != null && !last.trim().isEmpty()
                ? c.getString(R.string.st_places_visits_last, visitText, TimeText.ago(c, last))
                : visitText);
        holder.textBlock.setContentDescription(name + ", " + holder.tvVisitSummary.getText());

        holder.btnDeletePlace.setContentDescription(c.getString(R.string.st_places_remove, name));
        holder.btnDeletePlace.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDeleteClick(place);
        });
    }

    @Override
    public int getItemCount() {
        return places.size();
    }

    static class PlaceViewHolder extends RecyclerView.ViewHolder {
        final TextView tvPlaceName, tvPlaceAddress, tvVisitSummary;
        final View textBlock, btnDeletePlace;

        PlaceViewHolder(@NonNull View itemView) {
            super(itemView);
            textBlock = itemView.findViewById(R.id.placeText);
            tvPlaceName = itemView.findViewById(R.id.tvPlaceName);
            tvPlaceAddress = itemView.findViewById(R.id.tvPlaceAddress);
            tvVisitSummary = itemView.findViewById(R.id.tvVisitSummary);
            btnDeletePlace = itemView.findViewById(R.id.btnDeletePlace);
        }
    }
}
