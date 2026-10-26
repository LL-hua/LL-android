package com.hdm;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CanvasMapView extends View {

    private Paint linePaint;
    private Paint debugPaint;
    private Paint tilePaint;
    private Paint textPaint;
    private Paint textBgPaint;
    private Paint gpsPaint;
    private Paint gpsStrokePaint;
    
    // 地图中心经纬度
    private double centerLat = -4.8;
    private double centerLng = 32.5;
    private float zoom = 15.0f;

    // 手势的物理像素偏移量（未提交到经纬度的缓冲）
    private float offsetX = 0;
    private float offsetY = 0;

    // 手势状态
    private float lastX, lastY;
    private float lastDist = 0;

    public static final int MAP_ROAD = 0;
    public static final int MAP_SATELLITE = 1;
    public static final int MAP_TOPO = 2;
    private int currentMapType = MAP_ROAD;

    private List<Object[]> routeData;
    
    private double gpsLat = 0, gpsLng = 0;
    private String gpsKStr = "--";
    private String gpsBStr = "--";
    private boolean hasGps = false;

    private ExecutorService executor = Executors.newFixedThreadPool(4);
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private LruCache<String, Bitmap> memoryCache;
    private java.util.HashSet<String> downloadingKeys = new java.util.HashSet<>();
    
    private TileDatabase dbRoad;
    private TileDatabase dbSat;
    private TileDatabase dbTopo;

    // 高清屏放大倍数
    private float scaleFactor = 2.0f; 

    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();

    public CanvasMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint = new Paint();
        linePaint.setColor(Color.RED);
        linePaint.setStrokeWidth(8f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);
        
        debugPaint = new Paint();
        debugPaint.setColor(Color.GRAY);

        tilePaint = new Paint();
        tilePaint.setFilterBitmap(true); 
        tilePaint.setAntiAlias(true);
        tilePaint.setDither(true);

        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(14f); 
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.LEFT);

        textBgPaint = new Paint();
        textBgPaint.setColor(Color.WHITE);
        textBgPaint.setStyle(Paint.Style.FILL);

        gpsPaint = new Paint();
        gpsPaint.setColor(0xFF0078D4);
        gpsPaint.setStyle(Paint.Style.FILL);
        gpsPaint.setAntiAlias(true);

        gpsStrokePaint = new Paint();
        gpsStrokePaint.setColor(Color.WHITE);
        gpsStrokePaint.setStyle(Paint.Style.STROKE);
        gpsStrokePaint.setStrokeWidth(4f);
        gpsStrokePaint.setAntiAlias(true);

        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        Context ctx = getContext().getApplicationContext();
        dbRoad = new TileDatabase(ctx, "hdm_road.mbtiles");
        dbSat = new TileDatabase(ctx, "hdm_sat.mbtiles");
        dbTopo = new TileDatabase(ctx, "hdm_topo.mbtiles");
    }

    public void setMapType(int type) {
        this.currentMapType = type;
        invalidate();
    }

    public void setRouteData(List<Object[]> route) {
        this.routeData = route;
        invalidate();
    }

    public void updateGps(double lat, double lng, String kStr, String bStr) {
        this.gpsLat = lat;
        this.gpsLng = lng;
        this.gpsKStr = kStr;
        this.gpsBStr = bStr;
        this.hasGps = true;
        invalidate();
    }

    public void locateMe() {
        if (!hasGps) return;
        
        final double startLat = centerLat;
        final double startLng = centerLng;
        final float startZoom = zoom;
        
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(600);
        animator.addUpdateListener(animation -> {
            float t = animation.getAnimatedFraction();
            float ease = 1 - (1 - t) * (1 - t); 
            
            centerLat = startLat + (gpsLat - startLat) * ease;
            centerLng = startLng + (gpsLng - startLng) * ease;
            zoom = startZoom + (17.0f - startZoom) * ease;
            // 飞过去的时候，清空物理偏移
            offsetX = 0;
            offsetY = 0;
            invalidate();
        });
        animator.start();
    }

    private TileDatabase getCurrentDb() {
        if (currentMapType == MAP_SATELLITE) return dbSat;
        if (currentMapType == MAP_TOPO) return dbTopo;
        return dbRoad;
    }

    private double[] latLngToWorldPixel(double lat, double lng, float z) {
        double worldSize = 256 * Math.pow(2, z);
        double x = (lng + 180.0) / 360.0 * worldSize;
        double sinLat = Math.sin(lat * Math.PI / 180.0);
        double y = (0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI)) * worldSize;
        return new double[]{x, y};
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.LTGRAY);

        int width = getWidth();
        int height = getHeight();

        int tileZoom = (int) Math.floor(zoom);
        float subZoomScale = (float) Math.pow(2, zoom - tileZoom); 

        double[] centerWorld = latLngToWorldPixel(centerLat, centerLng, tileZoom);
        
        // 核心改进：这里减去物理偏移量 offsetX 和 offsetY，这样画布在视觉上就跟着手指走了
        double startX = centerWorld[0] - (width / 2.0) / (scaleFactor * subZoomScale) - (offsetX / (scaleFactor * subZoomScale));
        double startY = centerWorld[1] - (height / 2.0) / (scaleFactor * subZoomScale) - (offsetY / (scaleFactor * subZoomScale));

        int minTileX = (int) Math.floor(startX / 256);
        int maxTileX = (int) Math.floor((startX + width / (scaleFactor * subZoomScale)) / 256);
        int minTileY = (int) Math.floor(startY / 256);
        int maxTileY = (int) Math.floor((startY + height / (scaleFactor * subZoomScale)) / 256);

        canvas.save();
        canvas.scale(scaleFactor * subZoomScale, scaleFactor * subZoomScale); 

        // 画瓦片
        for (int tx = minTileX; tx <= maxTileX; tx++) {
            for (int ty = minTileY; ty <= maxTileY; ty++) {
                float screenX = (float) (tx * 256 - startX);
                float screenY = (float) (ty * 256 - startY);
                boolean drawn = drawTileWithFallback(canvas, tileZoom, tx, ty, screenX, screenY);
                if (!drawn) {
                    canvas.drawRect(screenX, screenY, screenX + 256, screenY + 256, debugPaint);
                }
            }
        }

        // 画路线与桩号
        if (routeData != null && !routeData.isEmpty()) {
            Path path = new Path();
            float lastKmPixelX = -99999;
            float lastKmPixelY = -99999;

            for (int i = 0; i < routeData.size(); i++) {
                Object[] pt = routeData.get(i);
                double lat = (Double) pt[0];
                double lng = (Double) pt[1];
                String kStr = (String) pt[2];
                double fwj = (Double) pt[3];

                double[] worldPx = latLngToWorldPixel(lat, lng, tileZoom);
                float x = (float) (worldPx[0] - startX);
                float y = (float) (worldPx[1] - startY);

                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);

                if (kStr.endsWith("00")) { 
                    float dist = (float) Math.sqrt(Math.pow(x - lastKmPixelX, 2) + Math.pow(y - lastKmPixelY, 2));
                    if (dist > 120) { 
                        lastKmPixelX = x;
                        lastKmPixelY = y;
                        float angleDeg = (float) (fwj * 180 / Math.PI);

                        canvas.save();
                        canvas.translate(x, y);
                        canvas.rotate(angleDeg); 
                        float textWidth = textPaint.measureText(kStr);
                        canvas.drawRect(4, -12, 4 + textWidth + 2, 4, textBgPaint);
                        canvas.drawText(kStr, 4, 0, textPaint);
                        canvas.restore();
                    }
                }
            }
            canvas.drawPath(path, linePaint);
        }

        // 画蓝点
        if (hasGps) {
            double[] gpsWorld = latLngToWorldPixel(gpsLat, gpsLng, tileZoom);
            float gx = (float) (gpsWorld[0] - startX);
            float gy = (float) (gpsWorld[1] - startY);

            canvas.drawCircle(gx, gy, 12, gpsPaint);
            canvas.drawCircle(gx, gy, 12, gpsStrokePaint);
        }

        canvas.restore();

        // 左上角信息框
        if (hasGps) {
            Paint uiTextPaint = new Paint(textPaint);
            uiTextPaint.setTextSize(40f); 
            
            Paint uiBgPaint = new Paint();
            uiBgPaint.setColor(0xF0FFFFFF); 
            uiBgPaint.setStyle(Paint.Style.FILL);
            uiBgPaint.setAntiAlias(true);
            
            String info = "桩号: " + gpsKStr + "\n偏距: " + gpsBStr;
            String[] lines = info.split("\n");
            
            float textW = 0;
            for (String line : lines) {
                textW = Math.max(textW, uiTextPaint.measureText(line));
            }
            float textH = uiTextPaint.getTextSize() * lines.length * 1.3f;
            
            float padLeft = 30f;
            float padTop = 30f;
            float boxMargin = 30f;
            
            RectF bgRect = new RectF(boxMargin, boxMargin, boxMargin + textW + padLeft * 2, boxMargin + textH + padTop);
            canvas.drawRoundRect(bgRect, 20f, 20f, uiBgPaint);
            
            for (int i = 0; i < lines.length; i++) {
                canvas.drawText(lines[i], boxMargin + padLeft, boxMargin + padTop + uiTextPaint.getTextSize() * (i + 0.8f), uiTextPaint);
            }
        }
    }

    private boolean drawTileWithFallback(Canvas canvas, int z, int x, int y, float screenX, float screenY) {
        Bitmap targetTile = getTile(z, x, y);
        if (targetTile != null) {
            canvas.drawBitmap(targetTile, screenX, screenY, tilePaint);
            return true;
        }
        int pZ = z - 1;
        if (pZ >= 0) {
            int pX = x >> 1;
            int pY = y >> 1;
            Bitmap parentTile = getTileDirect(pZ, pX, pY);
            if (parentTile != null) {
                int xOffset = (x % 2 == 0) ? 0 : 128;
                int yOffset = (y % 2 == 0) ? 0 : 128;
                srcRect.set(xOffset, yOffset, xOffset + 128, yOffset + 128);
                dstRect.set(screenX, screenY, screenX + 256, screenY + 256);
                canvas.drawBitmap(parentTile, srcRect, dstRect, tilePaint);
                return true;
            }
        }
        return false;
    }

    private Bitmap getTileDirect(int z, int x, int y) {
        String key = currentMapType + "_" + z + "/" + x + "/" + y;
        return memoryCache.get(key);
    }

    // ==========================================
    // 最纯粹的物理偏移手势（稳如泰山）
    // ==========================================
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            lastX = event.getX();
            lastY = event.getY();
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (event.getPointerCount() >= 2) {
                lastDist = distance(event);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (event.getPointerCount() == 1) {
                // 单指平移：只累加物理像素偏移
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                offsetX += dx;
                offsetY += dy;
                lastX = event.getX();
                lastY = event.getY();
                invalidate();
            } else if (event.getPointerCount() >= 2) {
                // 双指缩放
                float newDist = distance(event);
                if (newDist > 10 && lastDist > 10) {
                    float scale = newDist / lastDist;
                    float nextZoom = zoom + (float) (Math.log(scale) / Math.log(2));
                    if (nextZoom >= 3.0f && nextZoom <= 18.0f) {
                        zoom = nextZoom;
                        // 注意：由于这里是按新的 zoom 画图了，中心点不变的话视觉上确实会偏。
                        // 最简单粗暴的修正：让屏幕中心点保持不动。
                        // 为了简化计算，我们直接清零 offset。
                        offsetX = 0;
                        offsetY = 0;
                        invalidate();
                    }
                    lastDist = newDist;
                }
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // 手指抬起时，把物理像素偏移量一次性反算成经纬度的改变
            if (offsetX != 0 || offsetY != 0) {
                double worldSize = 256 * Math.pow(2, zoom);
                
                centerLng -= (offsetX / (scaleFactor * worldSize)) * 360.0;
                
                double latRad = centerLat * Math.PI / 180.0;
                centerLat += (offsetY / (scaleFactor * worldSize)) * 360.0 * Math.cos(latRad);
                
                offsetX = 0;
                offsetY = 0;
                invalidate();
            }
            return true;
        }

        return super.onTouchEvent(event);
    }

    private float distance(MotionEvent event) {
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private Bitmap getTile(int z, int x, int y) {
        int maxTile = (int) Math.pow(2, z) - 1;
        if (x < 0 || y < 0 || x > maxTile || y > maxTile) return null;
        String key = currentMapType + "_" + z + "/" + x + "/" + y;
        Bitmap bmp = memoryCache.get(key);
        if (bmp != null) return bmp;

        TileDatabase db = getCurrentDb();
        if (db != null) {
            byte[] blob = db.getTile(z, x, y);
            if (blob != null) {
                Bitmap dbBmp = BitmapFactory.decodeByteArray(blob, 0, blob.length);
                if (dbBmp != null) {
                    memoryCache.put(key, dbBmp);
                    return dbBmp;
                }
            }
        }

        if (downloadingKeys.contains(key)) return null;
        downloadingKeys.add(key);
        final int reqType = currentMapType;
        
        executor.execute(() -> {
            try {
                String urlStr = "";
                if (reqType == MAP_ROAD) urlStr = "https://mt1.google.com/vt/lyrs=r&hl=zh-CN&gl=cn&x=" + x + "&y=" + y + "&z=" + z;
                else if (reqType == MAP_SATELLITE) urlStr = "https://mt1.google.com/vt/lyrs=y&hl=zh-CN&gl=cn&x=" + x + "&y=" + y + "&z=" + z;
                else urlStr = "https://a.tile.opentopomap.org/" + z + "/" + x + "/" + y + ".png";

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 hdm/1.0");
                
                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = is.read(buffer)) != -1) baos.write(buffer, 0, len);
                    is.close();
                    byte[] imageBytes = baos.toByteArray();

                    if (db != null && imageBytes.length > 0) db.saveTile(z, x, y, imageBytes);

                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    if (bitmap != null) {
                        memoryCache.put(key, bitmap);
                        mainHandler.post(this::invalidate);
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                downloadingKeys.remove(key);
            }
        });
        return null;
    }
}
