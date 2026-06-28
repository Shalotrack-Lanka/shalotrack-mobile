package com.example.letstracklanka; // ඔයාගේ package name එක

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home); // ඔයාගේ XML ෆයිල් එකේ නම

        // Map Fragment එක හොයාගෙන Map එක Load වෙන්න දෙනවා
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    // Map එක සම්පූර්ණයෙන්ම Load වුණාට පස්සේ මේක වැඩ කරන්නේ
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // ආරම්භක ස්ථානය විදිහට ශ්‍රී ලංකාවේ මැද ලක්ෂ්‍යය (කැන්ඩි/මාතලේ හරිය) දෙනවා
        LatLng sriLanka = new LatLng(7.8731, 80.7718);

        // ඒ තැනට පොඩි මාකර් එකක් (පින් එකක්) දානවා
        mMap.addMarker(new MarkerOptions().position(sriLanka).title("Welcome to ShaloTrack!"));

        // Map එකේ කැමරාව ලංකාවට Zoom කරනවා (Zoom level 8)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(sriLanka, 8f));
    }
}