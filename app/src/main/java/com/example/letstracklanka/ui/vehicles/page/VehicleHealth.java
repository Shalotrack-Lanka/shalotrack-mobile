package com.example.letstracklanka.ui.vehicles.page;

import android.content.Context;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.letstracklanka.R;
import com.example.letstracklanka.ui.common.TimeText;
import com.example.letstracklanka.utils.ApiJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The tracker health card. A line-for-line port of Fleet's app/Support/DeviceHealth.php so the phone and the
 * portal always agree:
 *  - online = gateway says connected AND last contact (latest of last seen / heartbeat) within 30 minutes
 *  - vehicle power disconnected = problem
 *  - device battery: 0 or missing = unknown (no row); 10 % or less = problem; 20 % or less = warning
 *  - mobile signal 0-4: 3-4 good, 1-2 weak (warning), 0 none (warning)
 *  - ignition is shown as a fact, never a fault
 * Overall level ranks offline above problem above warning above ok, and the headline uses Fleet's wording.
 */
final class VehicleHealth {

    static final int STALE_MINUTES = 30;
    static final int BATTERY_WARN = 20;
    static final int BATTERY_BAD = 10;

    enum Level {
        OK(R.color.st_status_moving_text, R.color.st_status_moving),
        INFO(R.color.st_text_1, R.color.st_text_4),
        WARN(R.color.st_status_offline_text, R.color.st_status_offline),
        BAD(R.color.st_danger_text, R.color.st_danger),
        OFFLINE(R.color.st_status_offline_text, R.color.st_status_offline);

        @ColorRes final int text;
        @ColorRes final int dot;

        Level(int text, int dot) {
            this.text = text;
            this.dot = dot;
        }
    }

    static final class Fact {
        final String label;
        final String value;
        final Level level;

        Fact(String label, String value, Level level) {
            this.label = label;
            this.value = value;
            this.level = level;
        }
    }

    final List<Fact> facts = new ArrayList<>();
    final Level overall;
    final String headline;
    @Nullable final String lastContact;
    @Nullable final String imeiTail;

    private VehicleHealth(List<Fact> facts, Level overall, String headline, @Nullable String lastContact, @Nullable String imeiTail) {
        this.facts.addAll(facts);
        this.overall = overall;
        this.headline = headline;
        this.lastContact = lastContact;
        this.imeiTail = imeiTail;
    }

    /** From GET api/DeviceStatus/vehicle/{id} ("data" object). */
    @NonNull
    static VehicleHealth from(@NonNull Context c, @NonNull JsonObject d) {
        List<Fact> f = new ArrayList<>();
        Level level = Level.OK;

        Instant seen = TimeText.parse(ApiJson.string(d, "lastSeen"));
        Instant beat = TimeText.parse(ApiJson.string(d, "lastHeartbeat"));
        Instant contact = seen == null ? beat : beat == null ? seen : (seen.isAfter(beat) ? seen : beat);
        boolean stale = contact == null || Math.abs(Duration.between(contact, Instant.now()).toMinutes()) > STALE_MINUTES;
        boolean online = bool(d, "isOnline") && !stale;
        if (!online) level = raise(level, Level.OFFLINE);
        f.add(new Fact(c.getString(R.string.st_health_connection),
                c.getString(online ? R.string.st_health_online : R.string.st_health_offline),
                online ? Level.OK : Level.OFFLINE));

        boolean unplugged = isDisconnected(ApiJson.get(d, "powerStatus"));
        f.add(new Fact(c.getString(R.string.st_health_power),
                c.getString(unplugged ? R.string.st_health_power_cut : R.string.st_health_power_ok),
                unplugged ? Level.BAD : Level.OK));
        if (unplugged) level = raise(level, Level.BAD);

        Integer battery = integer(d, "batteryLevel");
        if (battery != null && battery <= 0) battery = null;   // not reported (a GPS103 never does): unknown, not empty
        if (battery != null) {
            Level b = battery <= BATTERY_BAD ? Level.BAD : battery <= BATTERY_WARN ? Level.WARN : Level.OK;
            f.add(new Fact(c.getString(R.string.st_health_battery), c.getString(R.string.st_fact_battery, Math.min(100, battery)), b));
            level = raise(level, b);
        }

        Integer signal = integer(d, "gpsSignal");   // mobile-network strength 0-4, not GPS satellites
        if (signal != null) {
            Level g = signal >= 3 ? Level.OK : Level.WARN;
            int text = signal <= 0 ? R.string.st_health_no_signal : signal <= 2 ? R.string.st_fact_signal_weak : R.string.st_fact_signal_good;
            f.add(new Fact(c.getString(R.string.st_health_signal), c.getString(text), g));
            level = raise(level, g);
        }

        boolean ignition = bool(d, "ignitionStatus");
        f.add(new Fact(c.getString(R.string.st_health_ignition),
                c.getString(ignition ? R.string.st_fact_ignition_on : R.string.st_fact_ignition_off), Level.INFO));

        String headline;
        if (level == Level.OFFLINE) {
            headline = contact != null
                    ? c.getString(R.string.st_health_not_reporting_since, TimeText.ago(c, contact.toString()))
                    : c.getString(R.string.st_health_not_reporting);
        } else if (unplugged) {
            headline = c.getString(R.string.st_health_headline_power);
        } else if (battery != null && battery <= BATTERY_BAD) {
            headline = c.getString(R.string.st_health_headline_battery_critical);
        } else if (battery != null && battery <= BATTERY_WARN) {
            headline = c.getString(R.string.st_health_headline_battery_low);
        } else if (signal != null && signal <= 0) {
            headline = c.getString(R.string.st_health_no_signal);
        } else if (signal != null && signal <= 2) {
            headline = c.getString(R.string.st_health_headline_weak_signal);
        } else {
            headline = c.getString(R.string.st_health_all_normal);
        }

        String imei = ApiJson.string(d, "imeiNumber");
        String tail = imei != null && imei.length() >= 4 ? imei.substring(imei.length() - 4) : null;
        return new VehicleHealth(f, level, headline, contact != null ? TimeText.dayAndTime(c, contact) : null, tail);
    }

    private static Level raise(Level current, Level to) {
        return rank(to) > rank(current) ? to : current;
    }

    private static int rank(Level l) {
        switch (l) {
            case OFFLINE: return 3;
            case BAD: return 2;
            case WARN: return 1;
            default: return 0;
        }
    }

    private static boolean isDisconnected(@Nullable JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) return false;
        if (e.getAsJsonPrimitive().isNumber()) return e.getAsInt() == 1;   // PowerStatus.Disconnected
        return "Disconnected".equalsIgnoreCase(e.getAsString());
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = ApiJson.get(o, key);
        try {
            return e != null && e.isJsonPrimitive() && e.getAsBoolean();
        } catch (Exception ex) {
            return false;
        }
    }

    @Nullable
    private static Integer integer(JsonObject o, String key) {
        JsonElement e = ApiJson.get(o, key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
