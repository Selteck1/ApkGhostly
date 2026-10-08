package com.ghostly.apk;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class GameActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        WebView view = new WebView(this);
        view.setBackgroundColor(Color.rgb(5,6,8));
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        view.setWebViewClient(new WebViewClient());
        view.loadUrl("file:///android_asset/game/index.html");
        setContentView(view);
    }

    @Override public void onBackPressed() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Выйти из игры?")
            .setMessage("Вернуться в меню Ghostly?")
            .setNegativeButton("Продолжить", null)
            .setPositiveButton("В меню", (d,w) -> finish())
            .show();
    }
}
