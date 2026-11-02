package com.example.letstracklanka.ui.profile;

import com.example.letstracklanka.utils.ClickGuard;
import com.example.letstracklanka.utils.SecureScreen;
import com.example.letstracklanka.utils.InputValidator;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.UpdateCustomerRequest;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.utils.ApiJson;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Edit the customer's own name and home address (PUT api/Customers/{id}).
 *
 * The phone number is the sign-in identity, so it is shown but never editable here, and it is sent
 * back unchanged. The existing profile image value is also sent back unchanged so a save never
 * clears it. All results are shown inline: no Toast, Snackbar or dialog.
 */
public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";

    private ApiService api;

    private ProgressBar progress;
    private View groupError, groupForm;
    private TextView tvLoadError, tvPhone, tvEmail, tvMessage;
    private TextInputLayout layoutFullName;
    private TextInputEditText etFullName, etAddress;
    private MaterialButton btnSave;

    private String customerId;
    private String currentPhone;
    private String currentProfileImage;
    private boolean saving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SecureScreen.enable(this);
        setContentView(R.layout.activity_profile);

        api = ApiClient.getClient().create(ApiService.class);

        progress = findViewById(R.id.progressProfile);
        groupError = findViewById(R.id.groupProfileError);
        groupForm = findViewById(R.id.groupProfileForm);
        tvLoadError = findViewById(R.id.tvProfileLoadError);
        tvPhone = findViewById(R.id.tvPhone);
        tvEmail = findViewById(R.id.tvEmail);
        tvMessage = findViewById(R.id.tvProfileMessage);
        layoutFullName = findViewById(R.id.layoutFullName);
        etFullName = findViewById(R.id.etFullName);
        etAddress = findViewById(R.id.etAddress);
        btnSave = findViewById(R.id.btnSaveProfile);

        findViewById(R.id.btnBackProfile).setOnClickListener(v -> finish());
        findViewById(R.id.btnProfileRetry).setOnClickListener(v -> loadProfile());
        btnSave.setOnClickListener(ClickGuard.wrap(v -> save()));

        loadProfile();
    }

    private void loadProfile() {
        progress.setVisibility(View.VISIBLE);
        groupError.setVisibility(View.GONE);
        groupForm.setVisibility(View.GONE);

        api.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        CustomerResponse customer = ApiJson.object(body.string(), CustomerResponse.class);
                        if (customer != null && customer.getCustomerId() != null) {
                            showProfile(customer);
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "loadProfile parse error", e);
                }
                showLoadError("We couldn't load your profile.");
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                Log.e(TAG, "loadProfile network error", t);
                showLoadError("No connection. Check your internet and try again.");
            }
        });
    }

    private void showProfile(CustomerResponse customer) {
        customerId = customer.getCustomerId();
        currentPhone = customer.getPhoneNumber();
        currentProfileImage = customer.getProfileImage();

        etFullName.setText(customer.getFullName() != null ? customer.getFullName() : "");
        etAddress.setText(customer.getAddress() != null ? customer.getAddress() : "");
        tvPhone.setText(currentPhone != null && !currentPhone.isEmpty() ? currentPhone : "Not set");
        String email = customer.getEmail();
        tvEmail.setText(email != null && !email.isEmpty() ? email : "Not set");

        tvMessage.setVisibility(View.GONE);
        groupForm.setVisibility(View.VISIBLE);
    }

    private void showLoadError(String message) {
        tvLoadError.setText(message);
        groupError.setVisibility(View.VISIBLE);
    }

    private void save() {
        if (saving || customerId == null) return;

        String name = InputValidator.cleanLine(etFullName.getText() != null ? etFullName.getText().toString() : "");
        String address = InputValidator.cleanLine(etAddress.getText() != null ? etAddress.getText().toString() : "");

        layoutFullName.setError(InputValidator.name(name));
        if (layoutFullName.getError() != null) return;
        if (!address.isEmpty()) {
            String addressError = InputValidator.address(address);
            if (addressError != null) {
                showMessage(addressError, false);
                return;
            }
        }

        setSaving(true);
        showMessage(null, false);

        UpdateCustomerRequest request = new UpdateCustomerRequest(
                name, currentPhone, address.isEmpty() ? null : address, currentProfileImage);

        api.updateCustomer(customerId, request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) return;
                setSaving(false);
                if (response.isSuccessful()) {
                    showMessage("Profile saved.", true);
                    return;
                }
                int code = response.code();
                String serverMessage = null;
                try (ResponseBody err = response.errorBody()) {
                    if (err != null && code >= 400 && code < 500) serverMessage = err.string();
                } catch (Exception e) {
                    Log.w(TAG, "could not read error body", e);
                }
                if (code == 401) {
                    showMessage("Your sign-in has expired. Please sign out and sign in again.", false);
                } else if (code == 400 || code == 409 || code == 422) {
                    showMessage(ApiJson.message(serverMessage, "Those details couldn't be saved. Check them and try again."), false);
                } else {
                    Log.w(TAG, "save failed, code " + code);
                    showMessage("We couldn't save your profile. Please try again.", false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                setSaving(false);
                Log.e(TAG, "save network error", t);
                showMessage("No connection. Check your internet and try again.", false);
            }
        });
    }

    private void setSaving(boolean value) {
        saving = value;
        btnSave.setEnabled(!value);
        btnSave.setText(value ? "Saving..." : "Save changes");
    }

    private void showMessage(String message, boolean success) {
        if (message == null) {
            tvMessage.setVisibility(View.GONE);
            return;
        }
        tvMessage.setText(message);
        if (success) {
            tvMessage.setBackgroundColor(Color.parseColor("#F0FDF4"));
            tvMessage.setTextColor(ContextCompat.getColor(this, R.color.status_success));
        } else {
            tvMessage.setBackgroundColor(ContextCompat.getColor(this, R.color.danger_banner_bg));
            tvMessage.setTextColor(ContextCompat.getColor(this, R.color.danger_banner_text));
        }
        tvMessage.setVisibility(View.VISIBLE);
    }
}