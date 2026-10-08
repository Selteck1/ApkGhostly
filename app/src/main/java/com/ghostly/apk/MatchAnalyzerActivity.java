package com.ghostly.apk;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatchAnalyzerActivity extends Activity {
    private static final int PICK_IMAGE = 901;
    private ImageView preview;
    private TextView status;
    private LinearLayout results;
    private final ArrayList<Player> players = new ArrayList<>();
    private TextRecognizer recognizer;

    static class Player {
        String name;
        int kills = -1, deaths = -1, assists = -1, score = -1;
        double kd = 0, rating = 0;
        Player(String n) { name = n; }
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        build();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView tv(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xfff5f0ff);
        t.setTextSize(size);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        if (bold) t.setTypeface(null, 1);
        return t;
    }

    private Button btn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(0xffffffff);
        b.setTextSize(15);
        return b;
    }

    private void build() {
        ScrollView sc = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(24));
        root.setBackgroundColor(0xff08070c);

        TextView title = tv("📊 GHOSTLY MATCH ANALYZER", 25, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = tv(
            "Загрузи скрин таблицы после матча. Ghostly распознает игроков и рассчитает собственный MVP-рейтинг.",
            15, false
        );
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        Button pick = btn("📸 Выбрать скриншот");
        root.addView(pick);
        pick.setOnClickListener(v -> pickImage());

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(preview, new LinearLayout.LayoutParams(-1, dp(260)));

        status = tv("Ожидаю скриншот…", 14, false);
        root.addView(status);

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        root.addView(results);

        Button back = btn("← Назад");
        root.addView(back);
        back.setOnClickListener(v -> finish());

        sc.addView(root);
        setContentView(sc);
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_IMAGE);
    }

    @Override protected void onActivityResult(int req, int code, Intent data) {
        super.onActivityResult(req, code, data);
        if (req != PICK_IMAGE || code != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            preview.setImageBitmap(BitmapFactory.decodeStream(getContentResolver().openInputStream(uri)));
            status.setText("🟣 Распознаю таблицу…");
            results.removeAllViews();
            InputImage image = InputImage.fromFilePath(this, uri);
            recognizer.process(image)
                .addOnSuccessListener(this::showAnalysis)
                .addOnFailureListener(e -> status.setText("❌ OCR не смог обработать изображение."));
        } catch (Exception e) {
            status.setText("❌ Не удалось открыть изображение.");
        }
    }

    private void showAnalysis(Text text) {
        players.clear();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                parseLine(line.getText(), seen);
            }
        }

        if (players.size() < 2) {
            status.setText("⚠️ Нашёл мало строк игроков. Нужен полный скрин таблицы матча.");
            TextView raw = tv("Распознанный текст:\n\n" + text.getText(), 13, false);
            raw.setBackgroundColor(0xff15111d);
            results.addView(raw);
            return;
        }

        calculateRatings();
        players.sort((a, b) -> Double.compare(b.rating, a.rating));

        Player mvp = players.get(0);
        status.setText("✅ Игроков: " + players.size() + " · Ghostly MVP: " + mvp.name);

        TextView head = tv(
            "🏆 GHOSTLY MVP\n" +
            mvp.name + "\n\n" +
            "Рейтинг: " + fmt(mvp.rating) + "\n" +
            "K/D: " + fmt(mvp.kd) + "\n" +
            "Почему: " + mvpReason(mvp),
            18, true
        );
        head.setBackgroundColor(0xff241630);
        results.addView(head);

        int place = 1;
        for (Player p : players) {
            String line =
                place + ". " + p.name + "\n" +
                "⚔️ Kills: " + num(p.kills) +
                "   💀 Deaths: " + num(p.deaths) +
                "   🤝 Assists: " + num(p.assists) + "\n" +
                "📈 K/D: " + fmt(p.kd) +
                "   ⭐ Ghostly Rating: " + fmt(p.rating) +
                (p.score >= 0 ? "\n🏅 Score: " + p.score : "") + "\n" +
                roleLabel(p);

            TextView card = tv(line, 15, place == 1);
            card.setBackgroundColor(place == 1 ? 0xff1f1830 : 0xff121017);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(7);
            results.addView(card, lp);
            place++;
        }
    }

    private void parseLine(String raw, Set<String> seen) {
        String s = raw.replaceAll("\\s+", " ").trim();
        if (s.length() < 3) return;

        Matcher m = Pattern.compile(
            "^(.*?)[ :|]+(\\d+)\\s+(\\d+)(?:\\s+(\\d+))?(?:\\s+(\\d+))?$"
        ).matcher(s);
        if (!m.find()) return;

        String name = m.group(1).replaceAll("^[#•·\\-]+", "").trim();
        if (name.length() < 2 || name.length() > 28) return;
        if (name.matches("(?i).*(kill|death|assist|score|round|player|команд|игрок|убий|смер|сч[её]т).*")) return;

        ArrayList<Integer> nums = new ArrayList<>();
        for (int i = 2; i <= 5; i++) {
            String g = m.group(i);
            if (g != null) nums.add(Integer.parseInt(g));
        }
        if (nums.size() < 2) return;
        if (seen.contains(name.toLowerCase(Locale.ROOT))) return;

        Player p = new Player(name);
        p.kills = nums.get(0);
        p.deaths = nums.get(1);
        if (nums.size() >= 3) p.assists = nums.get(2);
        if (nums.size() >= 4) p.score = nums.get(3);
        players.add(p);
        seen.add(name.toLowerCase(Locale.ROOT));
    }

    private void calculateRatings() {
        for (Player p : players) {
            p.kd = p.deaths > 0 ? ((double) Math.max(0, p.kills) / p.deaths) : (p.kills > 0 ? p.kills : 0);
            double k = Math.min(10, Math.max(0, p.kills)) * 4.0;
            double d = Math.min(10, Math.max(0, p.deaths)) * 1.6;
            double a = p.assists < 0 ? 0 : Math.min(10, p.assists) * 1.4;
            double bonus = p.score < 0 ? 0 : Math.min(100, p.score) * 0.08;
            p.rating = Math.max(0, Math.min(100, k - d + a + bonus));
        }
    }

    private String mvpReason(Player p) {
        ArrayList<String> r = new ArrayList<>();
        if (p.kills >= 0) r.add("высокий вклад по убийствам");
        if (p.kd >= 1.5) r.add("сильный K/D");
        if (p.assists >= 3) r.add("много ассистов");
        return r.isEmpty() ? "лучший распознанный Ghostly Rating" : String.join(", ", r);
    }

    private String roleLabel(Player p) {
        if (p.kd >= 2.0) return "🔥 Carry / Fragger";
        if (p.assists >= 4 && p.kd < 2.0) return "🤝 Support";
        if (p.deaths >= 0 && p.kills >= 0 && p.deaths > p.kills) return "🛡️ Entry / High risk";
        return "⚔️ Rifler";
    }

    private String num(int x) { return x < 0 ? "—" : String.valueOf(x); }
    private String fmt(double d) { return String.format(Locale.US, "%.2f", d); }

    @Override protected void onDestroy() {
        if (recognizer != null) recognizer.close();
        super.onDestroy();
    }
}
