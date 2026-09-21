package com.example.letstracklanka.ui.complaints;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.ComplaintResponse;
import com.example.letstracklanka.data.model.CreateComplaintReplyRequest;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.utils.PeriodicRefresher;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * A single complaint's thread — the original complaint as a fixed card
 * up top, then the two-way reply history (customer/dealer/admin), then a
 * reply composer that's swapped for a plain "closed" notice once the
 * complaint is Resolved/Closed. Same status→color mapping as
 * ComplaintAdapter, kept in one place there and reused here via the
 * shared ComplaintResponse model rather than re-deriving it.
 */
public class ComplaintDetailActivity extends AppCompatActivity {

    public static final String EXTRA_COMPLAINT_ID = "extra_complaint_id";

    private static final int COLOR_WITH_DEALER = 0xFFF59E0B;
    private static final int COLOR_WITH_ADMIN = 0xFF1976D2;
    private static final int COLOR_RESOLVED = 0xFF16A34A;
    private static final int COLOR_CLOSED = 0xFF757575;

    // NEW -- background refresh while this thread is open, so a
    // dealer/admin reply or a status change (Resolved/Closed) shows up on
    // its own. Shorter interval than the list screen since this is the
    // closest thing this app has to a chat thread.
    private static final long REFRESH_INTERVAL_MS = 12_000;

    private ApiService mainApiService;
    private String complaintId;
    private PeriodicRefresher refresher;
    // Tracks reply count across polls so a silent tick that found nothing
    // new doesn't yank the user's scroll position back to the bottom if
    // they've scrolled up to reread something earlier in the thread.
    private int lastReplyCount = -1;

    private TextView tvTitle, tvStatus, tvVehicle, tvDescription;
    private View errorBanner, progressBar, layoutReplyComposer;
    private TextView tvErrorBannerMessage, tvErrorBannerRetry, tvReplyClosedNotice;
    private RecyclerView rvReplies;
    private EditText etReplyMessage;
    private ImageView btnSendReply;
    private ComplaintReplyAdapter replyAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complaint_detail);

        complaintId = getIntent().getStringExtra(EXTRA_COMPLAINT_ID);
        if (complaintId == null || complaintId.isEmpty()) {
            Toast.makeText(this, "Complaint not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mainApiService = ApiClient.getClient().create(ApiService.class);
        refresher = new PeriodicRefresher(REFRESH_INTERVAL_MS, () -> fetchComplaint(false));
        initViews();
        fetchComplaint(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresher.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        refresher.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        refresher.stop(); // safety net -- onPause already stops it in the normal lifecycle
    }

    private void initViews() {
        findViewById(R.id.btnBackComplaintDetail).setOnClickListener(v -> finish());

        tvTitle = findViewById(R.id.tvDetailTitle);
        tvStatus = findViewById(R.id.tvDetailStatus);
        tvVehicle = findViewById(R.id.tvDetailVehicle);
        tvDescription = findViewById(R.id.tvDetailDescription);
        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        tvErrorBannerRetry = findViewById(R.id.tvErrorBannerRetry);
        progressBar = findViewById(R.id.progressComplaintDetail);
        rvReplies = findViewById(R.id.rvComplaintReplies);
        layoutReplyComposer = findViewById(R.id.layoutReplyComposer);
        tvReplyClosedNotice = findViewById(R.id.tvReplyClosedNotice);
        etReplyMessage = findViewById(R.id.etReplyMessage);
        btnSendReply = findViewById(R.id.btnSendReply);

        if (tvErrorBannerRetry != null) tvErrorBannerRetry.setOnClickListener(v -> fetchComplaint(true));
        if (btnSendReply != null) btnSendReply.setOnClickListener(v -> sendReply());

        rvReplies.setLayoutManager(new LinearLayoutManager(this));
        replyAdapter = new ComplaintReplyAdapter(new java.util.ArrayList<>());
        rvReplies.setAdapter(replyAdapter);
    }

    // showLoading distinguishes an explicit/user-visible load (first open,
    // retry, right after sending a reply) from a silent background tick --
    // a silent poll never shows the progress bar and never raises a fresh
    // error banner over a thread the user is currently, successfully
    // reading.
    private void fetchComplaint(boolean showLoading) {
        if (showLoading) {
            hideErrorBanner();
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        }

        mainApiService.getComplaintById(complaintId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        ComplaintResponse complaint = extractObject(body.string());
                        if (complaint != null) {
                            hideErrorBanner(); // recovered, even if this was a silent retry
                            renderComplaint(complaint);
                        } else if (showLoading) {
                            showErrorBanner("Something went wrong loading this complaint.");
                        }
                    } else {
                        Log.w("ComplaintDetail", "fetchComplaint failed, code " + response.code());
                        if (showLoading) showErrorBanner("Couldn't load this complaint.");
                    }
                } catch (Exception e) {
                    Log.e("ComplaintDetail", "fetchComplaint parse error", e);
                    if (showLoading) showErrorBanner("Something went wrong loading this complaint.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                Log.e("ComplaintDetail", "fetchComplaint network error", t);
                if (showLoading) showErrorBanner("Network error — check your connection.");
            }
        });
    }

    private void renderComplaint(ComplaintResponse complaint) {
        tvTitle.setText(complaint.getCategoryLabel());
        tvVehicle.setText(complaint.getVehicleLabel());
        tvDescription.setText(complaint.getDescription());

        int statusColor = statusColorFor(complaint.getStatus());
        tvStatus.setText(complaint.getStatusLabel());
        tvStatus.setTextColor(statusColor);
        Drawable pillBg = tvStatus.getBackground().mutate();
        pillBg.setTint(withAlpha(statusColor, 60));

        int newReplyCount = complaint.getReplies().size();
        replyAdapter.updateReplies(complaint.getReplies());
        // Only jump to the bottom when the count actually changed (first
        // load, or a new reply arrived via poll/send) -- not on every
        // silent tick, which would otherwise yank a user who's scrolled up
        // to reread something back down every 12 seconds.
        if (newReplyCount > 0 && newReplyCount != lastReplyCount) {
            rvReplies.scrollToPosition(newReplyCount - 1);
        }
        lastReplyCount = newReplyCount;

        boolean canReply = !complaint.isClosedForReplies();
        layoutReplyComposer.setVisibility(canReply ? View.VISIBLE : View.GONE);
        tvReplyClosedNotice.setVisibility(canReply ? View.GONE : View.VISIBLE);
    }

    private void sendReply() {
        String message = etReplyMessage.getText() != null ? etReplyMessage.getText().toString().trim() : "";
        if (message.isEmpty()) {
            Toast.makeText(this, "Type a message first.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSendReply.setEnabled(false);
        CreateComplaintReplyRequest request = new CreateComplaintReplyRequest(message);
        mainApiService.replyToComplaint(complaintId, request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                btnSendReply.setEnabled(true);
                if (response.isSuccessful()) {
                    etReplyMessage.setText("");
                    fetchComplaint(true);
                } else {
                    Log.w("ComplaintDetail", "sendReply failed, code " + response.code());
                    Toast.makeText(ComplaintDetailActivity.this, "Couldn't send your reply. Please try again.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                btnSendReply.setEnabled(true);
                Log.e("ComplaintDetail", "sendReply network error", t);
                Toast.makeText(ComplaintDetailActivity.this, "Network error — check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int statusColorFor(int status) {
        switch (status) {
            case 1: return COLOR_WITH_ADMIN;
            case 2: return COLOR_RESOLVED;
            case 3: return COLOR_CLOSED;
            default: return COLOR_WITH_DEALER;
        }
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private void showErrorBanner(String message) {
        if (errorBanner == null) return;
        errorBanner.setVisibility(View.VISIBLE);
        if (tvErrorBannerMessage != null) tvErrorBannerMessage.setText(message);
    }

    private void hideErrorBanner() {
        if (errorBanner != null) errorBanner.setVisibility(View.GONE);
    }

    private ComplaintResponse extractObject(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), ComplaintResponse.class);
            }
            return gson.fromJson(json, ComplaintResponse.class);
        } catch (Exception e) {
            Log.e("ComplaintDetail", "extractObject error", e);
            return null;
        }
    }
}