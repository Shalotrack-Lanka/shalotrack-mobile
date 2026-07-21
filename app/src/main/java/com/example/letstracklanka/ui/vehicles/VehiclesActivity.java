package com.example.letstracklanka.ui.vehicles;

import android.content.Intent;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.LocationResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.data.remote.ShaloTrackApi;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.ui.main.AddressResolver;
import com.example.letstracklanka.ui.main.HomeActivity;
import com.example.letstracklanka.ui.history.TripHistoryActivity;
import com.example.letstracklanka.ui.main.TagsActivity;
import com.example.letstracklanka.ui.main.CirclesActivity;
import com.example.letstracklanka.ui.main.VehicleTrailRenderer;
import com.example.letstracklanka.ui.main.RealtimeLocationClient;
import com.example.letstracklanka.ui.main.RealtimeLocationPayload;
import com.example.letstracklanka.ui.main.AlertsActivity;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VehiclesActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ShaloTrackApi trackingApi;
    private ApiService mainApiService;

    private VehicleTrailRenderer trailRenderer;
    private RealtimeLocationClient realtimeClient;   // NEW — Option B push client

    // UI components
    private View layoutCollapsed;              // FIX: was LinearLayout, XML root is RelativeLayout — see MIGRATION notes
    private LinearLayout layoutExpanded, layoutLeftFabs;
    private GridLayout gridMenu;
    private ImageView btnCloseExpanded;
    private View fabAdd, fabHistory, btnRefresh;
    private BottomSheetBehavior<View> bottomSheetBehavior;

    // Text tags and dots to show data
    private TextView tvCollapsedStatus, tvCollapsedAddress;
    private TextView tvExpandedStatus, tvExpandedAddress, tvLastUpdated, tvVehicleNameCollapsed, tvVehicleNameExpanded;
    private TextView tvVehicleImei, tvGpsDeviceStatus;
    private CardView dotIgnition, dotAC;
    private AddressResolver addressResolver;

    // Variables for updating map automatically
    private final Handler handler = new Handler();
    private Runnable trackingRunnable;
    private final int UPDATE_INTERVAL = 1000;   // NEW — was 10000. SignalR push is now primary; this is just a safety-net fallback poll in case the push connection drops.

    private String currentCustomerId = null;
    private String selectedVehicleId = null;
    private String selectedVehicleName = "No vehicle yet";
    private boolean hasRealVehicle = false;

    // Kept for Details/Nav grid actions.
    private VehicleResponse selectedVehicle = null;
    private LatLng lastKnownPosition = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicles);

        // Setup API connection
        trackingApi = ApiClient.getClient().create(ShaloTrackApi.class);
        mainApiService = ApiClient.getClient().create(ApiService.class);
        addressResolver = new AddressResolver(this); //object

        // Link code with XML design
        initViews();
        setupBottomSheet();
        setupGridMenu();

        // Load the Google Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapVehicles);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        // Fetch user data and start tracking
        loadUserData();
        startRealTimeTracking();
    }

    // Connect variables to XML IDs
    private void initViews() {
        layoutCollapsed = findViewById(R.id.layoutCollapsed);
        layoutExpanded = findViewById(R.id.layoutExpanded);
        layoutLeftFabs = findViewById(R.id.layoutLeftFabs);
        gridMenu = findViewById(R.id.gridMenu);
        fabAdd = findViewById(R.id.fabAdd);
        fabHistory = findViewById(R.id.fabHistory);

        // FIX: fabHistory now lives permanently in the top-right FAB stack (see XML),
        // always visible, same as Letstrack's reference UI. No visibility toggling
        // needed here anymore — it never moves or disappears with the sheet's state.
        if (fabHistory != null) {
            fabHistory.setOnClickListener(v -> openTripHistory());
        }

        tvCollapsedStatus = findViewById(R.id.tvCollapsedStatus);
        tvCollapsedAddress = findViewById(R.id.tvCollapsedAddress);
        tvExpandedStatus = findViewById(R.id.tvExpandedStatus);
        tvExpandedAddress = findViewById(R.id.tvExpandedAddress);
        tvLastUpdated = findViewById(R.id.tvLastUpdated);
        tvVehicleNameCollapsed = findViewById(R.id.tvVehicleNameCollapsed);
        tvVehicleNameExpanded = findViewById(R.id.tvVehicleNameExpanded);
        tvVehicleImei = findViewById(R.id.tvVehicleImei);
        tvGpsDeviceStatus = findViewById(R.id.tvGpsDeviceStatus);
        dotIgnition = findViewById(R.id.dotIgnition);
        dotAC = findViewById(R.id.dotAC);

        btnCloseExpanded = findViewById(R.id.btnCloseExpanded);
        btnRefresh = findViewById(R.id.btnRefresh);
    }

    // Setup how the bottom sheet moves and button clicks
    private void setupBottomSheet() {
        View bottomSheet = findViewById(R.id.bottomSheetVehicleDetails);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    // Show full menu when swiped all the way up
                    gridMenu.setVisibility(View.VISIBLE);
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    // Show small card when swiped down
                    layoutExpanded.setVisibility(View.GONE);
                    gridMenu.setVisibility(View.GONE);
                    layoutCollapsed.setVisibility(View.VISIBLE);
                    if (fabAdd != null) fabAdd.setVisibility(View.VISIBLE);
                    // FIX: fabHistory visibility no longer tied to sheet state — removed
                    // the two toggle lines that used to live here and in the expand
                    // block below. It stays permanently visible in the top-right stack.
                    if (layoutLeftFabs != null) layoutLeftFabs.setVisibility(View.GONE);
                }
            }
            @Override public void onSlide(@NonNull View bottomSheet, float slideOffset) { }
        });

        // Open to half-screen when clicking the small bottom card
        if (layoutCollapsed != null) {
            layoutCollapsed.setOnClickListener(v -> {
                layoutCollapsed.setVisibility(View.GONE);
                if (fabAdd != null) fabAdd.setVisibility(View.GONE);
                layoutExpanded.setVisibility(View.VISIBLE);
                gridMenu.setVisibility(View.VISIBLE);
                if (layoutLeftFabs != null) layoutLeftFabs.setVisibility(View.VISIBLE);

                // FIX round 2: setting visibility and calling setState() in the same
                // click let BottomSheetBehavior calculate the expanded height from a
                // STALE measurement taken before gridMenu became visible -- the sheet
                // expanded to its old, smaller size and the new grid content had
                // nowhere to go (looked "stuck", nothing left to scroll into).
                // Deferring setState() with post() lets the layout pass that makes
                // the grid visible complete FIRST, so the behavior re-measures
                // including the grid before deciding the expanded height.
                View bottomSheetView = findViewById(R.id.bottomSheetVehicleDetails);
                bottomSheetView.post(() ->
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED));
            });
        }

        // Close back to small card when 'X' is clicked
        if (btnCloseExpanded != null) {
            btnCloseExpanded.setOnClickListener(v -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED));
        }

        View btnMenuAlerts = findViewById(R.id.btnMenuAlerts);
        if (btnMenuAlerts != null) {
            btnMenuAlerts.setOnClickListener(v -> {
                Intent intent = new Intent(VehiclesActivity.this, AlertsActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        View navHome = findViewById(R.id.nav_home);
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                Intent intent = new Intent(VehiclesActivity.this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // Click Vehicles (Just close the sheet if it's open)
        View navVehicles = findViewById(R.id.nav_vehicles);
        if (navVehicles != null) {
            navVehicles.setOnClickListener(v -> {
                if (bottomSheetBehavior != null) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            });
        }

        // Go to Tags Screen
        View navTags = findViewById(R.id.nav_tags);
        if (navTags != null) {
            navTags.setOnClickListener(v -> {
                Intent intent = new Intent(VehiclesActivity.this, TagsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // Go to Circles Screen
        View navCircles = findViewById(R.id.nav_circles);
        if (navCircles != null) {
            navCircles.setOnClickListener(v -> {
                Intent intent = new Intent(VehiclesActivity.this, CirclesActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // Go to Alerts Screen (Updated)
        View navAlerts = findViewById(R.id.nav_alerts);
        if (navAlerts != null) {
            navAlerts.setOnClickListener(v -> {
                Intent intent = new Intent(VehiclesActivity.this, AlertsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // ----------------------------------------------------------------------
        // මෙන්න වෙනස් කරපු Menu Button කේතය (Home එකට ගිහින් Drawer එක අරින්න)
        // ----------------------------------------------------------------------
        View navMenu = findViewById(R.id.nav_menu);
        if (navMenu != null) {
            navMenu.setOnClickListener(v -> {
                Intent intent = new Intent(VehiclesActivity.this, HomeActivity.class);
                intent.putExtra("open_drawer", true);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        // Refresh location button
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                Toast.makeText(this, "Refreshing location...", Toast.LENGTH_SHORT).show();
                fetchLocationData();
            });
        }
    }

    /**
     * Wires the action grid (History / Alerts / VoiceTrack / Value / Places /
     * Immobilize / Nav / Details), matching Letstrack's reference layout.
     *
     * Only History, Nav, and Details do something real right now. VoiceTrack,
     * Value, and Places are grayed out in the XML and show a placeholder message —
     * they aren't built features yet, not broken ones. Immobilize is Engine Cut:
     * it is DELIBERATELY never wired to any real command. Do not change this
     * without an explicit, written safety specification from the client.
     */
    private void setupGridMenu() {
        View btnMenuHistory = findViewById(R.id.btnMenuHistory);
        if (btnMenuHistory != null) {
            btnMenuHistory.setOnClickListener(v -> openTripHistory());
        }

        View btnMenuVoiceTrack = findViewById(R.id.btnMenuVoiceTrack);
        if (btnMenuVoiceTrack != null) {
            btnMenuVoiceTrack.setOnClickListener(v ->
                    Toast.makeText(this, "Not available for this device", Toast.LENGTH_SHORT).show());
        }

        View btnMenuValue = findViewById(R.id.btnMenuValue);
        if (btnMenuValue != null) {
            btnMenuValue.setOnClickListener(v ->
                    Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show());
        }

        View btnMenuPlaces = findViewById(R.id.btnMenuPlaces);
        if (btnMenuPlaces != null) {
            btnMenuPlaces.setOnClickListener(v ->
                    Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show());
        }

        // SAFETY: Immobilize = Engine Cut. Never wire this to a real command without
        // an explicit safety spec (speed threshold, confirmation flow, PIN, fallback
        // behavior) agreed with the client. This handler only informs the user.
        View btnMenuImmobilize = findViewById(R.id.btnMenuImmobilize);
        if (btnMenuImmobilize != null) {
            btnMenuImmobilize.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Not available yet")
                    .setMessage("Remote engine cut requires additional safety configuration " +
                            "and is not enabled for this vehicle yet.")
                    .setPositiveButton("OK", null)
                    .show());
        }

        View btnMenuNav = findViewById(R.id.btnMenuNav);
        if (btnMenuNav != null) {
            btnMenuNav.setOnClickListener(v -> openNavigation());
        }

        View btnMenuDetails = findViewById(R.id.btnMenuDetails);
        if (btnMenuDetails != null) {
            btnMenuDetails.setOnClickListener(v -> showVehicleDetails());
        }
    }

    private void openTripHistory() {
        Intent intent = new Intent(VehiclesActivity.this, TripHistoryActivity.class);
        startActivity(intent);
    }

    private void openNavigation() {
        if (lastKnownPosition == null) {
            Toast.makeText(this, "No location available yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" +
                lastKnownPosition.latitude + "," + lastKnownPosition.longitude);
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            // Google Maps app not installed — fall back to a browser-based maps URL.
            Uri webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" +
                    lastKnownPosition.latitude + "," + lastKnownPosition.longitude);
            startActivity(new Intent(Intent.ACTION_VIEW, webUri));
        }
    }

    private void showVehicleDetails() {
        if (selectedVehicle == null) {
            Toast.makeText(this, "Vehicle details not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String message = "Vehicle Number: " + safe(selectedVehicle.getVehicleNumber()) + "\n" +
                "Make: " + safe(selectedVehicle.getMake()) + "\n" +
                "Model: " + safe(selectedVehicle.getModel()) + "\n" +
                "IMEI: " + (selectedVehicle.hasGpsDevice() && selectedVehicle.getImei() != null
                ? selectedVehicle.getImei() : "Not linked");

        new AlertDialog.Builder(this)
                .setTitle(selectedVehicleName)
                .setMessage(message)
                .setPositiveButton("Close", null)
                .show();
    }

    private String safe(String value) {
        return value != null ? value : "--";
    }

    private void showCallCenterBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_call_center, null);
        dialog.setContentView(view);

        ViewPager2 viewPager = view.findViewById(R.id.viewPagerCallCenter);
        if (viewPager != null) {
            // NOTE: Ensure CallCenterPagerAdapter exists in your project
            // viewPager.setAdapter(new CallCenterPagerAdapter());
        }

        ImageView btnClose = view.findViewById(R.id.btnCloseCallCenter);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        View btnCloseBottom = view.findViewById(R.id.btnCallCenterClose);
        if (btnCloseBottom != null) btnCloseBottom.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // customers). Now uses the /me endpoint, same fix already applied in HomeActivity.
    private void loadUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        mainApiService.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        CustomerResponse customer = extractObject(body.string(), CustomerResponse.class);
                        if (customer != null && customer.getCustomerId() != null) {
                            currentCustomerId = customer.getCustomerId();
                            fetchVehicles();
                        }
                    } else {
                        Log.w("VehiclesActivity", "getMyProfile failed with code " + response.code());
                    }
                } catch (Exception e) {
                    Log.e("VehiclesActivity", "Error loading user data", e);
                }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("VehiclesActivity", "Failed to fetch user data", t);
            }
        });
    }

    // Fetch the list of vehicles owned by this customer
    private void fetchVehicles() {
        if (currentCustomerId == null) return;
        mainApiService.getVehiclesByCustomer(currentCustomerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<VehicleResponse> list = parseList(body.string(), VehicleResponse.class);
                        if (!list.isEmpty()) {
                            // Select the most recently added vehicle
                            VehicleResponse vehicle = list.get(list.size() - 1);
                            selectedVehicle = vehicle;
                            selectedVehicleId = vehicle.getVehicleId();
                            trailRenderer.loadInitialTrail(selectedVehicleId, () -> {});

                            // NEW — start real-time push for this vehicle (Option B)
                            if (realtimeClient == null) {
                                realtimeClient = new RealtimeLocationClient();
                                realtimeClient.connect(selectedVehicleId, payload ->
                                        runOnUiThread(() -> handlePushedLocation(payload)));
                            }
                            selectedVehicleName = vehicle.getMake() + " " + vehicle.getModel();
                            hasRealVehicle = true;

                            // Vehicle Information display
                            if (vehicle.hasGpsDevice() && vehicle.getImei() != null) {
                                if (tvVehicleImei != null) tvVehicleImei.setText("IMEI: " + vehicle.getImei());
                                if (tvGpsDeviceStatus != null) {
                                    tvGpsDeviceStatus.setText("GPS Device: Linked");
                                    tvGpsDeviceStatus.setTextColor(ContextCompat.getColor(VehiclesActivity.this, com.example.letstracklanka.R.color.status_moving));
                                }
                            } else {
                                if (tvVehicleImei != null) tvVehicleImei.setText("IMEI: Not linked");
                                if (tvGpsDeviceStatus != null) {
                                    tvGpsDeviceStatus.setText("GPS Device: Not linked");
                                    tvGpsDeviceStatus.setTextColor(Color.parseColor("#E53935"));
                                }
                            }

                            updateVehicleUI();
                        }
                    }
                } catch (Exception e) {
                    Log.e("VehiclesActivity", "Error fetching vehicles", e);
                }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("VehiclesActivity", "Failed to fetch vehicles", t);
            }
        });
    }

    // Update the vehicle name on the screen
    private void updateVehicleUI() {
        if (tvVehicleNameCollapsed != null) tvVehicleNameCollapsed.setText(selectedVehicleName);
        if (tvVehicleNameExpanded != null) tvVehicleNameExpanded.setText(selectedVehicleName);
    }

    @Override public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        trailRenderer = new VehicleTrailRenderer(this, mMap, trackingApi);
    }

    // Start fetching location every few seconds
    private void startRealTimeTracking() {
        if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable);
        trackingRunnable = new Runnable() {
            @Override public void run() {
                fetchLocationData();
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        handler.post(trackingRunnable);
    }

    // FIX: was trackingApi.getAllCurrentLocations() — the staff-only endpoint that 403s
    // for regular customers (the exact bug fixed in HomeActivity earlier tonight).
    // Now polls the single selected vehicle via the scoped, ownership-checked endpoint.
    /**
     * Handles a location pushed via SignalR (Option B). Mirrors fetchLocationData()'s
     * success path, but reads from RealtimeLocationPayload instead of LocationResponse
     * since the latter has no setters and can't be constructed from pushed data.
     */
    private void handlePushedLocation(RealtimeLocationPayload payload) {
        if (payload.getVehicleId() == null || mMap == null || !hasRealVehicle) return;
        LatLng pos = new LatLng(payload.getLatitude(), payload.getLongitude());
        if (pos.latitude == 0 && pos.longitude == 0) return;

        trailRenderer.updatePosition(pos, (float) payload.getHeading(), selectedVehicleName);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));

        if (tvVehicleNameCollapsed != null) tvVehicleNameCollapsed.setText(selectedVehicleName);

        addressResolver.resolveAddress(pos.latitude, pos.longitude, address -> {
            if (tvCollapsedAddress != null) tvCollapsedAddress.setText(address);
            if (tvExpandedAddress != null) tvExpandedAddress.setText(address);
        });

        String status = payload.getSpeed() > 0
                ? "Moving (" + (int) payload.getSpeed() + " km/h)"
                : (payload.isIgnitionOn() ? "Idle" : "Parked");
        int color = payload.getSpeed() > 0 ? ContextCompat.getColor(this, com.example.letstracklanka.R.color.status_moving) : Color.parseColor("#1976D2");
        if (tvCollapsedStatus != null) { tvCollapsedStatus.setText(status); tvCollapsedStatus.setTextColor(color); }
        if (tvExpandedStatus != null) { tvExpandedStatus.setText(status); tvExpandedStatus.setTextColor(color); }

        int dotColor = payload.isIgnitionOn() ? ContextCompat.getColor(this, com.example.letstracklanka.R.color.status_success) : Color.parseColor("#E53935");
        if (dotIgnition != null) dotIgnition.setCardBackgroundColor(dotColor);
        if (dotAC != null) dotAC.setCardBackgroundColor(dotColor);

        if (tvLastUpdated != null) tvLastUpdated.setText(String.format(Locale.getDefault(), "Sync: %s", new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(new Date())));
    }

    private void fetchLocationData() {
        if (mMap == null || !hasRealVehicle || selectedVehicleId == null) return;

        trackingApi.getVehicleLocation(selectedVehicleId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        LocationResponse loc = extractObject(body.string(), LocationResponse.class);
                        if (loc == null || loc.getVehicleId() == null) return;

                        LatLng pos = new LatLng(loc.getLatitude(), loc.getLongitude());
                        if (pos.latitude != 0 || pos.longitude != 0) {
                            lastKnownPosition = pos;   // used by the Nav grid action
                            trailRenderer.updatePosition(pos, loc.getHeading(), selectedVehicleName);
                            updateUI(loc);
                        }
                    } else if (response.code() == 404) {
                        // No location reported yet for this vehicle — normal, not an error.
                        Log.d("VehiclesActivity", "No current location yet for " + selectedVehicleId);
                    }
                } catch (Exception e) {
                    Log.e("VehiclesActivity", "Error fetching location", e);
                }
            }
            @Override public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e("VehiclesActivity", "Failed to fetch location", t);
            }
        });
    }

    // Change text, colors, and camera based on location data
    private void updateUI(LocationResponse loc) {
        LatLng pos = new LatLng(loc.getLatitude(), loc.getLongitude());
        if (pos.latitude == 0 && pos.longitude == 0) return;
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));

        if (tvVehicleNameCollapsed != null) tvVehicleNameCollapsed.setText(selectedVehicleName);

        addressResolver.resolveAddress(pos.latitude, pos.longitude, address -> {
            if (tvCollapsedAddress != null) tvCollapsedAddress.setText(address);
            if (tvExpandedAddress != null) tvExpandedAddress.setText(address);
        });

        String status = loc.getSpeed() > 0 ? "Moving (" + (int) loc.getSpeed() + " km/h)" : (loc.isIgnitionOn() ? "Idle" : "Parked");
        int color = loc.getSpeed() > 0 ? ContextCompat.getColor(this, com.example.letstracklanka.R.color.status_moving) : Color.parseColor("#1976D2");
        if (tvCollapsedStatus != null) { tvCollapsedStatus.setText(status); tvCollapsedStatus.setTextColor(color); }
        if (tvExpandedStatus != null) { tvExpandedStatus.setText(status); tvExpandedStatus.setTextColor(color); }

        int dotColor = loc.isIgnitionOn() ? ContextCompat.getColor(this, com.example.letstracklanka.R.color.status_success) : Color.parseColor("#E53935");
        if (dotIgnition != null) dotIgnition.setCardBackgroundColor(dotColor);
        if (dotAC != null) dotAC.setCardBackgroundColor(dotColor);

        // Update the 'Last Updated' time text
        if (tvLastUpdated != null) tvLastUpdated.setText(String.format(Locale.getDefault(), "Sync: %s", new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(new Date())));
    }

    /**
     * FIX: the old parseList() never unwrapped the API's envelope
     * ({"success":true,...,"data":[...]}) — it parsed the WHOLE envelope object as if it
     * were a single VehicleResponse/CustomerResponse, producing an object with every
     * field null. Both parseList (for arrays) and extractObject (for single objects)
     * now correctly reach into "data" first.
     */
    private <T> List<T> parseList(String json, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonArray()) {
                list = gson.fromJson(root.getAsJsonArray("data"), TypeToken.getParameterized(List.class, clazz).getType());
            } else if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                list.add(gson.fromJson(root.getAsJsonObject("data"), clazz));
            }
        } catch (Exception e) {
            Log.e("VehiclesActivity", "Error parsing list JSON", e);
        }
        return list;
    }

    private <T> T extractObject(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) return null;
        Gson gson = new Gson();
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root != null && root.has("data") && root.get("data").isJsonObject()) {
                return gson.fromJson(root.getAsJsonObject("data"), clazz);
            }
            return null;
        } catch (Exception e) {
            Log.e("VehiclesActivity", "Error parsing object JSON", e);
            return null;
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (trackingRunnable != null) handler.removeCallbacks(trackingRunnable);
        if (realtimeClient != null) realtimeClient.stop();   // NEW
    }
}