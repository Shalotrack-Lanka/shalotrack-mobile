package com.example.letstracklanka.ui.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CreateDeviceAssignmentRequest;
import com.example.letstracklanka.data.model.CreateVehicleRequest;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.UpdateCustomerRequest;
import com.example.letstracklanka.data.model.RegisterFcmTokenRequest;
import com.example.letstracklanka.data.model.DashboardResponse;
import com.example.letstracklanka.data.model.GpsDeviceResponse;
import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.ui.auth.LoginActivity;
import com.example.letstracklanka.ui.vehicles.VehiclesActivity;
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
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int UPDATE_INTERVAL = 1000;
    private static final int CONTACTS_PERMISSION_REQUEST_CODE = 2001;

    private GoogleMap mMap;
    private ShaloTrackApi trackingApi;
    private ApiService mainApiService;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable trackingRunnable;

    private String currentCustomerId = null;
    private CustomerResponse currentCustomer = null;
    private final Map<String, String> myVehicles = new HashMap<>();
    private final Map<String, Marker> mapMarkers = new HashMap<>();
    private LatLng lastVehiclePosition = null;

    private TextView tvDeviceStatus, tvDeviceAddress, tvDeviceName;
    private View errorBanner;
    private TextView tvErrorBannerMessage, tvErrorBannerRetry;
    private MaterialCardView cardDefault, cardTerrain, cardSatellite, cardHybrid;
    private View mapTypeMenu;
    private VehicleTrailRenderer trailRenderer;
    private RealtimeLocationClient realtimeClient;
    private AddressResolver addressResolver;

    private boolean isMonthlyBilling = false;
    private BottomSheetDialog referDialog;

    private DrawerLayout drawerLayout;
    private TextView tvDrawerName, tvDrawerPhone, tvDrawerEmail;

    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermissionIfNeeded();
        setContentView(R.layout.activity_home);

        errorBanner = findViewById(R.id.errorBanner);
        tvErrorBannerMessage = findViewById(R.id.tvErrorBannerMessage);
        tvErrorBannerRetry = findViewById(R.id.tvErrorBannerRetry);

        registerNetworkMonitor();

        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);
        addressResolver = new AddressResolver(this);
        mainApiService = ApiClient.getClient().create(ApiService.class);

        initViews();
        setupUI();

        startRealTimeTracking();
        loadUserData();

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

        View menuRefer = findViewById(R.id.btnMenuRefer);
        if (menuRefer != null) {
            menuRefer.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showReferAndEarnBottomSheet();
            });
        }

        int[] comingSoonIds = {
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

        int[] comingSoonAddIds = {R.id.btnAddPerson, R.id.btnAddPet, R.id.btnAddTag, R.id.btnAddPlace};
        for (int id : comingSoonAddIds) {
            View item = findViewById(id);
            if (item != null) {
                item.setOnClickListener(v -> Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show());
            }
        }

        if (cardDefault != null) cardDefault.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_NORMAL, cardDefault));
        if (cardTerrain != null) cardTerrain.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_TERRAIN, cardTerrain));
        if (cardSatellite != null) cardSatellite.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_SATELLITE, cardSatellite));
        if (cardHybrid != null) cardHybrid.setOnClickListener(v -> changeMapType(GoogleMap.MAP_TYPE_HYBRID, cardHybrid));

        FloatingActionButton fabLocation = findViewById(R.id.fabLocation);
        if (fabLocation != null) fabLocation.setOnClickListener(v -> getPhoneLocation());

        MaterialButton btnHomeSOS = findViewById(R.id.btnSOS);
        if (btnHomeSOS != null) btnHomeSOS.setOnClickListener(v -> showSOSBottomSheet());

        MaterialButton btnSendLocation = findViewById(R.id.btnSendLocation);
        if (btnSendLocation != null) {
            btnSendLocation.setOnClickListener(v -> {
                if (lastVehiclePosition == null) {
                    Toast.makeText(this, "Vehicle location not available yet, try again in a moment", Toast.LENGTH_SHORT).show();
                    return;
                }
                String locationLink = "https://www.google.com/maps?q=" + lastVehiclePosition.latitude + "," + lastVehiclePosition.longitude;
                String message = "Here is my vehicle's current location:\n" + locationLink;

                try {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, message);
                    startActivity(Intent.createChooser(shareIntent, "Share vehicle location via"));
                } catch (Exception e) {
                    Toast.makeText(this, "No app available to share location", Toast.LENGTH_SHORT).show();
                }
            });
        }

        FloatingActionButton fabRefresh = findViewById(R.id.fabRefresh);
        if (fabRefresh != null) {
            fabRefresh.setOnClickListener(v -> {
                Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show();
                fetchLocation();
                fetchDashboard();
            });
        }

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

    private void showAppSubscriptionBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_app_subscription, null);
        dialog.setContentView(view);

        ImageView btnClose = view.findViewById(R.id.btnCloseAppSubs);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        MaterialCardView cardTabAnnually = view.findViewById(R.id.cardTabAnnually);
        TextView tvTabAnnually = view.findViewById(R.id.tvTabAnnually);
        MaterialCardView cardTabMonthly = view.findViewById(R.id.cardTabMonthly);
        TextView tvTabMonthly = view.findViewById(R.id.tvTabMonthly);

        MaterialCardView cardFree = view.findViewById(R.id.cardFree);
        MaterialCardView cardSilver = view.findViewById(R.id.cardSilver);
        MaterialCardView cardGold = view.findViewById(R.id.cardGold);
        MaterialCardView cardPlatinum = view.findViewById(R.id.cardPlatinum);

        TextView tvPriceFree = view.findViewById(R.id.tvPriceFree);
        TextView tvPriceSilver = view.findViewById(R.id.tvPriceSilver);
        TextView tvPriceGold = view.findViewById(R.id.tvPriceGold);
        TextView tvPricePlatinum = view.findViewById(R.id.tvPricePlatinum);

        View badgeFree = view.findViewById(R.id.badgeFree);
        View badgeSilver = view.findViewById(R.id.badgeSilver);
        View badgeGold = view.findViewById(R.id.badgeGold);
        View badgePlatinum = view.findViewById(R.id.badgePlatinum);

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

        Runnable resetCards = () -> {
            if (cardFree != null) { cardFree.animate().scaleX(1f).scaleY(1f).setDuration(200).start(); cardFree.setStrokeWidth(0); if(badgeFree != null) badgeFree.setVisibility(View.GONE); }
            if (cardSilver != null) { cardSilver.animate().scaleX(1f).scaleY(1f).setDuration(200).start(); cardSilver.setStrokeWidth(0); if(badgeSilver != null) badgeSilver.setVisibility(View.GONE); }
            if (cardGold != null) { cardGold.animate().scaleX(1f).scaleY(1f).setDuration(200).start(); cardGold.setStrokeWidth(0); if(badgeGold != null) badgeGold.setVisibility(View.GONE); }
            if (cardPlatinum != null) { cardPlatinum.animate().scaleX(1f).scaleY(1f).setDuration(200).start(); cardPlatinum.setStrokeWidth(0); if(badgePlatinum != null) badgePlatinum.setVisibility(View.GONE); }
        };

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

    private void showReferAndEarnBottomSheet() {
        referDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_refer_and_earn, null);
        referDialog.setContentView(view);

        ImageView btnClose = view.findViewById(R.id.btnCloseRefer);
        if (btnClose != null) btnClose.setOnClickListener(v -> referDialog.dismiss());

        MaterialButton btnSelectPhoneContacts = view.findViewById(R.id.btnSelectPhoneContacts);
        if (btnSelectPhoneContacts != null) {
            btnSelectPhoneContacts.setOnClickListener(v -> {
                checkContactsPermissionAndOpen();
            });
        }

        referDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        referDialog.show();
    }

    private void checkContactsPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, CONTACTS_PERMISSION_REQUEST_CODE);
        } else {
            if (referDialog != null && referDialog.isShowing()) {
                referDialog.dismiss();
            }
            showInviteContactsBottomSheet();
        }
    }

    private void showInviteContactsBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_invite_contacts, null);
        dialog.setContentView(view);

        ImageView btnClose = view.findViewById(R.id.btnCloseContacts);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewContacts);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<PhoneContact> contacts = getDeviceContacts();
            runOnUiThread(() -> {
                ContactsAdapter adapter = new ContactsAdapter(contacts);
                recyclerView.setAdapter(adapter);
            });
        });

        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.show();
    }

    private List<PhoneContact> getDeviceContacts() {
        List<PhoneContact> contactList = new ArrayList<>();
        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");

        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                @SuppressLint("Range") String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                @SuppressLint("Range") String phone = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                if (name != null && !name.trim().isEmpty() && phone != null) {
                    contactList.add(new PhoneContact(name, phone));
                }
            }
            cursor.close();
        }
        return contactList;
    }

    private static class PhoneContact {
        String name;
        String phone;

        PhoneContact(String name, String phone) {
            this.name = name;
            this.phone = phone;
        }

        String getInitials() {
            String[] parts = name.trim().split("\\s+");
            if (parts.length >= 2) {
                return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.ROOT);
            } else if (name.length() > 0) {
                return name.substring(0, 1).toUpperCase(Locale.ROOT);
            }
            return "?";
        }
    }

    private class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ContactViewHolder> {
        private final List<PhoneContact> contacts;

        ContactsAdapter(List<PhoneContact> contacts) {
            this.contacts = contacts;
        }

        @NonNull
        @Override
        public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact, parent, false);
            return new ContactViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
            PhoneContact contact = contacts.get(position);
            holder.tvName.setText(contact.name);
            holder.tvPhone.setText(contact.phone);
            holder.tvInitials.setText(contact.getInitials());

            holder.itemView.setOnClickListener(v -> {
                holder.radioButton.setChecked(!holder.radioButton.isChecked());
                if (holder.radioButton.isChecked()) {
                    Toast.makeText(HomeActivity.this, "Selected: " + contact.name, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return contacts.size();
        }

        class ContactViewHolder extends RecyclerView.ViewHolder {
            TextView tvInitials, tvName, tvPhone;
            RadioButton radioButton;

            ContactViewHolder(@NonNull View itemView) {
                super(itemView);
                tvInitials = itemView.findViewById(R.id.tvContactInitials);
                tvName = itemView.findViewById(R.id.tvContactName);
                tvPhone = itemView.findViewById(R.id.tvContactPhone);
                radioButton = itemView.findViewById(R.id.radioContactSelect);
            }
        }
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

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Register My GPS Vehicle")
                .setView(dialogView)
                .create();

        MaterialButton btnLinkDevice = dialogView.findViewById(R.id.btnLinkDevice);
        MaterialButton btnCancelAddVehicle = dialogView.findViewById(R.id.btnCancelAddVehicle);

        btnCancelAddVehicle.setOnClickListener(v -> dialog.dismiss());

        btnLinkDevice.setOnClickListener(v -> {
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
            dialog.dismiss();
        });

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92);
                int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
                dialog.getWindow().setLayout(width, height);
            }
        });

        dialog.show();
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

            if (lastVehiclePosition != null) {
                String locationLink = "https://www.google.com/maps?q=" + lastVehiclePosition.latitude + "," + lastVehiclePosition.longitude;
                String message = "EMERGENCY SOS!\nHere is my vehicle's current location:\n" + locationLink;

                try {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, message);
                    startActivity(Intent.createChooser(shareIntent, "Send SOS via"));
                } catch (Exception e) {
                    Toast.makeText(HomeActivity.this, "No app available to send SOS", Toast.LENGTH_SHORT).show();
                }

                Toast.makeText(this, "Opening SMS to send SOS...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Vehicle location not available right now — try again shortly", Toast.LENGTH_LONG).show();
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
                    if (!response.isSuccessful() || body == null) {
                        Log.e("HomeActivity", "fetchDashboard failed, code " + response.code());
                        showRetryDialog("Couldn't load dashboard data", HomeActivity.this::fetchDashboard);
                        return;
                    }
                    hideErrorBanner();

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
                    showRetryDialog("Something went wrong loading dashboard data", HomeActivity.this::fetchDashboard);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("HomeActivity", "fetchDashboard network error", t);
                showRetryDialog("Network error — couldn't load dashboard data", HomeActivity.this::fetchDashboard);
            }
        });
    }

    private void handlePushedLocation(RealtimeLocationPayload payload) {
        if (payload.getVehicleId() == null || mMap == null) return;
        LatLng pos = new LatLng(payload.getLatitude(), payload.getLongitude());
        if (pos.latitude == 0 && pos.longitude == 0) return;
        lastVehiclePosition = pos;

        String title = myVehicles.getOrDefault(payload.getVehicleId().toLowerCase(), "My Vehicle");
        trailRenderer.updatePosition(pos, (float) payload.getHeading(), title);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));

        if (tvDeviceName != null) tvDeviceName.setText(title);

        addressResolver.resolveAddress(pos.latitude, pos.longitude, address -> {
            if (tvDeviceAddress != null) tvDeviceAddress.setText(address);
        });

        String status = payload.getSpeed() > MOVEMENT_SPEED_THRESHOLD_KMH
                ? "Moving (" + (int) payload.getSpeed() + " km/h)"
                : (payload.isIgnitionOn() ? "Idle" : "Parked");
        int color = payload.getSpeed() > MOVEMENT_SPEED_THRESHOLD_KMH ? ContextCompat.getColor(this, com.example.letstracklanka.R.color.status_moving) : ContextCompat.getColor(this, com.example.letstracklanka.R.color.brand_primary);
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
                                    lastVehiclePosition = pos;
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
            tvDeviceStatus.setText(loc.getSpeed() > MOVEMENT_SPEED_THRESHOLD_KMH ? "Moving (" + (int) loc.getSpeed() + " km/h)" : (loc.isIgnitionOn() ? "Idle" : "Parked"));
            tvDeviceStatus.setTextColor(loc.getSpeed() > MOVEMENT_SPEED_THRESHOLD_KMH ? ContextCompat.getColor(this, com.example.letstracklanka.R.color.status_moving) : ContextCompat.getColor(this, com.example.letstracklanka.R.color.brand_primary));
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            // Handled notification permission if needed
        } else if (requestCode == CONTACTS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (referDialog != null && referDialog.isShowing()) {
                    referDialog.dismiss();
                }
                showInviteContactsBottomSheet();
            } else {
                Toast.makeText(this, "Contacts permission is required to invite friends", Toast.LENGTH_SHORT).show();
            }
        }
    }

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
                        hideErrorBanner();
                        String json = body.string();

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
                            registerFcmToken();
                            fetchMyVehicles();
                            fetchDashboard();
                        }
                    } else if (response.code() == 404) {
                        Log.w("HomeActivity", "No customer profile exists yet for this account.");
                    } else {
                        Log.w("HomeActivity", "getMyProfile failed with code " + response.code());
                        showRetryDialog("Couldn't load your profile", HomeActivity.this::loadUserData);
                    }
                } catch (Exception e) {
                    Log.e("HomeActivity", "loadUserData parse error", e);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("HomeActivity", "loadUserData network error", t);
                showRetryDialog("Network error — couldn't load your profile", HomeActivity.this::loadUserData);
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
                        hideErrorBanner();
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
                    } else {
                        Log.e("HomeActivity", "fetchMyVehicles failed, code " + response.code());
                        showRetryDialog("Couldn't load your vehicles", HomeActivity.this::fetchMyVehicles);
                    }
                } catch (Exception e) {
                    Log.e("HomeActivity", "fetchMyVehicles parse error", e);
                    showRetryDialog("Something went wrong loading your vehicles", HomeActivity.this::fetchMyVehicles);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("HomeActivity", "fetchMyVehicles network error", t);
                showRetryDialog("Network error — couldn't load your vehicles", HomeActivity.this::fetchMyVehicles);
            }
        });
    }

    private void showRetryDialog(String message, Runnable retryAction) {
        if (errorBanner == null) return;
        tvErrorBannerMessage.setText(message);
        tvErrorBannerRetry.setOnClickListener(v -> {
            hideErrorBanner();
            retryAction.run();
        });
        errorBanner.setVisibility(View.VISIBLE);
    }

    private void hideErrorBanner() {
        if (errorBanner != null) errorBanner.setVisibility(View.GONE);
    }

    private void getPhoneLocation() {
        if (lastVehiclePosition != null && mMap != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(lastVehiclePosition, 15f));
        } else {
            Toast.makeText(this, "Vehicle location not available yet", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        trailRenderer = new VehicleTrailRenderer(this, mMap, trackingApi);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(6.9271, 79.8612), 10f));

        int savedMapType = getSharedPreferences(MAP_PREFS_NAME, MODE_PRIVATE)
                .getInt(MAP_TYPE_PREF_KEY, GoogleMap.MAP_TYPE_NORMAL);
        MaterialCardView savedCard;
        if (savedMapType == GoogleMap.MAP_TYPE_TERRAIN) savedCard = cardTerrain;
        else if (savedMapType == GoogleMap.MAP_TYPE_SATELLITE) savedCard = cardSatellite;
        else if (savedMapType == GoogleMap.MAP_TYPE_HYBRID) savedCard = cardHybrid;
        else savedCard = cardDefault;
        changeMapType(savedMapType, savedCard);
    }

    private static final String MAP_PREFS_NAME = "ShaloTrackMapPrefs";
    private static final double MOVEMENT_SPEED_THRESHOLD_KMH = 2.0;
    private static final String MAP_TYPE_PREF_KEY = "selected_map_type";

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

            getSharedPreferences(MAP_PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(MAP_TYPE_PREF_KEY, mapType)
                    .apply();
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

    private void registerNetworkMonitor() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> showRetryDialog("No internet connection", HomeActivity.this::loadUserData));
            }

            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    hideErrorBanner();
                    loadUserData();
                });
            }
        };
        cm.registerDefaultNetworkCallback(networkCallback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable);
        if (realtimeClient != null) realtimeClient.stop();

        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        }
    }
}