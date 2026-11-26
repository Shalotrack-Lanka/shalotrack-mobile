package com.example.letstracklanka.ui.complaints;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.ComplaintReplyResponse;
import com.example.letstracklanka.data.model.ComplaintResponse;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.ui.common.TimeText;

import java.util.ArrayList;
import java.util.List;

/**
 * A complaint thread: the header card (category, state, vehicle), the customer's original message, then
 * every reply. The customer's messages sit on the right, the dealer's and ShaloTrack's on the left.
 */
public class ComplaintReplyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_MESSAGE = 1;

    /** One bubble: who, what, when. */
    private static final class Message {
        final boolean mine;
        final String author;
        final String text;
        final String time;

        Message(boolean mine, String author, String text, String time) {
            this.mine = mine;
            this.author = author;
            this.text = text;
            this.time = time;
        }
    }

    @Nullable private ComplaintResponse complaint;
    private final List<Message> messages = new ArrayList<>();

    public void show(@NonNull Context c, @NonNull ComplaintResponse complaint) {
        this.complaint = complaint;
        messages.clear();
        messages.add(new Message(true, c.getString(R.string.st_complaint_author_you),
                complaint.getDescription() == null ? "" : complaint.getDescription(),
                TimeText.ago(c, complaint.getCreatedAt())));
        for (ComplaintReplyResponse r : complaint.getReplies()) {
            if (r == null) continue;
            messages.add(new Message(r.isFromCustomer(), author(c, r, complaint),
                    r.getMessage() == null ? "" : r.getMessage(), TimeText.ago(c, r.getCreatedAt())));
        }
        notifyDataSetChanged();
    }

    /** Position of the newest message, for scrolling to it. */
    public int lastPosition() {
        return getItemCount() - 1;
    }

    private static String author(Context c, ComplaintReplyResponse r, ComplaintResponse complaint) {
        switch (r.getAuthorType()) {
            case 1: {
                String dealer = complaint.getDealerName();
                return dealer != null && !dealer.trim().isEmpty()
                        ? c.getString(R.string.st_complaint_author_dealer_named, dealer.trim())
                        : c.getString(R.string.st_complaint_author_dealer);
            }
            case 2:
                return c.getString(R.string.st_complaint_author_support);
            default:
                return c.getString(R.string.st_complaint_author_you);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_HEADER : TYPE_MESSAGE;
    }

    @Override
    public int getItemCount() {
        return complaint == null ? 0 : 1 + messages.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_complaint_header, parent, false));
        }
        return new MessageHolder(inflater.inflate(R.layout.item_complaint_reply, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (complaint == null) return;
        Context c = holder.itemView.getContext();
        if (holder instanceof HeaderHolder) {
            HeaderHolder h = (HeaderHolder) holder;
            String category = ComplaintText.category(c, complaint.getCategory());
            h.title.setText(category);
            ComplaintText.status(h.status, complaint.getStatus());
            h.plate.setText(Plates.format(c, complaint.getVehicleNumber()));
            String opened = c.getString(R.string.st_complaint_opened, TimeText.date(complaint.getCreatedAt()));
            h.opened.setText(opened);
            h.card.setContentDescription(category + ", " + h.status.getText() + ", "
                    + Plates.plain(complaint.getVehicleNumber()) + ", " + opened);
            return;
        }
        MessageHolder h = (MessageHolder) holder;
        Message m = messages.get(position - 1);
        h.author.setText(m.author);
        h.text.setText(m.text);
        h.time.setText(m.time);
        h.bubble.setBackgroundResource(m.mine ? R.drawable.bg_st_bubble_mine : R.drawable.bg_st_bubble_theirs);
        h.author.setVisibility(m.mine ? View.GONE : View.VISIBLE);
        h.time.setText(m.mine ? c.getString(R.string.st_complaint_bubble_meta, m.author, m.time) : m.time);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) h.bubble.getLayoutParams();
        lp.gravity = m.mine ? Gravity.END : Gravity.START;
        h.bubble.setLayoutParams(lp);
        h.time.setGravity(m.mine ? Gravity.END : Gravity.START);
        // Keep a bubble to about four fifths of the width, like a chat.
        int max = (int) (c.getResources().getDisplayMetrics().widthPixels * 0.72f);
        h.text.setMaxWidth(max);
        h.author.setMaxWidth(max);
        h.bubble.setContentDescription(m.author + ", " + m.time + ". " + m.text);
    }

    static final class HeaderHolder extends RecyclerView.ViewHolder {
        final View card;
        final TextView title, status, plate, opened;

        HeaderHolder(@NonNull View v) {
            super(v);
            card = v.findViewById(R.id.complaintHeaderCard);
            title = v.findViewById(R.id.tvDetailTitle);
            status = v.findViewById(R.id.tvDetailStatus);
            plate = v.findViewById(R.id.tvDetailPlate);
            opened = v.findViewById(R.id.tvDetailOpened);
        }
    }

    static final class MessageHolder extends RecyclerView.ViewHolder {
        final View bubble;
        final TextView author, text, time;

        MessageHolder(@NonNull View v) {
            super(v);
            bubble = v.findViewById(R.id.replyBubble);
            author = v.findViewById(R.id.tvReplyAuthor);
            text = v.findViewById(R.id.tvReplyMessage);
            time = v.findViewById(R.id.tvReplyTime);
        }
    }
}
