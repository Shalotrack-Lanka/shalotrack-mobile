package com.example.letstracklanka.ui.common;

import android.app.Activity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.example.letstracklanka.R;

/**
 * The in-line banner every screen uses for problems (layout: include_inline_banner.xml).
 * Replaces Toasts, Snackbars and pop-up dialogs: the message stays inside the screen that failed, is read
 * out by TalkBack (live region), and carries a Retry when there is something to retry.
 *
 *   offline  amber, "no internet" style: data on screen may be old
 *   error    red: a request failed
 *   info     blue: what happens next, with an optional action ("Upload slip now")
 *   success  green: a change went through ("Invite sent")
 */
public final class InlineBanner {

    private final View root;
    private final ImageView icon;
    private final TextView text;
    private final TextView action;

    public InlineBanner(@Nullable View root) {
        this.root = root;
        this.icon = root != null ? root.findViewById(R.id.inlineBannerIcon) : null;
        this.text = root != null ? root.findViewById(R.id.inlineBannerText) : null;
        this.action = root != null ? root.findViewById(R.id.inlineBannerAction) : null;
    }

    /** Finds the banner included in the activity's layout (id inlineBanner). */
    public static InlineBanner in(Activity activity) {
        return new InlineBanner(activity.findViewById(R.id.inlineBanner));
    }

    public void showOffline(CharSequence message, @Nullable Runnable retry) {
        show(message, null, retry, R.drawable.bg_st_banner_warning, R.drawable.ic_st_wifi_off,
                R.color.st_status_offline_text, R.color.st_status_offline_ink);
    }

    public void showError(CharSequence message, @Nullable Runnable retry) {
        show(message, null, retry, R.drawable.bg_st_banner_error, R.drawable.ic_st_alert_circle,
                R.color.st_danger_text, R.color.st_danger_text);
    }

    /** Blue: what happens next. The action, when given, is labelled actionLabel instead of Retry. */
    public void showInfo(CharSequence message, @Nullable CharSequence actionLabel, @Nullable Runnable action) {
        show(message, actionLabel, action, R.drawable.bg_st_banner_info, R.drawable.ic_st_info,
                R.color.st_tone_info_text, R.color.st_tone_info_text);
    }

    /** Green: a change went through. */
    public void showSuccess(CharSequence message) {
        show(message, null, null, R.drawable.bg_st_banner_success, R.drawable.ic_st_check,
                R.color.st_tone_green_text, R.color.st_tone_green_text);
    }

    public void showError(@StringRes int message, @Nullable Runnable retry) {
        if (root == null) return;
        showError(root.getContext().getString(message), retry);
    }

    public void showOffline(@StringRes int message, @Nullable Runnable retry) {
        if (root == null) return;
        showOffline(root.getContext().getString(message), retry);
    }

    public void hide() {
        if (root != null) root.setVisibility(View.GONE);
    }

    public boolean isShowing() {
        return root != null && root.getVisibility() == View.VISIBLE;
    }

    private void show(CharSequence message, @Nullable CharSequence actionLabel, @Nullable Runnable retry,
                      int background, int iconRes, int iconTint, int textColor) {
        if (root == null) return;
        root.setBackgroundResource(background);
        if (icon != null) {
            icon.setImageResource(iconRes);
            icon.setImageTintList(ContextCompat.getColorStateList(root.getContext(), iconTint));
        }
        if (text != null) {
            text.setText(message);
            text.setTextColor(ContextCompat.getColor(root.getContext(), textColor));
        }
        if (action != null) {
            if (retry != null) {
                if (actionLabel != null) action.setText(actionLabel);
                else action.setText(R.string.st_action_retry);
                action.setVisibility(View.VISIBLE);
                action.setOnClickListener(v -> {
                    hide();
                    retry.run();
                });
            } else {
                action.setVisibility(View.GONE);
                action.setOnClickListener(null);
            }
        }
        root.setVisibility(View.VISIBLE);
    }
}
