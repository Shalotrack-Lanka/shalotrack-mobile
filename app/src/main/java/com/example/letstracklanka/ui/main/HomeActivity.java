package com.example.letstracklanka.ui.main;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
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
import com.example.letstracklanka.data.model.CreateSubscriptionRequest;
import com.example.letstracklanka.data.model.CreateVehicleRequest;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.UpdateCustomerRequest;
import com.example.letstracklanka.data.model.RegisterFcmTokenRequest;
import com.google.firebase.messaging.FirebaseMessaging;
import com.example.letstracklanka.data.model.DashboardResponse;
import com.example.letstracklanka.data.model.GpsDeviceResponse;
import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.ui.auth.LoginActivity;
import com.example.letstracklanka.ui.contacts.EmergencyContactsActivity;
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
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final int UPDATE_INTERVAL = 1000;
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
    private RecyclerView recyclerHomeVehicles;
    private com.example.letstracklanka.ui.vehicles.VehicleListAdapter homeVehicleAdapter;
    private View errorBanner;
    private TextView tvErrorBannerMessage, tvErrorBannerRetry;
    private MaterialCardView cardDefault, cardTerrain, cardSatellite, cardHybrid;
    private View mapTypeMenu;
    private VehicleTrailRenderer trailRenderer;
    private RealtimeLocationClient realtimeClient;
    private AddressResolver addressResolver;
    // isMonthlyBilling removed: the new duration-plan model (1/2/3 years)
    // has no recurring monthly/annual billing choice, unlike the old
    // tier model this replaced.
    private DrawerLayout drawerLayout;
    private TextView tvDrawerName, tvDrawerPhone, tvDrawerEmail;
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
        recyclerHomeVehicles = findViewById(R.id.recyclerHomeVehicles);
        if (recyclerHomeVehicles != null) {
            recyclerHomeVehicles.setLayoutManager(new LinearLayoutManager(this));
            homeVehicleAdapter = new com.example.letstracklanka.ui.vehicles.VehicleListAdapter(
                    new ArrayList<>(), this::onHomeVehicleSelected, this::confirmRemoveVehicleFromHome);
            recyclerHomeVehicles.setAdapter(homeVehicleAdapter);
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
                showReportsMenuBottomSheet();
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
            btnMenuVoiceTrack.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showVoiceTrackBottomSheet();
            });
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
        View menuEmergencyContacts = findViewById(R.id.btnMenuEmergencyContacts);
        if (menuEmergencyContacts != null) {
            menuEmergencyContacts.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(HomeActivity.this, EmergencyContactsActivity.class));
            });
        }
        int[] comingSoonIds = {
                R.id.btnMenuShop,
                R.id.btnMenuHelpVideos,
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
    // Rebuilt to match the requirement doc's actual subscription model:
    // three fixed-price, one-time, multi-year terms, not the previous
    // Free/Silver/Gold/Platinum recurring feature-tier model with a
    // monthly/annual toggle -- that model didn't match the written spec
    // at all. Still UI-only: btnContinueAppSubs has no real backend
    // behind it yet (see the "Proceeding..." placeholder below) -- that's
    // separate, larger scope than this fix.
    private void showAppSubscriptionBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_app_subscription, null);
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

        // Tracks which plan is currently selected across all four card
        // click handlers below -- a 1-element array is the standard way
        // to capture *mutable* state in a Java lambda (plain local
        // variables captured in lambdas must be effectively final).
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
                if (btnContinueAppSubs != null) btnContinueAppSubs.setText(getString(R.string.subscription_continue_free));
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
                if (btnContinueAppSubs != null) btnContinueAppSubs.setText(getString(R.string.subscription_continue_1_year));
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
                if (btnContinueAppSubs != null) btnContinueAppSubs.setText(getString(R.string.subscription_continue_2_years));
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
                if (btnContinueAppSubs != null) btnContinueAppSubs.setText(getString(R.string.subscription_continue_3_years));
            });
        }
        if (btnContinueAppSubs != null) {
            btnContinueAppSubs.setOnClickListener(v -> {
                // Real backend call -- replaces the "Proceeding..." toast
                // placeholder. Disables the button during the request so a
                // double-tap can't fire two subscription requests for the
                // same plan.
                btnContinueAppSubs.setEnabled(false);
                CreateSubscriptionRequest request = new CreateSubscriptionRequest(selectedPlan[0]);
                mainApiService.requestSubscription(request).enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        btnContinueAppSubs.setEnabled(true);
                        try (ResponseBody body = response.body()) {
                            if (response.isSuccessful() && body != null) {
                                String message = extractInstructionsMessage(body.string());
                                Toast.makeText(HomeActivity.this, message, Toast.LENGTH_LONG).show();
                                dialog.dismiss();
                            } else {
                                Log.w("HomeActivity", "requestSubscription failed, code " + response.code());
                                String errorBody = null;
                                try {
                                    if (response.errorBody() != null) errorBody = response.errorBody().string();
                                } catch (Exception ignored) { }
                                String message = extractErrorMessage(errorBody, "Couldn't submit your subscription request. Please try again.");
                                Toast.makeText(HomeActivity.this, message, Toast.LENGTH_LONG).show();
                            }
                        } catch (Exception e) {
                            Log.e("HomeActivity", "requestSubscription parse error", e);
                            Toast.makeText(HomeActivity.this, "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        btnContinueAppSubs.setEnabled(true);
                        Log.e("HomeActivity", "requestSubscription network error", t);
                        Toast.makeText(HomeActivity.this, "Network error \u2014 check your connection and try again.", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        if (cardOneYear != null) cardOneYear.performClick(); // default selection, matches the old default-select-first-card behavior
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.show();
    }
    // Pulls data.instructionsMessage out of the ApiResponse envelope --
    // matches System.Text.Json's default camelCase serialization of the
    // C# SubscriptionResponseDto.InstructionsMessage property.
    private String extractInstructionsMessage(String json) {
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
            Log.e("HomeActivity", "extractInstructionsMessage parse error", e);
        }
        return "Subscription request submitted.";
    }

    // Pulls message + the first entry of errors (if any) out of a FAILED
    // ApiResponse envelope -- e.g. the 409 Conflict response from
    // RequestSubscriptionAsync when a customer already has an
    // Active/PendingPayment subscription. Previously this text was
    // discarded entirely in favor of a generic "try again" message.
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
            Log.e("HomeActivity", "extractErrorMessage parse error", e);
            return fallback;
        }
    }

    private void showReportsMenuBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_reports_menu, null);
        dialog.setContentView(view);
        ImageView btnClose = view.findViewById(R.id.btnCloseReportsMenu);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        View.OnClickListener comingSoon = v -> Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show();
        int[] cardIds = {R.id.cardKmReport, R.id.cardTripReport, R.id.cardFuelReport, R.id.cardTempReport, R.id.cardAlertReport, R.id.cardFuelGraph, R.id.cardStopAlert};
        for (int id : cardIds) {
            View card = view.findViewById(id);
            if (card != null) card.setOnClickListener(comingSoon);
        }
        dialog.show();
    }
    private void showVoiceTrackBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_voice_track, null);
        dialog.setContentView(view);
        ImageView btnClose = view.findViewById(R.id.btnCloseVoiceTrack);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        MaterialButton btnSend = view.findViewById(R.id.btnSendAlexaVerification);
        if (btnSend != null) {
            btnSend.setOnClickListener(v -> {
                Toast.makeText(this, "Verification email sent!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }
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
            boolean hasError = false;
            if (vNum.isEmpty()) {
                etVehicleNumber.setError("Vehicle Number is required");
                hasError = true;
            }
            if (imei.isEmpty()) {
                etImei.setError("Device IMEI is required");
                hasError = true;
            }
            if (hasError) return;
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
        mainApiService.lookupDeviceByImei(imei).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        GpsDeviceResponse device = extractObject(body.string(), GpsDeviceResponse.class);
                        if (device != null && device.getDeviceId() != null) {
                            createVehicle(vNum, chassis, engine, make, model, year, color, type, fuel, device.getDeviceId());
                        } else {
                            showRetryDialog("Something went wrong reading the device details", null);
                        }
                    } else if (response.code() == 404) {
                        showRetryDialog("IMEI not found in registry", null);
                    } else {
                        showRetryDialog("Could not check device registry (code " + response.code() + ")",
                                () -> processVehicleAddition(vNum, chassis, engine, make, model, year, color, type, fuel, imei));
                    }
                } catch (Exception e) {
                    Log.e("HomeActivity", "Error finding device", e);
                    showRetryDialog("Something went wrong checking the device registry",
                            () -> processVehicleAddition(vNum, chassis, engine, make, model, year, color, type, fuel, imei));
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                showRetryDialog("Network error — couldn't check device registry",
                        () -> processVehicleAddition(vNum, chassis, engine, make, model, year, color, type, fuel, imei));
            }
        });
    }
    private void createVehicle(String vNum, String chassis, String engine, String make, String model,
                               int year, String color, String type, String fuel, String deviceId) {
        if (currentCustomerId == null) {
            showRetryDialog("Your profile isn't loaded yet",
                    () -> createVehicle(vNum, chassis, engine, make, model, year, color, type, fuel, deviceId));
            return;
        }
        CreateVehicleRequest request = new CreateVehicleRequest(currentCustomerId, vNum, chassis, engine, make, model, year, color, type, fuel);
        mainApiService.createVehicle(request).enqueue(new Callback<VehicleResponse>() {
            @Override
            public void onResponse(@NonNull Call<VehicleResponse> call, @NonNull Response<VehicleResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    assignDeviceToVehicle(response.body().getVehicleId(), deviceId);
                } else {
                    showRetryDialog("Could not create vehicle (code " + response.code() + ")",
                            () -> createVehicle(vNum, chassis, engine, make, model, year, color, type, fuel, deviceId));
                }
            }
            @Override
            public void onFailure(@NonNull Call<VehicleResponse> call, @NonNull Throwable t) {
                showRetryDialog("Network error — couldn't create vehicle",
                        () -> createVehicle(vNum, chassis, engine, make, model, year, color, type, fuel, deviceId));
            }
        });
    }
    private void assignDeviceToVehicle(String vehicleId, String deviceId) {
        mainApiService.assignDevice(new CreateDeviceAssignmentRequest(vehicleId, deviceId)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    loadUserData();
                } else {
                    showRetryDialog("Could not assign device (code " + response.code() + ")",
                            () -> assignDeviceToVehicle(vehicleId, deviceId));
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                showRetryDialog("Network error — couldn't assign device",
                        () -> assignDeviceToVehicle(vehicleId, deviceId));
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
                    if (homeVehicleAdapter != null) {
                        List<com.example.letstracklanka.data.model.DashboardVehicle> dashboardVehicles =
                                gson.fromJson(data.getAsJsonArray("vehicles"),
                                        com.google.gson.reflect.TypeToken.getParameterized(
                                                List.class, com.example.letstracklanka.data.model.DashboardVehicle.class).getType());
                        homeVehicleAdapter.updateVehicles(dashboardVehicles);
                    }
                    for (com.google.gson.JsonElement el : data.getAsJsonArray("vehicles")) {
                        JsonObject v = el.getAsJsonObject();
                        if (!v.has("vehicleId") || v.get("vehicleId").isJsonNull()) continue;
                        String vehicleId = v.get("vehicleId").getAsString().toLowerCase();
                        String make = v.has("make") && !v.get("make").isJsonNull() ? v.get("make").getAsString() : "";
                        String model = v.has("model") && !v.get("model").isJsonNull() ? v.get("model").getAsString() : "";
                        myVehicles.put(vehicleId, (make + " " + model).trim());
                        // FIX: this used to call updateMarker() for EVERY vehicle
                        // here, adding a separate map marker for each one --
                        // redundant with (and inconsistent with) the properly
                        // selected-vehicle-only marker/trail that trailRenderer
                        // already draws via fetchLocation()/handlePushedLocation().
                        // Removed entirely; myVehicles still gets populated/kept
                        // fresh here, just no longer adds a raw marker per vehicle.
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
        if (!payload.getVehicleId().toLowerCase().equals(getSelectedVehicleId())) return;
        LatLng pos = new LatLng(payload.getLatitude(), payload.getLongitude());
        if (pos.latitude == 0 && pos.longitude == 0) return;
        lastVehiclePosition = pos;
        String title = myVehicles.getOrDefault(payload.getVehicleId().toLowerCase(), "My Vehicle");
        trailRenderer.updatePosition(pos, (float) payload.getHeading(), title);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));
    }
    private void onHomeVehicleSelected(com.example.letstracklanka.data.model.DashboardVehicle vehicle) {
        getSharedPreferences(
                com.example.letstracklanka.ui.vehicles.VehicleListActivity.VEHICLE_PREFS_NAME,
                Context.MODE_PRIVATE)
                .edit()
                .putString(com.example.letstracklanka.ui.vehicles.VehicleListActivity.SELECTED_VEHICLE_ID_KEY, vehicle.getVehicleId())
                .apply();
        // FIX: reverted -- stays on Home, just updates the map for the newly
        // selected vehicle. No navigation to VehiclesActivity.
        fetchLocation();
    }
    private void confirmRemoveVehicleFromHome(com.example.letstracklanka.data.model.DashboardVehicle vehicle) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + vehicle.getVehicleNumber() + "?")
                .setMessage("This removes the vehicle from your account and frees its GPS device so it can be linked to a new vehicle. Trip history and alerts are kept. This can't be undone from the app.")
                .setPositiveButton("Remove", (dialog, which) -> {
                    mainApiService.deleteVehicle(vehicle.getVehicleId()).enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                            if (response.isSuccessful()) {
                                fetchDashboard();
                            } else {
                                showRetryDialog("Couldn't remove vehicle (code " + response.code() + ")", null);
                            }
                        }
                        @Override
                        public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                            showRetryDialog("Network error — couldn't remove vehicle", null);
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private String getSelectedVehicleId() {
        if (myVehicles.isEmpty()) return "";
        String selected = getSharedPreferences(
                com.example.letstracklanka.ui.vehicles.VehicleListActivity.VEHICLE_PREFS_NAME,
                android.content.Context.MODE_PRIVATE)
                .getString(com.example.letstracklanka.ui.vehicles.VehicleListActivity.SELECTED_VEHICLE_ID_KEY, null);
        if (selected != null && myVehicles.containsKey(selected.toLowerCase())) {
            return selected.toLowerCase();
        }
        return myVehicles.keySet().iterator().next();
    }
    private void fetchLocation() {
        if (myVehicles.isEmpty()) {
            return;
        }
        String vehicleId = getSelectedVehicleId();
        {
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
    private <T> T extractObject(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), clazz);
            }
            return gson.fromJson(json, clazz);
        } catch (Exception e) {
            Log.e("HomeActivity", "extractObject parse error", e);
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
        if (retryAction != null) {
            tvErrorBannerRetry.setVisibility(View.VISIBLE);
            tvErrorBannerRetry.setOnClickListener(v -> {
                hideErrorBanner();
                retryAction.run();
            });
        } else {
            tvErrorBannerRetry.setVisibility(View.GONE);
        }
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
    // GPS receivers commonly report small non-zero speeds (drift, multipath
    // reflection) even when genuinely stationary -- confirmed via a real test
    // where a parked vehicle indoors consistently read 5 km/h. Matched to
    // VehiclesActivity's threshold too.
    private static final double MOVEMENT_SPEED_THRESHOLD_KMH = 7.0;
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
    private ConnectivityManager.NetworkCallback networkCallback;
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