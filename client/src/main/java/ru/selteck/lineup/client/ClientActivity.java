package ru.selteck.lineup.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.VideoView;
import android.widget.MediaController;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ClientActivity extends Activity {
    private static final String POLICY_URL = "https://raw.githubusercontent.com/Selteck1/ApkGhostly/main/content/policy.json";
    private static final String CATALOG_URL = "https://raw.githubusercontent.com/Selteck1/ApkGhostly/main/content/catalog.json";
    private static final int BG=Color.rgb(10,14,27), SURFACE=Color.rgb(21,28,47), SURFACE2=Color.rgb(29,38,61);
    private static final int FG=Color.rgb(245,246,250), MUTED=Color.rgb(153,160,173), PURPLE=Color.rgb(255,207,48), MINT=Color.rgb(255,207,48);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable poll=new Runnable(){@Override public void run(){if(unlocked){checkPolicy(false);handler.postDelayed(this,60000L);}}};
    private LinearLayout root,page;
    private boolean unlocked=false, checking=false, firstCheck=true, forceLocked=false;
    private long latestRevision=0, minimumRevision=0;
    private String telegramUrl="https://t.me/";
    private final List<GuideBlock> blocks=new ArrayList<>();
    private final Map<String,byte[]> images=new HashMap<>();
    private final Map<String,byte[]> videos=new HashMap<>();
    private String lastQuery="",filterCategory="Все категории",filterMap="Все карты",filterSide="Любая сторона",filterPlant="Любой плент",filterGrenade="Любая граната";

    private static final class GuideBlock {
        String id,title,map,category,side,plant,grenadeType,description;
        final List<String> photoFiles=new ArrayList<>();
        final List<String> videoFiles=new ArrayList<>();
    }
    private static final class RemoteContent {
        final List<GuideBlock> blocks=new ArrayList<>();
        final Map<String,byte[]> images=new HashMap<>();
        final Map<String,byte[]> videos=new HashMap<>();
        long revision=0;
    }

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        Window w=getWindow();w.setStatusBarColor(Color.rgb(9,13,24));w.setNavigationBarColor(Color.rgb(9,13,24));
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);setContentView(root);
        loadBundledContent();
        renderGate("Проверяю доступ и версию базы…",null);
        checkPolicy(true);
    }
    @Override protected void onResume(){super.onResume();if(unlocked){handler.removeCallbacks(poll);handler.postDelayed(poll,15000L);}}
    @Override protected void onPause(){handler.removeCallbacks(poll);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacks(poll);worker.shutdownNow();super.onDestroy();}

    private int dp(float v){return(int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private GradientDrawable shape(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void shell(String title,String subtitle){
        root.removeAllViews();
        LinearLayout header=row();header.setPadding(dp(16),dp(10),dp(16),dp(10));header.setBackgroundColor(Color.rgb(9,13,24));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_launcher);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);header.addView(logo,lp(42,42));
        LinearLayout titles=column();titles.setPadding(dp(12),0,0,0);titles.addView(text(title,20,FG,true));titles.addView(text(subtitle,11,MUTED,false));
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));root.addView(header,lp(-1,66));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        page=column();page.setPadding(dp(17),dp(18),dp(17),dp(24));scroll.addView(page,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void addText(String value,int size,int color,boolean bold,int top,int bottom){
        TextView t=text(value,size,color,bold);LinearLayout.LayoutParams p=lp(-1,-2);p.topMargin=dp(top);p.bottomMargin=dp(bottom);page.addView(t,p);
    }
    private void addCard(View child){
        LinearLayout wrap=column();wrap.setPadding(dp(14),dp(14),dp(14),dp(14));wrap.setBackground(shape(SURFACE,Color.rgb(39,49,75),18));wrap.addView(child);
        LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=dp(10);page.addView(wrap,p);
    }
    private void addButton(String label,Runnable action,boolean primary){
        TextView b=text(label,14,primary?Color.rgb(10,15,29):FG,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(15),dp(12),dp(15),dp(12));b.setMinHeight(dp(46));
        b.setBackground(shape(primary?MINT:SURFACE2,primary?0:Color.rgb(48,61,91),14));b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=dp(9);page.addView(b,p);
    }
    private EditText field(String hint,String value){
        EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setText(value==null?"":value);e.setTextColor(FG);e.setHintTextColor(MUTED);e.setTextSize(14);
        e.setPadding(dp(13),dp(12),dp(13),dp(12));e.setBackground(shape(SURFACE2,Color.rgb(55,66,97),13));
        LinearLayout.LayoutParams p=lp(-1,50);p.bottomMargin=dp(10);page.addView(e,p);return e;
    }
    private void renderGate(String message,String detail){
        unlocked=false;handler.removeCallbacks(poll);shell("LINEUP","СПРАВОЧНИК STANDOFF 2");
        addText(forceLocked?"Эта версия больше не поддерживается":"Проверка версии приложения",23,FG,true,3,8);
        addText("Блоки и фотографии загружаются из бесплатного каталога GitHub. Новые материалы появляются автоматически, без установки нового APK.",14,MUTED,false,0,16);
        addCard(text(message,14,forceLocked?Color.rgb(255,130,150):MINT,true));
        if(detail!=null&&!detail.trim().isEmpty())addText(detail,12,Color.rgb(255,160,170),false,2,12);
        addButton("Проверить ещё раз",()->checkPolicy(true),true);
        addButton("Обновить через Telegram",this::openTelegram,false);
        addText("Для загрузки свежей базы нужен интернет. После синхронизации материалы доступны до закрытия приложения.",11,MUTED,false,12,0);
    }

    private void checkPolicy(boolean showProgress){
        if(checking)return;checking=true;
        if(showProgress)renderGate("Подключаюсь и проверяю политику обновлений…",null);
        worker.execute(()->{
            String error=null;boolean locked=false;long remoteLatest=0,remoteMinimum=0;
            String remoteTelegram="https://t.me/";
            RemoteContent fetched=new RemoteContent();
            try{
                JSONObject policy=new JSONObject(new String(download(POLICY_URL,512*1024),StandardCharsets.UTF_8));
                JSONObject catalog=new JSONObject(new String(download(CATALOG_URL,2*1024*1024),StandardCharsets.UTF_8));
                remoteMinimum=policy.optLong("minClientRevision",0);
                remoteLatest=catalog.optLong("revision",0);
                remoteTelegram=policy.optString("telegramUrl","https://t.me/");
                if(!validTelegram(remoteTelegram))remoteTelegram="https://t.me/";
                if(catalog.optInt("schemaVersion",0)!=1||policy.optInt("schemaVersion",0)!=1)
                    throw new IllegalArgumentException("Формат каталога не поддерживается.");
                locked=BuildConfig.CONTENT_REVISION<remoteMinimum;
                if(!locked)fetched=loadRemoteContent(catalog);
                fetched.revision=remoteLatest;
            }catch(Exception ex){
                error=ex.getMessage()==null?"Не удалось проверить версию":ex.getMessage();
            }
            final String failure=error,link=remoteTelegram;
            final long latest=remoteLatest,minimum=remoteMinimum;
            final boolean isLocked=locked;
            final RemoteContent content=fetched;
            runOnUiThread(()->{
                checking=false;firstCheck=false;
                if(failure!=null){
                    renderGate("Не удалось загрузить актуальную базу.",failure+"\nПодключись к интернету и повтори проверку.");
                    return;
                }
                boolean refreshScreen=showProgress||!unlocked||latest!=latestRevision||isLocked;
                latestRevision=latest;minimumRevision=minimum;telegramUrl=link;
                if(isLocked){
                    forceLocked=true;blocks.clear();images.clear();
                    renderGate("Доступ заблокирован администратором. Установи разрешённую версию приложения.",
                        "Ревизия приложения: "+BuildConfig.CONTENT_REVISION+" · Минимальная разрешённая ревизия: "+minimum);
                    return;
                }
                forceLocked=false;
                blocks.clear();blocks.addAll(content.blocks);
                images.clear();images.putAll(content.images);
                videos.clear();videos.putAll(content.videos);
                unlocked=true;if(refreshScreen)showList("");
            });
        });
    }

    private RemoteContent loadRemoteContent(JSONObject catalog)throws Exception{
        RemoteContent result=new RemoteContent();
        result.revision=catalog.optLong("revision",0);
        JSONArray packs=catalog.optJSONArray("packs");
        if(packs==null)throw new IllegalArgumentException("В каталоге отсутствует список пакетов.");
        for(int i=0;i<packs.length();i++){
            JSONObject item=packs.getJSONObject(i);
            String id=item.optString("id",""),file=item.optString("file",""),expected=item.optString("sha256","").toLowerCase(Locale.ROOT);
            if(!id.matches("[A-Za-z0-9_-]{1,100}"))throw new IllegalArgumentException("Неправильный ID пакета.");
            if(!file.startsWith("content/packs/")||file.contains("..")||file.startsWith("/"))
                throw new IllegalArgumentException("Каталог содержит недопустимый путь пакета.");
            if(!expected.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Неправильная контрольная сумма пакета "+id);
            byte[] packageBytes=loadCachedPack(id,file,expected);
            addPackage(result,id,packageBytes);
        }
        return result;
    }

    private byte[] loadCachedPack(String id,String file,String expected)throws Exception{
        java.io.File cacheDir=new java.io.File(getFilesDir(),"remote_packs");
        if(!cacheDir.isDirectory()&&!cacheDir.mkdirs())throw new IllegalStateException("Не удалось создать кеш базы.");
        java.io.File cached=new java.io.File(cacheDir,id+".lineup");
        if(cached.isFile()){
            try{
                byte[] local=readFileLimited(cached,128*1024*1024);
                if(expected.equals(sha256(local)))return local;
            }catch(Exception ignored){}
        }
        String address="https://raw.githubusercontent.com/Selteck1/ApkGhostly/main/"+file.replace(" ","%20");
        byte[] remote=download(address+"?nocache="+System.currentTimeMillis(),128*1024*1024);
        if(!expected.equals(sha256(remote)))throw new IllegalArgumentException("Не совпала контрольная сумма пакета "+id+". Повтори проверку.");
        java.io.File temp=new java.io.File(cacheDir,id+".tmp");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(temp)){out.write(remote);out.getFD().sync();}
        if(cached.exists()&&!cached.delete()){temp.delete();throw new IllegalStateException("Не удалось заменить кеш пакета.");}
        if(!temp.renameTo(cached)){temp.delete();throw new IllegalStateException("Не удалось сохранить обновлённую базу.");}
        return remote;
    }

    private void addPackage(RemoteContent target,String packId,byte[] bytes)throws Exception{
        Map<String,byte[]> entries=new HashMap<>();
        int total=0;
        try(ZipInputStream zip=new ZipInputStream(new java.io.ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){
            ZipEntry entry;byte[] buffer=new byte[32768];
            while((entry=zip.getNextEntry())!=null){
                if(entry.isDirectory())continue;
                String name=entry.getName();
                if(name.startsWith("/")||name.contains("..")||!(name.equals("manifest.json")||name.startsWith("images/")))
                    throw new IllegalArgumentException("В пакете найден недопустимый путь.");
                ByteArrayOutputStream out=new ByteArrayOutputStream();int n;
                while((n=zip.read(buffer))!=-1){
                    total+=n;if(total>128*1024*1024)throw new IllegalArgumentException("Распакованный пакет слишком большой.");
                    out.write(buffer,0,n);
                }
                entries.put(name,out.toByteArray());zip.closeEntry();
            }
        }
        byte[] manifestBytes=entries.get("manifest.json");
        if(manifestBytes==null)throw new IllegalArgumentException("В пакете "+packId+" нет manifest.json.");
        JSONObject manifest=new JSONObject(new String(manifestBytes,StandardCharsets.UTF_8));
        if(!"lineup".equals(manifest.optString("format"))||manifest.optInt("schemaVersion",0)!=1)
            throw new IllegalArgumentException("Неподдерживаемый формат пакета "+packId+".");
        JSONArray data=manifest.optJSONArray("blocks");
        if(data==null||data.length()>5000)throw new IllegalArgumentException("Неправильный список блоков в пакете "+packId+".");
        for(int i=0;i<data.length();i++){
            JSONObject item=data.getJSONObject(i);GuideBlock b=new GuideBlock();
            b.id=item.optString("id",packId+"-"+i);
            b.title=item.optString("title","Без названия");
            b.map=item.optString("map","");b.category=item.optString("category","Раскидка");
            b.side=item.optString("side","Атака");b.plant=item.optString("plant","");b.grenadeType=item.optString("grenadeType","");b.description=item.optString("description","");
            JSONArray photos=item.optJSONArray("photos");
            if(photos!=null){
                if(photos.length()>12)throw new IllegalArgumentException("В блоке больше 12 фото.");
                for(int p=0;p<photos.length();p++){
                    String file=photos.getJSONObject(p).optString("file","");
                    if(!file.startsWith("images/")||file.contains(".."))throw new IllegalArgumentException("Неправильный путь фотографии.");
                    byte[] image=entries.get(file);
                    if(image==null||image.length==0||image.length>20*1024*1024)throw new IllegalArgumentException("В пакете отсутствует фотография "+file+".");
                    String localKey=packId+"/"+file;
                    b.photoFiles.add(localKey);target.images.put(localKey,image);
                }
            }
            JSONArray videosArray=item.optJSONArray("videos");
            if(videosArray!=null){
                if(videosArray.length()>4)throw new IllegalArgumentException("В блоке больше 4 видео.");
                for(int p=0;p<videosArray.length();p++){
                    String file=videosArray.getJSONObject(p).optString("file","");
                    if(!file.startsWith("videos/")||file.contains(".."))throw new IllegalArgumentException("Неправильный путь видео.");
                    byte[] video=entries.get(file);
                    if(video==null||video.length==0||video.length>40*1024*1024)throw new IllegalArgumentException("Видео отсутствует или слишком большое: "+file);
                    String localKey=packId+"/"+file;b.videoFiles.add(localKey);target.videos.put(localKey,video);
                }
            }
            target.blocks.add(b);
        }
    }

    private String sha256(byte[] bytes)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out=new StringBuilder();
        for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }

    private byte[] readFileLimited(java.io.File file,int max)throws Exception{
        try(InputStream in=new java.io.FileInputStream(file)){return readLimited(in,max);}
    }

    private byte[] readLimited(InputStream in,int max)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[32768];int total=0,n;
        while((n=in.read(buffer))!=-1){total+=n;if(total>max)throw new IllegalArgumentException("Файл слишком большой.");out.write(buffer,0,n);}
        return out.toByteArray();
    }

    private void loadBundledContent(){
        blocks.clear();images.clear();videos.clear();
        try(InputStream raw=getAssets().open("community.lineup");ZipInputStream zip=new ZipInputStream(raw,StandardCharsets.UTF_8)){
            Map<String,byte[]> entries=new HashMap<>();int total=0;ZipEntry entry;byte[] buffer=new byte[32768];
            while((entry=zip.getNextEntry())!=null){
                if(entry.isDirectory())continue;
                String name=entry.getName();
                if(name.startsWith("/")||name.contains(".."))throw new IllegalArgumentException("Недопустимый путь в пакете.");
                ByteArrayOutputStream out=new ByteArrayOutputStream();int read;
                while((read=zip.read(buffer))!=-1){total+=read;if(total>128*1024*1024)throw new IllegalArgumentException("Пакет базы слишком большой.");out.write(buffer,0,read);}
                entries.put(name,out.toByteArray());zip.closeEntry();
            }
            byte[] manifestBytes=entries.get("manifest.json");if(manifestBytes==null)throw new IllegalArgumentException("В пакете нет manifest.json.");
            JSONObject manifest=new JSONObject(new String(manifestBytes,StandardCharsets.UTF_8));
            if(!"lineup".equals(manifest.optString("format"))||manifest.optInt("schemaVersion",0)!=1)throw new IllegalArgumentException("Неверный формат пакета.");
            JSONArray data=manifest.optJSONArray("blocks");if(data==null||data.length()>5000)throw new IllegalArgumentException("Неверный список блоков.");
            for(int i=0;i<data.length();i++){
                JSONObject item=data.getJSONObject(i);GuideBlock b=new GuideBlock();
                b.id=item.optString("id","block-"+i);b.title=item.optString("title","Без названия");
                b.map=item.optString("map","");b.category=item.optString("category","Раскидка");
                b.side=item.optString("side","Атака");b.plant=item.optString("plant","");b.grenadeType=item.optString("grenadeType","");b.description=item.optString("description","");
                JSONArray photos=item.optJSONArray("photos");
                if(photos!=null)for(int p=0;p<Math.min(photos.length(),12);p++){
                    String file=photos.getJSONObject(p).optString("file","");
                    if(file.startsWith("images/")&&entries.containsKey(file))b.photoFiles.add(file);
                }
                JSONArray vids=item.optJSONArray("videos");
                if(vids!=null)for(int p=0;p<Math.min(vids.length(),4);p++){
                    String file=vids.getJSONObject(p).optString("file","");
                    if(file.startsWith("videos/")&&entries.containsKey(file)){b.videoFiles.add(file);videos.put(file,entries.get(file));}
                }
                blocks.add(b);
            }
            images.putAll(entries);
        }catch(Exception e){
            blocks.clear();images.clear();videos.clear();
        }
    }

    private void showList(String query){
        if(!unlocked){renderGate("Проверь актуальность базы перед просмотром.",null);return;}
        lastQuery=query==null?"":query.trim();
        shell("Lineup","СПРАВОЧНИК STANDOFF 2  ·  v"+latestRevision);
        addText("Тактики под рукой.",27,FG,true,0,4);
        addText("Свежие раскидки, схемы и видео — без лишнего шума.",13,MUTED,false,0,14);
        EditText search=field("Найти по названию, карте или описанию",lastQuery);
        addButton("⌕  Поиск",()->showList(search.getText().toString().trim()),true);
        addButton("⚙  Фильтры"+(filtersActive()?" · включены":""),this::showFilterDialog,false);
        addText("ПОСЛЕДНИЕ МАТЕРИАЛЫ",12,PURPLE,true,10,8);
        int shown=0;
        for(GuideBlock b:blocks){if(!matchesFilters(b,lastQuery))continue;addBlockCard(b);shown++;}
        if(shown==0)addCard(text(blocks.isEmpty()?"Пока нет опубликованных материалов. Новые блоки появятся после публикации администратором.":"Ничего не найдено. Попробуй изменить запрос или фильтры.",14,MUTED,false));
        else addText(shown+" материалов · прокручивай вниз до конца базы",11,MUTED,false,8,0);
        addButton("Проверить обновления",()->checkPolicy(true),false);
        addButton("Открыть Telegram",this::openTelegram,false);
    }

    private boolean filtersActive(){return !"Все категории".equals(filterCategory)||!"Все карты".equals(filterMap)||!"Любая сторона".equals(filterSide)||!"Любой плент".equals(filterPlant)||!"Любая граната".equals(filterGrenade);}
    private boolean matchesFilters(GuideBlock b,String query){
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        String hay=(safe(b.title)+" "+safe(b.map)+" "+safe(b.category)+" "+safe(b.side)+" "+safe(b.plant)+" "+safe(b.grenadeType)+" "+safe(b.description)).toLowerCase(Locale.ROOT);
        if(!q.isEmpty()&&!hay.contains(q))return false;
        if(!"Все категории".equals(filterCategory)&&!filterCategory.equalsIgnoreCase(safe(b.category)))return false;
        if(!"Все карты".equals(filterMap)&&!filterMap.equalsIgnoreCase(safe(b.map)))return false;
        if(!"Любая сторона".equals(filterSide)&&!filterSide.equalsIgnoreCase(safe(b.side)))return false;
        if(!"Любой плент".equals(filterPlant)&&!filterPlant.equalsIgnoreCase(safe(b.plant)))return false;
        if(!"Любая граната".equals(filterGrenade)&&!filterGrenade.equalsIgnoreCase(safe(b.grenadeType)))return false;
        return true;
    }
    private String safe(String value){return value==null?"":value;}

    private Spinner makeFilterSpinner(LinearLayout holder,String label,String[] values,String selected){
        TextView title=text(label,12,MUTED,true);LinearLayout.LayoutParams tp=lp(-1,-2);tp.topMargin=dp(8);tp.bottomMargin=dp(4);holder.addView(title,tp);
        Spinner spinner=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,values){
            @Override public View getView(int position,View convertView,ViewGroup parent){TextView t=(TextView)super.getView(position,convertView,parent);t.setTextColor(FG);t.setTextSize(14);t.setPadding(dp(12),dp(6),dp(12),dp(6));return t;}
            @Override public View getDropDownView(int position,View convertView,ViewGroup parent){TextView t=(TextView)super.getDropDownView(position,convertView,parent);t.setTextColor(FG);t.setTextSize(14);t.setBackgroundColor(SURFACE2);t.setPadding(dp(14),dp(12),dp(14),dp(12));return t;}
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);spinner.setBackground(shape(SURFACE2,Color.rgb(51,56,68),13));
        for(int i=0;i<values.length;i++)if(values[i].equalsIgnoreCase(selected)){spinner.setSelection(i);break;}
        LinearLayout.LayoutParams p=lp(-1,48);p.bottomMargin=dp(5);holder.addView(spinner,p);return spinner;
    }

    private void showFilterDialog(){
        LinearLayout form=column();form.setPadding(dp(18),dp(8),dp(18),dp(6));
        Spinner category=makeFilterSpinner(form,"Категория",new String[]{"Все категории","Раскидка","Тактика","Командная схема"},filterCategory);
        Spinner map=makeFilterSpinner(form,"Карта",new String[]{"Все карты","Duna","Sandstone","Province","Prison","Rust","Hanami","Breeze"},filterMap);
        Spinner side=makeFilterSpinner(form,"Сторона",new String[]{"Любая сторона","Атака","Оборона"},filterSide);
        Spinner plant=makeFilterSpinner(form,"Плент",new String[]{"Любой плент","Плент A","Плент B"},filterPlant);
        Spinner grenade=makeFilterSpinner(form,"Граната",new String[]{"Любая граната","Хае","Молотов","Флеш","Смок"},filterGrenade);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Фильтры материалов").setView(form).setNegativeButton("Отмена",null).setNeutralButton("Сбросить",null).setPositiveButton("Показать",null).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(PURPLE);dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(MUTED);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                filterCategory=category.getSelectedItem().toString();filterMap=map.getSelectedItem().toString();filterSide=side.getSelectedItem().toString();
                filterPlant=plant.getSelectedItem().toString();filterGrenade=grenade.getSelectedItem().toString();dialog.dismiss();showList(lastQuery);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                filterCategory="Все категории";filterMap="Все карты";filterSide="Любая сторона";filterPlant="Любой плент";filterGrenade="Любая граната";dialog.dismiss();showList(lastQuery);
            });
        });
        dialog.show();
    }

    private void addBlockCard(GuideBlock b){
        LinearLayout content=column();
        if(!b.photoFiles.isEmpty()){
            byte[] bytes=images.get(b.photoFiles.get(0));
            if(bytes!=null){Bitmap preview=decodeBytes(bytes,920,420);if(preview!=null){ImageView image=new ImageView(this);image.setImageBitmap(preview);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(shape(SURFACE2,0,14));LinearLayout.LayoutParams ip=lp(-1,168);ip.bottomMargin=dp(12);content.addView(image,ip);}}
        }
        LinearLayout titleRow=row();TextView name=text(b.title,16,FG,true);titleRow.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        titleRow.addView(text("▧ "+b.photoFiles.size()+"  ▶ "+b.videoFiles.size(),11,PURPLE,true));content.addView(titleRow);
        TextView meta=text(join(b.map,b.category,b.side,b.plant,b.grenadeType),11,PURPLE,true);LinearLayout.LayoutParams mp=lp(-1,-2);mp.topMargin=dp(6);content.addView(meta,mp);
        TextView desc=text(b.description.isEmpty()?"Описание не добавлено":b.description,13,MUTED,false);desc.setMaxLines(3);LinearLayout.LayoutParams dpv=lp(-1,-2);dpv.topMargin=dp(8);content.addView(desc,dpv);
        addCard(content);content.setClickable(true);content.setOnClickListener(v->showDetail(b));
    }

    private String join(String... values){ArrayList<String> list=new ArrayList<>();for(String value:values)if(value!=null&&!value.trim().isEmpty())list.add(value.trim());return android.text.TextUtils.join("  ·  ",list);}

    private void showDetail(GuideBlock b){
        if(!unlocked){renderGate("Эта версия приложения заблокирована.",null);return;}
        shell(b.title,join(b.map,b.category,b.side,b.plant,b.grenadeType));
        addText(b.description.isEmpty()?"Описание не добавлено.":b.description,15,FG,false,4,16);
        if(!b.photoFiles.isEmpty()){
            addText("ФОТО · "+b.photoFiles.size(),12,PURPLE,true,0,9);int index=1;
            for(String file:b.photoFiles){byte[] bytes=images.get(file);if(bytes==null)continue;Bitmap bitmap=decodeBytes(bytes,1800,1800);if(bitmap==null)continue;
                ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackground(shape(SURFACE,0,14));
                LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=dp(5);page.addView(image,p);image.setOnClickListener(v->showImageFull(bytes));
                addText("Фото "+index+" · нажми, чтобы увеличить",11,MUTED,false,0,11);index++;
            }
        }else addCard(text("Фотографии не добавлены.",13,MUTED,false));
        if(!b.videoFiles.isEmpty()){
            addText("ВИДЕО · "+b.videoFiles.size(),12,PURPLE,true,12,9);int index=1;
            for(String file:b.videoFiles){byte[] bytes=videos.get(file);if(bytes==null)continue;addText("▶ Ролик "+index,13,FG,true,0,5);
                VideoView player=new VideoView(this);player.setBackground(shape(SURFACE2,0,13));LinearLayout.LayoutParams p=lp(-1,224);p.bottomMargin=dp(14);page.addView(player,p);
                try{File fileOnDisk=cacheVideo(bytes,file);player.setVideoURI(Uri.fromFile(fileOnDisk));MediaController controls=new MediaController(this);controls.setAnchorView(player);player.setMediaController(controls);}
                catch(Exception ex){addText("Не удалось подготовить видео для воспроизведения.",12,Color.rgb(255,130,150),false,0,8);}index++;
            }
        }
        addButton("← Назад к материалам",()->showList(lastQuery),false);
    }

    private Bitmap decodeBytes(byte[] bytes,int reqW,int reqH){
        try{BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);int sample=1;while(bounds.outWidth/sample>reqW||bounds.outHeight/sample>reqH)sample*=2;BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=sample;opts.inPreferredConfig=Bitmap.Config.RGB_565;return BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);}catch(Throwable ignored){return null;}
    }

    private void showImageFull(byte[] bytes){
        Bitmap bitmap=decodeBytes(bytes,2400,2400);if(bitmap==null)return;
        Dialog dialog=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);ImageView image=new ImageView(this);
        image.setBackgroundColor(Color.BLACK);image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.FIT_CENTER);dialog.setContentView(image);image.setOnClickListener(v->dialog.dismiss());dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.black);dialog.getWindow().setLayout(-1,-1);}
    }

    private File cacheVideo(byte[] bytes,String name)throws Exception{
        File dir=new File(getCacheDir(),"lineup_videos");if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("Не удалось создать видеокеш");
        String ext=".mp4";if(name!=null){String lower=name.toLowerCase(Locale.ROOT);if(lower.endsWith(".webm"))ext=".webm";else if(lower.endsWith(".mov"))ext=".mov";else if(lower.endsWith(".3gp"))ext=".3gp";}
        File f=new File(dir,sha256(bytes)+ext);if(!f.isFile())try(FileOutputStream out=new FileOutputStream(f)){out.write(bytes);}return f;
    }

    private void openTelegram(){
        String url=validTelegram(telegramUrl)?telegramUrl:"https://t.me/";
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){Toast.makeText(this,"Не удалось открыть Telegram-ссылку: "+url,Toast.LENGTH_LONG).show();}
    }
    private boolean validTelegram(String url){
        try{Uri u=Uri.parse(url);String host=u.getHost();return "https".equalsIgnoreCase(u.getScheme())&&host!=null&&(host.equalsIgnoreCase("t.me")||host.equalsIgnoreCase("www.t.me")||host.equalsIgnoreCase("telegram.me")||host.equalsIgnoreCase("www.telegram.me"));}
        catch(Exception ignored){return false;}
    }
    private byte[] download(String address,int max)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(address+"?nocache="+System.currentTimeMillis()).openConnection();
        c.setRequestMethod("GET");c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setInstanceFollowRedirects(true);c.setUseCaches(false);
        c.setRequestProperty("Cache-Control","no-cache");c.setRequestProperty("User-Agent","Lineup-Client/"+BuildConfig.VERSION_NAME);
        try{
            int status=c.getResponseCode();if(status<200||status>=300)throw new IllegalStateException("Сервер ответил HTTP "+status);
            try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int total=0,n;
                while((n=in.read(b))!=-1){total+=n;if(total>max)throw new IllegalArgumentException("Ответ сервера слишком большой.");out.write(b,0,n);}return out.toByteArray();}
        }finally{c.disconnect();}
    }
}
