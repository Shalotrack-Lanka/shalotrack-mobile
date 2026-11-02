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

/**
 * Adds the customer's Firebase ID token to API requests.
 *
 * The token is attached ONLY to https requests for our own API host. Before this guard it was added to every
 * request the client made, so a redirect or a future call to another host would have handed the customer's
 * login token to that host.
 */
public class AuthInterceptor implements Interceptor {
    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();

        if (!ApiClient.isTrustedApiUrl(request.url())) {
            return chain.proceed(request);
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            try {
                String token = Tasks.await(user.getIdToken(false)).getToken();
                request = request.newBuilder()
                        .header("Authorization", "Bearer " + token)
                        .build();
            } catch (ExecutionException e) {
                throw new IOException("Token retrieval failed", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Token retrieval interrupted", e);
            }
        }

        return chain.proceed(request);
    }
}
