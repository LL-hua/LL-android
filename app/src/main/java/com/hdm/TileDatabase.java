package com.hdm;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;

import java.io.File;

public class TileDatabase extends SQLiteOpenHelper {

    // 核心：每个实例自己管自己的数据库连接
    private SQLiteDatabase myDb = null;

    public TileDatabase(Context context, String name) {
        super(context, getDbPath(name), null, 1);
    }

    private static String getDbPath(String name) {
        File sd = Environment.getExternalStorageDirectory();
        File dir = new File(sd, "1hdm");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name).getAbsolutePath();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE metadata (name TEXT, value TEXT);");
        db.execSQL("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB);");
        db.execSQL("CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row);");

        ContentValues values = new ContentValues();
        values.put("name", "format");
        values.put("value", "png");
        db.insert("metadata", null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS metadata");
        db.execSQL("DROP TABLE IF EXISTS tiles");
        onCreate(db);
    }

    private SQLiteDatabase getDb() {
        if (myDb == null || !myDb.isOpen()) {
            myDb = this.getWritableDatabase();
        }
        return myDb;
    }

    public synchronized void saveTile(int z, int x, int y, byte[] data) {
        int tmsY = (1 << z) - 1 - y; 
        try {
            SQLiteDatabase db = getDb();
            ContentValues values = new ContentValues();
            values.put("zoom_level", z);
            values.put("tile_column", x);
            values.put("tile_row", tmsY);
            values.put("tile_data", data);
            db.insertWithOnConflict("tiles", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized byte[] getTile(int z, int x, int y) {
        int tmsY = (1 << z) - 1 - y;
        byte[] data = null;
        Cursor cursor = null;
        try {
            SQLiteDatabase db = getDb();
            cursor = db.query("tiles", new String[]{"tile_data"},
                    "zoom_level = ? AND tile_column = ? AND tile_row = ?",
                    new String[]{String.valueOf(z), String.valueOf(x), String.valueOf(tmsY)},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                data = cursor.getBlob(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return data;
    }
}
