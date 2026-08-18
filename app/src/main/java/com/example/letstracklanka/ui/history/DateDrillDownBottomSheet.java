package com.example.letstracklanka.ui.history;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.letstracklanka.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Self-contained Month -> Week -> Day drill-down for jumping to a specific
 * period in Trip History, replacing the previous raw MaterialDatePicker
 * calendar grid. Deliberately does NOT replace the default scrolling trip
 * list (30-day window + load-more-on-scroll) -- that stays the fast default
 * for "just show me recent trips"; this is specifically for jumping to a
 * period you already have in mind.
 *
 * Rows are built as plain dynamically-generated TextViews rather than a
 * RecyclerView + Adapter + item layout, since the lists here are always
 * small (at most 12 months, ~5 weeks, 7 days) -- recycling overhead isn't
 * needed at this scale, and it keeps this a single self-contained file.
 */
public class DateDrillDownBottomSheet {

    public interface OnDaySelectedListener {
        void onDaySelected(Calendar day);
    }

    private enum Level { MONTH, WEEK, DAY }

    private final Context context;
    private final OnDaySelectedListener listener;
    private final BottomSheetDialog dialog;
    private final TextView tvTitle;
    private final View btnBack;
    private final LinearLayout rowContainer;

    private Level currentLevel = Level.MONTH;
    private Calendar selectedMonthStart;
    private Calendar selectedWeekStart;
    private Calendar selectedWeekEnd;

    private static final int MONTHS_BACK = 12;

    public DateDrillDownBottomSheet(Context context, OnDaySelectedListener listener) {
        this.context = context;
        this.listener = listener;

        dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_date_drilldown, null);
        dialog.setContentView(view);

        tvTitle = view.findViewById(R.id.tvDrillDownTitle);
        btnBack = view.findViewById(R.id.btnDrillDownBack);
        rowContainer = view.findViewById(R.id.rowContainer);

        View btnClose = view.findViewById(R.id.btnDrillDownClose);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        if (btnBack != null) btnBack.setOnClickListener(v -> goBack());
    }

    public void show() {
        renderMonthList();
        dialog.show();
    }

    private void goBack() {
        if (currentLevel == Level.DAY) {
            renderWeekList(selectedMonthStart);
        } else if (currentLevel == Level.WEEK) {
            renderMonthList();
        }
    }

    // ---- Level 1: Months ----

    private void renderMonthList() {
        currentLevel = Level.MONTH;
        tvTitle.setText("Select a month");
        btnBack.setVisibility(View.GONE);
        rowContainer.removeAllViews();

        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        for (int i = 0; i < MONTHS_BACK; i++) {
            Calendar monthStart = (Calendar) cal.clone();
            String label = monthFormat.format(monthStart.getTime());
            addRow(label, () -> {
                selectedMonthStart = monthStart;
                renderWeekList(monthStart);
            });
            cal.add(Calendar.MONTH, -1);
        }
    }

    // ---- Level 2: Weeks within the selected month ----

    private void renderWeekList(Calendar monthStart) {
        currentLevel = Level.WEEK;
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);
        tvTitle.setText(monthFormat.format(monthStart.getTime()));
        btnBack.setVisibility(View.VISIBLE);
        rowContainer.removeAllViews();

        Calendar monthEnd = (Calendar) monthStart.clone();
        monthEnd.add(Calendar.MONTH, 1);
        monthEnd.add(Calendar.MILLISECOND, -1);

        SimpleDateFormat dayFormat = new SimpleDateFormat("MMM d", Locale.US);
        Calendar weekStart = (Calendar) monthStart.clone();

        List<Calendar[]> weeks = new ArrayList<>();
        while (weekStart.before(monthEnd)) {
            Calendar weekEnd = (Calendar) weekStart.clone();
            weekEnd.add(Calendar.DAY_OF_YEAR, 6);
            if (weekEnd.after(monthEnd)) weekEnd = (Calendar) monthEnd.clone();
            weeks.add(new Calendar[]{(Calendar) weekStart.clone(), weekEnd});
            weekStart.add(Calendar.DAY_OF_YEAR, 7);
        }

        // Most recent week first, matching the rest of this app's
        // latest-first convention.
        for (int i = weeks.size() - 1; i >= 0; i--) {
            Calendar[] range = weeks.get(i);
            Calendar wStart = range[0];
            Calendar wEnd = range[1];
            String label = dayFormat.format(wStart.getTime()) + " \u2013 " + dayFormat.format(wEnd.getTime());
            addRow(label, () -> {
                selectedWeekStart = wStart;
                selectedWeekEnd = wEnd;
                renderDayList(wStart, wEnd);
            });
        }
    }

    // ---- Level 3: Days within the selected week ----

    private void renderDayList(Calendar weekStart, Calendar weekEnd) {
        currentLevel = Level.DAY;
        SimpleDateFormat rangeFormat = new SimpleDateFormat("MMM d", Locale.US);
        tvTitle.setText(rangeFormat.format(weekStart.getTime()) + " \u2013 " + rangeFormat.format(weekEnd.getTime()));
        btnBack.setVisibility(View.VISIBLE);
        rowContainer.removeAllViews();

        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE, MMM d", Locale.US);
        Calendar day = (Calendar) weekStart.clone();

        List<Calendar> days = new ArrayList<>();
        while (!day.after(weekEnd)) {
            days.add((Calendar) day.clone());
            day.add(Calendar.DAY_OF_YEAR, 1);
        }

        for (int i = days.size() - 1; i >= 0; i--) {
            Calendar d = days.get(i);
            String label = dayFormat.format(d.getTime());
            addRow(label, () -> {
                if (listener != null) listener.onDaySelected(d);
                dialog.dismiss();
            });
        }
    }

    // ---- Shared row builder ----

    private void addRow(String label, Runnable onClick) {
        TextView row = new TextView(context);
        row.setText(label);
        row.setTextSize(15);
        row.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        row.setGravity(Gravity.CENTER_VERTICAL);
        int paddingH = (int) (20 * context.getResources().getDisplayMetrics().density);
        int paddingV = (int) (14 * context.getResources().getDisplayMetrics().density);
        row.setPadding(paddingH, paddingV, paddingH, paddingV);
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setOnClickListener(v -> onClick.run());
        rowContainer.addView(row);

        View divider = new View(context);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(ContextCompat.getColor(context, R.color.surface_stroke));
        rowContainer.addView(divider);
    }
}