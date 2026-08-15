package com.example.letstracklanka.ui.main;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateSubscriptionRequest;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.UpdateCustomerRequest;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.contacts.EmergencyContactsActivity;
import com.example.letstracklanka.ui.vehicles.AddVehicleActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Callback;

/**
 * Every drawer menu item's real behavior, extracted from HomeActivity so
 * VehiclesActivity/AlertsActivity/TagsActivity/CirclesActivity can each get
 * a fully working drawer of their own instead of navigating away to Home
 * (or, for Tags/Circles, instead of the previous bug where "Menu" expanded
 * an unrelated empty-state bottom sheet that had nothing to do with a menu
 * at all).
 *
 * Deliberately self-contained: fetches the customer profile itself rather
 * than assuming the calling Activity has one cached, since Alerts/Tags/
 * Circles never did before this existed.
 *
 * Usage: call DrawerMenuHelper.wireDrawer(activity, drawerLayout,
 * tvDrawerName, tvDrawerPhone, tvDrawerEmail) once, after the drawer's
 * include layout view is in the layout.
 */
public final class DrawerMenuHelper {

    private DrawerMenuHelper() {} // static utility, never instantiated

    public static void wireDrawer(
            AppCompatActivity activity,
            DrawerLayout drawerLayout,
            TextView tvDrawerName,
            TextView tvDrawerPhone,
            TextView tvDrawerEmail) {

        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        loadProfileIntoHeader(activity, apiService, tvDrawerName, tvDrawerPhone, tvDrawerEmail);

        View ivEditProfileMenu = activity.findViewById(R.id.ivEditProfileMenu);
        if (ivEditProfileMenu != null) {
            ivEditProfileMenu.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showEditProfileBottomSheet(activity, apiService, tvDrawerName, tvDrawerPhone, tvDrawerEmail);
            });
        }

        TextView tvLogOut = activity.findViewById(R.id.tvLogOut);
        if (tvLogOut != null) {
            tvLogOut.setOnClickListener(v -> {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(activity, com.example.letstracklanka.ui.auth.LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                activity.startActivity(intent);
                activity.finish();
            });
        }

        View btnMenuAddNew = activity.findViewById(R.id.btnMenuAddNew);
        if (btnMenuAddNew != null) {
            btnMenuAddNew.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                launchAddVehicle(activity, apiService);
            });
        }

        View btnMenuReports = activity.findViewById(R.id.btnMenuReports);
        if (btnMenuReports != null) {
            btnMenuReports.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showReportsMenuBottomSheet(activity);
            });
        }

        View btnMenuChatSupport = activity.findViewById(R.id.btnMenuChatSupport);
        if (btnMenuChatSupport != null) {
            btnMenuChatSupport.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showCallCenterBottomSheet(activity);
            });
        }

        View btnMenuVoiceTrack = activity.findViewById(R.id.btnMenuVoiceTrack);
        if (btnMenuVoiceTrack != null) {
            btnMenuVoiceTrack.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showVoiceTrackBottomSheet(activity);
            });
        }

        View btnMenuPlaces = activity.findViewById(R.id.btnMenuPlaces);
        if (btnMenuPlaces != null) {
            btnMenuPlaces.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showPlacesBottomSheet(activity);
            });
        }

        View btnMenuVehicleSubs = activity.findViewById(R.id.btnMenuVehicleSubs);
        if (btnMenuVehicleSubs != null) {
            btnMenuVehicleSubs.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showDevicesToRenewBottomSheet(activity);
            });
        }

        View btnMenuAppSubs = activity.findViewById(R.id.btnMenuAppSubs);
        if (btnMenuAppSubs != null) {
            btnMenuAppSubs.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showAppSubscriptionBottomSheet(activity, apiService);
            });
        }

        View btnMenuEmergencyContacts = activity.findViewById(R.id.btnMenuEmergencyContacts);
        if (btnMenuEmergencyContacts != null) {
            btnMenuEmergencyContacts.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                activity.startActivity(new Intent(activity, EmergencyContactsActivity.class));
            });
        }

        View btnMenuSettings = activity.findViewById(R.id.btnMenuSettings);
        if (btnMenuSettings != null) {
            btnMenuSettings.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showSettingsBottomSheet(activity);
            });
        }

        // Buttons that are not ready yet (Coming soon) -- includes Refer &
        // Earn, which turned out to have no real wiring anywhere, even in
        // the original HomeActivity, despite looking like a real item.
        int[] comingSoonIds = {
                R.id.btnMenuRefer,
                R.id.btnMenuShop,
                R.id.btnMenuHelpVideos,
                R.id.btnMenuPrivacy
        };
        for (int id : comingSoonIds) {
            View item = activity.findViewById(id);
            if (item != null) {
                item.setOnClickListener(v -> Toast.makeText(activity, "Coming soon", Toast.LENGTH_SHORT).show());
            }
        }
    }

    private static void loadProfileIntoHeader(AppCompatActivity activity, ApiService apiService,
                                              TextView tvDrawerName, TextView tvDrawerPhone, TextView tvDrawerEmail) {
        apiService.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<ResponseBody> call, Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) return;
                    String json = body.string();
                    Gson gson = new Gson();
                    JsonObject root = gson.fromJson(json, JsonObject.class);
                    JsonObject data = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : root;

                    // Matches HomeActivity's real, existing logic exactly --
                    // checks both possible field names, not just one typed
                    // getter, same defensive dual-naming pattern already
                    // established elsewhere in this project.
                    String name = data.has("name") && !data.get("name").isJsonNull() ? data.get("name").getAsString() :
                            (data.has("fullName") && !data.get("fullName").isJsonNull() ? data.get("fullName").getAsString() : "Unknown User");
                    String phone = data.has("phone") && !data.get("phone").isJsonNull() ? data.get("phone").getAsString() :
                            (data.has("phoneNumber") && !data.get("phoneNumber").isJsonNull() ? data.get("phoneNumber").getAsString() : "No Phone Number");
                    String email = data.has("email") && !data.get("email").isJsonNull() ? data.get("email").getAsString() : "No Email";

                    if (tvDrawerName != null) tvDrawerName.setText(name);
                    if (tvDrawerPhone != null) tvDrawerPhone.setText(phone);
                    if (tvDrawerEmail != null) tvDrawerEmail.setText(email);
                } catch (Exception e) {
                    Log.e("DrawerMenuHelper", "loadProfileIntoHeader parse error", e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ResponseBody> call, Throwable t) {
                Log.e("DrawerMenuHelper", "loadProfileIntoHeader network error", t);
            }
        });
    }

    private static void showEditProfileBottomSheet(AppCompatActivity activity, ApiService apiService,
                                                   TextView tvDrawerName, TextView tvDrawerPhone, TextView tvDrawerEmail) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_edit_profile, null);
        dialog.setContentView(view);
        ImageView btnClose = view.findViewById(R.id.btnCloseEditProfile);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveProfile);
        EditText etFirstName = view.findViewById(R.id.etFirstName);
        EditText etSurname = view.findViewById(R.id.etSurname);
        EditText etPhone = view.findViewById(R.id.etPhone);
        EditText etEmail = view.findViewById(R.id.etEmail);

        if (tvDrawerName != null) {
            String fullName = tvDrawerName.getText().toString();
            String[] nameParts = fullName.split(" ");
            if (nameParts.length > 0) etFirstName.setText(nameParts[0]);
            if (nameParts.length > 1) {
                StringBuilder surname = new StringBuilder();
                for (int i = 1; i < nameParts.length; i++) surname.append(nameParts[i]).append(" ");
                etSurname.setText(surname.toString().trim());
            }
        }
        if (tvDrawerPhone != null) etPhone.setText(tvDrawerPhone.getText().toString());
        if (tvDrawerEmail != null) etEmail.setText(tvDrawerEmail.getText().toString());

        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        // Needs the real customer object (address/profileImage aren't in
        // the header text views), so this fetches its own fresh copy
        // rather than relying on state HomeActivity used to cache.
        btnSave.setOnClickListener(v -> {
            apiService.getMyProfile().enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(retrofit2.Call<ResponseBody> call, Response<ResponseBody> response) {
                    try (ResponseBody body = response.body()) {
                        if (!response.isSuccessful() || body == null) {
                            Toast.makeText(activity, "Profile not loaded yet, try again in a moment", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        CustomerResponse customer = extractObject(body.string(), CustomerResponse.class);
                        if (customer == null || customer.getCustomerId() == null) {
                            Toast.makeText(activity, "Profile not loaded yet, try again in a moment", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String firstName = etFirstName.getText().toString().trim();
                        String surname = etSurname.getText().toString().trim();
                        String fullName = surname.isEmpty() ? firstName : firstName + " " + surname;
                        String phone = etPhone.getText().toString().trim();
                        if (fullName.isEmpty()) {
                            Toast.makeText(activity, "Name can't be empty", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        btnSave.setEnabled(false);
                        UpdateCustomerRequest request = new UpdateCustomerRequest(
                                fullName, phone, customer.getAddress(), customer.getProfileImage());
                        apiService.updateCustomer(customer.getCustomerId(), request).enqueue(new Callback<ResponseBody>() {
                            @Override
                            public void onResponse(retrofit2.Call<ResponseBody> call, Response<ResponseBody> response) {
                                btnSave.setEnabled(true);
                                if (response.isSuccessful()) {
                                    Toast.makeText(activity, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                                    if (tvDrawerName != null) tvDrawerName.setText(fullName);
                                    if (tvDrawerPhone != null) tvDrawerPhone.setText(phone);
                                    dialog.dismiss();
                                } else {
                                    Toast.makeText(activity, "Could not save (code " + response.code() + ")", Toast.LENGTH_LONG).show();
                                }
                            }

                            @Override
                            public void onFailure(retrofit2.Call<ResponseBody> call, Throwable t) {
                                btnSave.setEnabled(true);
                                Toast.makeText(activity, "Network error \u2014 check your connection", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception e) {
                        Log.e("DrawerMenuHelper", "showEditProfileBottomSheet parse error", e);
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<ResponseBody> call, Throwable t) {
                    Toast.makeText(activity, "Network error \u2014 check your connection", Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.show();
    }

    // Fetches the real customer ID fresh, then launches AddVehicleActivity
    // -- the newer, cleaner flow that won over the old showAddVehicleDialog
    // (a real, complete duplicate found and removed during this same
    // audit).
    private static void launchAddVehicle(AppCompatActivity activity, ApiService apiService) {
        apiService.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<ResponseBody> call, Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        Toast.makeText(activity, "Couldn't load your profile. Try again.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    CustomerResponse customer = extractObject(body.string(), CustomerResponse.class);
                    if (customer == null || customer.getCustomerId() == null) {
                        Toast.makeText(activity, "Couldn't load your profile. Try again.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intent = new Intent(activity, AddVehicleActivity.class);
                    intent.putExtra(AddVehicleActivity.EXTRA_CUSTOMER_ID, customer.getCustomerId());
                    activity.startActivity(intent);
                } catch (Exception e) {
                    Log.e("DrawerMenuHelper", "launchAddVehicle parse error", e);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ResponseBody> call, Throwable t) {
                Toast.makeText(activity, "Network error \u2014 check your connection", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void showPlacesBottomSheet(AppCompatActivity activity) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_places, null);
        dialog.setContentView(view);
        ImageView btnClose = view.findViewById(R.id.btnClosePlaces);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.show();
    }

    private static void showDevicesToRenewBottomSheet(AppCompatActivity activity) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_devices_to_renew, null);
        dialog.setContentView(view);
        ImageView btnClose = view.findViewById(R.id.btnCloseRenew);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        MaterialButton btnShopNow = view.findViewById(R.id.btnShopNow);
        if (btnShopNow != null) {
            btnShopNow.setOnClickListener(v -> {
                Toast.makeText(activity, "Opening Shop...", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }
        dialog.show();
    }

    private static void showAppSubscriptionBottomSheet(AppCompatActivity activity, ApiService apiService) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_app_subscription, null);
        dialog.setContentView(view);

        ImageView btnClose = view.findViewById(R.id.btnCloseAppSubs);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        MaterialCardView cardFree = view.findViewById(R.id.cardFree);
        MaterialCardView cardOneYear = view.findViewById(R.id.cardOneYear);
        MaterialCardView cardTwoYears = view.findViewById(R.id.cardTwoYears);
        MaterialCardView cardThreeYears = view.findViewById(R.id.cardThreeYears);
        View badgeFree = view.findViewById(R.id.badgeFree);
        View badgeOneYear = view.findViewById(R.id.badgeOneYear);
        View badgeTwoYears = view.findViewById(R.id.badgeTwoYears);
        View badgeThreeYears = view.findViewById(R.id.badgeThreeYears);
        View btnContinueView = view.findViewById(R.id.btnContinueAppSubs);
        MaterialButton btnContinueAppSubs = (btnContinueView instanceof MaterialButton) ? (MaterialButton) btnContinueView : null;

        final String[] selectedPlan = {"OneYear"};

        Runnable resetCards = () -> {
            if (cardFree != null) { cardFree.animate().scaleX(1f).scaleY(1f).setDuration(200).start(); cardFree.setStrokeWidth(0); if (badgeFree != null) badgeFree.setVisibility(View.GONE); }
            if (cardOneYear != null) { cardOneYear.animate().scaleX(1f).scaleY(1f).setDuration(200).start(); cardOneYear.setStrokeWidth(0); if (badgeOneYear != null) badgeOneYear.setVisibility(View.GONE); }
            if (cardTwoYears != null) { cardTwoYears.animate().scaleX(1f).scaleY(1f).setDuration(200).start(); cardTwoYears.setStrokeWidth(0); if (badgeTwoYears != null) badgeTwoYears.setVisibility(View.GONE); }
            if (cardThreeYears != null) { cardThreeYears.animate().scaleX(1f).scaleY(1f).setDuration(200).start(); cardThreeYears.setStrokeWidth(0); if (badgeThreeYears != null) badgeThreeYears.setVisibility(View.GONE); }
        };

        if (cardFree != null) {
            cardFree.setOnClickListener(v -> {
                resetCards.run();
                cardFree.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start();
                cardFree.setStrokeWidth(6);
                cardFree.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#1877F2")));
                if (badgeFree != null) badgeFree.setVisibility(View.VISIBLE);
                selectedPlan[0] = "Free";
                if (btnContinueAppSubs != null) btnContinueAppSubs.setText(activity.getString(R.string.subscription_continue_free));
            });
        }
        if (cardOneYear != null) {
            cardOneYear.setOnClickListener(v -> {
                resetCards.run();
                cardOneYear.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start();
                cardOneYear.setStrokeWidth(6);
                cardOneYear.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#1877F2")));
                if (badgeOneYear != null) badgeOneYear.setVisibility(View.VISIBLE);
                selectedPlan[0] = "OneYear";
                if (btnContinueAppSubs != null) btnContinueAppSubs.setText(activity.getString(R.string.subscription_continue_1_year));
            });
        }
        if (cardTwoYears != null) {
            cardTwoYears.setOnClickListener(v -> {
                resetCards.run();
                cardTwoYears.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start();
                cardTwoYears.setStrokeWidth(6);
                cardTwoYears.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#1877F2")));
                if (badgeTwoYears != null) badgeTwoYears.setVisibility(View.VISIBLE);
                selectedPlan[0] = "TwoYears";
                if (btnContinueAppSubs != null) btnContinueAppSubs.setText(activity.getString(R.string.subscription_continue_2_years));
            });
        }
        if (cardThreeYears != null) {
            cardThreeYears.setOnClickListener(v -> {
                resetCards.run();
                cardThreeYears.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start();
                cardThreeYears.setStrokeWidth(6);
                cardThreeYears.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#1877F2")));
                if (badgeThreeYears != null) badgeThreeYears.setVisibility(View.VISIBLE);
                selectedPlan[0] = "ThreeYears";
                if (btnContinueAppSubs != null) btnContinueAppSubs.setText(activity.getString(R.string.subscription_continue_3_years));
            });
        }

        if (btnContinueAppSubs != null) {
            btnContinueAppSubs.setOnClickListener(v -> {
                btnContinueAppSubs.setEnabled(false);
                CreateSubscriptionRequest request = new CreateSubscriptionRequest(selectedPlan[0]);
                apiService.requestSubscription(request).enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(retrofit2.Call<ResponseBody> call, Response<ResponseBody> response) {
                        btnContinueAppSubs.setEnabled(true);
                        try (ResponseBody body = response.body()) {
                            if (response.isSuccessful() && body != null) {
                                String message = extractInstructionsMessage(body.string());
                                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                                dialog.dismiss();
                            } else {
                                Log.w("DrawerMenuHelper", "requestSubscription failed, code " + response.code());
                                String errorBody = null;
                                try {
                                    if (response.errorBody() != null) errorBody = response.errorBody().string();
                                } catch (Exception ignored) { }
                                String message = extractErrorMessage(errorBody, "Couldn't submit your subscription request. Please try again.");
                                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                            }
                        } catch (Exception e) {
                            Log.e("DrawerMenuHelper", "requestSubscription parse error", e);
                            Toast.makeText(activity, "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<ResponseBody> call, Throwable t) {
                        btnContinueAppSubs.setEnabled(true);
                        Log.e("DrawerMenuHelper", "requestSubscription network error", t);
                        Toast.makeText(activity, "Network error \u2014 check your connection and try again.", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        if (cardOneYear != null) cardOneYear.performClick();
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.show();
    }

    private static void showReportsMenuBottomSheet(AppCompatActivity activity) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_reports_menu, null);
        dialog.setContentView(view);
        ImageView btnClose = view.findViewById(R.id.btnCloseReportsMenu);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        View.OnClickListener comingSoon = v -> Toast.makeText(activity, "Coming soon", Toast.LENGTH_SHORT).show();
        int[] cardIds = {R.id.cardKmReport, R.id.cardTripReport, R.id.cardFuelReport, R.id.cardTempReport, R.id.cardAlertReport, R.id.cardFuelGraph, R.id.cardStopAlert};
        for (int id : cardIds) {
            View card = view.findViewById(id);
            if (card != null) card.setOnClickListener(comingSoon);
        }
        dialog.show();
    }

    private static void showVoiceTrackBottomSheet(AppCompatActivity activity) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_voice_track, null);
        dialog.setContentView(view);
        ImageView btnClose = view.findViewById(R.id.btnCloseVoiceTrack);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        MaterialButton btnSend = view.findViewById(R.id.btnSendAlexaVerification);
        if (btnSend != null) {
            btnSend.setOnClickListener(v -> {
                Toast.makeText(activity, "Verification email sent!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }
        dialog.show();
    }

    private static void showCallCenterBottomSheet(AppCompatActivity activity) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_call_center, null);
        dialog.setContentView(view);
        androidx.viewpager2.widget.ViewPager2 viewPager = view.findViewById(R.id.viewPagerCallCenter);
        if (viewPager != null) {
            viewPager.setAdapter(new com.example.letstracklanka.ui.vehicles.CallCenterPagerAdapter());
        }
        ImageView btnClose = view.findViewById(R.id.btnCloseCallCenter);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        View btnCloseBottom = view.findViewById(R.id.btnCallCenterClose);
        if (btnCloseBottom != null) btnCloseBottom.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static void showSettingsBottomSheet(AppCompatActivity activity) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_settings, null);
        dialog.setContentView(view);

        View btnClose = view.findViewById(R.id.btnCloseSettings);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        View btnNotifications = view.findViewById(R.id.btnSettingNotifications);
        if (btnNotifications != null) {
            btnNotifications.setOnClickListener(v -> {
                dialog.dismiss();
                activity.startActivity(new Intent(activity, SettingsActivity.class));
            });
        }

        View btnSOSSettings = view.findViewById(R.id.btnSettingSOS);
        if (btnSOSSettings != null) {
            btnSOSSettings.setOnClickListener(v -> {
                dialog.dismiss();
                activity.startActivity(new Intent(activity, EmergencyContactsActivity.class));
            });
        }

        int[] comingSoonSettingsIds = {
                R.id.btnSettingProfile,
                R.id.btnSettingDevices,
                R.id.btnSettingCircles,
                R.id.btnSettingTags,
                R.id.btnSettingReports,
                R.id.btnSettingPayments,
                R.id.btnSettingAppSettings,
                R.id.btnSettingDocWallet
        };
        for (int id : comingSoonSettingsIds) {
            View item = view.findViewById(id);
            if (item != null) {
                item.setOnClickListener(v -> Toast.makeText(activity, "Coming soon", Toast.LENGTH_SHORT).show());
            }
        }

        dialog.show();
    }

    private static String extractInstructionsMessage(String json) {
        if (json == null || json.trim().isEmpty()) return "Subscription request submitted.";
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                JsonObject data = root.getAsJsonObject("data");
                if (data.has("instructionsMessage") && !data.get("instructionsMessage").isJsonNull()) {
                    return data.get("instructionsMessage").getAsString();
                }
            }
        } catch (Exception e) {
            Log.e("DrawerMenuHelper", "extractInstructionsMessage parse error", e);
        }
        return "Subscription request submitted.";
    }

    private static String extractErrorMessage(String json, String fallback) {
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
            Log.e("DrawerMenuHelper", "extractErrorMessage parse error", e);
            return fallback;
        }
    }

    private static <T> T extractObject(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), clazz);
            }
        } catch (Exception e) {
            Log.e("DrawerMenuHelper", "extractObject error", e);
        }
        return null;
    }
}