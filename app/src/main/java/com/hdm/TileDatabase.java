package com.hdm;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;

import java.io.File;

public class TileDatabase extends SQLiteOpenHelper {

    private String dbName;

    public TileDatabase(Context context, String dbName) {
        super(context, getDbPath(dbName), null, 1);
        this.dbName = dbName;
    }

    private static String getDbPath(String dbName) {
        File sd = Environment.getExternalStorageDirectory();
        File dir = new File(sd, "1hdm");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, dbName).getAbsolutePath();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 严格遵循 MBTiles 1.3 规范
        db.execSQL("CREATE TABLE metadata (name TEXT, value TEXT);");
        db.execSQL("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB);");
        db.execSQL("CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row);");

        // 写入标准的元数据（QGIS 等软件读取的关键）
        ContentValues values = new ContentValues();
        values.put("name", "format");
        values.put("value", "png");
        db.insert("metadata", null, values);
        
        values.clear();
        values.put("name", "type");
        values.put("value", "baselayer");
        db.insert("metadata", null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS metadata");
        db.execSQL("DROP TABLE IF EXISTS tiles");
        onCreate(db);
    }

    // 保存瓦片（标准的 TMS Y轴翻转）
    public void saveTile(int z, int x, int y, byte[] data) {
        int tmsY = (1 << z) - 1 - y; 
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("zoom_level", z);
        values.put("tile_column", x);
        values.put("tile_row", tmsY);
        values.put("tile_data", data);
        db.insertWithOnConflict("tiles", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    // 读取瓦片
    public byte[] getTile(int z, int x, int y) {
        int tmsY = (1 << z) - 1 - y;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query("tiles", new String[]{"tile_data"},
                "zoom_level = ? AND tile_column = ? AND tile_row = ?",
                new String[]{String.valueOf(z), String.valueOf(x), String.valueOf(tmsY)},
                null, null, null);

        byte[] data = null;
        if (cursor != null && cursor.moveToFirst()) {
            data = cursor.getBlob(0);
            cursor.close();
        }
        db.close();
        return data;
    }
}
