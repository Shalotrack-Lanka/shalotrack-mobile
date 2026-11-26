package com.example.letstracklanka.ui.common;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.letstracklanka.R;

/** Binds include_st_top_bar: title, back closes the screen, and the bar sits below the status bar. */
public final class TopBar {

    private TopBar() {}

    public static void bind(@NonNull Activity activity, @StringRes int title) {
        View bar = activity.findViewById(R.id.stTopBar);
        if (bar == null) return;
        ((TextView) bar.findViewById(R.id.tvTopBarTitle)).setText(title);
        bar.findViewById(R.id.btnTopBarBack).setOnClickListener(v -> activity.finish());
        EdgeToEdge.padTop(bar);
    }
}
