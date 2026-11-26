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
import com.example.letstracklanka.ui.more.ExportFormatSheet;
import com.example.letstracklanka.ui.more.MoreRows;
import com.example.letstracklanka.ui.more.MyDataPdf;
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

    // Weekly summary switch (account-wide, Fleet parity)
    @Nullable private com.google.android.material.materialswitch.MaterialSwitch weeklySwitch;
    @Nullable private TextView weeklyDetail;
    private boolean weeklySaving;

    // Download my data. The export lives in memory only: it is written straight to the file the customer picks,
    // and kept for EXPORT_REUSE_MS so taking the PDF and then the JSON costs one of the 3 hourly exports, not two.
    private static final long EXPORT_REUSE_MS = 10 * 60 * 1000L;
    @Nullable private com.google.gson.JsonObject exportData;
    private long exportFetchedAt;
    @Nullable private ExportFormatSheet.Format pendingFormat;
    @Nullable private View exportRow;
    private boolean exporting;
    private androidx.activity.result.ActivityResultLauncher<String> savePdf, saveJson;
    private final java.util.concurrent.ExecutorService exportWorker = java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_more);
        SecureScreen.guardTouches(this);
        api = ApiClient.getClient().create(ApiService.class);
        savePdf = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.CreateDocument(ExportFormatSheet.Format.PDF.mime),
                uri -> writeExport(uri, ExportFormatSheet.Format.PDF));
        saveJson = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.CreateDocument(ExportFormatSheet.Format.JSON.mime),
                uri -> writeExport(uri, ExportFormatSheet.Format.JSON));

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
        // Which alerts send a push is set per vehicle (Alert settings) and enforced by the server. This row opens
        // Android's own settings for the app: sound, vibration, or every notification off on this phone.
        row(safety, R.drawable.ic_st_bell, R.string.st_more_notifications, R.string.st_more_notifications_detail,
                this::openSystemNotificationSettings);

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

        addWeeklySummaryRow(safety);

        ViewGroup account = section(sections, R.string.st_more_section_account);
        exportRow = MoreRows.add(account, R.drawable.ic_st_download, getString(R.string.st_more_export),
                getString(R.string.st_more_export_detail), false, v -> startExport());
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

    private void openSystemNotificationSettings() {
        Intent i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            // Some phones lack that page: fall back to the app's own system settings page.
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null)));
            } catch (ActivityNotFoundException ignored) {
                banner.showError(R.string.st_more_notifications_unavailable, null);
            }
        }
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
        exportData = null;   // personal data never outlives this screen
        exportWorker.shutdown();
        super.onDestroy();
    }

    // ---- Weekly summary ----

    private void addWeeklySummaryRow(ViewGroup card) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_more_switch_row, card, false);
        ((android.widget.ImageView) row.findViewById(R.id.switchRowIcon)).setImageResource(R.drawable.ic_st_calendar);
        ((TextView) row.findViewById(R.id.switchRowTitle)).setText(R.string.st_more_weekly);
        weeklyDetail = row.findViewById(R.id.switchRowDetail);
        weeklyDetail.setText(R.string.st_more_weekly_detail);
        weeklySwitch = row.findViewById(R.id.switchRowSwitch);
        weeklySwitch.setText("");
        weeklySwitch.setContentDescription(getString(R.string.st_more_weekly));
        card.addView(row);
        loadWeeklySummary();
    }

    private void loadWeeklySummary() {
        api.getWeeklySummarySettings().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isDestroyed() || weeklySwitch == null) {
                    com.example.letstracklanka.utils.ApiErrors.close(response);
                    return;
                }
                Boolean on = null;
                if (response.isSuccessful()) {
                    try (ResponseBody b = response.body()) {
                        com.google.gson.JsonObject d = b != null ? ApiJson.dataObject(b.string()) : null;
                        com.google.gson.JsonElement e = d != null ? ApiJson.get(d, "enabled") : null;
                        if (e != null && e.isJsonPrimitive()) on = e.getAsBoolean();
                    } catch (Exception ignored) {
                        on = null;
                    }
                } else {
                    com.example.letstracklanka.utils.ApiErrors.close(response);
                }
                if (on == null) return;   // leave it disabled: no state to show
                weeklySwitch.setOnCheckedChangeListener(null);
                weeklySwitch.setChecked(on);
                weeklySwitch.setEnabled(true);
                weeklySwitch.setOnCheckedChangeListener((b, checked) -> saveWeeklySummary(checked));
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                // switch stays disabled until the next visit
            }
        });
    }

    private void saveWeeklySummary(boolean on) {
        if (weeklySaving || weeklySwitch == null) return;
        weeklySaving = true;
        weeklySwitch.setEnabled(false);
        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("enabled", on);
        api.updateWeeklySummarySettings(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                weeklySaving = false;
                com.example.letstracklanka.utils.ApiErrors.close(response);
                if (isDestroyed() || weeklySwitch == null) return;
                weeklySwitch.setEnabled(true);
                if (!response.isSuccessful()) undoWeekly(on, false);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                weeklySaving = false;
                if (isDestroyed() || weeklySwitch == null) return;
                weeklySwitch.setEnabled(true);
                undoWeekly(on, true);
            }
        });
    }

    private void undoWeekly(boolean attempted, boolean network) {
        if (weeklySwitch == null) return;
        weeklySwitch.setOnCheckedChangeListener(null);
        weeklySwitch.setChecked(!attempted);
        weeklySwitch.setOnCheckedChangeListener((b, checked) -> saveWeeklySummary(checked));
        banner.showError(network ? R.string.st_error_network : R.string.st_more_weekly_failed, null);
    }

    // ---- Download my data ----

    private void startExport() {
        if (exporting) return;
        ExportFormatSheet.show(this, this::exportAs);
    }

    private void exportAs(ExportFormatSheet.Format format) {
        if (exporting) return;
        if (exportData != null && System.currentTimeMillis() - exportFetchedAt < EXPORT_REUSE_MS) {
            chooseFile(format);
            return;
        }
        exportData = null;
        exporting = true;
        setExportDetail(getString(R.string.st_more_export_preparing));
        api.exportMyData().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                exporting = false;
                if (isDestroyed()) {
                    com.example.letstracklanka.utils.ApiErrors.close(response);
                    return;
                }
                if (!response.isSuccessful()) {
                    setExportDetail(getString(R.string.st_more_export_detail));
                    String why = response.code() == 429
                            ? getString(R.string.st_more_export_limit)
                            : com.example.letstracklanka.utils.ApiErrors.read(response,
                                    getString(R.string.st_more_export_failed)).message;
                    banner.showError(why, null);
                    return;
                }
                com.google.gson.JsonObject data = null;
                try (ResponseBody b = response.body()) {
                    data = b != null ? ApiJson.dataObject(b.string()) : null;
                } catch (Exception ignored) {
                    data = null;
                }
                if (data == null) {
                    setExportDetail(getString(R.string.st_more_export_detail));
                    banner.showError(R.string.st_more_export_failed, null);
                    return;
                }
                exportData = data;
                exportFetchedAt = System.currentTimeMillis();
                chooseFile(format);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                exporting = false;
                if (isDestroyed()) return;
                setExportDetail(getString(R.string.st_more_export_detail));
                banner.showError(R.string.st_error_network, null);
            }
        });
    }

    private void chooseFile(ExportFormatSheet.Format format) {
        pendingFormat = format;
        setExportDetail(getString(R.string.st_more_export_choose));
        String name = "shalotrack-my-data-" + java.time.LocalDate.now(java.time.ZoneId.of("Asia/Colombo")) + "." + format.extension;
        (format == ExportFormatSheet.Format.PDF ? savePdf : saveJson).launch(name);
    }

    /**
     * Writes straight to the file the customer chose; nothing goes to the app's own storage. Runs off the main
     * thread: laying out a few hundred PDF rows is real work.
     */
    private void writeExport(@Nullable android.net.Uri uri, ExportFormatSheet.Format format) {
        final com.google.gson.JsonObject data = exportData;
        if (uri == null) {   // picker cancelled
            pendingFormat = null;
            setExportDetail(getString(R.string.st_more_export_detail));
            return;
        }
        if (data == null) {
            // The screen was recreated while the picker was open (rotation, or Android reclaimed the app). The
            // export is deliberately never put in saved state, so remove the empty file the picker made.
            pendingFormat = null;
            deleteQuietly(uri);
            setExportDetail(getString(R.string.st_more_export_detail));
            banner.showError(R.string.st_more_export_lost, null);
            return;
        }
        pendingFormat = null;
        exporting = true;
        setExportDetail(getString(format == ExportFormatSheet.Format.PDF
                ? R.string.st_more_export_building_pdf : R.string.st_more_export_preparing));
        final android.content.Context app = getApplicationContext();
        exportWorker.execute(() -> {
            boolean ok;
            try (java.io.OutputStream out = app.getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new java.io.IOException("no stream");
                if (format == ExportFormatSheet.Format.PDF) {
                    MyDataPdf.write(app, data, out);
                } else {
                    String json = new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
                            .create().toJson(data);
                    out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                ok = true;
            } catch (Exception e) {
                ok = false;
            }
            final boolean saved = ok;
            runOnUiThread(() -> {
                exporting = false;
                if (isDestroyed()) return;
                if (saved) {
                    setExportDetail(getString(R.string.st_more_export_saved));
                } else {
                    deleteQuietly(uri);   // no half-written file that looks like a real export
                    setExportDetail(getString(R.string.st_more_export_detail));
                    banner.showError(R.string.st_more_export_write_failed, null);
                }
            });
        });
    }

    private void deleteQuietly(android.net.Uri uri) {
        try {
            android.provider.DocumentsContract.deleteDocument(getContentResolver(), uri);
        } catch (Exception ignored) {
            // some providers don't allow it; the message still tells the customer what happened
        }
    }

    private void setExportDetail(String text) {
        if (exportRow == null) return;
        TextView d = exportRow.findViewById(R.id.moreRowDetail);
        d.setText(text);
        d.setVisibility(View.VISIBLE);
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
