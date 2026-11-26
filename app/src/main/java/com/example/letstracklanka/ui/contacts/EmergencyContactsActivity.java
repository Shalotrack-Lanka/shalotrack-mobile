package com.example.letstracklanka.ui.contacts;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateEmergencyContactRequest;
import com.example.letstracklanka.data.model.EmergencyContactResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.ConfirmSheet;
import com.example.letstracklanka.ui.common.EdgeToEdge;
import com.example.letstracklanka.ui.common.EmptyState;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.NetworkWatcher;
import com.example.letstracklanka.ui.common.RowDividers;
import com.example.letstracklanka.ui.common.TopBar;
import com.example.letstracklanka.utils.ApiErrors;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.InputValidator;
import com.example.letstracklanka.utils.SecureScreen;
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
 * Emergency contacts (api/EmergencyContacts). After an SOS the app lists these people so the customer can
 * call them; the server does not message them, and the screen says so. The plan's contact limit is
 * enforced by the server, whose own message is shown when it refuses. No Toast, Snackbar or dialog.
 */
public class EmergencyContactsActivity extends AppCompatActivity {

    private ApiService api;

    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private RecyclerView list;
    private InlineBanner banner;
    private EmptyState empty;
    private EmergencyContactAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_emergency_contacts);
        SecureScreen.guardTouches(this);
        TopBar.bind(this, R.string.st_contacts_title);
        EdgeToEdge.padBottom(findViewById(R.id.contactsContent));

        api = ApiClient.getClient().create(ApiService.class);
        swipe = findViewById(R.id.swipeContacts);
        progress = findViewById(R.id.progressContacts);
        list = findViewById(R.id.rvContacts);
        banner = InlineBanner.in(this);
        empty = new EmptyState(findViewById(R.id.emptyState));

        swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.st_navy));
        swipe.setOnRefreshListener(this::load);

        adapter = new EmergencyContactAdapter(new EmergencyContactAdapter.Listener() {
            @Override
            public void onCall(EmergencyContactResponse contact) {
                dial(contact.getPhoneNumber());
            }

            @Override
            public void onRemove(EmergencyContactResponse contact) {
                confirmRemove(contact);
            }
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        RowDividers.attach(list, true);

        findViewById(R.id.btnAddContact).setOnClickListener(v -> showAddSheet());

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

        progress.setVisibility(View.VISIBLE);
        load();
    }

    private void load() {
        api.getMyEmergencyContacts().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) {
                    ApiErrors.close(response);
                    return;
                }
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                List<EmergencyContactResponse> contacts = null;
                if (response.isSuccessful()) {
                    try (ResponseBody body = response.body()) {
                        if (body != null) contacts = ApiJson.list(body.string(), EmergencyContactResponse.class);
                    } catch (Exception ignored) {
                        contacts = null;
                    }
                } else {
                    ApiErrors.close(response);
                }
                if (contacts == null) {
                    banner.showError(response.code() == 401 ? R.string.st_error_session_expired
                            : R.string.st_contacts_load_failed, () -> load());
                    return;
                }
                show(contacts);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                banner.showError(R.string.st_error_network, () -> load());
            }
        });
    }

    private void show(List<EmergencyContactResponse> contacts) {
        List<EmergencyContactResponse> clean = new ArrayList<>();
        for (EmergencyContactResponse c : contacts) if (c != null && c.getEmergencyContactId() != null) clean.add(c);
        adapter.updateContacts(clean);
        list.setVisibility(clean.isEmpty() ? View.GONE : View.VISIBLE);
        if (clean.isEmpty()) {
            empty.show(getString(R.string.st_contacts_empty_title), getString(R.string.st_contacts_empty_body), null, null);
        } else {
            empty.hide();
        }
    }

    private void dial(String number) {
        if (number == null || number.trim().isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.trim())));
        } catch (Exception e) {
            banner.showError(R.string.st_error_no_dialer, null);
        }
    }

    // ------------------------------------------------------------------------------------ add

    @SuppressLint("InflateParams") // dialog content has no parent to measure against
    private void showAddSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_add_contact, null);
        sheet.setContentView(view);

        TextInputLayout tilName = view.findViewById(R.id.tilContactName);
        TextInputLayout tilPhone = view.findViewById(R.id.tilContactPhone);
        TextInputLayout tilRelationship = view.findViewById(R.id.tilContactRelationship);
        TextInputEditText etName = view.findViewById(R.id.etContactName);
        TextInputEditText etPhone = view.findViewById(R.id.etContactPhone);
        TextInputEditText etRelationship = view.findViewById(R.id.etContactRelationship);
        InlineBanner sheetBanner = new InlineBanner(view.findViewById(R.id.inlineBanner));
        MaterialButton save = view.findViewById(R.id.btnSaveContact);
        MaterialButton cancel = view.findViewById(R.id.btnCancelContact);

        cancel.setOnClickListener(v -> sheet.dismiss());
        etRelationship.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save.performClick();
                return true;
            }
            return false;
        });

        save.setOnClickListener(v -> {
            tilName.setError(null);
            tilPhone.setError(null);
            tilRelationship.setError(null);
            sheetBanner.hide();

            String name = InputValidator.cleanLine(text(etName));
            String phone = InputValidator.cleanLine(text(etPhone));
            String relationship = InputValidator.cleanLine(text(etRelationship));

            String nameError = InputValidator.name(name);
            if (nameError != null) {
                tilName.setError(nameError);
                etName.requestFocus();
                return;
            }
            String phoneError = InputValidator.phoneAny(phone);
            if (phoneError != null) {
                tilPhone.setError(phoneError);
                etPhone.requestFocus();
                return;
            }
            if (!relationship.isEmpty()) {
                String relError = InputValidator.label(relationship, 1, 50, "the relationship");
                if (relError != null) {
                    tilRelationship.setError(relError);
                    etRelationship.requestFocus();
                    return;
                }
            }

            setBusy(sheet, save, cancel, true);
            CreateEmergencyContactRequest request = new CreateEmergencyContactRequest(name,
                    InputValidator.toE164Any(phone), relationship.isEmpty() ? null : relationship);
            api.addEmergencyContact(request).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    if (!sheet.isShowing()) {
                        ApiErrors.close(response);
                        return;
                    }
                    setBusy(sheet, save, cancel, false);
                    if (response.isSuccessful()) {
                        ApiErrors.close(response);
                        sheet.dismiss();
                        if (isFinishing() || isDestroyed()) return;
                        load();
                        banner.showSuccess(getString(R.string.st_contact_added, name));
                        return;
                    }
                    // The server's own words, e.g. the plan's contact limit.
                    ApiErrors.Failure f = ApiErrors.read(response, getString(R.string.st_contact_save_failed));
                    sheetBanner.showError(f.code == 401 ? getString(R.string.st_error_session_expired) : f.message, null);
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    if (!sheet.isShowing()) return;
                    setBusy(sheet, save, cancel, false);
                    sheetBanner.showError(getString(R.string.st_error_network), null);
                }
            });
        });

        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.show();
    }

    private static void setBusy(BottomSheetDialog sheet, MaterialButton save, MaterialButton cancel, boolean busy) {
        save.setEnabled(!busy);
        cancel.setEnabled(!busy);
        sheet.setCancelable(!busy);
        save.setText(busy ? R.string.st_action_saving : R.string.st_contact_save);
    }

    private static String text(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString() : "";
    }

    // --------------------------------------------------------------------------------- remove

    private void confirmRemove(EmergencyContactResponse contact) {
        String name = contact.getName() != null ? contact.getName() : "";
        ConfirmSheet.show(this, getString(R.string.st_contacts_remove_title, name), getString(R.string.st_contacts_remove_body),
                getString(R.string.st_action_remove), getString(R.string.st_action_removing), done ->
                        api.deleteEmergencyContact(contact.getEmergencyContactId()).enqueue(new Callback<ResponseBody>() {
                            @Override
                            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                                if (response.isSuccessful()) {
                                    ApiErrors.close(response);
                                    done.succeeded();
                                    if (isFinishing() || isDestroyed()) return;
                                    load();
                                    banner.showSuccess(getString(R.string.st_contacts_removed, name));
                                    return;
                                }
                                done.failed(ApiErrors.read(response, getString(R.string.st_contacts_remove_failed)).message);
                            }

                            @Override
                            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                                done.failed(getString(R.string.st_error_network));
                            }
                        }));
    }
}
