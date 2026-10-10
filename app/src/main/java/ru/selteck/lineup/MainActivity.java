package ru.selteck.lineup;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.VideoView;
import android.widget.MediaController;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_PHOTOS=201,PICK_IMPORT=202,PICK_EXPORT=203,PICK_UPDATE=204,PICK_VIDEOS=205;
    private static final String CATALOG_URL="https://raw.githubusercontent.com/Selteck1/ApkGhostly/main/content/catalog.json";
    private static final int BG=Color.rgb(10,14,27),SURFACE=Color.rgb(21,28,47),SURFACE2=Color.rgb(29,38,61);
    private static final int FG=Color.rgb(245,246,250),MUTED=Color.rgb(153,160,173),PURPLE=Color.rgb(255,207,48),MINT=Color.rgb(255,207,48);
    private AppDb db;private SharedPreferences prefs;private LinearLayout root,page;private ScrollView scroll;private boolean adminSession=false;
    private String sessionAdminPassword="";
    private Block draft;private ArrayList<String> draftPhotos=new ArrayList<>(),draftVideos=new ArrayList<>();private EditText edTitle,edDescription;private Spinner edMap,edCategory,edSide,edPlant,edGrenade;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean databaseUnlocked=false, checkingUpdates=false, checkingRevision=false;
    private final Handler gateHandler=new Handler(Looper.getMainLooper());
    private final Runnable gatePoll=new Runnable(){@Override public void run(){if(!databaseUnlocked)return;pollCatalogRevision();gateHandler.postDelayed(this,60000L);}};

    @Override protected void onCreate(Bundle state){super.onCreate(state);Window w=getWindow();w.setStatusBarColor(Color.rgb(9,13,24));w.setNavigationBarColor(Color.rgb(9,13,24));w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        db=new AppDb(this);prefs=getSharedPreferences("lineup_settings",MODE_PRIVATE);root=new InsetRoot(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);setContentView(root);showAdminLogin();}
    @Override protected void onResume(){super.onResume();if(databaseUnlocked){gateHandler.removeCallbacks(gatePoll);gateHandler.postDelayed(gatePoll,15000L);}}
    @Override protected void onPause(){gateHandler.removeCallbacks(gatePoll);super.onPause();}
    @Override protected void onDestroy(){gateHandler.removeCallbacks(gatePoll);worker.shutdownNow();db.close();super.onDestroy();}
    private int dp(float v){return(int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private GradientDrawable shape(int color,int border,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(border!=0)d.setStroke(dp(1),border);return d;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void gap(int h){page.addView(new View(this),lp(1,h));}
    private void shell(String title,String sub,boolean back,Runnable action){
        root.removeAllViews();LinearLayout header=row();header.setPadding(dp(16),dp(10),dp(16),dp(10));header.setBackgroundColor(Color.rgb(9,13,24));
        if(back){TextView b=text("‹",32,FG,false);b.setGravity(Gravity.CENTER);header.addView(b,lp(42,46));b.setOnClickListener(v->action.run());}
        else{ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_launcher);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);header.addView(logo,lp(42,42));}
        LinearLayout titles=column();titles.setPadding(dp(12),0,0,0);titles.addView(text(title,20,FG,true));titles.addView(text(sub,11,MUTED,false));header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));root.addView(header,lp(-1,66));
        scroll=new ScrollView(this);scroll.setFillViewport(true);page=column();page.setPadding(dp(17),dp(18),dp(17),dp(22));scroll.addView(page,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private TextView button(String label,Runnable action,boolean primary){TextView b=text(label,14,primary?Color.rgb(10,15,29):FG,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(15),dp(12),dp(15),dp(12));b.setMinHeight(dp(46));b.setBackground(shape(primary?MINT:SURFACE2,primary?0:Color.rgb(48,61,91),14));b.setClickable(true);b.setFocusable(true);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=dp(9);b.setLayoutParams(p);return b;}
    private void addButton(String label,Runnable action,boolean primary){page.addView(button(label,action,primary));}
    private void addText(String value,int size,int color,boolean bold,int top,int bottom){TextView t=text(value,size,color,bold);LinearLayout.LayoutParams p=lp(-1,-2);p.topMargin=dp(top);p.bottomMargin=dp(bottom);page.addView(t,p);}
    private void addCard(View child){LinearLayout wrap=column();wrap.setPadding(dp(14),dp(14),dp(14),dp(14));wrap.setBackground(shape(SURFACE,Color.rgb(39,49,75),18));wrap.addView(child);LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=dp(10);page.addView(wrap,p);}
    private EditText field(String hint,String value,boolean multi){EditText e=new EditText(this);e.setSingleLine(!multi);e.setHint(hint);e.setText(value==null?"":value);e.setTextColor(FG);e.setHintTextColor(MUTED);e.setTextSize(15);e.setPadding(dp(13),dp(12),dp(13),dp(12));e.setBackground(shape(SURFACE2,Color.rgb(55,66,97),13));
        if(multi){e.setGravity(Gravity.TOP|Gravity.START);e.setMinLines(4);e.setMaxLines(12);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);}else e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams p=lp(-1,multi?124:50);p.bottomMargin=dp(10);page.addView(e,p);return e;}
    private Spinner makeDropdown(String[] values,String selected){
        Spinner spinner=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,values){
            @Override public View getView(int position,View convertView,ViewGroup parent){TextView t=(TextView)super.getView(position,convertView,parent);t.setTextColor(FG);t.setTextSize(14);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(12),dp(7),dp(12),dp(7));return t;}
            @Override public View getDropDownView(int position,View convertView,ViewGroup parent){TextView t=(TextView)super.getDropDownView(position,convertView,parent);t.setTextColor(FG);t.setTextSize(14);t.setBackgroundColor(SURFACE2);t.setPadding(dp(14),dp(12),dp(14),dp(12));return t;}
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);spinner.setBackground(shape(SURFACE2,Color.rgb(50,55,67),13));
        if(selected!=null)for(int i=0;i<values.length;i++)if(values[i].equalsIgnoreCase(selected)){spinner.setSelection(i);break;}
        LinearLayout.LayoutParams p=lp(-1,50);p.bottomMargin=dp(10);page.addView(spinner,p);return spinner;
    }
    private void showHome(){
        if(adminSession)showAdminMenu();else showAdminLogin();
    }
    private void showList(String query){shell("Блоки",query==null||query.isEmpty()?"ВСЯ СОХРАНЁННАЯ БАЗА":"РЕЗУЛЬТАТЫ ПОИСКА",true,this::showHome);EditText search=field("Поиск по названию, карте или описанию",query,false);addButton("Найти",()->showList(search.getText().toString().trim()),true);
        addText("Быстрые фильтры",16,FG,true,4,8);addButton("Все материалы",()->showList(""),false);addButton("Раскидки",()->showList("Раскидка"),false);addButton("Тактики",()->showList("Тактика"),false);addButton("Командные схемы",()->showList("Команда"),false);addText("Материалы",16,FG,true,8,8);
        List<Block> list=db.list(query);if(list.isEmpty())addCard(text("Ничего не найдено. Попробуй другой запрос или создай первый блок.",14,MUTED,false));else for(Block b:list)addBlockCard(b);}
    private String meta(Block b){ArrayList<String> parts=new ArrayList<>();if(b.map!=null&&!b.map.trim().isEmpty())parts.add(b.map.trim());if(b.category!=null&&!b.category.trim().isEmpty())parts.add(b.category.trim());if(b.side!=null&&!b.side.trim().isEmpty())parts.add(b.side.trim());if(b.plant!=null&&!b.plant.trim().isEmpty())parts.add(b.plant.trim());if(b.grenadeType!=null&&!b.grenadeType.trim().isEmpty())parts.add(b.grenadeType.trim());return android.text.TextUtils.join("  ·  ",parts);}
    private void addBlockCard(Block b){LinearLayout c=column();LinearLayout top=row();top.addView(text(b.title,16,FG,true),new LinearLayout.LayoutParams(0,-2,1));top.addView(text("▧ "+b.photos.size()+"   ▶ "+b.videos.size(),12,MINT,true));c.addView(top);LinearLayout.LayoutParams mp=lp(-1,-2);mp.topMargin=dp(6);c.addView(text(meta(b),11,PURPLE,true),mp);
        TextView desc=text(b.description.isEmpty()?"Без описания":b.description,13,MUTED,false);desc.setMaxLines(2);LinearLayout.LayoutParams dpv=lp(-1,-2);dpv.topMargin=dp(6);c.addView(desc,dpv);addCard(c);c.setClickable(true);c.setOnClickListener(v->showDetail(b.rowId));}
    private void showDetail(long id){
        Block b=db.get(id);if(b==null){showList("");return;}
        shell(b.title,meta(b),true,this::showHome);
        addText(b.description.isEmpty()?"Описание пока не добавлено.":b.description,15,FG,false,4,16);
        if(b.photos.isEmpty())addCard(text("Фотографии не добавлены.",13,MUTED,false));
        else{
            addText("Фото и порядок выполнения · "+b.photos.size(),15,FG,true,0,9);int index=1;
            for(String path:b.photos){
                File f=new File(path);if(!f.isFile()){index++;continue;}
                ImageView image=new ImageView(this);image.setBackground(shape(SURFACE,0,14));image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                Bitmap bm=decodeSampled(path,1100,1100);if(bm!=null)image.setImageBitmap(bm);
                LinearLayout.LayoutParams p=lp(-1,240);p.bottomMargin=dp(5);page.addView(image,p);
                image.setOnClickListener(v->showImageFull(path));addText("Фото "+index+" · нажми, чтобы открыть",11,MUTED,false,0,12);index++;
            }
        }
        if(!b.videos.isEmpty()){
            addText("Видео · "+b.videos.size(),15,FG,true,0,9);int index=1;
            for(String path:b.videos){File f=new File(path);if(!f.isFile())continue;addText("▶ Видео "+index+" · "+f.getName(),12,PURPLE,true,0,4);
                VideoView player=new VideoView(this);player.setVideoPath(path);MediaController controls=new MediaController(this);controls.setAnchorView(player);player.setMediaController(controls);
                LinearLayout.LayoutParams vp=lp(-1,220);vp.bottomMargin=dp(14);page.addView(player,vp);index++;
            }
        }
        if(adminSession){gap(4);addButton("Изменить блок",()->startEditor(b),true);addButton("Удалить блок",()->confirmDelete(b),false);}
        addButton("← К списку",()->showList(""),false);
    }

    private void showImageFull(String path){
        Bitmap bitmap=decodeSampled(path,2200,2200);if(bitmap==null)return;
        android.app.Dialog dialog=new android.app.Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView image=new ImageView(this);image.setBackgroundColor(Color.BLACK);image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        dialog.setContentView(image);image.setOnClickListener(v->dialog.dismiss());dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.black);dialog.getWindow().setLayout(-1,-1);}
    }

    private Bitmap decodeSampled(String path,int reqW,int reqH){try{BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,bounds);int sample=1;while(bounds.outWidth/sample>reqW*2||bounds.outHeight/sample>reqH*2)sample*=2;BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=sample;o.inPreferredConfig=Bitmap.Config.RGB_565;return BitmapFactory.decodeFile(path,o);}catch(Throwable ignored){return null;}}
    private void confirmDelete(Block b){new AlertDialog.Builder(this).setTitle("Удалить блок?").setMessage("«"+b.title+"» будет удалён с этого устройства.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{db.delete(b.rowId);Toast.makeText(this,"Блок удалён",Toast.LENGTH_SHORT).show();showList("");}).show();}
    private void openAdmin(){showAdminLogin();}

    private void showAdminLogin(){
        adminSession=false;sessionAdminPassword="";
        shell("LINEUP ADMIN","УПРАВЛЕНИЕ КОНТЕНТОМ",false,null);
        addText("Вход администратора",24,FG,true,4,8);
        addText("Ввод пароля открывает редактор блоков. Сохранённые на этом устройстве материалы не пропадут при выходе.",14,MUTED,false,0,16);
        EditText password=field("Пароль администратора","",false);
        password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addButton("Войти",()->{
            String value=password.getText().toString();
            if(hashPin(value).equals("178e7b310fb66370afa9cc1d17d0ad4e8f0aa22be82abd1a1c5cfa6598fb11db")){
                adminSession=true;sessionAdminPassword=value;showAdminMenu();
            }else{
                Toast.makeText(this,"Неверный пароль",Toast.LENGTH_SHORT).show();
            }
        },true);
        addText("Пароль хранится в виде хэша в интерфейсе. Не распространяй APK администратора — для пользователей предназначена отдельная сборка.",11,MUTED,false,12,0);
    }

    private String hashPin(String value){
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(("lineup-admin:"+value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder();
            for(byte b:bytes)result.append(String.format(Locale.ROOT,"%02x",b&255));
            return result.toString();
        }catch(Exception e){return "";}
    }

    private void showAdminMenu(){
        shell("Администратор","РЕДАКТОР И СБОРКА БАЗЫ",true,this::showAdminLogin);
        addText("Твоя база раскидок",24,FG,true,3,6);
        addText("Блоки и фотографии сохраняются на устройстве. Экспортируй community.lineup, перенеси его на ПК рядом с Lineup-Publish.bat и запусти BAT. Он опубликует данные в GitHub, а приложение игроков скачает их автоматически — без сборки нового APK.",14,MUTED,false,0,16);
        LinearLayout stats=row();stats.setPadding(dp(14),dp(13),dp(14),dp(13));stats.setBackground(shape(Color.rgb(18,33,50),Color.rgb(39,70,80),16));
        LinearLayout left=column();left.addView(text(String.valueOf(db.list("").size()),24,MINT,true));left.addView(text("сохранённых блоков",12,MUTED,false));
        stats.addView(left,new LinearLayout.LayoutParams(0,-2,1));TextView offline=text("● ЛОКАЛЬНО",11,MINT,true);offline.setGravity(Gravity.CENTER);stats.addView(offline);page.addView(stats,lp(-1,-2));gap(16);
        addButton("＋  Добавить блок",()->startEditor(null),true);
        addButton("✎  Все блоки / редактировать",()->showList(""),false);
        addButton("⬆  Обновить данные для пользователей",this::prepareContentUpdate,true);
        addButton("✈  Изменить ссылку Telegram",this::changeTelegramLink,false);
        addButton("🔒  Почему нельзя отключить старые APK",this::lockPreviousVersion,false);
        addButton("Экспортировать резервную копию .lineup",this::pickExport,false);
        addButton("Импортировать резервную копию .lineup",this::pickImport,false);
        addButton("Выйти из администратора",()->{adminSession=false;sessionAdminPassword="";showAdminLogin();},false);
    }

    private void startEditor(Block existing){
        draft=existing==null?new Block():existing.copy();draftPhotos=new ArrayList<>(existing==null?new ArrayList<>():existing.photos);draftVideos=new ArrayList<>(existing==null?new ArrayList<>():existing.videos);showEditor();
    }

    private void showEditor(){
        shell(draft.rowId==0?"Новый блок":"Редактирование","КАРТА · СТОРОНА · ПЛЕНТ · ГРАНАТА · МЕДИА",true,()->{if(draft.rowId>0)showDetail(draft.rowId);else showAdminMenu();});
        addText("Название блока *",12,MUTED,true,0,5);edTitle=field("Например: Смок на мид",draft.title,false);
        addText("Карта *",12,MUTED,true,0,5);edMap=makeDropdown(new String[]{"Выбери карту","Duna","Sandstone","Province","Prison","Rust","Hanami","Breeze"},draft.map);
        addText("Категория",12,MUTED,true,0,5);edCategory=makeDropdown(new String[]{"Раскидка","Тактика","Командная схема"},draft.category);
        addText("Сторона",12,MUTED,true,0,5);edSide=makeDropdown(new String[]{"Атака","Оборона"},draft.side);
        addText("Плент",12,MUTED,true,0,5);edPlant=makeDropdown(new String[]{"Не указан","Плент A","Плент B"},draft.plant);
        addText("Тип гранаты",12,MUTED,true,0,5);edGrenade=makeDropdown(new String[]{"Не указано","Хае","Молотов","Флеш","Смок"},draft.grenadeType);
        addText("Описание и порядок действий",12,MUTED,true,0,5);edDescription=field("Куда встать, куда смотреть и когда бросать…",draft.description,true);
        addText("Фотографии · "+draftPhotos.size()+" из 12",15,FG,true,4,9);
        if(draftPhotos.isEmpty())addCard(text("Добавь скриншоты позиции, прицела и результата.",13,MUTED,false));
        else for(int i=0;i<draftPhotos.size();i++){final int ix=i;LinearLayout r=row();File f=new File(draftPhotos.get(i));ImageView img=new ImageView(this);img.setBackground(shape(SURFACE2,0,10));img.setScaleType(ImageView.ScaleType.CENTER_CROP);Bitmap bm=decodeSampled(f.getAbsolutePath(),220,220);if(bm!=null)img.setImageBitmap(bm);r.addView(img,lp(72,72));LinearLayout details=column();details.setPadding(dp(10),0,0,0);details.addView(text("Фото "+(i+1),13,FG,true));details.addView(text(f.exists()?"Сохранено на устройстве":"Файл не найден",11,MUTED,false));r.addView(details,new LinearLayout.LayoutParams(0,-2,1));TextView remove=text("Убрать",12,Color.rgb(255,130,150),true);r.addView(remove);remove.setOnClickListener(v->{captureDraft();draftPhotos.remove(ix);showEditor();});addCard(r);}
        addButton("＋  Добавить фотографии",this::pickPhotos,false);
        addText("Видео · "+draftVideos.size()+" из 4",15,FG,true,3,9);
        if(draftVideos.isEmpty())addCard(text("Добавь короткий ролик с демонстрацией раскидки. До 40 МБ на ролик.",13,MUTED,false));
        else for(int i=0;i<draftVideos.size();i++){final int ix=i;File f=new File(draftVideos.get(i));LinearLayout r=row();r.addView(text("▶",22,PURPLE,true),lp(34,44));LinearLayout details=column();details.setPadding(dp(8),0,0,0);details.addView(text("Видео "+(i+1),13,FG,true));details.addView(text(f.exists()?f.getName():"Файл не найден",11,MUTED,false));r.addView(details,new LinearLayout.LayoutParams(0,-2,1));TextView remove=text("Убрать",12,Color.rgb(255,130,150),true);r.addView(remove);remove.setOnClickListener(v->{captureDraft();draftVideos.remove(ix);showEditor();});addCard(r);}
        addButton("＋  Добавить видео",this::pickVideos,false);addButton("Сохранить блок",this::saveEditor,true);
    }

    private String selected(Spinner spinner,String fallback){Object value=spinner==null?null:spinner.getSelectedItem();return value==null?fallback:value.toString();}
    private void captureDraft(){
        if(draft==null||edTitle==null)return;
        draft.title=edTitle.getText().toString().trim();String map=selected(edMap,"");draft.map="Выбери карту".equals(map)?"":map;
        draft.category=selected(edCategory,"Раскидка");draft.side=selected(edSide,"Атака");
        String plant=selected(edPlant,"Не указан");draft.plant="Не указан".equals(plant)?"":plant;
        String grenade=selected(edGrenade,"Не указано");draft.grenadeType="Не указано".equals(grenade)?"":grenade;
        draft.description=edDescription.getText().toString().trim();draft.photos=new ArrayList<>(draftPhotos);draft.videos=new ArrayList<>(draftVideos);
    }

    private void saveEditor(){
        captureDraft();if(draft.title.isEmpty()){Toast.makeText(this,"Добавь название блока",Toast.LENGTH_SHORT).show();return;}
        if(draft.map.isEmpty()){Toast.makeText(this,"Выбери карту",Toast.LENGTH_SHORT).show();return;}
        if(draft.description.isEmpty()){Toast.makeText(this,"Добавь описание или порядок действий",Toast.LENGTH_SHORT).show();return;}
        try{db.save(draft,draftPhotos,draftVideos);long id=draft.rowId;Toast.makeText(this,"Блок сохранён офлайн",Toast.LENGTH_SHORT).show();showDetail(id);}
        catch(Exception e){Toast.makeText(this,"Не удалось сохранить: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void pickPhotos(){captureDraft();if(draftPhotos.size()>=12){Toast.makeText(this,"Максимум 12 фото в одном блоке",Toast.LENGTH_SHORT).show();return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,PICK_PHOTOS);}
    private void pickVideos(){captureDraft();if(draftVideos.size()>=4){Toast.makeText(this,"Максимум 4 видео в одном блоке",Toast.LENGTH_SHORT).show();return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("video/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,PICK_VIDEOS);}
    private void pickImport(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,PICK_IMPORT);}
    private void pickExport(){if(!adminSession){Toast.makeText(this,"Сначала войди как администратор",Toast.LENGTH_SHORT).show();return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/octet-stream");i.putExtra(Intent.EXTRA_TITLE,"lineup-backup.lineup");startActivityForResult(i,PICK_EXPORT);}

    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||(data.getData()==null&&data.getClipData()==null))return;
        try{
            if(req==PICK_PHOTOS||req==PICK_VIDEOS){
                captureDraft();ArrayList<Uri> selected=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)selected.add(data.getClipData().getItemAt(i).getUri());}else selected.add(data.getData());int count=0;
                for(Uri uri:selected){if(req==PICK_PHOTOS){if(draftPhotos.size()>=12)break;String path=copyPhoto(uri);if(path!=null){draftPhotos.add(path);count++;}}else{if(draftVideos.size()>=4)break;String path=copyVideo(uri);if(path!=null){draftVideos.add(path);count++;}}}
                Toast.makeText(this,(req==PICK_PHOTOS?"Добавлено фото: ":"Добавлено видео: ")+count,Toast.LENGTH_SHORT).show();showEditor();
            }else if(req==PICK_IMPORT){int n=PackManager.importFromUri(this,db,data.getData());Toast.makeText(this,"Импортировано блоков: "+n,Toast.LENGTH_LONG).show();if(adminSession)showAdminMenu();else showHome();}
            else if(req==PICK_EXPORT){PackManager.exportToUri(this,db,data.getData());Toast.makeText(this,"Резервная копия .lineup сохранена",Toast.LENGTH_LONG).show();showAdminMenu();}
            else if(req==PICK_UPDATE){PackManager.exportToUri(this,db,data.getData(),prefs.getString("telegram_url","https://t.me/"));showPcPublishInstructions();}
        }catch(Exception e){Toast.makeText(this,"Ошибка: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()),Toast.LENGTH_LONG).show();if(req==PICK_PHOTOS||req==PICK_VIDEOS)showEditor();}
    }

    private String copyPhoto(Uri uri){return copyMedia(uri,false);}
    private String copyVideo(Uri uri){return copyMedia(uri,true);}
    private String copyMedia(Uri uri,boolean video){
        InputStream in=null;File target=null;
        try{
            File dir=new File(getFilesDir(),video?"lineup_videos":"lineup_images");if(!dir.exists()&&!dir.mkdirs())return null;
            String mime=getContentResolver().getType(uri);String lower=mime==null?"":mime.toLowerCase(Locale.ROOT);
            String ext=video?(lower.contains("webm")?".webm":lower.contains("quicktime")?".mov":lower.contains("3gpp")?".3gp":".mp4"):(lower.contains("png")?".png":lower.contains("webp")?".webp":".jpg");
            target=new File(dir,java.util.UUID.randomUUID().toString()+ext);in=getContentResolver().openInputStream(uri);if(in==null)return null;int max=video?40*1024*1024:20*1024*1024;
            try(FileOutputStream out=new FileOutputStream(target)){byte[] buffer=new byte[32768];int total=0,read;while((read=in.read(buffer))!=-1){total+=read;if(total>max)throw new IllegalArgumentException(video?"Видео не должно превышать 40 МБ":"Фото не должно превышать 20 МБ");out.write(buffer,0,read);}}
            return target.getAbsolutePath();
        }catch(Exception e){if(target!=null)target.delete();Toast.makeText(this,"Не удалось добавить "+(video?"видео: ":"фото: ")+e.getMessage(),Toast.LENGTH_LONG).show();return null;}
        finally{try{if(in!=null)in.close();}catch(Exception ignored){}}
    }
    private void showUpdateGate(String message,String error) {
        databaseUnlocked=false;
        shell("База данных","ОБЯЗАТЕЛЬНАЯ СИНХРОНИЗАЦИЯ",false,null);
        addText("Обновление перед входом",22,FG,true,4,8);
        addText("Перед просмотром блоков нужно проверить онлайн-версию базы и установить все обязательные изменения. После загрузки фотографии и описания сохраняются на устройстве.",14,MUTED,false,0,16);
        addCard(text(message,14,error==null?MINT:Color.rgb(255,130,150),true));
        if(error!=null)addText(error,13,Color.rgb(255,160,170),false,4,12);
        addButton(checkingUpdates?"Проверка выполняется…":"Проверить и обновить базу",()->checkUpdates(true),true);
        addButton("Я администратор — добавить/опубликовать блоки",this::openAdmin,false);
        addText("Для проверки и скачивания нужна сеть. Если сервер опубликовал новую версию, открыть материалы до её загрузки нельзя.",11,MUTED,false,12,0);
    }


    private void prepareContentUpdate(){
        if(!adminSession){showAdminLogin();return;}
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE,"community.lineup");
        new AlertDialog.Builder(this).setTitle("Подготовить обновление без сборки APK")
            .setMessage("Сохрани community.lineup. Затем перенеси этот файл на ПК в ту же папку, где лежит Lineup-Publish.bat, и запусти BAT. Скрипт опубликует базу в GitHub, а игроки получат новые блоки и фотографии автоматически — без пересборки APK.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Экспортировать пакет",(d,w)->startActivityForResult(intent,PICK_UPDATE)).show();
    }

    private static final String PUBLISH_BAT_URL="https://github.com/Selteck1/ApkGhostly/raw/refs/heads/main/tools/Lineup-Publish.bat";

    private void showPcPublishInstructions(){
        new AlertDialog.Builder(this).setTitle("Пакет данных готов")
            .setMessage("1. Файл community.lineup сохранён на телефоне. Перенеси его на ПК (например, по USB или через Telegram).\n\n2. Скачай Lineup-Publish.bat и положи рядом с community.lineup.\n\n3. Запусти BAT. Он опубликует пакет в GitHub. При первом запуске Git может попросить войти в GitHub через браузер.\n\n4. Игроки получат блоки и фотографии при следующей проверке обновлений, обычно в течение минуты при открытом приложении.\n\nДля BAT на ПК нужны Git for Windows и Python 3.")
            .setNeutralButton("Скопировать ссылку BAT",(d,w)->copyText("Ссылка на Lineup-Publish.bat",PUBLISH_BAT_URL))
            .setPositiveButton("Скачать BAT",(d,w)->openPcPublisher())
            .setNegativeButton("Закрыть",null).show();
    }

    private void openPcPublisher(){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(PUBLISH_BAT_URL)));}
        catch(Exception e){copyText("Ссылка на Lineup-Publish.bat",PUBLISH_BAT_URL);}
    }

    private void copyText(String label,String value){
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText(label,value));Toast.makeText(this,"Скопировано в буфер обмена",Toast.LENGTH_LONG).show();}
    }

    private void lockPreviousVersion(){
        new AlertDialog.Builder(this).setTitle("Блокировка старых APK")
            .setMessage("Для блокировки старых APK нужно выпустить новую версию приложения: номер версии зашит внутри APK. Публикация данных через Lineup-Publish.bat не меняет APK и не должна отключать актуальных пользователей.\n\nНовые блоки, описания, фотографии и ссылка Telegram публикуются отдельно — без пересборки APK.")
            .setPositiveButton("Понятно",null).show();
    }

    private void changeTelegramLink(){
        if(!adminSession){showAdminLogin();return;}
        EditText link=new EditText(this);link.setSingleLine(true);link.setText(prefs.getString("telegram_url","https://t.me/"));link.setHint("https://t.me/your_channel");link.setTextColor(FG);link.setHintTextColor(MUTED);link.setPadding(dp(12),dp(10),dp(12),dp(10));link.setBackground(shape(SURFACE2,PURPLE,12));
        new AlertDialog.Builder(this).setTitle("Ссылка обновления в Telegram").setMessage("Ссылка сохранится в настройках администратора и попадёт в content/policy.json при следующей публикации через Lineup-Publish.bat.")
            .setView(link).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{
                String value=link.getText().toString().trim();
                try{
                    Uri uri=Uri.parse(value);
                    if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||
                        !(uri.getHost().equalsIgnoreCase("t.me")||uri.getHost().equalsIgnoreCase("www.t.me")||uri.getHost().equalsIgnoreCase("telegram.me")||uri.getHost().equalsIgnoreCase("www.telegram.me"))){
                        Toast.makeText(this,"Нужна HTTPS-ссылка на t.me или telegram.me",Toast.LENGTH_LONG).show();return;
                    }
                }catch(Exception ex){Toast.makeText(this,"Неправильная ссылка",Toast.LENGTH_SHORT).show();return;}
                prefs.edit().putString("telegram_url",value).apply();
                Toast.makeText(this,"Ссылка сохранена. Опубликуется после следующей отправки через Lineup-Publish.bat.",Toast.LENGTH_LONG).show();
            }).show();
    }

    private void openBuildPage(){
        new AlertDialog.Builder(this).setTitle("Скачать приложение для игроков")
            .setMessage("Когда ключ подписи настроен, готовый APK появится в Releases. До этого тестовую сборку можно скачать как artifact из GitHub Actions.")
            .setPositiveButton("Последний APK",(d,w)->openUrl("https://github.com/Selteck1/ApkGhostly/releases/latest/download/Lineup-Client.apk"))
            .setNeutralButton("Тестовая сборка",(d,w)->openUrl("https://github.com/Selteck1/ApkGhostly/actions/workflows/client-release.yml"))
            .setNegativeButton("Отмена",null).show();
    }
    private void openUrl(String value){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(value)));}
        catch(Exception e){Toast.makeText(this,"Не удалось открыть ссылку",Toast.LENGTH_SHORT).show();}
    }

    private void checkUpdates(){checkUpdates(true);}

    private void checkUpdates(boolean requiredGate){
        if(checkingUpdates)return;
        checkingUpdates=true;
        databaseUnlocked=false;
        showUpdateGate("Подключаюсь к каталогу и проверяю версию базы…",null);
        worker.execute(()->{
            int packsDone=0,blocksDone=0;
            long revision=-1;
            String error=null;
            try{
                String catalogUrl=CATALOG_URL+"?nocache="+System.currentTimeMillis();
                JSONObject catalog=new JSONObject(new String(download(catalogUrl,2*1024*1024),StandardCharsets.UTF_8));
                if(catalog.optInt("schemaVersion",0)!=1)throw new IllegalArgumentException("Версия каталога не поддерживается. Обнови приложение.");
                revision=catalog.optLong("revision",-1);
                if(revision<0)throw new IllegalArgumentException("На сервере ещё не опубликована версия базы. Повтори проверку позже.");
                long installedRevision=prefs.getLong("synced_revision",-1);
                if(revision<installedRevision)throw new IllegalArgumentException("Сервер вернул старую версию базы. Изменения не применены.");

                JSONArray packs=catalog.optJSONArray("packs");
                if(packs==null)packs=new JSONArray();
                if(packs.length()==0)throw new IllegalArgumentException("Общая база ещё не опубликована. Администратор должен создать блоки и нажать «Опубликовать базу для всех устройств».");
                Set<String> activeIds=new HashSet<>();
                for(int i=0;i<packs.length();i++){
                    JSONObject item=packs.getJSONObject(i);
                    String id=item.optString("id",""),file=item.optString("file",""),expected=item.optString("sha256","").toLowerCase(Locale.ROOT);
                    if(!id.matches("[A-Za-z0-9_-]{1,100}"))throw new IllegalArgumentException("Неправильный ID пакета в каталоге.");
                    if(!file.startsWith("content/packs/")||file.contains(".."))throw new IllegalArgumentException("Каталог содержит недопустимый путь пакета.");
                    activeIds.add(id);
                    if(!expected.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Неправильная контрольная сумма пакета "+id);
                    if(expected.equals(prefs.getString("pack_hash_"+id,"")))continue;
                    String address="https://raw.githubusercontent.com/Selteck1/ApkGhostly/main/"+file.replace(" ","%20");
                    byte[] packageBytes=download(address+"?nocache="+System.currentTimeMillis(),128*1024*1024);
                    if(!expected.equals(PackManager.sha256(packageBytes)))throw new IllegalArgumentException("Проверка целостности не прошла для пакета "+id+". Повтори скачивание.");
                    int count=PackManager.importBytes(this,db,packageBytes);
                    prefs.edit().putString("pack_hash_"+id,expected).apply();
                    packsDone++;
                    blocksDone+=count;
                }

                // Remove downloaded packs that were explicitly removed from the published catalog.
                SharedPreferences.Editor cleanup=prefs.edit();
                for(String key:prefs.getAll().keySet()){
                    if(key.startsWith("pack_hash_")){
                        String oldId=key.substring("pack_hash_".length());
                        if(!activeIds.contains(oldId)){
                            db.deleteMissingFromPack(oldId,new HashSet<>());
                            cleanup.remove(key);
                        }
                    }
                }
                cleanup.putLong("synced_revision",revision).apply();
            }catch(Exception e){
                error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
            }
            final int completedPacks=packsDone,completedBlocks=blocksDone;
            final long completedRevision=revision;
            final String failure=error;
            runOnUiThread(()->{
                checkingUpdates=false;
                if(failure!=null){
                    databaseUnlocked=false;
                    showUpdateGate("Не удалось завершить синхронизацию базы.",failure+"\n\nИнтернет нужен для проверки и загрузки обязательного обновления. Локальная база не будет открыта до успешной проверки.");
                    return;
                }
                databaseUnlocked=true;
                gateHandler.removeCallbacks(gatePoll);
                gateHandler.postDelayed(gatePoll,60000L);
                showHome();
                if(completedPacks>0){
                    Toast.makeText(this,"База обновлена до версии "+completedRevision+". Пакетов: "+completedPacks+", блоков: "+completedBlocks+".",Toast.LENGTH_LONG).show();
                }else{
                    Toast.makeText(this,"База проверена. Установлена актуальная версия "+completedRevision+".",Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void pollCatalogRevision(){
        if(!databaseUnlocked||checkingUpdates||checkingRevision)return;
        checkingRevision=true;
        worker.execute(()->{
            boolean newer=false;
            try{
                String url=CATALOG_URL+"?nocache="+System.currentTimeMillis();
                JSONObject catalog=new JSONObject(new String(download(url,2*1024*1024),StandardCharsets.UTF_8));
                long remote=catalog.optLong("revision",-1);
                long installed=prefs.getLong("synced_revision",-1);
                newer=remote>=0&&remote>installed;
            }catch(Exception ignored){
                // If the internet is temporarily unavailable during an active session,
                // already downloaded materials remain available locally.
            }finally{
                checkingRevision=false;
            }
            if(newer)runOnUiThread(()->{if(databaseUnlocked)checkUpdates(true);});
        });
    }

    private byte[] download(String address,int max)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setRequestMethod("GET");c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","Lineup-Android/1.0");c.setUseCaches(false);c.setRequestProperty("Cache-Control","no-cache");
        try{int status=c.getResponseCode();if(status<200||status>=300)throw new IllegalStateException("Сервер вернул HTTP "+status);try(InputStream in=c.getInputStream()){return PackManager.readLimited(in,max);}}finally{c.disconnect();}}

    private final class InsetRoot extends LinearLayout {
        InsetRoot(Context context) { super(context); }
        @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
            setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        }
    }
}
