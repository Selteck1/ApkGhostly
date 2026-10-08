package com.ghostly.apk;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.*;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int STORAGE_REQUEST = 7001;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(17);
        root.setPadding(36, 50, 36, 50);
        root.setBackgroundColor(Color.rgb(8, 7, 12));

        TextView title = new TextView(this);
        title.setText("👻 GHOSTLY");
        title.setTextColor(Color.rgb(190, 125, 255));
        title.setTextSize(32);
        title.setGravity(17);
        title.setTypeface(null, 1);

        TextView desc = new TextView(this);
        desc.setText("Создание /sdcard/test.py с максимально возможными обычными файловыми правами 0777.");
        desc.setTextColor(Color.WHITE);
        desc.setTextSize(16);
        desc.setGravity(17);
        desc.setPadding(0, 28, 0, 24);

        Button create = new Button(this);
        create.setText("📄 Создать test.py");
        create.setTextSize(17);
        create.setAllCaps(false);

        status = new TextView(this);
        status.setText("Файл ещё не создан.");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(15);
        status.setGravity(17);
        status.setPadding(0, 24, 0, 0);

        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        root.addView(desc, new LinearLayout.LayoutParams(-1, -2));
        root.addView(create, new LinearLayout.LayoutParams(-1, -2));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        create.setOnClickListener(v -> createTestFile());
        setContentView(root);
    }

    private void createTestFile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent i = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                    );
                    startActivity(i);
                    status.setText("⚙️ Разреши «Доступ ко всем файлам» для Ghostly и снова нажми кнопку.");
                } catch (Exception e) {
                    status.setText("❌ Не удалось открыть настройки доступа к файлам.");
                }
                return;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_REQUEST
                );
                return;
            }
        }

        File file = new File(Environment.getExternalStorageDirectory(), "test.py");
        String content =
            "#!/usr/bin/env python3\n" +
            "# Ghostly test file\n" +
            "print('MAX LEVEL')\n";

        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new Exception("Не удалось открыть /sdcard");
            }

            try (FileOutputStream out = new FileOutputStream(file, false)) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            boolean chmodOk = false;
            try {
                android.system.Os.chmod(file.getAbsolutePath(), 0777);
                chmodOk = true;
            } catch (Throwable ignored) {
            }

            // Also request all conventional rwx bits through Java's File API.
            try { file.setReadable(true, false); } catch (Throwable ignored) {}
            try { file.setWritable(true, false); } catch (Throwable ignored) {}
            try { file.setExecutable(true, false); } catch (Throwable ignored) {}

            String mode = chmodOk ? "0777" : "максимальные доступные";
            status.setText(
                "✅ Файл создан:\n" +
                file.getAbsolutePath() +
                "\n\n🔐 Права: " + mode +
                "\n📦 Размер: " + file.length() + " байт"
            );
        } catch (Exception e) {
            status.setText(
                "❌ Не удалось создать /sdcard/test.py\n\n" +
                e.getClass().getSimpleName() + ": " + e.getMessage()
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == STORAGE_REQUEST) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                createTestFile();
            } else {
                status.setText("❌ Разрешение на запись отклонено.");
            }
        }
    }
}
