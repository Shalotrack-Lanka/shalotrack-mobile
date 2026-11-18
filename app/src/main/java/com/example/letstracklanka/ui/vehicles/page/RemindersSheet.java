package com.example.letstracklanka.ui.vehicles.page;

import android.annotation.SuppressLint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.more.MoreRows;
import com.example.letstracklanka.utils.ApiErrors;
import com.example.letstracklanka.utils.ApiJson;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Reminders (bottom_sheet_reminders): one per kind per vehicle, same as Fleet. Tap a kind to pick its date
 * (Save) or, when one is set, remove it (the picker's second button). The server sends the push before
 * the date; the app only stores the date.
 */
public final class RemindersSheet {

    public interface Listener {
        /** Text for the page row, for example "Insurance in 12 days", or null when none is set. */
        void onRemindersChanged(@Nullable String soonest);
    }

    private enum Kind {
        LICENCE(0, R.string.st_reminder_licence, R.drawable.ic_st_file),
        INSURANCE(1, R.string.st_reminder_insurance, R.drawable.ic_st_shield),
        SERVICE(2, R.string.st_reminder_service, R.drawable.ic_st_sliders);

        final int api;
        @StringRes final int title;
        @DrawableRes final int icon;

        Kind(int api, int title, int icon) {
            this.api = api;
            this.title = title;
            this.icon = icon;
        }
    }

    private static final class Reminder {
        final String id;
        final LocalDate due;
        final int daysLeft;

        Reminder(String id, LocalDate due, int daysLeft) {
            this.id = id;
            this.due = due;
            this.daysLeft = daysLeft;
        }
    }

    private static final DateTimeFormatter SHOW = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault());

    private final AppCompatActivity activity;
    private final String vehicleId;
    private final Listener listener;
    private final ApiService api = ApiClient.getClient().create(ApiService.class);
    private final BottomSheetDialog sheet;
    private final View progress;
    private final TextView tvError;
    private final ViewGroup rows;
    private final Map<Kind, Reminder> byKind = new HashMap<>();
    private boolean busy;

    @SuppressLint("InflateParams") // dialog content has no parent to measure against
    private RemindersSheet(AppCompatActivity activity, String vehicleId, Listener listener) {
        this.activity = activity;
        this.vehicleId = vehicleId;
        this.listener = listener;
        sheet = new BottomSheetDialog(activity);
        View v = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_reminders, null);
        sheet.setContentView(v);
        progress = v.findViewById(R.id.progressReminders);
        tvError = v.findViewById(R.id.tvRemindersError);
        rows = v.findViewById(R.id.reminderRows);
    }

    public static void show(@NonNull AppCompatActivity activity, @NonNull String vehicleId, @NonNull Listener listener) {
        RemindersSheet s = new RemindersSheet(activity, vehicleId, listener);
        s.sheet.show();
        s.load();
    }

    /** Reads the reminders and reports the soonest to the page, without showing anything. */
    public static void loadSummary(@NonNull AppCompatActivity activity, @NonNull String vehicleId, @NonNull Listener listener) {
        ApiClient.getClient().create(ApiService.class).getVehicleReminders(vehicleId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    ApiErrors.close(response);
                    return;
                }
                Map<Kind, Reminder> m = new HashMap<>();
                try (ResponseBody b = response.body()) {
                    parse(b != null ? ApiJson.dataArray(b.string()) : null, m);
                } catch (Exception ignored) {
                    return;
                }
                if (!activity.isDestroyed()) listener.onRemindersChanged(soonest(activity, m));
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                // the row keeps its neutral text
            }
        });
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        rows.setVisibility(View.GONE);
        api.getVehicleReminders(vehicleId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!sheet.isShowing()) {
                    ApiErrors.close(response);
                    return;
                }
                progress.setVisibility(View.GONE);
                if (!response.isSuccessful()) {
                    showError(ApiErrors.read(response, activity.getString(R.string.st_reminders_load_failed)).message);
                    return;
                }
                byKind.clear();
                try (ResponseBody b = response.body()) {
                    parse(b != null ? ApiJson.dataArray(b.string()) : null, byKind);
                } catch (Exception ignored) {
                    showError(activity.getString(R.string.st_reminders_load_failed));
                    return;
                }
                tvError.setVisibility(View.GONE);
                render();
                listener.onRemindersChanged(soonest(activity, byKind));
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!sheet.isShowing()) return;
                progress.setVisibility(View.GONE);
                showError(activity.getString(R.string.st_error_network));
            }
        });
    }

    private static void parse(@Nullable JsonArray arr, Map<Kind, Reminder> out) {
        if (arr == null) return;
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            JsonElement typeEl = ApiJson.get(o, "type");
            String due = ApiJson.string(o, "dueDate");
            String id = ApiJson.string(o, "reminderId");
            JsonElement left = ApiJson.get(o, "daysLeft");
            if (typeEl == null || due == null || id == null) continue;
            Kind kind = null;
            int t = typeEl.getAsInt();
            for (Kind k : Kind.values()) if (k.api == t) kind = k;
            if (kind == null) continue;
            try {
                LocalDate d = LocalDate.parse(due.length() > 10 ? due.substring(0, 10) : due);
                out.put(kind, new Reminder(id, d, left != null && left.isJsonPrimitive() ? left.getAsInt()
                        : (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), d)));
            } catch (Exception ignored) {
                // skip a date the app cannot read
            }
        }
    }

    private void render() {
        rows.removeAllViews();
        for (Kind k : Kind.values()) {
            Reminder r = byKind.get(k);
            String detail = r == null ? activity.getString(R.string.st_reminder_not_set) : describe(activity, r);
            MoreRows.add(rows, k.icon, activity.getString(k.title), detail, false, v -> pick(k));
        }
        rows.setVisibility(View.VISIBLE);
    }

    private static String describe(android.content.Context c, Reminder r) {
        String date = SHOW.format(r.due);
        if (r.daysLeft < 0) return c.getResources().getQuantityString(R.plurals.st_reminder_overdue, -r.daysLeft, date, -r.daysLeft);
        if (r.daysLeft == 0) return c.getString(R.string.st_reminder_due_today, date);
        return c.getResources().getQuantityString(R.plurals.st_reminder_due_in, r.daysLeft, date, r.daysLeft);
    }

    @Nullable
    private static String soonest(android.content.Context c, Map<Kind, Reminder> m) {
        Kind best = null;
        for (Map.Entry<Kind, Reminder> e : m.entrySet()) {
            if (best == null || e.getValue().due.isBefore(m.get(best).due)) best = e.getKey();
        }
        if (best == null) return null;
        Reminder r = m.get(best);
        String name = c.getString(best.title);
        if (r.daysLeft < 0) return c.getString(R.string.st_reminder_row_overdue, name);
        if (r.daysLeft == 0) return c.getString(R.string.st_reminder_row_today, name);
        return c.getResources().getQuantityString(R.plurals.st_reminder_row_in, r.daysLeft, name, r.daysLeft);
    }

    private void pick(Kind k) {
        if (busy) return;
        Reminder current = byKind.get(k);
        LocalDate start = current != null ? current.due : LocalDate.now().plusMonths(1);
        MaterialDatePicker.Builder<Long> b = MaterialDatePicker.Builder.datePicker()
                .setTitleText(activity.getString(k.title))
                .setSelection(start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                .setPositiveButtonText(activity.getString(R.string.st_action_save));
        if (current != null) b.setNegativeButtonText(activity.getString(R.string.st_reminder_remove));
        MaterialDatePicker<Long> picker = b.build();
        picker.addOnPositiveButtonClickListener(sel -> {
            if (sel == null) return;
            LocalDate d = Instant.ofEpochMilli(sel).atZone(ZoneOffset.UTC).toLocalDate();
            save(k, d);
        });
        if (current != null) picker.addOnNegativeButtonClickListener(x -> remove(current));
        picker.show(activity.getSupportFragmentManager(), "reminder_" + k.api);
    }

    private void save(Kind k, LocalDate due) {
        busy = true;
        progress.setVisibility(View.VISIBLE);
        JsonObject body = new JsonObject();
        body.addProperty("type", k.api);
        body.addProperty("dueDate", due.toString());
        api.upsertVehicleReminder(vehicleId, body).enqueue(done(R.string.st_reminders_save_failed));
    }

    private void remove(Reminder r) {
        busy = true;
        progress.setVisibility(View.VISIBLE);
        api.deleteVehicleReminder(r.id).enqueue(done(R.string.st_reminders_save_failed));
    }

    private Callback<ResponseBody> done(@StringRes int failure) {
        return new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                busy = false;
                if (!sheet.isShowing()) {
                    ApiErrors.close(response);
                    return;
                }
                if (response.isSuccessful() || response.code() == 404) {
                    ApiErrors.close(response);
                    load();
                    return;
                }
                progress.setVisibility(View.GONE);
                showError(ApiErrors.read(response, activity.getString(failure)).message);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                busy = false;
                if (!sheet.isShowing()) return;
                progress.setVisibility(View.GONE);
                showError(activity.getString(R.string.st_error_network));
            }
        };
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }
}
