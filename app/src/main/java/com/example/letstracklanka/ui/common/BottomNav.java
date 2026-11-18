package com.example.letstracklanka.ui.common;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.history.TripHistoryActivity;
import com.example.letstracklanka.ui.main.AlertsActivity;
import com.example.letstracklanka.ui.main.HomeActivity;
import com.example.letstracklanka.ui.main.MoreActivity;
import com.example.letstracklanka.ui.vehicles.VehicleListActivity;
import com.example.letstracklanka.utils.ApiJson;


import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * The one bottom navigation bar (layout: include_bottom_nav.xml). Before this, the bar was copy-pasted
 * into five layouts with five different colour sets and six tabs (two of them Letstrack leftovers).
 *
 * Tabs: Map, Vehicles, Trips, Alerts, More. Switching reorders the existing screen to the front
 * (keeps its scroll position and data) with no animation. Back on any tab returns to Map; back on Map
 * sends the app to the background (same as the system does for a launcher screen).
 */
public final class BottomNav {

    public enum Tab { MAP, VEHICLES, TRIPS, ALERTS, MORE }

    /** Unread count is refreshed at most once a minute, whichever tab asks. */
    private static final long BADGE_REFRESH_MS = 60_000;
    private static long lastBadgeFetch = 0;
    private static int lastUnread = 0;

    private BottomNav() {}

    public static void bind(@NonNull ComponentActivity activity, @NonNull Tab current) {
        View bar = activity.findViewById(R.id.bottomNav);
        if (bar == null) return;
        EdgeToEdge.padBottom(bar);

        wire(activity, current, Tab.MAP, R.id.navMap, R.id.navMapIndicator);
        wire(activity, current, Tab.VEHICLES, R.id.navVehicles, R.id.navVehiclesIndicator);
        wire(activity, current, Tab.TRIPS, R.id.navTrips, R.id.navTripsIndicator);
        wire(activity, current, Tab.ALERTS, R.id.navAlerts, R.id.navAlertsIndicator);
        wire(activity, current, Tab.MORE, R.id.navMore, R.id.navMoreIndicator);

        activity.getOnBackPressedDispatcher().addCallback(activity, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (current == Tab.MAP) {
                    activity.moveTaskToBack(true);
                } else {
                    go(activity, Tab.MAP);
                }
            }
        });

        // Every time this tab comes back on screen: show the last known count at once, then re-read it
        // if it is more than a minute old. Tabs are reordered, not recreated, so onCreate is not enough.
        activity.getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                showBadge(activity, lastUnread);
                refreshAlertsBadge(activity, false);
            }
        });
    }

    /** Hides the bar (used when a tab screen is opened for one vehicle from that vehicle's page). */
    public static void hide(@NonNull Activity activity) {
        View bar = activity.findViewById(R.id.bottomNav);
        if (bar != null) bar.setVisibility(View.GONE);
    }

    public static void go(@NonNull Activity from, @NonNull Tab tab) {
        Intent intent = new Intent(from, target(tab));
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        from.startActivity(intent);
        from.overridePendingTransition(0, 0);
    }

    /** Re-reads the unread alert count (one COUNT on the server) unless it was read in the last minute. */
    public static void refreshAlertsBadge(@NonNull Activity activity, boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && lastBadgeFetch != 0 && now - lastBadgeFetch < BADGE_REFRESH_MS) return;
        lastBadgeFetch = now;
        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.getUnreadAlertCount().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) return;
                    com.google.gson.JsonObject root = new com.google.gson.Gson().fromJson(body.string(), com.google.gson.JsonObject.class);
                    com.google.gson.JsonElement data = ApiJson.get(root, "data");
                    if (data == null || !data.isJsonPrimitive()) return;
                    lastUnread = Math.max(0, data.getAsInt());
                    if (!activity.isFinishing() && !activity.isDestroyed()) showBadge(activity, lastUnread);
                } catch (Exception ignored) {
                    // the badge is a hint only; the Alerts screen shows the real state
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                // no badge change without data
            }
        });
    }

    /** Called by AlertsActivity after it marks alerts read, so the badge matches what is on screen. */
    public static void setUnread(@NonNull Activity activity, int unread) {
        lastUnread = Math.max(0, unread);
        lastBadgeFetch = SystemClock.elapsedRealtime();
        showBadge(activity, lastUnread);
    }

    /** One alert was marked read (optimistically); every tab shows the lower count when it next starts. */
    public static void markOneRead(@NonNull Activity activity) {
        lastUnread = Math.max(0, lastUnread - 1);
        showBadge(activity, lastUnread);
    }

    /** The mark-read call failed and the alert is unread again. */
    public static void markOneUnread(@NonNull Activity activity) {
        lastUnread = lastUnread + 1;
        showBadge(activity, lastUnread);
    }

    /** Call from SignOut so the next customer never sees the previous one's count. */
    public static void reset() {
        lastUnread = 0;
        lastBadgeFetch = 0;
    }

    private static void showBadge(Activity activity, int unread) {
        TextView badge = activity.findViewById(R.id.navAlertsBadge);
        if (badge == null) return;
        View alertsTab = activity.findViewById(R.id.navAlerts);
        if (unread <= 0) {
            badge.setVisibility(View.GONE);
            if (alertsTab != null) alertsTab.setContentDescription(null);   // falls back to the "Alerts" label
            return;
        }
        badge.setText(unread > 9 ? "9+" : String.valueOf(unread));
        badge.setVisibility(View.VISIBLE);
        if (alertsTab != null) {
            alertsTab.setContentDescription(activity.getResources().getQuantityString(
                    R.plurals.st_nav_alerts_unread, unread, unread));
        }
    }

    private static void wire(Activity activity, Tab current, Tab tab, int itemId, int indicatorId) {
        View item = activity.findViewById(itemId);
        View indicator = activity.findViewById(indicatorId);
        boolean selected = tab == current;
        if (item != null) {
            item.setSelected(selected);
            setSelectedDeep(item, selected);
            item.setOnClickListener(selected ? null : v -> go(activity, tab));
        }
        if (indicator != null) indicator.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
    }

    /** Icon and label tints are colour state lists keyed on state_selected, so the children need it too. */
    private static void setSelectedDeep(View v, boolean selected) {
        v.setSelected(selected);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) setSelectedDeep(g.getChildAt(i), selected);
        }
    }

    private static Class<?> target(Tab tab) {
        switch (tab) {
            case VEHICLES: return VehicleListActivity.class;
            case TRIPS: return TripHistoryActivity.class;
            case ALERTS: return AlertsActivity.class;
            case MORE: return MoreActivity.class;
            case MAP:
            default: return HomeActivity.class;
        }
    }
}
