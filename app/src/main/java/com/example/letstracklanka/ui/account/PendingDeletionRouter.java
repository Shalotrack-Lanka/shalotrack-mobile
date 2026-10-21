package com.example.letstracklanka.ui.account;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * App-wide handler for "this account is waiting for deletion" (HTTP 403 ACCOUNT_PENDING_DELETION).
 * Install once from ShaloTrackApp.onCreate.
 *
 * Opens AccountDeletionActivity as the root of a fresh task, so Back cannot return to screens that
 * will only fail. Several requests usually fail together, so only one launch happens; the flag is
 * released when that screen is destroyed. Android blocks starting activities from the background,
 * so nothing is launched unless one of our screens is currently in front.
 */
public final class PendingDeletionRouter {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean launching = new AtomicBoolean(false);
    private static Application app;
    private static int resumedCount = 0;
    private static boolean installed = false;

    private PendingDeletionRouter() {
    }

    public static void install(@NonNull Application application) {
        if (installed) return;
        installed = true;
        app = application;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(@NonNull Activity activity) {
                resumedCount++;
            }

            @Override public void onActivityPaused(@NonNull Activity activity) {
                if (resumedCount > 0) resumedCount--;
            }

            @Override public void onActivityDestroyed(@NonNull Activity activity) {
                if (activity instanceof AccountDeletionActivity) launching.set(false);
            }

            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { }
            @Override public void onActivityStarted(@NonNull Activity a) { }
            @Override public void onActivityStopped(@NonNull Activity a) { }
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }
        });
    }

    /** Safe to call from any thread (the OkHttp interceptor runs on a worker). */
    public static void onPendingDeletion() {
        MAIN.post(PendingDeletionRouter::launchIfAppropriate);
    }

    private static void launchIfAppropriate() {
        if (app == null || resumedCount <= 0) return;
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        if (!launching.compareAndSet(false, true)) return;

        Intent intent = new Intent(app, AccountDeletionActivity.class);
        intent.putExtra(AccountDeletionActivity.EXTRA_FROM_LOCK, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            app.startActivity(intent);
        } catch (RuntimeException e) {
            launching.set(false);
        }
    }
}