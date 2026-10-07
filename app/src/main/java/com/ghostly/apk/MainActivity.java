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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

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

        TextView status = makeText(
                "✅ Сервер подключён\n\n" +
                "✅ Соединение установлено\n\n" +
                "✅ Система готова к работе\n\n" +
                "🟣 Ghostly APK • тестовая версия",
                19,
                false
        );
        status.setTextColor(Color.rgb(224, 218, 235));
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = 24;
        root.addView(status, statusParams);

        setContentView(root);
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
