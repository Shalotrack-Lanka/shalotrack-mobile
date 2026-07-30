package com.example.letstracklanka.ui.vehicles;

import android.content.res.Resources;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;

/**
 * Handles swipe-left-to-reveal-Remove directly, deliberately NOT using
 * ItemTouchHelper.
 *
 * ItemTouchHelper is built around swipe-TO-DISMISS: any swipe that doesn't
 * cross its own completion threshold gets auto-animated back to zero by
 * its internal recovery animation -- which runs by calling onChildDraw()
 * repeatedly with decreasing dX values, and that recovery finishes BEFORE
 * any "should this row stay open" decision code gets a chance to run. That
 * is exactly why an earlier ItemTouchHelper-based version of this always
 * snapped back shut instead of staying revealed: the row had already been
 * silently un-dragged by ItemTouchHelper's own machinery before this
 * class's equivalent of "decide to stay open" ever ran.
 *
 * This class owns the entire gesture itself, from ACTION_DOWN to
 * ACTION_UP, with no competing auto-recovery baked in underneath it -- so
 * "stays open" actually stays open.
 */
public class VehicleRowSwipeController implements View.OnTouchListener {

    public interface OpenRowTracker {
        /** Called just before this row opens; the tracker should close
         * whichever OTHER row was previously open (only one open at a time,
         * matching standard swipe-action UX). */
        void onRowOpening(VehicleRowSwipeController opening);
    }

    private final View foreground;
    private final float revealWidthPx;
    private final OpenRowTracker tracker;
    private final int touchSlop;

    private float downRawX;
    private float baseTranslation;
    private boolean isDragging = false;
    private boolean wasOpenAtDown = false;
    private boolean isOpen = false;
    private VelocityTracker velocityTracker;

    public VehicleRowSwipeController(View foreground, int revealWidthDp, OpenRowTracker tracker) {
        this.foreground = foreground;
        this.tracker = tracker;
        this.revealWidthPx = dpToPx(foreground.getResources(), revealWidthDp);
        this.touchSlop = ViewConfiguration.get(foreground.getContext()).getScaledTouchSlop();
    }

    public boolean isOpen() {
        return isOpen;
    }

    /** Animated close -- used when Remove is tapped, or when another row
     * opens and this one needs to yield. */
    public void close() {
        isOpen = false;
        animateTo(0f);
    }

    /** Instant, non-animated reset -- used on RecyclerView rebind, where an
     * animation would be visually wrong (the row now represents a
     * completely different vehicle, not a user action closing it). */
    public void resetImmediate() {
        isOpen = false;
        isDragging = false;
        foreground.setTranslationX(0f);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downRawX = event.getRawX();
                baseTranslation = foreground.getTranslationX();
                isDragging = false;
                wasOpenAtDown = isOpen;
                if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
                else velocityTracker.clear();
                velocityTracker.addMovement(event);
                // Don't consume yet -- if this turns out to be a plain tap
                // on an already-closed row, the normal click listener still
                // needs to see it.
                return false;
            }

            case MotionEvent.ACTION_MOVE: {
                float dX = event.getRawX() - downRawX;
                if (!isDragging && Math.abs(dX) > touchSlop) {
                    isDragging = true;
                    ViewParent parent = v.getParent();
                    if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                }
                if (isDragging) {
                    if (velocityTracker != null) velocityTracker.addMovement(event);
                    float target = clamp(baseTranslation + dX, -revealWidthPx, 0f);
                    foreground.setTranslationX(target);
                    return true;
                }
                return false;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                ViewParent parent = v.getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(false);

                if (!isDragging) {
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                    if (wasOpenAtDown) {
                        // Tapping anywhere on an already-open row just closes
                        // it -- it must NOT also trigger the row's normal
                        // select action, or closing the reveal would
                        // accidentally switch the active vehicle too.
                        close();
                        return true;
                    }
                    return false; // genuine tap on a closed row: let it click normally
                }

                isDragging = false;

                float velocityX = 0f;
                if (velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000);
                    velocityX = velocityTracker.getXVelocity();
                    velocityTracker.recycle();
                    velocityTracker = null;
                }

                boolean shouldOpen;
                if (Math.abs(velocityX) > 800f) {
                    // Fast flick: honor direction regardless of distance dragged.
                    shouldOpen = velocityX < 0;
                } else {
                    shouldOpen = foreground.getTranslationX() < -(revealWidthPx / 2f);
                }

                if (shouldOpen) {
                    if (tracker != null) tracker.onRowOpening(this);
                    isOpen = true;
                    animateTo(-revealWidthPx);
                } else {
                    isOpen = false;
                    animateTo(0f);
                }
                return true;
            }
        }
        return false;
    }

    private void animateTo(float target) {
        foreground.animate()
                .translationX(target)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dpToPx(Resources res, int dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, res.getDisplayMetrics());
    }
}