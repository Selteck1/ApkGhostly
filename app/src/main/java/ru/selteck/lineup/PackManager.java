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
    private static final int MAX_PACK_BYTES = 128 * 1024 * 1024;
    private PackManager() {}

    public static void exportToUri(Context context, AppDb db, Uri destination) throws Exception {
        exportToUri(context, db, destination, "https://t.me/");
    }

    public static void exportToUri(Context context, AppDb db, Uri destination, String telegramUrl) throws Exception {
        OutputStream raw = context.getContentResolver().openOutputStream(destination);
        if (raw == null) throw new IllegalStateException("Не удалось открыть файл для записи");
        JSONObject manifest = new JSONObject(); manifest.put("format", "lineup"); manifest.put("schemaVersion", 1);
        manifest.put("packId", "community"); manifest.put("version", System.currentTimeMillis()/1000L); manifest.put("title", "Lineup — база раскидок");
        manifest.put("telegramUrl", telegramUrl == null ? "https://t.me/" : telegramUrl);
        JSONArray list = new JSONArray(); Map<String, File> files = new HashMap<>(); int n=0;
        for (Block b : db.list("")) {
            JSONObject item = new JSONObject(); item.put("id",b.contentId); item.put("title",b.title); item.put("map",b.map);
            item.put("category",b.category); item.put("side",b.side); item.put("description",b.description);
            JSONArray photos = new JSONArray();
            for(String path:b.photos){File f=new File(path);if(!f.isFile())continue;String safe=b.contentId.replaceAll("[^A-Za-z0-9_-]","_");
                String entry="images/"+safe+"_"+(n++)+extension(f.getName());JSONObject ph=new JSONObject();ph.put("file",entry);photos.put(ph);files.put(entry,f);}
            item.put("photos",photos);list.put(item);
        }
        manifest.put("blocks",list);
        try(ZipOutputStream zip=new ZipOutputStream(raw,StandardCharsets.UTF_8)){
            zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            byte[] buffer=new byte[32768];
            for(Map.Entry<String,File> e:files.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));
                try(InputStream in=new java.io.FileInputStream(e.getValue())){int r;while((r=in.read(buffer))!=-1)zip.write(buffer,0,r);}zip.closeEntry();}
            zip.finish();
        }
    }

    public static byte[] exportToBytes(Context context, AppDb db) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        JSONObject manifest = new JSONObject();
        manifest.put("format", "lineup");
        manifest.put("schemaVersion", 1);
        manifest.put("packId", "community");
        manifest.put("version", System.currentTimeMillis() / 1000L);
        manifest.put("title", "Lineup — база раскидок");
        JSONArray blocks = new JSONArray();
        Map<String, File> files = new HashMap<>();
        int index = 0;
        for (Block b : db.list("")) {
            JSONObject item = new JSONObject();
            item.put("id", b.contentId);
            item.put("title", b.title);
            item.put("map", b.map);
            item.put("category", b.category);
            item.put("side", b.side);
            item.put("description", b.description);
            JSONArray photos = new JSONArray();
            for (String path : b.photos) {
                File image = new File(path);
                if (!image.isFile()) continue;
                String safe = b.contentId.replaceAll("[^A-Za-z0-9_-]", "_");
                String name = "images/" + safe + "_" + (index++) + extension(image.getName());
                JSONObject photo = new JSONObject();
                photo.put("file", name);
                photos.put(photo);
                files.put(name, image);
            }
            item.put("photos", photos);
            blocks.put(item);
        }
        if (blocks.length() == 0) throw new IllegalStateException("Сначала создай хотя бы один блок.");
        manifest.put("blocks", blocks);
        try (ZipOutputStream zip = new ZipOutputStream(raw, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            byte[] buffer = new byte[32768];
            for (Map.Entry<String, File> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                try (InputStream input = new java.io.FileInputStream(entry.getValue())) {
                    int read;
                    while ((read = input.read(buffer)) != -1) zip.write(buffer, 0, read);
                }
                zip.closeEntry();
            }
            zip.finish();
        }
        return raw.toByteArray();
    }

    private static String extension(String name){String lower=name.toLowerCase(java.util.Locale.ROOT);if(lower.endsWith(".png"))return ".png";if(lower.endsWith(".webp"))return ".webp";return ".jpg";}
    public static int importFromUri(Context context,AppDb db,Uri uri)throws Exception{
        InputStream in=context.getContentResolver().openInputStream(uri);if(in==null)throw new IllegalStateException("Не удалось прочитать пакет");
        try{return importBytes(context,db,readLimited(in,MAX_PACK_BYTES));}finally{in.close();}
    }
    public static int importBytes(Context context,AppDb db,byte[] bytes)throws Exception{
        if(bytes==null||bytes.length==0||bytes.length>MAX_PACK_BYTES)throw new IllegalArgumentException("Размер пакета недопустим");
        Map<String,byte[]> entries=new HashMap<>();int total=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){
            ZipEntry e;byte[] buffer=new byte[32768];
            while((e=zip.getNextEntry())!=null){String name=e.getName();if(e.isDirectory())continue;
                if(name.startsWith("/")||name.contains("..")||!(name.equals("manifest.json")||name.startsWith("images/")))throw new IllegalArgumentException("В пакете найден недопустимый путь");
                ByteArrayOutputStream out=new ByteArrayOutputStream();int r;
                while((r=zip.read(buffer))!=-1){total+=r;if(total>MAX_PACK_BYTES)throw new IllegalArgumentException("Пакет слишком большой после распаковки");out.write(buffer,0,r);}
                entries.put(name,out.toByteArray());zip.closeEntry();
            }
        }
        byte[] manifestBytes=entries.get("manifest.json");if(manifestBytes==null)throw new IllegalArgumentException("Нет manifest.json");
        JSONObject manifest=new JSONObject(new String(manifestBytes,StandardCharsets.UTF_8));
        if(!"lineup".equals(manifest.optString("format"))||manifest.optInt("schemaVersion",0)!=1)throw new IllegalArgumentException("Неизвестный формат пакета");
        String packId=manifest.optString("packId","community");if(packId.trim().isEmpty()||packId.length()>100)throw new IllegalArgumentException("Неправильный ID пакета");
        JSONArray list=manifest.optJSONArray("blocks");if(list==null||list.length()>5000)throw new IllegalArgumentException("Неправильный список блоков");
        File dir=new File(context.getFilesDir(),"lineup_images");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Не удалось создать хранилище фото");
        Set<String> ids=new HashSet<>();int imported=0;
        for(int i=0;i<list.length();i++){
            JSONObject item=list.getJSONObject(i);Block b=new Block();b.contentId=item.optString("id",UUID.randomUUID().toString());
            if(b.contentId.trim().isEmpty()||b.contentId.length()>150||!ids.add(b.contentId))throw new IllegalArgumentException("Неправильный или повторяющийся ID блока");
            b.sourcePackId=packId;b.title=item.optString("title","").trim();b.map=item.optString("map","").trim();b.category=item.optString("category","Раскидка").trim();
            b.side=item.optString("side","Любая сторона").trim();b.description=item.optString("description","").trim();b.updatedAt=System.currentTimeMillis();
            if(b.title.isEmpty())throw new IllegalArgumentException("В одном из блоков нет названия");
            List<String> imagePaths=new ArrayList<>();JSONArray photos=item.optJSONArray("photos");
            if(photos!=null){if(photos.length()>12)throw new IllegalArgumentException("В блоке больше 12 фото");
                for(int p=0;p<photos.length();p++){String name=photos.getJSONObject(p).optString("file","");
                    if(!name.startsWith("images/")||name.contains(".."))throw new IllegalArgumentException("Неправильный путь фото");
                    byte[] image=entries.get(name);if(image==null||image.length==0||image.length>20*1024*1024)throw new IllegalArgumentException("Фото отсутствует или слишком большое: "+name);
                    File target=new File(dir,UUID.randomUUID().toString()+extension(name));try(FileOutputStream out=new FileOutputStream(target)){out.write(image);}imagePaths.add(target.getAbsolutePath());
                }}
            db.save(b,imagePaths);imported++;
        }
        db.deleteMissingFromPack(packId,ids);return imported;
    }
    public static String sha256(byte[] bytes)throws Exception{byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    public static byte[] readLimited(InputStream in,int max)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[32768];int total=0,r;while((r=in.read(buffer))!=-1){total+=r;if(total>max)throw new IllegalArgumentException("Файл слишком большой");out.write(buffer,0,r);}return out.toByteArray();}
}
