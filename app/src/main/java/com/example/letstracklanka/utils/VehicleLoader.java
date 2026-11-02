package com.example.letstracklanka.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.letstracklanka.data.model.CustomerResponse;
import com.example.letstracklanka.data.model.VehicleResponse;
import com.example.letstracklanka.data.remote.ApiClient;
import com.example.letstracklanka.data.remote.ApiService;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Loads the signed-in customer's vehicles (profile, then vehicles) for screens that need a vehicle picker. */
public final class VehicleLoader {

    private static final String TAG = "VehicleLoader";

    public interface Callback2 {
        void onLoaded(List<VehicleResponse> vehicles);
        void onError(String message);
    }

    private VehicleLoader() {}

    public static void load(Callback2 cb) {
        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.getMyProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        CustomerResponse c = ApiJson.object(body.string(), CustomerResponse.class);
                        if (c != null && c.getCustomerId() != null) {
                            loadVehicles(api, c.getCustomerId(), cb);
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "profile parse error", e);
                }
                cb.onError("We couldn't load your vehicles.");
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                cb.onError("No connection. Check your internet and try again.");
            }
        });
    }

    private static void loadVehicles(ApiService api, String customerId, Callback2 cb) {
        api.getVehiclesByCustomer(customerId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                try (ResponseBody body = response.body()) {
                    if (response.isSuccessful() && body != null) {
                        List<VehicleResponse> list = ApiJson.list(body.string(), VehicleResponse.class);
                        if (list != null) { cb.onLoaded(list); return; }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "vehicles parse error", e);
                }
                cb.onError("We couldn't load your vehicles.");
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                cb.onError("No connection. Check your internet and try again.");
            }
        });
    }
}
