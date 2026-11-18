package com.example.letstracklanka.ui.main;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.account.AccountDeletionActivity;
import com.example.letstracklanka.ui.common.BottomNav;
import com.example.letstracklanka.ui.common.EdgeToEdge;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.NetworkWatcher;
import com.example.letstracklanka.ui.complaints.MyComplaintsActivity;
import com.example.letstracklanka.ui.contacts.EmergencyContactsActivity;
import com.example.letstracklanka.ui.geofences.GeofencesActivity;
import com.example.letstracklanka.ui.more.AppPlanSheet;
import com.example.letstracklanka.ui.more.EditProfileSheet;
import com.example.letstracklanka.ui.more.MoreRows;
import com.example.letstracklanka.ui.more.ProfileText;
import com.example.letstracklanka.ui.more.SupportSheet;
import com.example.letstracklanka.ui.places.FrequentPlacesActivity;
import com.example.letstracklanka.ui.places.PlacesActivity;
import com.example.letstracklanka.ui.renewals.RenewalActivity;
import com.example.letstracklanka.ui.stats.StatsCompareActivity;
import com.example.letstracklanka.ui.vehicles.AddVehicleActivity;
import com.example.letstracklanka.ui.vehicles.VehicleSharingActivity;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.CustomerSession;
import com.example.letstracklanka.utils.SecureScreen;
import com.example.letstracklanka.utils.SignOut;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * More tab. Replaces the side drawer that was copied into five screens. Every row opens a real,
 * working feature; the Letstrack leftovers (Refer and earn, Shop, Help videos, Voice track, the
 * call-center carousel, fuel and temperature reports, the "coming soon" settings) are gone.
 *
 * Profile: read once when the tab is created and again after an edit; never polled.
 */
public class MoreActivity extends AppCompatActivity {

    private static final String PRIVACY_URL = "https://www.shalotrack.com/privacy.html";

    private ApiService api;
    private InlineBanner banner;
    private TextView tvInitials, tvName, tvPhone, tvEmail, tvVersion;
    private MaterialButton btnEdit;
    @Nullable private CustomerResponse profile;
    private boolean profileLoading;
    private String appVersion = "";
    @Nullable private BottomSheetDialog signOutSheet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_more);
        SecureScreen.guardTouches(this);
        api = ApiClient.getClient().create(ApiService.class);

        EdgeToEdge.padTop(findViewById(R.id.moreContent));
        banner = InlineBanner.in(this);
        tvInitials = findViewById(R.id.tvAvatarInitials);
        tvName = findViewById(R.id.tvProfileName);
        tvPhone = findViewById(R.id.tvProfilePhone);
        tvEmail = findViewById(R.id.tvProfileEmail);
        tvVersion = findViewById(R.id.tvAppVersion);
        btnEdit = findViewById(R.id.btnEditProfile);
        btnEdit.setOnClickListener(v -> {
            if (profile != null) EditProfileSheet.show(this, profile, this::onNameSaved);
        });

        appVersion = readVersion();
        tvVersion.setText(getString(R.string.st_more_version, appVersion));

        buildSections();
        BottomNav.bind(this, BottomNav.Tab.MORE);

        NetworkWatcher.watch(this, new NetworkWatcher.Listener() {
            @Override
            public void onLost() {
                banner.showOffline(R.string.st_banner_offline_generic, null);
            }

            @Override
            public void onRestored() {
                banner.hide();
                if (profile == null) loadProfile();
            }
        });

        showProfilePlaceholder();
        loadProfile();
    }

    // ---- Profile ----

    private void showProfilePlaceholder() {
        tvInitials.setText("");
        tvName.setText(R.string.st_more_loading_profile);
        tvPhone.setText("");
        tvEmail.setVisibility(View.GONE);
        btnEdit.setEnabled(false);
    }

    private void loadProfile() {
        if (profileLoading) return;
        profileLoading = true;
        api.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                profileLoading = false;
                if (isDestroyed()) return;
                CustomerResponse c = null;
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) c = ApiJson.object(body.string(), CustomerResponse.class);
                } catch (Exception ignored) {
                    c = null;
                }
                if (c == null || c.getCustomerId() == null) {
                    onProfileFailed(false);
                    return;
                }
                profile = c;
                CustomerSession.remember(MoreActivity.this, c.getCustomerId());
                showProfile(c.getFullName(), c.getPhoneNumber(), c.getEmail());
                btnEdit.setEnabled(true);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                profileLoading = false;
                if (!isDestroyed()) onProfileFailed(true);
            }
        });
    }

    private void onProfileFailed(boolean network) {
        tvName.setText(R.string.st_more_profile_unavailable);
        banner.showError(network ? R.string.st_error_network : R.string.st_more_profile_failed, () -> {
            banner.hide();
            tvName.setText(R.string.st_more_loading_profile);
            loadProfile();
        });
    }

    private void showProfile(@Nullable String name, @Nullable String phone, @Nullable String email) {
        String n = name != null && !name.trim().isEmpty() ? name.trim() : getString(R.string.st_more_no_name);
        tvName.setText(n);
        tvInitials.setText(ProfileText.initials(name));
        tvPhone.setText(ProfileText.phone(phone));
        if (email != null && !email.trim().isEmpty()) {
            tvEmail.setText(email.trim());
            tvEmail.setVisibility(View.VISIBLE);
        } else {
            tvEmail.setVisibility(View.GONE);
        }
        findViewById(R.id.profileCard).setContentDescription(getString(R.string.st_more_profile_a11y,
                n, ProfileText.phone(phone)));
    }

    private void onNameSaved(@NonNull String fullName) {
        tvName.setText(fullName);
        tvInitials.setText(ProfileText.initials(fullName));
        banner.hide();
        // Re-read so the edit sheet starts from what the server stored.
        loadProfile();
    }

    // ---- Sections ----

    private void buildSections() {
        ViewGroup sections = findViewById(R.id.moreSections);
        sections.removeAllViews();

        ViewGroup vehicles = section(sections, R.string.st_more_section_vehicles);
        row(vehicles, R.drawable.ic_st_plus, R.string.st_more_add_vehicle, R.string.st_more_add_vehicle_detail, this::openAddVehicle);
        row(vehicles, R.drawable.ic_st_device, R.string.st_more_trackers, R.string.st_more_trackers_detail,
                () -> open(com.example.letstracklanka.ui.devices.DevicesActivity.class));
        row(vehicles, R.drawable.ic_st_users, R.string.st_more_sharing, R.string.st_more_sharing_detail,
                () -> open(VehicleSharingActivity.class));
        row(vehicles, R.drawable.ic_st_geofence, R.string.st_more_geofences, R.string.st_more_geofences_detail,
                () -> open(GeofencesActivity.class));
        row(vehicles, R.drawable.ic_st_pin, R.string.st_more_places, R.string.st_more_places_detail,
                () -> open(PlacesActivity.class));
        row(vehicles, R.drawable.ic_st_target, R.string.st_more_frequent_places, R.string.st_more_frequent_places_detail,
                () -> open(FrequentPlacesActivity.class));
        row(vehicles, R.drawable.ic_st_chart, R.string.st_more_compare, R.string.st_more_compare_detail,
                () -> open(StatsCompareActivity.class));

        ViewGroup reports = section(sections, R.string.st_more_section_reports);
        row(reports, R.drawable.ic_st_route, R.string.st_more_report_distance, R.string.st_more_report_distance_detail,
                () -> ReportFilterBottomSheet.show(this, ReportFilterBottomSheet.ReportType.KM));
        row(reports, R.drawable.ic_st_navigate, R.string.st_more_report_trips, R.string.st_more_report_trips_detail,
                () -> ReportFilterBottomSheet.show(this, ReportFilterBottomSheet.ReportType.TRIP));
        row(reports, R.drawable.ic_st_bell, R.string.st_more_report_alerts, R.string.st_more_report_alerts_detail,
                () -> ReportFilterBottomSheet.show(this, ReportFilterBottomSheet.ReportType.ALERT));
        row(reports, R.drawable.ic_st_clock, R.string.st_more_report_stops, R.string.st_more_report_stops_detail,
                () -> ReportFilterBottomSheet.show(this, ReportFilterBottomSheet.ReportType.STOP_ALERT));

        ViewGroup safety = section(sections, R.string.st_more_section_safety);
        row(safety, R.drawable.ic_st_sos, R.string.st_more_emergency_contacts, R.string.st_more_emergency_contacts_detail,
                () -> open(EmergencyContactsActivity.class));
        row(safety, R.drawable.ic_st_sliders, R.string.st_more_notifications, R.string.st_more_notifications_detail,
                () -> open(SettingsActivity.class));

        ViewGroup billing = section(sections, R.string.st_more_section_billing);
        row(billing, R.drawable.ic_st_calendar, R.string.st_more_renewals, R.string.st_more_renewals_detail,
                () -> open(RenewalActivity.class));
        row(billing, R.drawable.ic_st_receipt, R.string.st_more_app_plan, R.string.st_more_app_plan_detail,
                () -> AppPlanSheet.show(this));

        ViewGroup help = section(sections, R.string.st_more_section_help);
        row(help, R.drawable.ic_st_headset, R.string.st_more_support, R.string.st_more_support_detail,
                () -> SupportSheet.show(this, appVersion));
        row(help, R.drawable.ic_st_message, R.string.st_more_complaints, R.string.st_more_complaints_detail,
                () -> open(MyComplaintsActivity.class));

        ViewGroup account = section(sections, R.string.st_more_section_account);
        row(account, R.drawable.ic_st_shield, R.string.st_more_privacy, 0, this::openPrivacyPolicy);
        row(account, R.drawable.ic_st_logout, R.string.st_more_sign_out, 0, this::confirmSignOut);
        MoreRows.add(account, R.drawable.ic_st_trash, getString(R.string.st_more_delete_account),
                getString(R.string.st_more_delete_account_detail), true, v -> open(AccountDeletionActivity.class));
    }

    private ViewGroup section(ViewGroup parent, @StringRes int title) {
        View section = LayoutInflater.from(this).inflate(R.layout.item_more_section, parent, false);
        ((TextView) section.findViewById(R.id.tvMoreSectionTitle)).setText(title);
        parent.addView(section);
        return section.findViewById(R.id.moreSectionCard);
    }

    private void row(ViewGroup card, @DrawableRes int icon, @StringRes int title, @StringRes int detail, Runnable action) {
        MoreRows.add(card, icon, getString(title), detail != 0 ? getString(detail) : null, false, v -> action.run());
    }

    private void open(Class<?> screen) {
        startActivity(new Intent(this, screen));
    }

    private void openAddVehicle() {
        String cached = CustomerSession.cachedId(this);
        if (cached != null) {
            launchAddVehicle(cached);
            return;
        }
        CustomerSession.get(this, new CustomerSession.Callback2() {
            @Override
            public void onCustomer(@NonNull String customerId) {
                if (!isDestroyed()) launchAddVehicle(customerId);
            }

            @Override
            public void onError(int httpCode) {
                if (!isDestroyed()) banner.showError(httpCode == 0 ? R.string.st_error_network : R.string.st_more_profile_failed,
                        () -> {
                            banner.hide();
                            openAddVehicle();
                        });
            }
        });
    }

    private void launchAddVehicle(String customerId) {
        Intent intent = new Intent(this, AddVehicleActivity.class);
        intent.putExtra(AddVehicleActivity.EXTRA_CUSTOMER_ID, customerId);
        startActivity(intent);
    }

    private void openPrivacyPolicy() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)));
        } catch (ActivityNotFoundException e) {
            banner.showError(R.string.st_error_no_browser, null);
        }
    }

    private void confirmSignOut() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        signOutSheet = sheet;
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_confirm, null);
        sheet.setContentView(view);
        ((TextView) view.findViewById(R.id.tvConfirmTitle)).setText(R.string.st_sign_out_title);
        ((TextView) view.findViewById(R.id.tvConfirmBody)).setText(R.string.st_sign_out_body);
        MaterialButton action = view.findViewById(R.id.btnConfirmAction);
        MaterialButton cancel = view.findViewById(R.id.btnConfirmCancel);
        action.setText(R.string.st_more_sign_out);
        cancel.setOnClickListener(v -> sheet.dismiss());
        action.setOnClickListener(v -> {
            action.setEnabled(false);
            cancel.setEnabled(false);
            action.setText(R.string.st_sign_out_progress);
            sheet.setCancelable(false);
            // Removes this phone's push token from the account first (so the next person who signs in on
            // this phone never receives this customer's alerts), then clears local data and goes to sign-in.
            SignOut.perform(this);
        });
        sheet.show();
    }

    @Override
    protected void onDestroy() {
        // Sign-out closes this screen while its sheet is up; close the sheet with it.
        if (signOutSheet != null && signOutSheet.isShowing()) signOutSheet.dismiss();
        super.onDestroy();
    }

    private String readVersion() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v != null ? v : "";
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }
}
