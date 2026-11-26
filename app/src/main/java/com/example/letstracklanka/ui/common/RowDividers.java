package com.example.letstracklanka.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;

/** Hairlines between the rows of a grouped card, none after the last row. */
public final class RowDividers extends RecyclerView.ItemDecoration {

    private final Drawable line;

    private RowDividers(Drawable line) {
        this.line = line;
    }

    /** inset: start the line under the row text (64dp in), as in grouped cards with an icon tile. */
    public static void attach(@NonNull RecyclerView list, boolean inset) {
        Context c = list.getContext();
        @DrawableRes int res = inset ? R.drawable.bg_st_divider_inset : R.drawable.bg_st_divider;
        Drawable d = ContextCompat.getDrawable(c, res);
        if (d != null) list.addItemDecoration(new RowDividers(d));
    }

    @Override
    public void getItemOffsets(@NonNull Rect out, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        boolean last = adapter == null || position == RecyclerView.NO_POSITION || position == adapter.getItemCount() - 1;
        out.set(0, 0, 0, last ? 0 : line.getIntrinsicHeight());
    }

    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (adapter == null) return;
        int left = parent.getPaddingLeft();
        int right = parent.getWidth() - parent.getPaddingRight();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION || position == adapter.getItemCount() - 1) continue;
            int top = child.getBottom();
            line.setBounds(left, top, right, top + line.getIntrinsicHeight());
            line.draw(canvas);
        }
    }
}
