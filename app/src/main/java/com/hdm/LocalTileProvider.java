package com.hdm;

import android.os.Environment;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LocalTileProvider {

    public static File getCacheDir() {
        File sd = Environment.getExternalStorageDirectory();
        File dir = new File(sd, "1hdm/.map_cache");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static WebResourceResponse interceptTile(String url) {
        // 只拦截瓦片请求
        if (!url.contains("google.com/vt") && !url.contains("autonavi.com")) {
            return null; 
        }

        try {
            // 1. 提取 z, x, y
            int x = 0, y = 0, z = 0;
            String[] parts = url.split("&");
            for (String part : parts) {
                if (part.contains("x=")) x = Integer.parseInt(part.replaceAll("\\D+", ""));
                if (part.contains("y=")) y = Integer.parseInt(part.replaceAll("\\D+", ""));
                if (part.contains("z=")) z = Integer.parseInt(part.replaceAll("\\D+", ""));
            }

            // 2. 核心修复：提取图源类型 (r=路网, y=卫星)
            String type = "unknown";
            if (url.contains("lyrs=r")) type = "r";
            else if (url.contains("lyrs=y")) type = "y";
            else if (url.contains("style=7")) type = "gaode_r";
            else if (url.contains("style=6")) type = "gaode_y";

            // 3. 文件名带上 type 前缀，彻底分开！
            File tileFile = new File(getCacheDir(), type + "_" + z + "_" + x + "_" + y + ".png");

            // 本地有缓存，直接返回
            if (tileFile.exists() && tileFile.length() > 0) {
                FileInputStream is = new FileInputStream(tileFile);
                return new WebResourceResponse("image/png", "UTF-8", is);
            }

            // 本地没有，联网下载
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

                // 存入本地（带 type 前缀）
                try {
                    FileOutputStream fos = new FileOutputStream(tileFile);
                    fos.write(imageBytes);
                    fos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
                return new WebResourceResponse("image/png", "UTF-8", bais);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; 
    }
}
