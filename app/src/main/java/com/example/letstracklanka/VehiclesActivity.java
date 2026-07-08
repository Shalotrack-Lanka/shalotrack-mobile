package com.example.letstracklanka;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class VehiclesActivity extends AppCompatActivity {

    private RecyclerView recyclerVehicles;
    private SwipeRefreshLayout swipeRefreshVehicles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicles);

        recyclerVehicles = findViewById(R.id.recyclerVehicles);
        swipeRefreshVehicles = findViewById(R.id.swipeRefreshVehicles);

        recyclerVehicles.setLayoutManager(new LinearLayoutManager(this));

        // පල්ලෙහාට අදිනකොට (Pull to refresh) දත්ත අලුත් වෙන කෑල්ල
        swipeRefreshVehicles.setOnRefreshListener(() -> {
            loadVehiclesFromApi();
        });

        // මුලින්ම ඇප් එකට එනකොට දත්ත ලෝඩ් කිරීම
        loadVehiclesFromApi();
    }

    private void loadVehiclesFromApi() {
        swipeRefreshVehicles.setRefreshing(true);

        // මෙතන තමයි අපි Retrofit API කෝල් එක ලියන්නේ
        // ඊළඟ පියවරේදී අපි ShaloTrack API එකෙන් එන දත්ත අරගෙන මේකට සම්බන්ධ කරමු!

        // දැනට ටෙස්ට් කරන්න නිකම්ම Refresh එක නවත්තමු
        swipeRefreshVehicles.postDelayed(() -> {
            swipeRefreshVehicles.setRefreshing(false);
            Toast.makeText(this, "Vehicles loaded!", Toast.LENGTH_SHORT).show();
        }, 1500);
    }
}