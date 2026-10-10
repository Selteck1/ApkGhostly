package ru.selteck.lineup;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class PackManager {
    private static final int MAX_PACK_BYTES = 90 * 1024 * 1024;
    private static final int MAX_PHOTO_BYTES = 20 * 1024 * 1024;
    private static final int MAX_VIDEO_BYTES = 40 * 1024 * 1024;
    private PackManager() {}

    public static void exportToUri(Context context, AppDb db, Uri destination) throws Exception {
        exportToUri(context, db, destination, "https://t.me/");
    }

    public static void exportToUri(Context context, AppDb db, Uri destination, String telegramUrl) throws Exception {
        OutputStream raw = context.getContentResolver().openOutputStream(destination);
        if (raw == null) throw new IllegalStateException("Не удалось открыть файл для записи");
        writePackage(db, telegramUrl, raw);
    }

    public static byte[] exportToBytes(Context context, AppDb db) throws Exception {
        return exportToBytes(context, db, "https://t.me/");
    }

    public static byte[] exportToBytes(Context context, AppDb db, String telegramUrl) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        writePackage(db, telegramUrl, raw);
        return raw.toByteArray();
    }

    private static void writePackage(AppDb db, String telegramUrl, OutputStream raw) throws Exception {
        JSONObject manifest=new JSONObject();
        manifest.put("format","lineup");manifest.put("schemaVersion",1);manifest.put("packId","community");
        manifest.put("version",System.currentTimeMillis()/1000L);manifest.put("title","Lineup — база раскидок");
        manifest.put("telegramUrl",telegramUrl==null?"https://t.me/":telegramUrl);
        JSONArray list=new JSONArray();Map<String,File> files=new HashMap<>();int index=0;long total=0;
        for(Block b:db.list("")){
            JSONObject item=new JSONObject();
            item.put("id",b.contentId);item.put("title",b.title);item.put("map",b.map);item.put("category",b.category);
            item.put("side",b.side);item.put("plant",b.plant);item.put("grenadeType",b.grenadeType);item.put("description",b.description);
            JSONArray photos=new JSONArray();
            for(String path:b.photos){
                File f=new File(path);if(!f.isFile())continue;
                if(f.length()>MAX_PHOTO_BYTES)throw new IllegalArgumentException("Фото больше 20 МБ: "+f.getName());
                total+=f.length();
                String name="images/"+safe(b.contentId)+"_"+(index++)+extension(f.getName());
                JSONObject media=new JSONObject();media.put("file",name);photos.put(media);files.put(name,f);
            }
            item.put("photos",photos);
            JSONArray videoList=new JSONArray();
            for(String path:b.videos){
                File f=new File(path);if(!f.isFile())continue;
                if(f.length()>MAX_VIDEO_BYTES)throw new IllegalArgumentException("Видео больше 40 МБ: "+f.getName());
                total+=f.length();
                String name="videos/"+safe(b.contentId)+"_"+(index++)+extension(f.getName());
                JSONObject media=new JSONObject();media.put("file",name);videoList.put(media);files.put(name,f);
            }
            item.put("videos",videoList);list.put(item);
            if(total>MAX_PACK_BYTES)throw new IllegalArgumentException("Общий размер медиафайлов должен быть не более 90 МБ.");
        }
        if(list.length()==0)throw new IllegalStateException("Сначала создай хотя бы один блок.");
        manifest.put("blocks",list);
        try(ZipOutputStream zip=new ZipOutputStream(raw,StandardCharsets.UTF_8)){
            zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            byte[] buffer=new byte[32768];
            for(Map.Entry<String,File> e:files.entrySet()){
                zip.putNextEntry(new ZipEntry(e.getKey()));
                try(InputStream in=new java.io.FileInputStream(e.getValue())){int read;while((read=in.read(buffer))!=-1)zip.write(buffer,0,read);}
                zip.closeEntry();
            }
            zip.finish();
        }
    }

    private static String safe(String id){return id==null?"block":id.replaceAll("[^A-Za-z0-9_-]","_");}
    private static String extension(String name){
        String lower=name==null?"":name.toLowerCase(java.util.Locale.ROOT);int dot=lower.lastIndexOf('.');String ext=dot>=0?lower.substring(dot):"";
        if(ext.matches("\\.(png|webp|jpg|jpeg|mp4|m4v|webm|mov|3gp)"))return ext.equals(".jpeg")?".jpg":ext;
        return ".jpg";
    }

    public static int importFromUri(Context context,AppDb db,Uri uri)throws Exception{
        InputStream in=context.getContentResolver().openInputStream(uri);if(in==null)throw new IllegalStateException("Не удалось прочитать пакет");
        try{return importBytes(context,db,readLimited(in,MAX_PACK_BYTES));}finally{in.close();}
    }

    public static int importBytes(Context context,AppDb db,byte[] bytes)throws Exception{
        if(bytes==null||bytes.length==0||bytes.length>MAX_PACK_BYTES)throw new IllegalArgumentException("Размер пакета недопустим (максимум 90 МБ)");
        Map<String,byte[]> entries=new HashMap<>();int total=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){
            ZipEntry entry;byte[] buffer=new byte[32768];
            while((entry=zip.getNextEntry())!=null){
                String name=entry.getName();if(entry.isDirectory())continue;
                if(name.startsWith("/")||name.contains("..")||!(name.equals("manifest.json")||name.startsWith("images/")||name.startsWith("videos/")))throw new IllegalArgumentException("В пакете найден недопустимый путь");
                ByteArrayOutputStream out=new ByteArrayOutputStream();int read;
                while((read=zip.read(buffer))!=-1){total+=read;if(total>MAX_PACK_BYTES)throw new IllegalArgumentException("Распакованный пакет слишком большой");out.write(buffer,0,read);}
                entries.put(name,out.toByteArray());zip.closeEntry();
            }
        }
        byte[] manifestBytes=entries.get("manifest.json");if(manifestBytes==null)throw new IllegalArgumentException("Нет manifest.json");
        JSONObject manifest=new JSONObject(new String(manifestBytes,StandardCharsets.UTF_8));
        if(!"lineup".equals(manifest.optString("format"))||manifest.optInt("schemaVersion",0)!=1)throw new IllegalArgumentException("Неизвестный формат пакета");
        String packId=manifest.optString("packId","community");if(packId.trim().isEmpty()||packId.length()>100)throw new IllegalArgumentException("Неправильный ID пакета");
        JSONArray list=manifest.optJSONArray("blocks");if(list==null||list.length()>5000)throw new IllegalArgumentException("Неправильный список блоков");
        File imageDir=new File(context.getFilesDir(),"lineup_images"),videoDir=new File(context.getFilesDir(),"lineup_videos");
        if(!imageDir.isDirectory()&&!imageDir.mkdirs())throw new IllegalStateException("Не удалось создать хранилище фото");
        if(!videoDir.isDirectory()&&!videoDir.mkdirs())throw new IllegalStateException("Не удалось создать хранилище видео");
        Set<String> ids=new HashSet<>();int imported=0;
        for(int i=0;i<list.length();i++){
            JSONObject item=list.getJSONObject(i);Block b=new Block();b.contentId=item.optString("id",UUID.randomUUID().toString());
            if(b.contentId.trim().isEmpty()||b.contentId.length()>150||!ids.add(b.contentId))throw new IllegalArgumentException("Неправильный или повторяющийся ID блока");
            b.sourcePackId=packId;b.title=item.optString("title","").trim();b.map=item.optString("map","").trim();
            b.category=item.optString("category","Раскидка").trim();b.side=item.optString("side","Атака").trim();
            b.plant=item.optString("plant","").trim();b.grenadeType=item.optString("grenadeType","").trim();
            b.description=item.optString("description","").trim();b.updatedAt=System.currentTimeMillis();
            if(b.title.isEmpty())throw new IllegalArgumentException("В одном из блоков нет названия");
            List<String> photos=importMedia(item.optJSONArray("photos"),entries,imageDir,"images/",MAX_PHOTO_BYTES,12);
            List<String> vids=importMedia(item.optJSONArray("videos"),entries,videoDir,"videos/",MAX_VIDEO_BYTES,4);
            db.save(b,photos,vids);imported++;
        }
        db.deleteMissingFromPack(packId,ids);return imported;
    }

    private static List<String> importMedia(JSONArray list,Map<String,byte[]> entries,File dir,String prefix,int maxBytes,int maxCount)throws Exception{
        ArrayList<String> paths=new ArrayList<>();if(list==null)return paths;if(list.length()>maxCount)throw new IllegalArgumentException("Слишком много медиафайлов в блоке");
        for(int i=0;i<list.length();i++){
            String name=list.getJSONObject(i).optString("file","");
            if(!name.startsWith(prefix)||name.contains(".."))throw new IllegalArgumentException("Неправильный путь медиафайла");
            byte[] data=entries.get(name);if(data==null||data.length==0||data.length>maxBytes)throw new IllegalArgumentException("Файл отсутствует или слишком большой: "+name);
            File target=new File(dir,UUID.randomUUID().toString()+extension(name));
            try(FileOutputStream out=new FileOutputStream(target)){out.write(data);}paths.add(target.getAbsolutePath());
        }
        return paths;
    }

    public static String sha256(byte[] bytes)throws Exception{byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    public static byte[] readLimited(InputStream in,int max)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[32768];int total=0,r;while((r=in.read(buffer))!=-1){total+=r;if(total>max)throw new IllegalArgumentException("Файл слишком большой");out.write(buffer,0,r);}return out.toByteArray();}
}
