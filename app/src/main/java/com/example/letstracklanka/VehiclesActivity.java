package com.example.letstracklanka;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VehiclesActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ShaloTrackApi apiService;
    private TextView tvDeviceStatus, tvDeviceAddress;

    // Real-time අප්ඩේට් කරන්න ඕනේ කරන දේවල්
    private Handler handler = new Handler();
    private Runnable runnable;
    private final int UPDATE_INTERVAL = 10000; // තත්පර 10කට වරක් අප්ඩේට් වේ
    private String selectedDeviceId = "DEMO_DEVICE_001"; // ඔයාගේ Device ID එක

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicles);

        // UI කොටස් හොයාගැනීම
        tvDeviceStatus = findViewById(R.id.tvDeviceStatus);
        tvDeviceAddress = findViewById(R.id.tvDeviceAddress);

        // API සේවාව පටන් ගැනීම
        apiService = ApiClient.getClient().create(ShaloTrackApi.class);

        // Map එක ලෝඩ් කිරීම
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapVehicles);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Real-time Update එක පටන් ගන්නවා
        startRealTimeTracking();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        // මුලින්ම Map එක ආවම එක පාරක් දත්ත ගන්නවා
        fetchRealTimeVehicleData();
    }

    private void startRealTimeTracking() {
        runnable = new Runnable() {
            @Override
            public void run() {
                fetchRealTimeVehicleData();
                handler.postDelayed(this, UPDATE_INTERVAL); // ආයේ තත්පර 10කින් දුවනවා
            }
        };
        handler.post(runnable);
    }

    private void fetchRealTimeVehicleData() {
        if (apiService == null || mMap == null) return;

        // 1. ලොකේෂන් එක අරන් Map එක අප්ඩේට් කරනවා
        apiService.getCurrentLocation(selectedDeviceId).enqueue(new Callback<LocationResponse>() {
            @Override
            public void onResponse(Call<LocationResponse> call, Response<LocationResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    double lat = response.body().getLatitude();
                    double lng = response.body().getLongitude();
                    LatLng carLocation = new LatLng(lat, lng);

                    mMap.clear(); // පරණ මාකර් මකනවා
                    mMap.addMarker(new MarkerOptions().position(carLocation).title("LT Demo Device"));
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(carLocation, 16f)); // වාහනේ දිහාට Map එක Zoom වෙනවා

                    tvDeviceAddress.setText("Location updated: " + lat + ", " + lng); // දැනට අක්ෂාංශ/දේශාංශ පෙන්වමු
                }
            }

            @Override
            public void onFailure(Call<LocationResponse> call, Throwable t) {
                // Error එකක් ආවොත්
            }
        });

        // 2. වාහනේ යනවාද නවත්වලාද (Speed) බලනවා
        apiService.getDeviceStatus(selectedDeviceId).enqueue(new Callback<StatusResponse>() {
            @Override
            public void onResponse(Call<StatusResponse> call, Response<StatusResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    double speed = response.body().getSpeed();

                    if (speed > 0) {
                        tvDeviceStatus.setText("Moving (" + speed + " km/h)");
                        tvDeviceStatus.setTextColor(Color.parseColor("#00BFA5")); // යනවා නම් කොළ පාට
                    } else {
                        tvDeviceStatus.setText("Parked ");
                        tvDeviceStatus.setTextColor(Color.parseColor("#1877F2")); // නවත්වලා නම් නිල් පාට
                    }
                }
            }

            @Override
            public void onFailure(Call<StatusResponse> call, Throwable t) { }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // ඇප් එකෙන් එළියට යද්දී Real-time අප්ඩේට් වෙන එක නවත්තනවා (බැටරිය බේරගන්න)
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }
}