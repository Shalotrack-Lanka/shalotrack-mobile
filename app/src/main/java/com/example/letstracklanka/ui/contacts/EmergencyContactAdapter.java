package com.example.letstracklanka.ui.contacts;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.EmergencyContactResponse;
import com.example.letstracklanka.ui.more.ProfileText;

import java.util.ArrayList;
import java.util.List;

/** Emergency contacts in one grouped card. Tapping a contact opens the dialer (never calls by itself). */
public class EmergencyContactAdapter extends RecyclerView.Adapter<EmergencyContactAdapter.ContactViewHolder> {

    public interface Listener {
        void onCall(EmergencyContactResponse contact);
        void onRemove(EmergencyContactResponse contact);
    }

    private List<EmergencyContactResponse> contacts = new ArrayList<>();
    private final Listener listener;

    public EmergencyContactAdapter(Listener listener) {
        this.listener = listener;
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
        Context c = holder.itemView.getContext();
        String name = contact.getName() != null ? contact.getName() : "";
        String phone = ProfileText.phone(contact.getPhoneNumber());
        String relationship = contact.getRelationship() != null ? contact.getRelationship().trim() : "";

        holder.tvName.setText(name);
        holder.tvInitials.setText(ProfileText.initials(name));
        holder.tvDetail.setText(relationship.isEmpty() ? phone : c.getString(R.string.st_contacts_line, phone, relationship));

        holder.call.setContentDescription(c.getString(R.string.st_sos_call_description, name, phone));
        holder.call.setOnClickListener(v -> listener.onCall(contact));
        holder.btnDelete.setContentDescription(c.getString(R.string.st_contacts_remove, name));
        holder.btnDelete.setOnClickListener(v -> listener.onRemove(contact));
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName, tvDetail, tvInitials;
        final View call, btnDelete;

        ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            call = itemView.findViewById(R.id.contactCall);
            tvName = itemView.findViewById(R.id.tvContactName);
            tvDetail = itemView.findViewById(R.id.tvContactDetail);
            tvInitials = itemView.findViewById(R.id.tvContactInitials);
            btnDelete = itemView.findViewById(R.id.btnDeleteContact);
        }
    }
}
