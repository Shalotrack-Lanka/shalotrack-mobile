package com.example.letstracklanka.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.letstracklanka.data.repository.AuthRepository;
import com.google.firebase.auth.FirebaseAuth;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginViewModel extends ViewModel {
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final AuthRepository repository = new AuthRepository();

    private final MutableLiveData<String> authResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public void login(String email, String password) {
        isLoading.setValue(true);

        // Step 1: Firebase Authentication
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Step 2: Backend API Verification
                        repository.verifyCustomerBackend(email, new Callback<>() {
                            @Override
                            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                                isLoading.setValue(false);
                                if (response.isSuccessful()) {
                                    authResult.setValue("SUCCESS");
                                } else {
                                    authResult.setValue("Error: Backend verification failed.");
                                }
                            }

                            @Override
                            public void onFailure(Call<ResponseBody> call, Throwable t) {
                                isLoading.setValue(false);
                                authResult.setValue("Error: Network failure connecting to ShaloTrack.");
                            }
                        });
                    } else {
                        isLoading.setValue(false);
                        authResult.setValue("Error: " + task.getException().getMessage());
                    }
                });
    }

    public LiveData<String> getAuthResult() { return authResult; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
}