package com.example.letstracklanka.ui.common;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.letstracklanka.R;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Turns the API's UTC timestamps into what people read: "12 s ago", "5 min ago", "Today, 14:32",
 * "Yesterday, 08:10", "Mon 16 Nov, 08:10". Always shown in Sri Lanka time (the phone's zone).
 * Accepts timestamps with or without a zone suffix; a timestamp without one is treated as UTC,
 * because every timestamp the API stores is UTC.
 */
public final class TimeText {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault());
    private static final DateTimeFormatter DAY_LONG = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault());
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault());

    private TimeText() {}

    @Nullable
    public static Instant parse(@Nullable String iso) {
        if (iso == null || iso.trim().isEmpty()) return null;
        String s = iso.trim();
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (Exception ignored) {
            // no zone suffix
        }
        try {
            return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** "12 Mar 2027" in the phone's time zone, or "" when the value cannot be read. */
    public static String date(@Nullable String iso) {
        Instant t = parse(iso);
        return t == null ? "" : DATE.format(t.atZone(java.time.ZoneId.systemDefault()));
    }

    /** "12 s ago", "5 min ago", "2 h ago", then "Yesterday, 08:10" / "Mon 16 Nov, 08:10". */
    public static String ago(Context c, @Nullable String iso) {
        Instant t = parse(iso);
        if (t == null) return c.getString(R.string.st_time_never);
        long s = Math.max(0, Duration.between(t, Instant.now()).getSeconds());
        if (s < 60) return c.getString(R.string.st_time_seconds_ago, s);
        if (s < 3600) return c.getString(R.string.st_time_minutes_ago, s / 60);
        if (s < 6 * 3600) return c.getString(R.string.st_time_hours_ago, s / 3600);
        return dayAndTime(c, t);
    }

    /** "Today, 14:32" / "Yesterday, 08:10" / "Mon 16 Nov, 08:10". */
    public static String dayAndTime(Context c, Instant t) {
        ZonedDateTime local = t.atZone(ZoneId.systemDefault());
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        String time = local.format(TIME);
        if (local.toLocalDate().equals(today)) return c.getString(R.string.st_time_today_at, time);
        if (local.toLocalDate().equals(today.minusDays(1))) return c.getString(R.string.st_time_yesterday_at, time);
        return c.getString(R.string.st_time_day_at, local.format(DAY), time);
    }

    /** "14:32" in local time, or "--:--". */
    public static String clock(@Nullable String iso) {
        Instant t = parse(iso);
        return t == null ? "--:--" : t.atZone(ZoneId.systemDefault()).format(TIME);
    }

    /** Section header: "Today", "Yesterday" or "Wednesday 18 November". */
    public static String dayHeader(Context c, LocalDate day) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (day.equals(today)) return c.getString(R.string.st_time_today);
        if (day.equals(today.minusDays(1))) return c.getString(R.string.st_time_yesterday);
        return day.format(DAY_LONG);
    }

    @Nullable
    public static LocalDate localDay(@Nullable String iso) {
        Instant t = parse(iso);
        return t == null ? null : t.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** "1:42" for minutes (hours:minutes), "35 min" under an hour. */
    public static String duration(Context c, double minutes) {
        long m = Math.round(minutes);
        if (m < 60) return c.getString(R.string.st_duration_minutes, m);
        return String.format(Locale.getDefault(), "%d:%02d", m / 60, m % 60);
    }
}
