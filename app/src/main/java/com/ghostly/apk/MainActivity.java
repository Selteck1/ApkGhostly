package com.ghostly.apk;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  int BG=Color.rgb(7,4,13), CARD=Color.rgb(20,13,31), PURPLE=Color.rgb(190,125,255), SOFT=Color.rgb(225,219,235);
  android.content.SharedPreferences p;

  public void onCreate(Bundle b){super.onCreate(b); getWindow().setFlags(1024,1024); hide(); p=getSharedPreferences("ghostly",0);
    if(p.getBoolean("logged",false)) menu(); else if(p.contains("nick")) login(); else welcome();
  }
  void hide(){getWindow().getDecorView().setSystemUiVisibility(5894);}
  TextView t(String s,int z,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(Color.WHITE);v.setGravity(17);v.setTypeface(null,bold?1:0);return v;}
  LinearLayout root(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setGravity(17);r.setPadding(28,40,28,40);r.setBackgroundColor(BG);return r;}
  LinearLayout.LayoutParams mw(){return new LinearLayout.LayoutParams(-1,-2);}
  void logo(LinearLayout r){TextView x=t("👻 GHOSTLY",31,true);x.setTextColor(PURPLE);r.addView(x,mw());}
  GradientDrawable bg(int c,int rad){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(rad*getResources().getDisplayMetrics().density);d.setStroke(1,Color.rgb(65,45,86));return d;}
  EditText f(String h){EditText e=new EditText(this);e.setHint(h);e.setHintTextColor(Color.rgb(145,134,160));e.setTextColor(Color.WHITE);e.setTextSize(17);e.setSingleLine();e.setPadding(20,15,20,15);e.setBackground(bg(CARD,18));return e;}
  Button b(String s){Button x=new Button(this);x.setText(s);x.setTextSize(16);x.setTextColor(Color.WHITE);x.setAllCaps(false);x.setBackground(bg(Color.rgb(34,22,49),18));return x;}
  void gap(LinearLayout r,int top){View v=new View(this);LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(1,top);r.addView(v,q);}
  void welcome(){LinearLayout r=root();logo(r);gap(r,20);r.addView(t("Добро пожаловать в Ghostly",26,true),mw());r.addView(t("Приложение для игроков Standoff 2",16,false),mw());Button reg=b("📝 Регистрация"),log=b("🔐 Войти");r.addView(reg,mw());LinearLayout.LayoutParams q=mw();q.topMargin=12;r.addView(log,q);reg.setOnClickListener(v->register());log.setOnClickListener(v->login());setContentView(r);}
  void register(){ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);LinearLayout r=root();logo(r);r.addView(t("Регистрация",27,true),mw());
    EditText n=f("Никнейм"),id=f("ID Standoff 2"),pw=f("Пароль"),rp=f("Повторить пароль");id.setInputType(2);pw.setInputType(129);rp.setInputType(129);
    r.addView(n,mw());lp(r,id,12);lp(r,pw,12);lp(r,rp,12);TextView err=t("",14,false);err.setTextColor(Color.rgb(255,140,140));lp(r,err,12);
    Button ok=b("✅ Создать аккаунт"),back=b("← Назад");lp(r,ok,8);lp(r,back,8);
    ok.setOnClickListener(v->{String a=n.getText().toString().trim(),i=id.getText().toString().trim(),x=pw.getText().toString(),y=rp.getText().toString();
      if(a.length()<3||a.length()>20){err.setText("Никнейм: 3–20 символов");return;} if(!i.matches("\\d{3,20}")){err.setText("ID: только цифры, 3–20");return;}
      if(x.length()<6){err.setText("Пароль: минимум 6 символов");return;} if(!x.equals(y)){err.setText("Пароли не совпадают");return;}
      p.edit().putString("nick",a).putString("id",i).putString("pass",sha(x)).putBoolean("logged",true).apply();menu();
    });back.setOnClickListener(v->welcome());sc.addView(r);setContentView(sc);}
  void login(){LinearLayout r=root();logo(r);r.addView(t("Вход",27,true),mw());EditText n=f("Имя / никнейм"),pw=f("Пароль");pw.setInputType(129);r.addView(n,mw());lp(r,pw,12);TextView err=t("",14,false);err.setTextColor(Color.rgb(255,140,140));lp(r,err,12);
    Button ok=b("🔐 Войти"),reg=b("📝 Регистрация");lp(r,ok,8);lp(r,reg,8);
    ok.setOnClickListener(v->{if(p.getString("nick","").equalsIgnoreCase(n.getText().toString().trim())&&p.getString("pass","").equals(sha(pw.getText().toString()))){p.edit().putBoolean("logged",true).apply();menu();}else err.setText("Неверный никнейм или пароль");});reg.setOnClickListener(v->register());setContentView(r);}
  void menu(){LinearLayout r=root();logo(r);LinearLayout.LayoutParams q=mw();q.topMargin=16;TextView h=t("👋 Привет, "+p.getString("nick","Игрок")+"!",23,true);r.addView(h,q);r.addView(t("Главное меню",16,false),mw());
    Button s=b("📊 Статистика Standoff 2"),pr=b("👤 Мой профиль"),api=b("⚙️ Настройки API"),out=b("🚪 Выйти");lp(r,s,22);lp(r,pr,12);lp(r,api,12);lp(r,out,24);
    s.setOnClickListener(v->stats());pr.setOnClickListener(v->profile());api.setOnClickListener(v->apiSettings());out.setOnClickListener(v->{p.edit().putBoolean("logged",false).apply();login();});setContentView(r);}
  void profile(){LinearLayout r=root();logo(r);r.addView(t("👤 Профиль",26,true),mw());card(r,"Никнейм",p.getString("nick","—"));card(r,"ID Standoff 2",p.getString("id","—"));Button back=b("← Назад");lp(r,back,18);back.setOnClickListener(v->menu());setContentView(r);}
  void apiSettings(){LinearLayout r=root();logo(r);r.addView(t("⚙️ Настройки API",26,true),mw());r.addView(t("Укажи адрес своего Ghostly API",15,false),mw());EditText e=f("http://192.168.1.100:8081");e.setText(p.getString("url",""));lp(r,e,14);Button save=b("💾 Сохранить"),test=b("🔎 Проверить"),back=b("← Назад");lp(r,save,10);lp(r,test,10);TextView st=t("",14,false);st.setTextColor(SOFT);lp(r,st,12);lp(r,back,18);
    save.setOnClickListener(v->{p.edit().putString("url",clean(e.getText().toString())).apply();st.setText("✅ Сохранено");});
    test.setOnClickListener(v->new Thread(()->{try{JSONObject j=get(clean(e.getText().toString())+"/api/health");runOnUiThread(()->st.setText(j.optBoolean("ok",false)?"✅ API доступен":"⚠️ API ответил с ошибкой"));}catch(Exception ex){runOnUiThread(()->st.setText("❌ API недоступен"));}}).start());
    back.setOnClickListener(v->menu());setContentView(r);}
  void stats(){LinearLayout r=root();logo(r);r.addView(t("📊 Standoff 2",27,true),mw());EditText id=f("ID игрока");id.setInputType(2);id.setText(p.getString("id",""));lp(r,id,16);Button go=b("🔎 Получить статистику"),back=b("← Назад");lp(r,go,10);TextView st=t("",14,false);st.setTextColor(SOFT);lp(r,st,12);LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(18,18,18,18);card.setBackground(bg(CARD,18));card.setVisibility(View.GONE);lp(r,card,12);lp(r,back,18);
    go.setOnClickListener(v->{String x=id.getText().toString().trim(),u=clean(p.getString("url",""));if(!x.matches("\\d{3,20}")){st.setText("❌ ID только из цифр");return;}if(u.isEmpty()){st.setText("⚠️ Сначала укажи URL API в настройках");return;}go.setEnabled(false);st.setText("🟣 Получаем данные...");new Thread(()->{try{JSONObject j=get(u+"/api/player/"+x);if(!j.optBoolean("ok",false))throw new Exception(j.optString("error","API error"));JSONObject d=j.getJSONObject("data");runOnUiThread(()->{card.removeAllViews();add(card,"👤 "+val(d,"nickname"));add(card,"🆔 ID: "+val(d,"id"));add(card,"⭐ Уровень: "+val(d,"level"));add(card,"🏆 Рейтинг: "+val(d,"rating"));add(card,"🎮 Матчи: "+val(d,"matches"));add(card,"🥇 Победы: "+val(d,"wins"));add(card,"📈 Winrate: "+val(d,"win_rate"));add(card,"⚔️ K/D: "+val(d,"kd"));add(card,"🎯 Точность: "+val(d,"accuracy"));card.setVisibility(View.VISIBLE);st.setText("✅ Данные получены");go.setEnabled(true);});}catch(Exception ex){runOnUiThread(()->{st.setText("❌ "+ex.getMessage());go.setEnabled(true);});}}).start();});
    back.setOnClickListener(v->menu());setContentView(r);}
  void add(LinearLayout r,String s){TextView x=t(s,16,false);x.setGravity(3);x.setTextColor(SOFT);x.setPadding(0,7,0,7);r.addView(x,mw());}
  void card(LinearLayout r,String n,String v){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(18,16,18,16);c.setBackground(bg(CARD,18));lp(r,c,12);add(c,n);add(c,v);}
  void lp(LinearLayout r,View v,int top){LinearLayout.LayoutParams q=mw();q.topMargin=top;r.addView(v,q);}
  String clean(String s){if(s==null)return "";s=s.trim();while(s.endsWith("/"))s=s.substring(0,s.length()-1);return s;}
  JSONObject get(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setRequestMethod("GET");c.setConnectTimeout(7000);c.setReadTimeout(12000);int code=c.getResponseCode();BufferedReader br=new BufferedReader(new InputStreamReader(code<400?c.getInputStream():c.getErrorStream()));StringBuilder z=new StringBuilder();String l;while((l=br.readLine())!=null)z.append(l);br.close();if(code<200||code>=300)throw new Exception("HTTP "+code);return new JSONObject(z.toString());}
  String val(JSONObject j,String k){String s=j.optString(k,"—");return s.equals("null")||s.isEmpty()?"—":s;}
  String sha(String s){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder x=new StringBuilder();for(byte q:b)x.append(String.format("%02x",q));return x.toString();}catch(Exception e){return "";}}
}