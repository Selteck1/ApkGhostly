package com.kemtiz.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
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

import java.io.IOException;
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
    private SurfaceViewRenderer remoteRenderer;
    private SurfaceViewRenderer localRenderer;
    private TextView statusView;
    private Button micButton;
    private Button cameraButton;
    private PeerConnectionFactory factory;
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
        createUi();
        status("Подключаемся к Kemtiz…");
        if (hasMediaPermissions()) connectSocket();
        else requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, REQ_MEDIA);
    }

    private void createUi() {
        frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        remoteRenderer = new SurfaceViewRenderer(this);
        frame.addView(remoteRenderer, new FrameLayout.LayoutParams(-1, -1));
        localRenderer = new SurfaceViewRenderer(this);
        FrameLayout.LayoutParams localLp = new FrameLayout.LayoutParams(dp(112), dp(158), Gravity.TOP | Gravity.END);
        localLp.setMargins(0, dp(16), dp(14), 0);
        frame.addView(localRenderer, localLp);
        statusView = new TextView(this);
        statusView.setTextColor(WHITE);
        statusView.setTextSize(14);
        statusView.setGravity(Gravity.CENTER_VERTICAL);
        statusView.setPadding(dp(14), dp(10), dp(14), dp(10));
        statusView.setBackgroundColor(0x99090A0F);
        frame.addView(statusView, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(6), dp(10), dp(6), dp(14));
        controls.setBackgroundColor(0x99090A0F);
        micButton = controlButton("🎙 Микрофон");
        cameraButton = controlButton("📷 Камера");
        Button switchButton = controlButton("↻ Перевернуть");
        Button endButton = controlButton("Завершить");
        endButton.setBackgroundColor(Color.rgb(182, 45, 68));
        controls.addView(micButton, new LinearLayout.LayoutParams(0, dp(50), 1f));
        controls.addView(cameraButton, new LinearLayout.LayoutParams(0, dp(50), 1f));
        controls.addView(switchButton, new LinearLayout.LayoutParams(0, dp(50), 1f));
        controls.addView(endButton, new LinearLayout.LayoutParams(0, dp(50), 1f));
        frame.addView(controls, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        micButton.setOnClickListener(v -> toggleMic());
        cameraButton.setOnClickListener(v -> toggleCamera());
        switchButton.setOnClickListener(v -> switchCamera());
        endButton.setOnClickListener(v -> endCall("Звонок завершён."));
        setContentView(frame);
    }

    private Button controlButton(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(WHITE);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setPadding(dp(3), dp(4), dp(3), dp(4));
        button.setBackgroundColor(Color.rgb(46, 43, 58));
        return button;
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
            @Override public void onFailure(WebSocket ws, IOException error, Response response) {
                status("Нет связи с сервером Kemtiz.");
                if (outgoing && !callAccepted) main.postDelayed(() -> {
                    if (!isFinishing() && !callAccepted) finishWithMessage("Не удалось подключиться к серверу.");
                }, 600);
            }
        });
    }

    private void handleSocketEvent(JSONObject event) {
        String type = event.optString("type", "");
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
            eglBase = EglBase.create();
            remoteRenderer.init(eglBase.getEglBaseContext(), null);
            remoteRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
            remoteRenderer.setEnableHardwareScaler(true);
            localRenderer.init(eglBase.getEglBaseContext(), null);
            localRenderer.setMirror(true);
            localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
            localRenderer.setEnableHardwareScaler(true);
            factory = PeerConnectionFactory.builder()
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
            videoTrack = factory.createVideoTrack("kemtiz-video", videoSource);
            videoTrack.addSink(localRenderer);
            PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(
                    Collections.singletonList(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()));
            config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            peerConnection = factory.createPeerConnection(config, new PeerConnection.Observer() {
                @Override public void onSignalingChange(PeerConnection.SignalingState state) { }
                @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                    status(state == PeerConnection.IceConnectionState.CONNECTED
                            || state == PeerConnection.IceConnectionState.COMPLETED
                            ? "Видеосвязь установлена" : "Подключаем видео и звук…");
                }
                @Override public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState state) { }
                @Override public void onConnectionChange(PeerConnection.PeerConnectionState state) { }
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
                    MediaStreamTrack track = receiver.track();
                    if (track instanceof VideoTrack) addRemoteVideo((VideoTrack) track);
                }
            });
            if (peerConnection == null) throw new IllegalStateException("Не удалось создать соединение");
            peerConnection.addTrack(audioTrack, Collections.singletonList("kemtiz-stream"));
            peerConnection.addTrack(videoTrack, Collections.singletonList("kemtiz-stream"));
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                previousAudioMode = audioManager.getMode();
                previousSpeakerphone = audioManager.isSpeakerphoneOn();
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audioManager.setSpeakerphoneOn(true);
            }
            mediaPrepared = true;
            micButton.setText("🎙 Микрофон: вкл.");
            cameraButton.setText("📷 Камера: вкл.");
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

    private void addRemoteVideo(VideoTrack track) {
        main.post(() -> {
            if (remoteRenderer != null && !isFinishing()) {
                track.addSink(remoteRenderer);
                status("Собеседник подключился");
            }
        });
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
            socket.send(json("type", "call.signal", "chat_id", chatId,
                    "target_user_id", targetId, "call_id", callId, "signal", signal).toString());
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
        if (micButton != null) micButton.setText(micEnabled ? "🎙 Микрофон: вкл." : "🔇 Микрофон: выкл.");
    }

    private void toggleCamera() {
        cameraEnabled = !cameraEnabled;
        if (videoTrack != null) videoTrack.setEnabled(cameraEnabled);
        if (cameraButton != null) cameraButton.setText(cameraEnabled ? "📷 Камера: вкл." : "📷 Камера: выкл.");
        if (localRenderer != null) localRenderer.setVisibility(cameraEnabled ? View.VISIBLE : View.INVISIBLE);
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
                audioManager.setSpeakerphoneOn(previousSpeakerphone);
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
