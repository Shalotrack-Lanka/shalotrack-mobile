package com.example.letstracklanka.ui.renewals;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateRenewalRequest;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.data.model.RenewalPackage;
import com.example.letstracklanka.data.model.RenewalResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.VehicleField;
import com.example.letstracklanka.utils.ApiErrors;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.InputValidator;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * "Renew a vehicle" (POST api/Renewals). The price list is read from the server each time, so the phone
 * never hard-codes a price, and the request never carries an amount: the server sets it. Without a list
 * on the server yet, the package names are offered and the server decides the amount.
 */
final class NewRenewalSheet {

    interface Listener {
        void onCreated(@Nullable RenewalResponse created);
    }

    /** One length the customer can choose. */
    private static final class Option {
        final String duration;
        final String label;
        final String priceLine;

        Option(String duration, String label, String priceLine) {
            this.duration = duration;
            this.label = label;
            this.priceLine = priceLine;
        }
    }

    private final Activity activity;
    private final Listener listener;
    private final BottomSheetDialog sheet;
    private final VehicleField vehicle;
    private final ViewGroup chips;
    private final ProgressBar packagesProgress;
    private final TextView price;
    private final TextInputLayout tilReference;
    private final TextInputEditText etReference;
    private final InlineBanner banner;
    private final MaterialButton btnContinue;
    private final MaterialButton btnCancel;
    private final List<Option> options = new ArrayList<>();
    private final List<TextView> chipViews = new ArrayList<>();
    private int chosen = -1;

    @SuppressLint("InflateParams") // dialog content has no parent to measure against
    private NewRenewalSheet(@NonNull Activity activity, @NonNull Listener listener) {
        this.activity = activity;
        this.listener = listener;
        sheet = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_new_renewal, null);
        sheet.setContentView(view);

        vehicle = new VehicleField(activity, view.findViewById(R.id.stVehicleField), true);
        chips = view.findViewById(R.id.chipsDuration);
        packagesProgress = view.findViewById(R.id.progressRenewalPackages);
        price = view.findViewById(R.id.tvRenewalPrice);
        tilReference = view.findViewById(R.id.tilRenewalReference);
        etReference = view.findViewById(R.id.etRenewalReference);
        banner = new InlineBanner(view.findViewById(R.id.inlineBanner));
        btnContinue = view.findViewById(R.id.btnRenewalContinue);
        btnCancel = view.findViewById(R.id.btnRenewalSheetCancel);

        btnCancel.setOnClickListener(v -> sheet.dismiss());
        btnContinue.setOnClickListener(v -> create());
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
    }

    static void show(@NonNull Activity activity, @NonNull Listener listener) {
        NewRenewalSheet s = new NewRenewalSheet(activity, listener);
        s.sheet.show();
        s.vehicle.load(null);
        s.loadPackages();
    }

    private void loadPackages() {
        packagesProgress.setVisibility(View.VISIBLE);
        price.setText(R.string.st_renewal_loading_prices);
        ApiClient.getClient().create(ApiService.class).getRenewalPackages().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!sheet.isShowing()) {
                    ApiErrors.close(response);
                    return;
                }
                packagesProgress.setVisibility(View.GONE);
                List<RenewalPackage> offered = null;
                if (response.isSuccessful()) {
                    try (ResponseBody body = response.body()) {
                        if (body != null) offered = ApiJson.list(body.string(), RenewalPackage.class);
                        if (offered == null) offered = new ArrayList<>();
                    } catch (Exception ignored) {
                        offered = null;
                    }
                } else {
                    ApiErrors.close(response);
                }
                if (offered == null) {
                    price.setText("");
                    banner.showError(activity.getString(R.string.st_renewal_prices_failed), () -> loadPackages());
                    return;
                }
                showOptions(offered);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (!sheet.isShowing()) return;
                packagesProgress.setVisibility(View.GONE);
                price.setText("");
                banner.showError(activity.getString(R.string.st_error_network), () -> loadPackages());
            }
        });
    }

    private void showOptions(List<RenewalPackage> offered) {
        options.clear();
        int preferred = 0;
        List<RenewalPackage> orderable = new ArrayList<>();
        for (RenewalPackage p : offered) if (p != null && p.isOrderable()) orderable.add(p);
        if (orderable.isEmpty()) {
            // No list on the server yet: names only; the server confirms the amount.
            String unknown = activity.getString(R.string.st_renewal_price_unknown);
            for (String name : RenewalResponse.DURATION_NAMES) {
                options.add(new Option(name, RenewalAdapter.duration(activity, name), unknown));
                if ("OneYear".equals(name)) preferred = options.size() - 1;
            }
        } else {
            for (RenewalPackage p : orderable) {
                String length = RenewalAdapter.duration(activity, p.getDuration());
                if (length.isEmpty()) length = p.getLabel();
                String line = p.getPriceLkr() != null
                        ? activity.getString(R.string.st_renewal_runs, RenewalResponse.formatLkr(p.getPriceLkr()), length)
                        : activity.getString(R.string.st_renewal_price_unknown);
                if (p.getWarrantyMonths() > 0) {
                    line = activity.getString(R.string.st_renewal_warranty, line, p.getWarrantyMonths());
                }
                options.add(new Option(p.getDuration(), length, line));
                if ("OneYear".equals(p.getDuration())) preferred = options.size() - 1;
            }
        }

        chips.removeAllViews();
        chipViews.clear();
        LayoutInflater inflater = LayoutInflater.from(activity);
        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            TextView chip = (TextView) inflater.inflate(R.layout.item_st_filter_chip, chips, false);
            chip.setText(options.get(i).label);
            chip.setOnClickListener(v -> choose(index));
            chips.addView(chip);
            chipViews.add(chip);
        }
        choose(preferred);
        btnContinue.setEnabled(true);
    }

    private void choose(int index) {
        chosen = index;
        for (int i = 0; i < chipViews.size(); i++) {
            TextView chip = chipViews.get(i);
            boolean on = i == index;
            chip.setSelected(on);
            chip.setContentDescription(chip.getText() + (on ? ", " + activity.getString(R.string.st_a11y_selected) : ""));
        }
        price.setText(options.get(index).priceLine);
    }

    private void create() {
        tilReference.setError(null);
        banner.hide();
        DashboardVehicle v = vehicle.get();
        if (v == null) {
            banner.showError(activity.getString(vehicle.hasNoVehicles()
                    ? R.string.st_field_vehicle_none_owned : R.string.st_field_vehicle_pick), null);
            return;
        }
        if (chosen < 0 || chosen >= options.size()) return;
        String ref = InputValidator.cleanLine(etReference.getText() == null ? "" : etReference.getText().toString());
        if (!ref.isEmpty() && InputValidator.label(ref, 1, 40, "the payment reference") != null) {
            tilReference.setError(activity.getString(R.string.st_renewal_reference_invalid));
            etReference.requestFocus();
            return;
        }

        setBusy(true);
        ApiClient.getClient().create(ApiService.class)
                .createRenewal(new CreateRenewalRequest(v.getVehicleId(), options.get(chosen).duration, ref.isEmpty() ? null : ref))
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        if (!sheet.isShowing()) {
                            ApiErrors.close(response);
                            return;
                        }
                        setBusy(false);
                        if (response.isSuccessful()) {
                            RenewalResponse created = null;
                            try (ResponseBody body = response.body()) {
                                if (body != null) created = ApiJson.object(body.string(), RenewalResponse.class);
                            } catch (Exception ignored) {
                                created = null;
                            }
                            sheet.dismiss();
                            listener.onCreated(created);
                            return;
                        }
                        if (response.code() == 401) {
                            ApiErrors.close(response);
                            banner.showError(activity.getString(R.string.st_error_session_expired), null);
                            return;
                        }
                        // The server's own words, e.g. "You already have an open renewal for this vehicle".
                        banner.showError(ApiErrors.read(response, activity.getString(R.string.st_renewal_create_failed)).message, null);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        if (!sheet.isShowing()) return;
                        setBusy(false);
                        banner.showError(activity.getString(R.string.st_error_network), null);
                    }
                });
    }

    private void setBusy(boolean busy) {
        btnContinue.setEnabled(!busy && !options.isEmpty());
        btnCancel.setEnabled(!busy);
        vehicle.setEnabled(!busy);
        for (TextView chip : chipViews) chip.setEnabled(!busy);
        sheet.setCancelable(!busy);
        btnContinue.setText(busy ? R.string.st_renewal_creating : R.string.st_renewal_continue);
    }
}
