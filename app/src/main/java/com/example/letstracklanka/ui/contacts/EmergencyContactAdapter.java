package com.example.letstracklanka.ui.contacts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.EmergencyContactResponse;

import java.util.ArrayList;
import java.util.List;

public class EmergencyContactAdapter extends RecyclerView.Adapter<EmergencyContactAdapter.ContactViewHolder> {

    public interface OnDeleteClickListener {
        void onDeleteClick(EmergencyContactResponse contact);
    }

    private List<EmergencyContactResponse> contacts = new ArrayList<>();
    private final OnDeleteClickListener deleteListener;

    public EmergencyContactAdapter(OnDeleteClickListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    public void updateContacts(List<EmergencyContactResponse> newContacts) {
        this.contacts = newContacts != null ? newContacts : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emergency_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        EmergencyContactResponse contact = contacts.get(position);

        holder.tvName.setText(contact.getName());
        holder.tvPhone.setText(contact.getPhoneNumber());
        holder.tvInitials.setText(computeInitials(contact.getName()));

        if (contact.getRelationship() != null && !contact.getRelationship().trim().isEmpty()) {
            holder.tvRelationship.setText(contact.getRelationship());
            holder.tvRelationship.setVisibility(View.VISIBLE);
        } else {
            holder.tvRelationship.setVisibility(View.GONE);
        }

        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDeleteClick(contact);
        });
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    // First letter of the first two real words (e.g. "Sister Malsha" ->
    // "SM"), or the first two letters if there's only one word (e.g.
    // "Dad" -> "DA"). Filters out empty tokens from stray punctuation
    // like a dash-separated name ("Sister - Malsha").
    private static String computeInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] words = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (String word : words) {
            String cleaned = word.replaceAll("[^a-zA-Z]", "");
            if (!cleaned.isEmpty()) {
                initials.append(Character.toUpperCase(cleaned.charAt(0)));
                if (initials.length() == 2) break;
            }
        }
        if (initials.length() == 1 && words[0].replaceAll("[^a-zA-Z]", "").length() > 1) {
            String firstWordCleaned = words[0].replaceAll("[^a-zA-Z]", "");
            initials.append(Character.toUpperCase(firstWordCleaned.charAt(1)));
        }
        return initials.length() > 0 ? initials.toString() : "?";
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvPhone, tvRelationship, tvInitials;
        ImageView btnDelete;

        ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvContactName);
            tvPhone = itemView.findViewById(R.id.tvContactPhone);
            tvRelationship = itemView.findViewById(R.id.tvContactRelationship);
            tvInitials = itemView.findViewById(R.id.tvContactInitials);
            btnDelete = itemView.findViewById(R.id.btnDeleteContact);
        }
    }
}