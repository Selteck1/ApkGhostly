package com.kemtiz.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class KemtizPushService extends FirebaseMessagingService {
    public static final String MESSAGE_CHANNEL = "kemtiz_messages_v3";
    public static final String CALL_CHANNEL = "kemtiz_calls_v3";
    public static final int CALL_NOTIFICATION_ID = 27182;
    private static final String PREFS = "kemtiz";
    private static final String DEFAULT_SERVER = "https://laptop-t1f8ihla.tail5fc627.ts.net";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS).build();

    public static void ensureNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel messages = new NotificationChannel(
                MESSAGE_CHANNEL, "Kemtiz — сообщения", NotificationManager.IMPORTANCE_HIGH);
        messages.setDescription("Новые сообщения в чатах");
        messages.enableVibration(true);
        messages.setVibrationPattern(new long[]{0, 220, 120, 220});
        messages.enableLights(true);
        messages.setLightColor(Color.RED);
        messages.setShowBadge(true);
        manager.createNotificationChannel(messages);

        NotificationChannel calls = new NotificationChannel(
                CALL_CHANNEL, "Kemtiz — звонки", NotificationManager.IMPORTANCE_HIGH);
        calls.setDescription("Входящие видеозвонки");
        calls.enableVibration(true);
        calls.setVibrationPattern(new long[]{0, 450, 180, 450, 180, 450});
        calls.enableLights(true);
        calls.setLightColor(Color.RED);
        calls.setShowBadge(true);
        manager.createNotificationChannel(calls);
    }

    public static void registerCurrentToken(Context context) {
        Context app = context.getApplicationContext();
        if (!KemtizApplication.isFirebaseConfigured(app)) return;
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> uploadToken(app, token));
        } catch (Exception ignored) { }
    }

    private static void uploadToken(Context context, String fcmToken) {
        if (fcmToken == null || fcmToken.trim().isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String base = trimSlash(prefs.getString("server_base", DEFAULT_SERVER));
        String auth = prefs.getString("token", "");
        if (auth.isEmpty()) return;
        JSONObject body = new JSONObject();
        try { body.put("token", fcmToken); body.put("platform", "android"); }
        catch (JSONException ignored) { return; }
        Request request = new Request.Builder().url(base + "/api/devices/fcm-token")
                .header("Authorization", "Bearer " + auth)
                .post(RequestBody.create(JSON, body.toString())).build();
        HTTP.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { }
            @Override public void onResponse(Call call, Response response) { response.close(); }
        });
    }

    public static void unregisterCurrentToken(Context context) {
        Context app = context.getApplicationContext();
        if (!KemtizApplication.isFirebaseConfigured(app)) return;
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String auth = prefs.getString("token", "");
        String base = trimSlash(prefs.getString("server_base", DEFAULT_SERVER));
        if (auth.isEmpty()) return;
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(fcmToken -> {
                JSONObject body = new JSONObject();
                try { body.put("token", fcmToken); }
                catch (JSONException ignored) { return; }
                Request request = new Request.Builder().url(base + "/api/devices/fcm-token")
                        .header("Authorization", "Bearer " + auth)
                        .delete(RequestBody.create(JSON, body.toString())).build();
                HTTP.newCall(request).enqueue(new Callback() {
                    @Override public void onFailure(Call call, IOException e) { }
                    @Override public void onResponse(Call call, Response response) { response.close(); }
                });
            });
        } catch (Exception ignored) { }
    }

    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        uploadToken(getApplicationContext(), token);
    }

    @Override public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        Map<String, String> data = message.getData();
        if (data == null || data.isEmpty() || KemtizApplication.isAppInForeground()) return;
        String type = data.get("type");
        if ("message".equals(type)) showMessage(data);
        else if ("call.incoming".equals(type)) showIncomingCall(data);
    }

    private void showMessage(Map<String, String> data) {
        if (!notificationsAllowed()) return;
        String title = data.getOrDefault("title", "Новое сообщение");
        String body = data.getOrDefault("body", "Тебе написали в Kemtiz");
        long chatId = parseLong(data.get("chat_id"), -1);
        if (chatId <= 0) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int badge = prefs.getInt("notification_badge_count", 0) + 1;
        prefs.edit().putInt("notification_badge_count", badge).apply();
        Intent open = new Intent(this, KemtizActivity.class);
        open.putExtra("open_chat_id", chatId);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 4100 + (int)(chatId % 500000), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, MESSAGE_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.rgb(229, 48, 67))
                .setContentTitle(title).setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(pending).setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE).setShowWhen(true).setNumber(badge)
                .setVibrate(new long[]{0, 220, 120, 220}).build();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(10000 + (int)(chatId % 90000), notification);
    }

    private void showIncomingCall(Map<String, String> data) {
        if (!notificationsAllowed()) return;
        long chatId = parseLong(data.get("chat_id"), -1);
        long callerId = parseLong(data.get("from_user_id"), -1);
        String callId = data.getOrDefault("call_id", "");
        String callerName = data.getOrDefault("from_display_name", data.getOrDefault("from_username", "Пользователь"));
        if (chatId <= 0 || callerId <= 0 || callId.isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Intent accept = new Intent(this, CallActivity.class);
        accept.putExtra("server_base", prefs.getString("server_base", DEFAULT_SERVER));
        accept.putExtra("token", prefs.getString("token", ""));
        accept.putExtra("chat_id", chatId);
        accept.putExtra("target_id", callerId);
        accept.putExtra("target_name", callerName);
        accept.putExtra("call_id", callId);
        accept.putExtra("mode", "answer");
        accept.putExtra("video", true);
        PendingIntent acceptPending = PendingIntent.getActivity(this, CALL_NOTIFICATION_ID, accept,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CALL_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.rgb(229, 48, 67))
                .setContentTitle("Входящий видеозвонок").setContentText(callerName + " звонит тебе")
                .setContentIntent(acceptPending)
                .addAction(android.R.drawable.ic_menu_call, "Принять", acceptPending)
                .setAutoCancel(true).setCategory(Notification.CATEGORY_CALL).setShowWhen(true)
                .setNumber(1).setVibrate(new long[]{0, 450, 180, 450, 180, 450}).build();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(CALL_NOTIFICATION_ID, notification);
    }

    public static void clearNotifications(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancelAll();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt("notification_badge_count", 0).apply();
    }

    private boolean notificationsAllowed() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private static long parseLong(String value, long fallback) {
        try { return Long.parseLong(value); } catch (Exception ignored) { return fallback; }
    }

    private static String trimSlash(String base) {
        String value = base == null || base.trim().isEmpty() ? DEFAULT_SERVER : base.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
