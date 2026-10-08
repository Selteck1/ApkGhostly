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
    private static final String MODE_DIRECT = "direct";
    private static final String MODE_VPS = "vps";
    private static final String DEFAULT_PORT = "51888";
    private static final String STANDOFF_PACKAGE = "com.axlebolt.standoff2";
    private static final String TERMUX_PACKAGE = "com.termux";

    private final int BG = Color.rgb(8, 7, 12);
    private final int CARD = Color.rgb(18, 16, 26);
    private final int CARD_2 = Color.rgb(24, 21, 34);
    private final int PURPLE = Color.rgb(188, 124, 255);
    private final int PURPLE_DARK = Color.rgb(104, 58, 156);
    private final int WHITE = Color.rgb(245, 242, 250);
    private final int MUTED = Color.rgb(164, 158, 174);

    private LinearLayout root;
    private LinearLayout vpsPanel;
    private LinearLayout modeDirect;
    private LinearLayout modeVps;
    private TextView metrics;
    private TextView status;
    private EditText hostInput;
    private EditText portInput;
    private EditText tokenInput;
    private String currentRtt = "—";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        refreshModeUi();
    }

    private void buildUi() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

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
            "Сетевой режим для Standoff 2\n" +
            "Без root • без изменения игры • только сеть",
            14, MUTED, Gravity.LEFT
        );

        root.addView(brand, lp(0, 0, 0, 2));
        root.addView(title, lp(0, 0, 0, 2));
        root.addView(subtitle, lp(0, 0, 0, 16));

        root.addView(sectionTitle("РЕЖИМ ПОДКЛЮЧЕНИЯ"), lp(0, 6, 0, 8));

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setWeightSum(2f);

        modeDirect = modeCard(
            "⚡  ПРЯМОЙ",
            "Без VPS\nобычный маршрут телефона",
            MODE_DIRECT
        );
        modeVps = modeCard(
            "🚀  VPS RELAY",
            "Через твой сервер\nдля теста маршрута",
            MODE_VPS
        );

        modes.addView(modeDirect, weightLp(1f, 0, 0, 8));
        modes.addView(modeVps, weightLp(1f, 8, 0, 0));
        root.addView(modes, lp(0, 0, 0, 14));

        LinearLayout info = card();
        TextView infoTitle = label("Что делает режим", 15, WHITE, Gravity.LEFT);
        TextView infoBody = label("", 13, MUTED, Gravity.LEFT);
        infoBody.setTag("modeInfo");
        info.addView(infoTitle, lp(0, 0, 0, 4));
        info.addView(infoBody, lp(0, 0, 0, 0));
        root.addView(info, lp(0, 0, 0, 14));

        vpsPanel = card();
        TextView vpsTitle = label("НАСТРОЙКА VPS RELAY", 14, PURPLE, Gravity.LEFT);
        vpsTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        vpsPanel.addView(vpsTitle, lp(0, 0, 0, 8));

        hostInput = field("IP или домен VPS", prefs.getString("host", ""));
        portInput = field("UDP порт", prefs.getString("port", DEFAULT_PORT));
        tokenInput = field("Секретный токен", prefs.getString("token", ""));
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        vpsPanel.addView(hostInput, lp(0, 4, 0, 4));
        vpsPanel.addView(portInput, lp(0, 4, 0, 4));
        vpsPanel.addView(tokenInput, lp(0, 4, 0, 8));

        Button test = primaryButton("📡  Проверить VPS");
        test.setOnClickListener(v -> testRelay());
        vpsPanel.addView(test, lp(0, 0, 0, 0));

        root.addView(vpsPanel, lp(0, 0, 0, 14));

        LinearLayout statusCard = card();
        TextView statusTitle = label("СОСТОЯНИЕ", 13, PURPLE, Gravity.LEFT);
        statusTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        metrics = label("Режим: —\nGhostly VPN: выключен\nRelay RTT: —", 14, WHITE, Gravity.LEFT);
        status = label("Выбери режим.", 13, MUTED, Gravity.LEFT);

        statusCard.addView(statusTitle, lp(0, 0, 0, 8));
        statusCard.addView(metrics, lp(0, 0, 0, 8));
        statusCard.addView(status, lp(0, 0, 0, 0));
        root.addView(statusCard, lp(0, 0, 0, 14));

        Button mainAction = primaryButton("⚡  Запустить");
        mainAction.setTag("mainAction");
        mainAction.setOnClickListener(v -> {
            if (MODE_VPS.equals(selectedMode())) {
                prepareAndStartVps();
            } else {
                activateDirect();
            }
        });
        root.addView(mainAction, lp(0, 0, 0, 8));

        Button stop = secondaryButton("⛔  Остановить Ghostly");
        stop.setOnClickListener(v -> stopBoost());
        root.addView(stop, lp(0, 0, 0, 8));

        Button standoff = secondaryButton("🎮  Открыть Standoff 2");
        standoff.setOnClickListener(v -> launchPackage(STANDOFF_PACKAGE));
        root.addView(standoff, lp(0, 0, 0, 8));

        Button stable = secondaryButton("🛡  Стабильный режим");
        stable.setOnClickListener(v -> openStabilitySettings());
        root.addView(stable, lp(0, 0, 0, 8));

        Button termux = secondaryButton("📟  Открыть настройки Termux");
        termux.setOnClickListener(v -> openPackageSettings(TERMUX_PACKAGE));
        root.addView(termux, lp(0, 0, 0, 8));

        TextView foot = label(
            "Прямой режим = без посредника.\n" +
            "VPS режим = Standoff 2 через твой relay.\n" +
            "Другой VPN/прокси телефона может изменить маршрут.",
            12, MUTED, Gravity.CENTER
        );
        root.addView(foot, lp(0, 12, 0, 0));

        modeDirect.setOnClickListener(v -> selectMode(MODE_DIRECT));
        modeVps.setOnClickListener(v -> selectMode(MODE_VPS));

        scroll.addView(root);
        setContentView(scroll);
    }

    private void selectMode(String mode) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(MODE, mode)
            .apply();

        if (MODE_DIRECT.equals(mode)) {
            stopService(new Intent(this, GhostlyVpnService.class)
                .setAction(GhostlyVpnService.ACTION_STOP));
        }

        refreshModeUi();
    }

    private void refreshModeUi() {
        String mode = selectedMode();

        modeDirect.setBackground(round(
            MODE_DIRECT.equals(mode) ? PURPLE_DARK : CARD_2, 18
        ));
        modeVps.setBackground(round(
            MODE_VPS.equals(mode) ? PURPLE_DARK : CARD_2, 18
        ));

        TextView modeInfo = (TextView) root.findViewWithTag("modeInfo");

        if (MODE_VPS.equals(mode)) {
            vpsPanel.setVisibility(View.VISIBLE);
            modeInfo.setText(
                "Через VPS: только трафик Standoff 2 попадает в Ghostly relay.\n" +
                "Остальной трафик телефона через relay не идет."
            );
            setText(findMainAction(), "🚀  Запустить через VPS");
            metrics.setText(
                "Режим: VPS relay\nGhostly VPN: " +
                (GhostlyVpnService.isRunning() ? "включен" : "выключен") +
                "\nRelay RTT: " + currentRtt
            );
            status.setText("Введи IP, порт и секретный токен VPS.");
        } else {
            vpsPanel.setVisibility(View.GONE);
            modeInfo.setText(
                "Напрямую: Ghostly не ставит relay и не создает туннель.\n" +
                "Standoff 2 использует обычное сетевое подключение Android."
            );
            setText(findMainAction(), "⚡  Играть напрямую");

            boolean otherVpn = hasActiveVpn();
            metrics.setText(
                "Режим: прямой\nGhostly VPN: выключен\n" +
                "Другой VPN: " + (otherVpn ? "обнаружен" : "не обнаружен")
            );
            status.setText(
                otherVpn
                    ? "⚠️ Найден другой VPN. Прямой режим Ghostly его не отключает."
                    : "✅ Можно играть без relay."
            );
        }
    }

    private String selectedMode() {
        return getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(MODE, MODE_DIRECT);
    }

    private View findMainAction() {
        return root.findViewWithTag("mainAction");
    }

    private void setText(View view, String text) {
        if (view instanceof Button) {
            ((Button) view).setText(text);
        }
    }

    private void activateDirect() {
        stopService(new Intent(this, GhostlyVpnService.class)
            .setAction(GhostlyVpnService.ACTION_STOP));

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(MODE, MODE_DIRECT)
            .putBoolean("enabled", false)
            .apply();

        boolean otherVpn = hasActiveVpn();
        metrics.setText(
            "Режим: прямой\nGhostly VPN: выключен\n" +
            "Другой VPN: " + (otherVpn ? "обнаружен" : "не обнаружен")
        );
        status.setText(
            otherVpn
                ? "⚠️ Прямой режим активен. Другой VPN не принадлежит Ghostly и может менять маршрут."
                : "✅ Прямой режим активен. Ghostly relay не используется."
        );

        launchPackage(STANDOFF_PACKAGE);
    }

    private void stopBoost() {
        stopService(new Intent(this, GhostlyVpnService.class)
            .setAction(GhostlyVpnService.ACTION_STOP));

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean("enabled", false)
            .apply();

        metrics.setText(
            "Режим: " + (MODE_VPS.equals(selectedMode()) ? "VPS relay" : "прямой") +
            "\nGhostly VPN: выключен\nRelay RTT: " + currentRtt
        );
        status.setText("⛔ Ghostly остановлен.");
    }

    private void prepareAndStartVps() {
        try {
            Config c = readConfig();

            saveConfig(true);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(MODE, MODE_VPS)
                .apply();

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
        stopService(new Intent(this, GhostlyVpnService.class)
            .setAction(GhostlyVpnService.ACTION_STOP));

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

        metrics.setText(
            "Режим: VPS relay\nGhostly VPN: запуск…\nRelay RTT: " + currentRtt
        );
        status.setText(
            "🚀 Подключение к VPS. Android покажет системное разрешение VPN."
        );
    }

    private void testRelay() {
        try {
            Config c = readConfig();
            saveConfig(false);
            status.setText("⏳ Проверяю UDP relay…");

            new Thread(() -> {
                long started = System.nanoTime();

                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.setSoTimeout(3000);

                    byte[] hello = RelayProtocol.handshake(c.token);

                    socket.send(new DatagramPacket(
                        hello,
                        hello.length,
                        new InetSocketAddress(c.host, c.port)
                    ));

                    byte[] buf = new byte[256];
                    DatagramPacket reply = new DatagramPacket(buf, buf.length);
                    socket.receive(reply);

                    RelayProtocol.HandshakeReply parsed =
                        RelayProtocol.parseHandshakeReply(
                            reply.getData(),
                            reply.getLength(),
                            c.token
                        );

                    long rtt = (System.nanoTime() - started) / 1_000_000L;
                    currentRtt = rtt + " ms";

                    runOnUiThread(() -> {
                        metrics.setText(
                            "Режим: VPS relay\n" +
                            "Ghostly VPN: выключен\n" +
                            "Relay RTT: " + currentRtt
                        );
                        status.setText(
                            "✅ VPS доступен. Tunnel IP: " + parsed.virtualIp
                        );
                    });
                } catch (Exception e) {
                    runOnUiThread(() ->
                        status.setText("❌ VPS недоступен: " + e.getMessage())
                    );
                }
            }, "GhostlyRelayTest").start();

        } catch (Exception e) {
            status.setText("❌ " + e.getMessage());
        }
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

        if (host.isEmpty()) {
            throw new IllegalArgumentException("Не указан IP или домен VPS.");
        }

        if (token.length() < 16) {
            throw new IllegalArgumentException("Секретный токен слишком короткий.");
        }

        int port;

        try {
            port = Integer.parseInt(portText);
        } catch (Exception e) {
            throw new IllegalArgumentException("UDP порт должен быть числом.");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                "UDP порт должен быть от 1 до 65535."
            );
        }

        return new Config(host, port, token);
    }

    private LinearLayout modeCard(String title, String desc, String mode) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(12), dp(14));
        box.setBackground(round(CARD_2, 18));
        box.setTag(mode);

        TextView t = label(title, 14, WHITE, Gravity.LEFT);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        TextView d = label(desc, 12, MUTED, Gravity.LEFT);

        box.addView(t, lp(0, 0, 0, 5));
        box.addView(d, lp(0, 0, 0, 0));

        return box;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(round(CARD, 18));
        return box;
    }

    private TextView label(String text, float size, int color, int gravity) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(gravity);
        t.setLineSpacing(0f, 1.08f);
        return t;
    }

    private TextView sectionTitle(String value) {
        TextView t = label(value, 11, MUTED, Gravity.LEFT);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(WHITE);
        e.setHintTextColor(Color.rgb(116, 111, 126));
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setPadding(dp(13), dp(4), dp(13), dp(4));
        e.setBackground(round(Color.rgb(12, 11, 17), 12));
        return e;
    }

    private Button primaryButton(String text) {
        Button b = baseButton(text);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(PURPLE_DARK, 14));
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = baseButton(text);
        b.setTextColor(WHITE);
        b.setBackground(round(CARD_2, 14));
        return b;
    }

    private Button baseButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setMinHeight(dp(52));
        b.setPadding(dp(12), 0, dp(12), 0);
        return b;
    }

    private boolean hasActiveVpn() {
        try {
            ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

            if (cm == null) return false;

            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);

                if (caps != null &&
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private void openStabilitySettings() {
        try {
            PowerManager pm =
                (PowerManager) getSystemService(POWER_SERVICE);

            if (Build.VERSION.SDK_INT >= 23 &&
                !pm.isIgnoringBatteryOptimizations(getPackageName())) {

                Intent own = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())
                );

                startActivity(own);

                status.setText(
                    "🛡 Открой Ghostly → Батарея → Без ограничений.\n" +
                    "После этого так же выставь «Без ограничений» для " +
                    "Termux и Standoff 2."
                );
            } else {
                status.setText(
                    "✅ Для Ghostly оптимизация уже снята.\n" +
                    "Для стабильности: Termux и Standoff 2 → " +
                    "Батарея → Без ограничений."
                );
            }
        } catch (Exception e) {
            status.setText(
                "⚙️ Android → Приложения → Ghostly → " +
                "Батарея → Без ограничений."
            );
        }
    }

    private void openPackageSettings(String pkg) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + pkg));
            startActivity(i);
        } catch (Exception e) {
            status.setText("Не удалось открыть настройки: " + pkg);
        }
    }

    private void launchPackage(String pkg) {
        try {
            Intent launch =
                getPackageManager().getLaunchIntentForPackage(pkg);

            if (launch != null) {
                startActivity(launch);
                return;
            }

            openPackageSettings(pkg);
        } catch (Exception e) {
            openPackageSettings(pkg);
        }
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != 4101) return;

        if (resultCode == RESULT_OK) {
            try {
                startVpnService(readConfig());
            } catch (Exception e) {
                status.setText("❌ " + e.getMessage());
            }
        } else {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean("enabled", false)
                .apply();

            status.setText("❌ Разрешение VPN не выдано.");
        }
    }

    private android.graphics.drawable.GradientDrawable round(
        int color,
        int radius
    ) {
        android.graphics.drawable.GradientDrawable d =
            new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private LinearLayout.LayoutParams lp(
        int l,
        int t,
        int r,
        int b
    ) {
        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );

        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout.LayoutParams weightLp(
        float weight,
        int l,
        int t,
        int r
    ) {
        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                weight
            );

        p.setMargins(dp(l), dp(t), dp(r), 0);
        return p;
    }

    private int dp(int value) {
        return Math.round(
            value * getResources().getDisplayMetrics().density
        );
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
