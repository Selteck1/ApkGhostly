package com.ghostly.apk;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.*;
import android.graphics.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int STORAGE_REQUEST = 7001;
    private TextView status;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(17);
        root.setPadding(40, 50, 40, 50);
        root.setBackgroundColor(Color.rgb(8, 7, 12));

        TextView title = new TextView(this);
        title.setText("👻 GHOSTLY");
        title.setTextColor(Color.rgb(190, 125, 255));
        title.setTextSize(32);
        title.setGravity(17);
        title.setTypeface(null, 1);

        TextView desc = new TextView(this);
        desc.setText("Создание локального файла в памяти телефона");
        desc.setTextColor(Color.WHITE);
        desc.setTextSize(17);
        desc.setGravity(17);
        desc.setPadding(0, 30, 0, 25);

        Button create = new Button(this);
        create.setText("📄 Создать MAX LEVEL файл");
        create.setTextSize(17);
        create.setAllCaps(false);

        status = new TextView(this);
        status.setText("Файл ещё не создан.");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(15);
        status.setGravity(17);
        status.setPadding(0, 25, 0, 0);

        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        root.addView(desc, new LinearLayout.LayoutParams(-1, -2));
        root.addView(create, new LinearLayout.LayoutParams(-1, -2));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        create.setOnClickListener(v -> createMaxLevelFile());

        setContentView(root);
    }

    private void createMaxLevelFile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                STORAGE_REQUEST
            );
            return;
        }

        final String fileName = "max_level.json";
        final String body =
            "{\n" +
            "  \"level\": \"MAX\",\n" +
            "  \"boost\": true\n" +
            "}\n";

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                values.put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/GhostlyBoost"
                );
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                ContentResolver resolver = getContentResolver();
                android.net.Uri uri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                );

                if (uri == null) {
                    status.setText("❌ Не удалось создать файл.");
                    return;
                }

                try (OutputStream out = resolver.openOutputStream(uri)) {
                    if (out == null) throw new Exception("output stream unavailable");
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }

                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(uri, done, null, null);

                status.setText("✅ Создано:\nDownload/GhostlyBoost/" + fileName);
            } else {
                File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "GhostlyBoost"
                );
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new Exception("Не удалось создать папку");
                }

                File file = new File(dir, fileName);
                try (FileOutputStream out = new FileOutputStream(file, false)) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }

                status.setText("✅ Создано:\n" + file.getAbsolutePath());
            }
        } catch (Exception e) {
            status.setText("❌ Ошибка: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == STORAGE_REQUEST &&
            results.length > 0 &&
            results[0] == PackageManager.PERMISSION_GRANTED) {
            createMaxLevelFile();
        } else if (requestCode == STORAGE_REQUEST) {
            status.setText("❌ Разрешение на запись отклонено.");
        }
    }
}
