package com.example.letstracklanka.ui.complaints;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.ComplaintResponse;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ComplaintAdapter extends RecyclerView.Adapter<ComplaintAdapter.ComplaintViewHolder> {

    // Same status-ordinal-to-color mapping used on the admin side's Blade
    // views for this same feature (dealer/complaints.blade.php) -- kept in
    // lockstep with ComplaintStatus: WithDealer=0, WithAdmin=1, Resolved=2,
    // Closed=3.
    private static final int COLOR_WITH_DEALER = 0xFFF59E0B; // amber
    private static final int COLOR_WITH_ADMIN = 0xFF1976D2;  // blue
    private static final int COLOR_RESOLVED = 0xFF16A34A;    // green
    private static final int COLOR_CLOSED = 0xFF757575;      // gray

    public interface OnComplaintClickListener {
        void onComplaintClick(ComplaintResponse complaint);
    }

    private List<ComplaintResponse> complaints;
    private final OnComplaintClickListener listener;

    public ComplaintAdapter(List<ComplaintResponse> complaints, OnComplaintClickListener listener) {
        this.complaints = complaints;
        this.listener = listener;
    }

    public void updateComplaints(List<ComplaintResponse> newComplaints) {
        this.complaints = newComplaints;
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

        holder.tvCategory.setText(complaint.getCategoryLabel());
        holder.tvVehicle.setText(complaint.getVehicleLabel());
        holder.tvDescription.setText(complaint.getDescription());
        holder.tvDate.setText(formatDate(complaint.getCreatedAt()));

        int replyCount = complaint.getReplies().size();
        holder.tvReplyCount.setText(replyCount == 1 ? "1 reply" : replyCount + " replies");
        holder.tvReplyCount.setVisibility(replyCount > 0 ? View.VISIBLE : View.GONE);

        int statusColor = statusColorFor(complaint.getStatus());
        holder.tvStatus.setText(complaint.getStatusLabel());
        holder.tvStatus.setTextColor(statusColor);
        Drawable pillBg = holder.tvStatus.getBackground().mutate();
        pillBg.setTint(withAlpha(statusColor, 30));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onComplaintClick(complaint);
        });
    }

    private int statusColorFor(int status) {
        switch (status) {
            case 1: return COLOR_WITH_ADMIN;
            case 2: return COLOR_RESOLVED;
            case 3: return COLOR_CLOSED;
            default: return COLOR_WITH_DEALER;
        }
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private String formatDate(String isoUtc) {
        if (isoUtc == null) return "";
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            String trimmed = isoUtc.length() > 19 ? isoUtc.substring(0, 19) : isoUtc;
            Date date = parser.parse(trimmed);
            SimpleDateFormat display = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            display.setTimeZone(TimeZone.getDefault());
            return date != null ? "Filed " + display.format(date) : "";
        } catch (ParseException e) {
            return "";
        }
    }

    @Override
    public int getItemCount() {
        return complaints == null ? 0 : complaints.size();
    }

    static class ComplaintViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategory, tvStatus, tvVehicle, tvDescription, tvDate, tvReplyCount;

        ComplaintViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tvComplaintCategory);
            tvStatus = itemView.findViewById(R.id.tvComplaintStatus);
            tvVehicle = itemView.findViewById(R.id.tvComplaintVehicle);
            tvDescription = itemView.findViewById(R.id.tvComplaintDescription);
            tvDate = itemView.findViewById(R.id.tvComplaintDate);
            tvReplyCount = itemView.findViewById(R.id.tvComplaintReplyCount);
        }
    }
}