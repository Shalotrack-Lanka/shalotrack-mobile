package com.example.letstracklanka.ui.complaints;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.ComplaintResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.EdgeToEdge;
import com.example.letstracklanka.ui.common.EmptyState;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.NetworkWatcher;
import com.example.letstracklanka.ui.common.TopBar;
import com.example.letstracklanka.utils.ApiErrors;
import com.example.letstracklanka.utils.ApiJson;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Complaints (GET api/Complaints/mine). Opened from More, and from a complaint notification straight into
 * one complaint (EXTRA_OPEN_COMPLAINT_ID). Read on open, when the screen comes back and on pull-to-refresh;
 * not polled, because every reply and status change is pushed. No Toast, Snackbar or dialog.
 */
public class MyComplaintsActivity extends AppCompatActivity {

    /** Opens that complaint's thread on top of the list (from a notification tap). */
    public static final String EXTRA_OPEN_COMPLAINT_ID = "extra_open_complaint_id";

    private ApiService api;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private RecyclerView list;
    private InlineBanner banner;
    private EmptyState empty;
    private ComplaintAdapter adapter;
    private boolean firstResume = true;

    private final ActivityResultLauncher<Intent> fileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) banner.showSuccess(getString(R.string.st_complaints_sent));
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_my_complaints);
        TopBar.bind(this, R.string.st_complaints_title);
        EdgeToEdge.padBottom(findViewById(R.id.complaintsContent));

        api = ApiClient.getClient().create(ApiService.class);
        swipe = findViewById(R.id.swipeComplaints);
        progress = findViewById(R.id.progressComplaints);
        list = findViewById(R.id.rvComplaints);
        banner = InlineBanner.in(this);
        empty = new EmptyState(findViewById(R.id.emptyState));

        swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.st_navy));
        swipe.setOnRefreshListener(this::load);
        adapter = new ComplaintAdapter(this::open);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        findViewById(R.id.btnNewComplaint).setOnClickListener(v ->
                fileLauncher.launch(new Intent(this, FileComplaintActivity.class)));

        NetworkWatcher.watch(this, new NetworkWatcher.Listener() {
            @Override
            public void onLost() {
                banner.showOffline(R.string.st_error_offline_short, null);
            }

            @Override
            public void onRestored() {
                banner.hide();
                load();
            }
        });

        String openComplaintId = getIntent().getStringExtra(EXTRA_OPEN_COMPLAINT_ID);
        if (savedInstanceState == null && openComplaintId != null && !openComplaintId.isEmpty()) {
            openById(openComplaintId);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from a thread or the form: show the latest state and reply counts.
        if (firstResume) progress.setVisibility(View.VISIBLE);
        firstResume = false;
        load();
    }

    private void open(ComplaintResponse complaint) {
        openById(complaint.getComplaintId());
    }

    private void openById(String complaintId) {
        Intent intent = new Intent(this, ComplaintDetailActivity.class);
        intent.putExtra(ComplaintDetailActivity.EXTRA_COMPLAINT_ID, complaintId);
        startActivity(intent);
    }

    private void load() {
        api.getMyComplaints().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) {
                    ApiErrors.close(response);
                    return;
                }
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                List<ComplaintResponse> complaints = null;
                if (response.isSuccessful()) {
                    try (ResponseBody body = response.body()) {
                        if (body != null) complaints = ApiJson.list(body.string(), ComplaintResponse.class);
                    } catch (Exception ignored) {
                        complaints = null;
                    }
                } else {
                    ApiErrors.close(response);
                }
                if (complaints == null) {
                    banner.showError(response.code() == 401 ? R.string.st_error_session_expired
                            : R.string.st_complaints_load_failed, () -> load());
                    return;
                }
                List<ComplaintResponse> clean = new ArrayList<>();
                for (ComplaintResponse c : complaints) if (c != null && c.getComplaintId() != null) clean.add(c);
                adapter.updateComplaints(clean);
                list.setVisibility(clean.isEmpty() ? View.GONE : View.VISIBLE);
                if (clean.isEmpty()) {
                    empty.show(getString(R.string.st_complaints_empty_title),
                            getString(R.string.st_complaints_empty_body), null, null);
                } else {
                    empty.hide();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                banner.showError(R.string.st_error_network, () -> load());
            }
        });
    }
}
