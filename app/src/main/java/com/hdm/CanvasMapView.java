package com.hdm;

import com.hdm.cad.entity.ColorResolver;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CanvasMapView extends View {
// 控制 Canvas CAD 文字的显隐 (默认关闭)
    public boolean showCadText = false;

    public void setCadTextVisibility(boolean show) {
        this.showCadText = show;
        invalidate(); // 触发重绘
    }

    public interface OnViewportChangedListener {
        void onViewportChanged(double lat, double lng, float zoom);
    }

    private OnViewportChangedListener viewportListener;

    public void setOnViewportChangedListener(OnViewportChangedListener listener) {
        this.viewportListener = listener;
    }

    private Paint linePaint,
            debugPaint,
            tilePaint,
            textPaint,
            textBgPaint,
            gpsPaint,
            gpsStrokePaint,
            ctrlPaint,
            cadTextPaint;
    private double centerLat = -4.8, centerLng = 32.5;
    private float zoom = 15.0f;
    private float lastX, lastY, lastDist = 0;
    private long lastTapTime = 0;
    private float lastTapX = 0, lastTapY = 0;
    private long twoFingerDownTime = 0;
    private float twoFingerStartX = 0, twoFingerStartY = 0;

    private List<MapSource> mapSources = new ArrayList<>();
    private int currentMapIndex = 0;
    private HashMap<String, TileDatabase> dbCache = new HashMap<>();
    private List<Object[]> controlPoints = null;

    public static class CadTextModel {
        public double mx, my;
        public String text;
        public double height, rotation;
        public int color;
    }

    private List<CadTextModel> cadTexts = new ArrayList<>();

    private final double[] tmpMercator = new double[2];

    private void latLngToMercatorFast(double lat, double lng) {
        tmpMercator[0] = (lng + 180.0) / 360.0;
        double sinLat = Math.sin(lat * Math.PI / 180.0);
        tmpMercator[1] = 0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI);
    }

    private List<List<Object[]>> multiRoadData;
    private List<String> roadNames = null;
    private int currentRoadIndex = 0;
    private boolean showRoadNames = false;

    private double gpsLat = 0, gpsLng = 0;
    private String gpsKStr = "--", gpsBStr = "--";
    private boolean hasGps = false;
    private float gpsBearing = 0f;

    private ExecutorService executor = Executors.newFixedThreadPool(4);
    private LruCache<String, Bitmap> memoryCache;
    private java.util.HashSet<String> downloadingKeys = new java.util.HashSet<>();

    private float scaleFactor = 2.0f;
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();
    private ValueAnimator moveAnimator, zoomAnimator;

    private final int[] palette = {0xFF1E88E5, 0xFF43A047, 0xFFFB8C00, 0xFF8E24AA, 0xFF00ACC1, 0xFFD81B60, 0xFF6D4C41, 0xFF7CB342, 0xFF3949AB, 0xFF546E7A};
    private final int CURRENT_ROAD_COLOR = 0xFF0078D4;

    public CanvasMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
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
        gpsStrokePaint.setStrokeWidth(2f);
        gpsStrokePaint.setAntiAlias(true);
        ctrlPaint = new Paint();
        ctrlPaint.setColor(0xFF9C27B0);
        ctrlPaint.setStyle(Paint.Style.FILL);
        ctrlPaint.setAntiAlias(true);
        cadTextPaint = new Paint();
        cadTextPaint.setAntiAlias(true);
        cadTextPaint.setTextAlign(Paint.Align.LEFT);
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        memoryCache = new LruCache<String, Bitmap>(maxMemory / 8) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public void setCadTextEntities(List<com.hdm.cad.entity.EntityBase> entities, MyApp app) {
        cadTexts.clear();
        if (entities == null || app == null || app.data == null) {
            invalidate();
            return;
        }
        for (com.hdm.cad.entity.EntityBase ent : entities) {
            if (ent instanceof com.hdm.cad.entity.BasicEntities.EntText) {
                com.hdm.cad.entity.BasicEntities.EntText txt = (com.hdm.cad.entity.BasicEntities.EntText) ent;
                double[] lb = Projection.xy2Gps(txt.insertion.y, txt.insertion.x, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                if (lb != null && lb[0] != 0 && txt.text != null && !txt.text.isEmpty()) {
                    latLngToMercatorFast(lb[1], lb[0]);
                    CadTextModel ct = new CadTextModel();
                    ct.mx = tmpMercator[0];
                    ct.my = tmpMercator[1];
                    ct.text = txt.text;
                    ct.height = txt.height;
                    ct.rotation = txt.rotation;
                    int c = 0xFF000000;
                    if (txt.color != null && txt.color.hasIndex) {
                        int[] res = ColorResolver.aciColor(txt.color.index == 256 ? 7 : txt.color.index, true);
                        if (res[3] == 1) c = Color.rgb(res[0], res[1], res[2]);
                    }
                    ct.color = c;
                    cadTexts.add(ct);
                }
            }
        }
        invalidate();
    }

    public double getCenterLatitude() {
        return centerLat;
    }

    public double getCenterLongitude() {
        return centerLng;
    }

    public void setMapSources(List<MapSource> sources) {
        this.mapSources = sources;
        if (sources != null && !sources.isEmpty()) setMapSourceIndex(0);
    }

    public void setMapSourceIndex(int index) {
        if (mapSources == null || index < 0 || index >= mapSources.size()) return;
        this.currentMapIndex = index;
        invalidate();
    }

    public int getMapSourceIndex() {
        return currentMapIndex;
    }

    public List<MapSource> getMapSources() {
        return mapSources;
    }

    public void setShowRoadNames(boolean show) {
        this.showRoadNames = show;
        invalidate();
    }

    public void setControlPoints(List<Object[]> pts) {
        this.controlPoints = pts;
        invalidate();
    }

    public void setGpsBearing(float bearing) {
        this.gpsBearing = bearing;
        if (hasGps) invalidate();
    }

    public void setMultiRouteData(List<List<Object[]>> multiData, List<String> names) {
        this.multiRoadData = multiData;
        this.roadNames = names;
        if (multiData != null && !multiData.isEmpty()) switchRoad(0);
    }

    public void switchRoad(int index) {
        this.currentRoadIndex = index;
        if (multiRoadData != null && index < multiRoadData.size()) {
            List<Object[]> route = multiRoadData.get(index);
            if (route != null && !route.isEmpty()) {
                double minLat = Double.MAX_VALUE,
                        maxLat = -Double.MAX_VALUE,
                        minLng = Double.MAX_VALUE,
                        maxLng = -Double.MAX_VALUE;
                for (Object[] pt : route) {
                    double lat = (Double) pt[0], lng = (Double) pt[1];
                    if (lat < minLat) minLat = lat;
                    if (lat > maxLat) maxLat = lat;
                    if (lng < minLng) minLng = lng;
                    if (lng > maxLng) maxLng = lng;
                }
                animateToBounds(minLat, maxLat, minLng, maxLng);
            }
        } else invalidate();
    }

    public void zoomToFitAll() {
        if (multiRoadData == null || multiRoadData.isEmpty()) return;
        double minLat = Double.MAX_VALUE,
                maxLat = -Double.MAX_VALUE,
                minLng = Double.MAX_VALUE,
                maxLng = -Double.MAX_VALUE;
        boolean hasData = false;
        for (List<Object[]> route : multiRoadData) {
            for (Object[] pt : route) {
                double lat = (Double) pt[0], lng = (Double) pt[1];
                if (lat < minLat) minLat = lat;
                if (lat > maxLat) maxLat = lat;
                if (lng < minLng) minLng = lng;
                if (lng > maxLng) maxLng = lng;
                hasData = true;
            }
        }
        if (hasData) animateToBounds(minLat, maxLat, minLng, maxLng);
    }

    private void animateToBounds(double minLat, double maxLat, double minLng, double maxLng) {
        double centerLatTarget = (minLat + maxLat) / 2.0, centerLngTarget = (minLng + maxLng) / 2.0;
        float calculatedZoom = 15.0f;
        if (maxLat > minLat && maxLng > minLng) {
            int tileZoom = 15;
            double[] p1 = latLngToWorldPixel(maxLat, minLng, tileZoom),
                    p2 = latLngToWorldPixel(minLat, maxLng, tileZoom);
            double w = Math.abs(p2[0] - p1[0]), h = Math.abs(p1[1] - p2[1]);
            float screenWidth = getWidth(), screenHeight = getHeight();
            if (screenWidth == 0 || screenHeight == 0) {
                screenWidth = getResources().getDisplayMetrics().widthPixels;
                screenHeight = getResources().getDisplayMetrics().heightPixels;
            }
            double pixelW = Math.max(screenWidth - 120, 100),
                    pixelH = Math.max(screenHeight - 120, 100);
            float scaleX = (float) (pixelW / (w * scaleFactor)),
                    scaleY = (float) (pixelH / (h * scaleFactor));
            calculatedZoom = (float) (tileZoom + Math.log(Math.min(scaleX, scaleY)) / Math.log(2));
            if (calculatedZoom < 3.0f) calculatedZoom = 3.0f;
            if (calculatedZoom > 22.0f) calculatedZoom = 22.0f;
            if (Float.isNaN(calculatedZoom)) calculatedZoom = 15.0f;
        }
        final float targetZoom = calculatedZoom;
        final double startLat = centerLat, startLng = centerLng;
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
            if (viewportListener != null)
                viewportListener.onViewportChanged(centerLat, centerLng, zoom);
            invalidate();
        });
        moveAnimator.start();
    }

    private void animateZoom(final float targetZoom, final float pivotX, final float pivotY) {
        if (zoomAnimator != null && zoomAnimator.isRunning()) return;
        final float startZoom = zoom;
        final double startCenterLat = centerLat, startCenterLng = centerLng;
        double startWorldSize = 256 * Math.pow(2, startZoom),
                startCx = (startCenterLng + 180.0) / 360.0 * startWorldSize;
        double startSinLat = Math.sin(startCenterLat * Math.PI / 180.0),
                startCy =
                        (0.5 - Math.log((1 + startSinLat) / (1 - startSinLat)) / (4 * Math.PI)) * startWorldSize;
        float dx = pivotX - getWidth() / 2f, dy = pivotY - getHeight() / 2f;
        double focusWorldX = startCx + dx / scaleFactor, focusWorldY = startCy + dy / scaleFactor;
        double baseWorldX = focusWorldX / Math.pow(2, startZoom),
                baseWorldY = focusWorldY / Math.pow(2, startZoom);
        zoomAnimator = ValueAnimator.ofFloat(startZoom, targetZoom);
        zoomAnimator.setDuration(250);
        zoomAnimator.addUpdateListener(animation -> {
            float currentZoom = (float) animation.getAnimatedValue();
            zoom = currentZoom;
            double currentWorldSize = 256 * Math.pow(2, currentZoom),
                    currentFocusWorldX = baseWorldX * Math.pow(2, currentZoom),
                    currentFocusWorldY = baseWorldY * Math.pow(2, currentZoom);
            double newCenterWorldX = currentFocusWorldX - dx / scaleFactor,
                    newCenterWorldY = currentFocusWorldY - dy / scaleFactor;
            centerLng = newCenterWorldX / currentWorldSize * 360.0 - 180.0;
            double n = Math.PI - (2.0 * Math.PI * newCenterWorldY / currentWorldSize);
            centerLat = 180.0 / Math.PI * Math.atan(Math.sinh(n));
            if (viewportListener != null)
                viewportListener.onViewportChanged(centerLat, centerLng, zoom);
            invalidate();
        });
        zoomAnimator.start();
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
        final double startLat = centerLat, startLng = centerLng;
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
            if (viewportListener != null)
                viewportListener.onViewportChanged(centerLat, centerLng, zoom);
            invalidate();
        });
        moveAnimator.start();
    }

    private TileDatabase getCurrentDb() {
        if (mapSources == null || currentMapIndex >= mapSources.size()) return null;
        MapSource src = mapSources.get(currentMapIndex);
        if (!dbCache.containsKey(src.dbName))
            dbCache.put(src.dbName, new TileDatabase(getContext().getApplicationContext(), src.dbName));
        return dbCache.get(src.dbName);
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
        int width = getWidth(), height = getHeight();
        String url = mapSources.get(currentMapIndex).urlTemplate;

        // 判断是否为纯色底图
        boolean isBlank = false;
        if ("blank://dark".equals(url)) {
            canvas.drawColor(0xFF2B2D30);
            isBlank = true;
        } else if ("blank://white".equals(url)) {
            canvas.drawColor(0xFFF8F9FA);
            isBlank = true;
        } else {
            canvas.drawColor(Color.LTGRAY);
        }

        int tileZoom = (int) Math.floor(zoom);
        float subZoomScale = (float) Math.pow(2, zoom - tileZoom);
        float totalScale = scaleFactor * subZoomScale;

        // 计算视口的世界坐标起点
        double[] centerWorld = latLngToWorldPixel(centerLat, centerLng, tileZoom);
        double startX = centerWorld[0] - (width / 2.0) / (scaleFactor * subZoomScale),
                startY = centerWorld[1] - (height / 2.0) / (scaleFactor * subZoomScale);

        List<Object[]> roadLabels = new ArrayList<>();

        canvas.save();
        canvas.scale(scaleFactor * subZoomScale, scaleFactor * subZoomScale);

        // ================= 核心修改 =================
        // 只有在不是纯色底图的情况下，才去计算并绘制瓦片
        if (!isBlank) {
            int minTileX = (int) Math.floor(startX / 256),
                    maxTileX =
                            (int) Math.floor((startX + width / (scaleFactor * subZoomScale)) / 256);
            int minTileY = (int) Math.floor(startY / 256),
                    maxTileY =
                            (int) Math.floor((startY + height / (scaleFactor * subZoomScale)) / 256);

            int maxZoomLevel = 19;
            if (tileZoom > maxZoomLevel) {
                int diff = tileZoom - maxZoomLevel;
                float scale = (float) Math.pow(2, diff);
                for (int tx = minTileX >> diff; tx <= maxTileX >> diff; tx++) {
                    for (int ty = minTileY >> diff; ty <= maxTileY >> diff; ty++) {
                        float screenXActual = (float) (((tx << diff) * 256 - startX)),
                                screenYActual = (float) (((ty << diff) * 256 - startY)),
                                size = 256 * scale;
                        Bitmap targetTile = getTile(tileZoom, tx << diff, ty << diff);
                        if (targetTile != null) {
                            dstRect.set(screenXActual, screenYActual, screenXActual + size, screenYActual + size);
                            canvas.drawBitmap(targetTile, null, dstRect, tilePaint);
                        } else {
                            canvas.drawRect(screenXActual, screenYActual, screenXActual + size, screenYActual + size, debugPaint);
                        }
                    }
                }
            } else {
                for (int tx = minTileX; tx <= maxTileX; tx++) {
                    for (int ty = minTileY; ty <= maxTileY; ty++) {
                        float screenX = (float) (tx * 256 - startX),
                                screenY = (float) (ty * 256 - startY);
                        boolean drawn = drawTileWithFallback(canvas, tileZoom, tx, ty, screenX, screenY);
                        if (!drawn)
                            canvas.drawRect(screenX, screenY, screenX + 256, screenY + 256, debugPaint);
                    }
                }
            }
        }
        // ============================================

        // 路线
        if (multiRoadData != null && !multiRoadData.isEmpty()) {
            for (int r = 0; r < multiRoadData.size(); r++) {
                if (r == currentRoadIndex) continue;
                List<Object[]> route = multiRoadData.get(r);
                if (route == null || route.isEmpty()) continue;
                Path path = new Path();
                List<float[]> screenPts = new ArrayList<>();
                for (int i = 0; i < route.size(); i++) {
                    Object[] pt = route.get(i);
                    double[] worldPx = latLngToWorldPixel((Double) pt[0], (Double) pt[1], tileZoom);
                    float x = (float) (worldPx[0] - startX), y = (float) (worldPx[1] - startY);
                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                    float scrX = x * totalScale, scrY = y * totalScale;
                    if (showRoadNames && scrX >= 40 && scrX <= width - 40 && scrY >= 40 && scrY <= height - 40)
                        screenPts.add(new float[]{scrX, scrY});
                }
                linePaint.setColor(palette[r % palette.length]);
                linePaint.setPathEffect(new DashPathEffect(new float[]{12f, 4f}, 0));
                linePaint.setStrokeWidth(1f);
                canvas.drawPath(path, linePaint);
                if (showRoadNames && !screenPts.isEmpty()) {
                    int mi = screenPts.size() / 2;
                    float[] mid = screenPts.get(mi);
                    float angle = calcRoadAngle(screenPts, mi);
                    roadLabels.add(new Object[]{Integer.valueOf(r), Float.valueOf(mid[
                            0]), Float.valueOf(mid[1]), Float.valueOf(angle)});
                }
            }
            List<Object[]> currentRoute = multiRoadData.get(currentRoadIndex);
            if (currentRoute != null && !currentRoute.isEmpty()) {
                Path path = new Path();
                float lastKmPixelX = -99999, lastKmPixelY = -99999;
                List<float[]> screenPts = new ArrayList<>();
                for (int i = 0; i < currentRoute.size(); i++) {
                    Object[] pt = currentRoute.get(i);
                    double lat = (Double) pt[0], lng = (Double) pt[1];
                    String kStr = (String) pt[2];
                    double fwj = (Double) pt[3];
                    double[] worldPx = latLngToWorldPixel(lat, lng, tileZoom);
                    float x = (float) (worldPx[0] - startX), y = (float) (worldPx[1] - startY);
                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                    float scrX = x * totalScale, scrY = y * totalScale;
                    if (showRoadNames && scrX >= 40 && scrX <= width - 40 && scrY >= 40 && scrY <= height - 40)
                        screenPts.add(new float[]{scrX, scrY});
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
                linePaint.setColor(CURRENT_ROAD_COLOR);
                linePaint.setPathEffect(null);
                linePaint.setStrokeWidth(2f);
                canvas.drawPath(path, linePaint);
                if (showRoadNames && !screenPts.isEmpty()) {
                    int mi = screenPts.size() / 2;
                    float[] mid = screenPts.get(mi);
                    float angle = calcRoadAngle(screenPts, mi);
                    roadLabels.add(new Object[]{Integer.valueOf(currentRoadIndex), Float.valueOf(mid[
                            0]), Float.valueOf(mid[1]), Float.valueOf(angle)});
                }
            }
        }

        // 控制点
        if (controlPoints != null && !controlPoints.isEmpty()) {
            Paint ctrlTextBg = new Paint();
            ctrlTextBg.setColor(0xE0FFFFFF);
            ctrlTextBg.setStyle(Paint.Style.FILL);
            Paint ctrlText = new Paint();
            ctrlText.setAntiAlias(true);
            ctrlText.setColor(0xFF333333);
            ctrlText.setTextAlign(Paint.Align.LEFT);
            ctrlText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            List<float[]> drawnPixelPts = new ArrayList<>();
            boolean showText = zoom >= 13.0f;
            for (Object[] pt : controlPoints) {
                double lat = (Double) pt[0], lng = (Double) pt[1];
                String name = (String) pt[2];
                double[] worldPx = latLngToWorldPixel(lat, lng, tileZoom);
                float x = (float) (worldPx[0] - startX), y = (float) (worldPx[1] - startY);
                float scrX = x * totalScale, scrY = y * totalScale;
                if (scrX < -50 || scrX > width + 50 || scrY < -50 || scrY > height + 50) continue;
                boolean tooClose = false;
                for (float[] dp : drawnPixelPts) {
                    if (Math.sqrt(Math.pow(scrX - dp[0], 2) + Math.pow(scrY - dp[1], 2)) < 60) {
                        tooClose = true;
                        break;
                    }
                }
                if (tooClose) continue;
                drawnPixelPts.add(new float[]{scrX, scrY});
                canvas.drawCircle(x, y, 6f, ctrlPaint);
                canvas.drawCircle(x, y, 6f, gpsStrokePaint);
                if (showText) {
                    ctrlText.setTextSize(14f);
                    float textW = ctrlText.measureText(name);
                    canvas.drawRect(x + 8, y - 16, x + 14 + textW, y + 2, ctrlTextBg);
                    canvas.drawText(name, x + 11, y - 3, ctrlText);
                }
            }
        }

        // CAD 文字
// CAD 文字 (实心字，受开关控制)
        if (showCadText && !cadTexts.isEmpty()) {
            double worldSize = 256 * Math.pow(2, tileZoom);
            double metersPerScreenPixel = 156543.03392 * Math.cos(centerLat * Math.PI / 180.0) / (Math.pow(2, tileZoom) * totalScale);
            for (CadTextModel txt : cadTexts) {
                double screenMx = txt.mx * worldSize - startX,
                        screenMy = txt.my * worldSize - startY;
                if (screenMx < 0 || screenMx > width / totalScale || screenMy < 0 || screenMy > height / totalScale)
                    continue;
                float textSizePx = (float) (txt.height / metersPerScreenPixel);
                if (textSizePx < 8f) continue;
                cadTextPaint.setColor(txt.color);
                cadTextPaint.setTextSize(textSizePx / totalScale);
                canvas.save();
                canvas.translate((float) (txt.mx * worldSize - startX), (float) (txt.my * worldSize - startY));
                canvas.rotate((float) -Math.toDegrees(txt.rotation));
                canvas.drawText(txt.text, 0, 0, cadTextPaint);
                canvas.restore();
            }
        }

        // GPS
        if (hasGps) {
            double[] gpsWorld = latLngToWorldPixel(gpsLat, gpsLng, tileZoom);
            float gx = (float) (gpsWorld[0] - startX), gy = (float) (gpsWorld[1] - startY);
            canvas.save();
            canvas.translate(gx, gy);
            canvas.rotate(gpsBearing);
            canvas.drawCircle(0, 0, 8, gpsStrokePaint);
            canvas.drawCircle(0, 0, 6, gpsPaint);
            Paint triPaint = new Paint();
            triPaint.setColor(0xCCFFFFFF);
            triPaint.setStyle(Paint.Style.FILL);
            triPaint.setAntiAlias(true);
            Paint triBorder = new Paint();
            triBorder.setColor(0x33000000);
            triBorder.setStyle(Paint.Style.STROKE);
            triBorder.setStrokeWidth(1f);
            triBorder.setAntiAlias(true);
            Path triPath = new Path();
            triPath.moveTo(0, -10);
            triPath.lineTo(-3.5f, -1);
            triPath.lineTo(3.5f, -1);
            triPath.close();
            canvas.drawPath(triPath, triPaint);
            canvas.drawPath(triPath, triBorder);
            Paint redDotPaint = new Paint();
            redDotPaint.setColor(0xAAFF5722);
            redDotPaint.setStyle(Paint.Style.FILL);
            redDotPaint.setAntiAlias(true);
            canvas.drawCircle(0, -10, 2f, redDotPaint);
            canvas.restore();
        }

        canvas.restore();

        if (showRoadNames) drawRoadNameLabels(canvas, width, height, roadLabels);

        // 底部桩号
        if (hasGps) {
            Paint uiTextPaint = new Paint(textPaint);
            uiTextPaint.setTextSize(40f);
            uiTextPaint.setTextAlign(Paint.Align.LEFT);
            uiTextPaint.setColor(0xFF333333);
            Paint uiBgPaint = new Paint();
            uiBgPaint.setColor(0xF5FFFFFF);
            uiBgPaint.setStyle(Paint.Style.FILL);
            uiBgPaint.setAntiAlias(true);
            Paint uiShadowPaint = new Paint();
            uiShadowPaint.setColor(0x33000000);
            uiShadowPaint.setStyle(Paint.Style.FILL);
            uiShadowPaint.setAntiAlias(true);
            String info = "桩号: " + gpsKStr + "\n偏距: " + gpsBStr;
            String[] lines = info.split("\n");
            float textW = 0;
            for (String line : lines) textW = Math.max(textW, uiTextPaint.measureText(line));
            float textH = uiTextPaint.getTextSize() * lines.length * 1.3f;
            float padLeft = 50f, padTop = 30f, padBottom = 30f, boxMarginBottom = 35f;
            float boxWidth = textW + padLeft * 2, boxHeight = textH + padTop + padBottom;
            float boxLeft = (width - boxWidth) / 2f,
                    boxBottom = height - boxMarginBottom,
                    boxTop = boxBottom - boxHeight,
                    boxRight = boxLeft + boxWidth;
            RectF bgRect = new RectF(boxLeft, boxTop, boxRight, boxBottom);
            canvas.drawRoundRect(boxLeft, boxTop + 6f, boxRight, boxBottom + 6f, 24f, 24f, uiShadowPaint);
            canvas.drawRoundRect(bgRect, 24f, 24f, uiBgPaint);
            for (int i = 0; i < lines.length; i++) {
                float baselineY = boxTop + padTop + uiTextPaint.getTextSize() * (i + 0.85f);
                canvas.drawText(lines[i], boxLeft + padLeft, baselineY, uiTextPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        long currentTime = System.currentTimeMillis();
        if (action == MotionEvent.ACTION_DOWN) {
            lastX = event.getX();
            lastY = event.getY();
            if (moveAnimator != null) moveAnimator.cancel();
            if (zoomAnimator != null) zoomAnimator.cancel();
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (event.getPointerCount() >= 2) {
                lastDist = distance(event);
                twoFingerDownTime = currentTime;
                twoFingerStartX = (event.getX(0) + event.getX(1)) / 2f;
                twoFingerStartY = (event.getY(0) + event.getY(1)) / 2f;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (event.getPointerCount() == 1) {
                float dx = event.getX() - lastX, dy = event.getY() - lastY;
                if (Math.abs(dx) > 20 || Math.abs(dy) > 20) lastTapTime = 0;
                double worldSize = 256 * Math.pow(2, zoom);
                centerLng -= (dx / scaleFactor) / worldSize * 360.0;
                double latRad = centerLat * Math.PI / 180.0;
                centerLat += (dy / scaleFactor) / worldSize * 360.0 * Math.cos(latRad);
                lastX = event.getX();
                lastY = event.getY();
                if (viewportListener != null)
                    viewportListener.onViewportChanged(centerLat, centerLng, zoom);
                invalidate();
            } else if (event.getPointerCount() >= 2) {
                twoFingerDownTime = 0;
                float newDist = distance(event);
                if (newDist > 10 && lastDist > 10) {
                    float scale = newDist / lastDist;
                    float nextZoom = zoom + (float) (Math.log(scale) / Math.log(2));
                    if (nextZoom >= 3.0f && nextZoom <= 22.0f) {
                        float midX = (event.getX(0) + event.getX(1)) / 2f,
                                midY = (event.getY(0) + event.getY(1)) / 2f;
                        float dx = midX - getWidth() / 2f, dy = midY - getHeight() / 2f;
                        double worldSizeOld = 256 * Math.pow(2, zoom),
                                worldSizeNew = 256 * Math.pow(2, nextZoom);
                        double ratio = worldSizeNew / worldSizeOld;
                        double cx = (centerLng + 180.0) / 360.0 * worldSizeOld,
                                sinLat = Math.sin(centerLat * Math.PI / 180.0);
                        double cy = (0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI)) * worldSizeOld;
                        double tx = cx + dx / scaleFactor, ty = cy + dy / scaleFactor;
                        double newTx = tx * ratio, newTy = ty * ratio;
                        double newCx = newTx - dx / scaleFactor, newCy = newTy - dy / scaleFactor;
                        centerLng = newCx / worldSizeNew * 360.0 - 180.0;
                        double n = Math.PI - (2.0 * Math.PI * newCy / worldSizeNew);
                        centerLat = 180.0 / Math.PI * Math.atan(Math.sinh(n));
                        zoom = nextZoom;
                        if (viewportListener != null)
                            viewportListener.onViewportChanged(centerLat, centerLng, zoom);
                        invalidate();
                    }
                    lastDist = newDist;
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP) {
            if (twoFingerDownTime > 0 && currentTime - twoFingerDownTime < 300) {
                float midX = (event.getX(0) + event.getX(1)) / 2f,
                        midY = (event.getY(0) + event.getY(1)) / 2f;
                float moveDist = (float) Math.sqrt(Math.pow(midX - twoFingerStartX, 2) + Math.pow(midY - twoFingerStartY, 2));
                if (moveDist < 50)
                    animateZoom(Math.max(3.0f, (float) Math.floor(zoom) - 1), midX, midY);
            }
            twoFingerDownTime = 0;
            int remainIdx = event.getActionIndex() == 0 ? 1 : 0;
            if (remainIdx < event.getPointerCount()) {
                lastX = event.getX(remainIdx);
                lastY = event.getY(remainIdx);
            }
            lastDist = 0;
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            invalidate();
            if (currentTime - lastTapTime < 300) {
                float dx = event.getX() - lastTapX, dy = event.getY() - lastTapY;
                if (Math.sqrt(dx * dx + dy * dy) < 50)
                    animateZoom(Math.min(22.0f, (float) Math.ceil(zoom) + 1), lastTapX, lastTapY);
                lastTapTime = 0;
            } else {
                lastTapTime = currentTime;
                lastTapX = event.getX();
                lastTapY = event.getY();
            }
            lastDist = 0;
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            invalidate();
            lastDist = 0;
            twoFingerDownTime = 0;
            lastTapTime = 0;
            return true;
        }
        return super.onTouchEvent(event);
    }

    private float distance(MotionEvent event) {
        float dx = event.getX(0) - event.getX(1), dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float calcRoadAngle(List<float[]> pts, int midIdx) {
        if (pts == null || pts.size() < 2) return 0f;
        int i0 = Math.max(0, midIdx - 5), i1 = Math.min(pts.size() - 1, midIdx + 5);
        if (i0 == i1) return 0f;
        float[] p0 = pts.get(i0), p1 = pts.get(i1);
        float dx = p1[0] - p0[0], dy = p1[1] - p0[1];
        if (Math.abs(dx) < 1e-3f && Math.abs(dy) < 1e-3f) return 0f;
        float angleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
        while (angleDeg > 90f) angleDeg -= 180f;
        while (angleDeg <= -90f) angleDeg += 180f;
        return angleDeg;
    }

    private void drawRoadNameLabels(Canvas canvas, int width, int height, List<
                    Object[]> roadLabels) {
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
        float padH = 12f, padV = 8f;
        for (int li = 0; li < roadLabels.size(); li++) {
            Object[] label = roadLabels.get(li);
            int r = (Integer) label[0];
            float cx = (Float) label[1], cy = (Float) label[2], angle = (Float) label[3];
            if (r < 0 || r >= roadNames.size()) continue;
            String name = roadNames.get(r);
            if (name == null || name.isEmpty()) continue;
            boolean isCurrent = (r == currentRoadIndex);
            namePaint.setColor(isCurrent ? CURRENT_ROAD_COLOR : palette[r % palette.length]);
            nameBorder.setColor(isCurrent ? CURRENT_ROAD_COLOR : palette[r % palette.length]);
            float textW = namePaint.measureText(name);
            float textSize = namePaint.getTextSize();
            float bw = textW + padH * 2, bh = textSize + padV * 2;
            if (cx < bw / 2f + 4) cx = bw / 2f + 4;
            if (cx > width - bw / 2f - 4) cx = width - bw / 2f - 4;
            if (cy < bh / 2f + 4) cy = bh / 2f + 4;
            if (cy > height - bh / 2f - 4) cy = height - bh / 2f - 4;
            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(angle);
            float offsetY = -bh / 2f - 6f, bx = -bw / 2f, by = offsetY - bh / 2f;
            canvas.drawRect(bx, by, bx + bw, by + bh, nameBg);
            canvas.drawRect(bx, by, bx + bw, by + bh, nameBorder);
            Paint.FontMetrics fm = namePaint.getFontMetrics();
            float baseline = offsetY - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(name, 0, baseline, namePaint);
            canvas.restore();
        }
    }

    private boolean drawTileWithFallback(Canvas canvas, int z, int x, int y, float screenX, float screenY) {
        String urlTemplate = mapSources.get(currentMapIndex).urlTemplate;
        if (urlTemplate != null && urlTemplate.startsWith("blank://")) return true;
        Bitmap targetTile = getTile(z, x, y);
        if (targetTile != null) {
            canvas.drawBitmap(targetTile, screenX, screenY, tilePaint);
            return true;
        }
        int pZ = z - 1;
        if (pZ >= 0) {
            int pX = x >> 1, pY = y >> 1;
            Bitmap parentTile = getTileDirect(pZ, pX, pY);
            if (parentTile != null) {
                int xOffset = (x % 2 == 0) ? 0 : 128, yOffset = (y % 2 == 0) ? 0 : 128;
                srcRect.set(xOffset, yOffset, xOffset + 128, yOffset + 128);
                dstRect.set(screenX, screenY, screenX + 256, screenY + 256);
                canvas.drawBitmap(parentTile, srcRect, dstRect, tilePaint);
                return true;
            }
        }
        return false;
    }

    private Bitmap getTileDirect(int z, int x, int y) {
        return memoryCache.get(currentMapIndex + "_" + z + "/" + x + "/" + y);
    }

    private Bitmap getTile(int inputZ, int inputX, int inputY) {
        String urlTemplate = mapSources.get(currentMapIndex).urlTemplate;
        if (urlTemplate.startsWith("blank://")) return null;
        int maxZoomLevel = 19,
                z = Math.min(inputZ, maxZoomLevel),
                diff = inputZ - z,
                x = inputX >> diff,
                y = inputY >> diff;
        int maxTile = (int) Math.pow(2, z) - 1;
        if (x < 0 || y < 0 || x > maxTile || y > maxTile) return null;
        String key = currentMapIndex + "_" + z + "/" + x + "/" + y;
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
        final String urlStr = urlTemplate.replace("{x}", String.valueOf(x)).replace("{y}", String.valueOf(y)).replace("{z}", String.valueOf(z));
        executor.execute(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setRequestProperty("User-Agent", "hdm-android/3.3.0");
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
                        postInvalidate();
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