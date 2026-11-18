package com.example.letstracklanka.ui.more;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.letstracklanka.R;

/** Inflates item_more_row the same way everywhere (More tab, support sheet). */
public final class MoreRows {

    private MoreRows() {}

    public static View add(@NonNull ViewGroup parent, @DrawableRes int icon, CharSequence title,
                           @Nullable CharSequence detail, boolean danger, @NonNull View.OnClickListener onClick) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_more_row, parent, false);
        ImageView iv = row.findViewById(R.id.moreRowIcon);
        View tile = row.findViewById(R.id.moreRowIconTile);
        TextView tvTitle = row.findViewById(R.id.moreRowTitle);
        TextView tvDetail = row.findViewById(R.id.moreRowDetail);

        iv.setImageResource(icon);
        tvTitle.setText(title);
        if (detail != null && detail.length() > 0) {
            tvDetail.setText(detail);
            tvDetail.setVisibility(View.VISIBLE);
        }
        if (danger) {
            tint(iv, tile, R.color.st_danger_text, R.color.st_danger_bg);
            tvTitle.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.st_danger_text));
        }
        row.setContentDescription(detail != null && detail.length() > 0 ? title + ", " + detail : title);
        row.setOnClickListener(onClick);
        parent.addView(row);
        return row;
    }

    private static void tint(ImageView icon, View tile, @ColorRes int fg, @ColorRes int bg) {
        icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(icon.getContext(), fg)));
        tile.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(tile.getContext(), bg)));
    }
}
