package com.example.letstracklanka.ui.more;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.example.letstracklanka.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/** "Download my data" format choice (bottom_sheet_export_format): PDF first, like Fleet, then JSON. */
public final class ExportFormatSheet {

    public enum Format {
        PDF("application/pdf", "pdf"),
        JSON("application/json", "json");

        public final String mime;
        public final String extension;

        Format(String mime, String extension) {
            this.mime = mime;
            this.extension = extension;
        }
    }

    public interface Listener {
        void onFormat(@NonNull Format format);
    }

    private ExportFormatSheet() {}

    @SuppressLint("InflateParams") // dialog content has no parent to measure against
    public static void show(@NonNull Activity activity, @NonNull Listener listener) {
        BottomSheetDialog sheet = new BottomSheetDialog(activity);
        View v = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_export_format, null);
        sheet.setContentView(v);
        ViewGroup options = v.findViewById(R.id.exportOptions);
        MoreRows.add(options, R.drawable.ic_st_file, activity.getString(R.string.st_export_pdf),
                activity.getString(R.string.st_export_pdf_detail), false, x -> {
                    sheet.dismiss();
                    listener.onFormat(Format.PDF);
                });
        MoreRows.add(options, R.drawable.ic_st_download, activity.getString(R.string.st_export_json),
                activity.getString(R.string.st_export_json_detail), false, x -> {
                    sheet.dismiss();
                    listener.onFormat(Format.JSON);
                });
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.show();
    }
}
