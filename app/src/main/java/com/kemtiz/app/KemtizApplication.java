package com.kemtiz.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import org.json.JSONObject;

/** Restores public Firebase settings before FCM starts a cold app process. */
public class KemtizApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private static volatile int startedActivities = 0;
    private static volatile boolean firebaseReady = false;
    private static final Object FIREBASE_LOCK = new Object();

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
        initializeFirebaseFromSavedConfig(this);
        KemtizPushService.ensureNotificationChannels(this);
    }

    public static boolean isAppInForeground() {
        return startedActivities > 0;
    }

    public static boolean isFirebaseConfigured(Context context) {
        if (firebaseReady) return true;
        return initializeFirebaseFromSavedConfig(context.getApplicationContext());
    }

    /** Config is public Firebase Android config fetched from the user's own Kemtiz API. */
    public static boolean configureFirebase(Context context, JSONObject config) {
        if (config == null || !config.optBoolean("configured", false)) return false;
        String apiKey = config.optString("api_key", "").trim();
        String appId = config.optString("app_id", "").trim();
        String projectId = config.optString("project_id", "").trim();
        String senderId = config.optString("sender_id", "").trim();
        if (apiKey.isEmpty() || appId.isEmpty() || projectId.isEmpty() || senderId.isEmpty()) return false;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences("kemtiz_firebase", MODE_PRIVATE);
        prefs.edit().putString("api_key", apiKey).putString("app_id", appId)
                .putString("project_id", projectId).putString("sender_id", senderId).apply();
        return initializeFirebaseFromSavedConfig(context.getApplicationContext());
    }

    private static boolean initializeFirebaseFromSavedConfig(Context context) {
        synchronized (FIREBASE_LOCK) {
            try {
                if (!FirebaseApp.getApps(context).isEmpty()) {
                    firebaseReady = true;
                    return true;
                }
                SharedPreferences prefs = context.getSharedPreferences("kemtiz_firebase", MODE_PRIVATE);
                String apiKey = prefs.getString("api_key", "");
                String appId = prefs.getString("app_id", "");
                String projectId = prefs.getString("project_id", "");
                String senderId = prefs.getString("sender_id", "");
                if (apiKey.isEmpty() || appId.isEmpty() || projectId.isEmpty() || senderId.isEmpty()) return false;
                FirebaseOptions options = new FirebaseOptions.Builder()
                        .setApiKey(apiKey).setApplicationId(appId).setProjectId(projectId)
                        .setGcmSenderId(senderId).build();
                FirebaseApp.initializeApp(context, options);
                firebaseReady = !FirebaseApp.getApps(context).isEmpty();
                return firebaseReady;
            } catch (Exception ignored) {
                firebaseReady = false;
                return false;
            }
        }
    }

    @Override public void onActivityStarted(Activity activity) { startedActivities++; }
    @Override public void onActivityStopped(Activity activity) { startedActivities = Math.max(0, startedActivities - 1); }
    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityResumed(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    @Override public void onActivityDestroyed(Activity activity) { }
}
