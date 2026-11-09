package com.hdm;

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

    private Paint linePaint;
    private Paint debugPaint;
    private Paint tilePaint;
    private Paint textPaint;
    private Paint textBgPaint;
    private Paint gpsPaint;
    private Paint gpsStrokePaint;
    private Paint ctrlPaint;
    private Paint cadPaint;
    private Paint cadTextPaint;

    private double centerLat = -4.8;
    private double centerLng = 32.5;
    private float zoom = 15.0f;

    private float lastX, lastY;
    private float lastDist = 0;

    private long lastTapTime = 0;
    private float lastTapX = 0, lastTapY = 0;
    private long twoFingerDownTime = 0;
    private float twoFingerStartX = 0, twoFingerStartY = 0;

    private List<MapSource> mapSources = new ArrayList<>();
    private int currentMapIndex = 0;
    private HashMap<String, TileDatabase> dbCache = new HashMap<>();
    private List<Object[]> controlPoints = null;

    // ================= CAD 数据模型 =================
    private static class CadLine { double mx1, my1, mx2, my2; }
    private static class CadPolyline { double[] pts; }
    private static class CadCircle { double cx, cy, rM; }
    private static class CadText { double mx, my; String text; double height, rotation; }

    private List<CadLine> cadLines = new ArrayList<>();
    private List<CadPolyline> cadPolylines = new ArrayList<>();
    private List<CadCircle> cadCircles = new ArrayList<>();
    private List<CadText> cadTexts = new ArrayList<>();

    private boolean showCadLine = true;
    private boolean showCadPolyline = true;
    private boolean showCadCircle = true;
    private boolean showCadText = true;

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
    private String gpsKStr = "--";
    private String gpsBStr = "--";
    private boolean hasGps = false;
    private float gpsBearing = 0f;

    private ExecutorService executor = Executors.newFixedThreadPool(4);
    private LruCache<String, Bitmap> memoryCache;
    private java.util.HashSet<String> downloadingKeys = new java.util.HashSet<>();

    private float scaleFactor = 2.0f;
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();

    private ValueAnimator moveAnimator;
    private ValueAnimator zoomAnimator;

    private final int[] palette = {
            0xFF1E88E5, 0xFF43A047, 0xFFFB8C00, 0xFF8E24AA, 0xFF00ACC1,
            0xFFD81B60, 0xFF6D4C41, 0xFF7CB342, 0xFF3949AB, 0xFF546E7A
    };
    private final int CURRENT_ROAD_COLOR = 0xFF0078D4;

    public CanvasMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 【核心开启】：设置硬件加速
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        linePaint = new Paint(); linePaint.setStyle(Paint.Style.STROKE); linePaint.setAntiAlias(true);
        debugPaint = new Paint(); debugPaint.setColor(Color.GRAY);
        tilePaint = new Paint(); tilePaint.setFilterBitmap(true); tilePaint.setAntiAlias(true); tilePaint.setDither(true);
        textPaint = new Paint(); textPaint.setTextSize(14f); textPaint.setAntiAlias(true); textPaint.setTextAlign(Paint.Align.LEFT);
        textBgPaint = new Paint(); textBgPaint.setColor(Color.WHITE); textBgPaint.setStyle(Paint.Style.FILL);
        gpsPaint = new Paint(); gpsPaint.setColor(0xFF0078D4); gpsPaint.setStyle(Paint.Style.FILL); gpsPaint.setAntiAlias(true);
        gpsStrokePaint = new Paint(); gpsStrokePaint.setColor(Color.WHITE); gpsStrokePaint.setStyle(Paint.Style.STROKE); gpsStrokePaint.setStrokeWidth(2f); gpsStrokePaint.setAntiAlias(true);
        ctrlPaint = new Paint(); ctrlPaint.setColor(0xFF9C27B0); ctrlPaint.setStyle(Paint.Style.FILL); ctrlPaint.setAntiAlias(true);
        cadPaint = new Paint(); cadPaint.setStyle(Paint.Style.STROKE); cadPaint.setAntiAlias(true); cadPaint.setStrokeWidth(2f); cadPaint.setColor(0xFFE53935);
        cadTextPaint = new Paint(); cadTextPaint.setAntiAlias(true); cadTextPaint.setColor(0xFFD32F2F); cadTextPaint.setTextAlign(Paint.Align.LEFT);

        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount() / 1024; }
        };
    }

    public double getCenterLatitude() { return centerLat; }
    public double getCenterLongitude() { return centerLng; }
    public void setMapSources(List<MapSource> sources) { this.mapSources = sources; if (sources != null && !sources.isEmpty()) setMapSourceIndex(0); }
    public void setMapSourceIndex(int index) { if (mapSources == null || index < 0 || index >= mapSources.size()) return; this.currentMapIndex = index; invalidate(); }
    public int getMapSourceIndex() { return currentMapIndex; }
    public List<MapSource> getMapSources() { return mapSources; }
    public void setShowRoadNames(boolean show) { this.showRoadNames = show; invalidate(); }
    public void setControlPoints(List<Object[]> pts) { this.controlPoints = pts; invalidate(); }
    public void setGpsBearing(float bearing) { this.gpsBearing = bearing; if (hasGps) invalidate(); }
    public void setCadVisibility(boolean line, boolean polyline, boolean circle, boolean text) {
        this.showCadLine = line; this.showCadPolyline = polyline; this.showCadCircle = circle; this.showCadText = text; invalidate();
    }
    public boolean isShowCadLine() { return showCadLine; }
    public boolean isShowCadPolyline() { return showCadPolyline; }
    public boolean isShowCadCircle() { return showCadCircle; }
    public boolean isShowCadText() { return showCadText; }

    private void addArcPoints(List<com.hdm.cad.entity.EntityBase.Point2> outPts, double x1, double y1, double x2, double y2, double b) {
        if (Math.abs(b) < 1e-6) { outPts.add(new com.hdm.cad.entity.EntityBase.Point2(x2, y2)); return; }
        double dx = x2 - x1, dy = y2 - y1;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d < 1e-6) { outPts.add(new com.hdm.cad.entity.EntityBase.Point2(x2, y2)); return; }
        double coef = (1.0 - b * b) / (4.0 * b);
        double cx = (x1 + x2) / 2.0 - dy * coef;
        double cy = (y1 + y2) / 2.0 + dx * coef;
        double r = Math.sqrt((x1 - cx) * (x1 - cx) + (y1 - cy) * (y1 - cy));
        double startAngle = Math.atan2(y1 - cy, x1 - cx);
        double theta = 4.0 * Math.atan(b);
        int segments = 32;
        double angleStep = theta / segments;
        for (int i = 1; i <= segments; i++) {
            double angle = startAngle + i * angleStep;
            outPts.add(new com.hdm.cad.entity.EntityBase.Point2(cx + r * Math.cos(angle), cy + r * Math.sin(angle)));
        }
    }

    public void setCadEntities(List<com.hdm.cad.entity.EntityBase> entities) {
        cadLines.clear(); cadPolylines.clear(); cadCircles.clear(); cadTexts.clear();
        if (entities == null || entities.isEmpty()) { invalidate(); return; }
        MyApp app = (MyApp) getContext().getApplicationContext();
        if (app == null || app.data == null) return;

        for (com.hdm.cad.entity.EntityBase ent : entities) {
            if (ent instanceof com.hdm.cad.entity.BasicEntities.EntLine) {
                com.hdm.cad.entity.BasicEntities.EntLine line = (com.hdm.cad.entity.BasicEntities.EntLine) ent;
                double[] lb1 = Projection.xy2Gps(line.start.y, line.start.x, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                double[] lb2 = Projection.xy2Gps(line.end.y, line.end.x, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                if (lb1 != null && lb2 != null && lb1[0] != 0 && lb2[0] != 0) {
                    CadLine cl = new CadLine();
                    latLngToMercatorFast(lb1[1], lb1[0]); cl.mx1 = tmpMercator[0]; cl.my1 = tmpMercator[1];
                    latLngToMercatorFast(lb2[1], lb2[0]); cl.mx2 = tmpMercator[0]; cl.my2 = tmpMercator[1];
                    cadLines.add(cl);
                }
            } else if (ent instanceof com.hdm.cad.entity.BasicEntities.EntLwPolyline) {
                com.hdm.cad.entity.BasicEntities.EntLwPolyline poly = (com.hdm.cad.entity.BasicEntities.EntLwPolyline) ent;
                if (poly.vertices != null && poly.vertices.size() > 1) {
                    List<com.hdm.cad.entity.EntityBase.Point2> expandedPts = new ArrayList<>();
                    expandedPts.add(poly.vertices.get(0));
                    for (int i = 0; i < poly.vertices.size() - 1; i++) {
                        com.hdm.cad.entity.EntityBase.Point2 p1 = poly.vertices.get(i);
                        com.hdm.cad.entity.EntityBase.Point2 p2 = poly.vertices.get(i + 1);
                        double b = (poly.bulges != null && i < poly.bulges.length) ? poly.bulges[i] : 0.0;
                        if (Math.abs(b) < 1e-6) expandedPts.add(p2);
                        else addArcPoints(expandedPts, p1.x, p1.y, p2.x, p2.y, b);
                    }
                    if ((poly.flags & 0x200) != 0 && poly.vertices.size() > 2) {
                        com.hdm.cad.entity.EntityBase.Point2 lastPt = poly.vertices.get(poly.vertices.size() - 1);
                        com.hdm.cad.entity.EntityBase.Point2 firstPt = poly.vertices.get(0);
                        double lastBulge = (poly.bulges != null && poly.bulges.length == poly.vertices.size()) ? poly.bulges[poly.vertices.size() - 1] : 0.0;
                        if (Math.abs(lastBulge) < 1e-6) expandedPts.add(firstPt);
                        else addArcPoints(expandedPts, lastPt.x, lastPt.y, firstPt.x, firstPt.y, lastBulge);
                    }
                    List<Double> validCoords = new ArrayList<>();
                    for (com.hdm.cad.entity.EntityBase.Point2 pt : expandedPts) {
                        double[] lb = Projection.xy2Gps(pt.y, pt.x, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                        if (lb != null && lb[0] != 0) {
                            latLngToMercatorFast(lb[1], lb[0]);
                            validCoords.add(tmpMercator[0]); validCoords.add(tmpMercator[1]);
                        }
                    }
                    if (validCoords.size() >= 4) {
                        CadPolyline cp = new CadPolyline();
                        cp.pts = new double[validCoords.size()];
                        for (int k = 0; k < validCoords.size(); k++) cp.pts[k] = validCoords.get(k);
                        cadPolylines.add(cp);
                    }
                }
            } else if (ent instanceof com.hdm.cad.entity.BasicEntities.EntArc) {
                com.hdm.cad.entity.BasicEntities.EntArc arc = (com.hdm.cad.entity.BasicEntities.EntArc) ent;
                double startA = arc.angleStart, endA = arc.angleEnd;
                while (endA < startA) endA += 2 * Math.PI;
                double sweep = endA - startA;
                int segments = 32;
                double step = sweep / segments;
                List<Double> validCoords = new ArrayList<>();
                for (int i = 0; i <= segments; i++) {
                    double angle = startA + step * i;
                    double cadX = arc.center.x + arc.radius * Math.cos(angle);
                    double cadY = arc.center.y + arc.radius * Math.sin(angle);
                    double[] lb = Projection.xy2Gps(cadY, cadX, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                    if (lb != null && lb[0] != 0) {
                        latLngToMercatorFast(lb[1], lb[0]);
                        validCoords.add(tmpMercator[0]); validCoords.add(tmpMercator[1]);
                    }
                }
                if (validCoords.size() >= 4) {
                    CadPolyline cp = new CadPolyline();
                    cp.pts = new double[validCoords.size()];
                    for (int k = 0; k < validCoords.size(); k++) cp.pts[k] = validCoords.get(k);
                    cadPolylines.add(cp);
                }
            } else if (ent instanceof com.hdm.cad.entity.BasicEntities.EntCircle) {
                com.hdm.cad.entity.BasicEntities.EntCircle circle = (com.hdm.cad.entity.BasicEntities.EntCircle) ent;
                double[] lb = Projection.xy2Gps(circle.center.y, circle.center.x, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                if (lb != null && lb[0] != 0) {
                    latLngToMercatorFast(lb[1], lb[0]);
                    CadCircle cc = new CadCircle();
                    cc.cx = tmpMercator[0]; cc.cy = tmpMercator[1];
                    cc.rM = (circle.radius / 40075016.68);
                    cadCircles.add(cc);
                }
            } else if (ent instanceof com.hdm.cad.entity.BasicEntities.EntText) {
                com.hdm.cad.entity.BasicEntities.EntText txt = (com.hdm.cad.entity.BasicEntities.EntText) ent;
                double[] lb = Projection.xy2Gps(txt.insertion.y, txt.insertion.x, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                if (lb != null && lb[0] != 0 && txt.text != null && !txt.text.isEmpty()) {
                    latLngToMercatorFast(lb[1], lb[0]);
                    CadText ct = new CadText();
                    ct.mx = tmpMercator[0]; ct.my = tmpMercator[1];
                    ct.text = txt.text; ct.height = txt.height; ct.rotation = txt.rotation;
                    cadTexts.add(ct);
                }
            }
        }
        invalidate();
        // 自动缩放视野
        if (!cadLines.isEmpty() || !cadPolylines.isEmpty()) {
            double minLat = 90, maxLat = -90, minLng = 180, maxLng = -180;
            for (CadLine line : cadLines) {
                double lat1 = Math.atan(Math.sinh(Math.PI * (1 - 2 * line.my1))) * 180 / Math.PI;
                double lng1 = line.mx1 * 360.0 - 180.0;
                double lat2 = Math.atan(Math.sinh(Math.PI * (1 - 2 * line.my2))) * 180 / Math.PI;
                double lng2 = line.mx2 * 360.0 - 180.0;
                minLat = Math.min(minLat, Math.min(lat1, lat2)); maxLat = Math.max(maxLat, Math.max(lat1, lat2));
                minLng = Math.min(minLng, Math.min(lng1, lng2)); maxLng = Math.max(maxLng, Math.max(lng1, lng2));
            }
            animateToBounds(minLat, maxLat, minLng, maxLng);
        }
    }

    public boolean hasCadData() { return !cadLines.isEmpty() || !cadPolylines.isEmpty() || !cadCircles.isEmpty() || !cadTexts.isEmpty(); }
    public void clearCad() { cadLines.clear(); cadPolylines.clear(); cadCircles.clear(); cadTexts.clear(); invalidate(); }

    public void setMultiRouteData(List<List<Object[]>> multiData, List<String> names) { this.multiRoadData = multiData; this.roadNames = names; if (multiData != null && !multiData.isEmpty()) switchRoad(0); }
    public void switchRoad(int index) { this.currentRoadIndex = index; if (multiRoadData != null && index < multiRoadData.size()) fitToRoute(multiRoadData.get(index)); else invalidate(); }
    public void zoomToFitAll() { if (multiRoadData == null || multiRoadData.isEmpty()) return; double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE; double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE; boolean hasData = false; for (List<Object[]> route : multiRoadData) { for (Object[] pt : route) { double lat = (Double) pt[0]; double lng = (Double) pt[1]; if (lat < minLat) minLat = lat; if (lat > maxLat) maxLat = lat; if (lng < minLng) minLng = lng; if (lng > maxLng) maxLng = lng; hasData = true; } } if (hasData) animateToBounds(minLat, maxLat, minLng, maxLng); }
    private void fitToRoute(List<Object[]> route) { if (route == null || route.isEmpty()) { invalidate(); return; } double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE; double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE; for (Object[] pt : route) { double lat = (Double) pt[0]; double lng = (Double) pt[1]; if (lat < minLat) minLat = lat; if (lat > maxLat) maxLat = lat; if (lng < minLng) minLng = lng; if (lng > maxLng) maxLng = lng; } animateToBounds(minLat, maxLat, minLng, maxLng); }

    private void animateToBounds(double minLat, double maxLat, double minLng, double maxLng) {
        double centerLatTarget = (minLat + maxLat) / 2.0;
        double centerLngTarget = (minLng + maxLng) / 2.0;
        float calculatedZoom = 15.0f;
        if (maxLat > minLat && maxLng > minLng) {
            int tileZoom = 15;
            double[] p1 = latLngToWorldPixel(maxLat, minLng, tileZoom);
            double[] p2 = latLngToWorldPixel(minLat, maxLng, tileZoom);
            double w = Math.abs(p2[0] - p1[0]); double h = Math.abs(p1[1] - p2[1]);
            float screenWidth = getWidth(); float screenHeight = getHeight();
            if (screenWidth == 0 || screenHeight == 0) { screenWidth = getResources().getDisplayMetrics().widthPixels; screenHeight = getResources().getDisplayMetrics().heightPixels; }
            double pixelW = screenWidth - 120; double pixelH = screenHeight - 120;
            if (pixelW < 100) pixelW = 100; if (pixelH < 100) pixelH = 100;
            float scaleX = (float) (pixelW / (w * scaleFactor)); float scaleY = (float) (pixelH / (h * scaleFactor));
            float scale = Math.min(scaleX, scaleY);
            calculatedZoom = (float) (tileZoom + Math.log(scale) / Math.log(2));
            if (calculatedZoom < 3.0f) calculatedZoom = 3.0f; if (calculatedZoom > 22.0f) calculatedZoom = 22.0f;
            if (Float.isNaN(calculatedZoom)) calculatedZoom = 15.0f;
        }
        final float targetZoom = calculatedZoom;
        final double startLat = centerLat; final double startLng = centerLng; final float startZoom = zoom;
        if (moveAnimator != null) moveAnimator.cancel();
        moveAnimator = ValueAnimator.ofFloat(0f, 1f); moveAnimator.setDuration(600);
        moveAnimator.addUpdateListener(animation -> {
            float t = animation.getAnimatedFraction(); float ease = 1 - (1 - t) * (1 - t);
            centerLat = startLat + (centerLatTarget - startLat) * ease; centerLng = startLng + (centerLngTarget - startLng) * ease;
            zoom = startZoom + (targetZoom - startZoom) * ease; invalidate();
        });
        moveAnimator.start();
    }

    private void animateZoom(final float targetZoom, final float pivotX, final float pivotY) {
        if (zoomAnimator != null && zoomAnimator.isRunning()) return;
        final float startZoom = zoom; final double startCenterLat = centerLat; final double startCenterLng = centerLng;
        double startWorldSize = 256 * Math.pow(2, startZoom);
        double startCx = (startCenterLng + 180.0) / 360.0 * startWorldSize;
        double startSinLat = Math.sin(startCenterLat * Math.PI / 180.0);
        double startCy = (0.5 - Math.log((1 + startSinLat) / (1 - startSinLat)) / (4 * Math.PI)) * startWorldSize;
        float dx = pivotX - getWidth() / 2f; float dy = pivotY - getHeight() / 2f;
        double focusWorldX = startCx + dx / scaleFactor; double focusWorldY = startCy + dy / scaleFactor;
        double baseWorldX = focusWorldX / Math.pow(2, startZoom); double baseWorldY = focusWorldY / Math.pow(2, startZoom);
        zoomAnimator = ValueAnimator.ofFloat(startZoom, targetZoom); zoomAnimator.setDuration(250);
        zoomAnimator.addUpdateListener(animation -> {
            float currentZoom = (float) animation.getAnimatedValue(); zoom = currentZoom;
            double currentWorldSize = 256 * Math.pow(2, currentZoom);
            double currentFocusWorldX = baseWorldX * Math.pow(2, currentZoom); double currentFocusWorldY = baseWorldY * Math.pow(2, currentZoom);
            double newCenterWorldX = currentFocusWorldX - dx / scaleFactor; double newCenterWorldY = currentFocusWorldY - dy / scaleFactor;
            centerLng = newCenterWorldX / currentWorldSize * 360.0 - 180.0;
            double n = Math.PI - (2.0 * Math.PI * newCenterWorldY / currentWorldSize);
            centerLat = 180.0 / Math.PI * Math.atan(Math.sinh(n));
            invalidate();
        });
        zoomAnimator.start();
    }

    public void updateGps(double lat, double lng, String kStr, String bStr) { this.gpsLat = lat; this.gpsLng = lng; this.gpsKStr = kStr; this.gpsBStr = bStr; this.hasGps = true; invalidate(); }
    public void locateMe() { if (!hasGps) return; final double startLat = centerLat; final double startLng = centerLng; final float startZoom = zoom; if (moveAnimator != null) moveAnimator.cancel(); moveAnimator = ValueAnimator.ofFloat(0f, 1f); moveAnimator.setDuration(600); moveAnimator.addUpdateListener(animation -> { float t = animation.getAnimatedFraction(); float ease = 1 - (1 - t) * (1 - t); centerLat = startLat + (gpsLat - startLat) * ease; centerLng = startLng + (gpsLng - startLng) * ease; zoom = startZoom + (17.0f - startZoom) * ease; invalidate(); }); moveAnimator.start(); }

    private TileDatabase getCurrentDb() { if (mapSources == null || currentMapIndex >= mapSources.size()) return null; MapSource src = mapSources.get(currentMapIndex); if (!dbCache.containsKey(src.dbName)) { Context ctx = getContext().getApplicationContext(); dbCache.put(src.dbName, new TileDatabase(ctx, src.dbName)); } return dbCache.get(src.dbName); }
    private double[] latLngToWorldPixel(double lat, double lng, float z) { double worldSize = 256 * Math.pow(2, z); double x = (lng + 180.0) / 360.0 * worldSize; double sinLat = Math.sin(lat * Math.PI / 180.0); double y = (0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI)) * worldSize; return new double[]{x, y}; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        String url = mapSources.get(currentMapIndex).urlTemplate;
        if ("blank://dark".equals(url)) canvas.drawColor(0xFF2B2D30);
        else if ("blank://white".equals(url)) canvas.drawColor(0xFFF8F9FA);
        else canvas.drawColor(Color.LTGRAY);

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

        List<Object[]> roadLabels = new ArrayList<>();
        canvas.save();
        canvas.scale(scaleFactor * subZoomScale, scaleFactor * subZoomScale);

        for (int tx = minTileX; tx <= maxTileX; tx++) {
            for (int ty = minTileY; ty <= maxTileY; ty++) {
                float screenX = (float) (tx * 256 - startX);
                float screenY = (float) (ty * 256 - startY);
                boolean drawn = drawTileWithFallback(canvas, tileZoom, tx, ty, screenX, screenY);
                if (!drawn) canvas.drawRect(screenX, screenY, screenX + 256, screenY + 256, debugPaint);
            }
        }

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
                    float x = (float) (worldPx[0] - startX); float y = (float) (worldPx[1] - startY);
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                    float scrX = x * totalScale; float scrY = y * totalScale;
                    if (showRoadNames && scrX >= 40 && scrX <= width - 40 && scrY >= 40 && scrY <= height - 40) screenPts.add(new float[]{scrX, scrY});
                }
                linePaint.setColor(palette[r % palette.length]);
                linePaint.setPathEffect(new DashPathEffect(new float[]{16f, 12f}, 0));
                linePaint.setStrokeWidth(5f);
                canvas.drawPath(path, linePaint);
                if (showRoadNames && !screenPts.isEmpty()) { int mi = screenPts.size() / 2; float[] mid = screenPts.get(mi); float angle = calcRoadAngle(screenPts, mi); roadLabels.add(new Object[]{Integer.valueOf(r), Float.valueOf(mid[0]), Float.valueOf(mid[1]), Float.valueOf(angle)}); }
            }
            List<Object[]> currentRoute = multiRoadData.get(currentRoadIndex);
            if (currentRoute != null && !currentRoute.isEmpty()) {
                Path path = new Path();
                float lastKmPixelX = -99999; float lastKmPixelY = -99999;
                List<float[]> screenPts = new ArrayList<>();
                for (int i = 0; i < currentRoute.size(); i++) {
                    Object[] pt = currentRoute.get(i); double lat = (Double) pt[0]; double lng = (Double) pt[1]; String kStr = (String) pt[2]; double fwj = (Double) pt[3];
                    double[] worldPx = latLngToWorldPixel(lat, lng, tileZoom);
                    float x = (float) (worldPx[0] - startX); float y = (float) (worldPx[1] - startY);
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                    float scrX = x * totalScale; float scrY = y * totalScale;
                    if (showRoadNames && scrX >= 40 && scrX <= width - 40 && scrY >= 40 && scrY <= height - 40) screenPts.add(new float[]{scrX, scrY});
                    if (kStr.endsWith("00")) {
                        float dist = (float) Math.sqrt(Math.pow(x - lastKmPixelX, 2) + Math.pow(y - lastKmPixelY, 2));
                        if (dist > 120) {
                            lastKmPixelX = x; lastKmPixelY = y; float angleDeg = (float) (fwj * 180 / Math.PI);
                            canvas.save(); canvas.translate(x, y); canvas.rotate(angleDeg);
                            float textWidth = textPaint.measureText(kStr);
                            canvas.drawRect(4, -12, 4 + textWidth + 2, 4, textBgPaint); canvas.drawText(kStr, 4, 0, textPaint);
                            canvas.restore();
                        }
                    }
                }
                linePaint.setColor(CURRENT_ROAD_COLOR); linePaint.setPathEffect(null); linePaint.setStrokeWidth(8f); canvas.drawPath(path, linePaint);
                if (showRoadNames && !screenPts.isEmpty()) { int mi = screenPts.size() / 2; float[] mid = screenPts.get(mi); float angle = calcRoadAngle(screenPts, mi); roadLabels.add(new Object[]{Integer.valueOf(currentRoadIndex), Float.valueOf(mid[0]), Float.valueOf(mid[1]), Float.valueOf(angle)}); }
            }
        }

        if (controlPoints != null && !controlPoints.isEmpty()) {
            Paint ctrlTextBg = new Paint(); ctrlTextBg.setColor(0xE0FFFFFF); ctrlTextBg.setStyle(Paint.Style.FILL);
            Paint ctrlText = new Paint(); ctrlText.setAntiAlias(true); ctrlText.setColor(0xFF333333); ctrlText.setTextAlign(Paint.Align.LEFT); ctrlText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            List<float[]> drawnPixelPts = new ArrayList<>(); boolean showText = zoom >= 13.0f;
            for (Object[] pt : controlPoints) {
                double lat = (Double) pt[0]; double lng = (Double) pt[1]; String name = (String) pt[2];
                double[] worldPx = latLngToWorldPixel(lat, lng, tileZoom);
                float x = (float) (worldPx[0] - startX); float y = (float) (worldPx[1] - startY);
                float scrX = x * totalScale; float scrY = y * totalScale;
                if (scrX < -50 || scrX > width + 50 || scrY < -50 || scrY > height + 50) continue;
                boolean tooClose = false;
                for (float[] dp : drawnPixelPts) { if (Math.sqrt(Math.pow(scrX - dp[0], 2) + Math.pow(scrY - dp[1], 2)) < 60) { tooClose = true; break; } }
                if (tooClose) continue;
                drawnPixelPts.add(new float[]{scrX, scrY});
                float radius = 6f; canvas.drawCircle(x, y, radius, ctrlPaint); canvas.drawCircle(x, y, radius, gpsStrokePaint);
                if (showText) { ctrlText.setTextSize(14f); float textW = ctrlText.measureText(name); canvas.drawRect(x + 8, y - 16, x + 14 + textW, y + 2, ctrlTextBg); canvas.drawText(name, x + 11, y - 3, ctrlText); }
            }
        }

        // ================= 在这里直接绘制 CAD 图元（由硬件加速接管） =================
        if (hasCadData()) {
            double worldSize = 256 * Math.pow(2, tileZoom);
            if (showCadLine && !cadLines.isEmpty()) {
                for (CadLine line : cadLines) {
                    canvas.drawLine((float) (((line.mx1 * worldSize) - startX)), (float) (((line.my1 * worldSize) - startY)), (float) (((line.mx2 * worldSize) - startX)), (float) (((line.my2 * worldSize) - startY)), cadPaint);
                }
            }
            if (showCadPolyline && !cadPolylines.isEmpty()) {
                Path path = new Path();
                for (CadPolyline poly : cadPolylines) {
                    if (poly.pts.length < 4) continue;
                    path.reset();
                    path.moveTo((float) (poly.pts[0] * worldSize - startX), (float) (poly.pts[1] * worldSize - startY));
                    for (int i = 2; i < poly.pts.length; i += 2) path.lineTo((float) (poly.pts[i] * worldSize - startX), (float) (poly.pts[i + 1] * worldSize - startY));
                    canvas.drawPath(path, cadPaint);
                }
            }
            if (showCadCircle && !cadCircles.isEmpty()) {
                for (CadCircle circle : cadCircles) {
                    canvas.drawCircle((float) (((circle.cx * worldSize) - startX)), (float) (((circle.cy * worldSize) - startY)), (float) (circle.rM * worldSize), cadPaint);
                }
            }
            if (showCadText && !cadTexts.isEmpty()) {
                double metersPerScreenPixel = 156543.03392 * Math.cos(centerLat * Math.PI / 180.0) / (Math.pow(2, tileZoom) * totalScale);
                for (CadText txt : cadTexts) {
                    float textSizePx = (float) (txt.height / metersPerScreenPixel);
                    if (textSizePx < 10f) continue;
                    cadTextPaint.setTextSize(textSizePx / totalScale);
                    canvas.save();
                    canvas.translate((float) (((txt.mx * worldSize) - startX)), (float) (((txt.my * worldSize) - startY)));
                    canvas.rotate((float) -Math.toDegrees(txt.rotation));
                    canvas.drawText(txt.text, 0, 0, cadTextPaint);
                    canvas.restore();
                }
            }
        }

        if (hasGps) {
            double[] gpsWorld = latLngToWorldPixel(gpsLat, gpsLng, tileZoom);
            float gx = (float) (gpsWorld[0] - startX); float gy = (float) (gpsWorld[1] - startY);
            canvas.save(); canvas.translate(gx, gy); canvas.rotate(gpsBearing);
            canvas.drawCircle(0, 0, 8, gpsStrokePaint); canvas.drawCircle(0, 0, 6, gpsPaint);
            Paint triPaint = new Paint(); triPaint.setColor(0xCCFFFFFF); triPaint.setStyle(Paint.Style.FILL); triPaint.setAntiAlias(true);
            Paint triBorder = new Paint(); triBorder.setColor(0x33000000); triBorder.setStyle(Paint.Style.STROKE); triBorder.setStrokeWidth(1f); triBorder.setAntiAlias(true);
            Path triPath = new Path(); triPath.moveTo(0, -10); triPath.lineTo(-3.5f, -1); triPath.lineTo(3.5f, -1); triPath.close();
            canvas.drawPath(triPath, triPaint); canvas.drawPath(triPath, triBorder);
            Paint redDotPaint = new Paint(); redDotPaint.setColor(0xAAFF5722); redDotPaint.setStyle(Paint.Style.FILL); redDotPaint.setAntiAlias(true); canvas.drawCircle(0, -10, 2f, redDotPaint);
            canvas.restore();
        }

        canvas.restore();

        if (showRoadNames) drawRoadNameLabels(canvas, width, height, roadLabels);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        long currentTime = System.currentTimeMillis();

        if (action == MotionEvent.ACTION_DOWN) {
            lastX = event.getX(); lastY = event.getY();
            if (moveAnimator != null) moveAnimator.cancel(); if (zoomAnimator != null) zoomAnimator.cancel();
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (event.getPointerCount() >= 2) {
                lastDist = distance(event); twoFingerDownTime = currentTime;
                twoFingerStartX = (event.getX(0) + event.getX(1)) / 2f; twoFingerStartY = (event.getY(0) + event.getY(1)) / 2f;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (event.getPointerCount() == 1) {
                float dx = event.getX() - lastX; float dy = event.getY() - lastY;
                if (Math.abs(dx) > 20 || Math.abs(dy) > 20) lastTapTime = 0;
                double worldSize = 256 * Math.pow(2, zoom);
                centerLng -= (dx / scaleFactor) / worldSize * 360.0;
                double latRad = centerLat * Math.PI / 180.0;
                centerLat += (dy / scaleFactor) / worldSize * 360.0 * Math.cos(latRad);
                lastX = event.getX(); lastY = event.getY(); invalidate();
            } else if (event.getPointerCount() >= 2) {
                twoFingerDownTime = 0; float newDist = distance(event);
                if (newDist > 10 && lastDist > 10) {
                    float scale = newDist / lastDist; float nextZoom = zoom + (float) (Math.log(scale) / Math.log(2));
                    if (nextZoom >= 3.0f && nextZoom <= 22.0f) {
                        float midX = (event.getX(0) + event.getX(1)) / 2f; float midY = (event.getY(0) + event.getY(1)) / 2f;
                        float dx = midX - getWidth() / 2f; float dy = midY - getHeight() / 2f;
                        double worldSizeOld = 256 * Math.pow(2, zoom); double worldSizeNew = 256 * Math.pow(2, nextZoom); double ratio = worldSizeNew / worldSizeOld;
                        double cx = (centerLng + 180.0) / 360.0 * worldSizeOld; double sinLat = Math.sin(centerLat * Math.PI / 180.0); double cy = (0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI)) * worldSizeOld;
                        double tx = cx + dx / scaleFactor; double ty = cy + dy / scaleFactor; double newTx = tx * ratio; double newTy = ty * ratio; double newCx = newTx - dx / scaleFactor; double newCy = newTy - dy / scaleFactor;
                        centerLng = newCx / worldSizeNew * 360.0 - 180.0; double n = Math.PI - (2.0 * Math.PI * newCy / worldSizeNew); centerLat = 180.0 / Math.PI * Math.atan(Math.sinh(n)); zoom = nextZoom; invalidate();
                    }
                    lastDist = newDist;
                }
            }
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_UP) {
            if (twoFingerDownTime > 0 && currentTime - twoFingerDownTime < 300) {
                float midX = (event.getX(0) + event.getX(1)) / 2f; float midY = (event.getY(0) + event.getY(1)) / 2f;
                float moveDist = (float) Math.sqrt(Math.pow(midX - twoFingerStartX, 2) + Math.pow(midY - twoFingerStartY, 2));
                if (moveDist < 50) { float targetZoom = Math.max(3.0f, (float) Math.floor(zoom) - 1); animateZoom(targetZoom, midX, midY); }
            }
            twoFingerDownTime = 0;
            int remainIdx = event.getActionIndex() == 0 ? 1 : 0;
            if (remainIdx < event.getPointerCount()) { lastX = event.getX(remainIdx); lastY = event.getY(remainIdx); }
            lastDist = 0;
            return true;
        }

        if (action == MotionEvent.ACTION_UP) {
            if (currentTime - lastTapTime < 300) {
                float dx = event.getX() - lastTapX; float dy = event.getY() - lastTapY;
                float moveDist = (float) Math.sqrt(dx * dx + dy * dy);
                if (moveDist < 50) { float targetZoom = Math.min(22.0f, (float) Math.ceil(zoom) + 1); animateZoom(targetZoom, lastTapX, lastTapY); }
                lastTapTime = 0;
            } else { lastTapTime = currentTime; lastTapX = event.getX(); lastTapY = event.getY(); }
            lastDist = 0;
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            lastDist = 0; twoFingerDownTime = 0; lastTapTime = 0;
            return true;
        }
        return super.onTouchEvent(event);
    }

    private float distance(MotionEvent event) { float dx = event.getX(0) - event.getX(1); float dy = event.getY(0) - event.getY(1); return (float) Math.sqrt(dx * dx + dy * dy); }
    private float calcRoadAngle(List<float[]> pts, int midIdx) { if (pts == null || pts.size() < 2) return 0f; int i0 = Math.max(0, midIdx - 5); int i1 = Math.min(pts.size() - 1, midIdx + 5); if (i0 == i1) return 0f; float[] p0 = pts.get(i0); float[] p1 = pts.get(i1); float dx = p1[0] - p0[0]; float dy = p1[1] - p0[1]; if (Math.abs(dx) < 1e-3f && Math.abs(dy) < 1e-3f) return 0f; float angleDeg = (float) Math.toDegrees(Math.atan2(dy, dx)); while (angleDeg > 90f) angleDeg -= 180f; while (angleDeg <= -90f) angleDeg += 180f; return angleDeg; }
    private void drawRoadNameLabels(Canvas canvas, int width, int height, List<Object[]> roadLabels) { if (roadNames == null || roadLabels == null || roadLabels.isEmpty()) return; Paint namePaint = new Paint(); namePaint.setAntiAlias(true); namePaint.setTextSize(34f); namePaint.setTextAlign(Paint.Align.CENTER); namePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); Paint nameBg = new Paint(); nameBg.setColor(0xF0FFFFFF); nameBg.setStyle(Paint.Style.FILL); nameBg.setAntiAlias(true); Paint nameBorder = new Paint(); nameBorder.setStyle(Paint.Style.STROKE); nameBorder.setStrokeWidth(3f); nameBorder.setAntiAlias(true); float padH = 12f; float padV = 8f; for (int li = 0; li < roadLabels.size(); li++) { Object[] label = roadLabels.get(li); int r = (Integer) label[0]; float cx = (Float) label[1]; float cy = (Float) label[2]; float angle = (Float) label[3]; if (r < 0 || r >= roadNames.size()) continue; String name = roadNames.get(r); if (name == null || name.isEmpty()) continue; boolean isCurrent = (r == currentRoadIndex); namePaint.setColor(isCurrent ? CURRENT_ROAD_COLOR : palette[r % palette.length]); nameBorder.setColor(isCurrent ? CURRENT_ROAD_COLOR : palette[r % palette.length]); float textW = namePaint.measureText(name); float textSize = namePaint.getTextSize(); float bw = textW + padH * 2; float bh = textSize + padV * 2; if (cx < bw / 2f + 4) cx = bw / 2f + 4; if (cx > width - bw / 2f - 4) cx = width - bw / 2f - 4; if (cy < bh / 2f + 4) cy = bh / 2f + 4; if (cy > height - bh / 2f - 4) cy = height - bh / 2f - 4; canvas.save(); canvas.translate(cx, cy); canvas.rotate(angle); float offsetY = -bh / 2f - 6f; float bx = -bw / 2f; float by = offsetY - bh / 2f; canvas.drawRect(bx, by, bx + bw, by + bh, nameBg); canvas.drawRect(bx, by, bx + bw, by + bh, nameBorder); Paint.FontMetrics fm = namePaint.getFontMetrics(); float baseline = offsetY - (fm.ascent + fm.descent) / 2f; canvas.drawText(name, 0, baseline, namePaint); canvas.restore(); } }
    private boolean drawTileWithFallback(Canvas canvas, int z, int x, int y, float screenX, float screenY) { String urlTemplate = mapSources.get(currentMapIndex).urlTemplate; if (urlTemplate != null && urlTemplate.startsWith("blank://")) return true; Bitmap targetTile = getTile(z, x, y); if (targetTile != null) { canvas.drawBitmap(targetTile, screenX, screenY, tilePaint); return true; } int pZ = z - 1; if (pZ >= 0) { int pX = x >> 1; int pY = y >> 1; Bitmap parentTile = getTileDirect(pZ, pX, pY); if (parentTile != null) { int xOffset = (x % 2 == 0) ? 0 : 128; int yOffset = (y % 2 == 0) ? 0 : 128; srcRect.set(xOffset, yOffset, xOffset + 128, yOffset + 128); dstRect.set(screenX, screenY, screenX + 256, screenY + 256); canvas.drawBitmap(parentTile, srcRect, dstRect, tilePaint); return true; } } return false; }
    private Bitmap getTileDirect(int z, int x, int y) { String key = currentMapIndex + "_" + z + "/" + x + "/" + y; return memoryCache.get(key); }

    private Bitmap getTile(int z, int x, int y) {
        String urlTemplate = mapSources.get(currentMapIndex).urlTemplate;
        if (urlTemplate.startsWith("blank://")) return null;
        int maxTile = (int) Math.pow(2, z) - 1;
        if (x < 0 || y < 0 || x > maxTile || y > maxTile) return null;
        String key = currentMapIndex + "_" + z + "/" + x + "/" + y;
        Bitmap bmp = memoryCache.get(key); if (bmp != null) return bmp;
        TileDatabase db = getCurrentDb();
        if (db != null) { byte[] blob = db.getTile(z, x, y); if (blob != null) { Bitmap dbBmp = BitmapFactory.decodeByteArray(blob, 0, blob.length); if (dbBmp != null) { memoryCache.put(key, dbBmp); return dbBmp; } } }
        if (downloadingKeys.contains(key)) return null;
        downloadingKeys.add(key);
        final String urlStr = urlTemplate.replace("{x}", String.valueOf(x)).replace("{y}", String.valueOf(y)).replace("{z}", String.valueOf(z));
        executor.execute(() -> {
            try {
                URL url = new URL(urlStr); HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET"); conn.setConnectTimeout(5000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int len;
                    while ((len = is.read(buffer)) != -1) baos.write(buffer, 0, len); is.close(); byte[] imageBytes = baos.toByteArray();
                    if (db != null && imageBytes.length > 0) db.saveTile(z, x, y, imageBytes);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    if (bitmap != null) { memoryCache.put(key, bitmap); postInvalidate(); }
                }
                conn.disconnect();
            } catch (Exception e) { e.printStackTrace(); } finally { downloadingKeys.remove(key); }
        });
        return null;
    }
}