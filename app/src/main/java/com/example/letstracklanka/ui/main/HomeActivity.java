package com.example.letstracklanka.ui.main;

import android.Manifest;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateDeviceAssignmentRequest;
import com.example.letstracklanka.data.model.CreateVehicleRequest;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.UpdateCustomerRequest;
import com.example.letstracklanka.data.model.RegisterFcmTokenRequest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.messaging.FirebaseMessaging;
import com.example.letstracklanka.data.model.DashboardResponse;
import com.example.letstracklanka.data.model.GpsDeviceResponse;
import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.ui.main.AddressResolver;
import com.example.letstracklanka.ui.vehicles.VehiclesActivity;
import com.example.letstracklanka.ui.auth.LoginActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int UPDATE_INTERVAL = 1000;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private ShaloTrackApi trackingApi;
    private ApiService mainApiService;
    private final Handler handler = new Handler();
    private Runnable trackingRunnable;

    private String currentCustomerId = null;
    private CustomerResponse currentCustomer = null;
    private final Map<String, String> myVehicles = new HashMap<>();
    private final Map<String, Marker> mapMarkers = new HashMap<>();
    private LatLng myCurrentLocation = null;

    private TextView tvDeviceStatus, tvDeviceAddress, tvDeviceName;
    private MaterialCardView cardDefault, cardTerrain, cardSatellite, cardHybrid;
    private View mapTypeMenu;
    private VehicleTrailRenderer trailRenderer;
    private RealtimeLocationClient realtimeClient;
    private AddressResolver addressResolver;

    // App Subscription Tracker
    private boolean isMonthlyBilling = false;

    // Drawer Variables
    private DrawerLayout drawerLayout;
    private TextView tvDrawerName, tvDrawerPhone, tvDrawerEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermissionIfNeeded();
        setContentView(R.layout.activity_home);

        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);
        addressResolver = new AddressResolver(this);
        mainApiService = ApiClient.getClient().create(ApiService.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        setupUI();
        enableMyLocation();

        startRealTimeTracking();
        loadUserData();

        // Detect a signal from another screen and automatically open the drawer menu
        if (getIntent().getBooleanExtra("open_drawer", false)) {
            if (drawerLayout != null) {
                drawerLayout.post(() -> drawerLayout.openDrawer(GravityCompat.START));
            }
        }
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        tvDrawerName = findViewById(R.id.tvDrawerName);
        tvDrawerPhone = findViewById(R.id.tvDrawerPhone);
        tvDrawerEmail = findViewById(R.id.tvDrawerEmail);

        TextView tvLogOut = findViewById(R.id.tvLogOut);
        if(tvLogOut != null) {
            tvLogOut.setOnClickListener(v -> {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }

        View bottomSheetView = findViewById(R.id.bottomSheet);
        if (bottomSheetView != null) {
            tvDeviceName = bottomSheetView.findViewById(R.id.tvDeviceName);
            tvDeviceStatus = bottomSheetView.findViewById(R.id.tvDeviceStatus);
            tvDeviceAddress = bottomSheetView.findViewById(R.id.tvDeviceAddress);
        }

        cardDefault = findViewById(R.id.cardDefault);
        cardTerrain = findViewById(R.id.cardTerrain);
        cardSatellite = findViewById(R.id.cardSatellite);
        cardHybrid = findViewById(R.id.cardHybrid);
        mapTypeMenu = findViewById(R.id.mapTypeMenu);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        ImageView ivEditProfileMenu = findViewById(R.id.ivEditProfileMenu);
        if (ivEditProfileMenu != null) {
            ivEditProfileMenu.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showEditProfileBottomSheet();
            });
        }

        setupDrawerMenuItems();
    }

    private void setupDrawerMenuItems() {
        View btnMenuAddNew = findViewById(R.id.btnMenuAddNew);
        if (btnMenuAddNew != null) {
            btnMenuAddNew.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showAddVehicleDialog();
            });
        }

        View btnMenuReports = findViewById(R.id.btnMenuReports);
        if (btnMenuReports != null) {
            btnMenuReports.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(HomeActivity.this, com.example.letstracklanka.ui.history.TripHistoryActivity.class));
            });
        }

        View btnMenuChatSupport = findViewById(R.id.btnMenuChatSupport);
        if (btnMenuChatSupport != null) {
            btnMenuChatSupport.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showCallCenterBottomSheet();
            });
        }

        View btnMenuVoiceTrack = findViewById(R.id.btnMenuVoiceTrack);
        if (btnMenuVoiceTrack != null) {
            btnMenuVoiceTrack.setOnClickListener(v ->
                    Toast.makeText(this, "Not available for this app", Toast.LENGTH_SHORT).show());
        }

        View menuPlaces = findViewById(R.id.btnMenuPlaces);
        if (menuPlaces != null) {
            menuPlaces.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showPlacesBottomSheet();
            });
        }

        View menuVehicleSubs = findViewById(R.id.btnMenuVehicleSubs);
        if (menuVehicleSubs != null) {
            menuVehicleSubs.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showDevicesToRenewBottomSheet();
            });
        }

        View menuAppSubs = findViewById(R.id.btnMenuAppSubs);
        if (menuAppSubs != null) {
            menuAppSubs.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showAppSubscriptionBottomSheet();
            });
        }

        int[] comingSoonIds = {
                R.id.btnMenuRefer,
                R.id.btnMenuShop,
                R.id.btnMenuHelpVideos,
                R.id.btnMenuSettings,
                R.id.btnMenuPrivacy
        };
        for (int id : comingSoonIds) {
            View item = findViewById(id);
            if (item != null) {
                item.setOnClickListener(v -> Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show());
            }
        }
    }

    private void setupUI() {
        NestedScrollView bottomSheet = findViewById(R.id.bottomSheet);
        if (bottomSheet != null) BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_COLLAPSED);

        FloatingActionButton fabLayers = findViewById(R.id.fabLayers);
        if (fabLayers != null) {
            fabLayers.setOnClickListener(v -> {
                if (mapTypeMenu != null) {
                    if (mapTypeMenu.getVisibility() == View.VISIBLE) {
                        mapTypeMenu.setVisibility(View.GONE);
                    } else {
                        mapTypeMenu.setVisibility(View.VISIBLE);
                    }
                }
            });
        }

        findViewById(R.id.nav_vehicles).setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, VehiclesActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        View btnAddVehicle = findViewById(R.id.btnAddVehicle);
        if (btnAddVehicle != null) {
            btnAddVehicle.setOnClickListener(v -> showAddVehicleDialog());
        }

        if (cardDefault != null) cardDefault.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_NORMAL, cardDefault));
        if (cardTerrain != null) cardTerrain.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_TERRAIN, cardTerrain));
        if (cardSatellite != null) cardSatellite.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_SATELLITE, cardSatellite));
        if (cardHybrid != null) cardHybrid.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_HYBRID, cardHybrid));

        FloatingActionButton fabLocation = findViewById(R.id.fabLocation);
        if (fabLocation != null) fabLocation.setOnClickListener(v -> getPhoneLocation());

        MaterialButton btnHomeSOS = findViewById(R.id.btnSOS);
        if (btnHomeSOS != null) btnHomeSOS.setOnClickListener(v -> showSOSBottomSheet());

        LinearLayout bottomNavBar = findViewById(R.id.bottomNavBar);
        if (bottomNavBar != null) {
            View navTags = findViewById(R.id.nav_tags);
            if (navTags != null) {
                navTags.setOnClickListener(v -> {
                    Intent intent = new Intent(HomeActivity.this, TagsActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }

            View navCircles = findViewById(R.id.nav_circles);
            if (navCircles != null) {
                navCircles.setOnClickListener(v -> {
                    Intent intent = new Intent(HomeActivity.this, CirclesActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }

            View navAlerts = findViewById(R.id.nav_alerts);
            if (navAlerts != null) {
                navAlerts.setOnClickListener(v -> {
                    Intent intent = new Intent(HomeActivity.this, AlertsActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }

            View navMenu = findViewById(R.id.nav_menu);
            if (navMenu != null) {
                navMenu.setOnClickListener(v -> {
                    if (drawerLayout != null) {
                        drawerLayout.openDrawer(GravityCompat.START);
                    }
                });
            }
        }
    }

    private void showEditProfileBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_edit_profile, null);
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
                for (int i = 1; i < nameParts.length; i++) {
                    surname.append(nameParts[i]).append(" ");
                }
                etSurname.setText(surname.toString().trim());
            }
        }
        if (tvDrawerPhone != null) etPhone.setText(tvDrawerPhone.getText().toString());
        if (tvDrawerEmail != null) etEmail.setText(tvDrawerEmail.getText().toString());

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            if (currentCustomer == null || currentCustomerId == null) {
                Toast.makeText(this, "Profile not loaded yet, try again in a moment", Toast.LENGTH_SHORT).show();
                return;
            }

            String firstName = etFirstName.getText().toString().trim();
            String surname = etSurname.getText().toString().trim();
            String fullName = surname.isEmpty() ? firstName : firstName + " " + surname;
            String phone = etPhone.getText().toString().trim();

            if (fullName.isEmpty()) {
                Toast.makeText(this, "Name can be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            btnSave.setEnabled(false);

            UpdateCustomerRequest request = new UpdateCustomerRequest(
                    fullName,
                    phone,
                    currentCustomer.getAddress(),
                    currentCustomer.getProfileImage()
            );

            mainApiService.updateCustomer(currentCustomerId, request).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    btnSave.setEnabled(true);
                    if (response.isSuccessful()) {
                        Toast.makeText(HomeActivity.this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                        if (tvDrawerName != null) tvDrawerName.setText(fullName);
                        if (tvDrawerPhone != null) tvDrawerPhone.setText(phone);
                        dialog.dismiss();
                    } else {
                        Toast.makeText(HomeActivity.this, "Could not save (code " + response.code() + ")", Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    btnSave.setEnabled(true);
                    Toast.makeText(HomeActivity.this, "Network error — check your connection", Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.show();
    }

    private void showPlacesBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_places, null);
        dialog.setContentView(view);

        ImageView btnClose = view.findViewById(R.id.btnClosePlaces);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.show();
    }

    private void showDevicesToRenewBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_devices_to_renew, null);
        dialog.setContentView(view);

        ImageView btnClose = view.findViewById(R.id.btnCloseRenew);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        MaterialButton btnShopNow = view.findViewById(R.id.btnShopNow);
        if (btnShopNow != null) {
            btnShopNow.setOnClickListener(v -> {
                Toast.makeText(this, "Opening Shop...", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    // ---------------------------------------------------------
    // App Subscription Bottom Sheet with Animation & Dynamic Table
    // ---------------------------------------------------------
    private void showAppSubscriptionBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_app_subscription, null);
        dialog.setContentView(view);

        ImageView btnClose = view.findViewById(R.id.btnCloseAppSubs);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        // Toggle Buttons
        MaterialCardView cardTabAnnually = view.findViewById(R.id.cardTabAnnually);
        TextView tvTabAnnually = view.findViewById(R.id.tvTabAnnually);
        MaterialCardView cardTabMonthly = view.findViewById(R.id.cardTabMonthly);
        TextView tvTabMonthly = view.findViewById(R.id.tvTabMonthly);

        // Plan Cards
        MaterialCardView cardFree = view.findViewById(R.id.cardFree);
        MaterialCardView cardSilver = view.findViewById(R.id.cardSilver);
        MaterialCardView cardGold = view.findViewById(R.id.cardGold);
        MaterialCardView cardPlatinum = view.findViewById(R.id.cardPlatinum);

        // Card Prices
        TextView tvPriceFree = view.findViewById(R.id.tvPriceFree);
        TextView tvPriceSilver = view.findViewById(R.id.tvPriceSilver);
        TextView tvPriceGold = view.findViewById(R.id.tvPriceGold);
        TextView tvPricePlatinum = view.findViewById(R.id.tvPricePlatinum);

        // Badges
        View badgeFree = view.findViewById(R.id.badgeFree);
        View badgeSilver = view.findViewById(R.id.badgeSilver);
        View badgeGold = view.findViewById(R.id.badgeGold);
        View badgePlatinum = view.findViewById(R.id.badgePlatinum);

        // Table Elements
        TextView tvColSelected = view.findViewById(R.id.tvColSelected);
        TextView tvColNext = view.findViewById(R.id.tvColNext);

        TextView tvVal1Row1 = view.findViewById(R.id.tvVal1Row1);
        TextView tvVal2Row1 = view.findViewById(R.id.tvVal2Row1);
        TextView tvVal1Row2 = view.findViewById(R.id.tvVal1Row2);
        TextView tvVal2Row2 = view.findViewById(R.id.tvVal2Row2);
        TextView tvVal1Row3 = view.findViewById(R.id.tvVal1Row3);
        TextView tvVal2Row3 = view.findViewById(R.id.tvVal2Row3);
        TextView tvVal1Row4 = view.findViewById(R.id.tvVal1Row4);
        TextView tvVal2Row4 = view.findViewById(R.id.tvVal2Row4);

        View btnContinueView = view.findViewById(R.id.btnContinueAppSubs);
        MaterialButton btnContinueAppSubs = (btnContinueView instanceof MaterialButton) ? (MaterialButton) btnContinueView : null;

        // Toggle Price Update Logic
        Runnable updatePrices = () -> {
            if (isMonthlyBilling) {
                if (tvPriceFree != null) tvPriceFree.setText("L0/month\nL0/year");
                if (tvPriceSilver != null) tvPriceSilver.setText("LKR 1,004.75/month\nor LKR 999.00/year");
                if (tvPriceGold != null) tvPriceGold.setText("LKR 349.00/month\nor LKR 3,490.00/year");
                if (tvPricePlatinum != null) tvPricePlatinum.setText("LKR 469.00/month\nor LKR 4,690.00/year");
            } else {
                if (tvPriceFree != null) tvPriceFree.setText("L0/year");
                if (tvPriceSilver != null) tvPriceSilver.setText("LKR 999.00/year");
                if (tvPriceGold != null) tvPriceGold.setText("LKR 3,490.00/year");
                if (tvPricePlatinum != null) tvPricePlatinum.setText("LKR 4,690.00/year");
            }
        };

        if (cardTabAnnually != null && cardTabMonthly != null) {
            cardTabAnnually.setOnClickListener(v -> {
                if (!isMonthlyBilling) return;
                isMonthlyBilling = false;
                cardTabAnnually.setCardBackgroundColor(Color.parseColor("#1877F2"));
                cardTabAnnually.setCardElevation(2f);
                if (tvTabAnnually != null) tvTabAnnually.setTextColor(Color.WHITE);

                cardTabMonthly.setCardBackgroundColor(Color.TRANSPARENT);
                cardTabMonthly.setCardElevation(0f);
                if (tvTabMonthly != null) tvTabMonthly.setTextColor(Color.parseColor("#9E9E9E"));
                updatePrices.run();
            });

            cardTabMonthly.setOnClickListener(v -> {
                if (isMonthlyBilling) return;
                isMonthlyBilling = true;
                cardTabMonthly.setCardBackgroundColor(Color.parseColor("#1877F2"));
                cardTabMonthly.setCardElevation(2f);
                if (tvTabMonthly != null) tvTabMonthly.setTextColor(Color.WHITE);

                cardTabAnnually.setCardBackgroundColor(Color.TRANSPARENT);
                cardTabAnnually.setCardElevation(0f);
                if (tvTabAnnually != null) tvTabAnnually.setTextColor(Color.parseColor("#9E9E9E"));
                updatePrices.run();
            });
        }

        // Reset Card States
        Runnable resetCards = () -> {
            if (cardFree != null) { cardFree.animate().scaleX(1f).scaleY(1f).setDuration(200).start(); cardFree.setStrokeWidth(0); if(badgeFree != null) badgeFree.setVisibility(View.GONE); }
            if (cardSilver != null) { cardSilver.animate().scaleX(1f).scaleY(1f).setDuration(200).start(); cardSilver.setStrokeWidth(0); if(badgeSilver != null) badgeSilver.setVisibility(View.GONE); }
            if (cardGold != null) { cardGold.animate().scaleX(1f).scaleY(1f).setDuration(200).start(); cardGold.setStrokeWidth(0); if(badgeGold != null) badgeGold.setVisibility(View.GONE); }
            if (cardPlatinum != null) { cardPlatinum.animate().scaleX(1f).scaleY(1f).setDuration(200).start(); cardPlatinum.setStrokeWidth(0); if(badgePlatinum != null) badgePlatinum.setVisibility(View.GONE); }
        };

        // Click Free Plan
        if (cardFree != null) {
            cardFree.setOnClickListener(v -> {
                resetCards.run();
                cardFree.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start();
                cardFree.setStrokeWidth(6);
                cardFree.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#1877F2")));
                if (badgeFree != null) badgeFree.setVisibility(View.VISIBLE);

                if (tvColSelected != null) tvColSelected.setText("Free");
                if (tvColNext != null) tvColNext.setText("Silver");

                if (tvVal1Row1 != null) { tvVal1Row1.setText("🔒"); tvVal1Row1.setTextColor(Color.parseColor("#9E9E9E")); }
                if (tvVal2Row1 != null) { tvVal2Row1.setText("✔️"); tvVal2Row1.setTextColor(Color.parseColor("#4CAF50")); }

                if (tvVal1Row2 != null) { tvVal1Row2.setText("🔒"); tvVal1Row2.setTextColor(Color.parseColor("#9E9E9E")); }
                if (tvVal2Row2 != null) { tvVal2Row2.setText("Message with\nlocation"); tvVal2Row2.setTextColor(Color.parseColor("#9E9E9E")); }

                if (tvVal1Row3 != null) { tvVal1Row3.setText("1"); tvVal1Row3.setTextColor(Color.parseColor("#555555")); }
                if (tvVal2Row3 != null) { tvVal2Row3.setText("Up to 2"); tvVal2Row3.setTextColor(Color.parseColor("#9E9E9E")); }

                if (tvVal1Row4 != null) { tvVal1Row4.setText("🔒"); tvVal1Row4.setTextColor(Color.parseColor("#9E9E9E")); }
                if (tvVal2Row4 != null) { tvVal2Row4.setText("✔️"); tvVal2Row4.setTextColor(Color.parseColor("#4CAF50")); }

                if (btnContinueAppSubs != null) btnContinueAppSubs.setText("Active Plan");
            });
        }

        // Click Silver Plan
        if (cardSilver != null) {
            cardSilver.setOnClickListener(v -> {
                resetCards.run();
                cardSilver.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start();
                cardSilver.setStrokeWidth(6);
                cardSilver.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#1877F2")));
                if (badgeSilver != null) badgeSilver.setVisibility(View.VISIBLE);

                if (tvColSelected != null) tvColSelected.setText("Silver");
                if (tvColNext != null) tvColNext.setText("Gold");

                if (tvVal1Row1 != null) { tvVal1Row1.setText("✔️"); tvVal1Row1.setTextColor(Color.parseColor("#4CAF50")); }
                if (tvVal2Row1 != null) { tvVal2Row1.setText("✔️"); tvVal2Row1.setTextColor(Color.parseColor("#4CAF50")); }

                if (tvVal1Row2 != null) { tvVal1Row2.setText("Message with\nlocation"); tvVal1Row2.setTextColor(Color.parseColor("#555555")); }
                if (tvVal2Row2 != null) { tvVal2Row2.setText("Message with\nlocation"); tvVal2Row2.setTextColor(Color.parseColor("#9E9E9E")); }

                if (tvVal1Row3 != null) { tvVal1Row3.setText("Up to 2"); tvVal1Row3.setTextColor(Color.parseColor("#555555")); }
                if (tvVal2Row3 != null) { tvVal2Row3.setText("Up to 3 people"); tvVal2Row3.setTextColor(Color.parseColor("#9E9E9E")); }

                if (tvVal1Row4 != null) { tvVal1Row4.setText("✔️"); tvVal1Row4.setTextColor(Color.parseColor("#4CAF50")); }
                if (tvVal2Row4 != null) { tvVal2Row4.setText("✔️"); tvVal2Row4.setTextColor(Color.parseColor("#4CAF50")); }

                if (btnContinueAppSubs != null) btnContinueAppSubs.setText("Continue");
            });
        }

        // Click Gold Plan
        if (cardGold != null) {
            cardGold.setOnClickListener(v -> {
                resetCards.run();
                cardGold.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start();
                cardGold.setStrokeWidth(6);
                cardGold.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#1877F2")));
                if (badgeGold != null) badgeGold.setVisibility(View.VISIBLE);

                if (tvColSelected != null) tvColSelected.setText("Gold");
                if (tvColNext != null) tvColNext.setText("Platinum");

                if (tvVal1Row1 != null) { tvVal1Row1.setText("✔️"); tvVal1Row1.setTextColor(Color.parseColor("#4CAF50")); }
                if (tvVal2Row1 != null) { tvVal2Row1.setText("✔️"); tvVal2Row1.setTextColor(Color.parseColor("#4CAF50")); }

                if (tvVal1Row2 != null) { tvVal1Row2.setText("Message with\nlocation"); tvVal1Row2.setTextColor(Color.parseColor("#555555")); }
                if (tvVal2Row2 != null) { tvVal2Row2.setText("Call and\nMessage"); tvVal2Row2.setTextColor(Color.parseColor("#9E9E9E")); }

                if (tvVal1Row3 != null) { tvVal1Row3.setText("Up to 3 people"); tvVal1Row3.setTextColor(Color.parseColor("#555555")); }
                if (tvVal2Row3 != null) { tvVal2Row3.setText("Up to 5 people"); tvVal2Row3.setTextColor(Color.parseColor("#9E9E9E")); }

                if (tvVal1Row4 != null) { tvVal1Row4.setText("✔️"); tvVal1Row4.setTextColor(Color.parseColor("#4CAF50")); }
                if (tvVal2Row4 != null) { tvVal2Row4.setText("✔️"); tvVal2Row4.setTextColor(Color.parseColor("#4CAF50")); }

                if (btnContinueAppSubs != null) btnContinueAppSubs.setText("Continue");
            });
        }

        // Click Platinum Plan
        if (cardPlatinum != null) {
            cardPlatinum.setOnClickListener(v -> {
                resetCards.run();
                cardPlatinum.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start();
                cardPlatinum.setStrokeWidth(6);
                cardPlatinum.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#1877F2")));
                if (badgePlatinum != null) badgePlatinum.setVisibility(View.VISIBLE);

                if (tvColSelected != null) tvColSelected.setText("Platinum");
                if (tvColNext != null) tvColNext.setText("-");

                if (tvVal1Row1 != null) { tvVal1Row1.setText("✔️"); tvVal1Row1.setTextColor(Color.parseColor("#4CAF50")); }
                if (tvVal2Row1 != null) { tvVal2Row1.setText("-"); tvVal2Row1.setTextColor(Color.parseColor("#4CAF50")); }

                if (tvVal1Row2 != null) { tvVal1Row2.setText("Call and\nMessage"); tvVal1Row2.setTextColor(Color.parseColor("#555555")); }
                if (tvVal2Row2 != null) { tvVal2Row2.setText("-"); }

                if (tvVal1Row3 != null) { tvVal1Row3.setText("Up to 5 people"); tvVal1Row3.setTextColor(Color.parseColor("#555555")); }
                if (tvVal2Row3 != null) { tvVal2Row3.setText("-"); }

                if (tvVal1Row4 != null) { tvVal1Row4.setText("✔️"); tvVal1Row4.setTextColor(Color.parseColor("#4CAF50")); }
                if (tvVal2Row4 != null) { tvVal2Row4.setText("-"); }

                if (btnContinueAppSubs != null) btnContinueAppSubs.setText("Continue");
            });
        }

        if (btnContinueAppSubs != null) {
            btnContinueAppSubs.setOnClickListener(v -> {
                Toast.makeText(this, "Proceeding...", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }

        // Initialize default view
        if (cardFree != null) cardFree.performClick();
        updatePrices.run();

        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.show();
    }

    private void showCallCenterBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_call_center, null);
        dialog.setContentView(view);

        ViewPager2 viewPager = view.findViewById(R.id.viewPagerCallCenter);
        if (viewPager != null) {
            viewPager.setAdapter(new com.example.letstracklanka.ui.vehicles.CallCenterPagerAdapter());
        }

        ImageView btnClose = view.findViewById(R.id.btnCloseCallCenter);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        View btnCloseBottom = view.findViewById(R.id.btnCallCenterClose);
        if (btnCloseBottom != null) btnCloseBottom.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showAddVehicleDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_vehicle, null);
        EditText etVehicleNumber = dialogView.findViewById(R.id.etVehicleNumber);
        EditText etMake = dialogView.findViewById(R.id.etMake);
        EditText etModel = dialogView.findViewById(R.id.etModel);
        EditText etYear = dialogView.findViewById(R.id.etYear);
        EditText etChassis = dialogView.findViewById(R.id.etChassisNumber);
        EditText etEngine = dialogView.findViewById(R.id.etEngineNumber);
        EditText etColor = dialogView.findViewById(R.id.etColor);
        EditText etType = dialogView.findViewById(R.id.etVehicleType);
        EditText etFuel = dialogView.findViewById(R.id.etFuelType);
        EditText etImei = dialogView.findViewById(R.id.etImei);

        new AlertDialog.Builder(this)
                .setTitle("Register My GPS Vehicle")
                .setView(dialogView)
                .setPositiveButton("Link Device", (dialog, which) -> {
                    String vNum = etVehicleNumber.getText().toString().trim();
                    String make = etMake.getText().toString().trim();
                    String model = etModel.getText().toString().trim();
                    String yearStr = etYear.getText().toString().trim();
                    String imei = etImei.getText().toString().trim();
                    String chassis = etChassis.getText().toString().trim();
                    String engine = etEngine.getText().toString().trim();
                    String color = etColor.getText().toString().trim();
                    String type = etType.getText().toString().trim();
                    String fuel = etFuel.getText().toString().trim();

                    if (vNum.isEmpty() || imei.isEmpty()) {
                        Toast.makeText(this, "Vehicle Number and IMEI are required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int year = yearStr.isEmpty() ? 2024 : Integer.parseInt(yearStr);

                    Toast.makeText(this, "Linking Hardware...", Toast.LENGTH_SHORT).show();
                    processVehicleAddition(vNum, chassis, engine, make, model, year, color, type, fuel, imei);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void processVehicleAddition(String vNum, String chassis, String engine, String make, String model,
                                        int year, String color, String type, String fuel, String imei) {

        mainApiService.getGpsDevices().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<GpsDeviceResponse> devices = parseList(body.string(), GpsDeviceResponse.class);
                        GpsDeviceResponse targetDevice = null;
                        for (GpsDeviceResponse device : devices) {
                            if (imei.equalsIgnoreCase(device.getImeiNumber())) {
                                targetDevice = device;
                                break;
                            }
                        }
                        if (targetDevice != null) {
                            createVehicle(vNum, chassis, engine, make, model, year, color, type, fuel, targetDevice.getDeviceId());
                        } else {
                            Toast.makeText(HomeActivity.this, "IMEI not found in registry", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(HomeActivity.this,
                                "Could not check device registry (code " + response.code() + ")",
                                Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Log.e("HomeActivity", "Error finding device", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(HomeActivity.this, "Registry Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createVehicle(String vNum, String chassis, String engine, String make, String model,
                               int year, String color, String type, String fuel, String deviceId) {
        if (currentCustomerId == null) {
            Toast.makeText(this, "Your profile isn't loaded yet. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }
        CreateVehicleRequest request = new CreateVehicleRequest(currentCustomerId, vNum, chassis, engine, make, model, year, color, type, fuel);
        mainApiService.createVehicle(request).enqueue(new Callback<VehicleResponse>() {
            @Override
            public void onResponse(@NonNull Call<VehicleResponse> call, @NonNull Response<VehicleResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    assignDeviceToVehicle(response.body().getVehicleId(), deviceId);
                } else {
                    Toast.makeText(HomeActivity.this,
                            "Could not create vehicle (code " + response.code() + ")",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<VehicleResponse> call, @NonNull Throwable t) {
                Toast.makeText(HomeActivity.this, "Network error creating vehicle", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void assignDeviceToVehicle(String vehicleId, String deviceId) {
        mainApiService.assignDevice(new CreateDeviceAssignmentRequest(vehicleId, deviceId)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(HomeActivity.this, "Vehicle Linked to DB!", Toast.LENGTH_LONG).show();
                    loadUserData();
                } else {
                    Toast.makeText(HomeActivity.this,
                            "Could not assign device (code " + response.code() + ")",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(HomeActivity.this, "Network error assigning device", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSOSBottomSheet() {
        BottomSheetDialog sosDialog = new BottomSheetDialog(this);
        View sosView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_sos, null);
        sosDialog.setContentView(sosView);

        ImageView btnClose = sosView.findViewById(R.id.btnCloseSOS);
        LinearLayout btnTapSOS = sosView.findViewById(R.id.btnTapSOS);

        btnClose.setOnClickListener(v -> sosDialog.dismiss());

        btnTapSOS.setOnClickListener(v -> {
            sosDialog.dismiss();

            if (myCurrentLocation != null) {
                String locationLink = "https://www.google.com/maps?q=" + myCurrentLocation.latitude + "," + myCurrentLocation.longitude;
                String message = "EMERGENCY SOS!\nI need help. Here is my current location:\n" + locationLink;

                Intent smsIntent = new Intent(Intent.ACTION_VIEW);
                smsIntent.setType("vnd.android-dir/mms-sms");
                smsIntent.putExtra("sms_body", message);
                startActivity(smsIntent);

                Toast.makeText(this, "Opening SMS to send SOS...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Trying to find your location...", Toast.LENGTH_SHORT).show();
                getDeviceLocation();
            }
        });
        sosDialog.show();
    }

    private void startRealTimeTracking() {
        if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable);
        trackingRunnable = new Runnable() {
            @Override
            public void run() {
                fetchLocation();
                if (currentCustomerId != null) fetchDashboard();
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        handler.post(trackingRunnable);
    }

    private void fetchDashboard() {
        if (currentCustomerId == null) return;
        mainApiService.getCustomerDashboard(currentCustomerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) return;

                    Gson gson = new Gson();
                    JsonObject root = gson.fromJson(body.string(), JsonObject.class);
                    if (root == null || !root.has("data") || root.get("data").isJsonNull()) return;

                    JsonObject data = root.getAsJsonObject("data");
                    if (!data.has("vehicles") || !data.get("vehicles").isJsonArray()) return;

                    for (com.google.gson.JsonElement el : data.getAsJsonArray("vehicles")) {
                        JsonObject v = el.getAsJsonObject();
                        if (!v.has("vehicleId") || v.get("vehicleId").isJsonNull()) continue;

                        String vehicleId = v.get("vehicleId").getAsString().toLowerCase();
                        String make = v.has("make") && !v.get("make").isJsonNull() ? v.get("make").getAsString() : "";
                        String model = v.has("model") && !v.get("model").isJsonNull() ? v.get("model").getAsString() : "";
                        myVehicles.put(vehicleId, (make + " " + model).trim());

                        boolean hasLocation = v.has("latitude") && !v.get("latitude").isJsonNull()
                                && v.has("longitude") && !v.get("longitude").isJsonNull();
                        if (hasLocation && mMap != null) {
                            double lat = v.get("latitude").getAsDouble();
                            double lng = v.get("longitude").getAsDouble();
                            if (lat != 0 || lng != 0) {
                                updateMarker(vehicleId, new LatLng(lat, lng), myVehicles.get(vehicleId));
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e("HomeActivity", "Dashboard error", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
            }
        });
    }

    private void handlePushedLocation(RealtimeLocationPayload payload) {
        if (payload.getVehicleId() == null || mMap == null) return;
        LatLng pos = new LatLng(payload.getLatitude(), payload.getLongitude());
        if (pos.latitude == 0 && pos.longitude == 0) return;

        String title = myVehicles.getOrDefault(payload.getVehicleId().toLowerCase(), "My Vehicle");
        trailRenderer.updatePosition(pos, (float) payload.getHeading(), title);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));

        if (tvDeviceName != null) tvDeviceName.setText(title);

        addressResolver.resolveAddress(pos.latitude, pos.longitude, address -> {
            if (tvDeviceAddress != null) tvDeviceAddress.setText(address);
        });

        String status = payload.getSpeed() > 0
                ? "Moving (" + (int) payload.getSpeed() + " km/h)"
                : (payload.isIgnitionOn() ? "Idle" : "Parked");
        int color = payload.getSpeed() > 0 ? ContextCompat.getColor(this, com.example.letstracklanka.R.color.status_moving) : ContextCompat.getColor(this, com.example.letstracklanka.R.color.brand_primary);
        if (tvDeviceStatus != null) {
            tvDeviceStatus.setText(status);
            tvDeviceStatus.setTextColor(color);
        }
    }

    private void fetchLocation() {
        if (myVehicles.isEmpty()) {
            return;
        }

        for (String vehicleId : myVehicles.keySet()) {
            trackingApi.getVehicleLocation(vehicleId).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    try (ResponseBody body = response.body()) {
                        if (response.isSuccessful() && body != null && mMap != null) {
                            String json = body.string();
                            LocationResponse loc = extractLocation(json);
                            if (loc != null && loc.getVehicleId() != null) {
                                LatLng pos = new LatLng(loc.getLatitude(), loc.getLongitude());
                                if (pos.latitude != 0 || pos.longitude != 0) {
                                    String title = myVehicles.getOrDefault(vehicleId, "My Vehicle");
                                    trailRenderer.updatePosition(pos, loc.getHeading(), title);
                                    updateUI(loc);
                                }
                            }
                        } else if (response.code() == 404) {
                            Log.d("HomeActivity", "No current location yet for vehicle " + vehicleId);
                        }
                    } catch (Exception e) {
                        Log.e("HomeActivity", "Location parse error for " + vehicleId, e);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    Log.e("HomeActivity", "Location fetch failed for " + vehicleId, t);
                }
            });
        }
    }

    private LocationResponse extractLocation(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), LocationResponse.class);
            }
            return gson.fromJson(json, LocationResponse.class);
        } catch (Exception e) {
            Log.e("HomeActivity", "extractLocation parse error", e);
            return null;
        }
    }

    private void updateMarker(String id, LatLng pos, String title) {
        if (mMap == null) return;
        if (mapMarkers.containsKey(id)) {
            Marker m = mapMarkers.get(id);
            if (m != null) m.setPosition(pos);
        } else {
            Marker m = mMap.addMarker(new MarkerOptions().position(pos).title(title));
            mapMarkers.put(id, m);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 14f));
        }
    }

    private void updateUI(LocationResponse loc) {
        String vid = loc.getVehicleId().toLowerCase();
        String name = myVehicles.getOrDefault(vid, "My Vehicle");
        if (tvDeviceName != null) tvDeviceName.setText(name);
        if (tvDeviceAddress != null) {
            addressResolver.resolveAddress(loc.getLatitude(), loc.getLongitude(), address ->
                    tvDeviceAddress.setText(address));
        }
        if (tvDeviceStatus != null) {
            tvDeviceStatus.setText(loc.getSpeed() > 0 ? "Moving (" + (int) loc.getSpeed() + " km/h)" : (loc.isIgnitionOn() ? "Idle" : "Parked"));
            tvDeviceStatus.setTextColor(loc.getSpeed() > 0 ? ContextCompat.getColor(this, com.example.letstracklanka.R.color.status_moving) : ContextCompat.getColor(this, com.example.letstracklanka.R.color.brand_primary));
        }
    }

    /**
     * Android 13+ requires runtime permission to show any notification at all.
     * Harmless no-op on older versions -- the check itself prevents the request
     * from firing where it isn't needed or supported.
     */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }

    /**
     * Explicitly fetches and registers the current FCM token on every successful
     * login -- not relying solely on FirebaseMessagingService.onNewToken(), which
     * only fires when Firebase generates or rotates a token, not on every app
     * open. This ensures the backend always has a fresh, correct token for
     * whichever customer is currently logged in.
     */
    private void registerFcmToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w("HomeActivity", "Fetching FCM token failed", task.getException());
                return;
            }
            String token = task.getResult();
            mainApiService.registerFcmToken(new RegisterFcmTokenRequest(token, "android"))
                    .enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                            if (!response.isSuccessful()) {
                                Log.w("HomeActivity", "FCM token registration failed, code " + response.code());
                            }
                        }
                        @Override
                        public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                            Log.w("HomeActivity", "FCM token registration network error", t);
                        }
                    });
        });
    }

    private void loadUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        mainApiService.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        String json = body.string();

                        // Populate the drawer menu with the profile details
                        try {
                            Gson gson = new Gson();
                            JsonObject root = gson.fromJson(json, JsonObject.class);
                            JsonObject data = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : root;

                            String name = data.has("name") && !data.get("name").isJsonNull() ? data.get("name").getAsString() :
                                    (data.has("fullName") && !data.get("fullName").isJsonNull() ? data.get("fullName").getAsString() : "Unknown User");

                            String phone = data.has("phone") && !data.get("phone").isJsonNull() ? data.get("phone").getAsString() :
                                    (data.has("phoneNumber") && !data.get("phoneNumber").isJsonNull() ? data.get("phoneNumber").getAsString() : "No Phone Number");

                            String email = data.has("email") && !data.get("email").isJsonNull() ? data.get("email").getAsString() : "No Email";

                            if(tvDrawerName != null) tvDrawerName.setText(name);
                            if(tvDrawerPhone != null) tvDrawerPhone.setText(phone);
                            if(tvDrawerEmail != null) tvDrawerEmail.setText(email);
                        } catch(Exception e) {
                            Log.e("HomeActivity", "Drawer UI update error", e);
                        }

                        CustomerResponse customer = extractCustomer(json);
                        if (customer != null && customer.getCustomerId() != null) {
                            currentCustomerId = customer.getCustomerId();
                            currentCustomer = customer;
                            registerFcmToken();   // NEW -- ensure the token is registered every login, not just on first-ever generation
                            fetchMyVehicles();
                            fetchDashboard();
                        }
                    } else if (response.code() == 404) {
                        Log.w("HomeActivity", "No customer profile exists yet for this account.");
                    } else {
                        Log.w("HomeActivity", "getMyProfile failed with code " + response.code());
                    }
                } catch (Exception e) {
                    Log.e("HomeActivity", "loadUserData parse error", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("HomeActivity", "loadUserData network error", t);
            }
        });
    }

    private CustomerResponse extractCustomer(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), CustomerResponse.class);
            }
            return gson.fromJson(json, CustomerResponse.class);
        } catch (Exception e) {
            Log.e("HomeActivity", "extractCustomer parse error", e);
            return null;
        }
    }

    private void fetchMyVehicles() {
        if (currentCustomerId == null) return;
        mainApiService.getVehiclesByCustomer(currentCustomerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<VehicleResponse> list = parseList(body.string(), VehicleResponse.class);
                        myVehicles.clear();
                        for (VehicleResponse v : list) {
                            myVehicles.put(v.getVehicleId().toLowerCase(), v.getMake() + " " + v.getModel());
                            trailRenderer.loadInitialTrail(v.getVehicleId(), () -> {});

                            if (realtimeClient == null) {
                                realtimeClient = new RealtimeLocationClient();
                                realtimeClient.connect(v.getVehicleId(), payload ->
                                        runOnUiThread(() -> handlePushedLocation(payload)));
                            }
                        }
                        if (!myVehicles.isEmpty()) {
                            fetchLocation();
                        }
                    }
                } catch (Exception e) {
                    Log.e("HomeActivity", "fetchMyVehicles parse error", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
            }
        });
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (mMap != null) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
            }
            getDeviceLocation();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void getDeviceLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    myCurrentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                }
            });
        }
    }

    private void getPhoneLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    LatLng pos = new LatLng(location.getLatitude(), location.getLongitude());
                    if (mMap != null) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));
                }
            });
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        trailRenderer = new VehicleTrailRenderer(this, mMap, trackingApi);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(6.9271, 79.8612), 10f));
    }

    private void changeMapType(int mapType, MaterialCardView selectedCard) {
        if (mMap != null) {
            mMap.setMapType(mapType);
            cardDefault.setStrokeWidth(0);
            cardTerrain.setStrokeWidth(0);
            cardSatellite.setStrokeWidth(0);
            cardHybrid.setStrokeWidth(0);
            selectedCard.setStrokeWidth(8);
            selectedCard.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, com.example.letstracklanka.R.color.brand_primary)));
            if (mapTypeMenu != null) mapTypeMenu.setVisibility(View.GONE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        }
    }

    private <T> List<T> parseList(String json, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        Gson gson = new Gson();
        String trimmed = json.trim();
        try {
            JsonObject maybeEnvelope = null;
            try {
                maybeEnvelope = trimmed.startsWith("{") ? gson.fromJson(trimmed, JsonObject.class) : null;
            } catch (Exception ignored) {
            }

            if (maybeEnvelope != null && maybeEnvelope.has("data") && maybeEnvelope.get("data").isJsonArray()) {
                list = gson.fromJson(maybeEnvelope.getAsJsonArray("data"), TypeToken.getParameterized(List.class, clazz).getType());
            } else if (trimmed.startsWith("[")) {
                list = gson.fromJson(trimmed, TypeToken.getParameterized(List.class, clazz).getType());
            } else if (trimmed.startsWith("{")) {
                list.add(gson.fromJson(trimmed, clazz));
            }
        } catch (Exception e) {
            Log.e("HomeActivity", "Parse error", e);
        }
        return list;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable);
        if (realtimeClient != null) realtimeClient.stop();
    }
}