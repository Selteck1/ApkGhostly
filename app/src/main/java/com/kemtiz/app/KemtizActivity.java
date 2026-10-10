package com.kemtiz.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class KemtizActivity extends Activity {
    private static final String DEFAULT_API = "https://kemtiz-api.onrender.com";
    private static final String SERVER_PREF = "server_base";
    private static final String NOTIFICATION_CHANNEL = "kemtiz_activity";
    private static final int CALL_NOTIFICATION_ID = 27182;
    private static final int BG = Color.rgb(10, 11, 17);
    private static final int SURFACE = Color.rgb(21, 22, 32);
    private static final int PANEL = Color.rgb(31, 30, 45);
    private static final int PURPLE = Color.rgb(112, 78, 205);
    private static final int ACCENT = Color.rgb(171, 143, 255);
    private static final int WHITE = Color.rgb(246, 243, 252);
    private static final int MUTED = Color.rgb(157, 153, 176);
    private static final int GREEN = Color.rgb(88, 216, 161);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient http = new OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS).pingInterval(25, TimeUnit.SECONDS).build();
    private SharedPreferences prefs;
    private String serverBase = DEFAULT_API;
    private EditText serverUrlField;
    private String token = "";
    private JSONObject me;
    private JSONObject currentChat;
    private long chatId = -1;
    private String screen = "chats";
    private LinearLayout root, page;
    private ScrollView scroll;
    private WebSocket socket;
    private boolean activityVisible = false;
    private EditText chatMessageInput;
    private Button chatMessageSend;

    private interface Result { void done(Object data, String error); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        createNotificationChannel();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("kemtiz", MODE_PRIVATE);
        serverBase = normalizeServerBase(prefs.getString(SERVER_PREF, DEFAULT_API));
        if (serverBase.isEmpty()) serverBase = DEFAULT_API;
        token = prefs.getString("token", "");
        if (token.isEmpty()) { login(""); return; }
        api("GET", "/api/me", null, (data, error) -> {
            if (error != null || !(data instanceof JSONObject)) {
                clearSession();
                login(error == null ? "" : error);
            } else {
                me = (JSONObject) data;
                shell();
                socket();
            }
        });
    }

    // Native username/password authentication.
    private EditText authUsername;
    private EditText authPassword;
    private EditText authConfirm;
    private TextView authError;
    private Button authSubmit;
    private boolean registrationMode = false;

    private void login(String warning) {
        showAuth(false, warning);
    }

    private void showAuth(boolean register, String warning) {
        registrationMode = register;
        closeSocket();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(bg(BG, 0));

        ScrollView authScroll = new ScrollView(this);
        authScroll.setFillViewport(true);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(authScroll, scrollLp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(23), dp(24), dp(23), dp(25));
        authScroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        TextView logo = text("K", 30, WHITE, Gravity.CENTER);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setBackground(bg(PURPLE, 22));
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(66), dp(66));
        logoLp.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(logo, logoLp);

        TextView brand = text("KEMTIZ", 27, WHITE, Gravity.CENTER);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setLetterSpacing(.12f);
        LinearLayout.LayoutParams brandLp = wrap();
        brandLp.gravity = Gravity.CENTER_HORIZONTAL;
        brandLp.topMargin = dp(11);
        content.addView(brand, brandLp);

        TextView tagline = text("ТВОЙ КРУГ. ТВОИ РАЗГОВОРЫ.", 10, ACCENT, Gravity.CENTER);
        tagline.setLetterSpacing(.10f);
        LinearLayout.LayoutParams tagLp = wrap();
        tagLp.gravity = Gravity.CENTER_HORIZONTAL;
        tagLp.topMargin = dp(5);
        content.addView(tagline, tagLp);

        TextView heading = text(register ? "Создай свой аккаунт" : "С возвращением", 25, WHITE, Gravity.START);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams headingLp = match();
        headingLp.topMargin = dp(27);
        content.addView(heading, headingLp);
        TextView description = text(
            register ? "Зарегистрируйся и начни общаться." : "Войди, чтобы продолжить общение.",
            13, MUTED, Gravity.START);
        content.addView(description, topMargin(match(), 5));

        LinearLayout form = card();
        LinearLayout.LayoutParams formLp = match();
        formLp.topMargin = dp(20);
        content.addView(form, formLp);

        LinearLayout modeTabs = new LinearLayout(this);
        modeTabs.setOrientation(LinearLayout.HORIZONTAL);
        modeTabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        modeTabs.setBackground(bg(BG, 13));
        form.addView(modeTabs, match());

        Button loginTab = button("Войти", !register);
        Button registerTab = button("Регистрация", register);
        modeTabs.addView(loginTab, new LinearLayout.LayoutParams(0, dp(45), 1f));
        LinearLayout.LayoutParams regTabLp = new LinearLayout.LayoutParams(0, dp(45), 1f);
        regTabLp.leftMargin = dp(5);
        modeTabs.addView(registerTab, regTabLp);
        loginTab.setOnClickListener(v -> showAuth(false, ""));
        registerTab.setOnClickListener(v -> showAuth(true, ""));

        TextView loginLabel = text("ЛОГИН", 10, ACCENT, Gravity.START);
        loginLabel.setTypeface(Typeface.DEFAULT_BOLD);
        form.addView(loginLabel, topMargin(match(), 17));
        authUsername = field("Придумай логин");
        authUsername.setSingleLine(true);
        authUsername.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        authUsername.setAutofillHints("username");
        form.addView(authUsername, topMargin(match(), 7));

        TextView passwordLabel = text("ПАРОЛЬ", 10, ACCENT, Gravity.START);
        passwordLabel.setTypeface(Typeface.DEFAULT_BOLD);
        form.addView(passwordLabel, topMargin(match(), 14));
        authPassword = field("Введи пароль");
        authPassword.setSingleLine(true);
        authPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        authPassword.setAutofillHints("password");
        form.addView(authPassword, topMargin(match(), 7));

        if (register) {
            TextView confirmLabel = text("ПОВТОРИ ПАРОЛЬ", 10, ACCENT, Gravity.START);
            confirmLabel.setTypeface(Typeface.DEFAULT_BOLD);
            form.addView(confirmLabel, topMargin(match(), 14));
            authConfirm = field("Повтори пароль ещё раз");
            authConfirm.setSingleLine(true);
            authConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            form.addView(authConfirm, topMargin(match(), 7));
        } else {
            authConfirm = null;
        }

        TextView help = text(
            register ? "Логин: 3–24 символа, латиница, цифры и _. Пароль — минимум 8 символов."
                     : "Используй логин и пароль, указанные при регистрации.",
            11, MUTED, Gravity.START);
        form.addView(help, topMargin(match(), 12));

        authError = text(warning == null ? "" : warning, 12,
            Color.rgb(255, 130, 157), Gravity.START);
        authError.setVisibility(warning == null || warning.trim().isEmpty() ? View.GONE : View.VISIBLE);
        form.addView(authError, topMargin(match(), 8));

        authSubmit = button(register ? "Создать аккаунт" : "Войти в Kemtiz", true);
        LinearLayout.LayoutParams submitLp = match();
        submitLp.topMargin = dp(17);
        form.addView(authSubmit, submitLp);
        authSubmit.setOnClickListener(v -> {
            if (registrationMode) registerWithPassword();
            else loginWithPassword();
        });

        TextView footer = text("В домашней Wi-Fi-сети можно подключаться напрямую к ПК.", 11, MUTED, Gravity.CENTER);
        content.addView(footer, topMargin(match(), 17));

        TextView serverHeading = text("СВОЙ СЕРВЕР", 10, ACCENT, Gravity.START);
        serverHeading.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(serverHeading, topMargin(match(), 22));
        content.addView(text(
            "В одной Wi-Fi-сети используй IP компьютера. Для друзей из интернета запусти start_kemtiz_public.bat и вставь выданный HTTPS-адрес.",
            11, MUTED, Gravity.START), topMargin(match(), 5));
        serverUrlField = field("http://192.168.1.100:8000");
        serverUrlField.setSingleLine(true);
        serverUrlField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serverUrlField.setText(serverBase);
        content.addView(serverUrlField, topMargin(match(), 7));
        Button saveServer = button("Сохранить и проверить сервер", false);
        content.addView(saveServer, topMargin(match(), 7));
        saveServer.setOnClickListener(v -> saveAndCheckServer());

        setContentView(root);
    }

    private void loginWithPassword() {
        String username = authUsername.getText().toString().trim().replace("@", "").toLowerCase(Locale.ROOT);
        String password = authPassword.getText().toString();
        if (!username.matches("[a-z0-9][a-z0-9_]{2,23}")) {
            authUsername.setError("Логин: латиница, цифры и _, от 3 до 24 символов");
            return;
        }
        if (password.isEmpty()) {
            authPassword.setError("Введи пароль");
            return;
        }
        hideKeyboard(authPassword);
        authSubmit.setEnabled(false);
        authSubmit.setText("Проверяем…");
        api("POST", "/api/auth/password/login", obj("username", username, "password", password), (data, error) -> {
            authSubmit.setEnabled(true);
            authSubmit.setText("Войти в Kemtiz");
            if (error != null || !(data instanceof JSONObject)) {
                authError.setText(error == null ? "Не удалось войти. Попробуй ещё раз." : error);
                authError.setVisibility(View.VISIBLE);
            } else {
                accept((JSONObject) data);
            }
        });
    }

    private void registerWithPassword() {
        String username = authUsername.getText().toString().trim().replace("@", "").toLowerCase(Locale.ROOT);
        String password = authPassword.getText().toString();
        String confirm = authConfirm == null ? "" : authConfirm.getText().toString();

        if (!username.matches("[a-z0-9][a-z0-9_]{2,23}")) {
            authUsername.setError("Логин: латиница, цифры и _, от 3 до 24 символов");
            return;
        }
        if (password.length() < 8) {
            authPassword.setError("Пароль должен содержать минимум 8 символов");
            return;
        }
        if (password.length() > 128) {
            authPassword.setError("Пароль не должен быть длиннее 128 символов");
            return;
        }
        if (!password.equals(confirm)) {
            authConfirm.setError("Пароли не совпадают");
            return;
        }

        hideKeyboard(authConfirm);
        authSubmit.setEnabled(false);
        authSubmit.setText("Создаём аккаунт…");
        api("POST", "/api/auth/password/register",
            obj("username", username, "password", password, "display_name", username),
            (data, error) -> {
                authSubmit.setEnabled(true);
                authSubmit.setText("Создать аккаунт");
                if (error != null || !(data instanceof JSONObject)) {
                    authError.setText(error == null ? "Не удалось создать аккаунт." : error);
                    authError.setVisibility(View.VISIBLE);
                } else {
                    accept((JSONObject) data);
                }
            });
    }

    private void accept(JSONObject result) {
        token = result.optString("token", "");
        me = result.optJSONObject("user");
        if (token.isEmpty() || me == null) {
            clearSession(); login("Сервер не вернул данные аккаунта."); return;
        }
        prefs.edit().putString("token", token).apply();
        screen = "chats";
        shell();
        socket();
    }

    private void clearSession() {
        token = "";
        me = null;
        prefs.edit().remove("token").apply();
        closeSocket();
    }

    // Native navigation shell and screens
    private void shell() {
        if ("chat".equals(screen)) {
            chatShell();
            return;
        }
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(bg(BG, 0));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(17), dp(13), dp(17), dp(13));
        TextView logo = text("K", 19, WHITE, Gravity.CENTER);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setBackground(bg(PURPLE, 14));
        header.addView(logo, new LinearLayout.LayoutParams(dp(43), dp(43)));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, -2, 1);
        brandLp.leftMargin = dp(10);
        header.addView(brand, brandLp);
        TextView name = text("KEMTIZ", 17, WHITE, Gravity.START);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        brand.addView(name);
        brand.addView(text("ТВОЙ КРУГ. ТВОИ РАЗГОВОРЫ.", 9, MUTED, Gravity.START));
        TextView avatar = text(initial(me == null ? "K" : me.optString("display_name", "K")), 16, WHITE, Gravity.CENTER);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setBackground(bg(PANEL, 18));
        header.addView(avatar, new LinearLayout.LayoutParams(dp(42), dp(42)));
        avatar.setOnClickListener(v -> navigate("profile"));
        root.addView(header, match());
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(39, 37, 54));
        root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(17), dp(16), dp(20));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(7), dp(7), dp(7), dp(7));
        nav.setBackground(bg(SURFACE, 20));
        String[][] items = {{"chats","▰","Чаты"},{"friends","♧","Друзья"},{"requests","↗","Заявки"},{"search","⌕","Найти"},{"profile","●","Профиль"}};
        for (String[] item : items) {
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(2), dp(5), dp(2), dp(5));
            boolean selected = item[0].equals(screen) || ("chat".equals(screen) && "chats".equals(item[0]));
            int c = selected ? ACCENT : MUTED;
            tab.setBackground(bg(selected ? Color.rgb(52, 42, 78) : Color.TRANSPARENT, 14));
            tab.setContentDescription(item[2]);
            tab.addView(text(item[1], 20, c, Gravity.CENTER));
            tab.addView(text(item[2], 10, selected ? WHITE : MUTED, Gravity.CENTER), topMargin(wrap(), 2));
            tab.setOnClickListener(v -> navigate(item[0]));
            nav.addView(tab, new LinearLayout.LayoutParams(0, dp(54), 1));
        }
        LinearLayout.LayoutParams navLp = match();
        navLp.leftMargin = dp(10);
        navLp.rightMargin = dp(10);
        navLp.bottomMargin = dp(8);
        navLp.topMargin = dp(5);
        root.addView(nav, navLp);
        setContentView(root);
        requestNotificationPermission();
        render();
    }

    private void chatShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(bg(BG, 0));
        root.addView(buildChatHeader(), match());

        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(13), dp(13), dp(13), dp(18));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout composerShell = new LinearLayout(this);
        composerShell.setOrientation(LinearLayout.HORIZONTAL);
        composerShell.setGravity(Gravity.CENTER_VERTICAL);
        composerShell.setPadding(dp(10), dp(8), dp(10), dp(10));
        composerShell.setBackground(bg(SURFACE, 19));

        chatMessageInput = field("Сообщение…");
        chatMessageInput.setSingleLine(false);
        chatMessageInput.setMinHeight(dp(48));
        chatMessageInput.setMaxLines(4);
        chatMessageInput.setPadding(dp(15), dp(10), dp(15), dp(10));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, -2, 1f);
        inputLp.rightMargin = dp(8);
        composerShell.addView(chatMessageInput, inputLp);

        chatMessageSend = button("➤", true);
        chatMessageSend.setTextSize(18);
        composerShell.addView(chatMessageSend, new LinearLayout.LayoutParams(dp(52), dp(49)));
        chatMessageSend.setOnClickListener(v -> sendChatMessage());

        LinearLayout.LayoutParams composerLp = match();
        composerLp.leftMargin = dp(10);
        composerLp.rightMargin = dp(10);
        composerLp.topMargin = dp(5);
        composerLp.bottomMargin = dp(7);
        root.addView(composerShell, composerLp);
        setContentView(root);
        requestNotificationPermission();
        chat();
    }

    private void sendChatMessage() {
        if (chatMessageInput == null || chatMessageSend == null) return;
        String body = chatMessageInput.getText().toString().trim();
        if (body.isEmpty()) return;
        long id = chatId;
        chatMessageSend.setEnabled(false);
        api("POST", "/api/chats/" + id + "/messages", obj("body", body), (sent, error) -> {
            if (chatMessageSend != null) chatMessageSend.setEnabled(true);
            if (error != null) {
                toast(error);
            } else {
                if (chatMessageInput != null) chatMessageInput.setText("");
                chat();
            }
        });
    }

    private void navigate(String where) {
        screen = where;
        if (!"chat".equals(where)) { currentChat = null; chatId = -1; }
        shell();
    }

    private void render() {
        page.removeAllViews();
        switch (screen) {
            case "friends": friends(); break;
            case "requests": requests(); break;
            case "search": search(); break;
            case "profile": profile(); break;
            case "chat": chat(); break;
            default: chats();
        }
    }

    private void heading(String title, String subtitle) {
        TextView h = text(title, 25, WHITE, Gravity.START);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        page.addView(h);
        page.addView(text(subtitle, 13, MUTED, Gravity.START), topMargin(match(), 4));
        View spacer = new View(this);
        page.addView(spacer, new LinearLayout.LayoutParams(1, dp(12)));
    }

    private void chats() {
        heading("Сообщения", "Твои люди и разговоры в одном месте.");
        api("GET", "/api/chats", null, (data, error) -> {
            if (!"chats".equals(screen)) return;
            page.removeAllViews(); heading("Сообщения", "Твои люди и разговоры в одном месте.");
            if (error != null) { empty("Не удалось загрузить чаты", error, "Повторить", this::chats); return; }
            JSONArray list = data instanceof JSONArray ? (JSONArray)data : new JSONArray();
            if (list.length() == 0) { empty("Здесь пока тихо", "Добавь друзей, чтобы начать переписку.", "Найти людей", () -> navigate("search")); return; }
            for (int i=0; i<list.length(); i++) {
                JSONObject chat = list.optJSONObject(i);
                if (chat != null) chatRow(chat);
            }
        });
    }

    private void chatRow(JSONObject chat) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = text(initial(chat.optString("title", "K")), 18, WHITE, Gravity.CENTER);
        icon.setBackground(bg(PURPLE, 25));
        row.addView(icon, new LinearLayout.LayoutParams(dp(47), dp(47)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, -2, 1);
        infoLp.leftMargin = dp(11); row.addView(info, infoLp);
        info.addView(text(chat.optString("title", "Чат"), 15, WHITE, Gravity.START));
        String preview = chat.optString("last_message", "");
        if (preview.isEmpty() || "null".equals(preview)) preview = "Начни разговор";
        TextView last = text(preview, 12, MUTED, Gravity.START);
        last.setMaxLines(1); last.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(last, topMargin(match(), 4));
        row.setOnClickListener(v -> {
            currentChat = chat; chatId = chat.optLong("id", -1); screen = "chat"; shell();
        });
        page.addView(row);
    }

    private void friends() {
        heading("Друзья", "Люди, с которыми можно общаться.");
        api("GET", "/api/friends", null, (data, error) -> {
            if (!"friends".equals(screen)) return;
            page.removeAllViews(); heading("Друзья", "Люди, с которыми можно общаться.");
            if (error != null) { empty("Не получилось загрузить друзей", error, "Повторить", this::friends); return; }
            JSONArray list = data instanceof JSONArray ? (JSONArray)data : new JSONArray();
            if (list.length() == 0) { empty("Пока нет друзей", "Найди человека по username и отправь заявку.", "Найти друзей", () -> navigate("search")); return; }
            for (int i=0; i<list.length(); i++) {
                JSONObject u = list.optJSONObject(i); if (u == null) continue;
                LinearLayout row = card(); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
                TextView icon = text(initial(u.optString("display_name","K")),17,WHITE,Gravity.CENTER); icon.setBackground(bg(PURPLE,24));
                row.addView(icon,new LinearLayout.LayoutParams(dp(43),dp(43)));
                LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0,-2,1); ip.leftMargin=dp(10); row.addView(info,ip);
                info.addView(text(u.optString("display_name",u.optString("username","")),14,WHITE,Gravity.START));
                info.addView(text("@"+u.optString("username","")+(u.optBoolean("online",false)?" · в сети":""),12,u.optBoolean("online",false)?GREEN:MUTED,Gravity.START));
                Button write=button("Написать",true); row.addView(write,new LinearLayout.LayoutParams(dp(89),dp(44)));
                write.setOnClickListener(v -> direct(u)); row.setOnClickListener(v -> direct(u)); page.addView(row);
            }
        });
    }

    private void direct(JSONObject user) {
        api("POST","/api/chats/direct/"+user.optLong("id"),new JSONObject(),(data,error)->{
            if(error!=null) toast(error);
            else if(data instanceof JSONObject){currentChat=(JSONObject)data;chatId=currentChat.optLong("id",-1);screen="chat";shell();}
        });
    }

    private void requests() {
        heading("Заявки", "Новые знакомства начинаются здесь.");
        api("GET","/api/friends/requests",null,(data,error)->{
            if(!"requests".equals(screen)) return;
            page.removeAllViews(); heading("Заявки","Новые знакомства начинаются здесь.");
            if(error!=null){empty("Не удалось загрузить заявки",error,"Повторить",this::requests);return;}
            JSONObject obj=data instanceof JSONObject?(JSONObject)data:new JSONObject();
            JSONArray incoming=obj.optJSONArray("incoming"); if(incoming==null)incoming=new JSONArray();
            JSONArray outgoing=obj.optJSONArray("outgoing"); if(outgoing==null)outgoing=new JSONArray();
            TextView inHead=text("ВХОДЯЩИЕ · "+incoming.length(),11,ACCENT,Gravity.START); inHead.setTypeface(Typeface.DEFAULT_BOLD); page.addView(inHead,topMargin(match(),4));
            if(incoming.length()==0) page.addView(text("Новых заявок пока нет.",13,MUTED,Gravity.START),topMargin(match(),7));
            for(int i=0;i<incoming.length();i++){JSONObject u=incoming.optJSONObject(i);if(u!=null)requestRow(u,true);}
            TextView outHead=text("ОТПРАВЛЕННЫЕ · "+outgoing.length(),11,ACCENT,Gravity.START);outHead.setTypeface(Typeface.DEFAULT_BOLD);page.addView(outHead,topMargin(match(),18));
            if(outgoing.length()==0) page.addView(text("Ты пока никому не отправлял заявку.",13,MUTED,Gravity.START),topMargin(match(),7));
            for(int i=0;i<outgoing.length();i++){JSONObject u=outgoing.optJSONObject(i);if(u!=null)requestRow(u,false);}
        });
    }

    private void requestRow(JSONObject user, boolean incoming) {
        LinearLayout box=card(); box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(user.optString("display_name",user.optString("username","")),14,WHITE,Gravity.START));
        box.addView(text("@"+user.optString("username",""),12,MUTED,Gravity.START),topMargin(match(),3));
        if(incoming){
            LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); box.addView(actions,topMargin(match(),10));
            Button accept=button("Принять",true), decline=button("Отклонить",false);
            actions.addView(accept,new LinearLayout.LayoutParams(0,dp(44),1));
            LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(0,dp(44),1);dl.leftMargin=dp(8);actions.addView(decline,dl);
            long id=user.optLong("request_id",-1);
            accept.setOnClickListener(v->respond(id,"accept")); decline.setOnClickListener(v->respond(id,"decline"));
        } else box.addView(text("Ожидает ответа",12,ACCENT,Gravity.START),topMargin(match(),8));
        page.addView(box);
    }

    private void respond(long id,String action){
        api("POST","/api/friends/requests/"+id+"/"+action,new JSONObject(),(data,error)->{
            if(error!=null)toast(error);else{toast(action.equals("accept")?"Теперь вы друзья!":"Заявка отклонена.");requests();}
        });
    }

    private void search() {
        heading("Найти людей","Ищи по username или имени.");
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        EditText query=field("Например, ghost_player");query.setSingleLine(true);
        row.addView(query,new LinearLayout.LayoutParams(0,dp(51),1));
        Button go=button("Искать",true);LinearLayout.LayoutParams gl=new LinearLayout.LayoutParams(dp(85),dp(51));gl.leftMargin=dp(7);row.addView(go,gl);page.addView(row);
        LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);page.addView(results,topMargin(match(),16));
        go.setOnClickListener(v->{
            String q=query.getText().toString().trim();if(q.length()<2){query.setError("Введи хотя бы 2 символа");return;}
            hideKeyboard(query);results.removeAllViews();results.addView(text("Ищем…",13,MUTED,Gravity.START));
            try {
                api("GET","/api/users/search?q="+URLEncoder.encode(q,"UTF-8"),null,(data,error)->{
                    results.removeAllViews();
                    if(error!=null){results.addView(text(error,13,Color.RED,Gravity.START));return;}
                    JSONArray users=data instanceof JSONArray?(JSONArray)data:new JSONArray();
                    if(users.length()==0){results.addView(text("Никого не нашли. Проверь username.",13,MUTED,Gravity.START));return;}
                    for(int i=0;i<users.length();i++){JSONObject u=users.optJSONObject(i);if(u!=null)searchRow(results,u);}
                });
            } catch(Exception e){toast("Не удалось выполнить поиск.");}
        });
    }

    private void searchRow(LinearLayout parent,JSONObject user){
        LinearLayout row=card();row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon=text(initial(user.optString("display_name","K")),17,WHITE,Gravity.CENTER);icon.setBackground(bg(PURPLE,24));
        row.addView(icon,new LinearLayout.LayoutParams(dp(43),dp(43)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.leftMargin=dp(10);row.addView(info,ip);
        info.addView(text(user.optString("display_name",""),14,WHITE,Gravity.START));
        info.addView(text("@"+user.optString("username",""),12,MUTED,Gravity.START));
        Button add=button("Добавить",true);row.addView(add,new LinearLayout.LayoutParams(dp(91),dp(43)));
        add.setOnClickListener(v->{add.setEnabled(false);api("POST","/api/friends/requests",obj("username",user.optString("username","")),(data,error)->{
            if(error!=null){toast(error);add.setEnabled(true);}else{toast("Заявка отправлена.");add.setText("Отправлено");}
        });});
        parent.addView(row);
    }

    private void profile(){
        heading("Твой профиль","Настройки аккаунта Kemtiz.");
        LinearLayout box=card();box.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView icon=text(initial(me==null?"K":me.optString("display_name","K")),30,WHITE,Gravity.CENTER);icon.setTypeface(Typeface.DEFAULT_BOLD);icon.setBackground(bg(PURPLE,44));
        box.addView(icon,new LinearLayout.LayoutParams(dp(82),dp(82)));
        TextView name=text(me==null?"Kemtiz":me.optString("display_name","Kemtiz"),21,WHITE,Gravity.CENTER);name.setTypeface(Typeface.DEFAULT_BOLD);box.addView(name,topMargin(match(),12));
        box.addView(text(me==null?"":"@"+me.optString("username",""),14,ACCENT,Gravity.CENTER),topMargin(match(),4));
        if(me!=null&&!me.optString("email","").isEmpty())box.addView(text(me.optString("email",""),12,MUTED,Gravity.CENTER),topMargin(match(),4));
        page.addView(box);
        LinearLayout about=card();about.addView(text("О KEMTIZ",11,ACCENT,Gravity.START));
        about.addView(text(me==null?"Общайся, находи друзей и создавай чаты.":me.optString("about","Общайся, находи друзей и создавай чаты."),14,WHITE,Gravity.START),topMargin(match(),7));
        page.addView(about,topMargin(match(),8));
        Button logout=button("Выйти из аккаунта",false);page.addView(logout,topMargin(match(),13));
        logout.setOnClickListener(v->{clearSession();login("");});
    }


    private LinearLayout buildChatHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));
        bar.setBackground(bg(SURFACE, 18));
        Button back = button("‹", false);
        back.setTextSize(27);
        back.setPadding(0, 0, 0, 0);
        bar.addView(back, new LinearLayout.LayoutParams(dp(43), dp(46)));
        back.setOnClickListener(v -> navigate("chats"));
        String titleText = currentChat == null ? "Чат" : currentChat.optString("title", "Чат");
        TextView avatar = text(initial(titleText), 18, WHITE, Gravity.CENTER);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setBackground(bg(PURPLE, 24));
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(43), dp(43));
        avatarLp.leftMargin = dp(7);
        bar.addView(avatar, avatarLp);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, -2, 1f);
        infoLp.leftMargin = dp(10);
        bar.addView(info, infoLp);
        TextView title = text(titleText, 15, WHITE, Gravity.START);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        info.addView(title);
        String stateText = "group".equals(currentChat == null ? "" : currentChat.optString("kind", ""))
                ? "Групповой чат" : "Личный чат";
        JSONArray members = currentChat == null ? null : currentChat.optJSONArray("members");
        if (members != null && me != null) {
            for (int i = 0; i < members.length(); i++) {
                JSONObject member = members.optJSONObject(i);
                if (member != null && member.optLong("id", -1) != me.optLong("id", -2)) {
                    stateText = member.optBoolean("online", false) ? "В сети" : "Был(а) не в сети";
                    break;
                }
            }
        }
        info.addView(text(stateText, 11, GREEN, Gravity.START), topMargin(match(), 3));
        Button video = button("📹", true);
        video.setTextSize(17);
        bar.addView(video, new LinearLayout.LayoutParams(dp(52), dp(46)));
        video.setOnClickListener(v -> startVideoCall());
        return bar;
    }

    private void chat() {
        long id = chatId;
        if (page != null) {
            page.removeAllViews();
            page.addView(text("Загружаем сообщения…", 12, MUTED, Gravity.CENTER),
                    topMargin(match(), 14));
        }
        api("GET", "/api/chats/" + id + "/messages?limit=100", null, (data, error) -> {
            if (!"chat".equals(screen) || id != chatId || page == null) return;
            page.removeAllViews();
            if (error != null) {
                page.addView(text("Не удалось загрузить сообщения", 15, WHITE, Gravity.CENTER),
                        topMargin(match(), 12));
                page.addView(text(error, 12, Color.rgb(255,130,157), Gravity.CENTER),
                        topMargin(match(), 7));
                Button retry = button("Повторить", false);
                page.addView(retry, topMargin(match(), 10));
                retry.setOnClickListener(v -> chat());
                return;
            }
            JSONArray msgs = data instanceof JSONArray ? (JSONArray) data : new JSONArray();
            if (msgs.length() == 0) {
                LinearLayout empty = card();
                empty.setGravity(Gravity.CENTER);
                TextView icon = text("✦", 27, ACCENT, Gravity.CENTER);
                icon.setBackground(bg(PANEL, 28));
                empty.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));
                TextView h = text("Начни разговор", 16, WHITE, Gravity.CENTER);
                h.setTypeface(Typeface.DEFAULT_BOLD);
                empty.addView(h, topMargin(match(), 10));
                empty.addView(text("Напиши первое сообщение — оно появится здесь.", 12, MUTED, Gravity.CENTER),
                        topMargin(match(), 4));
                page.addView(empty, topMargin(match(), 12));
            }
            boolean groupChat = currentChat != null && "group".equals(currentChat.optString("kind", ""));
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject message = msgs.optJSONObject(i);
                if (message == null) continue;
                boolean own = me != null && message.optLong("sender_id", -2) == me.optLong("id", -1);
                LinearLayout line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(own ? Gravity.END : Gravity.START);
                LinearLayout.LayoutParams lineLp = match();
                lineLp.topMargin = dp(5);
                page.addView(line, lineLp);

                LinearLayout bubble = new LinearLayout(this);
                bubble.setOrientation(LinearLayout.VERTICAL);
                bubble.setPadding(dp(13), dp(9), dp(13), dp(8));
                bubble.setBackground(bg(own ? Color.rgb(65, 48, 103) : PANEL, 16));
                LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(-2, -2);
                bubbleLp.leftMargin = dp(own ? 42 : 0);
                bubbleLp.rightMargin = dp(own ? 0 : 42);
                line.addView(bubble, bubbleLp);

                if (groupChat) {
                    String sender = own ? "Ты" : message.optString("sender_display_name",
                            message.optString("sender_username", "Пользователь"));
                    TextView senderView = text(sender, 11, own ? ACCENT : GREEN, Gravity.START);
                    senderView.setTypeface(Typeface.DEFAULT_BOLD);
                    bubble.addView(senderView, match());
                }
                TextView body = text(message.optString("body", ""), 14, WHITE, Gravity.START);
                body.setMaxWidth(dp(270));
                bubble.addView(body, match());

                String created = message.optString("created_at", "");
                String time = created.length() >= 16 ? created.substring(11, 16) : "";
                if (!time.isEmpty()) {
                    TextView timeView = text(time, 10, MUTED, Gravity.END);
                    LinearLayout.LayoutParams timeLp = match();
                    timeLp.topMargin = dp(4);
                    bubble.addView(timeView, timeLp);
                }
            }
            if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void empty(String title,String description,String action,Runnable task){
        LinearLayout box=card();box.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView icon=text("✦",27,ACCENT,Gravity.CENTER);icon.setBackground(bg(PANEL,30));box.addView(icon,new LinearLayout.LayoutParams(dp(58),dp(58)));
        TextView h=text(title,17,WHITE,Gravity.CENTER);h.setTypeface(Typeface.DEFAULT_BOLD);box.addView(h,topMargin(match(),12));
        box.addView(text(description,13,MUTED,Gravity.CENTER),topMargin(match(),7));
        Button b=button(action,true);box.addView(b,topMargin(match(),14));b.setOnClickListener(v->task.run());page.addView(box);
    }

    // WebSocket events
    private String websocketUrl() {
        if (serverBase.startsWith("https://")) return "wss://" + serverBase.substring(8) + "/ws";
        if (serverBase.startsWith("http://")) return "ws://" + serverBase.substring(7) + "/ws";
        return serverBase + "/ws";
    }

    private String normalizeServerBase(String value) {
        if (value == null) return "";
        String base = value.trim();
        if (base.isEmpty()) return "";
        if (!base.startsWith("https://") && !base.startsWith("http://")) base = "https://" + base;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        android.net.Uri uri = android.net.Uri.parse(base);
        String scheme = uri.getScheme();
        if (uri.getHost() == null || uri.getHost().trim().isEmpty()
            || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
            || uri.getQuery() != null || uri.getFragment() != null
            || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            return "";
        }
        return base;
    }

    private void saveAndCheckServer() {
        String candidate = normalizeServerBase(serverUrlField == null ? "" : serverUrlField.getText().toString());
        if (candidate.isEmpty()) {
            if (serverUrlField != null) serverUrlField.setError("Введи полный адрес, например https://name.trycloudflare.com");
            return;
        }
        serverBase = candidate;
        prefs.edit().putString(SERVER_PREF, serverBase).apply();
        toast("Адрес сохранён. Проверяю API…");
        api("GET", "/health", null, (data, error) -> {
            if (error != null) {
                authError.setText("Сервер недоступен: " + error);
                authError.setVisibility(View.VISIBLE);
                return;
            }
            if (data instanceof JSONObject) {
                JSONObject health = (JSONObject) data;
                if (health.optBoolean("password_auth", false)) {
                    toast("Сервер Kemtiz доступен. Можно регистрироваться.");
                    if (authError != null) authError.setVisibility(View.GONE);
                } else {
                    String version = health.optString("api_version", "неизвестна");
                    authError.setText("Сервер отвечает, но версия API устарела (" + version
                        + "). Запусти новый серверный пакет Kemtiz на ПК.");
                    authError.setVisibility(View.VISIBLE);
                }
            } else {
                authError.setText("Сервер ответил в неизвестном формате.");
                authError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void socket(){
        closeSocket();if(token.isEmpty())return;
        socket=http.newWebSocket(new Request.Builder().url(websocketUrl()).build(),new WebSocketListener(){
            @Override public void onOpen(WebSocket ws,Response response){ws.send(obj("type","auth","token",token).toString());}
            @Override public void onMessage(WebSocket ws,String text){
                try{
                    JSONObject event=new JSONObject(text);String type=event.optString("type","");
                    main.post(()->{
                        if("message.new".equals(type)){
                            JSONObject m=event.optJSONObject("message");
                            if (m != null && !activityVisible && me != null
                                    && m.optLong("sender_id", -1) != me.optLong("id", -2)) {
                                showMessageNotification(m);
                            }
                            if("chat".equals(screen)&&m!=null&&m.optLong("chat_id",-1)==chatId)chat();
                            else if("chats".equals(screen))chats();
                        }else if("chat_list_changed".equals(type)&&"chats".equals(screen))chats();
                        else if("call.incoming".equals(type)){
                            if (activityVisible) showIncomingCall(event);
                            else showIncomingCallNotification(event);
                        }else if("call.error".equals(type)){
                            toast(event.optString("message","Не удалось начать звонок."));
                        }else if("friend_request".equals(type)&&"requests".equals(screen))requests();
                        else if("friend_list_changed".equals(type)){if("friends".equals(screen))friends();if("requests".equals(screen))requests();}
                    });
                }catch(JSONException ignored){}
            }
            @Override public void onFailure(WebSocket ws,Throwable t,Response response){
                if(!isFinishing()&&!token.isEmpty())main.postDelayed(()->{if(!isFinishing()&&!token.isEmpty())socket();},4000);
            }
        });
    }



    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                        NOTIFICATION_CHANNEL, "Kemtiz — сообщения и звонки", NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("Новые сообщения и входящие видеозвонки");
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !prefs.getBoolean("notification_permission_requested", false)) {
            prefs.edit().putBoolean("notification_permission_requested", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5100);
        }
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void showMessageNotification(JSONObject message) {
        if (!canPostNotifications()) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        String sender = message.optString("sender_display_name",
                message.optString("sender_username", "Новое сообщение"));
        String body = message.optString("body", "Тебе отправили сообщение");
        if (body.length() > 180) body = body.substring(0, 177) + "…";
        Intent open = new Intent(this, KemtizActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this,
                4100 + (int) Math.max(0, message.optLong("chat_id", 0) % 500000), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(sender)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setShowWhen(true)
                .build();
        manager.notify(10000 + (int) Math.max(0, message.optLong("chat_id", 0) % 90000), notification);
    }

    private void showIncomingCallNotification(JSONObject event) {
        if (!canPostNotifications()) return;
        long incomingChatId = event.optLong("chat_id", -1);
        long callerId = event.optLong("from_user_id", -1);
        String incomingCallId = event.optString("call_id", "");
        String callerName = event.optString("from_display_name", event.optString("from_username", "Пользователь"));
        if (incomingChatId <= 0 || callerId <= 0 || incomingCallId.isEmpty()) return;
        Intent accept = new Intent(this, CallActivity.class);
        accept.putExtra("server_base", serverBase);
        accept.putExtra("token", token);
        accept.putExtra("chat_id", incomingChatId);
        accept.putExtra("target_id", callerId);
        accept.putExtra("target_name", callerName);
        accept.putExtra("call_id", incomingCallId);
        accept.putExtra("mode", "answer");
        accept.putExtra("video", true);
        PendingIntent acceptPending = PendingIntent.getActivity(this, CALL_NOTIFICATION_ID, accept,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Входящий видеозвонок")
                .setContentText(callerName + " звонит тебе")
                .setContentIntent(acceptPending)
                .addAction(android.R.drawable.ic_menu_call, "Принять", acceptPending)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(false)
                .setShowWhen(true)
                .build();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(CALL_NOTIFICATION_ID, notification);
    }

    @Override protected void onResume() {
        super.onResume();
        activityVisible = true;
    }

    @Override protected void onPause() {
        activityVisible = false;
        super.onPause();
    }

    private void startVideoCall() {
        if (currentChat == null || chatId <= 0) {
            toast("Сначала открой личный чат.");
            return;
        }
        if (!"direct".equals(currentChat.optString("kind", "direct"))) {
            toast("Пока видеозвонки доступны в личных чатах.");
            return;
        }
        long otherId = currentChat.optLong("other_user_id", -1);
        if (otherId <= 0) {
            toast("Не удалось определить собеседника.");
            return;
        }
        Intent intent = new Intent(this, CallActivity.class);
        intent.putExtra("server_base", serverBase);
        intent.putExtra("token", token);
        intent.putExtra("chat_id", chatId);
        intent.putExtra("target_id", otherId);
        intent.putExtra("target_name", currentChat.optString("title", "Собеседник"));
        intent.putExtra("call_id", UUID.randomUUID().toString().replace("-", ""));
        intent.putExtra("mode", "offer");
        intent.putExtra("video", true);
        startActivity(intent);
    }

    private void showIncomingCall(JSONObject event) {
        if (isFinishing() || token.isEmpty()) return;
        final long incomingChatId = event.optLong("chat_id", -1);
        final long callerId = event.optLong("from_user_id", -1);
        final String incomingCallId = event.optString("call_id", "");
        final String callerName = event.optString("from_display_name",
                event.optString("from_username", "Пользователь"));
        if (incomingChatId <= 0 || callerId <= 0 || incomingCallId.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Входящий видеозвонок")
                .setMessage(callerName + " звонит тебе по видеосвязи.")
                .setPositiveButton("Принять", (dialog, which) -> {
                    Intent intent = new Intent(this, CallActivity.class);
                    intent.putExtra("server_base", serverBase);
                    intent.putExtra("token", token);
                    intent.putExtra("chat_id", incomingChatId);
                    intent.putExtra("target_id", callerId);
                    intent.putExtra("target_name", callerName);
                    intent.putExtra("call_id", incomingCallId);
                    intent.putExtra("mode", "answer");
                    intent.putExtra("video", true);
                    startActivity(intent);
                })
                .setNegativeButton("Отклонить", (dialog, which) -> {
                    if (socket != null) {
                        socket.send(obj("type", "call.reject", "chat_id", incomingChatId,
                                "target_user_id", callerId, "call_id", incomingCallId).toString());
                    }
                })
                .setOnCancelListener(dialog -> {
                    if (socket != null) {
                        socket.send(obj("type", "call.reject", "chat_id", incomingChatId,
                                "target_user_id", callerId, "call_id", incomingCallId).toString());
                    }
                })
                .show();
    }

    private void closeSocket(){if(socket!=null){socket.close(1000,"close");socket=null;}}

    private String apiError(Object parsed, int status, String path) {
        if (status == 404 && path.startsWith("/api/auth/password/")) {
            String action = path.endsWith("/register") ? "регистрации" : "входа";
            return "На выбранном сервере нет маршрута " + action
                + ". Запусти последнюю версию Kemtiz API и проверь адрес сервера.";
        }

        String fallback = "Ошибка " + status;
        if (parsed instanceof JSONObject) {
            JSONObject object = (JSONObject) parsed;
            Object detail = object.opt("detail");
            if (detail instanceof String && !((String) detail).trim().isEmpty()) {
                return ((String) detail).trim();
            }
            if (detail instanceof JSONArray) {
                JSONArray errors = (JSONArray) detail;
                if (errors.length() > 0) {
                    JSONObject first = errors.optJSONObject(0);
                    if (first != null) {
                        String message = first.optString("msg", "").trim();
                        if (!message.isEmpty()) return "Проверь данные: " + message;
                    }
                }
            }
            String message = object.optString("message", "").trim();
            if (!message.isEmpty()) return message;
        }
        return fallback;
    }

    private void api(String method,String path,JSONObject body,Result callback){
        Request.Builder b=new Request.Builder().url(serverBase+path);
        if(!token.isEmpty())b.header("Authorization","Bearer "+token);
        if("POST".equals(method))b.post(RequestBody.create(JSON,body==null?"{}":body.toString()));else b.get();
        http.newCall(b.build()).enqueue(new Callback(){
            @Override public void onFailure(Call call,IOException e){
                String message = e instanceof java.net.SocketTimeoutException
                    ? "Сервер долго отвечает. Проверь, что сервер запущен на ПК и публичный HTTPS-адрес активен."
                    : "Нет соединения. Проверь адрес сервера, запущен ли start_kemtiz_server.bat и разрешение Windows Firewall.";
                main.post(()->callback.done(null,message));
            }
            @Override public void onResponse(Call call,Response response)throws IOException{
                String raw=response.body()==null?"":response.body().string();Object parsed=new JSONObject();
                try{String s=raw.trim();if(s.startsWith("["))parsed=new JSONArray(s);else if(s.startsWith("{"))parsed=new JSONObject(s);}catch(JSONException ignored){}
                String error=null;
                if(!response.isSuccessful()) error=apiError(parsed,response.code(),path);
                Object finalData=parsed;String finalError=error;
                main.post(()->{
                    if(response.code()==401&&!path.startsWith("/api/auth/")&&!token.isEmpty()){
                        clearSession();login("Сессия завершилась. Войди снова.");return;
                    }
                    callback.done(finalData,finalError);
                });
            }
        });
    }

    // UI helpers
    private LinearLayout card(){
        LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(14),dp(14),dp(14),dp(14));v.setBackground(bg(SURFACE,17));
        LinearLayout.LayoutParams lp=match();lp.bottomMargin=dp(9);v.setLayoutParams(lp);return v;
    }
    private void feature(LinearLayout parent,String symbol,String title,String subtitle){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setOrientation(LinearLayout.HORIZONTAL);parent.addView(row,topMargin(match(),13));
        TextView icon=text(symbol,17,ACCENT,Gravity.CENTER);icon.setBackground(bg(PANEL,12));row.addView(icon,new LinearLayout.LayoutParams(dp(37),dp(37)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.leftMargin=dp(10);row.addView(info,ip);
        TextView h=text(title,13,WHITE,Gravity.START);h.setTypeface(Typeface.DEFAULT_BOLD);info.addView(h);
        info.addView(text(subtitle,11,MUTED,Gravity.START),topMargin(match(),2));
    }
    private TextView text(String value,float size,int color,int gravity){
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setGravity(gravity);v.setLineSpacing(dp(2),1.08f);return v;
    }
    private EditText field(String hint){
        EditText v=new EditText(this);v.setTextSize(14);v.setTextColor(WHITE);v.setHintTextColor(MUTED);v.setHint(hint);v.setPadding(dp(13),dp(11),dp(13),dp(11));v.setBackground(bg(PANEL,13));return v;
    }
    private Button button(String label,boolean primary){
        Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT_BOLD);b.setAllCaps(false);b.setTextColor(WHITE);b.setMinHeight(dp(47));b.setPadding(dp(10),dp(6),dp(10),dp(6));b.setBackground(bg(primary?PURPLE:PANEL,13));return b;
    }
    private GradientDrawable bg(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
        if(color==PURPLE)d.setStroke(dp(1),Color.rgb(166,137,255));
        else if(color==SURFACE||color==PANEL)d.setStroke(dp(1),Color.rgb(41,39,57));
        return d;
    }
    private LinearLayout.LayoutParams match(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private LinearLayout.LayoutParams wrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private LinearLayout.LayoutParams topMargin(LinearLayout.LayoutParams p,int dp){p.topMargin=dp(dp);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String initial(String s){return s==null||s.trim().isEmpty()?"K":s.trim().substring(0,1).toUpperCase(Locale.ROOT);}
    private JSONObject obj(Object... values){
        JSONObject o=new JSONObject();try{for(int i=0;i+1<values.length;i+=2)o.put(String.valueOf(values[i]),values[i+1]);}catch(JSONException ignored){}return o;
    }
    private void toast(String message){if(message!=null&&!message.trim().isEmpty())main.post(()->Toast.makeText(this,message,Toast.LENGTH_LONG).show());}
    private void hideKeyboard(View view){try{InputMethodManager im=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);if(im!=null)im.hideSoftInputFromWindow(view.getWindowToken(),0);}catch(Exception ignored){}}
    @Override public void onBackPressed(){
        if("chat".equals(screen))navigate("chats");else if(!"chats".equals(screen))navigate("chats");else super.onBackPressed();
    }
    @Override protected void onDestroy(){closeSocket();super.onDestroy();}
}
