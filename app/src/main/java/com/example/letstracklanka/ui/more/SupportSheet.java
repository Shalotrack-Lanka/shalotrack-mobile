package com.example.letstracklanka.ui.more;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.letstracklanka.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Contact ShaloTrack (bottom_sheet_support): call, WhatsApp or email. The numbers are the ones on shalotrack.com. Nothing about
 * the customer or their vehicles is put into the email or message: they choose what to send.
 * Replaces the old "Call center" carousel, which advertised Letstrack services ShaloTrack does not run.
 */
public final class SupportSheet {

    private SupportSheet() {}

    public static void show(@NonNull Activity activity, @NonNull String appVersion) {
        BottomSheetDialog sheet = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_support, null);
        sheet.setContentView(view);
        ViewGroup rows = view.findViewById(R.id.supportRows);
        TextView error = view.findViewById(R.id.tvSupportError);

        String phone = activity.getString(R.string.st_support_phone);
        MoreRows.add(rows, R.drawable.ic_st_phone, activity.getString(R.string.st_support_call),
                activity.getString(R.string.st_support_phone_display), false,
                v -> open(activity, sheet, error, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)),
                        R.string.st_error_no_dialer));

        MoreRows.add(rows, R.drawable.ic_st_message, activity.getString(R.string.st_support_whatsapp),
                activity.getString(R.string.st_support_whatsapp_detail), false,
                v -> open(activity, sheet, error, new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://wa.me/" + activity.getString(R.string.st_support_whatsapp_number))),
                        R.string.st_error_no_app));

        String email = activity.getString(R.string.st_support_email);
        MoreRows.add(rows, R.drawable.ic_st_file, activity.getString(R.string.st_support_email_title), email, false, v -> {
            Intent mail = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email));
            mail.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.st_support_email_subject));
            mail.putExtra(Intent.EXTRA_TEXT, activity.getString(R.string.st_support_email_body, appVersion));
            open(activity, sheet, error, mail, R.string.st_error_no_email_app);
        });


        sheet.show();
    }

    private static void open(Activity activity, BottomSheetDialog sheet, TextView error, Intent intent, @StringRes int noApp) {
        try {
            activity.startActivity(intent);
            sheet.dismiss();
        } catch (ActivityNotFoundException e) {
            error.setText(noApp);
            error.setVisibility(View.VISIBLE);
        }
    }
}
