package com.ghostly.apk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class MainActivity extends Activity {
    private static final String PREFS = "ghostly_booster";
    private static final String MODE = "mode";
    private static final String MODE_RELAY = "relay";
    private static final String MODE_PC = "pc";
    private static final String DEFAULT_PORT = "51888";
    private static final String STANDOFF_PACKAGE = "com.axlebolt.standoff2";

    private final int BG = Color.rgb(8, 7, 12);
    private final int CARD = Color.rgb(18, 16, 26);
    private final int CARD_2 = Color.rgb(24, 21, 34);
    private final int PURPLE = Color.rgb(188, 124, 255);
    private final int PURPLE_DARK = Color.rgb(104, 58, 156);
    private final int WHITE = Color.rgb(245, 242, 250);
    private final int MUTED = Color.rgb(164, 158, 174);

    private LinearLayout root, serverPanel;
    private LinearLayout relayCard, pcCard, fpsCard;
    private TextView status, metrics;
    private EditText hostInput, portInput, tokenInput;
    private boolean fpsEnabled = false;
    private int targetFps = 60;
    private LinearLayout fpsSelector;
    private Button fps60Button, fps90Button;
    private ShizukuPerformanceManager shizukuPerformance;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        shizukuPerformance = new ShizukuPerformanceManager(this);
        buildUi();
        refreshUi();
    }

    private void buildUi() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        targetFps = p.getInt("target_fps", 60);
        if (targetFps != 60 && targetFps != 90) targetFps = 60;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));

        TextView brand = label("GHOSTLY", 28, PURPLE, Gravity.LEFT);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = label("BOOSTER", 15, WHITE, Gravity.LEFT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView subtitle = label(
            "Три режима для Standoff 2\\nСеть • ПК • FPS", 14, MUTED, Gravity.LEFT);

        root.addView(brand, lp(0,0,0,2));
        root.addView(title, lp(0,0,0,2));
        root.addView(subtitle, lp(0,0,0,16));

        Button voiceChat = primaryButton("🎙️  GHOSTLY VOICE — Голосовой чат");
        voiceChat.setOnClickListener(v -> startActivity(new Intent(this, VoiceChatActivity.class)));
        root.addView(voiceChat, lp(0,0,0,10));

        Button roadGame = secondaryButton("🚗  Ghostly Road — игра");
        roadGame.setOnClickListener(v -> startActivity(new Intent(this, GameActivity.class)));
        root.addView(roadGame, lp(0,0,0,16));

        root.addView(sectionTitle("РЕЖИМЫ GHOSTLY"), lp(0,6,0,8));
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.VERTICAL);

        relayCard = modeCard("⚡  GHOSTLY BOOST",
            "Изменяет маршрут Standoff 2 через relay\\nТолько игровой трафик", MODE_RELAY);
        pcCard = modeCard("🖥  PC BOOST",
            "Телефон → твой ПК → Standoff 2\\nБез VPS", MODE_PC);
        fpsCard = modeCard("🔥  60 FPS STABLE",
            "Performance + целевой режим 60 FPS\\nCPU • Game Mode • экран", "fps");

        modes.addView(relayCard, lp(0,0,0,8));
        modes.addView(pcCard, lp(0,0,0,8));
        modes.addView(fpsCard, lp(0,0,0,14));
        root.addView(modes);

        fpsSelector = new LinearLayout(this);
        fpsSelector.setOrientation(LinearLayout.HORIZONTAL);
        fpsSelector.setWeightSum(2f);

        fps60Button = primaryButton("60 FPS");
        fps90Button = secondaryButton("90 FPS");

        fps60Button.setOnClickListener(v -> selectFps(60));
        fps90Button.setOnClickListener(v -> selectFps(90));

        fpsSelector.addView(fps60Button, weightLp(1f, 0, 0, 8));
        fpsSelector.addView(fps90Button, weightLp(1f, 8, 0, 0));
        root.addView(fpsSelector, lp(0,0,0,14));

        serverPanel = card();
        TextView serverTitle = label("НАСТРОЙКА СЕТИ", 13, PURPLE, Gravity.LEFT);
        serverTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        serverPanel.addView(serverTitle, lp(0,0,0,8));

        hostInput = field("IP или домен relay / IP ПК", p.getString("host",""));
        portInput = field("UDP порт", p.getString("port",DEFAULT_PORT));
        tokenInput = field("Секретный токен", p.getString("token",""));
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        serverPanel.addView(hostInput, lp(0,4,0,4));
        serverPanel.addView(portInput, lp(0,4,0,4));
        serverPanel.addView(tokenInput, lp(0,4,0,8));

        Button test = primaryButton("📡  Проверить соединение");
        test.setOnClickListener(v -> testRelay());
        serverPanel.addView(test);
        root.addView(serverPanel, lp(0,0,0,14));

        LinearLayout state = card();
        state.addView(label("СОСТОЯНИЕ", 13, PURPLE, Gravity.LEFT), lp(0,0,0,8));
        metrics = label("—",14,WHITE,Gravity.LEFT);
        status = label("Выбери режим.",13,MUTED,Gravity.LEFT);
        state.addView(metrics,lp(0,0,0,8));
        state.addView(status,lp(0,0,0,0));
        root.addView(state,lp(0,0,0,14));

        Button action = primaryButton("🚀  Запустить");
        action.setTag("mainAction");
        action.setOnClickListener(v -> {
            String mode = selectedMode();
            if ("fps".equals(mode)) {
                toggleFps();
            } else {
                startNetwork(mode);
            }
        });
        root.addView(action,lp(0,0,0,8));

        Button stop = secondaryButton("⛔  Отключить ВСЕ функции");
        stop.setOnClickListener(v -> stopAll());
        root.addView(stop,lp(0,0,0,8));

        Button game = secondaryButton("🎮  Открыть Standoff 2");
        game.setOnClickListener(v -> launchPackage(STANDOFF_PACKAGE));
        root.addView(game,lp(0,0,0,8));

        Button battery = secondaryButton("🛡  Стабильность / батарея");
        battery.setOnClickListener(v -> PerformanceController.requestIgnoreBattery(this));
        root.addView(battery,lp(0,0,0,8));

        Button shizuku = secondaryButton("🧩  Открыть / настроить Shizuku");
        shizuku.setOnClickListener(v -> openShizuku());
        root.addView(shizuku,lp(0,0,0,8));

        TextView foot = label(
            "⚠️ Маршрут не может гарантировать нулевые потери или меньший ping: результат зависит от провайдера и маршрута.\\n" +
            "Shizuku даёт ADB-level доступ без root; Fixed Performance поддерживается не на всех устройствах.",
            12,MUTED,Gravity.CENTER);
        root.addView(foot,lp(0,12,0,0));

        relayCard.setOnClickListener(v -> selectMode(MODE_RELAY));
        pcCard.setOnClickListener(v -> selectMode(MODE_PC));
        fpsCard.setOnClickListener(v -> selectMode("fps"));

        scroll.addView(root);
        setContentView(scroll);
    }

    private void selectMode(String mode) {
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(MODE,mode).apply();
        refreshUi();
    }

    private String selectedMode() {
        return getSharedPreferences(PREFS,MODE_PRIVATE).getString(MODE,MODE_RELAY);
    }

    private void refreshUi() {
        String mode = selectedMode();
        relayCard.setBackground(round(MODE_RELAY.equals(mode)?PURPLE_DARK:CARD_2,18));
        pcCard.setBackground(round(MODE_PC.equals(mode)?PURPLE_DARK:CARD_2,18));
        fpsCard.setBackground(round("fps".equals(mode)?PURPLE_DARK:CARD_2,18));

        serverPanel.setVisibility("fps".equals(mode)?View.GONE:View.VISIBLE);
        fpsSelector.setVisibility("fps".equals(mode)?View.VISIBLE:View.GONE);
        updateFpsButtons();
        Button action = (Button) root.findViewWithTag("mainAction");

        if ("fps".equals(mode)) {
            action.setText(fpsEnabled ? "🔥  " + targetFps + " FPS STABLE: ВЫКЛЮЧИТЬ" : "🔥  Включить " + targetFps + " FPS STABLE");
            metrics.setText("Режим: " + targetFps + " FPS STABLE\\nСостояние: " + (fpsEnabled?"включен":"выключен"));
            status.setText("Game Mode Performance + Fixed Performance + цель " + targetFps + " FPS.");
        } else if (MODE_PC.equals(mode)) {
            action.setText("🖥  Запустить через ПК");
            metrics.setText("Режим: PC BOOST\\nGhostly VPN: " +
                (GhostlyVpnService.isRunning()?"включен":"выключен") + "\\nRelay RTT: —");
            status.setText("Телефон и ПК должны быть доступны друг другу по сети.");
        } else {
            action.setText("⚡  Запустить GHOSTLY BOOST");
            metrics.setText("Режим: GHOSTLY BOOST\\nGhostly VPN: " +
                (GhostlyVpnService.isRunning()?"включен":"выключен") + "\\nRelay RTT: —");
            status.setText("Через relay идёт только Standoff 2.");
        }
    }

    private void startNetwork(String mode) {
        try {
            Config c = readConfig();
            saveConfig(true);
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(MODE,mode).apply();

            Intent prepare = VpnService.prepare(this);
            if (prepare != null) {
                startActivityForResult(prepare,4101);
            } else {
                startVpnService(c);
            }
        } catch(Exception e) {
            status.setText("❌ " + e.getMessage());
        }
    }

    private void startVpnService(Config c) {
        stopService(new Intent(this,GhostlyVpnService.class).setAction(GhostlyVpnService.ACTION_STOP));
        Intent i = new Intent(this,GhostlyVpnService.class)
            .setAction(GhostlyVpnService.ACTION_START)
            .putExtra(GhostlyVpnService.EXTRA_HOST,c.host)
            .putExtra(GhostlyVpnService.EXTRA_PORT,c.port)
            .putExtra(GhostlyVpnService.EXTRA_TOKEN,c.token);
        if (Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        status.setText("🚀 Подключение к " + c.host + ":" + c.port + "…");
        launchPackage(STANDOFF_PACKAGE);
        refreshUi();
    }

    private void selectFps(int fps) {
        if (fps != 60 && fps != 90) return;

        targetFps = fps;
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putInt("target_fps", fps)
            .apply();

        if (fpsEnabled) {
            shizukuPerformance.enable(fps, new ShizukuPerformanceManager.Callback() {
                @Override
                public void onSuccess(String details) {
                    status.setText("🔥 Переключено на " + fps + " FPS STABLE.");
                    refreshUi();
                }

                @Override
                public void onFailure(String message) {
                    status.setText("⚠️ Профиль " + fps + " FPS выбран, но Shizuku: " + message);
                    refreshUi();
                }
            });
        } else {
            status.setText("Выбран профиль " + fps + " FPS. Нажми включение режима.");
            refreshUi();
        }
    }

    private void updateFpsButtons() {
        if (fps60Button == null || fps90Button == null) return;

        fps60Button.setBackground(round(targetFps == 60 ? PURPLE_DARK : CARD_2, 14));
        fps90Button.setBackground(round(targetFps == 90 ? PURPLE_DARK : CARD_2, 14));
    }

    private void toggleFps() {
        if (!fpsEnabled) {
            fpsEnabled = true;
            PerformanceController.enable(this);
            status.setText("🔥 " + targetFps + " FPS STABLE запускается: Game Mode + Fixed Performance…");

            shizukuPerformance.enable(targetFps, new ShizukuPerformanceManager.Callback() {
                @Override
                public void onSuccess(String details) {
                    status.setText(
                        "🔥 " + targetFps + " FPS STABLE + SHIZUKU включён. " +
                        "Включены Game Mode Performance и Fixed Performance."
                    );
                    refreshUi();
                }

                @Override
                public void onFailure(String message) {
                    status.setText(
                        "⚠️ Базовый режим производительности работает. Shizuku: " +
                        message
                    );
                    refreshUi();
                }
            });
        } else {
            fpsEnabled = false;
            PerformanceController.disable(this);
            shizukuPerformance.disable(new ShizukuPerformanceManager.Callback() {
                @Override
                public void onSuccess(String details) {
                    status.setText("✅ " + targetFps + " FPS STABLE и Shizuku-профиль выключены.");
                    refreshUi();
                }

                @Override
                public void onFailure(String message) {
                    status.setText("✅ Базовый MAX PERFORMANCE выключен. " + message);
                    refreshUi();
                }
            });
            refreshUi();
        }
    }

    private void stopAll() {
        stopService(new Intent(this,GhostlyVpnService.class).setAction(GhostlyVpnService.ACTION_STOP));
        fpsEnabled = false;
        PerformanceController.disable(this);
        shizukuPerformance.disable(new ShizukuPerformanceManager.Callback() {
            @Override public void onSuccess(String details) {}
            @Override public void onFailure(String message) {}
        });
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("enabled",false).apply();
        status.setText("⛔ Все функции Ghostly отключены.");
        refreshUi();
    }

    private void testRelay() {
        try {
            Config c = readConfig();
            status.setText("⏳ Проверяю UDP…");
            new Thread(() -> {
                long started = System.nanoTime();
                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.setSoTimeout(3000);
                    byte[] hello = RelayProtocol.handshake(c.token);
                    socket.send(new DatagramPacket(hello,hello.length,new InetSocketAddress(c.host,c.port)));
                    byte[] buf = new byte[256];
                    DatagramPacket reply = new DatagramPacket(buf,buf.length);
                    socket.receive(reply);
                    RelayProtocol.HandshakeReply parsed = RelayProtocol.parseHandshakeReply(reply.getData(),reply.getLength(),c.token);
                    long rtt=(System.nanoTime()-started)/1_000_000L;
                    runOnUiThread(() -> {
                        metrics.setText("Режим: " + ("pc".equals(selectedMode())?"PC BOOST":"GHOSTLY BOOST") +
                            "\\nGhostly VPN: выключен\\nRelay RTT: "+rtt+" ms");
                        status.setText("✅ Соединение есть. Tunnel IP: "+parsed.virtualIp);
                    });
                } catch(Exception e) {
                    runOnUiThread(() -> status.setText("❌ Нет соединения: "+e.getMessage()));
                }
            },"GhostlyRelayTest").start();
        } catch(Exception e) { status.setText("❌ "+e.getMessage()); }
    }

    private void saveConfig(boolean enabled) {
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putString("host",hostInput.getText().toString().trim())
            .putString("port",portInput.getText().toString().trim())
            .putString("token",tokenInput.getText().toString().trim())
            .putBoolean("enabled",enabled).apply();
    }

    private Config readConfig() {
        String host=hostInput.getText().toString().trim();
        String portText=portInput.getText().toString().trim();
        String token=tokenInput.getText().toString().trim();
        if(host.isEmpty()) throw new IllegalArgumentException("Не указан IP relay или ПК.");
        if(token.length()<16) throw new IllegalArgumentException("Секретный токен слишком короткий.");
        int port;
        try { port=Integer.parseInt(portText); } catch(Exception e) { throw new IllegalArgumentException("UDP порт должен быть числом."); }
        if(port<1||port>65535) throw new IllegalArgumentException("UDP порт должен быть от 1 до 65535.");
        return new Config(host,port,token);
    }

    private LinearLayout modeCard(String title,String desc,String tag) {
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(14),dp(14),dp(12),dp(14));
        box.setBackground(round(CARD_2,18)); box.setTag(tag);
        TextView t=label(title,15,WHITE,Gravity.LEFT); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(t,lp(0,0,0,5)); box.addView(label(desc,12,MUTED,Gravity.LEFT));
        return box;
    }

    private LinearLayout card() { LinearLayout b=new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setPadding(dp(14),dp(14),dp(14),dp(14)); b.setBackground(round(CARD,18)); return b; }
    private TextView label(String text,float size,int color,int gravity) { TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color); t.setGravity(gravity); t.setLineSpacing(0f,1.08f); return t; }
    private TextView sectionTitle(String v) { TextView t=label(v,11,MUTED,Gravity.LEFT); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t; }
    private EditText field(String hint,String value) { EditText e=new EditText(this); e.setHint(hint); e.setText(value); e.setTextColor(WHITE); e.setHintTextColor(Color.rgb(116,111,126)); e.setTextSize(14); e.setSingleLine(true); e.setPadding(dp(13),dp(4),dp(13),dp(4)); e.setBackground(round(Color.rgb(12,11,17),12)); return e; }
    private Button primaryButton(String text) { Button b=baseButton(text); b.setTextColor(Color.WHITE); b.setBackground(round(PURPLE_DARK,14)); return b; }
    private Button secondaryButton(String text) { Button b=baseButton(text); b.setTextColor(WHITE); b.setBackground(round(CARD_2,14)); return b; }
    private Button baseButton(String text) { Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(14); b.setMinHeight(dp(52)); b.setPadding(dp(12),0,dp(12),0); return b; }

    private void openShizuku() {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (i != null) {
                startActivity(i);
                return;
            }
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"));
            startActivity(web);
        } catch (Exception e) {
            status.setText("Установи Shizuku из официального источника.");
        }
    }

    private void launchPackage(String pkg) {
        try { Intent i=getPackageManager().getLaunchIntentForPackage(pkg); if(i!=null){startActivity(i);return;} openPackageSettings(pkg); }
        catch(Exception e){openPackageSettings(pkg);}
    }
    private void openPackageSettings(String pkg) { try { Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); i.setData(Uri.parse("package:"+pkg)); startActivity(i); } catch(Exception ignored){} }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=4101)return;
        if(resultCode==RESULT_OK) {
            try { startVpnService(readConfig()); } catch(Exception e) { status.setText("❌ "+e.getMessage()); }
        } else {
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("enabled",false).apply();
            status.setText("❌ Разрешение VPN не выдано.");
        }
    }

    private android.graphics.drawable.GradientDrawable round(int color,int radius) { android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private LinearLayout.LayoutParams lp(int l,int t,int r,int b) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p; }
    @Override protected void onDestroy() {
        try {
            if (shizukuPerformance != null) shizukuPerformance.shutdown();
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    private LinearLayout.LayoutParams weightLp(float weight, int l, int t, int r) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, weight
        );
        p.setMargins(dp(l), dp(t), dp(r), 0);
        return p;
    }

    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }

    private static final class Config {
        final String host; final int port; final String token;
        Config(String host,int port,String token){this.host=host;this.port=port;this.token=token;}
    }
}