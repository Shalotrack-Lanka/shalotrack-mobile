package com.example.letstracklanka.ui.renewals;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateRenewalRequest;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.RenewalPackage;
import com.example.letstracklanka.data.model.RenewalResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ProgressRequestBody;
import com.example.letstracklanka.utils.SlipPreparer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
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
 * Vehicle Subscriptions: request a renewal, upload the bank slip, follow the decision.
 *
 * Money rules live on the server. The price list is read from the server and shown, but this screen
 * never SENDS an amount (the server fixes it) and never marks anything as paid: it only creates a request, uploads the slip, and shows the status the
 * API reports. The slip is shrunk/validated on the phone by SlipPreparer, and is never logged.
 *
 * The slip picker is the system document picker (no storage permission needed). The id being
 * uploaded for survives process death in the saved state, because the picker can outlive the app.
 */
public class RenewalActivity extends AppCompatActivity implements RenewalAdapter.Actions {

    private static final String TAG = "RenewalActivity";
    private static final String STATE_PENDING_ID = "pending_renewal_id";
    private static final String[] SLIP_TYPES = {"image/jpeg", "image/png", "application/pdf"};

    private ApiService api;
    private RenewalAdapter adapter;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<VehicleResponse> myVehicles = new ArrayList<>();

    private RecyclerView rv;
    private ProgressBar progress;
    private View emptyState, errorBanner;
    private TextView tvErrorMessage;
    private AlertDialog busyDialog;
    private TextView busyText, busyPercent;
    private ProgressBar busyBar;

    /** Renewal the picked slip belongs to. */
    private String pendingRenewalId;

    private final ActivityResultLauncher<String[]> slipPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onSlipPicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_renewal);
        if (savedInstanceState != null) pendingRenewalId = savedInstanceState.getString(STATE_PENDING_ID);

        api = ApiClient.getClient().create(ApiService.class);

        findViewById(R.id.btnBackRenewal).setOnClickListener(v -> finish());
        rv = findViewById(R.id.rvRenewals);
        progress = findViewById(R.id.progressRenewal);
        emptyState = findViewById(R.id.layoutRenewalEmpty);
        errorBanner = findViewById(R.id.renewalErrorBanner);
        tvErrorMessage = findViewById(R.id.tvRenewalErrorMessage);
        findViewById(R.id.tvRenewalErrorRetry).setOnClickListener(v -> loadRenewals());
        findViewById(R.id.fabNewRenewal).setOnClickListener(v -> startNewRenewal());

        adapter = new RenewalAdapter(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        if (busyDialog != null && busyDialog.isShowing()) busyDialog.dismiss();
    }

    // ------------------------------------------------------------------------------ list

    private void loadRenewals() {
        progress.setVisibility(View.VISIBLE);
        errorBanner.setVisibility(View.GONE);
        api.getMyRenewals().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<RenewalResponse> list = extractList(body.string(), RenewalResponse.class);
                        if (list == null) list = new ArrayList<>();
                        adapter.submit(list);
                        emptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    } else {
                        Log.w(TAG, "getMyRenewals failed, code " + response.code());
                        showError("Couldn't load your renewals.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getMyRenewals parse error", e);
                    showError("Couldn't load your renewals.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                Log.e(TAG, "getMyRenewals network error", t);
                showError("Network error — check your connection.");
            }
        });
    }

    private void showError(String message) {
        tvErrorMessage.setText(message);
        errorBanner.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------- new renewal

    private void startNewRenewal() {
        if (!myVehicles.isEmpty()) {
            loadPackagesThenShowDialog();
            return;
        }
        progress.setVisibility(View.VISIBLE);
        api.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) return;
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        CustomerResponse customer = extractObject(body.string(), CustomerResponse.class);
                        if (customer != null && customer.getCustomerId() != null) {
                            fetchVehicles(customer.getCustomerId());
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getMyProfile parse error", e);
                }
                progress.setVisibility(View.GONE);
                toast("Couldn't load your vehicles. Try again.");
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                toast("Network error — check your connection.");
            }
        });
    }

    private void fetchVehicles(String customerId) {
        api.getVehiclesByCustomer(customerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<VehicleResponse> list = extractList(body.string(), VehicleResponse.class);
                        myVehicles.clear();
                        if (list != null) myVehicles.addAll(list);
                        if (myVehicles.isEmpty()) {
                            toast("You don't have any vehicles to renew.");
                        } else {
                            loadPackagesThenShowDialog();
                        }
                        return;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getVehiclesByCustomer parse error", e);
                }
                toast("Couldn't load your vehicles. Try again.");
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                toast("Network error — check your connection.");
            }
        });
    }

    /**
     * The price list comes from the server every time the dialog opens, so the customer always sees
     * the current prices and nothing is hard-coded on the phone. If the server has no list yet (it is
     * pushed from the admin portal), the plain package names are offered without a price and the
     * server decides the amount when the request is created.
     */
    private void loadPackagesThenShowDialog() {
        showBusy("Loading prices…");
        api.getRenewalPackages().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) return;
                hideBusy();
                List<RenewalPackage> offered = null;
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        offered = extractList(body.string(), RenewalPackage.class);
                        if (offered == null) offered = new ArrayList<>();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getRenewalPackages parse error", e);
                    offered = null;
                }
                if (offered == null) {
                    toastLong("Couldn't load the renewal prices. Try again.");
                    return;
                }
                List<RenewalPackage> orderable = new ArrayList<>();
                for (RenewalPackage p : offered) if (p.isOrderable()) orderable.add(p);
                showNewRenewalDialog(orderable);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                hideBusy();
                Log.e(TAG, "getRenewalPackages network error", t);
                toast("Network error — check your connection.");
            }
        });
    }

    /** "12-month warranty from activation" style helper text for a package. */
    private static String priceLine(RenewalPackage p) {
        String line = RenewalResponse.formatLkr(p.getPriceLkr());
        if (p.getWarrantyMonths() > 0) line += " \u00B7 " + p.getWarrantyMonths() + "-month warranty from your first activation";
        return line;
    }

    private void showNewRenewalDialog(List<RenewalPackage> offered) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_new_renewal, null);
        AutoCompleteTextView spVehicle = view.findViewById(R.id.spinnerRenewalVehicle);
        AutoCompleteTextView spDuration = view.findViewById(R.id.spinnerRenewalDuration);
        EditText etRef = view.findViewById(R.id.etRenewalReference);

        List<String> vehicleLabels = new ArrayList<>();
        for (VehicleResponse v : myVehicles) vehicleLabels.add(vehicleLabel(v));
        spVehicle.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, vehicleLabels));
        final int[] vehicleIndex = {0};
        spVehicle.setText(vehicleLabels.get(0), false);
        spVehicle.setOnItemClickListener((parent, v, pos, id) -> vehicleIndex[0] = pos);
        spVehicle.setOnClickListener(v -> spVehicle.showDropDown());

        TextView tvPrice = view.findViewById(R.id.tvRenewalPrice);
        final List<String> optionLabels = new ArrayList<>();
        final List<String> optionDurations = new ArrayList<>();
        final List<String> optionPriceLines = new ArrayList<>();
        int defaultIndex = 0;
        if (offered.isEmpty()) {
            // No list on the server yet: names only, no price shown.
            for (int i = 0; i < RenewalResponse.DURATION_LABELS.length; i++) {
                optionLabels.add(RenewalResponse.DURATION_LABELS[i]);
                optionDurations.add(RenewalResponse.DURATION_NAMES[i]);
                optionPriceLines.add("Our team confirms the amount for this package.");
            }
            defaultIndex = 2; // 1 Year
        } else {
            for (int i = 0; i < offered.size(); i++) {
                RenewalPackage p = offered.get(i);
                optionLabels.add(p.getLabel() + " \u2014 " + RenewalResponse.formatLkr(p.getPriceLkr()));
                optionDurations.add(p.getDuration());
                optionPriceLines.add(priceLine(p));
                if ("OneYear".equals(p.getDuration())) defaultIndex = i;
            }
        }
        spDuration.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, optionLabels));
        final int[] durationIndex = {defaultIndex};
        spDuration.setText(optionLabels.get(defaultIndex), false);
        tvPrice.setText(optionPriceLines.get(defaultIndex));
        spDuration.setOnItemClickListener((parent, v, pos, id) -> {
            durationIndex[0] = pos;
            tvPrice.setText(optionPriceLines.get(pos));
        });
        spDuration.setOnClickListener(v -> spDuration.showDropDown());

        new AlertDialog.Builder(this)
                .setTitle("Renew subscription")
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (d, w) -> {
                    String ref = etRef.getText() == null ? "" : etRef.getText().toString().trim();
                    createRenewal(myVehicles.get(vehicleIndex[0]).getVehicleId(),
                            optionDurations.get(durationIndex[0]),
                            ref.isEmpty() ? null : ref);
                })
                .show();
    }

    private void createRenewal(String vehicleId, String durationName, String reference) {
        showBusy("Creating request…");
        api.createRenewal(new CreateRenewalRequest(vehicleId, durationName, reference))
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        if (isFinishing() || isDestroyed()) return;
                        hideBusy();
                        if (response.isSuccessful()) {
                            RenewalResponse created = null;
                            try (ResponseBody body = response.body()) {
                                if (body != null) created = extractObject(body.string(), RenewalResponse.class);
                            } catch (Exception e) {
                                Log.e(TAG, "createRenewal parse error", e);
                            }
                            loadRenewals();
                            final RenewalResponse result = created;
                            if (result != null && result.getRenewalRequestId() != null) {
                                String msg = result.getInstructionsMessage();
                                String text = (msg == null || msg.trim().isEmpty()
                                        ? "Pay by bank transfer, then upload a photo of the slip."
                                        : msg);
                                if (result.getAmountLkr() != null) {
                                    text = "Amount to transfer: " + RenewalResponse.formatLkr(result.getAmountLkr()) + "\n\n" + text;
                                }
                                new AlertDialog.Builder(RenewalActivity.this)
                                        .setTitle("Request created")
                                        .setMessage(text)
                                        .setNegativeButton("Later", null)
                                        .setPositiveButton("Upload slip now", (d, w) -> pickSlipFor(result.getRenewalRequestId()))
                                        .show();
                            }
                        } else {
                            toastLong(errorMessage(response, "Couldn't create the renewal request."));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        hideBusy();
                        Log.e(TAG, "createRenewal network error", t);
                        toast("Network error — check your connection.");
                    }
                });
    }

    // ---------------------------------------------------------------------- slip upload

    @Override
    public void onUploadSlip(RenewalResponse renewal) {
        pickSlipFor(renewal.getRenewalRequestId());
    }

    private void pickSlipFor(String renewalId) {
        pendingRenewalId = renewalId;
        slipPicker.launch(SLIP_TYPES);
    }

    private void onSlipPicked(Uri uri) {
        final String renewalId = pendingRenewalId;
        Log.i(TAG, "slip picker returned, uri=" + (uri != null) + ", renewalId=" + (renewalId != null));
        if (uri == null || renewalId == null) return; // customer backed out of the picker
        showBusy("Preparing your slip…");
        worker.execute(() -> {
            final SlipPreparer.Result prepared;
            try {
                prepared = SlipPreparer.prepare(getContentResolver(), uri);
            } catch (SlipPreparer.SlipException e) {
                Log.w(TAG, "slip rejected on device");
                runOnUiThread(() -> {
                    hideBusy();
                    showMessage("Can't use that file", e.getMessage());
                });
                return;
            } catch (RuntimeException e) {
                Log.e(TAG, "slip prepare failed", e);
                runOnUiThread(() -> {
                    hideBusy();
                    showMessage("Can't use that file", "Couldn't read that file. Please try another.");
                });
                return;
            }
            Log.i(TAG, "slip prepared, bytes=" + prepared.bytes.length + ", type=" + prepared.mimeType);
            runOnUiThread(() -> uploadSlip(renewalId, prepared));
        });
    }

    private void uploadSlip(String renewalId, SlipPreparer.Result slip) {
        if (isFinishing() || isDestroyed()) return;
        showBusy("Uploading slip…");
        RequestBody raw = RequestBody.create(slip.bytes, MediaType.parse(slip.mimeType));
        RequestBody body = new ProgressRequestBody(raw, percent ->
                runOnUiThread(() -> updateBusy("Uploading slip…", percent)));
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", slip.fileName, body);
        api.uploadRenewalSlip(renewalId, part).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) return;
                hideBusy();
                Log.i(TAG, "slip upload finished, http " + response.code());
                if (response.isSuccessful()) {
                    pendingRenewalId = null;
                    showMessage("Slip uploaded", "Thanks. Our team will review it and renew your subscription.");
                    loadRenewals();
                } else {
                    showMessage("Upload failed",
                            errorMessage(response, "Couldn't upload the slip (error " + response.code() + "). Please try again."));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                hideBusy();
                Log.e(TAG, "uploadSlip network error", t);
                showMessage("Upload failed", "Network error — check your connection and try again.");
            }
        });
    }

    // --------------------------------------------------------------------------- cancel

    @Override
    public void onCancel(RenewalResponse renewal) {
        new AlertDialog.Builder(this)
                .setTitle("Cancel this request?")
                .setMessage("You can create a new one any time.")
                .setNegativeButton("Keep it", null)
                .setPositiveButton("Cancel request", (d, w) -> cancelRenewal(renewal.getRenewalRequestId()))
                .show();
    }

    private void cancelRenewal(String renewalId) {
        showBusy("Cancelling…");
        api.cancelRenewal(renewalId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) return;
                hideBusy();
                if (response.isSuccessful()) {
                    loadRenewals();
                } else {
                    toastLong(errorMessage(response, "Couldn't cancel the request."));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                hideBusy();
                toast("Network error — check your connection.");
            }
        });
    }

    // --------------------------------------------------------------------------- helpers

    private void showBusy(String message) {
        hideBusy();
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null);
        busyText = v.findViewById(R.id.tvProgressMessage);
        busyBar = v.findViewById(R.id.barProgress);
        busyPercent = v.findViewById(R.id.tvProgressPercent);
        busyText.setText(message);
        busyBar.setIndeterminate(true);
        busyPercent.setText("");
        busyDialog = new AlertDialog.Builder(this).setView(v).setCancelable(false).create();
        busyDialog.show();
    }

    /** Switches the open busy dialog to a determinate bar. Main thread only. */
    private void updateBusy(String message, int percent) {
        if (busyDialog == null || !busyDialog.isShowing()) return;
        busyText.setText(message);
        busyBar.setIndeterminate(false);
        busyBar.setProgress(percent);
        busyPercent.setText(percent + "%");
    }

    private void hideBusy() {
        if (busyDialog != null && busyDialog.isShowing()) busyDialog.dismiss();
        busyDialog = null;
    }

    /** Errors that must not be missed: a dialog stays until dismissed, a toast does not. */
    private void showMessage(String title, String message) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("OK", null).show();
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    private void toastLong(String m) {
        Toast.makeText(this, m, Toast.LENGTH_LONG).show();
    }

    private String vehicleLabel(VehicleResponse v) {
        String label = ((v.getMake() == null ? "" : v.getMake()) + " " + (v.getModel() == null ? "" : v.getModel())).trim();
        if (v.getVehicleNumber() != null) label += " (" + v.getVehicleNumber() + ")";
        label = label.trim();
        return label.isEmpty() ? "Vehicle" : label;
    }

    /** Reads the API's own message from an error response without logging the body. */
    private String errorMessage(Response<ResponseBody> response, String fallback) {
        try {
            if (response.errorBody() == null) return fallback;
            String json = response.errorBody().string();
            JsonObject root = new Gson().fromJson(json, JsonObject.class);
            if (root == null) return fallback;
            String message = (root.has("message") && !root.get("message").isJsonNull())
                    ? root.get("message").getAsString() : null;
            String first = null;
            if (root.has("errors") && root.get("errors").isJsonArray() && root.getAsJsonArray("errors").size() > 0) {
                first = root.getAsJsonArray("errors").get(0).getAsString();
            }
            if (message != null && first != null) return message + " " + first;
            if (message != null) return message;
            if (first != null) return first;
        } catch (Exception e) {
            Log.w(TAG, "errorMessage parse failed, code " + response.code());
        }
        return fallback;
    }

    private <T> T extractObject(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), clazz);
            }
            return gson.fromJson(json, clazz);
        } catch (Exception e) {
            Log.e(TAG, "extractObject error", e);
            return null;
        }
    }

    private <T> List<T> extractList(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                Type listType = TypeToken.getParameterized(List.class, clazz).getType();
                return gson.fromJson(root.getAsJsonArray("data"), listType);
            }
        } catch (Exception e) {
            Log.e(TAG, "extractList error", e);
        }
        return null;
    }
}