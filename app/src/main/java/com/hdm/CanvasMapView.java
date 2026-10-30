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
import java.util.ArrayList;
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

    private double centerLat = -4.8;
    private double centerLng = 32.5;
    private float zoom = 15.0f;

    private float lastX, lastY;
    private float lastDist = 0;

    public static final int MAP_ROAD = 0;
    public static final int MAP_SATELLITE = 1;
    public static final int MAP_TOPO = 2;
    public static final int MAP_OSM = 3;
    private int currentMapType = MAP_ROAD;

    private List<List<Object[]>> multiRoadData;
    private List<String> roadNames = null;
    private int currentRoadIndex = 0;
    private boolean showRoadNames = false; // 默认不显示路名

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
    private TileDatabase dbOsm;

    private float scaleFactor = 2.0f;

    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();

    private ValueAnimator moveAnimator;

    public CanvasMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint = new Paint();
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);

        debugPaint = new Paint();
        debugPaint.setColor(Color.GRAY);

        tilePaint = new Paint();
        tilePaint.setFilterBitmap(true);
        tilePaint.setAntiAlias(true);
        tilePaint.setDither(true);

        textPaint = new Paint();
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
        dbOsm = new TileDatabase(ctx, "hdm_osm.mbtiles");
    }

    public void setMapType(int type) {
        this.currentMapType = type;
        invalidate();
    }

    public void setShowRoadNames(boolean show) {
        this.showRoadNames = show;
        invalidate();
    }

    public void setMultiRouteData(List<List<Object[]>> multiData) {
        setMultiRouteData(multiData, null);
    }

    public void setMultiRouteData(List<List<Object[]>> multiData, List<String> names) {
        this.multiRoadData = multiData;
        this.roadNames = names;
        if (multiData != null && !multiData.isEmpty()) {
            switchRoad(0);
        }
    }

    public void switchRoad(int index) {
        this.currentRoadIndex = index;
        if (multiRoadData != null && index < multiRoadData.size()) {
            fitToRoute(multiRoadData.get(index));
        } else {
            invalidate();
        }
    }

    public void zoomToFitAll() {
        if (multiRoadData == null || multiRoadData.isEmpty()) return;

        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
        boolean hasData = false;

        for (List<Object[]> route : multiRoadData) {
            for (Object[] pt : route) {
                double lat = (Double) pt[0];
                double lng = (Double) pt[1];
                if (lat < minLat) minLat = lat;
                if (lat > maxLat) maxLat = lat;
                if (lng < minLng) minLng = lng;
                if (lng > maxLng) maxLng = lng;
                hasData = true;
            }
        }

        if (hasData) {
            animateToBounds(minLat, maxLat, minLng, maxLng);
        }
    }

    private void fitToRoute(List<Object[]> route) {
        if (route == null || route.isEmpty()) {
            invalidate();
            return;
        }

        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;

        for (Object[] pt : route) {
            double lat = (Double) pt[0];
            double lng = (Double) pt[1];
            if (lat < minLat) minLat = lat;
            if (lat > maxLat) maxLat = lat;
            if (lng < minLng) minLng = lng;
            if (lng > maxLng) maxLng = lng;
        }

        animateToBounds(minLat, maxLat, minLng, maxLng);
    }

    private void animateToBounds(double minLat, double maxLat, double minLng, double maxLng) {
        double centerLatTarget = (minLat + maxLat) / 2.0;
        double centerLngTarget = (minLng + maxLng) / 2.0;

        float calculatedZoom = 15.0f;
        if (maxLat > minLat && maxLng > minLng) {
            int tileZoom = 15;
            double[] p1 = latLngToWorldPixel(maxLat, minLng, tileZoom);
            double[] p2 = latLngToWorldPixel(minLat, maxLng, tileZoom);
            double w = Math.abs(p2[0] - p1[0]);
            double h = Math.abs(p1[1] - p2[1]);

            float screenWidth = getWidth();
            float screenHeight = getHeight();
            if (screenWidth == 0 || screenHeight == 0) {
                screenWidth = getResources().getDisplayMetrics().widthPixels;
                screenHeight = getResources().getDisplayMetrics().heightPixels;
            }

            double pixelW = screenWidth - 120;
            double pixelH = screenHeight - 120;
            if (pixelW < 100) pixelW = 100;
            if (pixelH < 100) pixelH = 100;

            float scaleX = (float) (pixelW / (w * scaleFactor));
            float scaleY = (float) (pixelH / (h * scaleFactor));
            float scale = Math.min(scaleX, scaleY);

            calculatedZoom = (float) (tileZoom + Math.log(scale) / Math.log(2));
            if (calculatedZoom < 3.0f) calculatedZoom = 3.0f;
            if (calculatedZoom > 18.0f) calculatedZoom = 18.0f;
            if (Float.isNaN(calculatedZoom)) calculatedZoom = 15.0f;
        }

        final float targetZoom = calculatedZoom;

        final double startLat = centerLat;
        final double startLng = centerLng;
        final float startZoom = zoom;

        if (moveAnimator != null) moveAnimator.cancel();
        moveAnimator = ValueAnimator.ofFloat(0f, 1f);
        moveAnimator.setDuration(600);
        moveAnimator.addUpdateListener(animation -> {
            float t = animation.getAnimatedFraction();
            float ease = 1 - (1 - t) * (1 - t);

            centerLat = startLat + (centerLatTarget - startLat) * ease;
            centerLng = startLng + (centerLngTarget - startLng) * ease;
            zoom = startZoom + (targetZoom - startZoom) * ease;
            invalidate();
        });
        moveAnimator.start();
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

        if (moveAnimator != null) moveAnimator.cancel();
        moveAnimator = ValueAnimator.ofFloat(0f, 1f);
        moveAnimator.setDuration(600);
        moveAnimator.addUpdateListener(animation -> {
            float t = animation.getAnimatedFraction();
            float ease = 1 - (1 - t) * (1 - t);

            centerLat = startLat + (gpsLat - startLat) * ease;
            centerLng = startLng + (gpsLng - startLng) * ease;
            zoom = startZoom + (17.0f - startZoom) * ease;
            invalidate();
        });
        moveAnimator.start();
    }

    private TileDatabase getCurrentDb() {
        if (currentMapType == MAP_SATELLITE) return dbSat;
        if (currentMapType == MAP_TOPO) return dbTopo;
        if (currentMapType == MAP_OSM) return dbOsm;
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
        float totalScale = scaleFactor * subZoomScale;

        double[] centerWorld = latLngToWorldPixel(centerLat, centerLng, tileZoom);
        double startX = centerWorld[0] - (width / 2.0) / (scaleFactor * subZoomScale);
        double startY = centerWorld[1] - (height / 2.0) / (scaleFactor * subZoomScale);

        int minTileX = (int) Math.floor(startX / 256);
        int maxTileX = (int) Math.floor((startX + width / (scaleFactor * subZoomScale)) / 256);
        int minTileY = (int) Math.floor(startY / 256);
        int maxTileY = (int) Math.floor((startY + height / (scaleFactor * subZoomScale)) / 256);

        // 路名标注锚点：{roadIdx(Integer), screenX(Float), screenY(Float), angleDeg(Float)}
        List<Object[]> roadLabels = new ArrayList<>();

        canvas.save();
        canvas.scale(scaleFactor * subZoomScale, scaleFactor * subZoomScale);

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

        if (multiRoadData != null && !multiRoadData.isEmpty()) {
            // ---- 其它（灰色）线路 ----
            for (int r = 0; r < multiRoadData.size(); r++) {
                if (r == currentRoadIndex) continue;
                List<Object[]> route = multiRoadData.get(r);
                if (route == null || route.isEmpty()) continue;

                Path path = new Path();
                List<float[]> screenPts = new ArrayList<>();

                for (int i = 0; i < route.size(); i++) {
                    Object[] pt = route.get(i);
                    double[] worldPx = latLngToWorldPixel((Double) pt[0], (Double) pt[1], tileZoom);
                    float x = (float) (worldPx[0] - startX);
                    float y = (float) (worldPx[1] - startY);
                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);

                    float scrX = x * totalScale;
                    float scrY = y * totalScale;
                    if (showRoadNames && scrX >= 40 && scrX <= width - 40 && scrY >= 40 && scrY <= height - 40) {
                        screenPts.add(new float[]{scrX, scrY});
                    }
                }

                linePaint.setColor(0xFF888888);
                linePaint.setStrokeWidth(4f);
                canvas.drawPath(path, linePaint);

                if (showRoadNames && !screenPts.isEmpty()) {
                    int mi = screenPts.size() / 2;
                    float[] mid = screenPts.get(mi);
                    float angle = calcRoadAngle(screenPts, mi);
                    roadLabels.add(new Object[]{
                            Integer.valueOf(r),
                            Float.valueOf(mid[0]),
                            Float.valueOf(mid[1]),
                            Float.valueOf(angle)
                    });
                }
            }

            // ---- 当前（红色）线路 ----
            List<Object[]> currentRoute = multiRoadData.get(currentRoadIndex);
            if (currentRoute != null && !currentRoute.isEmpty()) {
                Path path = new Path();
                float lastKmPixelX = -99999;
                float lastKmPixelY = -99999;
                List<float[]> screenPts = new ArrayList<>();

                for (int i = 0; i < currentRoute.size(); i++) {
                    Object[] pt = currentRoute.get(i);
                    double lat = (Double) pt[0];
                    double lng = (Double) pt[1];
                    String kStr = (String) pt[2];
                    double fwj = (Double) pt[3];

                    double[] worldPx = latLngToWorldPixel(lat, lng, tileZoom);
                    float x = (float) (worldPx[0] - startX);
                    float y = (float) (worldPx[1] - startY);

                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);

                    float scrX = x * totalScale;
                    float scrY = y * totalScale;
                    if (showRoadNames && scrX >= 40 && scrX <= width - 40 && scrY >= 40 && scrY <= height - 40) {
                        screenPts.add(new float[]{scrX, scrY});
                    }

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

                linePaint.setColor(Color.RED);
                linePaint.setStrokeWidth(8f);
                canvas.drawPath(path, linePaint);

                if (showRoadNames && !screenPts.isEmpty()) {
                    int mi = screenPts.size() / 2;
                    float[] mid = screenPts.get(mi);
                    float angle = calcRoadAngle(screenPts, mi);
                    roadLabels.add(new Object[]{
                            Integer.valueOf(currentRoadIndex),
                            Float.valueOf(mid[0]),
                            Float.valueOf(mid[1]),
                            Float.valueOf(angle)
                    });
                }
            }
        }

        if (hasGps) {
            double[] gpsWorld = latLngToWorldPixel(gpsLat, gpsLng, tileZoom);
            float gx = (float) (gpsWorld[0] - startX);
            float gy = (float) (gpsWorld[1] - startY);

            canvas.drawCircle(gx, gy, 12, gpsPaint);
            canvas.drawCircle(gx, gy, 12, gpsStrokePaint);
        }

        canvas.restore();

        // 路名标注（屏幕坐标，平行道路）
        if (showRoadNames) {
            drawRoadNameLabels(canvas, width, height, roadLabels);
        }

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

            RectF bgRect = new RectF(boxMargin, boxMargin,
                    boxMargin + textW + padLeft * 2,
                    boxMargin + textH + padTop);
            canvas.drawRoundRect(bgRect, 20f, 20f, uiBgPaint);

            for (int i = 0; i < lines.length; i++) {
                canvas.drawText(lines[i],
                        boxMargin + padLeft,
                        boxMargin + padTop + uiTextPaint.getTextSize() * (i + 0.8f),
                        uiTextPaint);
            }
        }
    }

    private float calcRoadAngle(List<float[]> pts, int midIdx) {
        if (pts == null || pts.size() < 2) return 0f;
        int i0 = Math.max(0, midIdx - 5);
        int i1 = Math.min(pts.size() - 1, midIdx + 5);
        if (i0 == i1) return 0f;
        float[] p0 = pts.get(i0);
        float[] p1 = pts.get(i1);
        float dx = p1[0] - p0[0];
        float dy = p1[1] - p0[1];
        if (Math.abs(dx) < 1e-3f && Math.abs(dy) < 1e-3f) return 0f;

        float angleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
        while (angleDeg > 90f) angleDeg -= 180f;
        while (angleDeg <= -90f) angleDeg += 180f;
        return angleDeg;
    }

    private void drawRoadNameLabels(Canvas canvas, int width, int height, List<Object[]> roadLabels) {
        if (roadNames == null || roadLabels == null || roadLabels.isEmpty()) return;

        Paint namePaint = new Paint();
        namePaint.setAntiAlias(true);
        namePaint.setTextSize(34f);
        namePaint.setTextAlign(Paint.Align.CENTER);
        namePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        Paint nameBg = new Paint();
        nameBg.setColor(0xF0FFFFFF);
        nameBg.setStyle(Paint.Style.FILL);
        nameBg.setAntiAlias(true);

        Paint nameBorder = new Paint();
        nameBorder.setStyle(Paint.Style.STROKE);
        nameBorder.setStrokeWidth(3f);
        nameBorder.setAntiAlias(true);

        float padH = 12f;
        float padV = 8f;

        for (int li = 0; li < roadLabels.size(); li++) {
            Object[] label = roadLabels.get(li);
            int r = (Integer) label[0];
            float cx = (Float) label[1];
            float cy = (Float) label[2];
            float angle = (Float) label[3];

            if (r < 0 || r >= roadNames.size()) continue;
            String name = roadNames.get(r);
            if (name == null || name.isEmpty()) continue;

            boolean isCurrent = (r == currentRoadIndex);
            namePaint.setColor(isCurrent ? 0xFFD32F2F : 0xFF444444);
            nameBorder.setColor(isCurrent ? 0xFFD32F2F : 0xFF999999);

            float textW = namePaint.measureText(name);
            float textSize = namePaint.getTextSize();
            float bw = textW + padH * 2;
            float bh = textSize + padV * 2;

            if (cx < bw / 2f + 4) cx = bw / 2f + 4;
            if (cx > width - bw / 2f - 4) cx = width - bw / 2f - 4;
            if (cy < bh / 2f + 4) cy = bh / 2f + 4;
            if (cy > height - bh / 2f - 4) cy = height - bh / 2f - 4;

            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(angle);

            float offsetY = -bh / 2f - 6f;
            float bx = -bw / 2f;
            float by = offsetY - bh / 2f;
            canvas.drawRect(bx, by, bx + bw, by + bh, nameBg);
            canvas.drawRect(bx, by, bx + bw, by + bh, nameBorder);

            Paint.FontMetrics fm = namePaint.getFontMetrics();
            float baseline = offsetY - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(name, 0, baseline, namePaint);

            canvas.restore();
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            lastX = event.getX();
            lastY = event.getY();
            if (moveAnimator != null) moveAnimator.cancel();
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (event.getPointerCount() >= 2) lastDist = distance(event);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (event.getPointerCount() == 1) {
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                double worldSize = 256 * Math.pow(2, zoom);
                centerLng -= (dx / scaleFactor) / worldSize * 360.0;
                double latRad = centerLat * Math.PI / 180.0;
                centerLat += (dy / scaleFactor) / worldSize * 360.0 * Math.cos(latRad);
                lastX = event.getX();
                lastY = event.getY();
                invalidate();
            } else if (event.getPointerCount() >= 2) {
                float newDist = distance(event);
                if (newDist > 10 && lastDist > 10) {
                    float scale = newDist / lastDist;
                    float nextZoom = zoom + (float) (Math.log(scale) / Math.log(2));

                    if (nextZoom >= 3.0f && nextZoom <= 18.0f) {
                        float midX = (event.getX(0) + event.getX(1)) / 2f;
                        float midY = (event.getY(0) + event.getY(1)) / 2f;
                        float dx = midX - getWidth() / 2f;
                        float dy = midY - getHeight() / 2f;

                        double worldSizeOld = 256 * Math.pow(2, zoom);
                        double worldSizeNew = 256 * Math.pow(2, nextZoom);
                        double ratio = worldSizeNew / worldSizeOld;

                        double cx = (centerLng + 180.0) / 360.0 * worldSizeOld;
                        double sinLat = Math.sin(centerLat * Math.PI / 180.0);
                        double cy = (0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI)) * worldSizeOld;

                        double tx = cx + dx / scaleFactor;
                        double ty = cy + dy / scaleFactor;
                        double newTx = tx * ratio;
                        double newTy = ty * ratio;

                        double newCx = newTx - dx / scaleFactor;
                        double newCy = newTy - dy / scaleFactor;

                        centerLng = newCx / worldSizeNew * 360.0 - 180.0;
                        double n = Math.PI - (2.0 * Math.PI * newCy / worldSizeNew);
                        centerLat = 180.0 / Math.PI * Math.atan(Math.sinh(n));

                        zoom = nextZoom;
                        invalidate();
                    }
                    lastDist = newDist;
                }
            }
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_UP) {
            int remainIdx = event.getActionIndex() == 0 ? 1 : 0;
            if (remainIdx < event.getPointerCount()) {
                lastX = event.getX(remainIdx);
                lastY = event.getY(remainIdx);
            }
            lastDist = 0;
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            lastDist = 0;
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
                else if (reqType == MAP_TOPO) urlStr = "https://a.tile.opentopomap.org/" + z + "/" + x + "/" + y + ".png";
                else urlStr = "https://tile.openstreetmap.org/" + z + "/" + x + "/" + y + ".png";

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