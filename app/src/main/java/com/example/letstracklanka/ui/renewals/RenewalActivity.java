package com.example.letstracklanka.ui.renewals;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.RenewalResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ProgressRequestBody;
import com.example.letstracklanka.ui.common.ConfirmSheet;
import com.example.letstracklanka.ui.common.EdgeToEdge;
import com.example.letstracklanka.ui.common.EmptyState;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.TopBar;
import com.example.letstracklanka.utils.ApiErrors;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.SecureScreen;
import com.example.letstracklanka.utils.SlipPreparer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Renewals: request a renewal, upload the bank slip, follow the decision.
 *
 * Money rules live on the server. The price list is read from the server and shown, but this screen never
 * sends an amount and never marks anything as paid: it creates a request, uploads the slip and shows the
 * status the API reports. The slip is checked and shrunk on the phone by SlipPreparer and is never logged.
 * The picker is the system document picker (no storage permission). The request a slip belongs to
 * survives process death, because the picker can outlive the app. No Toast, Snackbar or dialog: progress
 * and results show on the screen.
 */
public class RenewalActivity extends AppCompatActivity implements RenewalAdapter.Actions {

    private static final String TAG = "RenewalActivity";
    private static final String STATE_PENDING_ID = "pending_renewal_id";
    private static final String[] SLIP_TYPES = {"image/jpeg", "image/png", "application/pdf"};

    private ApiService api;
    private RenewalAdapter adapter;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private SwipeRefreshLayout swipe;
    private NestedScrollView scroll;
    private RecyclerView list;
    private ProgressBar progress;
    private InlineBanner banner;
    private EmptyState empty;
    private View uploadCard;
    private TextView uploadText;
    private ProgressBar uploadBar;
    private boolean uploading;
    private boolean firstResume = true;

    /** Renewal the picked slip belongs to. */
    @Nullable private String pendingRenewalId;

    private final ActivityResultLauncher<String[]> slipPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onSlipPicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        SecureScreen.enable(this);
        setContentView(R.layout.activity_renewal);
        TopBar.bind(this, R.string.st_renewals_title);
        EdgeToEdge.padBottom(findViewById(R.id.renewalsContent));
        if (savedInstanceState != null) pendingRenewalId = savedInstanceState.getString(STATE_PENDING_ID);

        api = ApiClient.getClient().create(ApiService.class);
        swipe = findViewById(R.id.swipeRenewals);
        scroll = findViewById(R.id.scrollRenewals);
        list = findViewById(R.id.rvRenewals);
        progress = findViewById(R.id.progressRenewal);
        banner = InlineBanner.in(this);
        empty = new EmptyState(findViewById(R.id.emptyState));
        uploadCard = findViewById(R.id.cardRenewalUpload);
        uploadText = findViewById(R.id.tvRenewalUpload);
        uploadBar = findViewById(R.id.barRenewalUpload);

        swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.st_navy));
        swipe.setOnRefreshListener(this::loadRenewals);
        adapter = new RenewalAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        findViewById(R.id.btnNewRenewal).setOnClickListener(v -> NewRenewalSheet.show(this, this::onCreated));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) progress.setVisibility(View.VISIBLE);
        firstResume = false;
        loadRenewals();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PENDING_ID, pendingRenewalId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed();
    }

    // ------------------------------------------------------------------------------ list

    private void loadRenewals() {
        api.getMyRenewals().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!alive()) {
                    ApiErrors.close(response);
                    return;
                }
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                List<RenewalResponse> renewals = null;
                if (response.isSuccessful()) {
                    try (ResponseBody body = response.body()) {
                        if (body != null) renewals = ApiJson.list(body.string(), RenewalResponse.class);
                    } catch (Exception e) {
                        Log.e(TAG, "getMyRenewals parse error", e);
                    }
                } else {
                    ApiErrors.close(response);
                }
                if (renewals == null) {
                    banner.showError(response.code() == 401 ? R.string.st_error_session_expired
                            : R.string.st_renewals_load_failed, () -> loadRenewals());
                    return;
                }
                List<RenewalResponse> clean = new ArrayList<>();
                for (RenewalResponse r : renewals) if (r != null && r.getRenewalRequestId() != null) clean.add(r);
                adapter.submit(clean);
                list.setVisibility(clean.isEmpty() ? View.GONE : View.VISIBLE);
                if (clean.isEmpty()) {
                    empty.show(getString(R.string.st_renewals_empty_title), getString(R.string.st_renewals_empty_body), null, null);
                } else {
                    empty.hide();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!alive()) return;
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                banner.showError(R.string.st_error_network, () -> loadRenewals());
            }
        });
    }

    // ------------------------------------------------------------------- new renewal

    private void onCreated(@Nullable RenewalResponse created) {
        loadRenewals();
        if (created == null || created.getRenewalRequestId() == null) {
            banner.showInfo(getString(R.string.st_renewal_created_no_amount), null, null);
            return;
        }
        String text = created.getAmountLkr() != null
                ? getString(R.string.st_renewal_created, RenewalResponse.formatLkr(created.getAmountLkr()))
                : getString(R.string.st_renewal_created_no_amount);
        String id = created.getRenewalRequestId();
        banner.showInfo(text, getString(R.string.st_renewal_upload), () -> pickSlipFor(id));
        scroll.post(() -> scroll.smoothScrollTo(0, 0));
    }

    // ---------------------------------------------------------------------- slip upload

    @Override
    public void onUploadSlip(RenewalResponse renewal) {
        pickSlipFor(renewal.getRenewalRequestId());
    }

    private void pickSlipFor(String renewalId) {
        if (uploading) return;
        pendingRenewalId = renewalId;
        try {
            slipPicker.launch(SLIP_TYPES);
        } catch (Exception e) {
            banner.showError(R.string.st_error_no_app, null);
        }
    }

    private void onSlipPicked(@Nullable Uri uri) {
        final String renewalId = pendingRenewalId;
        if (uri == null || renewalId == null) return; // the customer backed out of the picker
        banner.hide();
        showUpload(getString(R.string.st_renewal_preparing), -1);
        worker.execute(() -> {
            final SlipPreparer.Result prepared;
            try {
                prepared = SlipPreparer.prepare(getContentResolver(), uri);
            } catch (SlipPreparer.SlipException e) {
                final String why = e.getMessage();
                runOnUiThread(() -> {
                    if (!alive()) return;
                    hideUpload();
                    banner.showError(why != null && !why.isEmpty() ? why : getString(R.string.st_renewal_file_bad), null);
                });
                return;
            } catch (RuntimeException e) {
                Log.e(TAG, "slip prepare failed", e);
                runOnUiThread(() -> {
                    if (!alive()) return;
                    hideUpload();
                    banner.showError(R.string.st_renewal_file_bad, null);
                });
                return;
            }
            runOnUiThread(() -> uploadSlip(renewalId, prepared));
        });
    }

    private void uploadSlip(String renewalId, SlipPreparer.Result slip) {
        if (!alive()) return;
        showUpload(getString(R.string.st_renewal_uploading, 0), 0);
        RequestBody raw = RequestBody.create(slip.bytes, MediaType.parse(slip.mimeType));
        RequestBody body = new ProgressRequestBody(raw, percent -> runOnUiThread(() -> {
            if (alive() && uploading) showUpload(getString(R.string.st_renewal_uploading, percent), percent);
        }));
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", slip.fileName, body);
        api.uploadRenewalSlip(renewalId, part).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!alive()) {
                    ApiErrors.close(response);
                    return;
                }
                hideUpload();
                if (response.isSuccessful()) {
                    ApiErrors.close(response);
                    pendingRenewalId = null;
                    banner.showSuccess(getString(R.string.st_renewal_uploaded));
                    loadRenewals();
                    return;
                }
                if (response.code() == 401) {
                    ApiErrors.close(response);
                    banner.showError(R.string.st_error_session_expired, null);
                    return;
                }
                banner.showError(ApiErrors.read(response, getString(R.string.st_renewal_upload_failed)).message,
                        () -> pickSlipFor(renewalId));
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!alive()) return;
                hideUpload();
                banner.showError(getString(R.string.st_error_network), () -> pickSlipFor(renewalId));
            }
        });
    }

    /** percent < 0: still preparing (moving bar). */
    private void showUpload(String text, int percent) {
        uploading = true;
        adapter.setBusy(true);
        uploadText.setText(text);
        uploadBar.setIndeterminate(percent < 0);
        if (percent >= 0) uploadBar.setProgress(percent);
        uploadCard.setVisibility(View.VISIBLE);
        scroll.post(() -> scroll.smoothScrollTo(0, 0));
    }

    private void hideUpload() {
        uploading = false;
        adapter.setBusy(false);
        uploadCard.setVisibility(View.GONE);
    }

    // --------------------------------------------------------------------------- cancel

    @Override
    public void onCancel(RenewalResponse renewal) {
        if (uploading) return;
        ConfirmSheet.show(this, getString(R.string.st_renewal_cancel_title), getString(R.string.st_renewal_cancel_body),
                getString(R.string.st_renewal_cancel), getString(R.string.st_renewal_cancel_busy), done ->
                        api.cancelRenewal(renewal.getRenewalRequestId()).enqueue(new Callback<ResponseBody>() {
                            @Override
                            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                                if (response.isSuccessful()) {
                                    ApiErrors.close(response);
                                    done.succeeded();
                                    if (alive()) loadRenewals();
                                    return;
                                }
                                done.failed(ApiErrors.read(response, getString(R.string.st_renewal_cancel_failed)).message);
                            }

                            @Override
                            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                                done.failed(getString(R.string.st_error_network));
                            }
                        }));
    }
}
