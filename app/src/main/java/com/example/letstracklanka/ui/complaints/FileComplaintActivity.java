package com.example.letstracklanka.ui.complaints;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateComplaintRequest;
import com.example.letstracklanka.data.model.DashboardVehicle;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.EdgeToEdge;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.TopBar;
import com.example.letstracklanka.ui.common.VehicleField;
import com.example.letstracklanka.utils.ApiErrors;
import com.example.letstracklanka.utils.InputValidator;
import com.example.letstracklanka.utils.SecureScreen;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * New complaint (POST api/Complaints). Only the customer's own vehicles are offered, matching the server's
 * ownership check. The category chips are index-matched to the API's ComplaintCategory enum. On success
 * the screen closes with RESULT_OK and the list says it was sent. No Toast, Snackbar or dialog.
 */
public class FileComplaintActivity extends AppCompatActivity {

    private static final String STATE_CATEGORY = "category";
    private static final int[] CHIP_IDS = {R.id.chipCatDevice, R.id.chipCatBilling, R.id.chipCatApp, R.id.chipCatOther};

    private VehicleField vehicle;
    private TextInputLayout tilDescription;
    private TextInputEditText etDescription;
    private MaterialButton btnSubmit;
    private InlineBanner banner;
    private final TextView[] chips = new TextView[CHIP_IDS.length];
    private int category;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_file_complaint);
        SecureScreen.guardTouches(this);
        TopBar.bind(this, R.string.st_complaint_new_title);
        EdgeToEdge.padBottomWithKeyboard(findViewById(R.id.fileComplaintFooter));

        banner = InlineBanner.in(this);
        vehicle = new VehicleField(this, findViewById(R.id.stVehicleField), true);
        tilDescription = findViewById(R.id.tilComplaintDescription);
        etDescription = findViewById(R.id.etComplaintDescription);
        btnSubmit = findViewById(R.id.btnSubmitComplaint);

        category = savedInstanceState != null ? savedInstanceState.getInt(STATE_CATEGORY, 0) : 0;
        for (int i = 0; i < CHIP_IDS.length; i++) {
            final int index = i;
            chips[i] = findViewById(CHIP_IDS[i]);
            chips[i].setOnClickListener(v -> selectCategory(index));
        }
        selectCategory(category);

        btnSubmit.setOnClickListener(v -> submit());
        vehicle.load(null);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CATEGORY, category);
    }

    private void selectCategory(int index) {
        category = index;
        for (int i = 0; i < chips.length; i++) {
            boolean on = i == index;
            chips[i].setSelected(on);
            chips[i].setContentDescription(chips[i].getText() + (on ? ", " + getString(R.string.st_a11y_selected) : ""));
        }
    }

    private void submit() {
        tilDescription.setError(null);
        banner.hide();

        DashboardVehicle chosen = vehicle.get();
        if (chosen == null) {
            banner.showError(getString(vehicle.hasNoVehicles()
                    ? R.string.st_field_vehicle_none_owned : R.string.st_field_vehicle_pick), null);
            return;
        }
        String description = InputValidator.clean(etDescription.getText() != null ? etDescription.getText().toString() : "");
        String error = description.isEmpty()
                ? getString(R.string.st_complaint_describe_required)
                : InputValidator.message(description, 10);
        if (error != null) {
            tilDescription.setError(error);
            etDescription.requestFocus();
            return;
        }

        setBusy(true);
        ApiClient.getClient().create(ApiService.class)
                .fileComplaint(new CreateComplaintRequest(chosen.getVehicleId(), category, description))
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        if (isFinishing() || isDestroyed()) {
                            ApiErrors.close(response);
                            return;
                        }
                        if (response.isSuccessful()) {
                            ApiErrors.close(response);
                            setResult(RESULT_OK);
                            finish();
                            return;
                        }
                        setBusy(false);
                        if (response.code() == 401) {
                            ApiErrors.close(response);
                            banner.showError(getString(R.string.st_error_session_expired), null);
                            return;
                        }
                        banner.showError(ApiErrors.read(response, getString(R.string.st_complaint_send_failed)).message, null);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        setBusy(false);
                        banner.showError(getString(R.string.st_error_network), null);
                    }
                });
    }

    private void setBusy(boolean busy) {
        btnSubmit.setEnabled(!busy);
        vehicle.setEnabled(!busy);
        etDescription.setEnabled(!busy);
        for (TextView chip : chips) chip.setEnabled(!busy);
        btnSubmit.setText(busy ? R.string.st_complaint_sending : R.string.st_complaint_send);
    }
}
