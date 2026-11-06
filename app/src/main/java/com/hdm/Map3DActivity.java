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

public class Map3DActivity extends Activity {

    private WebView webView;
    private MyApp app;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRunning = true;

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

        // ================= 核心：根据 URL 类型拦截本地瓦片缓存 =================
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // 拦截格式：hdm://tile/road/16/52345/34567.png
                if (url.startsWith("hdm://tile/")) {
                    try {
                        String[] parts = url.replace("hdm://tile/", "").split("/");
                        String type = parts[0]; // road, sat, topo, osm
                        int z = Integer.parseInt(parts[1]);
                        int x = Integer.parseInt(parts[2]);
                        int y = Integer.parseInt(parts[3].replace(".png", ""));

                        // 映射数据库名称
                        String dbName = "hdm_road.mbtiles";
                        if (type.equals("sat")) dbName = "hdm_sat.mbtiles";
                        else if (type.equals("topo")) dbName = "hdm_topo.mbtiles";
                        else if (type.equals("osm")) dbName = "hdm_osm.mbtiles";

                        TileDatabase db = new TileDatabase(view.getContext(), dbName);
                        byte[] data = db.getTile(z, x, y);
                        if (data != null) {
                            return new WebResourceResponse("image/png", "utf-8", new ByteArrayInputStream(data));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void closeActivity() {
                runOnUiThread(() -> finish());
            }
        }, "AndroidInterface");

        // 获取道路数据
        String geoJson = getRoadGeoJson();

        try {
            String encodedGeo = URLEncoder.encode(geoJson, "UTF-8");
            String url = "file:///android_asset/earth3d.html?lat=" + lat + "&lng=" + lng + "&geojson=" + encodedGeo;
            webView.loadUrl(url);
        } catch (Exception e) {}

        // ================= 核心：定时推送 GPS/RTK 位置 =================
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                
                // 优先用 RTK，其次用手机 GPS
                double cLat = app.hasRtkData ? app.currentRtkLat : app.currentGpsLat;
                double cLng = app.hasRtkData ? app.currentRtkLon : app.currentGpsLon;
                
                if (cLat != 0 && cLng != 0) {
                    String js = "updateMyLocation(" + cLat + "," + cLng + ")";
                    webView.evaluateJavascript(js, null);
                }
                handler.postDelayed(this, 1000); 
            }
        }, 1000);
    }

    private String getRoadGeoJson() {
        if (app.data == null) return "";
        try {
            List<double[]> route = LL.dantiaoxianlu(app.data.pqx, 0, 20.0);
            if (route.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[");

            double[][] lonlat = app.data.controlLonLat;
            double[][] xy = app.data.controlXy;
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
            sb.append("]}}]}");
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