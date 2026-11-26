package com.example.letstracklanka.ui.common;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.letstracklanka.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

/**
 * "Are you sure?" in a bottom sheet (bottom_sheet_confirm), never a system dialog. The action runs with the
 * sheet open and busy; on failure the reason shows inside the sheet so the customer can try again.
 */
public final class ConfirmSheet {

    /** Report back exactly once. */
    public interface Done {
        void succeeded();
        void failed(@NonNull CharSequence message);
    }

    public interface Action {
        void run(@NonNull Done done);
    }

    private ConfirmSheet() {}

    @SuppressLint("InflateParams") // dialog content has no parent to measure against
    public static void show(@NonNull Activity activity, @NonNull CharSequence title, @NonNull CharSequence body,
                            @NonNull CharSequence actionLabel, @NonNull CharSequence busyLabel,
                            @NonNull Action action) {
        BottomSheetDialog sheet = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_confirm, null);
        sheet.setContentView(view);
        ((TextView) view.findViewById(R.id.tvConfirmTitle)).setText(title);
        ((TextView) view.findViewById(R.id.tvConfirmBody)).setText(body);
        TextView error = view.findViewById(R.id.tvConfirmError);
        MaterialButton confirm = view.findViewById(R.id.btnConfirmAction);
        MaterialButton cancel = view.findViewById(R.id.btnConfirmCancel);
        confirm.setText(actionLabel);
        cancel.setOnClickListener(v -> sheet.dismiss());
        confirm.setOnClickListener(v -> {
            error.setVisibility(View.GONE);
            setBusy(confirm, cancel, sheet, true, busyLabel, actionLabel);
            action.run(new Done() {
                private boolean reported;

                @Override
                public void succeeded() {
                    if (reported) return;
                    reported = true;
                    if (sheet.isShowing()) sheet.dismiss();
                }

                @Override
                public void failed(@NonNull CharSequence message) {
                    if (reported) return;
                    reported = true;
                    if (!sheet.isShowing()) return;
                    setBusy(confirm, cancel, sheet, false, busyLabel, actionLabel);
                    error.setText(message);
                    error.setVisibility(View.VISIBLE);
                }
            });
        });
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.show();
    }

    private static void setBusy(MaterialButton confirm, MaterialButton cancel, BottomSheetDialog sheet, boolean busy,
                                CharSequence busyLabel, CharSequence actionLabel) {
        confirm.setEnabled(!busy);
        cancel.setEnabled(!busy);
        sheet.setCancelable(!busy);
        confirm.setText(busy ? busyLabel : actionLabel);
    }
}
