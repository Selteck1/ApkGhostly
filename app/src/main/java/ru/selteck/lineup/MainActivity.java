package ru.selteck.lineup;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_PHOTOS=201,PICK_IMPORT=202,PICK_EXPORT=203;
    private static final String CATALOG_URL="https://raw.githubusercontent.com/Selteck1/ApkGhostly/main/content/catalog.json";
    private static final int BG=Color.rgb(10,14,27),SURFACE=Color.rgb(21,28,47),SURFACE2=Color.rgb(29,38,61);
    private static final int FG=Color.rgb(241,244,255),MUTED=Color.rgb(158,170,197),PURPLE=Color.rgb(167,139,250),MINT=Color.rgb(77,226,197);
    private AppDb db;private SharedPreferences prefs;private LinearLayout root,page;private ScrollView scroll;private boolean adminSession=false;
    private Block draft;private ArrayList<String> draftPhotos=new ArrayList<>();private EditText edTitle,edMap,edCategory,edSide,edDescription;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state){super.onCreate(state);Window w=getWindow();w.setStatusBarColor(Color.rgb(9,13,24));w.setNavigationBarColor(Color.rgb(9,13,24));w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        db=new AppDb(this);prefs=getSharedPreferences("lineup_settings",MODE_PRIVATE);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);setContentView(root);showHome();}
    @Override protected void onDestroy(){worker.shutdownNow();db.close();super.onDestroy();}
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
        else{TextView logo=text("L",22,MINT,true);logo.setGravity(Gravity.CENTER);logo.setBackground(shape(SURFACE2,PURPLE,40));header.addView(logo,lp(42,42));}
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
    private void showHome(){adminSession=false;shell("LINEUP","ОФЛАЙН-СПРАВОЧНИК STANDOFF 2",false,null);addText("Тактики под рукой.",27,FG,true,2,5);addText("Сохрани раскидки заранее — смотри фото и инструкции даже без интернета.",14,MUTED,false,0,18);
        int count=db.list("").size();LinearLayout stats=row();stats.setPadding(dp(14),dp(13),dp(14),dp(13));stats.setBackground(shape(Color.rgb(18,33,50),Color.rgb(39,70,80),16));LinearLayout left=column();left.addView(text(String.valueOf(count),24,MINT,true));left.addView(text("блоков на устройстве",12,MUTED,false));stats.addView(left,new LinearLayout.LayoutParams(0,-2,1));TextView off=text("● ОФЛАЙН",11,MINT,true);off.setGravity(Gravity.CENTER);stats.addView(off);page.addView(stats,lp(-1,-2));gap(16);
        addButton("📚  Открыть все блоки",()->showList(""),true);addButton("⬇  Проверить обновления",this::checkUpdates,false);addButton("＋  Импортировать пакет с устройства",this::pickImport,false);addButton("⚙  Администратор",this::openAdmin,false);
        addText("Недавно добавленные",17,FG,true,15,10);List<Block> latest=db.list("");if(latest.isEmpty())addCard(text("Здесь появятся твои раскидки. Открой режим администратора и создай первый блок.",14,MUTED,false));else for(int i=0;i<Math.min(4,latest.size());i++)addBlockCard(latest.get(i));
        addText("Обновления скачиваются отдельно от APK. Установленные материалы работают офлайн.",11,MUTED,false,8,0);}
    private void showList(String query){shell("Блоки",query==null||query.isEmpty()?"ВСЯ СОХРАНЁННАЯ БАЗА":"РЕЗУЛЬТАТЫ ПОИСКА",true,this::showHome);EditText search=field("Поиск по названию, карте или описанию",query,false);addButton("Найти",()->showList(search.getText().toString().trim()),true);
        addText("Быстрые фильтры",16,FG,true,4,8);addButton("Все материалы",()->showList(""),false);addButton("Раскидки",()->showList("Раскидка"),false);addButton("Тактики",()->showList("Тактика"),false);addButton("Командные схемы",()->showList("Команда"),false);addText("Материалы",16,FG,true,8,8);
        List<Block> list=db.list(query);if(list.isEmpty())addCard(text("Ничего не найдено. Попробуй другой запрос или создай первый блок.",14,MUTED,false));else for(Block b:list)addBlockCard(b);}
    private String meta(Block b){ArrayList<String> parts=new ArrayList<>();if(b.map!=null&&!b.map.trim().isEmpty())parts.add(b.map.trim());if(b.category!=null&&!b.category.trim().isEmpty())parts.add(b.category.trim());if(b.side!=null&&!b.side.trim().isEmpty())parts.add(b.side.trim());return android.text.TextUtils.join("  ·  ",parts);}
    private void addBlockCard(Block b){LinearLayout c=column();LinearLayout top=row();top.addView(text(b.title,16,FG,true),new LinearLayout.LayoutParams(0,-2,1));top.addView(text("▧ "+b.photos.size(),12,MINT,true));c.addView(top);LinearLayout.LayoutParams mp=lp(-1,-2);mp.topMargin=dp(6);c.addView(text(meta(b),11,PURPLE,true),mp);
        TextView desc=text(b.description.isEmpty()?"Без описания":b.description,13,MUTED,false);desc.setMaxLines(2);LinearLayout.LayoutParams dpv=lp(-1,-2);dpv.topMargin=dp(6);c.addView(desc,dpv);addCard(c);c.setClickable(true);c.setOnClickListener(v->showDetail(b.rowId));}
    private void showDetail(long id){Block b=db.get(id);if(b==null){showList("");return;}shell(b.title,meta(b),true,this::showHome);addText(b.description.isEmpty()?"Описание пока не добавлено.":b.description,15,FG,false,4,16);
        if(b.photos.isEmpty())addCard(text("Фотографии не добавлены.",13,MUTED,false));else{addText("Фото и порядок выполнения · "+b.photos.size(),15,FG,true,0,9);int index=1;for(String path:b.photos){File f=new File(path);if(!f.isFile()){index++;continue;}ImageView image=new ImageView(this);image.setBackground(shape(SURFACE,0,14));image.setScaleType(ImageView.ScaleType.FIT_CENTER);Bitmap bm=decodeSampled(path,1100,1100);if(bm!=null)image.setImageBitmap(bm);LinearLayout.LayoutParams p=lp(-1,240);p.bottomMargin=dp(5);page.addView(image,p);addText("Фото "+index,11,MUTED,false,0,12);index++;}}
        if(adminSession){gap(4);addButton("Изменить блок",()->startEditor(b),true);addButton("Удалить блок",()->confirmDelete(b),false);}addButton("← К списку",()->showList(""),false);}
    private Bitmap decodeSampled(String path,int reqW,int reqH){try{BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,bounds);int sample=1;while(bounds.outWidth/sample>reqW*2||bounds.outHeight/sample>reqH*2)sample*=2;BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=sample;o.inPreferredConfig=Bitmap.Config.RGB_565;return BitmapFactory.decodeFile(path,o);}catch(Throwable ignored){return null;}}
    private void confirmDelete(Block b){new AlertDialog.Builder(this).setTitle("Удалить блок?").setMessage("«"+b.title+"» будет удалён с этого устройства.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{db.delete(b.rowId);Toast.makeText(this,"Блок удалён",Toast.LENGTH_SHORT).show();showList("");}).show();}
    private void openAdmin(){String saved=prefs.getString("admin_pin_hash","");EditText pin=new EditText(this);pin.setHint(saved.isEmpty()?"Придумай PIN от 6 цифр":"PIN администратора");pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);pin.setTextColor(FG);pin.setHintTextColor(MUTED);pin.setPadding(dp(12),dp(10),dp(12),dp(10));pin.setBackground(shape(SURFACE2,PURPLE,12));
        LinearLayout box=column();box.setPadding(dp(4),dp(8),dp(4),dp(3));box.addView(text(saved.isEmpty()?"Создай локальный PIN. Он защищает редактирование на этом устройстве.":"Введи PIN, чтобы открыть инструменты администратора.",13,MUTED,false));box.addView(new View(this),lp(1,10));box.addView(pin,lp(-1,50));
        new AlertDialog.Builder(this).setTitle(saved.isEmpty()?"Регистрация администратора":"Вход администратора").setView(box).setNegativeButton("Отмена",null).setPositiveButton(saved.isEmpty()?"Создать PIN":"Войти",(d,w)->{String value=pin.getText().toString().trim();if(saved.isEmpty()){if(value.length()<6){Toast.makeText(this,"PIN должен содержать минимум 6 цифр",Toast.LENGTH_LONG).show();return;}prefs.edit().putString("admin_pin_hash",hashPin(value)).apply();adminSession=true;showAdminMenu();}else if(hashPin(value).equals(saved)){adminSession=true;showAdminMenu();}else Toast.makeText(this,"Неверный PIN",Toast.LENGTH_SHORT).show();}).show();}
    private String hashPin(String value){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest((getPackageName()+":lineup:"+value).getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}catch(Exception e){return value;}}
    private void showAdminMenu(){shell("Администратор","УПРАВЛЕНИЕ ЛОКАЛЬНЫМИ МАТЕРИАЛАМИ",true,this::showHome);addText("Редактор контента",20,FG,true,2,6);addText("Создавай материалы на телефоне. Чтобы поделиться ими со всеми, экспортируй пакет и загрузи его в репозиторий контента.",13,MUTED,false,0,18);
        addButton("＋  Создать блок",()->startEditor(null),true);addButton("✎  Изменить существующий блок",()->showList(""),false);addButton("⇧  Экспортировать пакет .lineup",this::pickExport,false);addButton("⇩  Импортировать пакет .lineup",this::pickImport,false);addButton("Выйти из режима администратора",()->{adminSession=false;showHome();},false);}
    private void startEditor(Block existing){draft=existing==null?new Block():existing.copy();draftPhotos=new ArrayList<>(existing==null?new ArrayList<>():existing.photos);showEditor();}
    private void showEditor(){shell(draft.rowId==0?"Новый блок":"Редактирование","НАЗВАНИЕ, КАРТА, ОПИСАНИЕ И ФОТО",true,()->{if(draft.rowId>0)showDetail(draft.rowId);else showAdminMenu();});
        addText("Название блока *",12,MUTED,true,0,5);edTitle=field("Например: Смок на мид",draft.title,false);addText("Карта",12,MUTED,true,0,5);edMap=field("Название карты",draft.map,false);
        addText("Тип материала",12,MUTED,true,0,5);edCategory=field("Раскидка / Тактика / Командная схема",draft.category,false);addText("Сторона",12,MUTED,true,0,5);edSide=field("Атака / Защита / Любая сторона",draft.side,false);
        addText("Описание и порядок действий *",12,MUTED,true,0,5);edDescription=field("Куда встать, куда смотреть и когда бросать…",draft.description,true);addText("Фотографии · "+draftPhotos.size()+" из 12",15,FG,true,4,9);
        if(draftPhotos.isEmpty())addCard(text("Добавь несколько скриншотов: позиция, прицел и результат.",13,MUTED,false));else for(int i=0;i<draftPhotos.size();i++){final int ix=i;LinearLayout r=row();File f=new File(draftPhotos.get(i));ImageView img=new ImageView(this);img.setBackground(shape(SURFACE2,0,10));img.setScaleType(ImageView.ScaleType.CENTER_CROP);Bitmap bm=decodeSampled(f.getAbsolutePath(),220,220);if(bm!=null)img.setImageBitmap(bm);r.addView(img,lp(72,72));
            LinearLayout details=column();details.setPadding(dp(10),0,0,0);details.addView(text("Фото "+(i+1),13,FG,true));details.addView(text(f.exists()?"Сохранено на устройстве":"Файл не найден",11,MUTED,false));r.addView(details,new LinearLayout.LayoutParams(0,-2,1));TextView remove=text("Убрать",12,Color.rgb(255,130,150),true);r.addView(remove);remove.setOnClickListener(v->{captureDraft();draftPhotos.remove(ix);showEditor();});addCard(r);}
        addButton("＋  Добавить фотографии",this::pickPhotos,false);addButton("Сохранить блок",this::saveEditor,true);}
    private void captureDraft(){if(draft==null||edTitle==null)return;draft.title=edTitle.getText().toString().trim();draft.map=edMap.getText().toString().trim();draft.category=edCategory.getText().toString().trim();draft.side=edSide.getText().toString().trim();draft.description=edDescription.getText().toString().trim();}
    private void saveEditor(){captureDraft();if(draft.title.isEmpty()){Toast.makeText(this,"Добавь название блока",Toast.LENGTH_SHORT).show();return;}if(draft.description.isEmpty()){Toast.makeText(this,"Добавь описание или порядок действий",Toast.LENGTH_SHORT).show();return;}
        try{db.save(draft,draftPhotos);long id=draft.rowId;Toast.makeText(this,"Блок сохранён офлайн",Toast.LENGTH_SHORT).show();showDetail(id);}catch(Exception e){Toast.makeText(this,"Не удалось сохранить: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private void pickPhotos(){captureDraft();if(draftPhotos.size()>=12){Toast.makeText(this,"Максимум 12 фото в одном блоке",Toast.LENGTH_SHORT).show();return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,PICK_PHOTOS);}
    private void pickImport(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,PICK_IMPORT);}
    private void pickExport(){if(!adminSession){Toast.makeText(this,"Сначала войди как администратор",Toast.LENGTH_SHORT).show();return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/octet-stream");i.putExtra(Intent.EXTRA_TITLE,"community.lineup");startActivityForResult(i,PICK_EXPORT);}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||(data.getData()==null&&data.getClipData()==null))return;try{
        if(req==PICK_PHOTOS){captureDraft();ArrayList<Uri> selected=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)selected.add(data.getClipData().getItemAt(i).getUri());}else selected.add(data.getData());int count=0;for(Uri uri:selected){if(draftPhotos.size()>=12)break;String path=copyPhoto(uri);if(path!=null){draftPhotos.add(path);count++;}}Toast.makeText(this,"Добавлено фото: "+count,Toast.LENGTH_SHORT).show();showEditor();}
        else if(req==PICK_IMPORT){int n=PackManager.importFromUri(this,db,data.getData());Toast.makeText(this,"Импортировано блоков: "+n,Toast.LENGTH_LONG).show();if(adminSession)showAdminMenu();else showHome();}
        else if(req==PICK_EXPORT){PackManager.exportToUri(this,db,data.getData());Toast.makeText(this,"Пакет сохранён. Загрузи его в content/packs/community.lineup в GitHub.",Toast.LENGTH_LONG).show();showAdminMenu();}
    }catch(Exception e){Toast.makeText(this,"Ошибка: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()),Toast.LENGTH_LONG).show();if(req==PICK_PHOTOS)showEditor();}}
    private String copyPhoto(Uri uri){InputStream in=null;try{File dir=new File(getFilesDir(),"lineup_images");if(!dir.exists()&&!dir.mkdirs())return null;String mime=getContentResolver().getType(uri);String ext=mime!=null&&mime.toLowerCase(Locale.ROOT).contains("png")?".png":mime!=null&&mime.toLowerCase(Locale.ROOT).contains("webp")?".webp":".jpg";File target=new File(dir,java.util.UUID.randomUUID().toString()+ext);in=getContentResolver().openInputStream(uri);if(in==null)return null;
        try(FileOutputStream out=new FileOutputStream(target)){byte[] buffer=new byte[32768];int total=0,read;while((read=in.read(buffer))!=-1){total+=read;if(total>20*1024*1024){target.delete();throw new IllegalArgumentException("Фото не должно превышать 20 МБ");}out.write(buffer,0,read);}}return target.getAbsolutePath();
    }catch(Exception e){Toast.makeText(this,"Не удалось добавить фото: "+e.getMessage(),Toast.LENGTH_LONG).show();return null;}finally{try{if(in!=null)in.close();}catch(Exception ignored){}}}
    private void checkUpdates(){Toast.makeText(this,"Проверяю каталог…",Toast.LENGTH_SHORT).show();worker.execute(()->{int packsDone=0,blocksDone=0;String error=null;try{
        JSONObject catalog=new JSONObject(new String(download(CATALOG_URL,2*1024*1024),StandardCharsets.UTF_8));JSONArray packs=catalog.optJSONArray("packs");if(packs==null)packs=new JSONArray();
        for(int i=0;i<packs.length();i++){JSONObject item=packs.getJSONObject(i);String id=item.optString("id",""),file=item.optString("file",""),expected=item.optString("sha256","").toLowerCase(Locale.ROOT);if(id.isEmpty()||file.isEmpty())continue;
            if(!expected.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Неправильная контрольная сумма пакета "+id);if(expected.equals(prefs.getString("pack_hash_"+id,"")))continue;
            String url=file.startsWith("https://")?file:"https://raw.githubusercontent.com/Selteck1/ApkGhostly/main/"+file.replace(" ","%20");byte[] content=download(url,128*1024*1024);if(!expected.equals(PackManager.sha256(content)))throw new IllegalArgumentException("Контрольная сумма не совпала для "+id);
            int n=PackManager.importBytes(this,db,content);prefs.edit().putString("pack_hash_"+id,expected).apply();packsDone++;blocksDone+=n;}
    }catch(Exception e){error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
        final int p=packsDone,b=blocksDone;final String err=error;runOnUiThread(()->{if(err!=null)new AlertDialog.Builder(this).setTitle("Не удалось обновить базу").setMessage(err+"\n\nПроверь подключение. Уже сохранённые материалы остаются на устройстве.").setPositiveButton("Понятно",null).show();
            else if(p==0){Toast.makeText(this,"Новых пакетов нет. База готова к работе офлайн.",Toast.LENGTH_LONG).show();showHome();}
            else new AlertDialog.Builder(this).setTitle("Обновление готово").setMessage("Загружено пакетов: "+p+"\nОбработано блоков: "+b+"\nМатериалы доступны офлайн.").setPositiveButton("Открыть",(d,w)->showHome()).show();});});}
    private byte[] download(String address,int max)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setRequestMethod("GET");c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","Lineup-Android/1.0");
        try{int status=c.getResponseCode();if(status<200||status>=300)throw new IllegalStateException("Сервер вернул HTTP "+status);try(InputStream in=c.getInputStream()){return PackManager.readLimited(in,max);}}finally{c.disconnect();}}
}
