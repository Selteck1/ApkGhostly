package com.ghostly.apk;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
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
    private int imageWidth = 1;
    private int imageHeight = 1;

    static class Player {
        String name;
        String team;
        int kills = -1;     // У
        int assists = -1;   // П
        int deaths = -1;    // С
        int score = -1;
        int ping = -1;
        double kd = 0;
        double rating = 0;

        Player(String n, String t) {
            name = n;
            team = t;
        }
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        build();
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView tv(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xfff5f0ff);
        t.setTextSize(size);
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
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
        root.setPadding(dp(14), dp(18), dp(14), dp(24));
        root.setBackgroundColor(0xff08070c);

        TextView title = tv("📊 GHOSTLY MATCH ANALYZER", 25, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = tv(
            "Загрузи итоговый скрин матча Standoff 2. Ghostly разберёт обе команды и каждого игрока отдельно.",
            15, false
        );
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        Button pick = btn("📸 Загрузить скрин результата");
        root.addView(pick);
        pick.setOnClickListener(v -> pickImage());

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(250));
        pp.topMargin = dp(8);
        root.addView(preview, pp);

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
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
            if (bitmap == null) throw new Exception("bitmap=null");

            imageWidth = bitmap.getWidth();
            imageHeight = bitmap.getHeight();
            preview.setImageBitmap(bitmap);
            status.setText("🟣 Распознаю таблицу игроков…");
            results.removeAllViews();

            InputImage image = InputImage.fromBitmap(bitmap, 0);
            recognizer.process(image)
                .addOnSuccessListener(this::showAnalysis)
                .addOnFailureListener(e ->
                    status.setText("❌ Не удалось распознать таблицу.")
                );
        } catch (Exception e) {
            status.setText("❌ Не удалось открыть изображение.");
        }
    }

    private void showAnalysis(Text text) {
        players.clear();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect box = line.getBoundingBox();
                parsePlayerLine(line.getText(), box, seen);
            }
        }

        if (players.size() < 6) {
            status.setText("⚠️ Распознано только " + players.size() +
                " игроков. Нужен полный скрин таблицы 5×5.");
            TextView raw = tv(
                "Распознанный текст для проверки:\n\n" + text.getText(),
                13, false
            );
            raw.setBackgroundColor(0xff15111d);
            results.addView(raw);
            return;
        }

        calculateRatings();
        players.sort((a, b) -> Double.compare(b.rating, a.rating));

        Player mvp = bestByRating();
        Player fragger = bestBy(players, "kills");
        Player support = bestBy(players, "assists");
        Player kdBest = bestBy(players, "kd");
        Player pingBest = bestLowestPing(players);

        String matchInfo = detectScore(text.getText());
        String scoreText = matchInfo.isEmpty() ? "Счёт: не удалось определить" : "Счёт: " + matchInfo;

        status.setText("✅ Распознано игроков: " + players.size());

        TextView summary = tv(
            "🏆 GHOSTLY MVP\n" +
            mvp.name + " · " + mvp.team + "\n" +
            "⭐ Ghostly Rating: " + fmt(mvp.rating) + "\n" +
            "📊 " + scoreLine(mvp) + "\n" +
            "💡 " + mvpReason(mvp) + "\n\n" +
            "🏁 " + scoreText + "\n" +
            "🔥 Лучший фрагер: " + fragger.name + " · " + num(fragger.kills) + " У\n" +
            "🤝 Лучший помощник: " + support.name + " · " + num(support.assists) + " П\n" +
            "⚔️ Лучший K/D: " + kdBest.name + " · " + fmt(kdBest.kd) + "\n" +
            "📶 Лучший пинг: " + pingBest.name + " · " + num(pingBest.ping) + " ms",
            17, true
        );
        summary.setBackgroundColor(0xff241630);
        results.addView(summary);

        addTeamSection("🔵 КОМАНДА 1", filteredTeam(players, true));
        addTeamSection("🔴 КОМАНДА 2", filteredTeam(players, false));
    }

    private void addTeamSection(String title, ArrayList<Player> teamPlayers) {
        if (teamPlayers.isEmpty()) return;

        TextView header = tv(title + " · Игроков: " + teamPlayers.size(), 19, true);
        header.setBackgroundColor(0xff15111d);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(10);
        results.addView(header, hp);

        teamPlayers.sort((a, b) -> Double.compare(b.rating, a.rating));
        int place = 1;

        for (Player p : teamPlayers) {
            String cardText =
                place + ". " + p.name + "\n" +
                "⚔️ Убийства: " + num(p.kills) +
                "   🤝 Помощь: " + num(p.assists) +
                "   💀 Смерти: " + num(p.deaths) + "\n" +
                "📈 K/D: " + fmt(p.kd) +
                "   🏅 Счёт: " + num(p.score) +
                "   📶 Пинг: " + num(p.ping) + " ms\n" +
                "⭐ Ghostly Rating: " + fmt(p.rating) + "\n" +
                roleLabel(p);

            TextView card = tv(cardText, 15, place == 1);
            card.setBackgroundColor(place == 1 ? 0xff1f1830 : 0xff121017);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(6);
            results.addView(card, lp);
            place++;
        }
    }

    private ArrayList<Player> filteredTeam(ArrayList<Player> src, boolean left) {
        ArrayList<Player> out = new ArrayList<>();
        for (Player p : src) {
            boolean isLeft = "Оборона".equals(p.team);
            if (isLeft == left) out.add(p);
        }
        // If OCR failed to identify a team, split the result by original ordering.
        if (out.isEmpty()) {
            for (int i = left ? 0 : src.size() / 2; i < (left ? src.size() / 2 : src.size()); i++) {
                out.add(src.get(i));
            }
        }
        return out;
    }

    private void parsePlayerLine(String raw, Rect box, Set<String> seen) {
        if (raw == null || box == null) return;
        String s = raw.replaceAll("\\s+", " ").trim();
        if (s.length() < 5) return;

        ArrayList<Matcher> matches = new ArrayList<>();
        Matcher numbers = Pattern.compile("\\d+").matcher(s);
        while (numbers.find()) matches.add(numbers);

        if (matches.size() < 5) return;

        Matcher firstStat = matches.get(matches.size() - 5);
        int[] stat = new int[5];
        for (int i = 0; i < 5; i++) {
            stat[i] = Integer.parseInt(matches.get(matches.size() - 5 + i).group());
        }

        String name = s.substring(0, firstStat.start()).trim();
        name = name.replaceFirst("^\\d+\\s+", "");
        name = name.replaceAll("^[#•·\\-]+", "").trim();
        name = name.replaceAll("\\s{2,}", " ");

        if (name.length() < 2 || name.length() > 35) return;

        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("имя") || lower.contains("ранг") ||
            lower.contains("счет") || lower.contains("пинг") ||
            lower.contains("статистика") || lower.contains("атака") ||
            lower.contains("оборона")) return;

        String team = box.centerX() < imageWidth / 2 ? "Оборона" : "Атака";
        String key = lower + "|" + team;
        if (seen.contains(key)) return;

        Player p = new Player(name, team);
        p.kills = stat[0];
        p.assists = stat[1];
        p.deaths = stat[2];
        p.score = stat[3];
        p.ping = stat[4];
        players.add(p);
        seen.add(key);
    }

    private void calculateRatings() {
        for (Player p : players) {
            p.kd = p.deaths > 0 ? ((double)p.kills / p.deaths) : (p.kills > 0 ? p.kills : 0);

            // Score is the strongest signal because it already reflects
            // the player's contribution in the match table. Raw combat stats
            // refine the Ghostly rating rather than replacing the score.
            double scorePart = Math.max(0, p.score) * 0.68;
            double killPart = Math.max(0, p.kills) * 1.10;
            double assistPart = Math.max(0, p.assists) * 0.35;
            double deathPenalty = Math.max(0, p.deaths) * 0.55;
            double pingPenalty = p.ping > 120 ? Math.min(8, (p.ping - 120) / 80.0) : 0;

            p.rating = Math.max(0, Math.min(100,
                scorePart + killPart + assistPart - deathPenalty - pingPenalty
            ));
        }
    }

    private Player bestByRating() {
        Player best = players.get(0);
        for (Player p : players) if (p.rating > best.rating) best = p;
        return best;
    }

    private Player bestBy(ArrayList<Player> list, String metric) {
        Player best = list.get(0);
        for (Player p : list) {
            double a = metric.equals("kills") ? p.kills :
                       metric.equals("assists") ? p.assists : p.kd;
            double b = metric.equals("kills") ? best.kills :
                       metric.equals("assists") ? best.assists : best.kd;
            if (a > b) best = p;
        }
        return best;
    }

    private Player bestLowestPing(ArrayList<Player> list) {
        Player best = null;
        for (Player p : list) {
            if (p.ping < 0) continue;
            if (best == null || p.ping < best.ping) best = p;
        }
        return best == null ? list.get(0) : best;
    }

    private String detectScore(String allText) {
        if (allText == null) return "";
        Matcher m = Pattern.compile("(?m)(?:^|\\s)(\\d{1,2})\\s*(?:[:\\-]|\\s)\\s*(\\d{1,2})(?:\\s|$)").matcher(allText);
        String found = "";
        while (m.find()) {
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(2));
            if (a <= 25 && b <= 25 && (a != b || a == 0)) found = a + ":" + b;
        }
        return found;
    }

    private String scoreLine(Player p) {
        return "У " + num(p.kills) +
               " · П " + num(p.assists) +
               " · С " + num(p.deaths) +
               " · Счёт " + num(p.score) +
               " · Пинг " + num(p.ping);
    }

    private String mvpReason(Player p) {
        ArrayList<String> r = new ArrayList<>();
        r.add("высокий игровой счёт");
        if (p.kills >= 15) r.add("много убийств");
        if (p.kd >= 2.0) r.add("сильный K/D");
        if (p.assists >= 5) r.add("высокий вклад через помощь");
        return String.join(", ", r);
    }

    private String roleLabel(Player p) {
        if (p.kd >= 2.0 && p.kills >= 15) return "🔥 Carry / Fragger";
        if (p.assists >= 7) return "🤝 Support";
        if (p.kd >= 1.5) return "⚔️ Rifler / Fragger";
        if (p.ping >= 120) return "📶 High ping";
        return "🎮 Stable player";
    }

    private String num(int x) {
        return x < 0 ? "—" : String.valueOf(x);
    }

    private String fmt(double d) {
        return String.format(Locale.US, "%.2f", d);
    }

    @Override protected void onDestroy() {
        if (recognizer != null) recognizer.close();
        super.onDestroy();
    }
}
