package ru.selteck.lineup;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AppDb extends SQLiteOpenHelper {
    private static final int DB_VERSION = 2;
    public AppDb(Context context) { super(context, "lineup.db", null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE blocks (_id INTEGER PRIMARY KEY AUTOINCREMENT, content_id TEXT NOT NULL UNIQUE, source_pack_id TEXT NOT NULL DEFAULT '', title TEXT NOT NULL, map_name TEXT NOT NULL DEFAULT '', category TEXT NOT NULL DEFAULT 'Раскидка', side TEXT NOT NULL DEFAULT 'Атака', plant TEXT NOT NULL DEFAULT '', grenade_type TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE photos (_id INTEGER PRIMARY KEY AUTOINCREMENT, block_id INTEGER NOT NULL, path TEXT NOT NULL, caption TEXT NOT NULL DEFAULT '', sort_index INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(block_id) REFERENCES blocks(_id) ON DELETE CASCADE)");
        createVideosTable(db);
        db.execSQL("CREATE INDEX idx_blocks_updated ON blocks(updated_at DESC)");
        db.execSQL("CREATE INDEX idx_photos_block ON photos(block_id, sort_index)");
    }

    private void createVideosTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS videos (_id INTEGER PRIMARY KEY AUTOINCREMENT, block_id INTEGER NOT NULL, path TEXT NOT NULL, sort_index INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(block_id) REFERENCES blocks(_id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_videos_block ON videos(block_id, sort_index)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE blocks ADD COLUMN plant TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE blocks ADD COLUMN grenade_type TEXT NOT NULL DEFAULT ''");
            createVideosTable(db);
            db.execSQL("UPDATE blocks SET side='Атака' WHERE side='' OR side='Любая сторона'");
        }
    }

    public synchronized List<Block> list(String query) {
        ArrayList<Block> result = new ArrayList<>();
        String q = query == null ? "" : query.trim();
        Cursor c;
        if (q.isEmpty()) c = getReadableDatabase().rawQuery("SELECT * FROM blocks ORDER BY updated_at DESC, _id DESC", null);
        else {
            String like = "%" + q + "%";
            c = getReadableDatabase().rawQuery("SELECT * FROM blocks WHERE title LIKE ? OR map_name LIKE ? OR category LIKE ? OR side LIKE ? OR plant LIKE ? OR grenade_type LIKE ? OR description LIKE ? ORDER BY updated_at DESC, _id DESC",
                new String[]{like, like, like, like, like, like, like});
        }
        try { while (c.moveToNext()) { Block b = fromCursor(c); b.photos = photos(b.rowId); b.videos = videos(b.rowId); result.add(b); } }
        finally { c.close(); }
        return result;
    }

    public synchronized Block get(long rowId) {
        Cursor c = getReadableDatabase().query("blocks", null, "_id=?", new String[]{String.valueOf(rowId)}, null, null, null);
        try { if (!c.moveToFirst()) return null; Block b = fromCursor(c); b.photos = photos(rowId); b.videos = videos(rowId); return b; }
        finally { c.close(); }
    }

    public synchronized List<String> photos(long rowId) { return readMedia("photos", rowId); }
    public synchronized List<String> videos(long rowId) { return readMedia("videos", rowId); }

    private List<String> readMedia(String table,long rowId) {
        ArrayList<String> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(table, new String[]{"path"}, "block_id=?", new String[]{String.valueOf(rowId)}, null, null, "sort_index ASC, _id ASC");
        try { while (c.moveToNext()) list.add(c.getString(0)); } finally { c.close(); }
        return list;
    }

    private Block fromCursor(Cursor c) {
        Block b = new Block();
        b.rowId = c.getLong(c.getColumnIndexOrThrow("_id")); b.contentId = c.getString(c.getColumnIndexOrThrow("content_id"));
        b.sourcePackId = c.getString(c.getColumnIndexOrThrow("source_pack_id")); b.title = c.getString(c.getColumnIndexOrThrow("title"));
        b.map = c.getString(c.getColumnIndexOrThrow("map_name")); b.category = c.getString(c.getColumnIndexOrThrow("category"));
        b.side = c.getString(c.getColumnIndexOrThrow("side")); b.plant = c.getString(c.getColumnIndexOrThrow("plant"));
        b.grenadeType = c.getString(c.getColumnIndexOrThrow("grenade_type")); b.description = c.getString(c.getColumnIndexOrThrow("description"));
        b.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")); return b;
    }

    public synchronized void save(Block b, List<String> images) { save(b, images, b == null ? null : b.videos); }

    public synchronized void save(Block b, List<String> images, List<String> videoPaths) {
        SQLiteDatabase db = getWritableDatabase();
        if (b.contentId == null || b.contentId.trim().isEmpty()) b.contentId = UUID.randomUUID().toString();
        b.updatedAt = System.currentTimeMillis(); db.beginTransaction();
        try {
            ContentValues cv = values(b); long id = b.rowId;
            if (id > 0 && db.update("blocks", cv, "_id=?", new String[]{String.valueOf(id)}) == 0) id = -1;
            if (id <= 0) {
                Cursor c = db.query("blocks", new String[]{"_id"}, "content_id=?", new String[]{b.contentId}, null, null, null);
                try { if (c.moveToFirst()) { id = c.getLong(0); db.update("blocks", cv, "_id=?", new String[]{String.valueOf(id)}); } else id = db.insertOrThrow("blocks", null, cv); }
                finally { c.close(); }
            }
            db.delete("photos", "block_id=?", new String[]{String.valueOf(id)});
            db.delete("videos", "block_id=?", new String[]{String.valueOf(id)});
            int order=0;
            if(images!=null)for(String path:images){if(path==null||path.trim().isEmpty())continue;ContentValues p=new ContentValues();p.put("block_id",id);p.put("path",path);p.put("caption","");p.put("sort_index",order++);db.insertOrThrow("photos",null,p);}
            order=0;
            if(videoPaths!=null)for(String path:videoPaths){if(path==null||path.trim().isEmpty())continue;ContentValues p=new ContentValues();p.put("block_id",id);p.put("path",path);p.put("sort_index",order++);db.insertOrThrow("videos",null,p);}
            db.setTransactionSuccessful();b.rowId=id;b.photos=images==null?new ArrayList<>():new ArrayList<>(images);b.videos=videoPaths==null?new ArrayList<>():new ArrayList<>(videoPaths);
        } finally { db.endTransaction(); }
    }

    private ContentValues values(Block b) {
        ContentValues cv = new ContentValues(); cv.put("content_id",b.contentId);
        cv.put("source_pack_id",b.sourcePackId==null?"":b.sourcePackId);
        cv.put("title",b.title==null?"":b.title.trim());cv.put("map_name",b.map==null?"":b.map.trim());
        cv.put("category",b.category==null?"Раскидка":b.category.trim());cv.put("side",b.side==null?"Атака":b.side.trim());
        cv.put("plant",b.plant==null?"":b.plant.trim());cv.put("grenade_type",b.grenadeType==null?"":b.grenadeType.trim());
        cv.put("description",b.description==null?"":b.description.trim());cv.put("updated_at",b.updatedAt);return cv;
    }

    public synchronized void delete(long rowId) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{db.delete("photos","block_id=?",new String[]{String.valueOf(rowId)});db.delete("videos","block_id=?",new String[]{String.valueOf(rowId)});db.delete("blocks","_id=?",new String[]{String.valueOf(rowId)});db.setTransactionSuccessful();}
        finally{db.endTransaction();}
    }

    public synchronized void deleteMissingFromPack(String packId,Set<String> incomingIds) {
        if(packId==null||packId.isEmpty())return;
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            Cursor c=db.query("blocks",new String[]{"_id","content_id"},"source_pack_id=?",new String[]{packId},null,null,null);
            ArrayList<Long> remove=new ArrayList<>();
            try{while(c.moveToNext())if(!incomingIds.contains(c.getString(1)))remove.add(c.getLong(0));}finally{c.close();}
            for(Long id:remove){db.delete("photos","block_id=?",new String[]{String.valueOf(id)});db.delete("videos","block_id=?",new String[]{String.valueOf(id)});db.delete("blocks","_id=?",new String[]{String.valueOf(id)});}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}
