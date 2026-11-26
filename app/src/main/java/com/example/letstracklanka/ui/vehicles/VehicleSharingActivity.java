package com.example.letstracklanka.ui.vehicles;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.RespondToVehicleShareRequest;
import com.example.letstracklanka.data.model.VehicleShareResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.ConfirmSheet;
import com.example.letstracklanka.ui.common.EdgeToEdge;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.NetworkWatcher;
import com.example.letstracklanka.ui.common.Pill;
import com.example.letstracklanka.ui.common.Plates;
import com.example.letstracklanka.ui.common.TimeText;
import com.example.letstracklanka.ui.common.TopBar;
import com.example.letstracklanka.ui.more.ProfileText;
import com.example.letstracklanka.utils.ApiErrors;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.SecureScreen;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Sharing (api/VehicleShares): invites waiting for the customer (Accept / Decline on the card), vehicles
 * shared with them (open on the map), and the vehicles they share (Revoke an accepted share, Cancel a
 * pending invite). "Share a vehicle" opens ShareInviteSheet.
 *
 * Until someone accepts, the owner sees only the number they typed, never a name: the server sends the
 * name only after acceptance, so the list can't be used to look people up.
 *
 * Read on open, when the screen comes back, and on pull-to-refresh. Not polled: a new invite arrives as a
 * push notification. No Toast, Snackbar or dialog.
 */
public class VehicleSharingActivity extends AppCompatActivity {

    /** Optional: the vehicle to preselect in the invite sheet (opened from a vehicle's page). */
    public static final String EXTRA_VEHICLE_ID = "extra_vehicle_id";

    /** Declined and expired invites stay in the owner's list this long, then drop off. */
    private static final Duration ENDED_VISIBLE_FOR = Duration.ofDays(30);

    private ApiService api;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private View lists;
    private InlineBanner banner;
    private TextView invitesLabel;
    private LinearLayout invites, sharedWithMe, myShares;
    private TextView sharedWithMeEmpty, mySharesEmpty;
    @Nullable private String vehicleId;

    /** Each load gets a number; replies from an older load are dropped. */
    private int generation;
    private boolean firstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_vehicle_sharing);
        SecureScreen.guardTouches(this);
        TopBar.bind(this, R.string.st_sharing_title);
        EdgeToEdge.padBottom(findViewById(R.id.sharingContent));

        vehicleId = getIntent().getStringExtra(EXTRA_VEHICLE_ID);
        api = ApiClient.getClient().create(ApiService.class);
        swipe = findViewById(R.id.swipeSharing);
        progress = findViewById(R.id.progressSharing);
        lists = findViewById(R.id.groupSharingLists);
        banner = InlineBanner.in(this);
        invitesLabel = findViewById(R.id.tvInvitesLabel);
        invites = findViewById(R.id.listInvites);
        sharedWithMe = findViewById(R.id.listSharedWithMe);
        myShares = findViewById(R.id.listMyShares);
        sharedWithMeEmpty = findViewById(R.id.tvSharedWithMeEmpty);
        mySharesEmpty = findViewById(R.id.tvMySharesEmpty);

        swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.st_navy));
        swipe.setOnRefreshListener(this::load);
        findViewById(R.id.btnShareVehicle).setOnClickListener(v -> ShareInviteSheet.show(this, vehicleId, phone -> {
            load();
            banner.showSuccess(getString(R.string.st_share_sent, phone));
        }));

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) progress.setVisibility(View.VISIBLE);
        firstResume = false;
        load();
    }

    // ---------------------------------------------------------------------------------- loading

    private final class Load {
        final int id = ++generation;
        int pending = 3;
        boolean failed;
        boolean unauthorised;
        List<VehicleShareResponse> invited, shared, mine;

        void got(int which, @Nullable List<VehicleShareResponse> data, int code) {
            if (id != generation || VehicleSharingActivity.this.isFinishing() || VehicleSharingActivity.this.isDestroyed()) return;
            if (data == null) {
                failed = true;
                if (code == 401) unauthorised = true;
            }
            if (which == 0) invited = data;
            else if (which == 1) shared = data;
            else mine = data;
            if (--pending > 0) return;
            progress.setVisibility(View.GONE);
            swipe.setRefreshing(false);
            if (failed) {
                banner.showError(unauthorised ? R.string.st_error_session_expired : R.string.st_sharing_load_failed,
                        VehicleSharingActivity.this::load);
            }
            if (invited != null) showInvites(invited);
            if (shared != null) showSharedWithMe(shared);
            if (mine != null) showMyShares(mine);
            if (invited != null || shared != null || mine != null) lists.setVisibility(View.VISIBLE);
        }
    }

    private void load() {
        Load load = new Load();
        fetch(api.getPendingVehicleShareInvites(), load, 0);
        fetch(api.getVehiclesSharedWithMe(), load, 1);
        fetch(api.getMyVehicleShares(null), load, 2);
    }

    private void fetch(Call<ResponseBody> call, Load load, int which) {
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> c, @NonNull Response<ResponseBody> response) {
                List<VehicleShareResponse> data = null;
                if (response.isSuccessful()) {
                    try (ResponseBody body = response.body()) {
                        if (body != null) data = ApiJson.list(body.string(), VehicleShareResponse.class);
                    } catch (Exception ignored) {
                        data = null;
                    }
                } else {
                    ApiErrors.close(response);
                }
                load.got(which, clean(data), response.code());
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> c, @NonNull Throwable t) {
                load.got(which, null, 0);
            }
        });
    }

    @Nullable
    private static List<VehicleShareResponse> clean(@Nullable List<VehicleShareResponse> in) {
        if (in == null) return null;
        List<VehicleShareResponse> out = new ArrayList<>();
        for (VehicleShareResponse s : in) if (s != null && s.getShareId() != null) out.add(s);
        return out;
    }

    // ---------------------------------------------------------------------------- invites for me

    private void showInvites(List<VehicleShareResponse> list) {
        invites.removeAllViews();
        invitesLabel.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        for (VehicleShareResponse invite : list) {
            View card = LayoutInflater.from(this).inflate(R.layout.item_share_invite, invites, false);
            ((TextView) card.findViewById(R.id.tvInvitePlate)).setText(Plates.format(this, invite.getVehicleNumber()));
            ((TextView) card.findViewById(R.id.tvInviteVehicle)).setText(makeModel(invite));
            ((TextView) card.findViewById(R.id.tvInviteBody)).setText(
                    getString(R.string.st_sharing_invite_body, who(invite.getOtherPartyName(), invite.getOtherPartyPhoneNumber())));
            TextView error = card.findViewById(R.id.tvInviteError);
            View accept = card.findViewById(R.id.btnInviteAccept);
            View decline = card.findViewById(R.id.btnInviteDecline);
            accept.setOnClickListener(v -> respond(invite, true, accept, decline, error));
            decline.setOnClickListener(v -> respond(invite, false, accept, decline, error));
            invites.addView(card);
        }
    }

    private void respond(VehicleShareResponse invite, boolean accept, View btnAccept, View btnDecline, TextView error) {
        btnAccept.setEnabled(false);
        btnDecline.setEnabled(false);
        error.setVisibility(View.GONE);
        api.respondToVehicleShare(invite.getShareId(), new RespondToVehicleShareRequest(accept))
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        if (isFinishing() || isDestroyed()) {
                            ApiErrors.close(response);
                            return;
                        }
                        if (response.isSuccessful()) {
                            ApiErrors.close(response);
                            load();
                            banner.showSuccess(accept
                                    ? getString(R.string.st_sharing_accepted, Plates.plain(invite.getVehicleNumber()))
                                    : getString(R.string.st_sharing_declined));
                            return;
                        }
                        btnAccept.setEnabled(true);
                        btnDecline.setEnabled(true);
                        error.setText(ApiErrors.read(response, getString(R.string.st_sharing_respond_failed)).message);
                        error.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        btnAccept.setEnabled(true);
                        btnDecline.setEnabled(true);
                        error.setText(R.string.st_error_network);
                        error.setVisibility(View.VISIBLE);
                    }
                });
    }

    // --------------------------------------------------------------------------- shared with me

    private void showSharedWithMe(List<VehicleShareResponse> list) {
        sharedWithMe.removeAllViews();
        sharedWithMe.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        sharedWithMeEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < list.size(); i++) {
            VehicleShareResponse share = list.get(i);
            if (i > 0) addDivider(sharedWithMe);
            View row = LayoutInflater.from(this).inflate(R.layout.item_shared_with_me, sharedWithMe, false);
            String owner = who(share.getOtherPartyName(), share.getOtherPartyPhoneNumber());
            ((TextView) row.findViewById(R.id.tvSharedPlate)).setText(Plates.format(this, share.getVehicleNumber()));
            ((TextView) row.findViewById(R.id.tvSharedVehicle)).setText(makeModel(share));
            ((TextView) row.findViewById(R.id.tvSharedOwner)).setText(getString(R.string.st_sharing_from, owner));
            row.setContentDescription(Plates.plain(share.getVehicleNumber()) + ", " + makeModel(share) + ", "
                    + getString(R.string.st_sharing_from, owner));
            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, SharedVehicleMapActivity.class);
                intent.putExtra(SharedVehicleMapActivity.EXTRA_VEHICLE_ID, share.getVehicleId());
                intent.putExtra(SharedVehicleMapActivity.EXTRA_VEHICLE_TITLE, makeModel(share));
                intent.putExtra(SharedVehicleMapActivity.EXTRA_OWNER_NAME, owner);
                startActivity(intent);
            });
            sharedWithMe.addView(row);
        }
    }

    // ------------------------------------------------------------------------------ my shares

    private void showMyShares(List<VehicleShareResponse> all) {
        List<VehicleShareResponse> list = new ArrayList<>();
        Instant cutoff = Instant.now().minus(ENDED_VISIBLE_FOR);
        for (VehicleShareResponse s : all) {
            String st = status(s);
            if ("accepted".equals(st) || "pending".equals(st)) {
                list.add(s);
            } else if ("declined".equals(st) || "expired".equals(st)) {
                Instant when = TimeText.parse(s.getRespondedAt() != null ? s.getRespondedAt() : s.getInvitedAt());
                if (when != null && when.isAfter(cutoff)) list.add(s);
            }
        }
        myShares.removeAllViews();
        myShares.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        mySharesEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) addDivider(myShares);
            myShares.addView(myShareRow(list.get(i)));
        }
    }

    private View myShareRow(VehicleShareResponse share) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_my_share, myShares, false);
        String st = status(share);
        String name = share.getOtherPartyName() != null ? share.getOtherPartyName().trim() : "";
        String phone = ProfileText.phone(share.getOtherPartyPhoneNumber());
        String who = name.isEmpty() ? phone : name;

        TextView initials = row.findViewById(R.id.tvMyShareInitials);
        ImageView userIcon = row.findViewById(R.id.imgMyShareUser);
        initials.setText(ProfileText.initials(name));
        initials.setVisibility(name.isEmpty() ? View.GONE : View.VISIBLE);
        userIcon.setVisibility(name.isEmpty() ? View.VISIBLE : View.GONE);

        ((TextView) row.findViewById(R.id.tvMyShareWho)).setText(who);
        ((TextView) row.findViewById(R.id.tvMySharePlate)).setText(Plates.format(this, share.getVehicleNumber()));
        TextView pill = row.findViewById(R.id.tvMyShareStatus);
        TextView detail = row.findViewById(R.id.tvMyShareDetail);
        com.google.android.material.button.MaterialButton action = row.findViewById(R.id.btnMyShareAction);

        String statusLabel;
        switch (st) {
            case "accepted":
                statusLabel = getString(R.string.st_share_status_accepted);
                Pill.apply(pill, statusLabel, Pill.Tone.GREEN);
                detail.setText(getString(R.string.st_share_since,
                        TimeText.date(share.getRespondedAt() != null ? share.getRespondedAt() : share.getInvitedAt())));
                action.setText(R.string.st_share_revoke);
                action.setOnClickListener(v -> confirmEnd(share, who, false));
                break;
            case "pending":
                statusLabel = getString(R.string.st_share_status_invited);
                Pill.apply(pill, statusLabel, Pill.Tone.AMBER);
                detail.setText(TimeText.ago(this, share.getInvitedAt()));
                action.setText(R.string.st_share_cancel_invite);
                action.setOnClickListener(v -> confirmEnd(share, who, true));
                break;
            case "declined":
                statusLabel = getString(R.string.st_share_status_declined);
                Pill.apply(pill, statusLabel, Pill.Tone.GREY);
                detail.setText(TimeText.date(share.getRespondedAt()));
                action.setVisibility(View.GONE);
                break;
            default:
                statusLabel = getString(R.string.st_share_status_expired);
                Pill.apply(pill, statusLabel, Pill.Tone.GREY);
                detail.setText(TimeText.date(share.getInvitedAt()));
                action.setVisibility(View.GONE);
                break;
        }
        row.findViewById(R.id.myShareText).setContentDescription(who + ", " + statusLabel + ", "
                + Plates.plain(share.getVehicleNumber()) + ", " + detail.getText());
        action.setContentDescription(action.getText() + ", " + who);
        return row;
    }

    private void confirmEnd(VehicleShareResponse share, String who, boolean pendingInvite) {
        String plate = Plates.plain(share.getVehicleNumber());
        CharSequence title = pendingInvite
                ? getString(R.string.st_share_cancel_title, who)
                : getString(R.string.st_share_revoke_title, plate, who);
        ConfirmSheet.show(this, title,
                getString(pendingInvite ? R.string.st_share_cancel_body : R.string.st_share_revoke_body),
                getString(pendingInvite ? R.string.st_share_cancel_action : R.string.st_share_revoke_action),
                getString(pendingInvite ? R.string.st_share_cancel_busy : R.string.st_share_revoke_busy),
                done -> api.revokeVehicleShare(share.getShareId()).enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            ApiErrors.close(response);
                            done.succeeded();
                            if (isFinishing() || isDestroyed()) return;
                            load();
                            banner.showSuccess(getString(R.string.st_share_ended, who));
                            return;
                        }
                        done.failed(ApiErrors.read(response, getString(R.string.st_error_try_again)).message);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        done.failed(getString(R.string.st_error_network));
                    }
                }));
    }

    // --------------------------------------------------------------------------------- helpers

    private static String status(VehicleShareResponse s) {
        return s.getStatus() == null ? "" : s.getStatus().trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String makeModel(VehicleShareResponse s) {
        String make = s.getMake() == null ? "" : s.getMake();
        String model = s.getModel() == null ? "" : s.getModel();
        return (make + " " + model).trim();
    }

    private static String who(@Nullable String name, @Nullable String phone) {
        return name != null && !name.trim().isEmpty() ? name.trim() : ProfileText.phone(phone);
    }

    private void addDivider(LinearLayout parent) {
        View line = new View(this);
        line.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(getResources().getDisplayMetrics().density))));
        line.setBackgroundColor(ContextCompat.getColor(this, R.color.st_line_soft));
        line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(line);
    }
}
