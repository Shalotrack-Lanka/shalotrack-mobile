package com.example.letstracklanka.ui.renewals;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.RenewalResponse;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class RenewalAdapter extends RecyclerView.Adapter<RenewalAdapter.Holder> {

    public interface Actions {
        void onUploadSlip(RenewalResponse renewal);
        void onCancel(RenewalResponse renewal);
    }

    private List<RenewalResponse> items = new ArrayList<>();
    private final Actions actions;

    public RenewalAdapter(Actions actions) {
        this.actions = actions;
    }

    public void submit(List<RenewalResponse> newItems) {
        items = newItems == null ? new ArrayList<>() : newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_renewal, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        RenewalResponse r = items.get(position);
        h.tvVehicle.setText(r.getVehicleNumber() == null ? "Vehicle" : r.getVehicleNumber());
        h.tvStatus.setText(r.getStatusLabel());
        h.tvDetail.setText(r.getDurationLabel() + " renewal");

        String note = null;
        if (RenewalResponse.STATUS_REJECTED.equals(r.getStatus()) && r.getDecisionReason() != null) {
            note = r.getDecisionReason();
        } else if (RenewalResponse.STATUS_AWAITING_SLIP.equals(r.getStatus())) {
            note = "Pay by bank transfer, then upload a photo of the slip.";
        } else if (RenewalResponse.STATUS_PENDING_REVIEW.equals(r.getStatus())) {
            note = "We have your slip and will confirm shortly.";
        } else if (RenewalResponse.STATUS_APPROVED.equals(r.getStatus())) {
            note = "Your subscription has been renewed.";
        }
        h.tvNote.setText(note == null ? "" : note);
        h.tvNote.setVisibility(note == null ? View.GONE : View.VISIBLE);

        boolean open = r.isOpen();
        h.btnUpload.setVisibility(open ? View.VISIBLE : View.GONE);
        h.btnUpload.setText(r.hasSlip() ? "Replace slip" : "Upload slip");
        h.btnCancel.setVisibility(open ? View.VISIBLE : View.GONE);
        h.btnUpload.setOnClickListener(v -> actions.onUploadSlip(r));
        h.btnCancel.setOnClickListener(v -> actions.onCancel(r));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView tvVehicle, tvStatus, tvDetail, tvNote;
        final MaterialButton btnUpload, btnCancel;

        Holder(@NonNull View v) {
            super(v);
            tvVehicle = v.findViewById(R.id.tvRenewalVehicle);
            tvStatus = v.findViewById(R.id.tvRenewalStatus);
            tvDetail = v.findViewById(R.id.tvRenewalDetail);
            tvNote = v.findViewById(R.id.tvRenewalNote);
            btnUpload = v.findViewById(R.id.btnRenewalUpload);
            btnCancel = v.findViewById(R.id.btnRenewalCancel);
        }
    }
}