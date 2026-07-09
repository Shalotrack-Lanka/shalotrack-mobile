package com.example.letstracklanka.data.remote;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {
    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            try {
                // Get the token synchronously for the interceptor
                String token = Tasks.await(user.getIdToken(false)).getToken();
                request = request.newBuilder()
                        .addHeader("Authorization", "Bearer " + token)
                        .build();
            } catch (ExecutionException | InterruptedException e) {
                // Handle token retrieval failure
            }
        }
        return chain.proceed(request);
    }
}