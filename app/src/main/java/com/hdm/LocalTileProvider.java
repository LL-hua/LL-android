package com.hdm;

import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LocalTileProvider {

    private static TileDatabase dbRoad = null;
    private static TileDatabase dbSat = null;
    private static TileDatabase dbTopo = null;

    public static void init(android.content.Context context) {
        if (dbRoad == null) dbRoad = new TileDatabase(context.getApplicationContext(), "hdm_road.mbtiles");
        if (dbSat == null) dbSat = new TileDatabase(context.getApplicationContext(), "hdm_sat.mbtiles");
        if (dbTopo == null) dbTopo = new TileDatabase(context.getApplicationContext(), "hdm_topo.mbtiles");
    }

    public static WebResourceResponse interceptTile(String url) {
        if (!url.contains("google.com/vt") && !url.contains("opentopomap.org")) {
            return null;
        }

        try {
            int x = 0, y = 0, z = 0;
            String[] parts = url.split("&");
            for (String part : parts) {
                if (part.contains("x=")) x = Integer.parseInt(part.replaceAll("\\D+", ""));
                if (part.contains("y=")) y = Integer.parseInt(part.replaceAll("\\D+", ""));
                if (part.contains("z=")) z = Integer.parseInt(part.replaceAll("\\D+", ""));
            }

            TileDatabase currentDb = null;
            String urlFormat = "image/png";
            
            if (url.contains("google.com/vt")) {
                if (url.contains("lyrs=r")) {
                    currentDb = dbRoad;
                } else if (url.contains("lyrs=y")) {
                    currentDb = dbSat;
                }
            } else if (url.contains("opentopomap.org")) {
                currentDb = dbTopo;
            }

            if (currentDb == null) return null;

            // 1. 查本地数据库
            byte[] cachedData = currentDb.getTile(z, x, y);
            if (cachedData != null && cachedData.length > 0) {
                return new WebResourceResponse(urlFormat, "UTF-8", new ByteArrayInputStream(cachedData));
            }

            // 2. 联网下载
            URL u = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            if (conn.getResponseCode() == 200) {
                InputStream netIs = conn.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = netIs.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                netIs.close();
                byte[] imageBytes = baos.toByteArray();

                // 3. 存进对应数据库
                if (imageBytes.length > 0) {
                    currentDb.saveTile(z, x, y, imageBytes);
                }

                return new WebResourceResponse(urlFormat, "UTF-8", new ByteArrayInputStream(imageBytes));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
