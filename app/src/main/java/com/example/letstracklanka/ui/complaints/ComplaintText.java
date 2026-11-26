package com.example.letstracklanka.ui.complaints;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.ComplaintResponse;
import com.example.letstracklanka.ui.common.Pill;

/**
 * Words and colours for a complaint, in one place for the list and the thread. Category and status are
 * the API's enums: Category DeviceIssue 0, Billing 1, AppBug 2, Other 3; Status WithDealer 0,
 * WithAdmin 1, Resolved 2, Closed 3.
 */
final class ComplaintText {

    /** Index = the category value the API takes. */
    static final int[] CATEGORY_LABELS = {
            R.string.st_complaint_cat_device, R.string.st_complaint_cat_billing,
            R.string.st_complaint_cat_app, R.string.st_complaint_cat_other};

    private ComplaintText() {}

    static String category(@NonNull Context c, int category) {
        return c.getString(category >= 0 && category < CATEGORY_LABELS.length
                ? CATEGORY_LABELS[category] : R.string.st_complaint_cat_other);
    }

    static void status(@NonNull TextView pill, int status) {
        Context c = pill.getContext();
        switch (status) {
            case 1:
                Pill.apply(pill, c.getString(R.string.st_complaint_status_admin), Pill.Tone.NAVY);
                break;
            case 2:
                Pill.apply(pill, c.getString(R.string.st_complaint_status_resolved), Pill.Tone.GREEN);
                break;
            case 3:
                Pill.apply(pill, c.getString(R.string.st_complaint_status_closed), Pill.Tone.GREY);
                break;
            default:
                Pill.apply(pill, c.getString(R.string.st_complaint_status_dealer), Pill.Tone.AMBER);
                break;
        }
    }

    static String vehicleName(@NonNull ComplaintResponse complaint) {
        String make = complaint.getMake() == null ? "" : complaint.getMake();
        String model = complaint.getModel() == null ? "" : complaint.getModel();
        return (make + " " + model).trim();
    }
}
