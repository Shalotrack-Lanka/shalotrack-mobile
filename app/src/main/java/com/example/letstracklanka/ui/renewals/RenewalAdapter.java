package com.example.letstracklanka.ui.renewals;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.RenewalResponse;
import com.example.letstracklanka.ui.common.Pill;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.ui.common.TimeText;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/** Renewal request cards: plate, state, package and amount, a note, and Upload / Replace slip and Cancel. */
public class RenewalAdapter extends RecyclerView.Adapter<RenewalAdapter.Holder> {

    public interface Actions {
        void onUploadSlip(RenewalResponse renewal);
        void onCancel(RenewalResponse renewal);
    }

    private List<RenewalResponse> items = new ArrayList<>();
    private final Actions actions;
    private boolean busy;

    public RenewalAdapter(Actions actions) {
        this.actions = actions;
    }

    public void submit(List<RenewalResponse> newItems) {
        items = newItems == null ? new ArrayList<>() : newItems;
        notifyDataSetChanged();
    }

    /** While a slip is uploading, the slip and cancel buttons are disabled. */
    public void setBusy(boolean busy) {
        if (this.busy == busy) return;
        this.busy = busy;
        notifyDataSetChanged();
    }

    /** "1 year" for the API's duration name ("OneYear"), from st_renewal_durations. */
    static String duration(@NonNull Context c, @Nullable String name) {
        String[] labels = c.getResources().getStringArray(R.array.st_renewal_durations);
        for (int i = 0; i < RenewalResponse.DURATION_NAMES.length && i < labels.length; i++) {
            if (RenewalResponse.DURATION_NAMES[i].equals(name)) return labels[i];
        }
        return name == null ? "" : name;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_renewal, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        RenewalResponse r = items.get(position);
        Context c = h.itemView.getContext();
        String status = r.getStatus();

        h.tvPlate.setText(Plates.format(c, r.getVehicleNumber()));
        Pill.Tone tone;
        if (RenewalResponse.STATUS_AWAITING_SLIP.equals(status)) tone = Pill.Tone.AMBER;
        else if (RenewalResponse.STATUS_PENDING_REVIEW.equals(status)) tone = Pill.Tone.NAVY;
        else if (RenewalResponse.STATUS_APPROVED.equals(status)) tone = Pill.Tone.GREEN;
        else if (RenewalResponse.STATUS_REJECTED.equals(status)) tone = Pill.Tone.RED;
        else tone = Pill.Tone.GREY;
        Pill.apply(h.tvStatus, r.getStatusLabel(), tone);

        String length = duration(c, r.getDuration());
        h.tvDetail.setText(r.getAmountLkr() != null
                ? c.getString(R.string.st_renewal_line, length, RenewalResponse.formatLkr(r.getAmountLkr()))
                : length);

        String note;
        if (RenewalResponse.STATUS_REJECTED.equals(status) && r.getDecisionReason() != null
                && !r.getDecisionReason().trim().isEmpty()) {
            note = r.getDecisionReason().trim();
        } else if (RenewalResponse.STATUS_AWAITING_SLIP.equals(status)) {
            String asked = c.getString(R.string.st_renewal_asked, TimeText.date(r.getCreatedAt()));
            String ref = r.getPaymentReference();
            note = ref != null && !ref.trim().isEmpty()
                    ? c.getString(R.string.st_renewal_line, c.getString(R.string.st_renewal_reference, ref.trim()), asked)
                    : c.getString(R.string.st_renewal_note_waiting);
        } else if (RenewalResponse.STATUS_PENDING_REVIEW.equals(status)) {
            note = c.getString(R.string.st_renewal_note_review);
        } else if (RenewalResponse.STATUS_APPROVED.equals(status)) {
            note = c.getString(R.string.st_renewal_note_approved);
        } else if (RenewalResponse.STATUS_CANCELLED.equals(status)) {
            note = c.getString(R.string.st_renewal_note_cancelled);
        } else {
            note = null;
        }
        h.tvNote.setText(note == null ? "" : note);
        h.tvNote.setVisibility(note == null ? View.GONE : View.VISIBLE);

        boolean open = r.isOpen();
        h.actions.setVisibility(open ? View.VISIBLE : View.GONE);
        h.btnUpload.setText(r.hasSlip() ? R.string.st_renewal_replace : R.string.st_renewal_upload);
        h.btnUpload.setEnabled(!busy);
        h.btnCancel.setEnabled(!busy);
        h.btnUpload.setOnClickListener(v -> actions.onUploadSlip(r));
        h.btnCancel.setOnClickListener(v -> actions.onCancel(r));

        h.summary.setContentDescription(Plates.plain(r.getVehicleNumber()) + ", " + r.getStatusLabel() + ", "
                + h.tvDetail.getText() + (note == null ? "" : ". " + note));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View summary, actions;
        final TextView tvPlate, tvStatus, tvDetail, tvNote;
        final MaterialButton btnUpload, btnCancel;

        Holder(@NonNull View v) {
            super(v);
            summary = v.findViewById(R.id.renewalSummary);
            actions = v.findViewById(R.id.groupRenewalActions);
            tvPlate = v.findViewById(R.id.tvRenewalPlate);
            tvStatus = v.findViewById(R.id.tvRenewalStatus);
            tvDetail = v.findViewById(R.id.tvRenewalDetail);
            tvNote = v.findViewById(R.id.tvRenewalNote);
            btnUpload = v.findViewById(R.id.btnRenewalUpload);
            btnCancel = v.findViewById(R.id.btnRenewalCancel);
        }
    }
}
