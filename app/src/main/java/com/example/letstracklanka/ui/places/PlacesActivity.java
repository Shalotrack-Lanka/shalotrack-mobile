package com.example.letstracklanka.ui.places;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.letstracklanka.R;
import com.example.letstracklanka.data.model.SavedPlaceResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;
import com.example.letstracklanka.ui.common.ConfirmSheet;
import com.example.letstracklanka.ui.common.EdgeToEdge;
import com.example.letstracklanka.ui.common.EmptyState;
import com.example.letstracklanka.ui.common.InlineBanner;
import com.example.letstracklanka.ui.common.NetworkWatcher;
import com.example.letstracklanka.ui.common.RowDividers;
import com.example.letstracklanka.ui.common.TopBar;
import com.example.letstracklanka.ui.more.MoreRows;
import com.example.letstracklanka.utils.ApiErrors;
import com.example.letstracklanka.utils.ApiJson;
import com.example.letstracklanka.utils.SecureScreen;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Places: the places the customer has named, each with its visit count (the server counts a visit when a
 * vehicle stops inside one). A place is saved from where a vehicle is right now, read from its tracker;
 * the phone's own location is never used. Frequent places (where vehicles stop most) is one row away.
 * All results and errors stay on the screen: no Toast, Snackbar or dialog.
 */
public class PlacesActivity extends AppCompatActivity {

    private ApiService api;

    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private RecyclerView list;
    private InlineBanner banner;
    private EmptyState empty;
    private SavedPlaceAdapter adapter;
    private boolean loaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_places);
        SecureScreen.guardTouches(this);
        TopBar.bind(this, R.string.st_places_title);
        EdgeToEdge.padBottom(findViewById(R.id.placesContent));

        api = ApiClient.getClient().create(ApiService.class);
        swipe = findViewById(R.id.swipePlaces);
        progress = findViewById(R.id.progressPlaces);
        list = findViewById(R.id.rvPlaces);
        banner = InlineBanner.in(this);
        empty = new EmptyState(findViewById(R.id.emptyState));

        swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.st_navy));
        swipe.setOnRefreshListener(() -> load(false));

        ViewGroup frequent = findViewById(R.id.cardFrequentPlaces);
        View row = MoreRows.add(frequent, R.drawable.ic_st_star, getString(R.string.st_places_frequent),
                getString(R.string.st_places_frequent_detail), false,
                v -> startActivity(new Intent(this, FrequentPlacesActivity.class)));
        ImageView icon = row.findViewById(R.id.moreRowIcon);
        icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.st_signal_text)));
        row.findViewById(R.id.moreRowIconTile).setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.st_signal_50)));

        adapter = new SavedPlaceAdapter(this::confirmRemove);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        RowDividers.attach(list, true);

        findViewById(R.id.btnSavePlace).setOnClickListener(v -> SavePlaceSheet.show(this, name -> {
            load(false);
            banner.showSuccess(getString(R.string.st_place_saved, name));
        }));

        NetworkWatcher.watch(this, new NetworkWatcher.Listener() {
            @Override
            public void onLost() {
                banner.showOffline(R.string.st_error_offline_short, null);
            }

            @Override
            public void onRestored() {
                banner.hide();
                load(false);
            }
        });

        load(true);
    }

    private void load(boolean spinner) {
        if (spinner && !loaded) progress.setVisibility(View.VISIBLE);
        api.getMyPlaces().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (isFinishing() || isDestroyed()) {
                    ApiErrors.close(response);
                    return;
                }
                done();
                List<SavedPlaceResponse> places = null;
                if (response.isSuccessful()) {
                    try (ResponseBody body = response.body()) {
                        if (body != null) places = ApiJson.list(body.string(), SavedPlaceResponse.class);
                    } catch (Exception ignored) {
                        places = null;
                    }
                } else {
                    ApiErrors.close(response);
                }
                if (places == null) {
                    failed(response.code() == 401 ? R.string.st_error_session_expired : R.string.st_places_load_failed);
                    return;
                }
                show(places);
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                done();
                failed(R.string.st_error_network);
            }
        });
    }

    private void done() {
        progress.setVisibility(View.GONE);
        swipe.setRefreshing(false);
    }

    private void show(List<SavedPlaceResponse> places) {
        loaded = true;
        List<SavedPlaceResponse> clean = new ArrayList<>();
        for (SavedPlaceResponse p : places) if (p != null && p.getPlaceId() != null) clean.add(p);
        adapter.updatePlaces(clean);
        list.setVisibility(clean.isEmpty() ? View.GONE : View.VISIBLE);
        if (clean.isEmpty()) {
            empty.show(getString(R.string.st_places_empty_title), getString(R.string.st_places_empty_body), null, null);
        } else {
            empty.hide();
        }
    }

    private void failed(int message) {
        banner.showError(message, () -> load(true));
        if (!loaded) empty.hide();
    }

    private void confirmRemove(SavedPlaceResponse place) {
        String name = place.getName() != null ? place.getName() : "";
        ConfirmSheet.show(this, getString(R.string.st_places_remove_title, name), getString(R.string.st_places_remove_body),
                getString(R.string.st_action_remove), getString(R.string.st_action_removing), done ->
                        api.deletePlace(place.getPlaceId()).enqueue(new Callback<ResponseBody>() {
                            @Override
                            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                                if (response.isSuccessful()) {
                                    ApiErrors.close(response);
                                    done.succeeded();
                                    if (isFinishing() || isDestroyed()) return;
                                    load(false);
                                    banner.showSuccess(getString(R.string.st_places_removed, name));
                                    return;
                                }
                                done.failed(ApiErrors.read(response, getString(R.string.st_places_remove_failed)).message);
                            }

                            @Override
                            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                                done.failed(getString(R.string.st_error_network));
                            }
                        }));
    }
}
