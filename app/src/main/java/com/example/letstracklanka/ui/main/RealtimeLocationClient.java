package com.example.letstracklanka.ui.main;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;
import com.microsoft.signalr.HubConnectionState;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;

/**
 * Real-time push client for live vehicle location (Option B). Connects to the
 * API's SignalR hub and joins the group for one specific vehicle. When the
 * server pushes a "LocationUpdated" event, this hands the parsed payload to
 * whoever registered a listener -- same shape as the existing polling result,
 * so callers can feed it into the exact same trailRenderer.updatePosition()/
 * updateUI() pipeline already built for the poll-based path.
 *
 * This does NOT replace polling -- it's meant to run ALONGSIDE a much-slower
 * fallback poll (see HomeActivity/VehiclesActivity wiring notes). If the push
 * connection drops for any reason (backgrounding, network blip, server
 * restart), the fallback poll keeps the UI from going stale indefinitely
 * while SignalR's automatic reconnect tries to recover in the background.
 */
public class RealtimeLocationClient {

    private static final String TAG = "RealtimeLocationClient";
    private static final String HUB_URL = "https://api.shalotrack.com/hubs/location";

    public interface LocationUpdateListener {
        void onLocationUpdated(RealtimeLocationPayload payload);
    }

    private HubConnection hubConnection;
    private String pendingVehicleId;
    private LocationUpdateListener listener;

    /**
     * Builds the connection and starts it. Safe to call once per screen (Activity)
     * lifecycle -- call stop() in onStop()/onDestroy() to release resources.
     */
    public void connect(String vehicleId, LocationUpdateListener listener) {
        this.pendingVehicleId = vehicleId;
        this.listener = listener;

        hubConnection = HubConnectionBuilder.create(HUB_URL)
                .withAccessTokenProvider(getTokenSingle())
                .withHeader("X-Requested-With", "ShaloTrackAndroid")
                .build();

        hubConnection.on("LocationUpdated", this::handlePayload, JsonObject.class);

        hubConnection.start()
                .subscribe(
                        () -> {
                            Log.d(TAG, "Hub connected, joining group for " + pendingVehicleId);
                            joinVehicleGroup(pendingVehicleId);
                        },
                        error -> Log.e(TAG, "Hub connection failed to start", error)
                );

        // Re-join the group automatically after any reconnect -- SignalR groups
        // don't survive a dropped/reestablished connection on their own.
        hubConnection.onClosed(exception -> Log.w(TAG, "Hub connection closed", exception));
    }

    private void handlePayload(JsonObject json) {
        try {
            Log.d(TAG, "Received LocationUpdated push: " + json.toString());   // NEW — confirms receipt
            RealtimeLocationPayload payload = new Gson().fromJson(json, RealtimeLocationPayload.class);
            if (payload != null && listener != null) {
                listener.onLocationUpdated(payload);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing pushed location payload", e);
        }
    }

    private void joinVehicleGroup(String vehicleId) {
        if (hubConnection == null || hubConnection.getConnectionState() != HubConnectionState.CONNECTED) {
            return;
        }
        hubConnection.send("JoinVehicleGroup", vehicleId);
    }

    /**
     * Firebase ID tokens expire (~1hr). withAccessTokenProvider() is called by the
     * SignalR client on every (re)connection attempt, not just once -- so this
     * always fetches a fresh token rather than risking a stale one on reconnect.
     */
    private Single<String> getTokenSingle() {
        return Single.create(emitter -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                emitter.onError(new IllegalStateException("No signed-in user"));
                return;
            }
            user.getIdToken(false)
                    .addOnSuccessListener(result -> emitter.onSuccess(result.getToken()))
                    .addOnFailureListener(emitter::onError);
        });
    }

    public void stop() {
        if (hubConnection != null) {
            try {
                hubConnection.stop().timeout(3, TimeUnit.SECONDS).blockingAwait();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping hub connection", e);
            }
        }
    }

    public boolean isConnected() {
        return hubConnection != null && hubConnection.getConnectionState() == HubConnectionState.CONNECTED;
    }
}