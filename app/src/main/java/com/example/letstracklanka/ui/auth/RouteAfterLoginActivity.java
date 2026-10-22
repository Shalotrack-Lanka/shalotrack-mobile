package com.example.letstracklanka.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.account.PendingDeletionRouter;
import com.example.letstracklanka.ui.main.MainActivity;
import com.example.letstracklanka.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Where a phone goes right after it has been verified (OTP typed, auto-verified, or an existing
 * Firebase session without a verified email).
 *
 * The SERVER decides, not the phone: GET api/Customers/me
 *   200 -> this phone already has a customer profile: straight into the app (no forms again).
 *   404 -> genuinely new customer: the registration form (EmailInputActivity).
 *   403 ACCOUNT_PENDING_DELETION -> the deletion screen (opened by PendingDeletionRouter).
 *   anything else / no network -> an inline message with Retry and Sign out. Never a guess:
 *   guessing "new customer" is what used to make returning customers re-type everything.
 */
public class RouteAfterLoginActivity extends AppCompatActivity {

    private View groupLoading;
    private View groupError;
    private TextView tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_after_login);

        groupLoading = findViewById(R.id.groupRouteLoading);
        groupError = findViewById(R.id.groupRouteError);
        tvError = findViewById(R.id.tvRouteError);

        findViewById(R.id.btnRouteRetry).setOnClickListener(v -> checkProfile());
        findViewById(R.id.btnRouteSignOut).setOnClickListener(v -> signOut());

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            goTo(LoginActivity.class);
            return;
        }
        checkProfile();
    }

    private void checkProfile() {
        groupError.setVisibility(View.GONE);
        groupLoading.setVisibility(View.VISIBLE);

        ApiClient.getClient().create(ApiService.class).getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) return;

                int code = response.code();
                if (response.isSuccessful()) {
                    goTo(MainActivity.class);
                } else if (code == 404) {
                    goTo(EmailInputActivity.class);
                } else if (code == 403 && bodyMentionsPendingDeletion(response)) {
                    // The interceptor has already asked for the deletion screen; asking again is harmless.
                    PendingDeletionRouter.onPendingDeletion();
                } else if (code == 401) {
                    showError("Your sign-in has expired. Please sign out and sign in again.");
                } else {
                    showError("We couldn't check your account. Please try again.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                showError("No connection. Check your internet and try again.");
            }
        });
    }

    private static boolean bodyMentionsPendingDeletion(Response<ResponseBody> response) {
        try (ResponseBody body = response.errorBody()) {
            return body != null && body.string().contains("ACCOUNT_PENDING_DELETION");
        } catch (Exception e) {
            return false;
        }
    }

    private void showError(String message) {
        groupLoading.setVisibility(View.GONE);
        tvError.setText(message);
        groupError.setVisibility(View.VISIBLE);
    }

    private void signOut() {
        FirebaseAuth.getInstance().signOut();
        new SessionManager(getApplicationContext()).logoutUser();
        goTo(LoginActivity.class);
    }

    private void goTo(Class<?> target) {
        Intent intent = new Intent(this, target);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}