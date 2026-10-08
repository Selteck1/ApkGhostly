package com.ghostly.apk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class MainActivity extends Activity {
    private static final String PREFS = "ghostly_booster";
    private static final String DEFAULT_PORT = "51888";
    private static final String STANDOFF_PACKAGE = "com.axlebolt.standoff2";
    private static final String TERMUX_PACKAGE = "com.termux";

    private EditText hostInput;
    private EditText portInput;
    private EditText tokenInput;
    private TextView status;
    private TextView metrics;
    private String currentRtt = "—";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(26, 28, 26, 24);
        root.setBackgroundColor(Color.rgb(8, 7, 12));

        TextView title = text("👻 GHOSTLY BOOSTER", 28, Color.rgb(198, 135, 255));
        title.setTypeface(null, 1);

        TextView subtitle = text(
            "UDP relay для Standoff 2\n" +
            "Без root • только трафик игры • без изменения игры",
            15, Color.LTGRAY
        );

        hostInput = field("IP / домен relay", prefs.getString("host", ""));
        portInput = field("UDP порт", prefs.getString("port", DEFAULT_PORT));
        tokenInput = field("Секретный токен relay", prefs.getString("token", ""));
        tokenInput.setInputType(129);

        Button test = button("📡 Проверить relay");
        Button start = button("⚡ Запустить Ghostly Boost");
        Button stop = button("⛔ Остановить");
        Button stable = button("🛡 Настроить стабильный режим");
        Button standoff = button("🎮 Открыть Standoff 2");
        Button termux = button("📟 Открыть Termux");

        metrics = text("Relay RTT: —\nVPN: выключен", 15, Color.WHITE);
        status = text("Введи адрес relay и токен.", 14, Color.LTGRAY);

        root.addView(title, lp());
        root.addView(subtitle, lp());
        root.addView(hostInput, lp());
        root.addView(portInput, lp());
        root.addView(tokenInput, lp());
        root.addView(test, lp());
        root.addView(start, lp());
        root.addView(stop, lp());
        root.addView(stable, lp());
        root.addView(standoff, lp());
        root.addView(termux, lp());
        root.addView(metrics, lp());
        root.addView(status, lp());

        test.setOnClickListener(v -> testRelay());
        start.setOnClickListener(v -> prepareAndStart());
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, GhostlyVpnService.class)
                .setAction(GhostlyVpnService.ACTION_STOP));
            metrics.setText("Relay RTT: " + currentRtt + "\nVPN: выключен");
            status.setText("⛔ Ghostly Boost остановлен.");
        });
        stable.setOnClickListener(v -> openStabilitySettings());
        standoff.setOnClickListener(v -> openPackage(STANDOFF_PACKAGE));
        termux.setOnClickListener(v -> openPackage(TERMUX_PACKAGE));

        setContentView(root);
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 8, 0, 10);
        return t;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        return b;
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY);
        e.setSingleLine(true);
        e.setPadding(18, 10, 18, 10);
        return e;
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 4, 0, 4);
        return p;
    }

    private void saveConfig(boolean enabled) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("host", hostInput.getText().toString().trim())
            .putString("port", portInput.getText().toString().trim())
            .putString("token", tokenInput.getText().toString().trim())
            .putBoolean("enabled", enabled)
            .apply();
    }

    private Config readConfig() {
        String host = hostInput.getText().toString().trim();
        String portText = portInput.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();

        if (host.isEmpty()) throw new IllegalArgumentException("Не указан адрес relay.");
        if (token.length() < 16) throw new IllegalArgumentException("Токен relay должен быть не короче 16 символов.");

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (Exception e) {
            throw new IllegalArgumentException("Порт должен быть числом.");
        }
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Неверный UDP порт.");

        return new Config(host, port, token);
    }

    private void testRelay() {
        try {
            Config c = readConfig();
            saveConfig(false);
            status.setText("⏳ Проверяю UDP relay...");

            new Thread(() -> {
                long started = System.nanoTime();
                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.setSoTimeout(3000);
                    byte[] hello = RelayProtocol.handshake(c.token);
                    socket.send(new DatagramPacket(
                        hello, hello.length, new InetSocketAddress(c.host, c.port)
                    ));

                    byte[] buf = new byte[256];
                    DatagramPacket reply = new DatagramPacket(buf, buf.length);
                    socket.receive(reply);

                    RelayProtocol.HandshakeReply parsed =
                        RelayProtocol.parseHandshakeReply(reply.getData(), reply.getLength(), c.token);

                    long rtt = (System.nanoTime() - started) / 1_000_000L;
                    currentRtt = rtt + " ms";

                    runOnUiThread(() -> {
                        metrics.setText("Relay RTT: " + currentRtt + "\nVPN: выключен");
                        status.setText("✅ Relay доступен.\nTunnel IP: " + parsed.virtualIp);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> status.setText("❌ Relay недоступен: " + e.getMessage()));
                }
            }).start();
        } catch (Exception e) {
            status.setText("❌ " + e.getMessage());
        }
    }

    private void prepareAndStart() {
        try {
            Config c = readConfig();
            saveConfig(true);

            Intent prepare = VpnService.prepare(this);
            if (prepare != null) {
                startActivityForResult(prepare, 4101);
            } else {
                startVpnService(c);
            }
        } catch (Exception e) {
            status.setText("❌ " + e.getMessage());
        }
    }

    private void startVpnService(Config c) {
        Intent intent = new Intent(this, GhostlyVpnService.class)
            .setAction(GhostlyVpnService.ACTION_START)
            .putExtra(GhostlyVpnService.EXTRA_HOST, c.host)
            .putExtra(GhostlyVpnService.EXTRA_PORT, c.port)
            .putExtra(GhostlyVpnService.EXTRA_TOKEN, c.token);

        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        metrics.setText("Relay RTT: " + currentRtt + "\nVPN: запуск...");
        status.setText("⚡ Boost запускается. Android покажет системное подтверждение VPN.");
    }

    private void openStabilitySettings() {
        try {
            PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
            if (Build.VERSION.SDK_INT >= 23 && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent own = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())
                );
                startActivity(own);
                status.setText("🛡 Сначала выставь Ghostly: «Без ограничений». Затем открой настройки Termux и Standoff 2.");
            } else {
                status.setText(
                    "✅ Для Ghostly уже снята оптимизация батареи.\n\n" +
                    "Termux: Батарея → Без ограничений.\n" +
                    "Standoff 2: Батарея → Без ограничений и закрепи игру в недавних."
                );
            }
        } catch (Exception e) {
            status.setText("⚙️ Открой Android → Приложения → Ghostly → Батарея → Без ограничений.");
        }
    }

    private void openPackage(String pkg) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + pkg));
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
            } catch (Exception ignored) {
            }
            status.setText("Не удалось открыть настройки пакета: " + pkg);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 4101) {
            if (resultCode == RESULT_OK) {
                try {
                    startVpnService(readConfig());
                } catch (Exception e) {
                    status.setText("❌ " + e.getMessage());
                }
            } else {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
                status.setText("❌ Разрешение VPN не выдано.");
            }
        }
    }

    private static final class Config {
        final String host;
        final int port;
        final String token;

        Config(String host, int port, String token) {
            this.host = host;
            this.port = port;
            this.token = token;
        }
    }
}
