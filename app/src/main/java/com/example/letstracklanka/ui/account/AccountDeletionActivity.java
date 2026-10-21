package com.example.letstracklanka.ui.account;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.DeleteAccountRequest;
import com.example.letstracklanka.data.remote.AccountApi;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.ui.auth.LoginActivity;
import com.example.letstracklanka.ui.main.SplashActivity;
import com.example.letstracklanka.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * "Delete my account" (Google Play: in-app deletion path) and, when the API says the account is
 * already waiting for deletion, the place to keep it.
 *
 * Three states, all inline (no toasts / snackbars / dialogs):
 *   ACTIVE  - nothing scheduled: explains what happens, asks the customer to type DELETE.
 *   PENDING - scheduled: shows the erase date and a "Keep my account" button.
 *   DONE    - just scheduled: the customer has been signed out; one button back to sign-in.
 *
 * The API needs a sign-in from the last 10 minutes for the delete request. If it answers
 * REAUTH_REQUIRED, the customer is told to sign out and in again (phone OTP is the only login).
 */
public class AccountDeletionActivity extends AppCompatActivity {

    public static final String EXTRA_FROM_LOCK = "from_lock";

    private static final String CONFIRM_WORD = "DELETE";
    private static final String REAUTH_CODE = "REAUTH_REQUIRED";

    private AccountApi api;

    private TextView tvTitle;
    private ProgressBar progress;
    private View groupLoadError;
    private View groupActive;
    private View groupPending;
    private View groupDone;

    private EditText etConfirm;
    private TextView tvActionError;
    private MaterialButton btnReauth;
    private MaterialButton btnDelete;

    private TextView tvPendingHeadline;
    private TextView tvPendingError;
    private MaterialButton btnKeep;

    private TextView tvDoneBody;

    /** True once the screen shows PENDING or DONE: Back then leaves the app (nothing else works). */
    private boolean leaveOnBack = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_deletion);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            goToLogin();
            return;
        }

        api = ApiClient.getClient().create(AccountApi.class);
        bindViews();
        setupActions();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                leaveScreen();
            }
        });

        loadStatus();
    }

    private void bindViews() {
        tvTitle = findViewById(R.id.tvAccountDeletionTitle);
        progress = findViewById(R.id.progressAccountDeletion);
        groupLoadError = findViewById(R.id.groupLoadError);
        groupActive = findViewById(R.id.groupActive);
        groupPending = findViewById(R.id.groupPending);
        groupDone = findViewById(R.id.groupDone);

        etConfirm = findViewById(R.id.etConfirmDelete);
        tvActionError = findViewById(R.id.tvActionError);
        btnReauth = findViewById(R.id.btnReauth);
        btnDelete = findViewById(R.id.btnDeleteAccount);

        tvPendingHeadline = findViewById(R.id.tvPendingHeadline);
        tvPendingError = findViewById(R.id.tvPendingError);
        btnKeep = findViewById(R.id.btnKeepAccount);

        tvDoneBody = findViewById(R.id.tvDoneBody);
    }

    private void setupActions() {
        findViewById(R.id.btnBackAccountDeletion).setOnClickListener(v -> leaveScreen());
        findViewById(R.id.btnRetryAccountDeletion).setOnClickListener(v -> loadStatus());
        findViewById(R.id.btnSignOutPending).setOnClickListener(v -> signOutToLogin());
        findViewById(R.id.btnDone).setOnClickListener(v -> goToLogin());

        btnReauth.setOnClickListener(v -> signOutToLogin());
        btnKeep.setOnClickListener(v -> cancelDeletion());
        btnDelete.setOnClickListener(v -> submitDeletion());

        setDeleteEnabled(false);
        etConfirm.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                setDeleteEnabled(CONFIRM_WORD.equals(s.toString().trim()));
            }
        });
    }

    // ---------------------------------------------------------------- state

    private void showLoading() {
        progress.setVisibility(View.VISIBLE);
        groupLoadError.setVisibility(View.GONE);
        groupActive.setVisibility(View.GONE);
        groupPending.setVisibility(View.GONE);
        groupDone.setVisibility(View.GONE);
    }

    private void showLoadError() {
        progress.setVisibility(View.GONE);
        groupLoadError.setVisibility(View.VISIBLE);
        groupActive.setVisibility(View.GONE);
        groupPending.setVisibility(View.GONE);
        groupDone.setVisibility(View.GONE);
    }

    private void showActive() {
        progress.setVisibility(View.GONE);
        groupLoadError.setVisibility(View.GONE);
        groupActive.setVisibility(View.VISIBLE);
        groupPending.setVisibility(View.GONE);
        groupDone.setVisibility(View.GONE);
        tvTitle.setText("Delete account");
        leaveOnBack = false;
    }

    private void showPending(String scheduledFor, int daysLeft) {
        progress.setVisibility(View.GONE);
        groupLoadError.setVisibility(View.GONE);
        groupActive.setVisibility(View.GONE);
        groupPending.setVisibility(View.VISIBLE);
        groupDone.setVisibility(View.GONE);
        tvTitle.setText("Account scheduled for deletion");
        leaveOnBack = true;

        String when = formatDate(scheduledFor);
        String left = daysLeft == 1 ? "1 day left" : daysLeft + " days left";
        tvPendingHeadline.setText(when.isEmpty()
                ? "Your account will be permanently deleted (" + left + ")."
                : "Your account will be permanently deleted on " + when + " (" + left + ").");
    }

    private void showDone(String scheduledFor) {
        progress.setVisibility(View.GONE);
        groupLoadError.setVisibility(View.GONE);
        groupActive.setVisibility(View.GONE);
        groupPending.setVisibility(View.GONE);
        groupDone.setVisibility(View.VISIBLE);
        tvTitle.setText("Deletion scheduled");
        leaveOnBack = true;

        String when = formatDate(scheduledFor);
        tvDoneBody.setText("Your account is locked and you have been signed out.\n\n"
                + (when.isEmpty() ? "It will be permanently deleted in 30 days. " : "It will be permanently deleted on " + when + ". ")
                + "Sign in before then if you change your mind.");
    }

    private void setDeleteEnabled(boolean enabled) {
        btnDelete.setEnabled(enabled);
        btnDelete.setAlpha(enabled ? 1f : 0.4f);
    }

    private void showActionError(String message, boolean offerReauth) {
        tvActionError.setText(message);
        tvActionError.setVisibility(View.VISIBLE);
        btnReauth.setVisibility(offerReauth ? View.VISIBLE : View.GONE);
    }

    private void showPendingError(String message) {
        tvPendingError.setText(message);
        tvPendingError.setVisibility(View.VISIBLE);
    }

    // ---------------------------------------------------------------- calls

    private void loadStatus() {
        showLoading();
        api.getDeletionStatus().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) return;
                JsonObject data = response.isSuccessful() ? readData(response) : null;
                if (data == null) {
                    showLoadError();
                    return;
                }
                if (getBoolean(data, "pending")) {
                    showPending(getString(data, "scheduledFor"), getInt(data, "daysLeft"));
                } else {
                    showActive();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                showLoadError();
            }
        });
    }

    private void submitDeletion() {
        if (!CONFIRM_WORD.equals(etConfirm.getText().toString().trim())) return;

        tvActionError.setVisibility(View.GONE);
        btnReauth.setVisibility(View.GONE);
        setDeleteEnabled(false);
        btnDelete.setText("Deleting...");

        api.requestDeletion(new DeleteAccountRequest(CONFIRM_WORD)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) return;
                btnDelete.setText("Delete my account");

                if (response.isSuccessful()) {
                    JsonObject data = readData(response);
                    // The server has revoked this device's sign-in already; clear it locally too.
                    FirebaseAuth.getInstance().signOut();
                    new SessionManager(getApplicationContext()).logoutUser();
                    showDone(data != null ? getString(data, "scheduledFor") : "");
                    return;
                }

                setDeleteEnabled(CONFIRM_WORD.equals(etConfirm.getText().toString().trim()));
                String errorJson = readErrorBody(response);
                int code = response.code();
                if (code == 403 && errorJson != null && errorJson.contains(REAUTH_CODE)) {
                    String msg = serverMessage(errorJson);
                    showActionError(msg != null ? msg
                            : "For your security, please sign out and sign in again, then confirm within 10 minutes.", true);
                } else if (code == 429) {
                    showActionError("Too many attempts. Please wait a minute and try again.", false);
                } else if (code == 401) {
                    showActionError("Your session has expired. Please sign out and sign in again.", true);
                } else {
                    showActionError("Something went wrong. Please try again.", false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                btnDelete.setText("Delete my account");
                setDeleteEnabled(CONFIRM_WORD.equals(etConfirm.getText().toString().trim()));
                showActionError("No connection. Check your internet and try again.", false);
            }
        });
    }

    private void cancelDeletion() {
        tvPendingError.setVisibility(View.GONE);
        btnKeep.setEnabled(false);
        btnKeep.setText("Cancelling...");

        api.cancelDeletion().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful()) {
                    // Restart from the splash so every screen reloads against the unlocked account.
                    Intent intent = new Intent(AccountDeletionActivity.this, SplashActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                    return;
                }
                btnKeep.setEnabled(true);
                btnKeep.setText("Keep my account");
                showPendingError("We couldn't cancel the deletion. Please try again.");
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                btnKeep.setEnabled(true);
                btnKeep.setText("Keep my account");
                showPendingError("No connection. Check your internet and try again.");
            }
        });
    }

    // ---------------------------------------------------------------- navigation

    private void leaveScreen() {
        if (leaveOnBack) {
            // This screen is the root of its task; nothing else works for a locked account.
            finishAffinity();
        } else {
            finish();
        }
    }

    private void signOutToLogin() {
        FirebaseAuth.getInstance().signOut();
        new SessionManager(getApplicationContext()).logoutUser();
        goToLogin();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ---------------------------------------------------------------- parsing helpers

    private static JsonObject readData(Response<ResponseBody> response) {
        try (ResponseBody body = response.body()) {
            if (body == null) return null;
            JsonObject root = new Gson().fromJson(body.string(), JsonObject.class);
            if (root == null) return null;
            return root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : root;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readErrorBody(Response<ResponseBody> response) {
        try (ResponseBody body = response.errorBody()) {
            return body != null ? body.string() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String serverMessage(String errorJson) {
        try {
            JsonObject root = new Gson().fromJson(errorJson, JsonObject.class);
            if (root != null && root.has("message") && !root.get("message").isJsonNull()) {
                return root.get("message").getAsString();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private static boolean getBoolean(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
    }

    private static int getInt(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : 0;
    }

    private static String getString(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    /** "2026-11-20T10:15:00Z" -> "20 Nov 2026". Empty when it cannot be read. */
    private static String formatDate(String iso) {
        if (iso == null || iso.length() < 10) return "";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso.substring(0, 10));
            return d == null ? "" : new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(d);
        } catch (Exception e) {
            return "";
        }
    }
}