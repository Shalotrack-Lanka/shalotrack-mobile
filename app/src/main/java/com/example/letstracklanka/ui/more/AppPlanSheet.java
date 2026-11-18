package com.example.letstracklanka.ui.more;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateSubscriptionRequest;
import com.example.letstracklanka.data.model.RenewalPackage;
import com.example.letstracklanka.data.model.RenewalResponse;
import com.example.letstracklanka.data.model.SubscriptionResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.TimeText;
import com.example.letstracklanka.utils.ApiJson;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * App plan (bottom_sheet_app_plan). Reads two things at once: the customer's plans (to show what is
 * active or waiting for payment) and the server price list (so the prices shown are the prices charged).
 * If the price list is unavailable the guideline prices from strings.xml show, exactly as the server
 * itself falls back. A plan the server no longer sells cannot be picked. The server also refuses a
 * second request while one is active or pending, so the button is off in that case.
 */
public final class AppPlanSheet {

    private enum Plan {
        FREE("Free", R.string.st_plan_free, R.string.subscription_price_free, 0),
        ONE_YEAR("OneYear", R.string.st_plan_1_year, R.string.subscription_price_1_year, 12),
        TWO_YEARS("TwoYears", R.string.st_plan_2_years, R.string.subscription_price_2_years, 24),
        THREE_YEARS("ThreeYears", R.string.st_plan_3_years, R.string.subscription_price_3_years, 36);

        final String api;
        @StringRes final int name;
        @StringRes final int fallbackPrice;
        final int months;

        Plan(String api, int name, int fallbackPrice, int months) {
            this.api = api;
            this.name = name;
            this.fallbackPrice = fallbackPrice;
            this.months = months;
        }

        static Plan of(String api) {
            for (Plan p : values()) if (p.api.equalsIgnoreCase(api)) return p;
            return null;
        }
    }

    private final Activity activity;
    private final BottomSheetDialog sheet;
    private final ApiService api = ApiClient.getClient().create(ApiService.class);

    private final View form, done, progress;
    private final TextView tvCurrent, tvError, tvDoneBody;
    private final ViewGroup options;
    private final MaterialButton btnContinue, btnCancel;
    private final List<View> optionViews = new ArrayList<>();

    @Nullable private Plan selected;
    private boolean blockedByExisting;
    private int pending = 2;
    @Nullable private List<RenewalPackage> priceList;
    private boolean priceListFailed;

    private AppPlanSheet(Activity activity) {
        this.activity = activity;
        sheet = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_app_plan, null);
        sheet.setContentView(view);
        form = view.findViewById(R.id.planForm);
        done = view.findViewById(R.id.planDone);
        progress = view.findViewById(R.id.progressPlans);
        tvCurrent = view.findViewById(R.id.tvPlanCurrent);
        tvError = view.findViewById(R.id.tvPlanError);
        tvDoneBody = view.findViewById(R.id.tvPlanDoneBody);
        options = view.findViewById(R.id.planOptions);
        btnContinue = view.findViewById(R.id.btnPlanContinue);
        btnCancel = view.findViewById(R.id.btnPlanCancel);
        btnCancel.setOnClickListener(v -> sheet.dismiss());
        view.findViewById(R.id.btnPlanDone).setOnClickListener(v -> sheet.dismiss());
        btnContinue.setOnClickListener(v -> submit());
    }

    public static void show(@NonNull Activity activity) {
        AppPlanSheet s = new AppPlanSheet(activity);
        s.sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        s.sheet.getBehavior().setSkipCollapsed(true);
        s.sheet.show();
        s.load();
    }

    private void load() {
        api.getMySubscriptions().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                List<SubscriptionResponse> mine = null;
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) mine = ApiJson.list(body.string(), SubscriptionResponse.class);
                } catch (Exception ignored) {
                    mine = null;
                }
                showCurrent(mine);
                stepDone();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                showCurrent(null);
                stepDone();
            }
        });
        api.getRenewalPackages().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) priceList = ApiJson.list(body.string(), RenewalPackage.class);
                } catch (Exception ignored) {
                    priceList = null;
                }
                priceListFailed = priceList == null;
                stepDone();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                priceListFailed = true;
                stepDone();
            }
        });
    }

    private void stepDone() {
        if (--pending > 0 || !sheet.isShowing()) return;
        progress.setVisibility(View.GONE);
        buildOptions();
    }

    /** The newest active or pending plan, if any, goes above the choices. */
    private void showCurrent(@Nullable List<SubscriptionResponse> mine) {
        if (!sheet.isShowing()) return;
        SubscriptionResponse current = null;
        if (mine != null) for (SubscriptionResponse s : mine) {
            if (s != null && (s.isActive() || s.isPendingPayment())) {
                current = s;
                break;
            }
        }
        if (current == null) {
            tvCurrent.setVisibility(View.GONE);
            blockedByExisting = false;
            return;
        }
        Plan p = Plan.of(current.getPlan());
        String planName = p != null ? activity.getString(p.name) : current.getPlan();
        if (current.isActive()) {
            String until = TimeText.date(current.getEndDate());
            tvCurrent.setText(until.isEmpty()
                    ? activity.getString(R.string.st_plan_current_active, planName)
                    : activity.getString(R.string.st_plan_current_active_until, planName, until));
        } else {
            tvCurrent.setText(activity.getString(R.string.st_plan_current_pending, planName));
        }
        tvCurrent.setVisibility(View.VISIBLE);
        blockedByExisting = true;
    }

    private void buildOptions() {
        options.removeAllViews();
        optionViews.clear();
        LayoutInflater inf = LayoutInflater.from(activity);
        Plan firstAvailable = null;
        for (Plan p : Plan.values()) {
            View row = inf.inflate(R.layout.item_plan_option, options, false);
            TextView name = row.findViewById(R.id.tvPlanName);
            TextView detail = row.findViewById(R.id.tvPlanDetail);
            TextView price = row.findViewById(R.id.tvPlanPrice);
            TextView tag = row.findViewById(R.id.tvPlanTag);
            name.setText(p.name);

            boolean available = true;
            String priceText;
            String detailText;
            if (p == Plan.FREE) {
                priceText = activity.getString(p.fallbackPrice);
                detailText = activity.getString(R.string.st_plan_free_detail);
            } else {
                RenewalPackage pkg = find(p.api);
                if (pkg != null) {
                    priceText = RenewalResponse.formatLkr(pkg.getPriceLkr());
                    detailText = detail(pkg.getMonths() > 0 ? pkg.getMonths() : p.months, pkg.getWarrantyMonths());
                    String positioning = pkg.getPositioning();
                    if (positioning != null && !positioning.trim().isEmpty()) {
                        tag.setText(positioning.trim());
                        tag.setVisibility(View.VISIBLE);
                    }
                } else if (priceListFailed || priceList == null || !hasAnyOrderable()) {
                    // No price list (not published, or not reachable): the server charges the guideline price.
                    priceText = activity.getString(p.fallbackPrice);
                    detailText = detail(p.months, 0);
                } else {
                    available = false;
                    priceText = "";
                    detailText = activity.getString(R.string.st_plan_unavailable);
                }
            }
            price.setText(priceText);
            price.setVisibility(priceText.isEmpty() ? View.GONE : View.VISIBLE);
            detail.setText(detailText);

            row.setEnabled(available && !blockedByExisting);
            row.setAlpha(available ? 1f : 0.45f);
            row.setContentDescription(activity.getString(R.string.st_plan_option_a11y,
                    activity.getString(p.name), priceText, detailText));
            if (available && !blockedByExisting) {
                row.setOnClickListener(v -> select(p));
                if (firstAvailable == null || p == Plan.ONE_YEAR) firstAvailable = p;
            }
            row.setTag(p);
            options.addView(row);
            optionViews.add(row);
        }
        if (blockedByExisting) {
            btnContinue.setEnabled(false);
            btnContinue.setText(R.string.st_plan_request);
        } else if (firstAvailable != null) {
            select(firstAvailable);
        }
    }

    private void select(Plan p) {
        selected = p;
        for (View v : optionViews) v.setSelected(v.getTag() == p);
        tvError.setVisibility(View.GONE);
        btnContinue.setEnabled(true);
        btnContinue.setText(activity.getString(R.string.st_plan_request_named, activity.getString(p.name)));
    }

    private void submit() {
        Plan p = selected;
        if (p == null || blockedByExisting) return;
        btnContinue.setEnabled(false);
        btnCancel.setEnabled(false);
        btnContinue.setText(R.string.st_action_sending);
        tvError.setVisibility(View.GONE);
        api.requestSubscription(new CreateSubscriptionRequest(p.api)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (!sheet.isShowing()) {
                    closeQuietly(response);
                    return;
                }
                if (response.isSuccessful()) {
                    String instructions = null;
                    try (ResponseBody body = response.body()) {
                        SubscriptionResponse created = body != null ? ApiJson.object(body.string(), SubscriptionResponse.class) : null;
                        instructions = created != null ? created.getInstructionsMessage() : null;
                    } catch (Exception ignored) {
                        instructions = null;
                    }
                    form.setVisibility(View.GONE);
                    tvDoneBody.setText(instructions != null && !instructions.trim().isEmpty()
                            ? instructions.trim() : activity.getString(R.string.st_plan_done_body));
                    done.setVisibility(View.VISIBLE);
                    return;
                }
                String message = activity.getString(R.string.st_plan_request_failed);
                if (response.code() >= 400 && response.code() < 500) {
                    try (ResponseBody err = response.errorBody()) {
                        message = ApiJson.message(err != null ? err.string() : null, message);
                    } catch (Exception ignored) {
                        // keep the generic message
                    }
                }
                fail(message);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (sheet.isShowing()) fail(activity.getString(R.string.st_error_network));
            }
        });
    }

    private void fail(String message) {
        btnCancel.setEnabled(true);
        btnContinue.setEnabled(true);
        if (selected != null) btnContinue.setText(activity.getString(R.string.st_plan_request_named, activity.getString(selected.name)));
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private String detail(int months, int warrantyMonths) {
        String m = activity.getResources().getQuantityString(R.plurals.st_plan_months, months, months);
        return warrantyMonths > 0
                ? activity.getString(R.string.st_plan_detail_warranty, m,
                    activity.getResources().getQuantityString(R.plurals.st_plan_months, warrantyMonths, warrantyMonths))
                : m;
    }

    @Nullable
    private RenewalPackage find(String duration) {
        if (priceList == null) return null;
        for (RenewalPackage p : priceList) if (p != null && p.isOrderable() && duration.equals(p.getDuration())) return p;
        return null;
    }

    private boolean hasAnyOrderable() {
        if (priceList == null) return false;
        for (RenewalPackage p : priceList) if (p != null && p.isOrderable()) return true;
        return false;
    }

    private static void closeQuietly(Response<ResponseBody> r) {
        if (r.body() != null) r.body().close();
        if (r.errorBody() != null) r.errorBody().close();
    }
}
