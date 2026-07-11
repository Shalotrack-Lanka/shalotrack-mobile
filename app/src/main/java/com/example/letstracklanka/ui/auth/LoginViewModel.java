package com.example.letstracklanka.ui.auth;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.letstracklanka.data.repository.AuthRepository;
import com.google.firebase.auth.FirebaseAuth;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@SuppressWarnings("unused")
public class LoginViewModel extends ViewModel {
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final AuthRepository repository = new AuthRepository();

    private final MutableLiveData<String> authResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public void login(String email, String password) {
        isLoading.setValue(true);

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        repository.verifyCustomerBackend(email, new Callback<ResponseBody>() {
                            @Override
                            public void onResponse(@NonNull Call<ResponseBody> call,
                                                   @NonNull Response<ResponseBody> response) {
                                isLoading.setValue(false);
                                if (response.isSuccessful()) {
                                    authResult.setValue("SUCCESS");
                                } else {
                                    authResult.setValue("Error: Backend verification failed.");
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<ResponseBody> call,
                                                  @NonNull Throwable t) {
                                isLoading.setValue(false);
                                String error = t.getMessage() != null ? t.getMessage() : "Unknown network error";
                                authResult.setValue("Error: " + error);
                            }
                        });
                    } else {
                        isLoading.setValue(false);
                        String error = task.getException() != null ? task.getException().getMessage() : "Authentication failed";
                        authResult.setValue("Error: " + error);
                    }
                });
    }

    public LiveData<String> getAuthResult() { return authResult; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
}
