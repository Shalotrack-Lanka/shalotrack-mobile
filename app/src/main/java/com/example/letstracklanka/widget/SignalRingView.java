package com.example.letstracklanka.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.core.content.ContextCompat;

public class SignalRingView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();
    private final float strokeWidthPx;
    private float sweepAngle = 0f;

    public SignalRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        strokeWidthPx = dp(4);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidthPx);
        trackPaint.setColor(Color.parseColor("#33FFFFFF"));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(strokeWidthPx);
        int accent;
        try {
            accent = ContextCompat.getColor(context, resId(context, "brand_accent"));
        } catch (Exception e) {
            accent = Color.parseColor("#FF7A1A");
        }
        progressPaint.setColor(accent);
    }

    private static int resId(Context c, String colorName) {
        return c.getResources().getIdentifier(colorName, "color", c.getPackageName());
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float inset = strokeWidthPx;
        arcBounds.set(inset, inset, w - inset, h - inset);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawArc(arcBounds, 0, 360, false, trackPaint);
        canvas.drawArc(arcBounds, -90, sweepAngle, false, progressPaint);
    }

    public void setProgress(float fraction) {
        sweepAngle = 360f * Math.max(0f, Math.min(1f, fraction));
        invalidate();
    }

    public void animateProgress(float toFraction, long durationMs, Runnable onEnd) {
        float from = sweepAngle / 360f;
        ValueAnimator anim = ValueAnimator.ofFloat(from, toFraction);
        anim.setDuration(durationMs);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> setProgress((float) a.getAnimatedValue()));
        if (onEnd != null) {
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            });
        }
        anim.start();
    }
}