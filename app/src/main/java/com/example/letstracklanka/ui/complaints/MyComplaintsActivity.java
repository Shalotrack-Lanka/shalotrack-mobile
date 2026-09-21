package com.example.letstracklanka.ui.complaints;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.ComplaintResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.utils.PeriodicRefresher;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * "My Complaints" list — the Android entry point into the
 * Complaint/Feedback feature. Real backend calls throughout (GET
 * api/Complaints/mine), same error-banner/retry + empty-state pattern
 * already established for Emergency Contacts. Opened from the drawer
 * menu (DrawerMenuHelper, "My Complaints" item) and, via the FCM tap
 * handler, straight into a specific complaint's detail screen when
 * pushed from a reply/status-change notification.
 */
public class MyComplaintsActivity extends AppCompatActivity {

    /** If present, opens straight into that complaint's detail screen
     * instead of the list — used when launched from a push notification
     * tap (see ShaloTrackFirebaseMessagingService). */
    public static final String EXTRA_OPEN_COMPLAINT_ID = "extra_open_complaint_id";

    // NEW -- background refresh while this list is on screen, so a status
    // change or dealer/admin reply shows up without the user needing to
    // leave and reopen this screen. See PeriodicRefresher's own doc for
    // why this is foreground-only, not a true OS background poller.
    private static final long REFRESH_INTERVAL_MS = 20_000;

    private ApiService mainApiService;

    private View errorBanner, progressBar, layoutEmptyState;
    private android.widget.TextView tvErrorBannerMessage, tvErrorBannerRetry;
    private RecyclerView rvComplaints;
    private View fabFileComplaint;
    private ComplaintAdapter adapter;
    private ConnectivityManager.NetworkCallback networkCallback;
    private PeriodicRefresher refresher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_complaints);

        mainApiService = ApiClient.getClient().create(ApiService.class);
        refresher = new PeriodicRefresher(REFRESH_INTERVAL_MS, () -> fetchComplaints(false));

        initViews();
        registerNetworkMonitor();
        fetchComplaints(true);

        // NEW -- pushed straight from a complaint notification tap.
        String openComplaintId = getIntent().getStringExtra(EXTRA_OPEN_COMPLAINT_ID);
        if (openComplaintId != null && !openComplaintId.isEmpty()) {
            Intent intent = new Intent(this, ComplaintDetailActivity.class);
            intent.putExtra(ComplaintDetailActivity.EXTRA_COMPLAINT_ID, openComplaintId);
            startActivity(intent);
        }
    }

    private void initViews() {
        findViewById(R.id.btnBackMyComplaints).setOnClickListener(v -> finish());

        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        tvErrorBannerRetry = findViewById(R.id.tvErrorBannerRetry);
        progressBar = findViewById(R.id.progressMyComplaints);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        rvComplaints = findViewById(R.id.rvMyComplaints);
        fabFileComplaint = findViewById(R.id.fabFileComplaint);

        if (tvErrorBannerRetry != null) tvErrorBannerRetry.setOnClickListener(v -> fetchComplaints(true));
        if (fabFileComplaint != null) {
            fabFileComplaint.setOnClickListener(v -> startActivity(new Intent(this, FileComplaintActivity.class)));
        }

        rvComplaints.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ComplaintAdapter(new ArrayList<>(), this::onComplaintClicked);
        rvComplaints.setAdapter(adapter);
    }

    private void onComplaintClicked(ComplaintResponse complaint) {
        Intent intent = new Intent(this, ComplaintDetailActivity.class);
        intent.putExtra(ComplaintDetailActivity.EXTRA_COMPLAINT_ID, complaint.getComplaintId());
        startActivity(intent);
    }

    // showLoading distinguishes an explicit/user-visible load (first open,
    // pull-to-retry, reconnect) from a silent background tick: a silent
    // poll never touches the progress bar and never raises a fresh error
    // banner over data the user is currently, successfully looking at --
    // a transient failure every 20s would otherwise flash an error banner
    // at someone quietly reading their complaint list.
    private void fetchComplaints(boolean showLoading) {
        if (showLoading) {
            hideErrorBanner();
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        }

        mainApiService.getMyComplaints().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        hideErrorBanner(); // recovered, even if this was a silent retry
                        List<ComplaintResponse> complaints = parseList(body.string());
                        adapter.updateComplaints(complaints);
                        if (layoutEmptyState != null) {
                            layoutEmptyState.setVisibility(complaints.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                        rvComplaints.setVisibility(complaints.isEmpty() ? View.GONE : View.VISIBLE);
                    } else {
                        Log.w("MyComplaints", "fetchComplaints failed, code " + response.code());
                        if (showLoading) showErrorBanner("Couldn't load your complaints.");
                    }
                } catch (Exception e) {
                    Log.e("MyComplaints", "fetchComplaints parse error", e);
                    if (showLoading) showErrorBanner("Something went wrong loading your complaints.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                Log.e("MyComplaints", "fetchComplaints network error", t);
                if (showLoading) showErrorBanner("Network error — check your connection.");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh every time this screen becomes visible again -- covers
        // returning from FileComplaintActivity after a successful submit,
        // and from ComplaintDetailActivity after a status change, without
        // either of those screens needing to know about this one.
        fetchComplaints(true);
        refresher.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        refresher.stop();
    }

    private void showErrorBanner(String message) {
        if (errorBanner == null) return;
        errorBanner.setVisibility(View.VISIBLE);
        if (tvErrorBannerMessage != null) tvErrorBannerMessage.setText(message);
    }

    private void hideErrorBanner() {
        if (errorBanner != null) errorBanner.setVisibility(View.GONE);
    }

    private void registerNetworkMonitor() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> showErrorBanner("No internet connection."));
            }

            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> fetchComplaints(true));
            }
        };
        cm.registerDefaultNetworkCallback(networkCallback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        refresher.stop(); // safety net -- onPause already stops it in the normal lifecycle
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        }
    }

    private List<ComplaintResponse> parseList(String json) {
        List<ComplaintResponse> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                Type listType = new TypeToken<List<ComplaintResponse>>() {}.getType();
                List<ComplaintResponse> parsed = gson.fromJson(root.getAsJsonArray("data"), listType);
                if (parsed != null) list = parsed;
            }
        } catch (Exception e) {
            Log.e("MyComplaints", "parseList error", e);
        }
        return list;
    }
}