package com.example.letstracklanka.ui.complaints;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.ComplaintResponse;
import com.example.letstracklanka.data.model.CreateComplaintReplyRequest;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.EdgeToEdge;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.TopBar;
import com.example.letstracklanka.utils.ApiErrors;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.ClickGuard;
import com.example.letstracklanka.utils.InputValidator;
import com.example.letstracklanka.utils.SecureScreen;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * One complaint's thread (GET api/Complaints/{id}, POST .../reply). Read on open, when the screen comes
 * back and on pull-to-refresh; every reply and status change is also pushed, and the notification opens
 * this screen. A resolved or closed complaint takes no more replies (the server enforces it too).
 * No Toast, Snackbar or dialog.
 */
public class ComplaintDetailActivity extends AppCompatActivity {

    public static final String EXTRA_COMPLAINT_ID = "extra_complaint_id";

    private ApiService api;
    private String complaintId;

    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private RecyclerView thread;
    private ComplaintReplyAdapter adapter;
    private InlineBanner banner;
    private View composer, closedNotice;
    private EditText etReply;
    private ImageButton btnSend;
    private int shownCount = -1;
    private boolean firstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_complaint_detail);
        SecureScreen.guardTouches(this);
        TopBar.bind(this, R.string.st_complaint_title);
        EdgeToEdge.padBottomWithKeyboard(findViewById(R.id.complaintFooter));

        banner = InlineBanner.in(this);
        swipe = findViewById(R.id.swipeComplaintDetail);
        progress = findViewById(R.id.progressComplaintDetail);
        thread = findViewById(R.id.rvComplaintThread);
        composer = findViewById(R.id.layoutReplyComposer);
        closedNotice = findViewById(R.id.tvReplyClosedNotice);
        etReply = findViewById(R.id.etReplyMessage);
        btnSend = findViewById(R.id.btnSendReply);

        complaintId = getIntent().getStringExtra(EXTRA_COMPLAINT_ID);
        if (complaintId == null || complaintId.trim().isEmpty()) {
            banner.showError(R.string.st_complaint_not_found, null);
            swipe.setEnabled(false);
            return;
        }

        api = ApiClient.getClient().create(ApiService.class);
        adapter = new ComplaintReplyAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(this);
        thread.setLayoutManager(lm);
        thread.setAdapter(adapter);
        swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.st_navy));
        swipe.setOnRefreshListener(this::load);
        btnSend.setOnClickListener(ClickGuard.wrap(v -> send()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (api == null) return;
        if (firstResume) progress.setVisibility(View.VISIBLE);
        firstResume = false;
        load();
    }

    private void load() {
        api.getComplaintById(complaintId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) {
                    ApiErrors.close(response);
                    return;
                }
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                ComplaintResponse complaint = null;
                if (response.isSuccessful()) {
                    try (ResponseBody body = response.body()) {
                        if (body != null) complaint = ApiJson.object(body.string(), ComplaintResponse.class);
                    } catch (Exception ignored) {
                        complaint = null;
                    }
                } else {
                    ApiErrors.close(response);
                }
                if (complaint == null || complaint.getComplaintId() == null) {
                    int code = response.code();
                    banner.showError(code == 401 ? R.string.st_error_session_expired
                            : code == 403 || code == 404 ? R.string.st_complaint_not_found
                            : R.string.st_complaint_load_failed, code == 403 || code == 404 ? null : () -> load());
                    return;
                }
                render(complaint);
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

    private void render(ComplaintResponse complaint) {
        adapter.show(this, complaint);
        int count = adapter.getItemCount();
        // Jump to the newest message only when there is a new one, not on every refresh.
        if (count != shownCount) thread.scrollToPosition(adapter.lastPosition());
        shownCount = count;
        boolean open = !complaint.isClosedForReplies();
        composer.setVisibility(open ? View.VISIBLE : View.GONE);
        closedNotice.setVisibility(open ? View.GONE : View.VISIBLE);
    }

    private void send() {
        banner.hide();
        String message = InputValidator.clean(etReply.getText() != null ? etReply.getText().toString() : "");
        String error = message.isEmpty() ? getString(R.string.st_complaint_reply_required) : InputValidator.message(message, 2);
        if (error != null) {
            banner.showError(error, null);
            etReply.requestFocus();
            return;
        }
        btnSend.setEnabled(false);
        etReply.setEnabled(false);
        api.replyToComplaint(complaintId, new CreateComplaintReplyRequest(message)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) {
                    ApiErrors.close(response);
                    return;
                }
                btnSend.setEnabled(true);
                etReply.setEnabled(true);
                if (response.isSuccessful()) {
                    ApiErrors.close(response);
                    etReply.setText("");
                    load();
                    return;
                }
                if (response.code() == 401) {
                    ApiErrors.close(response);
                    banner.showError(R.string.st_error_session_expired, null);
                    return;
                }
                banner.showError(ApiErrors.read(response, getString(R.string.st_complaint_reply_failed)).message, null);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                btnSend.setEnabled(true);
                etReply.setEnabled(true);
                banner.showError(R.string.st_error_network, null);
            }
        });
    }
}
