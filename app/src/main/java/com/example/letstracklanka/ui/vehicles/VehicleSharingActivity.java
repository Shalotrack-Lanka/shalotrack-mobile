package com.example.letstracklanka.ui.vehicles;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.RespondToVehicleShareRequest;
import com.example.letstracklanka.data.model.VehicleShareResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Vehicle Sharing screen -- pending invites (with accept/decline right on
 * the card) and vehicles currently shared with you, matching the confirmed
 * design mockup.
 *
 * Honest scope note: tapping a shared vehicle currently shows a short
 * message rather than opening full live tracking. Actually wiring that in
 * means teaching HomeActivity/VehiclesActivity's core vehicle list to merge
 * owned and shared vehicles together, which is a separate, larger piece of
 * work than this screen -- flagged here rather than faked.
 */
public class VehicleSharingActivity extends AppCompatActivity {

    private ApiService mainApiService;

    private View errorBanner;
    private TextView tvErrorBannerMessage;
    private NestedScrollView scrollContent;
    private ProgressBar progressSharing;

    private TextView tvPendingInvitesLabel;
    private LinearLayout pendingInvitesContainer;
    private LinearLayout sharedWithMeContainer;
    private View tvNoSharedVehicles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicle_sharing);

        mainApiService = ApiClient.getClient().create(ApiService.class);

        initViews();
        fetchAll();
    }

    private void initViews() {
        View btnBack = findViewById(R.id.btnBackSharing);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        scrollContent = findViewById(R.id.scrollSharingContent);
        progressSharing = findViewById(R.id.progressSharing);

        tvPendingInvitesLabel = findViewById(R.id.tvPendingInvitesLabel);
        pendingInvitesContainer = findViewById(R.id.pendingInvitesContainer);
        sharedWithMeContainer = findViewById(R.id.sharedWithMeContainer);
        tvNoSharedVehicles = findViewById(R.id.tvNoSharedVehicles);
    }

    private void fetchAll() {
        hideError();
        setLoading(true);
        fetchPendingInvites();
    }

    private void fetchPendingInvites() {
        mainApiService.getPendingVehicleShareInvites().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                List<VehicleShareResponse> invites = new ArrayList<>();
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<VehicleShareResponse> parsed = parseList(body.string());
                        if (parsed != null) invites = parsed;
                    } else {
                        Log.w("VehicleSharingActivity", "fetchPendingInvites failed, code " + response.code());
                    }
                } catch (Exception e) {
                    Log.e("VehicleSharingActivity", "fetchPendingInvites parse error", e);
                }
                displayPendingInvites(invites);
                fetchSharedWithMe(); // chained, not parallel -- keeps the screen simple
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("VehicleSharingActivity", "fetchPendingInvites network error", t);
                fetchSharedWithMe();
            }
        });
    }

    private void fetchSharedWithMe() {
        mainApiService.getVehiclesSharedWithMe().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                setLoading(false);
                List<VehicleShareResponse> shared = new ArrayList<>();
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<VehicleShareResponse> parsed = parseList(body.string());
                        if (parsed != null) shared = parsed;
                    } else {
                        Log.w("VehicleSharingActivity", "fetchSharedWithMe failed, code " + response.code());
                        showError("Couldn't load shared vehicles (code " + response.code() + ")");
                    }
                } catch (Exception e) {
                    Log.e("VehicleSharingActivity", "fetchSharedWithMe parse error", e);
                }
                displaySharedWithMe(shared);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                setLoading(false);
                Log.e("VehicleSharingActivity", "fetchSharedWithMe network error", t);
                showError("Network error \u2014 check your connection.");
            }
        });
    }

    private void displayPendingInvites(List<VehicleShareResponse> invites) {
        if (scrollContent != null) scrollContent.setVisibility(View.VISIBLE);
        if (pendingInvitesContainer == null) return;

        pendingInvitesContainer.removeAllViews();
        boolean hasInvites = invites != null && !invites.isEmpty();
        if (tvPendingInvitesLabel != null) {
            tvPendingInvitesLabel.setVisibility(hasInvites ? View.VISIBLE : View.GONE);
        }
        if (!hasInvites) return;

        for (VehicleShareResponse invite : invites) {
            addInviteCard(invite);
        }
    }

    private void displaySharedWithMe(List<VehicleShareResponse> shared) {
        if (sharedWithMeContainer == null) return;
        sharedWithMeContainer.removeAllViews();

        boolean hasShared = shared != null && !shared.isEmpty();
        if (tvNoSharedVehicles != null) tvNoSharedVehicles.setVisibility(hasShared ? View.GONE : View.VISIBLE);
        if (!hasShared) return;

        for (VehicleShareResponse share : shared) {
            addSharedVehicleRow(share);
        }
    }

    // Matches the confirmed mockup: avatar circle, name + vehicle, accept
    // and decline buttons directly on the card.
    private void addInviteCard(VehicleShareResponse invite) {
        int density = (int) getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_stat_card);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 10 * density);
        card.setLayoutParams(cardParams);
        card.setPadding(14 * density, 14 * density, 14 * density, 14 * density);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams topRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        topRowParams.bottomMargin = 12 * density;
        topRow.setLayoutParams(topRowParams);

        LinearLayout avatar = new LinearLayout(this);
        int avatarSize = 40 * density;
        avatar.setLayoutParams(new LinearLayout.LayoutParams(avatarSize, avatarSize));
        avatar.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable avatarBg = new android.graphics.drawable.GradientDrawable();
        avatarBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        avatarBg.setColor(ContextCompat.getColor(this, R.color.brand_accent));
        avatar.setBackground(avatarBg);
        TextView avatarText = new TextView(this);
        String name = invite.getOtherPartyName() != null ? invite.getOtherPartyName() : "?";
        avatarText.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
        avatarText.setTextColor(Color.WHITE);
        avatarText.setTextSize(15);
        avatarText.setTypeface(null, android.graphics.Typeface.BOLD);
        avatar.addView(avatarText);
        topRow.addView(avatar);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textColParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textColParams.setMarginStart(12 * density);
        textCol.setLayoutParams(textColParams);
        TextView titleText = new TextView(this);
        titleText.setText(name + " wants to share");
        titleText.setTextSize(14);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        TextView subtitleText = new TextView(this);
        subtitleText.setText(invite.getMake() + " " + invite.getModel() + " \u2022 " + invite.getVehicleNumber());
        subtitleText.setTextSize(13);
        subtitleText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        textCol.addView(titleText);
        textCol.addView(subtitleText);
        topRow.addView(textCol);

        card.addView(topRow);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonRow.setLayoutParams(buttonRowParams);

        android.widget.Button btnAccept = new android.widget.Button(this);
        btnAccept.setText("Accept");
        btnAccept.setAllCaps(false);
        btnAccept.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_success)));
        btnAccept.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams acceptParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        acceptParams.setMarginEnd(8 * density);
        btnAccept.setLayoutParams(acceptParams);
        btnAccept.setOnClickListener(v -> respondToInvite(invite, true));
        buttonRow.addView(btnAccept);

        android.widget.Button btnDecline = new android.widget.Button(this);
        btnDecline.setText("Decline");
        btnDecline.setAllCaps(false);
        LinearLayout.LayoutParams declineParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnDecline.setLayoutParams(declineParams);
        btnDecline.setOnClickListener(v -> respondToInvite(invite, false));
        buttonRow.addView(btnDecline);

        card.addView(buttonRow);
        pendingInvitesContainer.addView(card);
    }

    // Matches the confirmed mockup: car icon, vehicle name + owner, chevron.
    private void addSharedVehicleRow(VehicleShareResponse share) {
        int density = (int) getResources().getDisplayMetrics().density;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_stat_card);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, 10 * density);
        row.setLayoutParams(rowParams);
        row.setPadding(14 * density, 12 * density, 14 * density, 12 * density);

        ImageView carIcon = new ImageView(this);
        carIcon.setImageResource(R.drawable.ic_car_3d_small);
        int iconSize = 26 * density;
        carIcon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        row.addView(carIcon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textColParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textColParams.setMarginStart(12 * density);
        textCol.setLayoutParams(textColParams);
        TextView nameText = new TextView(this);
        nameText.setText(share.getMake() + " " + share.getModel());
        nameText.setTextSize(15);
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);
        nameText.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        TextView ownerText = new TextView(this);
        ownerText.setText("Shared by " + share.getOtherPartyName());
        ownerText.setTextSize(12);
        ownerText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        textCol.addView(nameText);
        textCol.addView(ownerText);
        row.addView(textCol);

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(ContextCompat.getColor(this, R.color.text_muted));
        int chevronSize = 20 * density;
        chevron.setLayoutParams(new LinearLayout.LayoutParams(chevronSize, chevronSize));
        row.addView(chevron);

        row.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, SharedVehicleMapActivity.class);
            intent.putExtra(SharedVehicleMapActivity.EXTRA_VEHICLE_ID, share.getVehicleId());
            intent.putExtra(SharedVehicleMapActivity.EXTRA_VEHICLE_TITLE, share.getMake() + " " + share.getModel());
            intent.putExtra(SharedVehicleMapActivity.EXTRA_OWNER_NAME, share.getOtherPartyName());
            startActivity(intent);
        });

        sharedWithMeContainer.addView(row);
    }

    private void respondToInvite(VehicleShareResponse invite, boolean accept) {
        RespondToVehicleShareRequest request = new RespondToVehicleShareRequest(accept);
        mainApiService.respondToVehicleShare(invite.getShareId(), request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(VehicleSharingActivity.this,
                            accept ? "Invite accepted." : "Invite declined.", Toast.LENGTH_SHORT).show();
                    fetchAll();
                } else {
                    Log.w("VehicleSharingActivity", "respondToInvite failed, code " + response.code());
                    Toast.makeText(VehicleSharingActivity.this, "Couldn't respond. Try again.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("VehicleSharingActivity", "respondToInvite network error", t);
                Toast.makeText(VehicleSharingActivity.this, "Network error \u2014 check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        if (progressSharing != null) progressSharing.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        if (errorBanner == null) return;
        errorBanner.setVisibility(View.VISIBLE);
        if (tvErrorBannerMessage != null) tvErrorBannerMessage.setText(message);
    }

    private void hideError() {
        if (errorBanner != null) errorBanner.setVisibility(View.GONE);
    }

    private List<VehicleShareResponse> parseList(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<VehicleShareResponse>>() {}.getType();
                return gson.fromJson(root.getAsJsonArray("data"), listType);
            }
        } catch (Exception e) {
            Log.e("VehicleSharingActivity", "parseList error", e);
        }
        return null;
    }
}