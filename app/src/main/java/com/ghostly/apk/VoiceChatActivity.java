package com.ghostly.apk;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import java.util.Locale;
import java.util.Random;

public class VoiceChatActivity extends Activity {
    private static final String PREFS = "ghostly_voice";
    private static final String STATUS_ACTION = "com.ghostly.apk.VOICE_STATUS";
    private static final int REQ_MIC = 9001;
    private static final int REQ_OVERLAY = 9002;

    private final int BG = Color.rgb(8, 7, 12);
    private final int CARD = Color.rgb(20, 18, 28);
    private final int PURPLE = Color.rgb(188, 124, 255);
    private final int PURPLE_DARK = Color.rgb(104, 58, 156);
    private final int WHITE = Color.rgb(245, 242, 250);
    private final int MUTED = Color.rgb(164, 158, 174);

    private EditText serverInput;
    private EditText roomInput;
    private TextView status;
    private TextView roomCode;
    private Button joinButton;
    private BroadcastReceiver statusReceiver;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        registerStatusReceiver();
        updateFromPrefs();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(28));

        TextView brand = label("GHOSTLY", 30, PURPLE, Gravity.LEFT);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = label("VOICE", 17, WHITE, Gravity.LEFT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(brand, lp(0,0,0,0));
        root.addView(title, lp(0,0,0,6));
        root.addView(label("Голосовой чат для игры. Открой Standoff 2 — голос продолжит работать в фоне.", 13, MUTED, Gravity.LEFT), lp(0,0,0,18));

        LinearLayout serverCard = card();
        serverCard.addView(label("СЕРВЕР", 11, MUTED, Gravity.LEFT), lp(0,0,0,6));
        serverInput = field("wss://.../ws", getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString("server_url", "wss://YOUR-VOICE-RELAY.onrender.com/ws"));
        serverCard.addView(serverInput, lp(0,0,0,8));
        root.addView(serverCard, lp(0,0,0,12));

        LinearLayout roomCard = card();
        roomCard.addView(label("КОМНАТА", 11, MUTED, Gravity.LEFT), lp(0,0,0,6));
        roomInput = field("Код комнаты", "");
        roomInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        roomCard.addView(roomInput, lp(0,0,0,8));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button create = primaryButton("✨ Создать");
        joinButton = secondaryButton("Войти");
        actions.addView(create, weightLp(1f,0,0,6));
        actions.addView(joinButton, weightLp(1f,6,0,0));
        roomCard.addView(actions);
        root.addView(roomCard, lp(0,0,0,12));

        LinearLayout currentCard = card();
        currentCard.addView(label("ТВОЙ КОД", 11, MUTED, Gravity.LEFT), lp(0,0,0,4));
        roomCode = label("—", 28, WHITE, Gravity.CENTER);
        roomCode.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        currentCard.addView(roomCode, lp(0,4,0,8));

        LinearLayout shareRow = new LinearLayout(this);
        shareRow.setOrientation(LinearLayout.HORIZONTAL);
        Button share = secondaryButton("📤 Поделиться кодом");
        Button overlay = secondaryButton("🪟 Поверх игры");
        shareRow.addView(share, weightLp(1f,0,0,6));
        shareRow.addView(overlay, weightLp(1f,6,0,0));
        currentCard.addView(shareRow);

        status = label("Готово. Создай комнату или введи код друга.", 13, MUTED, Gravity.LEFT);
        currentCard.addView(status, lp(0,10,0,0));
        root.addView(currentCard, lp(0,0,0,12));

        Button openGame = primaryButton("🎮 Открыть Standoff 2");
        root.addView(openGame, lp(0,0,0,8));

        Button stop = secondaryButton("⛔ Завершить голосовой чат");
        root.addView(stop, lp(0,0,0,8));

        TextView info = label(
            "🎙 Микрофон работает через foreground service. При сворачивании Ghostly голос не отключается. " +
            "Мини-окно поверх игры — необязательное разрешение Android.",
            12, MUTED, Gravity.CENTER
        );
        root.addView(info, lp(0,14,0,0));

        create.setOnClickListener(v -> {
            if (!ensureMicrophone()) return;
            String code = generateRoomCode();
            roomInput.setText(code);
            roomCode.setText(code);
            startVoice(code);
        });

        joinButton.setOnClickListener(v -> {
            if (!ensureMicrophone()) return;
            String code = roomInput.getText().toString().trim().toUpperCase(Locale.US);
            if (code.length() < 6) {
                status.setText("⚠️ Введи код комнаты.");
                return;
            }
            roomCode.setText(code);
            startVoice(code);
        });

        share.setOnClickListener(v -> shareRoomCode());
        overlay.setOnClickListener(v -> enableOverlay());
        openGame.setOnClickListener(v -> launchGame());
        stop.setOnClickListener(v -> stopVoice());

        scroll.addView(root);
        setContentView(scroll);
    }

    private void startVoice(String code) {
        String server = serverInput.getText().toString().trim();
        if (server.isEmpty()) {
            status.setText("❌ Укажи адрес голосового сервера.");
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("server_url", server)
            .putString("room", code)
            .apply();

        Intent i = new Intent(this, VoiceChatService.class)
            .setAction(VoiceChatService.ACTION_START)
            .putExtra(VoiceChatService.EXTRA_SERVER, server)
            .putExtra(VoiceChatService.EXTRA_ROOM, code);

        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);

        status.setText("⏳ Подключение к комнате " + code + "…");
    }

    private void stopVoice() {
        startService(new Intent(this, VoiceChatService.class).setAction(VoiceChatService.ACTION_STOP));
        status.setText("✅ Голосовой чат завершён.");
    }

    private boolean ensureMicrophone() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return false;
        }
        return true;
    }

    private void enableOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, REQ_OVERLAY);
                status.setText("🪟 Разреши «поверх других приложений», затем нажми ещё раз.");
            } catch (Exception e) {
                status.setText("❌ Не удалось открыть настройки окна.");
            }
            return;
        }

        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("overlay", true).apply();
        Intent i = new Intent(this, VoiceChatService.class)
            .setAction(VoiceChatService.ACTION_SHOW_OVERLAY);
        startService(i);
        status.setText("🪟 Мини-окно включено.");
    }

    private void shareRoomCode() {
        String code = roomInput.getText().toString().trim();
        if (code.isEmpty()) {
            status.setText("Сначала создай комнату.");
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, "Заходи в Ghostly Voice. Код комнаты: " + code);
        startActivity(Intent.createChooser(send, "Поделиться кодом"));
    }

    private String generateRoomCode() {
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random r = new Random();
        StringBuilder b = new StringBuilder(8);
        for (int i=0;i<8;i++) b.append(chars.charAt(r.nextInt(chars.length())));
        return b.toString();
    }

    private void launchGame() {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage("com.axlebolt.standoff2");
            if (i != null) {
                startActivity(i);
            } else {
                status.setText("❌ Standoff 2 не найден.");
            }
        } catch (Exception e) {
            status.setText("❌ Не удалось открыть Standoff 2.");
        }
    }

    private void registerStatusReceiver() {
        statusReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!STATUS_ACTION.equals(intent.getAction())) return;
                String s = intent.getStringExtra("status");
                if (s != null) status.setText(s);
                String room = intent.getStringExtra("room");
                if (room != null && !room.isEmpty()) roomCode.setText(room);
            }
        };
        IntentFilter f = new IntentFilter(STATUS_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, f, RECEIVER_NOT_EXPORTED);
        else registerReceiver(statusReceiver, f);
    }

    private void updateFromPrefs() {
        android.content.SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String room = p.getString("room", "");
        if (!room.isEmpty()) {
            roomInput.setText(room);
            roomCode.setText(room);
        }
        status.setText(p.getString("status", "Готово. Создай комнату или введи код друга."));
    }

    @Override protected void onDestroy() {
        if (statusReceiver != null) {
            try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    private LinearLayout card() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(14),dp(14),dp(14),dp(14));
        b.setBackground(round(CARD,18));
        return b;
    }

    private TextView label(String text, float size, int color, int gravity) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(size); t.setTextColor(color); t.setGravity(gravity);
        t.setLineSpacing(0f,1.08f);
        return t;
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setText(value);
        e.setTextColor(WHITE); e.setHintTextColor(Color.rgb(116,111,126));
        e.setTextSize(14); e.setSingleLine(true);
        e.setPadding(dp(13),dp(4),dp(13),dp(4));
        e.setBackground(round(Color.rgb(12,11,17),12));
        return e;
    }

    private Button baseButton(String text) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextSize(13);
        b.setMinHeight(dp(52)); b.setPadding(dp(10),0,dp(10),0);
        return b;
    }

    private Button primaryButton(String text) {
        Button b = baseButton(text); b.setTextColor(Color.WHITE);
        b.setBackground(round(PURPLE_DARK,14)); return b;
    }

    private Button secondaryButton(String text) {
        Button b = baseButton(text); b.setTextColor(WHITE);
        b.setBackground(round(Color.rgb(24,21,34),14)); return b;
    }

    private LinearLayout.LayoutParams lp(int l,int t,int r,int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p;
    }

    private LinearLayout.LayoutParams weightLp(float weight,int l,int t,int r) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        p.setMargins(dp(l),dp(t),dp(r),0); return p;
    }

    private android.graphics.drawable.GradientDrawable round(int color,int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
