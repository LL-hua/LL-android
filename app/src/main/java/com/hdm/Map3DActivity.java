package com.hdm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Map3DActivity extends Activity {

    private WebView webView;
    private MyApp app;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRunning = true;

    // 和 2D 页面一模一样的调色板
    private final String[] palette = {
            "#1E88E5", "#43A047", "#FB8C00", "#8E24AA", "#00ACC1",
            "#D81B60", "#6D4C41", "#7CB342", "#3949AB", "#546E7A"
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        app = (MyApp) getApplication();
        webView = new WebView(this);
        setContentView(webView);

        double lat = getIntent().getDoubleExtra("lat", 39.9042);
        double lng = getIntent().getDoubleExtra("lng", 116.4074);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                
                String dbName = null;
                if (url.contains("mt1.google.com/vt/lyrs=r")) dbName = "hdm_road.mbtiles";
                else if (url.contains("mt1.google.com/vt/lyrs=y")) dbName = "hdm_sat.mbtiles";
                else if (url.contains("a.tile.opentopomap.org")) dbName = "hdm_topo.mbtiles";
                else if (url.contains("tile.openstreetmap.org")) dbName = "hdm_osm.mbtiles";

                if (dbName != null) {
                    try {
                        Matcher m = Pattern.compile("(\\d+)/(\\d+)/(\\d+)\\.png").matcher(url);
                        if (m.find()) {
                            int z = Integer.parseInt(m.group(1));
                            int x = Integer.parseInt(m.group(2));
                            int y = Integer.parseInt(m.group(3));

                            TileDatabase db = new TileDatabase(view.getContext(), dbName);
                            byte[] data = db.getTile(z, x, y);
                            if (data != null) {
                                return new WebResourceResponse("image/png", "utf-8", new ByteArrayInputStream(data));
                            }
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void closeActivity() { runOnUiThread(() -> finish()); }
        }, "AndroidInterface");

        String geoJson = getRoadGeoJson();

        try {
            String encodedGeo = URLEncoder.encode(geoJson, "UTF-8");
            String url = "file:///android_asset/earth3d.html?lat=" + lat + "&lng=" + lng + "&geojson=" + encodedGeo;
            webView.loadUrl(url);
        } catch (Exception e) {}

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                double cLat = app.hasRtkData ? app.currentRtkLat : app.currentGpsLat;
                double cLng = app.hasRtkData ? app.currentRtkLon : app.currentGpsLon;
                if (cLat != 0 && cLng != 0) {
                    webView.evaluateJavascript("updateMyLocation(" + cLat + "," + cLng + ")", null);
                }
                handler.postDelayed(this, 1000); 
            }
        }, 1000);
    }

    private String getRoadGeoJson() {
        if (app.data == null) return "";
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"type\":\"FeatureCollection\",\"features\":[");
            boolean isFirst = true;

            // 1. 主线，颜色固定为 #0078D4
            String mainJson = createLineJson(app.data.pqx, "main", "#0078D4");
            if (!mainJson.isEmpty()) {
                sb.append(mainJson);
                isFirst = false;
            }

            // 2. 其它线路，循环分配颜色
            if (app.data.extraRoads != null) {
                for (int i = 0; i < app.data.extraRoads.size(); i++) {
                    MapRoadData extra = app.data.extraRoads.get(i);
                    String color = palette[i % palette.length];
                    String extraJson = createLineJson(extra.pqx, "extra", color);
                    if (!extraJson.isEmpty()) {
                        if (!isFirst) sb.append(",");
                        sb.append(extraJson);
                        isFirst = false;
                    }
                }
            }
            sb.append("]}");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String createLineJson(double[][] pqx, String type, String color) {
        try {
            List<double[]> route = LL.dantiaoxianlu(pqx, 0, 20.0);
            if (route.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            // 将颜色塞进 properties
            sb.append("{\"type\":\"Feature\",\"properties\":{\"type\":\"").append(type).append("\",\"color\":\"").append(color).append("\"},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[");

            double[][] lonlat = app.data.controlLonLat;
            double[][] xy = app.data.controlXy;
            if (lonlat == null || lonlat.length == 0) return "";

            double center = 0.0;
            for (double[] ll : lonlat) center += ll[0];
            center = center / lonlat.length;

            double[] sourceXY = new double[lonlat.length * 2];
            double[] targetXY = new double[lonlat.length * 2];
            boolean isUtm = app.data.projection.toLowerCase().contains("utm");

            for (int i = 0; i < lonlat.length; i++) {
                double[] projected = isUtm ? Projection.utm_proj(lonlat[i][0], lonlat[i][1]) : Projection.gauss_proj(lonlat[i][0], lonlat[i][1], center);
                sourceXY[i * 2] = projected[0];
                sourceXY[i * 2 + 1] = projected[1];
                targetXY[i * 2] = xy[i][0];
                targetXY[i * 2 + 1] = xy[i][1];
            }
            double[] cs4 = Projection.cs4(targetXY, sourceXY);
            int zone = (int) Math.floor((center + 180) / 6) + 1;
            boolean north = lonlat[0][1] >= 0;

            for (int i = 0; i < route.size(); i++) {
                double[] pt = route.get(i);
                double[] lsxy = Projection.fourParameterTransform(pt[1], pt[2], cs4[0], cs4[1], cs4[2], cs4[3]);
                double[] lb = isUtm ? Projection.utm_unproj(lsxy[0], lsxy[1], north, zone) : Projection.gauss_unproj(lsxy[0], lsxy[1], center);

                if (i > 0) sb.append(",");
                sb.append("[").append(lb[0]).append(",").append(lb[1]).append("]");
            }
            sb.append("]}}");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onDestroy() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}