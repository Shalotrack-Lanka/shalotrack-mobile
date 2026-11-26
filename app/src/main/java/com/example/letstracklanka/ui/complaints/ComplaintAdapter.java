package com.example.letstracklanka.ui.complaints;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.ComplaintResponse;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.ui.common.TimeText;

import java.util.ArrayList;
import java.util.List;

/** Complaint cards: category and state, vehicle, the first lines, when it was opened and replies. */
public class ComplaintAdapter extends RecyclerView.Adapter<ComplaintAdapter.ComplaintViewHolder> {

    public interface OnComplaintClickListener {
        void onComplaintClick(ComplaintResponse complaint);
    }

    private List<ComplaintResponse> complaints = new ArrayList<>();
    private final OnComplaintClickListener listener;

    public ComplaintAdapter(OnComplaintClickListener listener) {
        this.listener = listener;
    }

    public void updateComplaints(List<ComplaintResponse> newComplaints) {
        this.complaints = newComplaints != null ? newComplaints : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ComplaintViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_complaint, parent, false);
        return new ComplaintViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ComplaintViewHolder holder, int position) {
        ComplaintResponse complaint = complaints.get(position);
        Context c = holder.itemView.getContext();

        String category = ComplaintText.category(c, complaint.getCategory());
        holder.tvCategory.setText(category);
        ComplaintText.status(holder.tvStatus, complaint.getStatus());
        holder.tvPlate.setText(Plates.format(c, complaint.getVehicleNumber()));
        holder.tvDescription.setText(complaint.getDescription());

        String meta = c.getString(R.string.st_complaint_opened, TimeText.date(complaint.getCreatedAt()));
        if (complaint.getStatus() == 2 && complaint.getResolvedAt() != null) {
            meta = c.getString(R.string.st_complaint_meta, meta,
                    c.getString(R.string.st_complaint_resolved_on, TimeText.date(complaint.getResolvedAt())));
        } else {
            int replies = complaint.getReplies().size();
            if (replies > 0) {
                meta = c.getString(R.string.st_complaint_meta, meta,
                        c.getResources().getQuantityString(R.plurals.st_complaint_replies, replies, replies));
            }
        }
        holder.tvMeta.setText(meta);

        holder.itemView.setContentDescription(category + ", " + holder.tvStatus.getText() + ", "
                + Plates.plain(complaint.getVehicleNumber()) + ", " + meta);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onComplaintClick(complaint);
        });
    }

    @Override
    public int getItemCount() {
        return complaints.size();
    }

    static class ComplaintViewHolder extends RecyclerView.ViewHolder {
        final TextView tvCategory, tvStatus, tvPlate, tvDescription, tvMeta;

        ComplaintViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tvComplaintCategory);
            tvStatus = itemView.findViewById(R.id.tvComplaintStatus);
            tvPlate = itemView.findViewById(R.id.tvComplaintPlate);
            tvDescription = itemView.findViewById(R.id.tvComplaintDescription);
            tvMeta = itemView.findViewById(R.id.tvComplaintMeta);
        }
    }
}
