package com.example.letstracklanka.ui.history;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.TrackingPoint;
import com.example.letstracklanka.data.model.TripSummary;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.ui.common.TimeText;
import com.example.letstracklanka.ui.main.AddressResolver;
import com.example.letstracklanka.utils.ApiJson;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Trip list: day headings and trip cards (item_trip_history_day_header / item_trip_history_card).
 *
 * Route thumbnails use Google Maps SDK LITE MODE (a bitmap of a real map, drawn by the Maps SDK):
 *   - no per-image charge, and it uses the app's Android-restricted Maps key;
 *   - replaces the Static Maps API, whose key had to be left without an app restriction and sat in a
 *     public repository where anyone could run up the bill.
 * Following Google's lite-mode list pattern: one MapView per view holder, cleared when recycled.
 *
 * Each route is fetched once (max 500 points, the API's cap), simplified to at most 120 points and
 * cached, so scrolling back never refetches. Until it arrives the card shows a straight start-to-end line.
 */
public class TripHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_DAY = 0;
    private static final int VIEW_TYPE_TRIP = 1;
    private static final int ROUTE_PAGE_SIZE = 500;
    private static final int MAX_THUMBNAIL_POINTS = 120;

    /** Routes for this app session, keyed by vehicle + start + end. */
    private static final LruCache<String, List<LatLng>> ROUTES = new LruCache<>(80);

    public interface OnTripClickListener {
        void onTripClick(TripSummary trip);
    }

    private static final class Row {
        final boolean isDay;
        final String dayTitle;
        final String daySummary;
        final TripSummary trip;

        Row(String dayTitle, String daySummary) {
            this.isDay = true;
            this.dayTitle = dayTitle;
            this.daySummary = daySummary;
            this.trip = null;
        }

        Row(TripSummary trip) {
            this.isDay = false;
            this.dayTitle = null;
            this.daySummary = null;
            this.trip = trip;
        }
    }

    private final OnTripClickListener clickListener;
    private final ShaloTrackApi trackingApi;
    private final Set<String> inFlight = new HashSet<>();
    private String vehicleId;
    private List<Row> rows = new ArrayList<>();

    public TripHistoryAdapter(OnTripClickListener clickListener, ShaloTrackApi trackingApi) {
        this.clickListener = clickListener;
        this.trackingApi = trackingApi;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    /** Groups the trips by local (Sri Lanka) day, newest first. */
    public void updateTrips(Context context, List<TripSummary> trips) {
        List<TripSummary> sorted = new ArrayList<>();
        if (trips != null) for (TripSummary t : trips) if (t != null && TimeText.parse(t.getStartTime()) != null) sorted.add(t);
        Collections.sort(sorted, (a, b) -> TimeText.parse(b.getStartTime()).compareTo(TimeText.parse(a.getStartTime())));

        Map<LocalDate, List<TripSummary>> byDay = new LinkedHashMap<>();
        for (TripSummary t : sorted) {
            LocalDate day = TimeText.localDay(t.getStartTime());
            if (day == null) continue;
            List<TripSummary> list = byDay.get(day);
            if (list == null) {
                list = new ArrayList<>();
                byDay.put(day, list);
            }
            list.add(t);
        }

        List<Row> out = new ArrayList<>();
        for (Map.Entry<LocalDate, List<TripSummary>> e : byDay.entrySet()) {
            double km = 0, minutes = 0;
            for (TripSummary t : e.getValue()) {
                km += t.getDistanceKm();
                minutes += t.getDurationMinutes();
            }
            String summary = context.getString(R.string.st_trips_day_summary,
                    String.format(Locale.getDefault(), "%.1f", km), TimeText.duration(context, minutes));
            out.add(new Row(TimeText.dayHeader(context, e.getKey()), summary));
            for (TripSummary t : e.getValue()) out.add(new Row(t));
        }
        rows = out;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isDay ? VIEW_TYPE_DAY : VIEW_TYPE_TRIP;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_DAY) {
            return new DayHolder(inf.inflate(R.layout.item_trip_history_day_header, parent, false));
        }
        return new TripHolder(inf.inflate(R.layout.item_trip_history_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof DayHolder) {
            ((DayHolder) holder).title.setText(row.dayTitle);
            ((DayHolder) holder).summary.setText(row.daySummary);
            return;
        }
        ((TripHolder) holder).bind(row.trip);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof TripHolder) ((TripHolder) holder).clearMap();
    }

    // ── Routes ─────────────────────────────────────────────

    private static String routeKey(String vehicleId, TripSummary t) {
        return vehicleId + "|" + t.getStartTime() + "|" + t.getEndTime();
    }

    private void loadRoute(TripSummary trip, String key) {
        if (vehicleId == null || trackingApi == null || inFlight.contains(key)) return;
        inFlight.add(key);
        trackingApi.getTrackingHistory(vehicleId, trip.getStartTime(), trip.getEndTime(), ROUTE_PAGE_SIZE)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        inFlight.remove(key);
                        try (ResponseBody body = response.body()) {
                            if (!response.isSuccessful() || body == null) return;
                            List<TrackingPoint> points = ApiJson.list(body.string(), TrackingPoint.class);
                            if (points == null || points.size() < 2) return;
                            Collections.sort(points, (a, b) -> {
                                String ea = a.getEventTime() != null ? a.getEventTime() : "";
                                String eb = b.getEventTime() != null ? b.getEventTime() : "";
                                return ea.compareTo(eb);
                            });
                            List<LatLng> line = new ArrayList<>();
                            for (TrackingPoint p : points) {
                                if (p.getLatitude() != 0 || p.getLongitude() != 0) line.add(new LatLng(p.getLatitude(), p.getLongitude()));
                            }
                            // The API returns at most 500 points; a longer trip still ends where it ended.
                            if (points.size() >= ROUTE_PAGE_SIZE) line.add(new LatLng(trip.getEndLatitude(), trip.getEndLongitude()));
                            ROUTES.put(key, simplify(line, MAX_THUMBNAIL_POINTS));
                            notifyRouteReady(key);
                        } catch (Exception ignored) {
                            // keep the straight-line preview
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        inFlight.remove(key);
                    }
                });
    }

    private void notifyRouteReady(String key) {
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (!r.isDay && key.equals(routeKey(vehicleId, r.trip))) notifyItemChanged(i);
        }
    }

    /** Keeps every n-th point plus the last one: enough for a 76 x 96 dp thumbnail. */
    private static List<LatLng> simplify(List<LatLng> in, int max) {
        if (in.size() <= max) return in;
        List<LatLng> out = new ArrayList<>(max + 1);
        double step = (double) in.size() / max;
        for (double i = 0; i < in.size(); i += step) out.add(in.get((int) i));
        LatLng last = in.get(in.size() - 1);
        if (!out.get(out.size() - 1).equals(last)) out.add(last);
        return out;
    }

    // ── View holders ───────────────────────────────────────

    static final class DayHolder extends RecyclerView.ViewHolder {
        final TextView title, summary;

        DayHolder(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tvDayTitle);
            summary = v.findViewById(R.id.tvDaySummary);
        }
    }

    final class TripHolder extends RecyclerView.ViewHolder implements OnMapReadyCallback {
        final MapView mapView;
        final TextView times, distance, start, end, meta;
        @Nullable GoogleMap map;
        @Nullable TripSummary trip;

        TripHolder(@NonNull View v) {
            super(v);
            mapView = v.findViewById(R.id.liteMapTrip);
            times = v.findViewById(R.id.tvTripTimes);
            distance = v.findViewById(R.id.tvTripDistance);
            start = v.findViewById(R.id.tvTripStart);
            end = v.findViewById(R.id.tvTripEnd);
            meta = v.findViewById(R.id.tvTripMeta);
            Context c = v.getContext();
            start.setCompoundDrawablesRelativeWithIntrinsicBounds(dot(c, R.color.st_status_moving), null, null, null);
            end.setCompoundDrawablesRelativeWithIntrinsicBounds(dot(c, R.color.st_signal), null, null, null);
            if (mapView != null) {
                mapView.onCreate(null);
                mapView.getMapAsync(this);
            }
        }

        void bind(TripSummary t) {
            trip = t;
            Context c = itemView.getContext();
            times.setText(c.getString(R.string.st_trips_time_range, TimeText.clock(t.getStartTime()),
                    t.isInProgress() ? c.getString(R.string.st_trips_now) : TimeText.clock(t.getEndTime())));
            distance.setText(c.getString(R.string.st_trips_km_value, String.format(Locale.getDefault(), "%.1f", t.getDistanceKm())));
            meta.setText(c.getString(R.string.st_trips_meta, TimeText.duration(c, t.getDurationMinutes()),
                    Math.round(t.getMaxSpeed())));

            start.setText(R.string.st_trips_finding_place);
            end.setText(R.string.st_trips_finding_place);
            String key = routeKey(vehicleId, t);
            itemView.setTag(key);
            AddressResolver.resolveCached(c, t.getStartLatitude(), t.getStartLongitude(), name -> {
                if (key.equals(itemView.getTag())) start.setText(name);
            });
            AddressResolver.resolveCached(c, t.getEndLatitude(), t.getEndLongitude(), name -> {
                if (key.equals(itemView.getTag())) end.setText(name);
            });

            itemView.setContentDescription(c.getString(R.string.st_trips_card_description,
                    TimeText.clock(t.getStartTime()), TimeText.clock(t.getEndTime()),
                    String.format(Locale.getDefault(), "%.1f", t.getDistanceKm())));
            itemView.setOnClickListener(x -> {
                if (clickListener != null) clickListener.onTripClick(t);
            });

            if (ROUTES.get(key) == null) loadRoute(t, key);
            drawRoute();
        }

        @Override
        public void onMapReady(@NonNull GoogleMap googleMap) {
            map = googleMap;
            Context c = itemView.getContext();
            map.getUiSettings().setMapToolbarEnabled(false);
            // A tap on the lite map would otherwise open the Google Maps app.
            map.setOnMapClickListener(latLng -> itemView.performClick());
            map.setOnMarkerClickListener(m -> {
                itemView.performClick();
                return true;
            });
            try {
                map.setMapStyle(MapStyleOptions.loadRawResourceStyle(c, R.raw.map_style_daylight));
            } catch (Exception ignored) {
                // default style
            }
            drawRoute();
        }

        void drawRoute() {
            GoogleMap m = map;
            TripSummary t = trip;
            if (m == null || t == null) return;
            Context c = itemView.getContext();
            m.clear();
            m.setMapType(GoogleMap.MAP_TYPE_NORMAL);

            LatLng a = new LatLng(t.getStartLatitude(), t.getStartLongitude());
            LatLng b = new LatLng(t.getEndLatitude(), t.getEndLongitude());
            List<LatLng> route = ROUTES.get(routeKey(vehicleId, t));
            List<LatLng> line = route != null ? route : java.util.Arrays.asList(a, b);

            m.addPolyline(new PolylineOptions().addAll(line)
                    .width(3f * c.getResources().getDisplayMetrics().density)
                    .color(ContextCompat.getColor(c, R.color.st_navy)));
            m.addMarker(new MarkerOptions().position(a).anchor(0.5f, 0.5f).icon(markerDot(c, R.color.st_status_moving)));
            m.addMarker(new MarkerOptions().position(b).anchor(0.5f, 0.5f).icon(markerDot(c, R.color.st_signal)));

            LatLngBounds.Builder bounds = new LatLngBounds.Builder();
            for (LatLng p : line) bounds.include(p);
            float d = c.getResources().getDisplayMetrics().density;
            int w = Math.round(76 * d), h = Math.round(96 * d), pad = Math.round(12 * d);
            try {
                LatLngBounds bb = bounds.build();
                double span = Math.max(Math.abs(bb.northeast.latitude - bb.southwest.latitude),
                        Math.abs(bb.northeast.longitude - bb.southwest.longitude));
                if (span < 0.002) {
                    m.moveCamera(CameraUpdateFactory.newLatLngZoom(bb.getCenter(), 15f));
                } else {
                    m.moveCamera(CameraUpdateFactory.newLatLngBounds(bb, w, h, pad));
                }
            } catch (Exception e) {
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(a, 14f));
            }
        }

        void clearMap() {
            if (map != null) {
                map.clear();
                map.setMapType(GoogleMap.MAP_TYPE_NONE);
            }
        }
    }

    // ── Small drawing helpers ──────────────────────────────

    private static final Map<Integer, BitmapDescriptor> MARKER_DOTS = new java.util.HashMap<>();

    private static BitmapDescriptor markerDot(Context c, int colorRes) {
        BitmapDescriptor cached = MARKER_DOTS.get(colorRes);
        if (cached != null) return cached;
        float d = c.getResources().getDisplayMetrics().density;
        int size = Math.round(10 * d);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(ContextCompat.getColor(c, R.color.st_surface));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setColor(ContextCompat.getColor(c, colorRes));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1.5f * d, p);
        BitmapDescriptor out = BitmapDescriptorFactory.fromBitmap(bmp);
        MARKER_DOTS.put(colorRes, out);
        return out;
    }

    private static android.graphics.drawable.GradientDrawable dot(Context c, int colorRes) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(ContextCompat.getColor(c, colorRes));
        int px = Math.round(8 * c.getResources().getDisplayMetrics().density);
        g.setSize(px, px);
        return g;
    }
}
