package com.ghostly.apk;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private final Handler handler = new Handler();
    private boolean finished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        hideSystemUi();
        showIntro();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void showIntro() {
        VideoView video = new VideoView(this);
        video.setBackgroundColor(Color.BLACK);
        video.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        setContentView(video);

        Uri uri = Uri.parse(
                "android.resource://" + getPackageName() + "/" + R.raw.ghostly_intro
        );
        video.setVideoURI(uri);

        video.setOnCompletionListener(mp -> showReadyScreen());
        video.setOnErrorListener((mp, what, extra) -> {
            showReadyScreen();
            return true;
        });

        video.start();
        handler.postDelayed(this::showReadyScreen, 15500);
    }

    private void showReadyScreen() {
        if (finished) {
            return;
        }
        finished = true;
        handler.removeCallbacksAndMessages(null);
        hideSystemUi();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(Color.rgb(7, 4, 13));

        TextView logo = makeText("👻 GHOSTLY", 34, true);
        logo.setTextColor(Color.rgb(192, 130, 255));
        root.addView(logo, matchWrap());

        TextView title = makeText("Приложение работает", 28, true);
        title.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = 28;
        root.addView(title, titleParams);

        TextView status = makeText("Подключение к серверу...", 20, false);
        status.setTextColor(Color.rgb(224, 218, 235));
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = 28;
        root.addView(status, statusParams);

        Button retry = new Button(this);
        retry.setText("🔄 Повторить");
        retry.setVisibility(View.GONE);
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        retryParams.topMargin = 24;
        root.addView(retry, retryParams);

        retry.setOnClickListener(v -> checkServer(status, retry));

        setContentView(root);
        checkServer(status, retry);
    }

    private void checkServer(TextView status, Button retry) {
        status.setText("🟣 Подключение к серверу...");
        status.setTextColor(Color.rgb(224, 218, 235));
        retry.setVisibility(View.GONE);

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(getString(R.string.server_url) + "/api/health");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setUseCaches(false);

                int code = connection.getResponseCode();
                if (code != 200) {
                    throw new IllegalStateException("HTTP " + code);
                }

                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }
                }

                JSONObject json = new JSONObject(body.toString());
                boolean ok = json.optBoolean("ok", false);
                String version = json.optString("version", "?");

                if (!ok) {
                    throw new IllegalStateException("Server returned ok=false");
                }

                handler.post(() -> {
                    status.setText(
                            "✅ Сервер подключён

" +
                            "✅ Соединение установлено

" +
                            "✅ Система готова к работе

" +
                            "🟣 Ghostly API • v" + version
                    );
                    status.setTextColor(Color.rgb(180, 255, 195));
                });
            } catch (Exception e) {
                handler.post(() -> {
                    status.setText(
                            "❌ Сервер недоступен

" +
                            "Запусти Ghostly API на устройстве и повтори проверку.

" +
                            "Адрес: " + getString(R.string.server_url)
                    );
                    status.setTextColor(Color.rgb(255, 145, 145));
                    retry.setVisibility(View.VISIBLE);
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private TextView makeText(String value, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(null, bold
                ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }
}
