package com.example.letstracklanka.ui.common;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.letstracklanka.R;
import com.google.android.material.button.MaterialButton;

/**
 * The in-screen empty / failed state (layout: include_empty_state.xml). Never a blank screen: a title,
 * one line of help and, when there is something to do, one button.
 */
public final class EmptyState {

    private final View root;
    private final TextView title;
    private final TextView body;
    private final MaterialButton action;

    public EmptyState(@Nullable View root) {
        this.root = root;
        this.title = root != null ? root.findViewById(R.id.emptyStateTitle) : null;
        this.body = root != null ? root.findViewById(R.id.emptyStateBody) : null;
        this.action = root != null ? root.findViewById(R.id.emptyStateAction) : null;
    }

    public void show(CharSequence titleText, CharSequence bodyText,
                     @Nullable CharSequence actionLabel, @Nullable Runnable onAction) {
        if (root == null) return;
        if (title != null) title.setText(titleText);
        if (body != null) {
            body.setText(bodyText);
            body.setVisibility(bodyText == null || bodyText.length() == 0 ? View.GONE : View.VISIBLE);
        }
        if (action != null) {
            if (actionLabel != null && onAction != null) {
                action.setText(actionLabel);
                action.setVisibility(View.VISIBLE);
                action.setOnClickListener(v -> onAction.run());
            } else {
                action.setVisibility(View.GONE);
                action.setOnClickListener(null);
            }
        }
        root.setVisibility(View.VISIBLE);
    }

    public void hide() {
        if (root != null) root.setVisibility(View.GONE);
    }
}
