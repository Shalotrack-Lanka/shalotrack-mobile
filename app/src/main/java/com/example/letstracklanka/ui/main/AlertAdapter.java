package com.example.letstracklanka.ui.main;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.AlertResponse;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Groups a flat List<AlertResponse> into day-header + alert-card rows,
 * same architecture already proven in TripHistoryAdapter -- day grouping
 * is computed here from each alert's real triggeredAt, the API itself
 * returns a flat list. Sorted latest-first, matching the same convention
 * already established for Trip History.
 */
public class AlertAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_DAY_HEADER = 0;
    private static final int VIEW_TYPE_VEHICLE_HEADER = 1;
    private static final int VIEW_TYPE_ALERT_CARD = 2;

    public interface OnAlertClickListener {
        void onAlertClick(AlertResponse alert);
    }

    private enum RowType { DAY_HEADER, VEHICLE_HEADER, ALERT_CARD }

    private static class Row {
        final RowType type;
        final String dayTitle;
        final int dayCount;
        final String vehicleNumber;
        final AlertResponse alert;

        static Row dayHeader(String dayTitle, int dayCount) {
            return new Row(RowType.DAY_HEADER, dayTitle, dayCount, null, null);
        }

        static Row vehicleHeader(String vehicleNumber) {
            return new Row(RowType.VEHICLE_HEADER, null, 0, vehicleNumber, null);
        }

        static Row card(AlertResponse alert) {
            return new Row(RowType.ALERT_CARD, null, 0, null, alert);
        }

        private Row(RowType type, String dayTitle, int dayCount, String vehicleNumber, AlertResponse alert) {
            this.type = type;
            this.dayTitle = dayTitle;
            this.dayCount = dayCount;
            this.vehicleNumber = vehicleNumber;
            this.alert = alert;
        }
    }

    private final OnAlertClickListener listener;
    private List<Row> rows = new ArrayList<>();

    private final SimpleDateFormat isoParser;
    private final SimpleDateFormat dayKeyFormat;
    private final SimpleDateFormat dayTitleFormat;
    private final SimpleDateFormat timeFormat;

    public AlertAdapter(List<AlertResponse> alerts, OnAlertClickListener listener) {
        this.listener = listener;
        isoParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        isoParser.setTimeZone(TimeZone.getTimeZone("UTC"));
        dayKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        dayTitleFormat = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());
        timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        updateAlerts(alerts);
    }

    public void updateAlerts(List<AlertResponse> newAlerts) {
        Map<String, List<AlertResponse>> byDay = new LinkedHashMap<>();
        boolean multiVehicle = false;

        if (newAlerts != null) {
            List<AlertResponse> sorted = new ArrayList<>();
            for (AlertResponse a : newAlerts) {
                if (a != null) sorted.add(a); // guards against a malformed API response containing a null entry
            }
            Collections.sort(sorted, (a, b) -> {
                Date da = parseIso(a.getTriggeredAt());
                Date db = parseIso(b.getTriggeredAt());
                if (da == null || db == null) return 0;
                return db.compareTo(da); // latest first, matching Trip History's convention
            });

            // NEW: auto-detects whether this list spans more than one
            // vehicle. Screens already filtered to a single vehicle (e.g.
            // opened from that vehicle's own detail panel) naturally have
            // every alert sharing one vehicleId, so this needs no separate
            // flag passed in from AlertsActivity -- the data itself
            // already implies whether vehicle sub-headers add any value.
            String firstVehicleId = null;
            for (AlertResponse a : sorted) {
                if (firstVehicleId == null) {
                    firstVehicleId = a.getVehicleId();
                } else if (a.getVehicleId() == null || !a.getVehicleId().equals(firstVehicleId)) {
                    multiVehicle = true;
                    break;
                }
            }

            for (AlertResponse alert : sorted) {
                Date triggered = parseIso(alert.getTriggeredAt());
                if (triggered == null) continue; // can't group an alert whose time doesn't parse
                String dayKey = dayKeyFormat.format(triggered);
                byDay.computeIfAbsent(dayKey, k -> new ArrayList<>()).add(alert);
            }
        }

        List<Row> newRows = new ArrayList<>();
        for (Map.Entry<String, List<AlertResponse>> dayEntry : byDay.entrySet()) {
            Date dayDate;
            try {
                dayDate = dayKeyFormat.parse(dayEntry.getKey());
            } catch (ParseException e) {
                dayDate = new Date();
            }
            String title = dayTitleFormat.format(dayDate);
            List<AlertResponse> dayAlerts = dayEntry.getValue();
            newRows.add(Row.dayHeader(title, dayAlerts.size()));

            if (!multiVehicle) {
                // Single-vehicle list: flat, same as before this change.
                for (AlertResponse alert : dayAlerts) {
                    newRows.add(Row.card(alert));
                }
                continue;
            }

            // Multi-vehicle: sub-group within this day. Since dayAlerts is
            // already time-sorted latest-first, a LinkedHashMap naturally
            // orders vehicle sub-groups by "most recent activity first"
            // within the day, as a side effect of insertion order -- same
            // trick already used for the day-level grouping itself.
            Map<String, List<AlertResponse>> byVehicle = new LinkedHashMap<>();
            Map<String, String> vehicleNumbers = new LinkedHashMap<>();
            for (AlertResponse alert : dayAlerts) {
                String vId = alert.getVehicleId() != null ? alert.getVehicleId() : "unknown";
                byVehicle.computeIfAbsent(vId, k -> new ArrayList<>()).add(alert);
                vehicleNumbers.putIfAbsent(vId, alert.getVehicleNumber() != null ? alert.getVehicleNumber() : "Unknown vehicle");
            }

            for (Map.Entry<String, List<AlertResponse>> vEntry : byVehicle.entrySet()) {
                newRows.add(Row.vehicleHeader(vehicleNumbers.get(vEntry.getKey())));
                for (AlertResponse alert : vEntry.getValue()) {
                    newRows.add(Row.card(alert));
                }
            }
        }

        this.rows = newRows;
        notifyDataSetChanged();
    }

    private Date parseIso(String isoUtc) {
        if (isoUtc == null) return null;
        try {
            String trimmed = isoUtc.length() > 19 ? isoUtc.substring(0, 19) : isoUtc;
            return isoParser.parse(trimmed);
        } catch (ParseException e) {
            return null;
        }
    }

    @Override
    public int getItemViewType(int position) {
        switch (rows.get(position).type) {
            case DAY_HEADER: return VIEW_TYPE_DAY_HEADER;
            case VEHICLE_HEADER: return VIEW_TYPE_VEHICLE_HEADER;
            default: return VIEW_TYPE_ALERT_CARD;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_DAY_HEADER) {
            return new DayHeaderViewHolder(inflater.inflate(R.layout.item_alert_day_header, parent, false));
        }
        if (viewType == VIEW_TYPE_VEHICLE_HEADER) {
            return new VehicleHeaderViewHolder(inflater.inflate(R.layout.item_alert_vehicle_header, parent, false));
        }
        return new AlertViewHolder(inflater.inflate(R.layout.item_alert, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);

        if (holder instanceof DayHeaderViewHolder) {
            DayHeaderViewHolder h = (DayHeaderViewHolder) holder;
            h.tvDayTitle.setText(row.dayTitle);
            h.tvDayCount.setText(row.dayCount == 1 ? "1 alert" : row.dayCount + " alerts");
            return;
        }

        if (holder instanceof VehicleHeaderViewHolder) {
            VehicleHeaderViewHolder h = (VehicleHeaderViewHolder) holder;
            h.tvVehicleHeader.setText(row.vehicleNumber);
            return;
        }

        AlertViewHolder h = (AlertViewHolder) holder;
        AlertResponse alert = row.alert;

        h.tvAlertType.setText(formatAlertType(alert.getAlertType()));
        h.tvAlertMessage.setText(alert.getMessage());
        h.tvAlertVehicle.setText(alert.getVehicleNumber());
        h.tvAlertTime.setText(formatTime(alert.getTriggeredAt()));

        int color = colorForAlertType(alert.getAlertType());
        h.iconCard.setCardBackgroundColor(color);
        h.tvAlertIcon.setText(glyphForAlertType(alert.getAlertType()));

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(alert.isRead() ? Color.TRANSPARENT : Color.parseColor("#E53935"));
        h.dotUnread.setBackground(dot);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAlertClick(alert);
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private String formatAlertType(String rawType) {
        if (rawType == null) return "Alert";
        // "IgnitionOn" -> "Ignition On" (simple camelCase splitter, no new deps)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rawType.length(); i++) {
            char c = rawType.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) sb.append(' ');
            sb.append(c);
        }
        return sb.toString();
    }

    private String formatTime(String isoUtc) {
        Date date = parseIso(isoUtc);
        return date != null ? timeFormat.format(date) : "";
    }

    // Color/glyph per alert type -- set in code rather than as separate
    // drawable resources, matching how the unread dot was already handled
    // in the original layout. Colors are a judgment call grouped by rough
    // severity (red = safety/security-relevant, amber = warning, blue =
    // informational), not something specified anywhere -- easy to retune
    // if the actual alert type list or severity grouping differs from
    // what's assumed here.
    private int colorForAlertType(String rawType) {
        if (rawType == null) return Color.parseColor("#9E9E9E");
        switch (rawType) {
            case "Overspeed":
                return Color.parseColor("#E53935"); // red -- safety-relevant
            case "PowerCut":
                return Color.parseColor("#D32F2F"); // red -- security-relevant
            case "LowBattery":
                return Color.parseColor("#FB8C00"); // amber -- warning
            case "DeviceOffline":
                return Color.parseColor("#757575"); // gray -- informational but concerning
            case "IgnitionOn":
                return Color.parseColor("#43A047"); // green -- informational
            case "IgnitionOff":
                return Color.parseColor("#1877F2"); // blue -- informational
            default:
                return Color.parseColor("#9E9E9E");
        }
    }

    private String glyphForAlertType(String rawType) {
        if (rawType == null) return "!";
        switch (rawType) {
            case "Overspeed": return "\u26A0"; // ⚠
            case "PowerCut": return "\u26A1"; // ⚡
            case "LowBattery": return "\uD83D\uDD0B"; // 🔋
            case "DeviceOffline": return "\u25CF"; // ●
            case "IgnitionOn": return "\u2713"; // ✓
            case "IgnitionOff": return "\u25CB"; // ○
            default: return "!";
        }
    }

    static class DayHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDayTitle, tvDayCount;

        DayHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDayTitle = itemView.findViewById(R.id.tvAlertDayTitle);
            tvDayCount = itemView.findViewById(R.id.tvAlertDayCount);
        }
    }

    static class VehicleHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvVehicleHeader;

        VehicleHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvVehicleHeader = itemView.findViewById(R.id.tvAlertVehicleHeader);
        }
    }

    static class AlertViewHolder extends RecyclerView.ViewHolder {
        View dotUnread;
        CardView iconCard;
        TextView tvAlertIcon, tvAlertType, tvAlertMessage, tvAlertVehicle, tvAlertTime;

        AlertViewHolder(@NonNull View itemView) {
            super(itemView);
            dotUnread = itemView.findViewById(R.id.dotUnread);
            iconCard = itemView.findViewById(R.id.cardAlertIcon);
            tvAlertIcon = itemView.findViewById(R.id.tvAlertIcon);
            tvAlertType = itemView.findViewById(R.id.tvAlertType);
            tvAlertMessage = itemView.findViewById(R.id.tvAlertMessage);
            tvAlertVehicle = itemView.findViewById(R.id.tvAlertVehicle);
            tvAlertTime = itemView.findViewById(R.id.tvAlertTime);
        }
    }
}