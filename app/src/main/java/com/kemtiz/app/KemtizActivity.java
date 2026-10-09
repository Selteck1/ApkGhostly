package com.kemtiz.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import org.json.JSONObject;

public class KemtizActivity extends Activity {
    private static final String APP_URL =
        "https://appassets.androidplatform.net/assets/kemtiz/index.html";
    private static final int BG = Color.rgb(11, 12, 17);
    private static final int PANEL = Color.rgb(24, 25, 36);
    private static final int ACCENT = Color.rgb(132, 98, 220);
    private static final int WHITE = Color.rgb(244, 241, 250);
    private static final int MUTED = Color.rgb(157, 153, 172);

    private WebView webView;
    private LinearLayout root;
    private LinearLayout errorPanel;
    private WebViewAssetLoader assetLoader;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.BLACK);
        buildLayout();
        loadApp();
    }

    private void buildLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();

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
            public WebResourceResponse shouldInterceptRequest(
                WebView view, WebResourceRequest request
            ) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equalsIgnoreCase(uri.getScheme())
                    && "appassets.androidplatform.net".equalsIgnoreCase(uri.getHost())) {
                    return false;
                }
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {
                    }
                }
                return true;
            }

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
            public void onReceivedError(
                WebView view, WebResourceRequest request, WebResourceError error
            ) {
                if (request.isForMainFrame()) showServerError();
            }

            @Override
            public void onReceivedSslError(
                WebView view, SslErrorHandler handler, SslError error
            ) {
                handler.cancel();
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
            "Не удалось открыть интерфейс Kemtiz.\\nПерезапусти приложение или установи APK заново.",
            14, MUTED, Gravity.CENTER);
        LinearLayout.LayoutParams descLp = wrap();
        descLp.topMargin = dp(12);
        errorPanel.addView(description, descLp);

        Button retry = button("↻  Повторить запуск");
        retry.setBackground(round(ACCENT, 14));
        retry.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams retryLp = match();
        retryLp.topMargin = dp(22);
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
    }

    private void beginGoogleSignIn(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            sendGoogleError("Google-вход не настроен. Добавь OAuth Client ID в настройки сервера.");
            return;
        }
        try {
            GetGoogleIdOption googleOption = new GetGoogleIdOption.Builder()
                .setServerClientId(clientId.trim())
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build();
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
                            && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(
                                credential.getType())) {
                            try {
                                GoogleIdTokenCredential googleCredential =
                                    GoogleIdTokenCredential.createFrom(
                                        ((CustomCredential) credential).getData());
                                sendGoogleCredential(googleCredential.getIdToken());
                            } catch (Exception error) {
                                sendGoogleError("Не удалось прочитать Google-аккаунт. Попробуй ещё раз.");
                            }
                        } else {
                            sendGoogleError("Google вернул неподдерживаемый тип аккаунта.");
                        }
                    }

                    @Override
                    public void onError(GetCredentialException error) {
                        sendGoogleError(
                            "Вход через Google отменён или не завершён. Попробуй выбрать аккаунт ещё раз.");
                    }
                }
            );
        } catch (Exception error) {
            sendGoogleError("Не удалось открыть выбор Google-аккаунта. Проверь Google Play Services.");
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
        String quoted = JSONObject.quote(
            message == null ? "Не удалось войти через Google." : message);
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript(
                    "window.KemtizNativeGoogleError && window.KemtizNativeGoogleError(" + quoted + ");",
                    null
                );
            }
        });
    }

    private void loadApp() {
        errorPanel.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(APP_URL);
    }

    private void showServerError() {
        runOnUiThread(() -> {
            webView.setVisibility(View.GONE);
            errorPanel.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
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
        android.graphics.drawable.GradientDrawable drawable =
            new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
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
