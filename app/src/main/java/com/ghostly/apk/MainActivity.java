package com.ghostly.apk;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private final Handler handler = new Handler();
    private boolean introFinished = false;

    private final int bg = Color.rgb(7, 4, 13);
    private final int card = Color.rgb(18, 12, 29);
    private final int purple = Color.rgb(192, 130, 255);
    private final int soft = Color.rgb(224, 218, 235);

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

        video.setOnCompletionListener(mp -> showServerGate());
        video.setOnErrorListener((mp, what, extra) -> {
            showServerGate();
            return true;
        });

        video.start();
        handler.postDelayed(this::showServerGate, 15500);
    }

    private void showServerGate() {
        if (introFinished) {
            return;
        }
        introFinished = true;
        handler.removeCallbacksAndMessages(null);
        hideSystemUi();

        LinearLayout root = baseColumn();
        root.addView(makeText("👻 GHOSTLY", 34, true), matchWrap());

        TextView title = makeText("Standoff 2 • статистика", 27, true);
        title.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = 28;
        root.addView(title, titleParams);

        TextView status = makeText("🟣 Подключение к Ghostly API...", 19, false);
        status.setTextColor(soft);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = 24;
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
        status.setText("🟣 Подключение к Ghostly API...");
        status.setTextColor(soft);
        retry.setVisibility(View.GONE);

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(getString(R.string.server_url) + "/api/health");
                connection = open(url);
                int code = connection.getResponseCode();
                if (code != 200) {
                    throw new IllegalStateException("HTTP " + code);
                }

                JSONObject json = readJson(connection);
                boolean apiReady = json.optBoolean("standoff2_api", false);

                handler.post(() -> {
                    if (apiReady) {
                        showStatsScreen();
                    } else {
                        status.setText(
                                "✅ Ghostly API работает\n\n" +
                                "⚠️ API Standoff 2 ещё не настроен\n\n" +
                                "Добавь STANDOFF2_HANDSHAKE на сервере."
                        );
                        status.setTextColor(Color.rgb(255, 220, 150));
                        retry.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    status.setText(
                            "❌ Ghostly API недоступен\n\n" +
                            "Запусти сервер и повтори проверку.\n\n" +
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

    private void showStatsScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);

        LinearLayout root = baseColumn();
        root.setPadding(28, 34, 28, 40);

        TextView logo = makeText("👻 GHOSTLY", 30, true);
        logo.setTextColor(purple);
        root.addView(logo, matchWrap());

        TextView title = makeText("STANDOFF 2", 23, true);
        title.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = 8;
        root.addView(title, titleParams);

        TextView subtitle = makeText("Проверка статистики игрока по ID", 16, false);
        subtitle.setTextColor(soft);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = 6;
        root.addView(subtitle, subtitleParams);

        EditText idInput = new EditText(this);
        idInput.setHint("Standoff 2 ID");
        idInput.setHintTextColor(Color.rgb(140, 130, 155));
        idInput.setTextColor(Color.WHITE);
        idInput.setTextSize(18);
        idInput.setSingleLine(true);
        idInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        idInput.setPadding(26, 18, 26, 18);
        idInput.setBackground(roundBg(card, 22));
        LinearLayout.LayoutParams inputParams = matchWrap();
        inputParams.topMargin = 24;
        root.addView(idInput, inputParams);

        Button check = new Button(this);
        check.setText("🔎 Проверить игрока");
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        checkParams.topMargin = 14;
        root.addView(check, checkParams);

        TextView state = makeText("Введите ID игрока", 16, false);
        state.setTextColor(soft);
        LinearLayout.LayoutParams stateParams = matchWrap();
        stateParams.topMargin = 16;
        root.addView(state, stateParams);

        LinearLayout resultCard = new LinearLayout(this);
        resultCard.setOrientation(LinearLayout.VERTICAL);
        resultCard.setPadding(22, 22, 22, 22);
        resultCard.setBackground(roundBg(card, 22));
        resultCard.setVisibility(View.GONE);
        LinearLayout.LayoutParams resultParams = matchWrap();
        resultParams.topMargin = 18;
        root.addView(resultCard, resultParams);

        check.setOnClickListener(v -> {
            String id = idInput.getText().toString().trim();
            if (!id.matches("\\d{3,20}")) {
                state.setText("❌ ID должен содержать только цифры.");
                state.setTextColor(Color.rgb(255, 145, 145));
                resultCard.setVisibility(View.GONE);
                return;
            }

            check.setEnabled(false);
            state.setText("🟣 Получаем данные игрока...");
            state.setTextColor(soft);
            resultCard.setVisibility(View.GONE);

            new Thread(() -> loadPlayer(
                    id,
                    state,
                    resultCard,
                    check
            )).start();
        });

        scroll.addView(root);
        setContentView(scroll);
    }

    private void loadPlayer(
            String playerId,
            TextView state,
            LinearLayout resultCard,
            Button check
    ) {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(
                    getString(R.string.server_url) + "/api/player/" + playerId
            );
            connection = open(url);

            int code = connection.getResponseCode();
            JSONObject json = readJson(connection);

            if (code != 200 || !json.optBoolean("ok", false)) {
                String error = json.optString("error", "Неизвестная ошибка");
                throw new IllegalStateException(error);
            }

            JSONObject data = json.getJSONObject("data");

            handler.post(() -> {
                state.setText("✅ Данные получены");
                state.setTextColor(Color.rgb(180, 255, 195));
                renderPlayer(resultCard, data);
                check.setEnabled(true);
            });
        } catch (Exception e) {
            handler.post(() -> {
                state.setText("❌ " + e.getMessage());
                state.setTextColor(Color.rgb(255, 145, 145));
                check.setEnabled(true);
            });
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void renderPlayer(LinearLayout cardView, JSONObject data) {
        cardView.removeAllViews();
        cardView.setVisibility(View.VISIBLE);

        String nickname = value(data, "nickname");
        String id = value(data, "id");
        String level = value(data, "level");
        String rating = value(data, "rating");
        String matches = value(data, "matches");
        String wins = value(data, "wins");
        String winRate = value(data, "win_rate");
        String kills = value(data, "kills");
        String deaths = value(data, "deaths");
        String assists = value(data, "assists");
        String kd = value(data, "kd");
        String accuracy = value(data, "accuracy");

        TextView name = makeText(
                nickname.equals("—") ? "Игрок Standoff 2" : nickname,
                25,
                true
        );
        name.setTextColor(Color.WHITE);
        cardView.addView(name, matchWrap());

        addStat(cardView, "🆔 ID", id);
        addStat(cardView, "⭐ Уровень", level);
        addStat(cardView, "🏆 Рейтинг", rating);
        addStat(cardView, "🎮 Матчи", matches);
        addStat(cardView, "🥇 Победы", wins);
        addStat(cardView, "📈 Winrate", percent(winRate));
        addStat(cardView, "☠️ Убийства", kills);
        addStat(cardView, "💀 Смерти", deaths);
        addStat(cardView, "🤝 Ассисты", assists);
        addStat(cardView, "⚔️ K/D", kd);
        addStat(cardView, "🎯 Точность", percent(accuracy));

        TextView source = makeText(
                "Ghostly API • Standoff 2 RPC",
                13,
                false
        );
        source.setTextColor(Color.rgb(150, 135, 170));
        LinearLayout.LayoutParams sourceParams = matchWrap();
        sourceParams.topMargin = 18;
        cardView.addView(source, sourceParams);
    }

    private void addStat(LinearLayout root, String label, String value) {
        TextView row = new TextView(this);
        row.setText(label + ": " + value);
        row.setTextSize(17);
        row.setTextColor(soft);
        row.setPadding(0, 9, 0, 9);
        root.addView(row, matchWrap());
    }

    private String value(JSONObject object, String key) {
        if (object.isNull(key)) {
            return "—";
        }
        String value = object.optString(key, "—");
        return value == null || value.isEmpty() || value.equals("null") ? "—" : value;
    }

    private String percent(String value) {
        if (value.equals("—")) {
            return value;
        }
        return value.endsWith("%") ? value : value + "%";
    }

    private HttpURLConnection open(URL url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setUseCaches(false);
        return connection;
    }

    private JSONObject readJson(HttpURLConnection connection) throws Exception {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream())
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return new JSONObject(body.toString());
    }

    private LinearLayout baseColumn() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(34, 48, 34, 48);
        root.setBackgroundColor(bg);
        return root;
    }

    private TextView makeText(String value, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private GradientDrawable roundBg(int color, int radiusDp) {
        float density = getResources().getDisplayMetrics().density;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp * density);
        drawable.setStroke(Math.max(1, (int) density), Color.rgb(55, 38, 76));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }
}
