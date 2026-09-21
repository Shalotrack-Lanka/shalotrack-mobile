package com.example.letstracklanka.ui.complaints;

import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateComplaintRequest;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * File a new complaint. Vehicle picker is scoped to ONLY this customer's
 * owned vehicles (api/Vehicles/customer/{customerId} — the same
 * "getVehiclesByCustomer" endpoint used for the geofence vehicle-scope
 * picker), never the shared-with-me list — matches
 * ComplaintService.CreateAsync's strict server-side ownership check
 * exactly, so a customer never sees a vehicle here that the API would
 * reject anyway.
 *
 * Category labels/order below are index-matched 1:1 to
 * ShaloTrack_API.Enums.ComplaintCategory (DeviceIssue=0, Billing=1,
 * AppBug=2, Other=3) — the label list's position IS the value sent, not
 * re-derived from the displayed text.
 *
 * Both pickers use inputType="none" (no keyboard), so AutoCompleteTextView
 * won't auto-open its dropdown on typed filter matches like it normally
 * does -- showDropDown() is called explicitly on tap instead.
 */
public class FileComplaintActivity extends AppCompatActivity {

    private static final String[] CATEGORY_LABELS = {"Device Issue", "Billing", "App Bug", "Other"};

    private ApiService mainApiService;
    private String currentCustomerId;
    private final List<VehicleResponse> myVehicles = new ArrayList<>();

    private AutoCompleteTextView spinnerVehicle, spinnerCategory;
    private EditText etDescription;
    private MaterialButton btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_complaint);

        mainApiService = ApiClient.getClient().create(ApiService.class);

        findViewById(R.id.btnBackFileComplaint).setOnClickListener(v -> finish());

        spinnerVehicle = findViewById(R.id.spinnerComplaintVehicle);
        spinnerCategory = findViewById(R.id.spinnerComplaintCategory);
        etDescription = findViewById(R.id.etComplaintDescription);
        btnSubmit = findViewById(R.id.btnSubmitComplaint);

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, CATEGORY_LABELS);
        spinnerCategory.setAdapter(categoryAdapter);
        spinnerCategory.setText(CATEGORY_LABELS[0], false);
        spinnerCategory.setOnClickListener(v -> spinnerCategory.showDropDown()); // NEW

        btnSubmit.setOnClickListener(v -> submitComplaint());

        loadMyVehicles();
    }

    private void loadMyVehicles() {
        mainApiService.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        CustomerResponse customer = extractObject(body.string(), CustomerResponse.class);
                        if (customer != null && customer.getCustomerId() != null) {
                            currentCustomerId = customer.getCustomerId();
                            fetchMyVehiclesForPicker();
                        } else {
                            Toast.makeText(FileComplaintActivity.this, "Couldn't load your profile. Try again.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.w("FileComplaint", "getMyProfile failed, code " + response.code());
                        Toast.makeText(FileComplaintActivity.this, "Couldn't load your profile. Try again.", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e("FileComplaint", "loadMyVehicles parse error", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("FileComplaint", "loadMyVehicles network error", t);
                Toast.makeText(FileComplaintActivity.this, "Network error — check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchMyVehiclesForPicker() {
        mainApiService.getVehiclesByCustomer(currentCustomerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<VehicleResponse> list = extractList(body.string(), VehicleResponse.class);
                        myVehicles.clear();
                        if (list != null) myVehicles.addAll(list);

                        List<String> labels = new ArrayList<>();
                        for (VehicleResponse vehicle : myVehicles) {
                            String label = (safe(vehicle.getMake()) + " " + safe(vehicle.getModel())).trim();
                            if (vehicle.getVehicleNumber() != null) label += " (" + vehicle.getVehicleNumber() + ")";
                            labels.add(label.trim().isEmpty() ? "Vehicle" : label);
                        }
                        ArrayAdapter<String> vehicleAdapter = new ArrayAdapter<>(
                                FileComplaintActivity.this, android.R.layout.simple_dropdown_item_1line, labels);
                        spinnerVehicle.setAdapter(vehicleAdapter);
                        if (!labels.isEmpty()) spinnerVehicle.setText(labels.get(0), false);
                        spinnerVehicle.setOnClickListener(v -> spinnerVehicle.showDropDown()); // NEW
                    } else {
                        Log.w("FileComplaint", "fetchMyVehiclesForPicker failed, code " + response.code());
                    }
                } catch (Exception e) {
                    Log.e("FileComplaint", "fetchMyVehiclesForPicker parse error", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("FileComplaint", "fetchMyVehiclesForPicker network error", t);
                Toast.makeText(FileComplaintActivity.this, "Network error — check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void submitComplaint() {
        if (myVehicles.isEmpty()) {
            Toast.makeText(this, "You don't have any vehicles to file a complaint for.", Toast.LENGTH_SHORT).show();
            return;
        }

        String vehicleText = spinnerVehicle.getText() != null ? spinnerVehicle.getText().toString() : "";
        int vehicleIndex = findVehicleIndex(vehicleText);
        if (vehicleIndex < 0) {
            Toast.makeText(this, "Please select a vehicle.", Toast.LENGTH_SHORT).show();
            return;
        }

        String categoryText = spinnerCategory.getText() != null ? spinnerCategory.getText().toString() : "";
        int categoryIndex = indexOf(CATEGORY_LABELS, categoryText);
        if (categoryIndex < 0) categoryIndex = 0; // "Device Issue" default, matches the pre-selected text

        String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
        if (description.isEmpty()) {
            Toast.makeText(this, "Please describe the issue.", Toast.LENGTH_SHORT).show();
            return;
        }

        String vehicleId = myVehicles.get(vehicleIndex).getVehicleId();

        btnSubmit.setEnabled(false);
        CreateComplaintRequest request = new CreateComplaintRequest(vehicleId, categoryIndex, description);
        mainApiService.fileComplaint(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                btnSubmit.setEnabled(true);
                if (response.isSuccessful()) {
                    Toast.makeText(FileComplaintActivity.this, "Complaint submitted.", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    String errorBody = null;
                    try {
                        if (response.errorBody() != null) errorBody = response.errorBody().string();
                    } catch (Exception e) {
                        Log.e("FileComplaint", "submitComplaint: failed to read error body", e);
                    }
                    Log.w("FileComplaint", "submitComplaint failed, code " + response.code() + ", body=" + errorBody);
                    String message = extractErrorMessage(errorBody, "Couldn't submit your complaint. Please try again.");
                    Toast.makeText(FileComplaintActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                btnSubmit.setEnabled(true);
                Log.e("FileComplaint", "submitComplaint network error", t);
                Toast.makeText(FileComplaintActivity.this, "Network error — check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int findVehicleIndex(String label) {
        if (label == null) return -1;
        for (int i = 0; i < myVehicles.size(); i++) {
            VehicleResponse vehicle = myVehicles.get(i);
            String candidate = (safe(vehicle.getMake()) + " " + safe(vehicle.getModel())).trim();
            if (vehicle.getVehicleNumber() != null) candidate += " (" + vehicle.getVehicleNumber() + ")";
            if (candidate.trim().equals(label.trim())) return i;
        }
        return -1;
    }

    private int indexOf(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) return i;
        }
        return -1;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private <T> T extractObject(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), clazz);
            }
            return gson.fromJson(json, clazz);
        } catch (Exception e) {
            Log.e("FileComplaint", "extractObject error", e);
            return null;
        }
    }

    private <T> List<T> extractList(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                Type listType = TypeToken.getParameterized(List.class, clazz).getType();
                return gson.fromJson(root.getAsJsonArray("data"), listType);
            }
        } catch (Exception e) {
            Log.e("FileComplaint", "extractList error", e);
        }
        return null;
    }

    private String extractErrorMessage(String json, String fallback) {
        if (json == null || json.trim().isEmpty()) return fallback;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null) return fallback;
            String message = (root.has("message") && !root.get("message").isJsonNull())
                    ? root.get("message").getAsString() : null;
            String firstError = null;
            if (root.has("errors") && root.get("errors").isJsonArray() && root.getAsJsonArray("errors").size() > 0) {
                firstError = root.getAsJsonArray("errors").get(0).getAsString();
            }
            if (message != null && firstError != null) return message + " " + firstError;
            if (message != null) return message;
            if (firstError != null) return firstError;
            return fallback;
        } catch (Exception e) {
            Log.e("FileComplaint", "extractErrorMessage parse error", e);
            return fallback;
        }
    }
}