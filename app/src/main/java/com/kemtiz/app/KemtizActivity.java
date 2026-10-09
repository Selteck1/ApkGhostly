package com.kemtiz.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
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
import java.util.concurrent.TimeUnit;

public class KemtizActivity extends Activity {
    private static final String API = "https://kemtiz-api.onrender.com";
    private static final String WS = "wss://kemtiz-api.onrender.com/ws";
    private static final String GOOGLE_CLIENT_ID = "649066614178-fu0q0b09mi7iumi98ke3u435oe193vl8.apps.googleusercontent.com";
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
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS).pingInterval(25, TimeUnit.SECONDS).build();
    private SharedPreferences prefs;
    private String token = "";
    private JSONObject me;
    private JSONObject googleProfile;
    private String googleCredential = "";
    private JSONObject currentChat;
    private long chatId = -1;
    private String screen = "chats";
    private LinearLayout root, page;
    private ScrollView scroll;
    private WebSocket socket;

    private interface Result { void done(Object data, String error); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("kemtiz", MODE_PRIVATE);
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

    // Google authentication
    private void login(String warning) {
        closeSocket();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(23), dp(25), dp(23), dp(25));
        root.setBackground(bg(BG, 0));

        TextView logo = text("✦", 36, WHITE, Gravity.CENTER);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setBackground(bg(PURPLE, 25));
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(82), dp(82));
        logoLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(logo, logoLp);

        TextView brand = text("KEMTIZ", 29, WHITE, Gravity.CENTER);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setLetterSpacing(.12f);
        LinearLayout.LayoutParams brandLp = wrap();
        brandLp.gravity = Gravity.CENTER_HORIZONTAL;
        brandLp.topMargin = dp(15);
        root.addView(brand, brandLp);
        TextView tagline = text("ТВОЙ КРУГ. ТВОИ РАЗГОВОРЫ.", 10, ACCENT, Gravity.CENTER);
        tagline.setLetterSpacing(.1f);
        LinearLayout.LayoutParams tagLp = wrap();
        tagLp.gravity = Gravity.CENTER_HORIZONTAL;
        tagLp.topMargin = dp(5);
        root.addView(tagline, tagLp);

        LinearLayout intro = card();
        LinearLayout.LayoutParams introLp = match();
        introLp.topMargin = dp(30);
        root.addView(intro, introLp);
        TextView kicker = text("ТВОЙ МИР ОБЩЕНИЯ", 11, ACCENT, Gravity.START);
        kicker.setTypeface(Typeface.DEFAULT_BOLD);
        intro.addView(kicker);
        TextView title = text("Ближе к своим.\nВ каждом сообщении.", 25, WHITE, Gravity.START);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = match();
        titleLp.topMargin = dp(11);
        intro.addView(title, titleLp);
        TextView subtitle = text("Личные чаты, друзья и свои группы. Всё важное — в одном приложении.", 14, MUTED, Gravity.START);
        LinearLayout.LayoutParams subLp = match();
        subLp.topMargin = dp(9);
        intro.addView(subtitle, subLp);
        feature(intro, "✦", "Вход через Google", "Без отдельного пароля");
        feature(intro, "◎", "Твои люди", "Поиск друзей по username");
        feature(intro, "↗", "Нативный Android", "Не веб-страница внутри приложения");

        Button signIn = button("Продолжить с Google", true);
        LinearLayout.LayoutParams signLp = match();
        signLp.topMargin = dp(21);
        root.addView(signIn, signLp);
        signIn.setOnClickListener(v -> googleSignIn());
        root.addView(text("Защищённый вход · Kemtiz Account", 12, MUTED, Gravity.CENTER), topMargin(match(), 12));
        if (warning != null && !warning.trim().isEmpty()) {
            TextView msg = text(warning, 12, Color.rgb(255, 130, 157), Gravity.CENTER);
            root.addView(msg, topMargin(match(), 10));
        }
        setContentView(root);
    }

    private void googleSignIn() {
        try {
            GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setServerClientId(GOOGLE_CLIENT_ID)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build();
            GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option).build();
            CredentialManager.create(this).getCredentialAsync(
                this, request, new CancellationSignal(), command -> main.post(command),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(GetCredentialResponse response) {
                        Credential cred = response.getCredential();
                        if (cred instanceof CustomCredential &&
                            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(cred.getType())) {
                            try {
                                GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(
                                    ((CustomCredential) cred).getData());
                                googleStart(google.getIdToken());
                            } catch (Exception e) { toast("Не удалось прочитать аккаунт Google."); }
                        } else toast("Google вернул неподдерживаемый тип аккаунта.");
                    }
                    @Override public void onError(GetCredentialException e) {
                        String message = e.getMessage();
                        if (message != null && message.toLowerCase(Locale.ROOT).contains("cancel")) return;
                        toast("Не удалось войти через Google. Попробуй ещё раз.");
                    }
                });
        } catch (Exception e) {
            toast("Не удалось открыть Google-вход. Проверь Google Play Services.");
        }
    }

    private void googleStart(String credential) {
        JSONObject body = obj("credential", credential);
        api("POST", "/api/auth/google/start", body, (data, error) -> {
            if (error != null || !(data instanceof JSONObject)) {
                login(error == null ? "Не удалось войти через Google." : error);
                return;
            }
            JSONObject result = (JSONObject) data;
            if (result.optBoolean("needs_profile", false)) {
                googleCredential = credential;
                googleProfile = result.optJSONObject("profile");
                profileSetup();
            } else accept((JSONObject) data);
        });
    }

    private void profileSetup() {
        String email = googleProfile == null ? "" : googleProfile.optString("email", "");
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(25));
        root.setBackground(bg(BG, 0));
        root.addView(text("✦", 32, ACCENT, Gravity.START));
        TextView title = text("Создадим твой профиль", 25, WHITE, Gravity.START);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, topMargin(match(), 12));
        root.addView(text((googleProfile == null ? "Google аккаунт" : googleProfile.optString("name", "Google аккаунт")) +
            (email.isEmpty() ? "" : "\n" + email), 13, MUTED, Gravity.START), topMargin(match(), 8));
        EditText username = field("Username (латиница, цифры, _)");
        username.setSingleLine(true);
        username.setText(suggestUsername(email));
        root.addView(username, topMargin(match(), 24));
        EditText country = field("Страна (необязательно)");
        country.setSingleLine(true);
        root.addView(country, topMargin(match(), 10));
        EditText about = field("О себе (необязательно)");
        about.setMinLines(2);
        about.setGravity(Gravity.TOP | Gravity.START);
        root.addView(about, topMargin(match(), 10));
        Button finish = button("Создать аккаунт", true);
        root.addView(finish, topMargin(match(), 18));
        finish.setOnClickListener(v -> {
            String name = username.getText().toString().trim().replace("@", "").toLowerCase(Locale.ROOT);
            if (!name.matches("[a-z0-9][a-z0-9_]{2,23}")) {
                username.setError("3–24 символа: латиница, цифры и _");
                return;
            }
            hideKeyboard(username);
            JSONObject body = obj("credential", googleCredential, "username", name,
                "country", country.getText().toString().trim(), "about", about.getText().toString().trim());
            finish.setEnabled(false);
            api("POST", "/api/auth/google/finish", body, (data, error) -> {
                finish.setEnabled(true);
                if (error != null || !(data instanceof JSONObject)) toast(error == null ? "Не удалось создать профиль." : error);
                else accept((JSONObject) data);
            });
        });
        setContentView(root);
    }

    private String suggestUsername(String email) {
        String name = "kemtiz_user";
        if (email != null && email.contains("@")) {
            name = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_").replaceAll("^[^a-z0-9]+", "");
        }
        if (name.length() < 3) name = "kemtiz_user";
        if (name.length() > 15) name = name.substring(0, 15);
        return name + "_" + (1000 + (int)(Math.random() * 9000));
    }

    private void accept(JSONObject result) {
        token = result.optString("token", "");
        me = result.optJSONObject("user");
        if (token.isEmpty() || me == null) {
            clearSession(); login("Сервер не вернул данные аккаунта."); return;
        }
        prefs.edit().putString("token", token).apply();
        googleCredential = "";
        googleProfile = null;
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
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(bg(BG, 0));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(17), dp(13), dp(17), dp(13));
        TextView logo = text("✦", 19, WHITE, Gravity.CENTER);
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
        nav.setPadding(dp(4), dp(8), dp(4), dp(7));
        nav.setBackground(bg(SURFACE, 18));
        String[][] items = {{"chats","▤","Чаты"},{"friends","♙","Друзья"},{"requests","↗","Заявки"},{"search","⌕","Найти"},{"profile","●","Профиль"}};
        for (String[] item : items) {
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            int c = item[0].equals(screen) || ("chat".equals(screen) && "chats".equals(item[0])) ? ACCENT : MUTED;
            tab.addView(text(item[1], 19, c, Gravity.CENTER));
            tab.addView(text(item[2], 10, c == ACCENT ? WHITE : MUTED, Gravity.CENTER), topMargin(wrap(), 2));
            tab.setOnClickListener(v -> navigate(item[0]));
            nav.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
        }
        root.addView(nav, match());
        setContentView(root);
        render();
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
        heading("Твои чаты", "Все разговоры — в одном месте.");
        api("GET", "/api/chats", null, (data, error) -> {
            if (!"chats".equals(screen)) return;
            page.removeAllViews(); heading("Твои чаты", "Все разговоры — в одном месте.");
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

    private void chat(){
        Button back=button("‹   Назад к чатам",false);page.addView(back);back.setOnClickListener(v->navigate("chats"));
        heading(currentChat==null?"Чат":currentChat.optString("title","Чат"),"Переписка синхронизируется с сервером.");
        long id=chatId;
        api("GET","/api/chats/"+id+"/messages?limit=100",null,(data,error)->{
            if(!"chat".equals(screen)||id!=chatId)return;
            page.removeAllViews();
            Button b=button("‹   Назад к чатам",false);page.addView(b);b.setOnClickListener(v->navigate("chats"));
            heading(currentChat==null?"Чат":currentChat.optString("title","Чат"),"Сообщения Kemtiz");
            if(error!=null)page.addView(text(error,13,Color.rgb(255,130,157),Gravity.START));
            JSONArray msgs=data instanceof JSONArray?(JSONArray)data:new JSONArray();
            if(msgs.length()==0)page.addView(text("Напиши первое сообщение 👋",13,MUTED,Gravity.CENTER),topMargin(match(),12));
            for(int i=0;i<msgs.length();i++){
                JSONObject m=msgs.optJSONObject(i);if(m==null)continue;
                boolean own=me!=null&&m.optLong("sender_id",-2)==me.optLong("id",-1);
                LinearLayout bubble=card();bubble.setBackground(bg(own?Color.rgb(53,41,83):PANEL,15));
                bubble.addView(text(own?"Ты":m.optString("sender_display_name",m.optString("sender_username","Пользователь")),11,own?ACCENT:GREEN,Gravity.START));
                bubble.addView(text(m.optString("body",""),14,WHITE,Gravity.START),topMargin(match(),4));
                LinearLayout.LayoutParams bp=match();bp.topMargin=dp(4);bp.leftMargin=own?dp(25):0;bp.rightMargin=own?0:dp(25);page.addView(bubble,bp);
            }
            LinearLayout composer=new LinearLayout(this);composer.setOrientation(LinearLayout.HORIZONTAL);composer.setGravity(Gravity.CENTER_VERTICAL);
            page.addView(composer,topMargin(match(),12));
            EditText message=field("Написать сообщение…");message.setMinHeight(dp(50));message.setMaxLines(4);
            composer.addView(message,new LinearLayout.LayoutParams(0,-2,1));
            Button send=button("➤",true);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(53),dp(50));sp.leftMargin=dp(7);composer.addView(send,sp);
            send.setOnClickListener(v->{
                String text=message.getText().toString().trim();if(text.isEmpty())return;
                send.setEnabled(false);api("POST","/api/chats/"+id+"/messages",obj("body",text),(sent,sendError)->{
                    if(sendError!=null){toast(sendError);send.setEnabled(true);}else chat();
                });
            });
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
    private void socket(){
        closeSocket();if(token.isEmpty())return;
        socket=http.newWebSocket(new Request.Builder().url(WS).build(),new WebSocketListener(){
            @Override public void onOpen(WebSocket ws,Response response){ws.send(obj("type","auth","token",token).toString());}
            @Override public void onMessage(WebSocket ws,String text){
                try{
                    JSONObject event=new JSONObject(text);String type=event.optString("type","");
                    main.post(()->{
                        if("message.new".equals(type)){
                            JSONObject m=event.optJSONObject("message");
                            if("chat".equals(screen)&&m!=null&&m.optLong("chat_id",-1)==chatId)chat();
                            else if("chats".equals(screen))chats();
                        }else if("chat_list_changed".equals(type)&&"chats".equals(screen))chats();
                        else if("friend_request".equals(type)&&"requests".equals(screen))requests();
                        else if("friend_list_changed".equals(type)){if("friends".equals(screen))friends();if("requests".equals(screen))requests();}
                    });
                }catch(JSONException ignored){}
            }
            @Override public void onFailure(WebSocket ws,Throwable t,Response response){
                if(!isFinishing()&&!token.isEmpty())main.postDelayed(()->{if(!isFinishing()&&!token.isEmpty())socket();},4000);
            }
        });
    }

    private void closeSocket(){if(socket!=null){socket.close(1000,"close");socket=null;}}

    private void api(String method,String path,JSONObject body,Result callback){
        Request.Builder b=new Request.Builder().url(API+path);
        if(!token.isEmpty())b.header("Authorization","Bearer "+token);
        if("POST".equals(method))b.post(RequestBody.create(JSON,body==null?"{}":body.toString()));else b.get();
        http.newCall(b.build()).enqueue(new Callback(){
            @Override public void onFailure(Call call,IOException e){main.post(()->callback.done(null,"Нет соединения с сервером. Проверь интернет и повтори попытку."));}
            @Override public void onResponse(Call call,Response response)throws IOException{
                String raw=response.body()==null?"":response.body().string();Object parsed=new JSONObject();
                try{String s=raw.trim();if(s.startsWith("["))parsed=new JSONArray(s);else if(s.startsWith("{"))parsed=new JSONObject(s);}catch(JSONException ignored){}
                String error=null;
                if(!response.isSuccessful()){
                    error="Ошибка "+response.code();
                    if(parsed instanceof JSONObject)error=((JSONObject)parsed).optString("detail",((JSONObject)parsed).optString("message",error));
                }
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
