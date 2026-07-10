package com.example.letstracklanka.data.remote;

import android.util.Log;
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
        
        // Skip adding token for registration endpoint if you suspect the backend rejects it during signup
        // However, usually, a Bearer token is fine. Let's add more logs to see the actual request.
        
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            try {
                // Get the token synchronously for the interceptor
                String token = Tasks.await(user.getIdToken(false)).getToken();
                Log.d("AUTH_INTERCEPTOR", "Adding Bearer Token to request: " + request.url());
                request = request.newBuilder()
                        .addHeader("Authorization", "Bearer " + token)
                        .build();
            } catch (ExecutionException | InterruptedException e) {
                Log.e("AUTH_INTERCEPTOR", "Token retrieval failed: " + e.getMessage());
            }
        }
        
        return chain.proceed(request);
    }
}
