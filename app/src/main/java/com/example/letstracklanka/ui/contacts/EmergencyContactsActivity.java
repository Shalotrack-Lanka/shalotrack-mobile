package com.example.letstracklanka.ui.contacts;

import android.app.AlertDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateEmergencyContactRequest;
import com.example.letstracklanka.data.model.EmergencyContactResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
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
 * Emergency contact list management -- new screen, nothing like it existed in
 * this app before. Real backend calls throughout (GET/POST/DELETE
 * api/EmergencyContacts), including surfacing the actual plan-based limit
 * message from the backend (e.g. "Your current plan allows up to 1
 * emergency contact(s)...") rather than a generic error, same discipline
 * already established for Subscriptions.
 */
public class EmergencyContactsActivity extends AppCompatActivity {

    private ApiService mainApiService;

    private View btnBack, errorBanner, progressBar, layoutEmptyState;
    private android.widget.TextView tvErrorBannerMessage, tvErrorBannerRetry;
    private RecyclerView rvContacts;
    private View fabAdd;
    private EmergencyContactAdapter adapter;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_contacts);

        mainApiService = ApiClient.getClient().create(ApiService.class);

        initViews();
        registerNetworkMonitor();
        fetchContacts();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBackEmergencyContacts);
        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        tvErrorBannerRetry = findViewById(R.id.tvErrorBannerRetry);
        progressBar = findViewById(R.id.progressEmergencyContacts);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        rvContacts = findViewById(R.id.rvEmergencyContacts);
        fabAdd = findViewById(R.id.fabAddEmergencyContact);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        if (tvErrorBannerRetry != null) tvErrorBannerRetry.setOnClickListener(v -> fetchContacts());
        if (fabAdd != null) fabAdd.setOnClickListener(v -> showAddContactDialog());

        rvContacts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EmergencyContactAdapter(this::confirmDeleteContact);
        rvContacts.setAdapter(adapter);
    }

    private void fetchContacts() {
        hideErrorBanner();
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        mainApiService.getMyEmergencyContacts().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<EmergencyContactResponse> contacts = parseList(body.string());
                        adapter.updateContacts(contacts);
                        if (layoutEmptyState != null) {
                            layoutEmptyState.setVisibility(contacts.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    } else {
                        Log.w("EmergencyContacts", "fetchContacts failed, code " + response.code());
                        showErrorBanner("Couldn't load your emergency contacts.");
                    }
                } catch (Exception e) {
                    Log.e("EmergencyContacts", "fetchContacts parse error", e);
                    showErrorBanner("Something went wrong loading your contacts.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                Log.e("EmergencyContacts", "fetchContacts network error", t);
                showErrorBanner("Network error \u2014 check your connection.");
            }
        });
    }

    private void showAddContactDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_emergency_contact, null);
        EditText etName = dialogView.findViewById(R.id.etContactName);
        EditText etPhone = dialogView.findViewById(R.id.etContactPhone);
        EditText etRelationship = dialogView.findViewById(R.id.etContactRelationship);

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String phone = etPhone.getText().toString().trim();
                    String relationship = etRelationship.getText().toString().trim();

                    if (name.isEmpty() || phone.isEmpty()) {
                        Toast.makeText(this, "Name and phone number are required.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    addContact(name, phone, relationship.isEmpty() ? null : relationship);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addContact(String name, String phone, String relationship) {
        CreateEmergencyContactRequest request = new CreateEmergencyContactRequest(name, phone, relationship);
        mainApiService.addEmergencyContact(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(EmergencyContactsActivity.this, "Contact added.", Toast.LENGTH_SHORT).show();
                    fetchContacts();
                } else {
                    // Surfaces the real backend message, e.g. "Your current
                    // plan allows up to 1 emergency contact(s). Upgrade
                    // your plan to add more." -- not a generic failure.
                    String errorBody = null;
                    try {
                        if (response.errorBody() != null) errorBody = response.errorBody().string();
                    } catch (Exception e) {
                        Log.e("EmergencyContacts", "addContact: failed to read error body", e);
                    }
                    // FIX: this branch previously logged nothing at all --
                    // no way to diagnose a failure even from Logcat. Same
                    // "log the actual error, not just show a toast"
                    // discipline used everywhere else in this project.
                    Log.w("EmergencyContacts", "addContact failed, code " + response.code() + ", body=" + errorBody);
                    String message = extractErrorMessage(errorBody, "Couldn't add contact. Please try again.");
                    Toast.makeText(EmergencyContactsActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("EmergencyContacts", "addContact network error", t);
                Toast.makeText(EmergencyContactsActivity.this, "Network error \u2014 check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmDeleteContact(EmergencyContactResponse contact) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + contact.getName() + "?")
                .setMessage("This contact will no longer be notified in an emergency.")
                .setPositiveButton("Remove", (dialog, which) -> deleteContact(contact))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteContact(EmergencyContactResponse contact) {
        mainApiService.deleteEmergencyContact(contact.getEmergencyContactId()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    fetchContacts();
                } else {
                    Log.w("EmergencyContacts", "deleteContact failed, code " + response.code());
                    Toast.makeText(EmergencyContactsActivity.this, "Couldn't remove contact. Please try again.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("EmergencyContacts", "deleteContact network error", t);
                Toast.makeText(EmergencyContactsActivity.this, "Network error \u2014 check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showErrorBanner(String message) {
        if (errorBanner == null) return;
        errorBanner.setVisibility(View.VISIBLE);
        if (tvErrorBannerMessage != null) tvErrorBannerMessage.setText(message);
    }

    private void hideErrorBanner() {
        if (errorBanner != null) errorBanner.setVisibility(View.GONE);
    }

    private void registerNetworkMonitor() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> showErrorBanner("No internet connection."));
            }

            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(EmergencyContactsActivity.this::fetchContacts);
            }
        };
        cm.registerDefaultNetworkCallback(networkCallback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        }
    }

    private List<EmergencyContactResponse> parseList(String json) {
        List<EmergencyContactResponse> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                Type listType = new TypeToken<List<EmergencyContactResponse>>() {}.getType();
                List<EmergencyContactResponse> parsed = gson.fromJson(root.getAsJsonArray("data"), listType);
                if (parsed != null) list = parsed;
            }
        } catch (Exception e) {
            Log.e("EmergencyContacts", "parseList error", e);
        }
        return list;
    }

    // Same pattern already used in HomeActivity's subscription error
    // handling -- pulls message + first entry of errors out of a failed
    // ApiResponse envelope, instead of a generic message.
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
            Log.e("EmergencyContacts", "extractErrorMessage parse error", e);
            return fallback;
        }
    }
}