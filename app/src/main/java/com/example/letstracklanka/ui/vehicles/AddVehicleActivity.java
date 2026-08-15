package com.example.letstracklanka.ui.vehicles;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateDeviceAssignmentRequest;
import com.example.letstracklanka.data.model.CreateVehicleRequest;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * New screen -- nothing like this existed before (fabAdd in VehiclesActivity
 * was a real, visible button with no click listener at all, confirmed via a
 * systematic dead-end check). Collects vehicle details and optionally links
 * a GPS device in the same flow, using the ApiService endpoints
 * (createVehicle/lookupDeviceByImei/assignDevice) that already existed but
 * had no Android screen calling any of them.
 */
public class AddVehicleActivity extends AppCompatActivity {

    public static final String EXTRA_CUSTOMER_ID = "extra_customer_id";

    private ApiService mainApiService;
    private String customerId;

    private View errorBanner;
    private TextView tvErrorBannerMessage;

    private TextInputEditText etVehicleNumber, etMake, etModel, etYear, etColor;
    private TextInputEditText etChassisNumber, etEngineNumber;
    private AutoCompleteTextView spinnerVehicleType, spinnerFuelType;
    private View btnToggleAdvanced, layoutAdvancedFields;

    private TextInputEditText etImei;
    private View btnLookupDevice, layoutDeviceFound;
    private TextView tvDeviceFoundDetails;
    private View btnSaveVehicle;
    private ProgressBar progressAddVehicle;

    // Set only after a successful lookup -- a typed IMEI alone is never
    // trusted; linking only ever uses a device ID the server itself
    // confirmed exists.
    private String lookedUpDeviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_vehicle);

        mainApiService = ApiClient.getClient().create(ApiService.class);
        customerId = getIntent().getStringExtra(EXTRA_CUSTOMER_ID);

        initViews();

        if (customerId == null || customerId.isEmpty()) {
            showError("No customer profile found. Please go back and try again.");
            if (btnSaveVehicle != null) btnSaveVehicle.setEnabled(false);
        }
    }

    private void initViews() {
        View btnBack = findViewById(R.id.btnBackAddVehicle);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);

        etVehicleNumber = findViewById(R.id.etVehicleNumber);
        etMake = findViewById(R.id.etMake);
        etModel = findViewById(R.id.etModel);
        etYear = findViewById(R.id.etYear);
        etColor = findViewById(R.id.etColor);
        etChassisNumber = findViewById(R.id.etChassisNumber);
        etEngineNumber = findViewById(R.id.etEngineNumber);
        spinnerVehicleType = findViewById(R.id.spinnerVehicleType);
        spinnerFuelType = findViewById(R.id.spinnerFuelType);

        // Categories matching the ones already named in the client's own
        // requirement document (Cars, Bikes, Vans, Trucks, Fleet vehicles),
        // plus SUV since it's the vehicle type on real existing data
        // (JAPAN Mazda, Honda Vezel).
        String[] vehicleTypes = {"Car", "SUV", "Van", "Truck", "Bike"};
        spinnerVehicleType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, vehicleTypes));

        String[] fuelTypes = {"Petrol", "Diesel", "Electric", "Hybrid"};
        spinnerFuelType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, fuelTypes));

        btnToggleAdvanced = findViewById(R.id.btnToggleAdvanced);
        layoutAdvancedFields = findViewById(R.id.layoutAdvancedFields);
        if (btnToggleAdvanced != null) {
            btnToggleAdvanced.setOnClickListener(v -> {
                boolean showing = layoutAdvancedFields.getVisibility() == View.VISIBLE;
                layoutAdvancedFields.setVisibility(showing ? View.GONE : View.VISIBLE);
                ((TextView) btnToggleAdvanced).setText(showing
                        ? "+ Add chassis / engine number (optional)"
                        : "\u2212 Hide chassis / engine number");
            });
        }

        etImei = findViewById(R.id.etImei);
        btnLookupDevice = findViewById(R.id.btnLookupDevice);
        layoutDeviceFound = findViewById(R.id.layoutDeviceFound);
        tvDeviceFoundDetails = findViewById(R.id.tvDeviceFoundDetails);
        if (btnLookupDevice != null) btnLookupDevice.setOnClickListener(v -> lookupDevice());

        // Typing a different IMEI after a successful lookup invalidates
        // that lookup -- otherwise a stale, previously-confirmed device ID
        // could get linked to a vehicle even after the IMEI field was
        // edited to something else.
        if (etImei != null) {
            etImei.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    lookedUpDeviceId = null;
                    if (layoutDeviceFound != null) layoutDeviceFound.setVisibility(View.GONE);
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        btnSaveVehicle = findViewById(R.id.btnSaveVehicle);
        progressAddVehicle = findViewById(R.id.progressAddVehicle);
        if (btnSaveVehicle != null) btnSaveVehicle.setOnClickListener(v -> attemptSaveVehicle());
    }

    private void lookupDevice() {
        String imei = textOf(etImei);
        if (imei.isEmpty()) {
            Toast.makeText(this, "Enter an IMEI first.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLookupDevice.setEnabled(false);
        mainApiService.lookupDeviceByImei(imei).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                btnLookupDevice.setEnabled(true);
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        JsonObject device = extractObjectRaw(body.string());
                        String deviceId = device != null && device.has("deviceId") && !device.get("deviceId").isJsonNull()
                                ? device.get("deviceId").getAsString() : null;
                        if (deviceId == null) {
                            Toast.makeText(AddVehicleActivity.this, "Device not found for that IMEI.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        lookedUpDeviceId = deviceId;
                        if (layoutDeviceFound != null) layoutDeviceFound.setVisibility(View.VISIBLE);
                        if (tvDeviceFoundDetails != null) {
                            String status = device.has("status") && !device.get("status").isJsonNull()
                                    ? device.get("status").getAsString() : "";
                            tvDeviceFoundDetails.setText("IMEI " + imei + (status.isEmpty() ? "" : " \u2014 " + status));
                        }
                    } else {
                        Toast.makeText(AddVehicleActivity.this, "Device not found for that IMEI.", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Log.e("AddVehicleActivity", "lookupDevice parse error", e);
                    Toast.makeText(AddVehicleActivity.this, "Something went wrong looking up that device.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                btnLookupDevice.setEnabled(true);
                Log.e("AddVehicleActivity", "lookupDevice network error", t);
                Toast.makeText(AddVehicleActivity.this, "Network error \u2014 check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void attemptSaveVehicle() {
        hideError();

        String vehicleNumber = textOf(etVehicleNumber);
        String make = textOf(etMake);
        String model = textOf(etModel);
        String yearText = textOf(etYear);

        if (vehicleNumber.isEmpty() || make.isEmpty() || model.isEmpty() || yearText.isEmpty()) {
            showError("Vehicle Number, Make, Model, and Year are required.");
            return;
        }

        int year;
        try {
            year = Integer.parseInt(yearText);
        } catch (NumberFormatException e) {
            showError("Year must be a valid number.");
            return;
        }

        String color = emptyToNull(textOf(etColor));
        String vehicleType = emptyToNull(spinnerVehicleType.getText().toString().trim());
        String fuelType = emptyToNull(spinnerFuelType.getText().toString().trim());
        String chassisNumber = emptyToNull(textOf(etChassisNumber));
        String engineNumber = emptyToNull(textOf(etEngineNumber));

        CreateVehicleRequest request = new CreateVehicleRequest(
                customerId, vehicleNumber, chassisNumber, engineNumber,
                make, model, year, color, vehicleType, fuelType);

        setSaving(true);
        mainApiService.createVehicle(request).enqueue(new Callback<VehicleResponse>() {
            @Override
            public void onResponse(@NonNull Call<VehicleResponse> call, @NonNull Response<VehicleResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String newVehicleId = response.body().getVehicleId();
                    if (lookedUpDeviceId != null && newVehicleId != null) {
                        linkDeviceThenFinish(newVehicleId);
                    } else {
                        setSaving(false);
                        Toast.makeText(AddVehicleActivity.this, "Vehicle added.", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }
                } else {
                    setSaving(false);
                    Log.w("AddVehicleActivity", "createVehicle failed, code " + response.code());
                    showError("Couldn't add vehicle (code " + response.code() + "). Please try again.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<VehicleResponse> call, @NonNull Throwable t) {
                setSaving(false);
                Log.e("AddVehicleActivity", "createVehicle network error", t);
                showError("Network error \u2014 vehicle could not be added. Try again.");
            }
        });
    }

    private void linkDeviceThenFinish(String newVehicleId) {
        CreateDeviceAssignmentRequest assignRequest = new CreateDeviceAssignmentRequest(newVehicleId, lookedUpDeviceId);
        mainApiService.assignDevice(assignRequest).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                setSaving(false);
                if (response.isSuccessful()) {
                    Toast.makeText(AddVehicleActivity.this, "Vehicle added and device linked.", Toast.LENGTH_SHORT).show();
                } else {
                    // The vehicle itself was created successfully -- a
                    // failed device link is a real, separate problem to
                    // surface, not a reason to hide that the vehicle save
                    // actually succeeded.
                    Log.w("AddVehicleActivity", "assignDevice failed after vehicle creation, code " + response.code());
                    Toast.makeText(AddVehicleActivity.this,
                            "Vehicle added, but the device couldn't be linked. You can link it later from the vehicle's settings.",
                            Toast.LENGTH_LONG).show();
                }
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                setSaving(false);
                Log.e("AddVehicleActivity", "assignDevice network error", t);
                Toast.makeText(AddVehicleActivity.this,
                        "Vehicle added, but the device couldn't be linked (network error). You can link it later.",
                        Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    private void setSaving(boolean saving) {
        if (btnSaveVehicle != null) btnSaveVehicle.setEnabled(!saving);
        if (progressAddVehicle != null) progressAddVehicle.setVisibility(saving ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        if (errorBanner == null) return;
        errorBanner.setVisibility(View.VISIBLE);
        if (tvErrorBannerMessage != null) tvErrorBannerMessage.setText(message);
    }

    private void hideError() {
        if (errorBanner != null) errorBanner.setVisibility(View.GONE);
    }

    private String textOf(TextInputEditText field) {
        return field != null && field.getText() != null ? field.getText().toString().trim() : "";
    }

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private JsonObject extractObjectRaw(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return root.getAsJsonObject("data");
            }
        } catch (Exception e) {
            Log.e("AddVehicleActivity", "extractObjectRaw error", e);
        }
        return null;
    }
}