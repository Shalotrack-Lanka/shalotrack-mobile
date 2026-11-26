package com.example.letstracklanka.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.letstracklanka.R;

/**
 * The state of a record (an invite, a complaint, a renewal, a tracker link) on a Widget.ST.StatusPill.
 * Live vehicle states use VehicleStatus instead.
 */
public final class Pill {

    public enum Tone {
        GREEN(R.color.st_tone_green_dot, R.color.st_tone_green_text, R.color.st_tone_green_bg),
        NAVY(R.color.st_tone_navy_dot, R.color.st_tone_navy_text, R.color.st_tone_navy_bg),
        AMBER(R.color.st_tone_amber_dot, R.color.st_tone_amber_text, R.color.st_tone_amber_bg),
        RED(R.color.st_tone_red_dot, R.color.st_tone_red_text, R.color.st_tone_red_bg),
        GREY(R.color.st_tone_grey_dot, R.color.st_tone_grey_text, R.color.st_tone_grey_bg);

        @ColorRes final int dot;
        @ColorRes final int text;
        @ColorRes final int background;

        Tone(@ColorRes int dot, @ColorRes int text, @ColorRes int background) {
            this.dot = dot;
            this.text = text;
            this.background = background;
        }
    }

    private Pill() {}

    public static void apply(@NonNull TextView pill, @NonNull CharSequence label, @NonNull Tone tone) {
        Context c = pill.getContext();
        pill.setText(label);
        pill.setTextColor(ContextCompat.getColor(c, tone.text));
        pill.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, tone.background)));
        int px = Math.round(6 * c.getResources().getDisplayMetrics().density);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(ContextCompat.getColor(c, tone.dot));
        dot.setSize(px, px);
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
    }
}
