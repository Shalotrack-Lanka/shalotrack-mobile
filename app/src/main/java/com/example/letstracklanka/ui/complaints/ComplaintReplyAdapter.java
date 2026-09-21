package com.example.letstracklanka.ui.complaints;

import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.ComplaintReplyResponse;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Two-way reply thread. Customer's own replies align right with a blue
 * bubble; dealer/admin replies align left with a neutral bubble — same
 * visual convention as every common chat UI, so nothing here needed
 * explaining to a first-time user.
 */
public class ComplaintReplyAdapter extends RecyclerView.Adapter<ComplaintReplyAdapter.ReplyViewHolder> {

    private List<ComplaintReplyResponse> replies;

    public ComplaintReplyAdapter(List<ComplaintReplyResponse> replies) {
        this.replies = replies;
    }

    public void updateReplies(List<ComplaintReplyResponse> newReplies) {
        this.replies = newReplies;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReplyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_complaint_reply, parent, false);
        return new ReplyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReplyViewHolder holder, int position) {
        ComplaintReplyResponse reply = replies.get(position);
        boolean isMine = reply.isFromCustomer();

        holder.tvAuthor.setText(reply.getAuthorLabel());
        holder.tvMessage.setText(reply.getMessage());
        holder.tvTime.setText(formatTime(reply.getCreatedAt()));

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) holder.column.getLayoutParams();
        params.gravity = isMine ? Gravity.END : Gravity.START;
        holder.column.setLayoutParams(params);
        holder.tvAuthor.setGravity(isMine ? Gravity.END : Gravity.START);
        holder.tvTime.setGravity(isMine ? Gravity.END : Gravity.START);

        Drawable bubbleBg = holder.bubble.getBackground().mutate();
        if (isMine) {
            bubbleBg.setTint(ContextCompat.getColor(holder.itemView.getContext(), R.color.surface_chip_selected_bg));
            holder.tvMessage.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary));
        } else {
            bubbleBg.setTint(ContextCompat.getColor(holder.itemView.getContext(), R.color.surface_white));
            holder.tvMessage.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary));
        }
    }

    private String formatTime(String isoUtc) {
        if (isoUtc == null) return "";
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            String trimmed = isoUtc.length() > 19 ? isoUtc.substring(0, 19) : isoUtc;
            Date date = parser.parse(trimmed);
            SimpleDateFormat display = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());
            display.setTimeZone(TimeZone.getDefault());
            return date != null ? display.format(date) : "";
        } catch (ParseException e) {
            return "";
        }
    }

    @Override
    public int getItemCount() {
        return replies == null ? 0 : replies.size();
    }

    static class ReplyViewHolder extends RecyclerView.ViewHolder {
        View column, bubble;
        TextView tvAuthor, tvMessage, tvTime;

        ReplyViewHolder(@NonNull View itemView) {
            super(itemView);
            column = itemView.findViewById(R.id.columnReply);
            bubble = itemView.findViewById(R.id.bubbleReply);
            tvAuthor = itemView.findViewById(R.id.tvReplyAuthor);
            tvMessage = itemView.findViewById(R.id.tvReplyMessage);
            tvTime = itemView.findViewById(R.id.tvReplyTime);
        }
    }
}