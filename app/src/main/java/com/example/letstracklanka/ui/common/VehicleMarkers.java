package com.example.letstracklanka.ui.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.ui.vehicles.VehicleListAdapter;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Map markers for the vehicles that are NOT the one being followed: a white disc with the vehicle-type
 * icon, ringed in the vehicle's status colour (same colours as the lists). Offline and no-tracker
 * vehicles get a grey icon. Bitmaps are cached per (type, status), so a dashboard refresh never
 * re-draws them.
 */
public final class VehicleMarkers {

    private static final int SIZE_DP = 34;
    private static final float RING_DP = 3f;
    private static final int ICON_DP = 16;
    private static final Map<String, BitmapDescriptor> CACHE = new HashMap<>();

    private VehicleMarkers() {}

    public static BitmapDescriptor icon(Context context, DashboardVehicle v) {
        VehicleStatus status = VehicleStatus.of(v);
        int iconRes = VehicleListAdapter.iconForType(v.getVehicleType());
        String key = iconRes + ":" + status.name();
        BitmapDescriptor cached = CACHE.get(key);
        if (cached != null) return cached;

        float d = context.getResources().getDisplayMetrics().density;
        int size = Math.round(SIZE_DP * d);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        float ring = RING_DP * d;
        float r = size / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(status.dotColor(context));
        canvas.drawCircle(r, r, r, paint);
        paint.setColor(ContextCompat.getColor(context, R.color.st_surface));
        canvas.drawCircle(r, r, r - ring, paint);

        Drawable icon = ContextCompat.getDrawable(context, iconRes);
        if (icon != null) {
            icon = DrawableCompat.wrap(icon.mutate());
            int tint = status.isReporting() ? R.color.st_navy : R.color.st_text_4;
            DrawableCompat.setTint(icon, ContextCompat.getColor(context, tint));
            int iconPx = Math.round(ICON_DP * d);
            int left = (size - iconPx) / 2;
            icon.setBounds(left, left, left + iconPx, left + iconPx);
            icon.draw(canvas);
        }

        BitmapDescriptor descriptor = BitmapDescriptorFactory.fromBitmap(bmp);
        CACHE.put(key, descriptor);
        return descriptor;
    }
}
