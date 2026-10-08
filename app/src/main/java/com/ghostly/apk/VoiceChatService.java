package com.ghostly.apk;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.*;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.*;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.*;

public class VoiceChatService extends Service {
    public static final String ACTION_START = "com.ghostly.apk.VOICE_START";
    public static final String ACTION_STOP = "com.ghostly.apk.VOICE_STOP";
    public static final String ACTION_TOGGLE_MUTE = "com.ghostly.apk.VOICE_TOGGLE_MUTE";
    public static final String ACTION_SHOW_OVERLAY = "com.ghostly.apk.VOICE_SHOW_OVERLAY";
    public static final String EXTRA_SERVER = "server";
    public static final String EXTRA_ROOM = "room";

    private static final String PREFS = "ghostly_voice";
    private static final String STATUS_ACTION = "com.ghostly.apk.VOICE_STATUS";
    private static final int NOTIFICATION_ID = 8301;
    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_SAMPLES = 320;
    private static final int FRAME_BYTES_PCM = FRAME_SAMPLES * 2;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final BlockingQueue<byte[]> playbackQueue = new LinkedBlockingQueue<>(25);

    private OkHttpClient client;
    private WebSocket socket;
    private AudioRecord recorder;
    private AudioTrack player;
    private AcousticEchoCanceler aec;
    private NoiseSuppressor ns;
    private Thread captureThread;
    private Thread playbackThread;
    private volatile boolean muted = false;
    private volatile String currentRoom = "";
    private WindowManager windowManager;
    private View overlayView;

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        client = new OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopVoice();
            return START_NOT_STICKY;
        }

        if (ACTION_TOGGLE_MUTE.equals(action)) {
            muted = !muted;
            publish("🎙 " + (muted ? "Микрофон выключен" : "Микрофон включён"));
            updateNotification();
            updateOverlay();
            return START_STICKY;
        }

        if (ACTION_SHOW_OVERLAY.equals(action)) {
            showOverlay();
            return START_STICKY;
        }

        if (ACTION_START.equals(action)) {
            String server = intent.getStringExtra(EXTRA_SERVER);
            String room = intent.getStringExtra(EXTRA_ROOM);
            if (server != null && room != null) {
                startVoice(server.trim(), room.trim().toUpperCase());
            }
        }

        return START_STICKY;
    }

    private synchronized void startVoice(String server, String room) {
        if (running.get()) stopVoice();

        if (!server.endsWith("/ws")) {
            if (server.endsWith("/")) server = server.substring(0, server.length()-1);
            server += "/ws";
        }
        server = normalizeWs(server);

        currentRoom = room;
        muted = false;
        running.set(true);
        saveState("⏳ Подключение к голосовой комнате…", room);
        promoteToForeground();

        Request request = new Request.Builder().url(server).build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                webSocket.send("JOIN|" + room);
                publish("🟢 Подключено. Ждём второго игрока…");
                startAudio();
                if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("overlay", false)) {
                    showOverlay();
                }
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                if (text.startsWith("ROOM_COUNT|")) {
                    String count = text.substring("ROOM_COUNT|".length());
                    if ("2".equals(count)) publish("🟢 В голосовом чате 2 игрока.");
                    else publish("🟡 В комнате " + count + " игрок.");
                } else if (text.startsWith("ERROR|")) {
                    publish("❌ " + text.substring(6));
                }
            }

            @Override public void onMessage(WebSocket webSocket, okio.ByteString bytes) {
                byte[] frame = bytes.toByteArray();
                if (frame.length == FRAME_SAMPLES) {
                    if (playbackQueue.remainingCapacity() == 0) playbackQueue.poll();
                    playbackQueue.offer(frame);
                }
            }

            @Override public void onClosing(WebSocket webSocket, int code, String reason) {
                publish("⚠️ Соединение закрывается…");
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                if (running.get()) publish("⚠️ Голосовой сервер отключил соединение.");
            }

            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                publish("❌ Ошибка голоса: " + (t.getMessage() == null ? "соединение потеряно" : t.getMessage()));
            }
        });
    }

    private String normalizeWs(String url) {
        if (url.startsWith("https://")) return "wss://" + url.substring(8);
        if (url.startsWith("http://")) return "ws://" + url.substring(7);
        return url;
    }

    private void startAudio() {
        if (recorder != null || player != null) return;

        try {
            AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);

            int minIn = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int minOut = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

            int inBuffer = Math.max(minIn * 2, FRAME_BYTES_PCM * 4);
            int outBuffer = Math.max(minOut * 2, FRAME_BYTES_PCM * 4);

            recorder = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build())
                .setBufferSizeInBytes(inBuffer)
                .build();

            player = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(outBuffer)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    aec = AcousticEchoCanceler.create(recorder.getAudioSessionId());
                    if (aec != null) aec.setEnabled(true);
                }
            } catch (Exception ignored) {}

            try {
                if (NoiseSuppressor.isAvailable()) {
                    ns = NoiseSuppressor.create(recorder.getAudioSessionId());
                    if (ns != null) ns.setEnabled(true);
                }
            } catch (Exception ignored) {}

            recorder.startRecording();
            player.play();

            captureThread = new Thread(this::captureLoop, "GhostlyVoiceCapture");
            playbackThread = new Thread(this::playbackLoop, "GhostlyVoicePlayback");
            captureThread.start();
            playbackThread.start();

            publish("🎙 Голос работает. Можно открывать Standoff 2.");
        } catch (Exception e) {
            publish("❌ Не удалось запустить микрофон: " + e.getMessage());
            stopVoice();
        }
    }

    private void captureLoop() {
        short[] pcm = new short[FRAME_SAMPLES];
        while (running.get() && recorder != null) {
            try {
                int n = recorder.read(pcm, 0, FRAME_SAMPLES);
                if (n != FRAME_SAMPLES) continue;

                byte[] encoded = new byte[FRAME_SAMPLES];
                if (muted) {
                    for (int i=0;i<encoded.length;i++) encoded[i] = (byte)0xFF;
                } else {
                    for (int i=0;i<FRAME_SAMPLES;i++) encoded[i] = MuLaw.encode(pcm[i]);
                }

                WebSocket ws = socket;
                if (ws != null) ws.send(okio.ByteString.of(encoded));
            } catch (Exception e) {
                break;
            }
        }
    }

    private void playbackLoop() {
        while (running.get() && player != null) {
            try {
                byte[] encoded = playbackQueue.poll(500, TimeUnit.MILLISECONDS);
                if (encoded == null) continue;

                short[] pcm = new short[FRAME_SAMPLES];
                for (int i=0;i<FRAME_SAMPLES;i++) pcm[i] = MuLaw.decode(encoded[i]);

                player.write(pcm, 0, pcm.length);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                break;
            }
        }
    }

    private synchronized void stopVoice() {
        running.set(false);
        hideOverlay();

        try { if (socket != null) socket.close(1000, "bye"); } catch (Exception ignored) {}
        socket = null;

        try { if (recorder != null) recorder.stop(); } catch (Exception ignored) {}
        try { if (recorder != null) recorder.release(); } catch (Exception ignored) {}
        recorder = null;

        try { if (player != null) player.stop(); } catch (Exception ignored) {}
        try { if (player != null) player.release(); } catch (Exception ignored) {}
        player = null;

        try { if (aec != null) aec.release(); } catch (Exception ignored) {}
        aec = null;
        try { if (ns != null) ns.release(); } catch (Exception ignored) {}
        ns = null;

        playbackQueue.clear();

        try {
            AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
            am.setMode(AudioManager.MODE_NORMAL);
        } catch (Exception ignored) {}

        publish("✅ Голосовой чат завершён.");
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void promoteToForeground() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, "ghostly_voice")
            : new Notification.Builder(this);

        Intent mute = new Intent(this, VoiceControlReceiver.class).setAction(VoiceControlReceiver.ACTION_MUTE);
        PendingIntent mutePi = PendingIntent.getBroadcast(this, 1, mute,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, VoiceControlReceiver.class).setAction(VoiceControlReceiver.ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getBroadcast(this, 2, stop,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("👻 Ghostly Voice")
            .setContentText("Голос работает в фоне")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .addAction(new Notification.Action.Builder(null, "Микрофон", mutePi).build())
            .addAction(new Notification.Action.Builder(null, "Завершить", stopPi).build());

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, builder.build());
        }
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, "ghostly_voice")
            : new Notification.Builder(this);

        Intent mute = new Intent(this, VoiceControlReceiver.class).setAction(VoiceControlReceiver.ACTION_MUTE);
        PendingIntent mutePi = PendingIntent.getBroadcast(this, 1, mute,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, VoiceControlReceiver.class).setAction(VoiceControlReceiver.ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getBroadcast(this, 2, stop,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("👻 Ghostly Voice")
            .setContentText(muted ? "Микрофон выключен" : "Голос работает в фоне")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .addAction(new Notification.Action.Builder(null, muted ? "Включить" : "Микрофон", mutePi).build())
            .addAction(new Notification.Action.Builder(null, "Завершить", stopPi).build());

        nm.notify(NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannel() {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                "ghostly_voice", "Ghostly Voice", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Голосовой чат Ghostly во время игры");
            nm.createNotificationChannel(ch);
        }
    }

    private void publish(String message) {
        saveState(message, currentRoom);
        Intent i = new Intent(STATUS_ACTION);
        i.setPackage(getPackageName());
        i.putExtra("status", message);
        i.putExtra("room", currentRoom);
        sendBroadcast(i);
        updateOverlay();
    }

    private void saveState(String message, String room) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("status", message)
            .putString("room", room == null ? "" : room)
            .putBoolean("running", running.get())
            .apply();
    }

    private void showOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;
        if (overlayView != null) return;

        windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(8), dp(6), dp(6), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEE17131F);
        bg.setCornerRadius(dp(22));
        box.setBackground(bg);

        TextView mic = new TextView(this);
        mic.setText("🎙");
        mic.setTextSize(20);
        mic.setGravity(Gravity.CENTER);
        mic.setTextColor(Color.WHITE);
        mic.setOnClickListener(v -> {
            muted = !muted;
            publish("🎙 " + (muted ? "Микрофон выключен" : "Микрофон включён"));
            updateNotification();
        });

        TextView end = new TextView(this);
        end.setText("✕");
        end.setTextSize(18);
        end.setTextColor(Color.WHITE);
        end.setGravity(Gravity.CENTER);
        end.setPadding(dp(8),0,dp(8),0);
        end.setOnClickListener(v -> stopVoice());

        box.addView(mic, new LinearLayout.LayoutParams(dp(42), dp(42)));
        box.addView(end, new LinearLayout.LayoutParams(dp(36), dp(42)));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            dp(90), dp(54),
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = dp(10);
        lp.y = dp(90);

        try {
            windowManager.addView(box, lp);
            overlayView = box;
        } catch (Exception ignored) {}
    }

    private void updateOverlay() {
        if (overlayView instanceof ViewGroup) {
            TextView mic = findFirstTextView((ViewGroup)overlayView);
            if (mic != null) mic.setText(muted ? "🔇" : "🎙");
        }
    }

    private TextView findFirstTextView(ViewGroup group) {
        for (int i=0;i<group.getChildCount();i++) {
            View v = group.getChildAt(i);
            if (v instanceof TextView) return (TextView)v;
        }
        return null;
    }

    private void hideOverlay() {
        try {
            if (windowManager != null && overlayView != null) windowManager.removeView(overlayView);
        } catch (Exception ignored) {}
        overlayView = null;
    }

    @Override public void onDestroy() {
        running.set(false);
        hideOverlay();
        try { if (socket != null) socket.cancel(); } catch (Exception ignored) {}
        socket = null;
        try { if (recorder != null) recorder.stop(); } catch (Exception ignored) {}
        try { if (recorder != null) recorder.release(); } catch (Exception ignored) {}
        recorder = null;
        try { if (player != null) player.stop(); } catch (Exception ignored) {}
        try { if (player != null) player.release(); } catch (Exception ignored) {}
        player = null;
        try { if (aec != null) aec.release(); } catch (Exception ignored) {}
        aec = null;
        try { if (ns != null) ns.release(); } catch (Exception ignored) {}
        ns = null;
        if (client != null) client.dispatcher().executorService().shutdown();
        try {
            AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
            am.setMode(AudioManager.MODE_NORMAL);
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // Keep the voice service alive; Android still controls process lifecycle.
        super.onTaskRemoved(rootIntent);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private static int dp(int v) {
        return Math.round(v * android.content.res.Resources.getSystem().getDisplayMetrics().density);
    }

    static final class MuLaw {
        private static final int BIAS = 0x84;
        private static final int CLIP = 32635;

        static byte encode(short sample) {
            int s = sample;
            int sign = (s < 0) ? 0x80 : 0;
            if (s < 0) s = -s;
            if (s > CLIP) s = CLIP;
            s += BIAS;
            int exponent = 7;
            for (int expMask = 0x4000; (s & expMask) == 0 && exponent > 0; expMask >>= 1) exponent--;
            int mantissa = (s >> (exponent + 3)) & 0x0F;
            int ulaw = ~(sign | (exponent << 4) | mantissa);
            return (byte)ulaw;
        }

        static short decode(byte value) {
            int u = ~value & 0xFF;
            int sign = u & 0x80;
            int exponent = (u >> 4) & 0x07;
            int mantissa = u & 0x0F;
            int sample = ((mantissa << 3) + BIAS) << exponent;
            sample -= BIAS;
            return (short)(sign != 0 ? -sample : sample);
        }
    }
}
