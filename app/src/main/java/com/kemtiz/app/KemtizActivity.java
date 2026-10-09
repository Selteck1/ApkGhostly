package com.kemtiz.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
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
    private static final String LOCAL_URL = "http://127.0.0.1:8000/";
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
            "Сервер пока не запущен на этом телефоне.\nЗапусти backend в Termux, затем нажми «Повторить».",
            14, MUTED, Gravity.CENTER);
        LinearLayout.LayoutParams descLp = wrap();
        descLp.topMargin = dp(12);
        errorPanel.addView(description, descLp);

        Button help = button("Как запустить сервер");
        LinearLayout.LayoutParams helpLp = match();
        helpLp.topMargin = dp(20);
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

    private void loadApp() {
        errorPanel.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(LOCAL_URL);
    }

    private void showServerError() {
        runOnUiThread(() -> {
            webView.setVisibility(View.GONE);
            errorPanel.setVisibility(View.VISIBLE);
        });
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
            .setTitle("Запуск Kemtiz")
            .setMessage(
                "1. Открой Termux.\n\n" +
                "2. Перейди в папку проекта.\n\n" +
                "3. Активируй окружение и выполни:\n" +
                "cd kemtiz && uvicorn server:app --host 127.0.0.1 --port 8000\n\n" +
                "4. Не закрывай Termux, пока тестируешь мессенджер.\n\n" +
                "Когда сервер запустится, вернись сюда и нажми «Повторить подключение»."
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
