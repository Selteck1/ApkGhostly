package com.kemtiz.app;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.CancellationSignal;
import android.webkit.JavascriptInterface;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import org.json.JSONObject;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class KemtizActivity extends Activity {
    private static final String PREFS = "kemtiz_settings";
    private static final String DEFAULT_SERVER_URL = "";
    private static final int BG = Color.rgb(11, 12, 17);
    private static final int PANEL = Color.rgb(24, 25, 36);
    private static final int ACCENT = Color.rgb(132, 98, 220);
    private static final int WHITE = Color.rgb(244, 241, 250);
    private static final int MUTED = Color.rgb(157, 153, 172);

    private WebView webView;
    private LinearLayout root;
    private LinearLayout errorPanel;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.BLACK);
        buildLayout();
        if (state == null) {
            loadApp();
        } else if (webView != null) {
            webView.restoreState(state);
        } else {
            loadApp();
        }
    }

    private void buildLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        webView = new WebView(this);
        webView.setBackgroundColor(BG);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new NativeGoogleBridge(), "KemtizNativeGoogle");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                errorPanel.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                errorPanel.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showServerError();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                showServerError();
            }
        });

        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(dp(28), dp(28), dp(28), dp(28));
        errorPanel.setBackgroundColor(BG);

        TextView mark = label("K", 48, Color.WHITE, Gravity.CENTER);
        mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        mark.setBackground(round(ACCENT, 24));
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(dp(92), dp(92));
        markLp.gravity = Gravity.CENTER;
        errorPanel.addView(mark, markLp);

        TextView title = label("KEMTIZ", 27, WHITE, Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = wrap();
        titleLp.topMargin = dp(20);
        errorPanel.addView(title, titleLp);

        TextView description = label(
            "Подключись к общему серверу Kemtiz.\nОдин и тот же HTTPS-адрес нужен на телефоне и компьютере.",
            14, MUTED, Gravity.CENTER);
        LinearLayout.LayoutParams descLp = wrap();
        descLp.topMargin = dp(12);
        errorPanel.addView(description, descLp);

        Button server = button("⚙  Адрес общего сервера");
        LinearLayout.LayoutParams serverLp = match();
        serverLp.topMargin = dp(20);
        errorPanel.addView(server, serverLp);
        server.setOnClickListener(v -> editServerUrl());

        Button help = button("Как настроить сервер");
        LinearLayout.LayoutParams helpLp = match();
        helpLp.topMargin = dp(8);
        errorPanel.addView(help, helpLp);
        help.setOnClickListener(v -> showHelp());

        Button retry = button("↻  Повторить подключение");
        retry.setBackground(round(ACCENT, 14));
        retry.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams retryLp = match();
        retryLp.topMargin = dp(10);
        errorPanel.addView(retry, retryLp);
        retry.setOnClickListener(v -> loadApp());

        root.addView(webView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(errorPanel, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private final class NativeGoogleBridge {
        @JavascriptInterface
        public void signIn(String clientId) {
            runOnUiThread(() -> beginGoogleSignIn(clientId));
        }

        @JavascriptInterface
        public void scanQrCode() {
            runOnUiThread(() -> beginQrScan());
        }
    }

    private void beginQrScan() {
        try {
            GmsBarcodeScanning.getClient(this).startScan()
                .addOnSuccessListener(barcode -> {
                    String value = barcode == null ? null : barcode.getRawValue();
                    if (value == null || value.trim().isEmpty()) {
                        sendQrError("В QR-коде нет данных. Попробуй ещё раз.");
                    } else {
                        sendQrScanned(value.trim());
                    }
                })
                .addOnCanceledListener(() -> sendQrError("Сканирование QR-кода отменено."))
                .addOnFailureListener(error -> {
                    String detail = error == null ? "" : error.getClass().getSimpleName();
                    sendQrError("Не удалось открыть сканер QR-кода" +
                        (detail.isEmpty() ? "." : " (" + detail + ").") +
                        " Проверь интернет и сервисы Google Play.");
                });
        } catch (Exception error) {
            sendQrError("Не удалось запустить сканер QR-кода: " + error.getClass().getSimpleName());
        }
    }

    private void sendQrScanned(String value) {
        String quoted = JSONObject.quote(value == null ? "" : value);
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript(
                    "window.KemtizNativeQrScanned && window.KemtizNativeQrScanned(" + quoted + ");",
                    null
                );
            }
        });
    }

    private void sendQrError(String message) {
        String quoted = JSONObject.quote(message == null ? "Сканирование QR-кода не удалось." : message);
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript(
                    "window.KemtizNativeQrError && window.KemtizNativeQrError(" + quoted + ");",
                    null
                );
            }
        });
    }

    private void beginGoogleSignIn(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            sendGoogleError("Google-вход не настроен. Добавь OAuth Client ID в настройки сервера.");
            return;
        }
        requestGoogleCredential(clientId.trim(), true);
    }

    private void requestGoogleCredential(String clientId, boolean useButtonFlow) {
        try {
            androidx.credentials.CredentialOption googleOption;
            if (useButtonFlow) {
                // First try the dedicated "Sign in with Google" button flow.
                googleOption = new GetSignInWithGoogleOption.Builder(clientId).build();
            } else {
                // Google's recommended fallback: allow both returning and new accounts.
                googleOption = new GetGoogleIdOption.Builder()
                    .setServerClientId(clientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build();
            }

            GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build();
            CredentialManager manager = CredentialManager.create(this);
            manager.getCredentialAsync(
                this,
                request,
                new CancellationSignal(),
                command -> runOnUiThread(command),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        Credential credential = response.getCredential();
                        if (credential instanceof CustomCredential
                            && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            try {
                                GoogleIdTokenCredential googleCredential =
                                    GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
                                sendGoogleCredential(googleCredential.getIdToken());
                            } catch (Exception error) {
                                sendGoogleError("Google вернул ответ, который не удалось прочитать: "
                                    + error.getClass().getSimpleName() + ". Попробуй ещё раз.");
                            }
                        } else {
                            sendGoogleError("Google вернул неподдерживаемый тип аккаунта: "
                                + credential.getClass().getSimpleName() + ".");
                        }
                    }

                    @Override
                    public void onError(GetCredentialException error) {
                        String type = error.getClass().getSimpleName();
                        String reason = error.getMessage() == null ? "" : error.getMessage().trim();
                        // Do not reopen the account sheet after the user explicitly cancelled it.
                        if (type.toLowerCase(java.util.Locale.ROOT).contains("cancel")) {
                            sendGoogleError("Вход через Google отменён. Нажми кнопку ещё раз, чтобы повторить.");
                            return;
                        }
                        if (useButtonFlow) {
                            requestGoogleCredential(clientId, false);
                            return;
                        }

                        String details = type + (reason.isEmpty() ? "" : ": " + reason);
                        if (details.length() > 280) details = details.substring(0, 280);
                        sendGoogleError(
                            "Не удалось открыть список аккаунтов Google (" + details + ").\n\n" +
                            "Проверь: Google Play Services включены и обновлены; Google-аккаунт добавлен " +
                            "в настройках телефона; в Google Cloud создан Android OAuth-клиент для package " +
                            "com.kemtiz.app с SHA-1 именно установленного APK. После исправления нажми вход ещё раз."
                        );
                    }
                }
            );
        } catch (Exception error) {
            String problem = error.getClass().getSimpleName();
            String reason = error.getMessage();
            if (reason != null && !reason.trim().isEmpty()) problem += ": " + reason.trim();
            if (problem.length() > 280) problem = problem.substring(0, 280);
            if (useButtonFlow) {
                requestGoogleCredential(clientId, false);
            } else {
                sendGoogleError("Credential Manager не смог открыть Google-вход (" + problem + "). "
                    + "Проверь Google Play Services, Android OAuth Client ID и SHA-1 подписи APK.");
            }
        }
    }

    private void sendGoogleCredential(String idToken) {
        if (webView == null || idToken == null || idToken.isEmpty()) {
            sendGoogleError("Google не вернул токен аккаунта.");
            return;
        }
        String quoted = JSONObject.quote(idToken);
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript(
                    "window.KemtizNativeGoogleCredential && window.KemtizNativeGoogleCredential(" + quoted + ");",
                    null
                );
            }
        });
    }

    private void sendGoogleError(String message) {
        String quoted = JSONObject.quote(message == null ? "Не удалось войти через Google." : message);
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript(
                    "window.KemtizNativeGoogleError && window.KemtizNativeGoogleError(" + quoted + ");",
                    null
                );
            }
        });
    }

    private String serverUrl() {
        String value = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString("server_url", DEFAULT_SERVER_URL).trim();
        if (value.isEmpty()) return "";
        return value.endsWith("/") ? value : value + "/";
    }

    private void loadApp() {
        String address = serverUrl();
        if (address.isEmpty()) {
            showServerError();
            return;
        }
        errorPanel.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(address);
    }

    private boolean isPrivateLanIPv4(String host) {
        if (host == null) return false;
        String[] parts = host.split("\\.");
        if (parts.length != 4) return false;
        int[] octets = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                if (parts[i].isEmpty() || parts[i].length() > 3) return false;
                octets[i] = Integer.parseInt(parts[i]);
                if (octets[i] < 0 || octets[i] > 255) return false;
            }
        } catch (NumberFormatException ex) {
            return false;
        }
        boolean privateRange = octets[0] == 10
            || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
            || (octets[0] == 192 && octets[1] == 168);
        return privateRange && octets[3] > 0 && octets[3] < 255;
    }

    private void editServerUrl() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setText(serverUrl());
        input.setHint("https://your-server.example");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_VARIATION_URI);

        new AlertDialog.Builder(this)
            .setTitle("Адрес сервера Kemtiz")
            .setMessage("Введи адрес общего сервера Kemtiz, который будет использоваться и на ПК. Для доступа через интернет обязателен HTTPS. HTTP разрешён только для локальной разработки в доверенной сети.")
            .setView(input)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить", (dialog, which) -> {
                String value = input.getText().toString().trim();
                if (value.isEmpty()) {
                    return;
                }
                if (!value.startsWith("http://") && !value.startsWith("https://")) {
                    value = "https://" + value;
                }
                Uri uri = Uri.parse(value);
                String host = uri.getHost();
                boolean localHttp = "http".equals(uri.getScheme()) &&
                    ("127.0.0.1".equals(host) || "localhost".equals(host) || isPrivateLanIPv4(host));
                if (host == null || (!"https".equals(uri.getScheme()) && !localHttp)) {
                    new AlertDialog.Builder(this)
                        .setMessage("Разрешены HTTPS-адреса, localhost или частный IPv4-адрес в доверенной локальной сети (10.x.x.x, 172.16–31.x.x, 192.168.x.x). HTTP не шифрует трафик.")
                        .setPositiveButton("ОК", null).show();
                    return;
                }
                String base = uri.buildUpon().path("/").query(null).fragment(null).build().toString();
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("server_url", base).apply();
                loadApp();
            })
            .show();
    }

    private void showServerError() {
        runOnUiThread(() -> {
            webView.setVisibility(View.GONE);
            errorPanel.setVisibility(View.VISIBLE);
        });
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
            .setTitle("Общий сервер Kemtiz")
            .setMessage(
                "1. Сервер и общая база должны быть развёрнуты отдельно.\n\n" +
                "2. Инструкция для владельца проекта:\n" +
                "https://github.com/Selteck1/ApkGhostly/blob/kemtiz-messenger/kemtiz/DEPLOY.md\n\n" +
                "3. Скопируй выданный HTTPS-адрес сервера в настройках Kemtiz на телефоне и на ПК.\n\n" +
                "Если сервер ещё не развёрнут, сначала заверши настройку Render и Neon по инструкции."
            )
            .setPositiveButton("Понятно", null)
            .show();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private TextView label(String text, float size, int color, int gravity) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(gravity);
        view.setLineSpacing(0f, 1.15f);
        return view;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(WHITE);
        button.setMinHeight(dp(50));
        button.setBackground(round(PANEL, 14));
        return button;
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
