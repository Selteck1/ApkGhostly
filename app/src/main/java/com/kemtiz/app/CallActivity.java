package com.kemtiz.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.RendererCommon;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/** Direct one-to-one WebRTC video call. Kemtiz only handles call signalling. */
public class CallActivity extends Activity {
    private static final int REQ_MEDIA = 819;
    private static final int BG = Color.rgb(9, 10, 15);
    private static final int WHITE = Color.rgb(246, 243, 252);
    private static boolean factoryInitialized = false;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();
    private String serverBase = "";
    private String token = "";
    private String targetName = "Собеседник";
    private String callId = "";
    private long chatId = -1;
    private long targetId = -1;
    private boolean outgoing;
    private boolean micEnabled = true;
    private boolean cameraEnabled = true;
    private boolean socketReady;
    private boolean callAccepted;
    private boolean rejectForPermissions;
    private boolean remoteDescriptionSet;
    private boolean mediaPrepared;
    private boolean ended;
    private int previousAudioMode = AudioManager.MODE_NORMAL;
    private boolean previousSpeakerphone;
    private WebSocket socket;
    private AudioManager audioManager;
    private FrameLayout frame;
    private FrameLayout localTile;
    private SurfaceViewRenderer remoteRenderer;
    private SurfaceViewRenderer localRenderer;
    private TextView remotePlaceholder;
    private TextView localPlaceholder;
    private TextView statusView;
    private LinearLayout controls;
    private CallControlView micButton;
    private CallControlView cameraButton;
    private CallControlView endButton;
    private PeerConnectionFactory factory;
    private AudioDeviceModule audioDeviceModule;
    private AudioTrack remoteAudioTrack;
    private android.media.AudioDeviceInfo previousCommunicationDevice;
    private int previousVoiceCallVolume = -1;
    private boolean remoteVideoAttached = false;
    private boolean localVideoFrameRendered = false;
    private boolean remoteVideoFrameRendered = false;
    private boolean iceConnected = false;
    private boolean remoteAudioTrackReceived = false;
    private PeerConnection peerConnection;
    private EglBase eglBase;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private VideoSource videoSource;
    private VideoTrack videoTrack;
    private CameraVideoCapturer cameraCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    private final List<IceCandidate> queuedCandidates = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        serverBase = getIntent().getStringExtra("server_base");
        token = getIntent().getStringExtra("token");
        targetName = getIntent().getStringExtra("target_name");
        callId = getIntent().getStringExtra("call_id");
        chatId = getIntent().getLongExtra("chat_id", -1);
        targetId = getIntent().getLongExtra("target_id", -1);
        outgoing = "offer".equals(getIntent().getStringExtra("mode"));
        if (serverBase == null || serverBase.isEmpty() || token == null || token.isEmpty()
                || callId == null || callId.isEmpty() || chatId <= 0 || targetId <= 0) {
            Toast.makeText(this, "Не хватает данных для звонка.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (targetName == null || targetName.isEmpty()) targetName = "Собеседник";
        android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) notificationManager.cancel(27182);
        createUi();
        status("Подключаемся к Kemtiz…");
        if (hasMediaPermissions()) connectSocket();
        else requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, REQ_MEDIA);
    }

    private void createUi() {
        frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        remoteRenderer = new SurfaceViewRenderer(this);
        remoteRenderer.setBackgroundColor(Color.BLACK);
        frame.addView(remoteRenderer, new FrameLayout.LayoutParams(-1, -1));

        remotePlaceholder = new TextView(this);
        remotePlaceholder.setText("Ожидаем видеосвязь…");
        remotePlaceholder.setTextColor(WHITE);
        remotePlaceholder.setTextSize(17);
        remotePlaceholder.setGravity(Gravity.CENTER);
        remotePlaceholder.setBackgroundColor(BG);
        frame.addView(remotePlaceholder, new FrameLayout.LayoutParams(-1, -1));

        localTile = new FrameLayout(this);
        GradientDrawable tileBackground = new GradientDrawable();
        tileBackground.setColor(Color.rgb(24, 24, 34));
        tileBackground.setCornerRadius(dp(18));
        tileBackground.setStroke(dp(1), Color.rgb(139, 116, 205));
        localTile.setBackground(tileBackground);
        localTile.setClipToOutline(true);
        localRenderer = new SurfaceViewRenderer(this);
        localRenderer.setBackgroundColor(Color.rgb(24, 24, 34));
        // SurfaceView video outputs need an explicit ordering so the self-view is above
        // the full-screen remote renderer on devices with separate compositor surfaces.
        localRenderer.setZOrderMediaOverlay(true);
        localTile.addView(localRenderer, new FrameLayout.LayoutParams(-1, -1));

        localPlaceholder = new TextView(this);
        localPlaceholder.setText("Запускаем камеру…");
        localPlaceholder.setTextColor(WHITE);
        localPlaceholder.setTextSize(11);
        localPlaceholder.setGravity(Gravity.CENTER);
        localPlaceholder.setPadding(dp(6), dp(6), dp(6), dp(6));
        localPlaceholder.setBackgroundColor(0xAA181822);
        localTile.addView(localPlaceholder, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams localLp = new FrameLayout.LayoutParams(
                dp(104), dp(148), Gravity.TOP | Gravity.END);
        localLp.setMargins(0, dp(82), dp(14), 0);
        frame.addView(localTile, localLp);
        localTile.setContentDescription("Моё видео. Нажми, чтобы переключить камеру");
        localTile.setOnClickListener(v -> switchCamera());

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(10), dp(140), dp(10));
        header.setBackgroundColor(0x77090A0F);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(38);
        back.setTextColor(WHITE);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Вернуться и завершить звонок");
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(48)));
        back.setOnClickListener(v -> endCall("Звонок завершён."));

        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        titleStack.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titleStack, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView name = new TextView(this);
        name.setText(targetName);
        name.setTextColor(WHITE);
        name.setTextSize(16);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleStack.addView(name, new LinearLayout.LayoutParams(-1, -2));
        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(214, 208, 231));
        statusView.setTextSize(12);
        statusView.setMaxLines(2);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(2);
        titleStack.addView(statusView, statusLp);
        frame.addView(header, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(24), dp(12), dp(24), dp(18));
        controls.setBackgroundColor(0x55090A0F);
        cameraButton = new CallControlView(this, CallControlView.CAMERA, Color.WHITE);
        endButton = new CallControlView(this, CallControlView.HANGUP, Color.rgb(226, 39, 76));
        micButton = new CallControlView(this, CallControlView.MICROPHONE, Color.WHITE);
        cameraButton.setContentDescription("Выключить камеру");
        endButton.setContentDescription("Завершить звонок");
        micButton.setContentDescription("Выключить микрофон");
        LinearLayout.LayoutParams cameraLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        cameraLp.rightMargin = dp(30);
        controls.addView(cameraButton, cameraLp);
        LinearLayout.LayoutParams endLp = new LinearLayout.LayoutParams(dp(64), dp(64));
        endLp.rightMargin = dp(30);
        controls.addView(endButton, endLp);
        controls.addView(micButton, new LinearLayout.LayoutParams(dp(58), dp(58)));
        frame.addView(controls, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        cameraButton.setOnClickListener(v -> toggleCamera());
        micButton.setOnClickListener(v -> toggleMic());
        endButton.setOnClickListener(v -> endCall("Звонок завершён."));

        setContentView(frame);
        if (Build.VERSION.SDK_INT >= 35) {
            frame.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                header.setPadding(dp(8) + bars.left, dp(7) + bars.top,
                        dp(140) + bars.right, dp(7));
                controls.setPadding(dp(24) + bars.left, dp(10),
                        dp(24) + bars.right, dp(14) + bars.bottom);
                FrameLayout.LayoutParams tileLp = (FrameLayout.LayoutParams) localTile.getLayoutParams();
                tileLp.topMargin = dp(82) + bars.top;
                tileLp.rightMargin = dp(14) + bars.right;
                localTile.setLayoutParams(tileLp);
                return insets;
            });
            frame.requestApplyInsets();
        }
    }

    /** Hand-drawn vector-like call controls, consistent across Android skins. */
    private static final class CallControlView extends View {
        static final int CAMERA = 1, HANGUP = 2, MICROPHONE = 3;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int icon;
        private int fill;
        private boolean crossedOut;

        CallControlView(Context context, int icon, int fill) {
            super(context);
            this.icon = icon;
            this.fill = fill;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        void setFill(int color) { fill = color; invalidate(); }
        void setCrossedOut(boolean value) { crossedOut = value; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth()/2f, cy = getHeight()/2f;
            float radius = Math.min(cx, cy) - dp(2);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fill);
            canvas.drawCircle(cx, cy, radius, paint);
            int ink = fill == Color.WHITE ? Color.rgb(30, 28, 40) : Color.WHITE;
            paint.setColor(ink);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2.4f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if (icon == CAMERA) {
                RectF body = new RectF(cx-dp(11), cy-dp(7), cx+dp(5), cy+dp(7));
                canvas.drawRoundRect(body, dp(2), dp(2), paint);
                Path lens = new Path();
                lens.moveTo(cx+dp(5), cy-dp(4));
                lens.lineTo(cx+dp(12), cy-dp(8));
                lens.lineTo(cx+dp(12), cy+dp(8));
                lens.lineTo(cx+dp(5), cy+dp(4));
                lens.close();
                canvas.drawPath(lens, paint);
            } else if (icon == HANGUP) {
                Path handset = new Path();
                handset.moveTo(cx-dp(12), cy-dp(5));
                handset.cubicTo(cx-dp(9), cy+dp(7), cx+dp(4), cy+dp(13), cx+dp(12), cy+dp(5));
                handset.lineTo(cx+dp(7), cy);
                handset.cubicTo(cx+dp(3), cy+dp(3), cx, cy+dp(1), cx-dp(2), cy-dp(3));
                handset.lineTo(cx-dp(5), cy-dp(8));
                handset.close();
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(handset, paint);
            } else {
                RectF capsule = new RectF(cx-dp(4), cy-dp(12), cx+dp(4), cy+dp(3));
                canvas.drawRoundRect(capsule, dp(4), dp(4), paint);
                Path stand = new Path();
                stand.moveTo(cx-dp(9), cy);
                stand.cubicTo(cx-dp(9), cy+dp(12), cx+dp(9), cy+dp(12), cx+dp(9), cy);
                stand.moveTo(cx, cy+dp(9)); stand.lineTo(cx, cy+dp(13));
                stand.moveTo(cx-dp(5), cy+dp(13)); stand.lineTo(cx+dp(5), cy+dp(13));
                canvas.drawPath(stand, paint);
            }
            if (crossedOut) {
                paint.setColor(ink);
                paint.setStrokeWidth(dp(2.5f));
                canvas.drawLine(cx-dp(12), cy+dp(12), cx+dp(12), cy-dp(12), paint);
            }
        }
        private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    }

    private boolean hasMediaPermissions() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MEDIA) return;
        boolean granted = grantResults.length >= 2
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED;
        if (granted) connectSocket();
        else if (outgoing) {
            status("Для видеозвонка нужны камера и микрофон.");
            main.postDelayed(this::finish, 1200);
        } else {
            rejectForPermissions = true;
            status("Без доступа к камере и микрофону звонок нельзя принять.");
            connectSocket();
        }
    }

    private String websocketUrl() {
        if (serverBase.startsWith("https://")) return "wss://" + serverBase.substring(8) + "/ws";
        if (serverBase.startsWith("http://")) return "ws://" + serverBase.substring(7) + "/ws";
        return serverBase + "/ws";
    }

    private void connectSocket() {
        if (socket != null) return;
        socket = http.newWebSocket(new Request.Builder().url(websocketUrl()).build(), new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) {
                ws.send(json("type", "auth", "token", token).toString());
            }
            @Override public void onMessage(WebSocket ws, String raw) {
                try {
                    JSONObject event = new JSONObject(raw);
                    main.post(() -> handleSocketEvent(event));
                } catch (JSONException ignored) { }
            }
            @Override public void onFailure(WebSocket ws, Throwable error, Response response) {
                status("Нет связи с сервером Kemtiz.");
                if (outgoing && !callAccepted) main.postDelayed(() -> {
                    if (!isFinishing() && !callAccepted) finishWithMessage("Не удалось подключиться к серверу.");
                }, 600);
            }
        });
    }

    private void handleSocketEvent(JSONObject event) {
        String type = event.optString("type", "");
        String eventCallId = event.optString("call_id", "");
        if (!eventCallId.isEmpty() && !eventCallId.equals(callId)) return;
        long eventChatId = event.optLong("chat_id", -1);
        if (eventChatId > 0 && eventChatId != chatId) return;

        if ("ready".equals(type) && !socketReady) {
            socketReady = true;
            if (rejectForPermissions) {
                sendControl("call.reject");
                main.postDelayed(this::finish, 500);
            } else if (outgoing) {
                sendControl("call.start");
                status("Звоним: " + targetName + "…");
                main.postDelayed(() -> {
                    if (!isFinishing() && !callAccepted) finishWithMessage("Нет ответа на видеозвонок.");
                }, 45000);
            } else {
                if (preparePeerConnection()) {
                    sendControl("call.accept");
                    status("Соединяем видеосвязь…");
                } else {
                    sendControl("call.reject");
                    finishWithMessage("Не удалось запустить камеру или микрофон.");
                }
            }
            return;
        }
        if ("call.accepted".equals(type) && outgoing && !callAccepted) {
            callAccepted = true;
            status("Собеседник ответил. Устанавливаем соединение…");
            if (preparePeerConnection()) createOffer();
            else {
                sendControl("call.end");
                finishWithMessage("Не удалось запустить камеру или микрофон.");
            }
        } else if ("call.signal".equals(type)) {
            JSONObject signal = event.optJSONObject("signal");
            if (signal != null) handleSignal(signal);
        } else if ("call.rejected".equals(type)) {
            finishWithMessage("Собеседник отклонил звонок.");
        } else if ("call.ended".equals(type)) {
            finishWithMessage("Собеседник завершил звонок.");
        } else if ("call.error".equals(type)) {
            finishWithMessage(event.optString("message", "Ошибка звонка."));
        }
    }

    private boolean preparePeerConnection() {
        if (mediaPrepared) return true;
        try {
            synchronized (CallActivity.class) {
                if (!factoryInitialized) {
                    PeerConnectionFactory.initialize(
                            PeerConnectionFactory.InitializationOptions.builder(getApplicationContext())
                                    .createInitializationOptions());
                    factoryInitialized = true;
                }
            }
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                previousAudioMode = audioManager.getMode();
                previousSpeakerphone = audioManager.isSpeakerphoneOn();
                previousVoiceCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
                int maxVoiceVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
                int safeVoiceVolume = Math.max(1, Math.round(maxVoiceVolume * 0.60f));
                if (previousVoiceCallVolume > safeVoiceVolume) {
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, safeVoiceVolume, 0);
                }
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    previousCommunicationDevice = audioManager.getCommunicationDevice();
                    android.media.AudioDeviceInfo selectedDevice = null;
                    for (android.media.AudioDeviceInfo device : audioManager.getAvailableCommunicationDevices()) {
                        int type = device.getType();
                        if (type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                                || type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
                                || type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
                                || type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                                || type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET) {
                            selectedDevice = device;
                            break;
                        }
                    }
                    if (selectedDevice == null) {
                        for (android.media.AudioDeviceInfo device : audioManager.getAvailableCommunicationDevices()) {
                            if (device.getType() == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                                selectedDevice = device;
                                break;
                            }
                        }
                    }
                    if (selectedDevice != null) audioManager.setCommunicationDevice(selectedDevice);
                } else {
                    audioManager.setSpeakerphoneOn(true);
                }
            }

            eglBase = EglBase.create();
            remoteRenderer.init(eglBase.getEglBaseContext(), new RendererCommon.RendererEvents() {
                @Override public void onFirstFrameRendered() {
                    main.post(() -> {
                        remoteVideoFrameRendered = true;
                        if (remotePlaceholder != null) remotePlaceholder.setVisibility(View.GONE);
                        refreshMediaStatus();
                    });
                }
                @Override public void onFrameResolutionChanged(int width, int height, int rotation) { }
            });
            remoteRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
            remoteRenderer.setEnableHardwareScaler(true);
            localRenderer.init(eglBase.getEglBaseContext(), new RendererCommon.RendererEvents() {
                @Override public void onFirstFrameRendered() {
                    main.post(() -> {
                        localVideoFrameRendered = true;
                        if (localPlaceholder != null) localPlaceholder.setVisibility(View.GONE);
                        refreshMediaStatus();
                    });
                }
                @Override public void onFrameResolutionChanged(int width, int height, int rotation) { }
            });
            localRenderer.setMirror(true);
            localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
            localRenderer.setEnableHardwareScaler(true);
            // Keep a single echo/noise reduction chain. On some phones stacking the
            // hardware effect with WebRTC's software AEC makes voices metallic or squeal.
            audioDeviceModule = JavaAudioDeviceModule.builder(getApplicationContext())
                    .setUseHardwareAcousticEchoCanceler(false)
                    .setUseHardwareNoiseSuppressor(false)
                    .createAudioDeviceModule();
            factory = PeerConnectionFactory.builder()
                    .setAudioDeviceModule(audioDeviceModule)
                    .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                    .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                    .createPeerConnectionFactory();
            MediaConstraints audioConstraints = new MediaConstraints();
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
            audioSource = factory.createAudioSource(audioConstraints);
            audioTrack = factory.createAudioTrack("kemtiz-audio", audioSource);
            CameraEnumerator enumerator = Camera2Enumerator.isSupported(this)
                    ? new Camera2Enumerator(this) : new Camera1Enumerator(true);
            cameraCapturer = findCamera(enumerator, true);
            if (cameraCapturer == null) cameraCapturer = findCamera(enumerator, false);
            if (cameraCapturer == null) throw new IllegalStateException("Камера не найдена");
            videoSource = factory.createVideoSource(false);
            surfaceTextureHelper = SurfaceTextureHelper.create("KemtizCapture", eglBase.getEglBaseContext());
            cameraCapturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
            cameraCapturer.startCapture(640, 480, 24);
            main.postDelayed(() -> {
                if (!isFinishing() && mediaPrepared && !localVideoFrameRendered) {
                    status("Камера запущена, но кадры не отображаются. Проверь разрешение камеры и закрой другие приложения с камерой.");
                }
            }, 7000);
            videoTrack = factory.createVideoTrack("kemtiz-video", videoSource);
            videoTrack.addSink(localRenderer);
            List<PeerConnection.IceServer> iceServers = new ArrayList<>();
            iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
            iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
            PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(iceServers);
            config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            peerConnection = factory.createPeerConnection(config, new PeerConnection.Observer() {
                @Override public void onSignalingChange(PeerConnection.SignalingState state) { }
                @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                    if (state == PeerConnection.IceConnectionState.CONNECTED
                            || state == PeerConnection.IceConnectionState.COMPLETED) {
                        iceConnected = true;
                        refreshMediaStatus();
                        main.postDelayed(() -> {
                            if (isFinishing() || !iceConnected) return;
                            if (!remoteVideoAttached) {
                                status("Сеть соединена, но видеодорожка собеседника не пришла. Перезапусти звонок на обоих телефонах.");
                            } else if (!remoteVideoFrameRendered) {
                                status("Видеодорожка получена, но кадры не отображаются. Проверяем видеорендер.");
                            }
                        }, 10000);
                    } else if (state == PeerConnection.IceConnectionState.FAILED) {
                        iceConnected = false;
                        status("Телефоны не смогли соединиться напрямую. Нужен TURN-сервер или другая сеть.");
                    } else if (state == PeerConnection.IceConnectionState.DISCONNECTED) {
                        iceConnected = false;
                        status("Связь прервана, пытаемся восстановить…");
                    } else {
                        iceConnected = false;
                        status("Согласуем связь между телефонами…");
                    }
                }
                @Override public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState state) { }
                @Override public void onConnectionChange(PeerConnection.PeerConnectionState state) {
                    if (state == PeerConnection.PeerConnectionState.FAILED) {
                        iceConnected = false;
                        status("Не удалось связать телефоны. Для этой сети может понадобиться TURN.");
                    } else if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                        iceConnected = true;
                        refreshMediaStatus();
                    } else if (state == PeerConnection.PeerConnectionState.DISCONNECTED) {
                        iceConnected = false;
                        refreshMediaStatus();
                    }
                }
                @Override public void onIceConnectionReceivingChange(boolean receiving) { }
                @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) { }
                @Override public void onIceCandidate(IceCandidate candidate) {
                    sendSignal(json("type", "ice", "candidate", candidate.sdp,
                            "sdp_mid", candidate.sdpMid, "sdp_mline_index", candidate.sdpMLineIndex));
                }
                @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) { }
                @Override public void onAddStream(MediaStream stream) {
                    if (stream != null && !stream.videoTracks.isEmpty()) addRemoteVideo(stream.videoTracks.get(0));
                }
                @Override public void onRemoveStream(MediaStream stream) { }
                @Override public void onDataChannel(org.webrtc.DataChannel channel) { }
                @Override public void onRenegotiationNeeded() { }
                @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
                    handleRemoteTrack(receiver == null ? null : receiver.track());
                }
                @Override public void onTrack(org.webrtc.RtpTransceiver transceiver) {
                    if (transceiver != null && transceiver.getReceiver() != null) {
                        handleRemoteTrack(transceiver.getReceiver().track());
                    }
                }
            });
            if (peerConnection == null) throw new IllegalStateException("Не удалось создать соединение");
            peerConnection.addTrack(audioTrack, Collections.singletonList("kemtiz-stream"));
            peerConnection.addTrack(videoTrack, Collections.singletonList("kemtiz-stream"));
            mediaPrepared = true;
            micButton.setCrossedOut(false);
            cameraButton.setCrossedOut(false);
            return true;
        } catch (Exception e) {
            status("Ошибка звонка: " + (e.getMessage() == null ? "проверь разрешения" : e.getMessage()));
            releaseMedia();
            return false;
        }
    }

    private CameraVideoCapturer findCamera(CameraEnumerator enumerator, boolean front) {
        for (String name : enumerator.getDeviceNames()) {
            if (front != enumerator.isFrontFacing(name)) continue;
            CameraVideoCapturer capturer = enumerator.createCapturer(name, null);
            if (capturer != null) return capturer;
        }
        return null;
    }

    private void handleRemoteTrack(MediaStreamTrack track) {
        if (track instanceof VideoTrack) {
            addRemoteVideo((VideoTrack) track);
        } else if (track instanceof AudioTrack) {
            main.post(() -> {
                if (isFinishing()) return;
                remoteAudioTrack = (AudioTrack) track;
                remoteAudioTrack.setEnabled(true);
                remoteAudioTrack.setVolume(0.75);
                remoteAudioTrackReceived = true;
                refreshMediaStatus();
            });
        }
    }

    private void addRemoteVideo(VideoTrack track) {
        main.post(() -> {
            if (remoteRenderer != null && !isFinishing()) {
                if (!remoteVideoAttached) {
                    track.addSink(remoteRenderer);
                    remoteVideoAttached = true;
                    status("Видеодорожка собеседника получена, ждём первый кадр…");
                }
                // Keep the waiting layer until SurfaceViewRenderer actually renders a frame.
                refreshMediaStatus();
            }
        });
    }

    private void refreshMediaStatus() {
        if (isFinishing() || statusView == null) return;
        if (!mediaPrepared) {
            status("Подготавливаем камеру и микрофон…");
        } else if (!localVideoFrameRendered) {
            status("Ждём изображение с твоей камеры…");
        } else if (!iceConnected) {
            status("Камера работает · соединяем телефоны…");
        } else if (!remoteVideoAttached) {
            status("Сеть соединена · ждём видеодорожку собеседника…");
        } else if (!remoteVideoFrameRendered) {
            status("Видеодорожка получена · ждём первый кадр…");
        } else if (!remoteAudioTrackReceived) {
            status("Видео подключено · ждём звук собеседника…");
        } else {
            status("Видео и звук подключены");
        }
    }

    private void createOffer() {
        if (peerConnection == null) return;
        status("Создаём видеосоединение…");
        peerConnection.createOffer(new SdpAdapter() {
            @Override public void onCreateSuccess(SessionDescription description) {
                peerConnection.setLocalDescription(new SdpAdapter() {
                    @Override public void onSetSuccess() {
                        sendSignal(json("type", "offer", "sdp", description.description));
                    }
                    @Override public void onSetFailure(String error) { finishWithMessage("Не удалось подготовить соединение: " + error); }
                }, description);
            }
            @Override public void onCreateFailure(String error) { finishWithMessage("Не удалось создать звонок: " + error); }
        }, new MediaConstraints());
    }

    private void createAnswer() {
        if (peerConnection == null) return;
        peerConnection.createAnswer(new SdpAdapter() {
            @Override public void onCreateSuccess(SessionDescription description) {
                peerConnection.setLocalDescription(new SdpAdapter() {
                    @Override public void onSetSuccess() {
                        sendSignal(json("type", "answer", "sdp", description.description));
                    }
                    @Override public void onSetFailure(String error) { finishWithMessage("Не удалось подготовить ответ: " + error); }
                }, description);
            }
            @Override public void onCreateFailure(String error) { finishWithMessage("Не удалось ответить на звонок: " + error); }
        }, new MediaConstraints());
    }

    private void handleSignal(JSONObject signal) {
        if (peerConnection == null) return;
        String type = signal.optString("type", "");
        if ("ice".equals(type)) {
            IceCandidate candidate = new IceCandidate(signal.optString("sdp_mid", "0"),
                    signal.optInt("sdp_mline_index", 0), signal.optString("candidate", ""));
            if (remoteDescriptionSet) peerConnection.addIceCandidate(candidate);
            else queuedCandidates.add(candidate);
            return;
        }
        if (!"offer".equals(type) && !"answer".equals(type)) return;
        String sdp = signal.optString("sdp", "");
        if (sdp.isEmpty()) return;
        SessionDescription.Type descType = "offer".equals(type)
                ? SessionDescription.Type.OFFER : SessionDescription.Type.ANSWER;
        peerConnection.setRemoteDescription(new SdpAdapter() {
            @Override public void onSetSuccess() {
                remoteDescriptionSet = true;
                for (IceCandidate candidate : new ArrayList<>(queuedCandidates)) peerConnection.addIceCandidate(candidate);
                queuedCandidates.clear();
                if ("offer".equals(type)) createAnswer();
                else status("Видеосвязь устанавливается…");
            }
            @Override public void onSetFailure(String error) { finishWithMessage("Не удалось принять описание звонка: " + error); }
        }, new SessionDescription(descType, sdp));
    }

    private void sendSignal(JSONObject signal) {
        if (socket != null && socketReady) {
            boolean sent = socket.send(json("type", "call.signal", "chat_id", chatId,
                    "target_user_id", targetId, "call_id", callId, "signal", signal).toString());
            if (!sent) status("Не удалось отправить сигнал видеосвязи. Проверяем соединение с сервером…");
        } else {
            status("Сигнальный канал ещё не готов. Ожидаем сервер…");
        }
    }

    private void sendControl(String type) {
        if (socket != null && socketReady) {
            socket.send(json("type", type, "chat_id", chatId, "target_user_id", targetId,
                    "call_id", callId, "mode", "video").toString());
        }
    }

    private void toggleMic() {
        micEnabled = !micEnabled;
        if (audioTrack != null) audioTrack.setEnabled(micEnabled);
        micButton.setCrossedOut(!micEnabled);
        micButton.setContentDescription(micEnabled ? "Выключить микрофон" : "Включить микрофон");
        micButton.setFill(micEnabled ? Color.WHITE : Color.rgb(76, 64, 92));
    }

    private void toggleCamera() {
        cameraEnabled = !cameraEnabled;
        if (videoTrack != null) videoTrack.setEnabled(cameraEnabled);
        if (localTile != null) localTile.setVisibility(cameraEnabled ? View.VISIBLE : View.INVISIBLE);
        cameraButton.setCrossedOut(!cameraEnabled);
        cameraButton.setContentDescription(cameraEnabled ? "Выключить камеру" : "Включить камеру");
        cameraButton.setFill(cameraEnabled ? Color.WHITE : Color.rgb(76, 64, 92));
    }

    private void switchCamera() {
        if (cameraCapturer == null) return;
        cameraCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
            @Override public void onCameraSwitchDone(boolean front) {
                main.post(() -> status(front ? "Фронтальная камера" : "Основная камера"));
            }
            @Override public void onCameraSwitchError(String error) { status("Не удалось переключить камеру: " + error); }
        });
    }

    private void status(String text) {
        main.post(() -> {
            if (statusView != null && !isFinishing()) statusView.setText(text);
        });
    }

    private void finishWithMessage(String message) {
        status(message);
        main.postDelayed(() -> { if (!isFinishing()) finish(); }, 1100);
    }

    private void endCall(String message) {
        if (ended) return;
        ended = true;
        if (socketReady) sendControl("call.end");
        finishWithMessage(message);
    }

    private void releaseMedia() {
        try { if (peerConnection != null) { peerConnection.close(); peerConnection.dispose(); } } catch (Exception ignored) { }
        peerConnection = null;
        try { if (cameraCapturer != null) { cameraCapturer.stopCapture(); cameraCapturer.dispose(); } } catch (Exception ignored) { }
        cameraCapturer = null;
        try { if (videoTrack != null) videoTrack.dispose(); } catch (Exception ignored) { }
        videoTrack = null;
        try { if (audioTrack != null) audioTrack.dispose(); } catch (Exception ignored) { }
        audioTrack = null;
        try { if (videoSource != null) videoSource.dispose(); } catch (Exception ignored) { }
        videoSource = null;
        try { if (audioSource != null) audioSource.dispose(); } catch (Exception ignored) { }
        audioSource = null;
        try { if (surfaceTextureHelper != null) surfaceTextureHelper.dispose(); } catch (Exception ignored) { }
        surfaceTextureHelper = null;
        try { if (factory != null) factory.dispose(); } catch (Exception ignored) { }
        factory = null;
        try { if (audioDeviceModule != null) audioDeviceModule.release(); } catch (Exception ignored) { }
        audioDeviceModule = null;
        remoteAudioTrack = null;
        remoteVideoAttached = false;
        localVideoFrameRendered = false;
        remoteVideoFrameRendered = false;
        remoteAudioTrackReceived = false;
        iceConnected = false;
        mediaPrepared = false;
    }

    @Override public void onBackPressed() { endCall("Звонок завершён."); }

    @Override protected void onDestroy() {
        if (socket != null) { socket.close(1000, "call finished"); socket = null; }
        releaseMedia();
        try { if (remoteRenderer != null) remoteRenderer.release(); } catch (Exception ignored) { }
        try { if (localRenderer != null) localRenderer.release(); } catch (Exception ignored) { }
        try { if (eglBase != null) eglBase.release(); } catch (Exception ignored) { }
        if (audioManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (previousCommunicationDevice != null) {
                        audioManager.setCommunicationDevice(previousCommunicationDevice);
                    } else {
                        audioManager.clearCommunicationDevice();
                    }
                } else {
                    audioManager.setSpeakerphoneOn(previousSpeakerphone);
                }
                if (previousVoiceCallVolume >= 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, previousVoiceCallVolume, 0);
                }
                audioManager.setMode(previousAudioMode);
            } catch (Exception ignored) { }
        }
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private JSONObject json(Object... values) {
        JSONObject object = new JSONObject();
        try {
            for (int i = 0; i + 1 < values.length; i += 2) object.put(String.valueOf(values[i]), values[i + 1]);
        } catch (JSONException ignored) { }
        return object;
    }

    private abstract static class SdpAdapter implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription description) { }
        @Override public void onSetSuccess() { }
        @Override public void onCreateFailure(String error) { }
        @Override public void onSetFailure(String error) { }
    }
}
