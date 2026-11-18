package com.example.letstracklanka.ui.vehicles.page;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PersistableBundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.ui.common.TimeText;
import com.example.letstracklanka.utils.ApiErrors;
import com.example.letstracklanka.utils.ApiJson;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Live location links (bottom_sheet_live_links). Same feature and limits as Fleet: 1 to 24 hours, at most
 * 3 active per vehicle, owner only, page at fleet.shalotrack.com/live/{token}.
 *
 * The token exists in the create reply only (the server stores its hash), so the full link is shown once,
 * here, with Copy and Share. Copy marks the clip as sensitive (Android 13+ hides it from the clipboard
 * preview and keyboards' clipboard history). The link is never logged.
 */
public final class LiveLinksSheet {

    public interface Listener {
        /** Active links changed (made or stopped): the page refreshes its row text. */
        void onLinksChanged(int activeCount);
    }

    private static final String LIVE_BASE_URL = "https://fleet.shalotrack.com/live/";
    private static final int[] HOURS = {1, 4, 8, 24};

    private final Activity activity;
    private final String vehicleId;
    private final String plate;
    private final Listener listener;
    private final ApiService api = ApiClient.getClient().create(ApiService.class);
    private final BottomSheetDialog sheet;

    private final View newLink, progress, activeLabel;
    private final TextView tvNewTitle, tvNewUrl, tvError;
    private final ViewGroup list;
    private final MaterialButton btnCreate;
    private final List<TextView> chips = new ArrayList<>();
    private int hours = 4;
    private int activeCount;

    @SuppressLint("InflateParams") // dialog content has no parent to measure against
    private LiveLinksSheet(Activity activity, String vehicleId, String plate, Listener listener) {
        this.activity = activity;
        this.vehicleId = vehicleId;
        this.plate = plate;
        this.listener = listener;
        sheet = new BottomSheetDialog(activity);
        View v = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_live_links, null);
        sheet.setContentView(v);
        newLink = v.findViewById(R.id.liveNewLink);
        progress = v.findViewById(R.id.progressLive);
        activeLabel = v.findViewById(R.id.tvLiveActiveLabel);
        tvNewTitle = v.findViewById(R.id.tvLiveNewTitle);
        tvNewUrl = v.findViewById(R.id.tvLiveNewUrl);
        tvError = v.findViewById(R.id.tvLiveError);
        list = v.findViewById(R.id.liveList);
        btnCreate = v.findViewById(R.id.btnLiveCreate);

        int[] ids = {R.id.chipLive1h, R.id.chipLive4h, R.id.chipLive8h, R.id.chipLive24h};
        for (int i = 0; i < ids.length; i++) {
            TextView chip = v.findViewById(ids[i]);
            final int h = HOURS[i];
            chip.setOnClickListener(x -> selectHours(h));
            chips.add(chip);
        }
        selectHours(4);
        btnCreate.setOnClickListener(x -> create());
    }

    public static void show(@NonNull Activity activity, @NonNull String vehicleId, @Nullable String plate,
                            @NonNull Listener listener) {
        LiveLinksSheet s = new LiveLinksSheet(activity, vehicleId, plate != null ? plate : "", listener);
        s.sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        s.sheet.getBehavior().setSkipCollapsed(true);
        s.sheet.show();
        s.loadActive();
    }

    private void selectHours(int h) {
        hours = h;
        for (int i = 0; i < chips.size(); i++) chips.get(i).setSelected(HOURS[i] == h);
    }

    private void loadActive() {
        progress.setVisibility(View.VISIBLE);
        api.getLiveShares(vehicleId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!sheet.isShowing()) {
                    ApiErrors.close(response);
                    return;
                }
                progress.setVisibility(View.GONE);
                JsonArray arr = null;
                if (response.isSuccessful()) {
                    try (ResponseBody body = response.body()) {
                        arr = body != null ? ApiJson.dataArray(body.string()) : null;
                    } catch (Exception ignored) {
                        arr = null;
                    }
                } else {
                    ApiErrors.read(response, "");
                }
                renderActive(arr);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!sheet.isShowing()) return;
                progress.setVisibility(View.GONE);
                showError(activity.getString(R.string.st_error_network));
            }
        });
    }

    private void renderActive(@Nullable JsonArray arr) {
        list.removeAllViews();
        activeCount = 0;
        Instant now = Instant.now();
        if (arr != null) {
            LayoutInflater inf = LayoutInflater.from(activity);
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                String linkId = ApiJson.string(o, "linkId");
                String expires = ApiJson.string(o, "expiresAt");
                String created = ApiJson.string(o, "createdAt");
                Instant exp = TimeText.parse(expires);
                if (linkId == null || exp == null || !exp.isAfter(now)) continue;
                activeCount++;
                View row = inf.inflate(R.layout.item_live_link, list, false);
                ((TextView) row.findViewById(R.id.tvLiveEnds)).setText(
                        activity.getString(R.string.st_live_ends, TimeText.dayAndTime(activity, exp)));
                Instant made = TimeText.parse(created);
                ((TextView) row.findViewById(R.id.tvLiveMade)).setText(made != null
                        ? activity.getString(R.string.st_live_made, TimeText.dayAndTime(activity, made)) : "");
                MaterialButton stop = row.findViewById(R.id.btnLiveStop);
                stop.setContentDescription(activity.getString(R.string.st_live_stop_a11y, TimeText.dayAndTime(activity, exp)));
                stop.setOnClickListener(x -> revoke(linkId, stop));
                list.addView(row);
            }
        }
        boolean any = activeCount > 0;
        list.setVisibility(any ? View.VISIBLE : View.GONE);
        activeLabel.setVisibility(any ? View.VISIBLE : View.GONE);
        btnCreate.setEnabled(activeCount < 3);
        btnCreate.setText(activeCount < 3 ? R.string.st_live_create : R.string.st_live_limit);
        listener.onLinksChanged(activeCount);
    }

    private void create() {
        tvError.setVisibility(View.GONE);
        btnCreate.setEnabled(false);
        btnCreate.setText(R.string.st_action_sending);
        JsonObject body = new JsonObject();
        body.addProperty("durationHours", hours);
        api.createLiveShare(vehicleId, body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!sheet.isShowing()) {
                    ApiErrors.close(response);
                    return;
                }
                if (!response.isSuccessful()) {
                    btnCreate.setText(R.string.st_live_create);
                    btnCreate.setEnabled(true);
                    showError(ApiErrors.read(response, activity.getString(R.string.st_live_create_failed)).message);
                    return;
                }
                String token = null;
                String expires = null;
                try (ResponseBody b = response.body()) {
                    JsonObject data = b != null ? ApiJson.dataObject(b.string()) : null;
                    token = data != null ? ApiJson.string(data, "token") : null;
                    expires = data != null ? ApiJson.string(data, "expiresAt") : null;
                } catch (Exception ignored) {
                    token = null;
                }
                if (token == null) {
                    showError(activity.getString(R.string.st_live_create_failed));
                } else {
                    showNewLink(LIVE_BASE_URL + token, TimeText.parse(expires));
                }
                loadActive();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!sheet.isShowing()) return;
                btnCreate.setText(R.string.st_live_create);
                btnCreate.setEnabled(true);
                showError(activity.getString(R.string.st_error_network));
            }
        });
    }

    private void showNewLink(String url, @Nullable Instant expires) {
        tvNewTitle.setText(expires != null
                ? activity.getString(R.string.st_live_ready_until, TimeText.dayAndTime(activity, expires))
                : activity.getString(R.string.st_live_ready));
        tvNewUrl.setText(url);
        newLink.setVisibility(View.VISIBLE);
        MaterialButton copy = sheet.findViewById(R.id.btnLiveCopy);
        MaterialButton share = sheet.findViewById(R.id.btnLiveShare);
        if (copy != null) copy.setOnClickListener(x -> {
            copy(url);
            copy.setText(R.string.st_live_copied);
        });
        if (share != null) share.setOnClickListener(x -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, activity.getString(R.string.st_live_share_text, Plates.plain(plate), url));
            try {
                activity.startActivity(Intent.createChooser(send, activity.getString(R.string.st_live_share)));
            } catch (Exception e) {
                showError(activity.getString(R.string.st_error_no_share_app));
            }
        });
    }

    private void copy(String url) {
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        ClipData clip = ClipData.newPlainText(activity.getString(R.string.st_live_title), url);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }
        cm.setPrimaryClip(clip);
    }

    private void revoke(String linkId, MaterialButton stop) {
        stop.setEnabled(false);
        stop.setText(R.string.st_action_removing);
        api.revokeLiveShare(linkId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!sheet.isShowing()) {
                    ApiErrors.close(response);
                    return;
                }
                if (response.isSuccessful() || response.code() == 404) {
                    ApiErrors.close(response);
                    newLink.setVisibility(View.GONE);
                    loadActive();
                    return;
                }
                stop.setEnabled(true);
                stop.setText(R.string.st_live_stop);
                showError(ApiErrors.read(response, activity.getString(R.string.st_live_stop_failed)).message);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!sheet.isShowing()) return;
                stop.setEnabled(true);
                stop.setText(R.string.st_live_stop);
                showError(activity.getString(R.string.st_error_network));
            }
        });
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }
}
