package com.example.letstracklanka.ui.history;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.TripSummary;
import com.example.letstracklanka.ui.main.AddressResolver;
import com.google.android.material.button.MaterialButton;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Groups a flat List<TripSummary> into day-header + trip-card rows for
 * rvTripHistory. TripsReportResponse itself is a flat list (confirmed by
 * reading the actual model) -- day grouping is computed here from each
 * trip's real startTime, not sourced from any backend grouping.
 *
 * "Post" and "Save Place" are intentionally NOT wired here -- both hidden
 * (visibility="gone") in the layouts per explicit decision to defer them
 * past this deployment. Nothing in this adapter references them.
 */
public class TripHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_DAY_HEADER = 0;
    private static final int VIEW_TYPE_TRIP_CARD = 1;

    public interface OnTripClickListener {
        void onTripClick(TripSummary trip);
    }

    private static class Row {
        final boolean isHeader;
        final String dayKey;
        final String dayTitle;
        final String daySummary;
        final TripSummary trip;
        final String tripKey;

        static Row header(String dayKey, String dayTitle, String daySummary) {
            return new Row(true, dayKey, dayTitle, daySummary, null, null);
        }

        static Row card(String dayKey, TripSummary trip, String tripKey) {
            return new Row(false, dayKey, null, null, trip, tripKey);
        }

        private Row(boolean isHeader, String dayKey, String dayTitle, String daySummary, TripSummary trip, String tripKey) {
            this.isHeader = isHeader;
            this.dayKey = dayKey;
            this.dayTitle = dayTitle;
            this.daySummary = daySummary;
            this.trip = trip;
            this.tripKey = tripKey;
        }
    }

    private final OnTripClickListener clickListener;
    private final Map<String, List<TripSummary>> tripsByDay = new LinkedHashMap<>();
    private final Map<String, String> dayTitles = new HashMap<>();
    private final Map<String, String> daySummaries = new HashMap<>();
    private final Set<String> collapsedDays = new HashSet<>();
    private List<Row> rows = new ArrayList<>();

    // Same fix as VehicleListAdapter: AddressResolver keeps a single
    // lastLat/lastLng/lastAddress per INSTANCE, not per trip -- one shared
    // resolver across every row would let one trip's cached address bleed
    // into another's. Keyed by a synthetic trip identity (TripSummary has
    // no id field of its own), with cleanup on every updateTrips() so a
    // long history session doesn't accumulate one Geocoder/executor per
    // trip ever seen -- more important here than for the vehicle list,
    // since a trip history can realistically have far more rows.
    private final Map<String, AddressResolver> addressResolvers = new HashMap<>();

    private final SimpleDateFormat isoParser;
    private final SimpleDateFormat dayKeyFormat;
    private final SimpleDateFormat dayTitleFormat;
    private final SimpleDateFormat timeFormat;

    public TripHistoryAdapter(OnTripClickListener clickListener) {
        this.clickListener = clickListener;
        isoParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        isoParser.setTimeZone(TimeZone.getTimeZone("UTC"));
        dayKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        dayTitleFormat = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());
        timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    }

    public void updateTrips(List<TripSummary> trips) {
        tripsByDay.clear();
        dayTitles.clear();
        daySummaries.clear();

        Set<String> currentTripKeys = new HashSet<>();

        if (trips != null) {
            // "Latest first" -- sort newest-to-oldest by real start time
            // before grouping. tripsByDay is a LinkedHashMap (preserves
            // insertion order), so grouping from an already-descending list
            // naturally puts both the day sections AND the trips within
            // each day in latest-first order, in one pass.
            List<TripSummary> sorted = new ArrayList<>(trips);
            Collections.sort(sorted, (a, b) -> {
                Date da = parseIso(a.getStartTime());
                Date db = parseIso(b.getStartTime());
                if (da == null || db == null) return 0;
                return db.compareTo(da); // descending: b before a
            });

            for (TripSummary trip : sorted) {
                Date start = parseIso(trip.getStartTime());
                if (start == null) continue; // can't group a trip whose start time doesn't parse

                String dayKey = dayKeyFormat.format(start);
                tripsByDay.computeIfAbsent(dayKey, k -> new ArrayList<>()).add(trip);
                currentTripKeys.add(tripKeyFor(trip));
            }
        }

        // Cheap cleanup: drop resolvers for trips no longer in the current
        // report window.
        addressResolvers.keySet().retainAll(currentTripKeys);

        for (Map.Entry<String, List<TripSummary>> entry : tripsByDay.entrySet()) {
            String dayKey = entry.getKey();
            List<TripSummary> dayTrips = entry.getValue();

            double totalKm = 0;
            double totalMinutes = 0;
            for (TripSummary t : dayTrips) {
                totalKm += t.getDistanceKm();
                totalMinutes += t.getDurationMinutes();
            }

            Date dayDate;
            try {
                dayDate = dayKeyFormat.parse(dayKey);
            } catch (ParseException e) {
                dayDate = new Date();
            }
            String dayOfWeek = dayTitleFormat.format(dayDate);

            dayTitles.put(dayKey, dayOfWeek + " - " + dayTrips.size() + (dayTrips.size() == 1 ? " Trip" : " Trips"));
            daySummaries.put(dayKey, "Total - " + String.format(Locale.getDefault(), "%.0f km in %s", totalKm, formatDuration(totalMinutes)));
        }

        rebuildRows();
    }

    private void rebuildRows() {
        List<Row> newRows = new ArrayList<>();
        for (String dayKey : tripsByDay.keySet()) {
            newRows.add(Row.header(dayKey, dayTitles.get(dayKey), daySummaries.get(dayKey)));
            if (!collapsedDays.contains(dayKey)) {
                for (TripSummary trip : tripsByDay.get(dayKey)) {
                    newRows.add(Row.card(dayKey, trip, tripKeyFor(trip)));
                }
            }
        }
        rows = newRows;
        notifyDataSetChanged();
    }

    private String tripKeyFor(TripSummary trip) {
        return trip.getStartTime() + "_" + trip.getEndTime();
    }

    private Date parseIso(String iso) {
        if (iso == null) return null;
        try {
            return isoParser.parse(iso);
        } catch (ParseException e) {
            return null;
        }
    }

    private String formatDuration(double totalMinutes) {
        int hours = (int) (totalMinutes / 60);
        int minutes = (int) (totalMinutes % 60);
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isHeader ? VIEW_TYPE_DAY_HEADER : VIEW_TYPE_TRIP_CARD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_DAY_HEADER) {
            return new DayHeaderViewHolder(inflater.inflate(R.layout.item_trip_history_day_header, parent, false));
        }
        return new TripCardViewHolder(inflater.inflate(R.layout.item_trip_history_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);

        if (holder instanceof DayHeaderViewHolder) {
            DayHeaderViewHolder h = (DayHeaderViewHolder) holder;
            h.tvDayTitle.setText(row.dayTitle);
            h.tvDaySummary.setText(row.daySummary);

            // "▶ Day" is being used here as an expand/collapse toggle for
            // that day's trip cards -- a deliberate, deliberately-simple
            // interpretation, NOT a full multi-trip combined-playback
            // feature (which would need stitching multiple trips' GPS
            // point sets into one continuous session -- a much bigger,
            // separate feature on its own).
            boolean collapsed = collapsedDays.contains(row.dayKey);
            h.tvToggleDayLabel.setText(collapsed ? "▶ Day" : "▼ Day");
            h.btnToggleDay.setOnClickListener(v -> {
                if (collapsedDays.contains(row.dayKey)) collapsedDays.remove(row.dayKey);
                else collapsedDays.add(row.dayKey);
                rebuildRows();
            });
            return;
        }

        TripCardViewHolder h = (TripCardViewHolder) holder;
        TripSummary trip = row.trip;
        String tripKey = row.tripKey;

        h.itemView.setTag(tripKey); // guards the async address callback below against recycling

        h.tvTripDistance.setText(String.format(Locale.getDefault(), "%.0f km Trip", trip.getDistanceKm()));
        h.tvTripTime.setText(formatTime(trip.getStartTime()) + " - " + formatTime(trip.getEndTime())
                + " (" + formatDuration(trip.getDurationMinutes()) + ")");
        h.tvTopSpeed.setText("Top Speed: " + (int) trip.getMaxSpeed() + " kph");

        h.tvTripAddresses.setText("Resolving location...");
        h.tvStartPlaceName.setText("Resolving...");
        h.tvEndPlaceName.setText("Resolving...");

        AddressResolver resolver = addressResolvers.get(tripKey);
        if (resolver == null) {
            resolver = new AddressResolver(h.itemView.getContext());
            addressResolvers.put(tripKey, resolver);
        }

        resolver.resolveAddress(trip.getStartLatitude(), trip.getStartLongitude(), startAddress -> {
            if (tripKey.equals(h.itemView.getTag())) {
                h.tvStartPlaceName.setText(startAddress);
                updateCombinedAddress(h, startAddress, null);
            }
        });
        resolver.resolveAddress(trip.getEndLatitude(), trip.getEndLongitude(), endAddress -> {
            if (tripKey.equals(h.itemView.getTag())) {
                h.tvEndPlaceName.setText(endAddress);
                updateCombinedAddress(h, null, endAddress);
            }
        });

        h.layoutTripIconClick.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onTripClick(trip);
        });

        // Map thumbnail: sized to roughly match the ImageView's actual
        // layout dimensions (140dp tall, full card width) converted to
        // px, so we're not requesting/paying for a bigger image than
        // will ever be displayed.
        if (h.ivTripMap != null) {
            float density = h.itemView.getResources().getDisplayMetrics().density;
            int widthPx = h.itemView.getResources().getDisplayMetrics().widthPixels;
            int heightPx = (int) (140 * density);
            StaticMapLoader.load(h.ivTripMap, trip.getStartLatitude(), trip.getStartLongitude(),
                    trip.getEndLatitude(), trip.getEndLongitude(), widthPx, heightPx);
        }

        if (h.btnPostTrip != null) {
            h.btnPostTrip.setOnClickListener(v -> shareTrip(h.itemView.getContext(), trip, h));
        }
    }

    // Native Android share sheet -- real trip data, no proprietary "post to
    // a feed" backend (doesn't exist), no fake caption text, no competitor
    // branding/URL like the original mock had.
    private void shareTrip(android.content.Context context, TripSummary trip, TripCardViewHolder h) {
        String start = h.tvStartPlaceName != null ? h.tvStartPlaceName.getText().toString() : "start";
        String end = h.tvEndPlaceName != null ? h.tvEndPlaceName.getText().toString() : "end";
        // Addresses resolve asynchronously; if the user taps Share before
        // they've finished, this can still say "Resolving..." -- a known,
        // minor edge case, not worth blocking/disabling the button for.

        String shareText = String.format(Locale.getDefault(),
                "Trip: %.0f km\n%s - %s (%s)\nFrom %s\nTo %s",
                trip.getDistanceKm(),
                formatTime(trip.getStartTime()), formatTime(trip.getEndTime()),
                formatDuration(trip.getDurationMinutes()),
                start, end);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        context.startActivity(Intent.createChooser(shareIntent, "Share trip"));
    }

    // tvTripAddresses shows "start - end" combined; since the two halves
    // resolve independently and asynchronously, this rebuilds it from
    // whatever's currently in the two separate labels rather than needing
    // to track resolution state separately.
    private void updateCombinedAddress(TripCardViewHolder h, String startOverride, String endOverride) {
        String start = startOverride != null ? startOverride : h.tvStartPlaceName.getText().toString();
        String end = endOverride != null ? endOverride : h.tvEndPlaceName.getText().toString();
        h.tvTripAddresses.setText(start + " - " + end);
    }

    private String formatTime(String iso) {
        Date d = parseIso(iso);
        return d != null ? timeFormat.format(d) : "--:--";
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class DayHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDayTitle, tvDaySummary, tvToggleDayLabel;
        View btnToggleDay;

        DayHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDayTitle = itemView.findViewById(R.id.tvDayTitle);
            tvDaySummary = itemView.findViewById(R.id.tvDaySummary);
            tvToggleDayLabel = itemView.findViewById(R.id.tvToggleDayLabel);
            btnToggleDay = itemView.findViewById(R.id.btnToggleDay);
        }
    }

    static class TripCardViewHolder extends RecyclerView.ViewHolder {
        TextView tvTripDistance, tvTripAddresses, tvTripTime, tvTopSpeed, tvStartPlaceName, tvEndPlaceName;
        View layoutTripIconClick;
        ImageView ivTripMap;
        MaterialButton btnPostTrip;

        TripCardViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTripDistance = itemView.findViewById(R.id.tvTripDistance);
            tvTripAddresses = itemView.findViewById(R.id.tvTripAddresses);
            tvTripTime = itemView.findViewById(R.id.tvTripTime);
            tvTopSpeed = itemView.findViewById(R.id.tvTopSpeed);
            tvStartPlaceName = itemView.findViewById(R.id.tvStartPlaceName);
            tvEndPlaceName = itemView.findViewById(R.id.tvEndPlaceName);
            layoutTripIconClick = itemView.findViewById(R.id.layoutTripIconClick);
            ivTripMap = itemView.findViewById(R.id.ivTripMap);
            btnPostTrip = itemView.findViewById(R.id.btnPostTrip);
        }
    }
}