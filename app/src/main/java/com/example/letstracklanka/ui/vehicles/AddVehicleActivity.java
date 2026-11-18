package com.example.letstracklanka.ui.vehicles;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateDeviceAssignmentRequest;
import com.example.letstracklanka.data.model.CreateVehicleRequest;
import com.example.letstracklanka.data.model.UpdateVehicleRequest;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.EdgeToEdge;
import com.example.letstracklanka.utils.ApiErrors;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.ClickGuard;
import com.example.letstracklanka.utils.InputValidator;
import com.example.letstracklanka.utils.SecureScreen;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;

import java.util.Calendar;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Add a vehicle (optionally linking its tracker in the same step) or edit one (EXTRA_EDIT_VEHICLE_ID).
 *
 * Tracker linking only ever uses a device ID the server confirmed for the IMEI typed here; changing the IMEI
 * forgets that confirmation. If the vehicle is created but the link fails, the screen says so and stays open
 * with "Done": the vehicle exists, so saving again would make a duplicate, and the tracker can be linked from
 * the vehicle page. No toasts: errors sit on the field or in the box at the top.
 */
public class AddVehicleActivity extends AppCompatActivity {

    public static final String EXTRA_CUSTOMER_ID = "extra_customer_id";

    // Edit mode: present means "edit this vehicle"; the other extras pre-fill the form (the caller already has them).
    public static final String EXTRA_EDIT_VEHICLE_ID = "extra_edit_vehicle_id";
    public static final String EXTRA_VEHICLE_NUMBER = "extra_vehicle_number";
    public static final String EXTRA_MAKE = "extra_make";
    public static final String EXTRA_MODEL = "extra_model";
    public static final String EXTRA_YEAR = "extra_year";
    public static final String EXTRA_COLOR = "extra_color";
    public static final String EXTRA_VEHICLE_TYPE = "extra_vehicle_type";
    public static final String EXTRA_FUEL_TYPE = "extra_fuel_type";
    public static final String EXTRA_CHASSIS_NUMBER = "extra_chassis_number";
    public static final String EXTRA_ENGINE_NUMBER = "extra_engine_number";

    // Same lists as before (VehicleListAdapter and VehicleTrailRenderer key their icons on these exact values).
    private static final String[] VEHICLE_TYPES = {"Car", "SUV", "Van", "Truck", "Bike", "Tuk"};
    private static final String[] FUEL_TYPES = {"Petrol", "Diesel", "Electric", "Hybrid"};

    private ApiService api;
    @Nullable private String customerId;
    @Nullable private String editingVehicleId;

    private TextView tvError;
    private TextInputLayout tilVehicleNumber, tilMake, tilModel, tilYear, tilColor, tilChassis, tilEngine, tilImei;
    private TextInputEditText etVehicleNumber, etMake, etModel, etYear, etColor, etChassis, etEngine, etImei;
    private MaterialAutoCompleteTextView spinnerVehicleType, spinnerFuelType;
    private MaterialButton btnToggleAdvanced, btnLookup, btnSave;
    private View layoutAdvanced, layoutDeviceFound;
    private TextView tvDeviceFoundDetails;
    private ProgressBar progress;

    /** Set only by a successful lookup of exactly the IMEI now in the field. */
    @Nullable private String lookedUpDeviceId;
    /** Set once the vehicle exists but its tracker link failed: the button then just closes the screen. */
    @Nullable private String createdVehicleId;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_vehicle);
        SecureScreen.guardTouches(this);
        EdgeToEdge.padTop(findViewById(R.id.addVehicleTopBar));
        EdgeToEdge.padBottom(findViewById(R.id.addVehicleContent));

        api = ApiClient.getClient().create(ApiService.class);
        customerId = getIntent().getStringExtra(EXTRA_CUSTOMER_ID);
        editingVehicleId = getIntent().getStringExtra(EXTRA_EDIT_VEHICLE_ID);

        bindViews();

        if (editingVehicleId != null) {
            enterEditMode();
        } else if (customerId == null || customerId.isEmpty()) {
            // Callers always pass the signed-in customer; without it nothing can be saved.
            showError(getString(R.string.st_addv_no_customer));
            btnSave.setEnabled(false);
        }
    }

    private void bindViews() {
        findViewById(R.id.btnBackAddVehicle).setOnClickListener(v -> finish());
        tvError = findViewById(R.id.tvAddVehicleError);

        tilVehicleNumber = findViewById(R.id.tilVehicleNumber);
        tilMake = findViewById(R.id.tilMake);
        tilModel = findViewById(R.id.tilModel);
        tilYear = findViewById(R.id.tilYear);
        tilColor = findViewById(R.id.tilColor);
        tilChassis = findViewById(R.id.tilChassisNumber);
        tilEngine = findViewById(R.id.tilEngineNumber);
        tilImei = findViewById(R.id.tilImei);

        etVehicleNumber = findViewById(R.id.etVehicleNumber);
        etMake = findViewById(R.id.etMake);
        etModel = findViewById(R.id.etModel);
        etYear = findViewById(R.id.etYear);
        etColor = findViewById(R.id.etColor);
        etChassis = findViewById(R.id.etChassisNumber);
        etEngine = findViewById(R.id.etEngineNumber);
        etImei = findViewById(R.id.etImei);

        clearErrorOnEdit(etVehicleNumber, tilVehicleNumber);
        clearErrorOnEdit(etMake, tilMake);
        clearErrorOnEdit(etModel, tilModel);
        clearErrorOnEdit(etYear, tilYear);
        clearErrorOnEdit(etColor, tilColor);
        clearErrorOnEdit(etChassis, tilChassis);
        clearErrorOnEdit(etEngine, tilEngine);

        spinnerVehicleType = findViewById(R.id.spinnerVehicleType);
        spinnerFuelType = findViewById(R.id.spinnerFuelType);
        spinnerVehicleType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, VEHICLE_TYPES));
        spinnerFuelType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, FUEL_TYPES));

        btnToggleAdvanced = findViewById(R.id.btnToggleAdvanced);
        layoutAdvanced = findViewById(R.id.layoutAdvancedFields);
        btnToggleAdvanced.setOnClickListener(v -> setAdvancedVisible(layoutAdvanced.getVisibility() != View.VISIBLE));

        btnLookup = findViewById(R.id.btnLookupDevice);
        layoutDeviceFound = findViewById(R.id.layoutDeviceFound);
        tvDeviceFoundDetails = findViewById(R.id.tvDeviceFoundDetails);
        btnLookup.setOnClickListener(ClickGuard.wrap(v -> lookupDevice()));
        etImei.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            lookupDevice();
            return true;
        });
        // A different IMEI cancels the earlier confirmation, so a stale device ID can never be linked.
        etImei.addTextChangedListener(new SimpleWatcher(() -> {
            lookedUpDeviceId = null;
            layoutDeviceFound.setVisibility(View.GONE);
            tilImei.setError(null);
        }));

        btnSave = findViewById(R.id.btnSaveVehicle);
        progress = findViewById(R.id.progressAddVehicle);
        btnSave.setOnClickListener(ClickGuard.wrap(v -> onSave()));
    }

    private void enterEditMode() {
        ((TextView) findViewById(R.id.tvAddVehicleTitle)).setText(R.string.st_addv_title_edit);
        btnSave.setText(R.string.st_addv_save);
        findViewById(R.id.layoutGpsDeviceSection).setVisibility(View.GONE);

        Intent in = getIntent();
        setText(etVehicleNumber, in.getStringExtra(EXTRA_VEHICLE_NUMBER));
        setText(etMake, in.getStringExtra(EXTRA_MAKE));
        setText(etModel, in.getStringExtra(EXTRA_MODEL));
        int year = in.getIntExtra(EXTRA_YEAR, 0);
        if (year > 0) setText(etYear, String.valueOf(year));
        setText(etColor, in.getStringExtra(EXTRA_COLOR));
        setText(etChassis, in.getStringExtra(EXTRA_CHASSIS_NUMBER));
        setText(etEngine, in.getStringExtra(EXTRA_ENGINE_NUMBER));
        String type = in.getStringExtra(EXTRA_VEHICLE_TYPE);
        if (type != null) spinnerVehicleType.setText(type, false);
        String fuel = in.getStringExtra(EXTRA_FUEL_TYPE);
        if (fuel != null) spinnerFuelType.setText(fuel, false);

        // Numbers already on file: show them instead of hiding them behind the toggle.
        if (!isBlank(in.getStringExtra(EXTRA_CHASSIS_NUMBER)) || !isBlank(in.getStringExtra(EXTRA_ENGINE_NUMBER))) {
            setAdvancedVisible(true);
        }
    }

    private void setAdvancedVisible(boolean show) {
        layoutAdvanced.setVisibility(show ? View.VISIBLE : View.GONE);
        btnToggleAdvanced.setText(show ? R.string.st_addv_hide_numbers : R.string.st_addv_show_numbers);
        btnToggleAdvanced.setIconResource(show ? R.drawable.ic_st_close : R.drawable.ic_st_plus);
    }

    // ---- Tracker lookup ----

    private void lookupDevice() {
        if (busy) return;
        hideError();
        final String imei = digits(etImei);
        String bad = InputValidator.imei(imei);
        if (bad != null) {
            tilImei.setError(getString(R.string.st_addv_imei_invalid));
            return;
        }
        tilImei.setError(null);
        btnLookup.setEnabled(false);
        btnLookup.setText(R.string.st_addv_checking);
        api.lookupDeviceByImei(imei).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isDestroyed()) {
                    ApiErrors.close(response);
                    return;
                }
                restoreLookupButton();
                if (!imei.equals(digits(etImei))) {   // the field changed while we waited
                    ApiErrors.close(response);
                    return;
                }
                if (!response.isSuccessful()) {
                    tilImei.setError(lookupFailure(ApiErrors.read(response, getString(R.string.st_addv_lookup_failed))));
                    return;
                }
                JsonObject d = null;
                try (ResponseBody b = response.body()) {
                    d = b != null ? ApiJson.dataObject(b.string()) : null;
                } catch (Exception ignored) {
                    d = null;
                }
                String deviceId = d != null ? ApiJson.string(d, "deviceId") : null;
                if (isBlank(deviceId)) {
                    tilImei.setError(getString(R.string.st_addv_lookup_failed));
                    return;
                }
                lookedUpDeviceId = deviceId;
                tvDeviceFoundDetails.setText(getString(R.string.st_addv_found_detail, imei));
                layoutDeviceFound.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isDestroyed()) return;
                restoreLookupButton();
                tilImei.setError(getString(R.string.st_error_network));
            }
        });
    }

    /** The server's reply says why; turn each case into what the customer can do about it. */
    private String lookupFailure(ApiErrors.Failure f) {
        switch (f.code) {
            case 404: return getString(R.string.st_addv_imei_not_found);
            case 409: return getString(R.string.st_addv_imei_linked);
            case 429: return getString(R.string.st_addv_imei_too_many);
            default: return f.message;
        }
    }

    private void restoreLookupButton() {
        btnLookup.setEnabled(true);
        btnLookup.setText(R.string.st_addv_check);
    }

    // ---- Save ----

    private void onSave() {
        if (busy) return;
        if (createdVehicleId != null) {   // vehicle already added; only the tracker link failed
            setResult(RESULT_OK);
            finish();
            return;
        }
        hideError();

        String plate = text(etVehicleNumber).toUpperCase(Locale.ROOT);
        String make = text(etMake);
        String model = text(etModel);
        String yearText = text(etYear);
        String color = text(etColor);
        String chassis = text(etChassis).toUpperCase(Locale.ROOT);
        String engine = text(etEngine).toUpperCase(Locale.ROOT);

        boolean ok = true;
        ok &= check(tilVehicleNumber, plate.isEmpty() ? getString(R.string.st_addv_required) : InputValidator.plate(plate));
        ok &= check(tilMake, make.isEmpty() ? getString(R.string.st_addv_required) : InputValidator.label(make, 1, 40, "the make"));
        ok &= check(tilModel, model.isEmpty() ? getString(R.string.st_addv_required) : InputValidator.label(model, 1, 40, "the model"));
        int year = parseYear(yearText);
        int maxYear = Calendar.getInstance().get(Calendar.YEAR) + 1;
        ok &= check(tilYear, yearText.isEmpty() ? getString(R.string.st_addv_required)
                : year < 1950 || year > maxYear ? getString(R.string.st_addv_year_range, maxYear) : null);
        ok &= check(tilColor, InputValidator.label(color, 0, 30, "the colour"));
        String chassisError = InputValidator.label(chassis, 0, 40, "the chassis number");
        String engineError = InputValidator.label(engine, 0, 40, "the engine number");
        ok &= check(tilChassis, chassisError);
        ok &= check(tilEngine, engineError);
        if (chassisError != null || engineError != null) setAdvancedVisible(true);

        // A typed IMEI that was never checked is not silently dropped: the customer meant to link it.
        if (editingVehicleId == null && !digits(etImei).isEmpty() && lookedUpDeviceId == null) {
            tilImei.setError(getString(R.string.st_addv_imei_check_first));
            ok = false;
        }
        if (!ok) return;

        String type = emptyToNull(spinnerVehicleType.getText() != null ? spinnerVehicleType.getText().toString().trim() : "");
        String fuel = emptyToNull(spinnerFuelType.getText() != null ? spinnerFuelType.getText().toString().trim() : "");
        if (editingVehicleId != null) {
            update(new UpdateVehicleRequest(plate, emptyToNull(chassis), emptyToNull(engine), make, model, year,
                    emptyToNull(color), type, fuel));
        } else {
            create(new CreateVehicleRequest(customerId, plate, emptyToNull(chassis), emptyToNull(engine), make, model,
                    year, emptyToNull(color), type, fuel));
        }
    }

    private void create(CreateVehicleRequest request) {
        setBusy(true);
        api.createVehicle(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isDestroyed()) {
                    ApiErrors.close(response);
                    return;
                }
                if (!response.isSuccessful()) {
                    setBusy(false);
                    showError(ApiErrors.read(response, getString(R.string.st_addv_add_failed)).message);
                    return;
                }
                VehicleResponse created;
                try (ResponseBody b = response.body()) {
                    created = b != null ? VehicleResponse.from(b.string()) : null;
                } catch (Exception e) {
                    created = null;
                }
                if (lookedUpDeviceId == null) {
                    setBusy(false);
                    setResult(RESULT_OK);
                    finish();
                } else if (created == null) {
                    // Saved, but the reply had no ID to link with: say so rather than pretend.
                    vehicleAddedButNotLinked(null, getString(R.string.st_addv_link_later));
                } else {
                    link(created.getVehicleId());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isDestroyed()) return;
                setBusy(false);
                // The request may or may not have reached the server: the list will show if it did.
                showError(getString(R.string.st_addv_add_network));
            }
        });
    }

    private void link(final String vehicleId) {
        api.assignDevice(new CreateDeviceAssignmentRequest(vehicleId, lookedUpDeviceId)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isDestroyed()) {
                    ApiErrors.close(response);
                    return;
                }
                if (response.isSuccessful()) {
                    ApiErrors.close(response);
                    setBusy(false);
                    setResult(RESULT_OK);
                    finish();
                    return;
                }
                ApiErrors.Failure f = ApiErrors.read(response, getString(R.string.st_addv_link_later));
                vehicleAddedButNotLinked(vehicleId, f.code == 409 ? getString(R.string.st_addv_imei_linked) : f.message);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isDestroyed()) return;
                vehicleAddedButNotLinked(vehicleId, getString(R.string.st_error_network));
            }
        });
    }

    private void vehicleAddedButNotLinked(@Nullable String vehicleId, String reason) {
        setBusy(false);
        createdVehicleId = vehicleId != null ? vehicleId : "";
        showError(getString(R.string.st_addv_added_not_linked, reason));
        btnSave.setText(R.string.st_action_done);
        btnLookup.setEnabled(false);
        etImei.setEnabled(false);
        setResult(RESULT_OK);
    }

    private void update(UpdateVehicleRequest request) {
        setBusy(true);
        api.updateVehicle(editingVehicleId, request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isDestroyed()) {
                    ApiErrors.close(response);
                    return;
                }
                setBusy(false);
                if (response.isSuccessful()) {
                    ApiErrors.close(response);
                    setResult(RESULT_OK);
                    finish();
                    return;
                }
                showError(ApiErrors.read(response, getString(R.string.st_addv_save_failed)).message);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isDestroyed()) return;
                setBusy(false);
                showError(getString(R.string.st_addv_save_network));
            }
        });
    }

    // ---- Helpers ----

    private void setBusy(boolean b) {
        busy = b;
        btnSave.setEnabled(!b);
        btnLookup.setEnabled(!b);
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
    }

    private boolean check(TextInputLayout til, @Nullable String error) {
        til.setError(error);
        return error == null;
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
        View scroll = findViewById(R.id.addVehicleScroll);
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }

    private static void clearErrorOnEdit(TextInputEditText et, TextInputLayout til) {
        et.addTextChangedListener(new SimpleWatcher(() -> til.setError(null)));
    }

    private static int parseYear(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void setText(TextInputEditText field, @Nullable String value) {
        if (value != null) field.setText(value);
    }

    private static String text(TextInputEditText field) {
        return field.getText() != null ? InputValidator.cleanLine(field.getText().toString()) : "";
    }

    private static String digits(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().replaceAll("\\D", "") : "";
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    @Nullable
    private static String emptyToNull(@Nullable String s) {
        return isBlank(s) ? null : s;
    }

    /** Runs one action on every text change. */
    private static final class SimpleWatcher implements TextWatcher {
        private final Runnable onChange;

        SimpleWatcher(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { onChange.run(); }
        @Override public void afterTextChanged(Editable s) {}
    }
}
