package com.hdm;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class RealMapActivity extends BaseNavActivity {

    private MyApp app;
    private WebView webView;
    private LinearLayout content;

    private LocationManager lm;
    private LocationListener listener;
    private static final int REQ_LOCATION = 200;

    @Override
    protected String currentTab() { return "realmap"; }

    @Override
    protected String pageTitle() { return "实景地图"; }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (MyApp) getApplication();

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        setContent(content);

        initUI();
        startGps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lm != null && listener == null) startGps();
    }

    @Override
    protected void onProjectLoaded() {
        initUI();
        if (lm != null && listener == null) startGps();
    }

    private void initUI() {
        content.removeAllViews();
        if (app.data == null) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("请点击右上角选择项目文件");
            tvEmpty.setTextSize(16);
            tvEmpty.setGravity(Gravity.CENTER);
            content.addView(tvEmpty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
            return;
        }

        FrameLayout mapBox = new FrameLayout(this);
        mapBox.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // 初始化瓦片数据库
        LocalTileProvider.init(this);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                WebResourceResponse res = LocalTileProvider.interceptTile(url);
                if (res != null) return res;
                return super.shouldInterceptRequest(view, request);
            }

            @SuppressWarnings("deprecation")
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                WebResourceResponse res = LocalTileProvider.interceptTile(url);
                if (res != null) return res;
                return super.shouldInterceptRequest(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                sendRouteToJs();
            }
        });
        
        webView.loadUrl("file:///android_asset/map.html");
        mapBox.addView(webView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // ==========================================
        // 右下角悬浮定位按钮 (小房子 ⌂)
        // ==========================================
        TextView btnLocate = new TextView(this);
        btnLocate.setText("⌂");
        btnLocate.setTextSize(32);
        btnLocate.setTextColor(0xFF0078D4);
        btnLocate.setBackgroundColor(0xEEFFFFFF);
        btnLocate.setPadding(dp(16), dp(4), dp(16), dp(8));
        btnLocate.setGravity(Gravity.CENTER);
        
        FrameLayout.LayoutParams lpLoc = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, 
                FrameLayout.LayoutParams.WRAP_CONTENT, 
                Gravity.BOTTOM | Gravity.RIGHT);
        lpLoc.bottomMargin = dp(30); 
        lpLoc.rightMargin = dp(16);
        btnLocate.setLayoutParams(lpLoc); 
        mapBox.addView(btnLocate);

        btnLocate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                webView.evaluateJavascript("window.locateMe();", null);
            }
        });

        content.addView(mapBox);
    }

    private void sendRouteToJs() {
        if (webView == null || app.data == null) return;

        try {
            List<double[]> route = LL.dantiaoxianlu(app.data.pqx, 0, 10.0);
            
            double[][] lonlat = app.data.controlLonLat;
            double[][] xy = app.data.controlXy;
            
            if (lonlat == null || xy == null || lonlat.length == 0 || lonlat.length != xy.length) return;

            double[] sourceXY = new double[lonlat.length * 2];
            double[] targetXY = new double[lonlat.length * 2];
            double center = 0.0;
            for (double[] ll : lonlat) center += ll[0];
            center = center / lonlat.length;

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

            StringBuilder jsData = new StringBuilder("[");
            int validCount = 0;

            for (int i = 0; i < route.size(); i++) {
                double[] pt = route.get(i);
                double[] lsxy = Projection.fourParameterTransform(pt[1], pt[2], cs4[0], cs4[1], cs4[2], cs4[3]);
                double[] lb = isUtm ? Projection.utm_unproj(lsxy[0], lsxy[1], north, zone) : Projection.gauss_unproj(lsxy[0], lsxy[1], center);

                double lon = lb[0];
                double lat = lb[1];

                if (Double.isNaN(lon) || Double.isNaN(lat) || lon == 0.0) continue;

                if (validCount > 0) jsData.append(",");
                String kStr = LL.hua_Num2K(pt[0]);
                jsData.append(String.format("[%.8f,%.8f,\"%s\",%.4f]", lat, lon, kStr, pt[3]));
                validCount++;
            }
            jsData.append("]");

            if (validCount > 0) {
                webView.evaluateJavascript("window.drawRoute(" + jsData.toString() + ");", null);
            } else {
                Toast.makeText(this, "有效点数为0", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startGps() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                return;
            }
        }
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null || !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return;

        listener = new LocationListener() {
            @Override public void onLocationChanged(Location loc) { handleLocation(loc); }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, listener); } catch (Exception e) {}
    }

    private void handleLocation(Location loc) {
        if (webView == null || app.data == null) return;
        final double lon = loc.getLongitude();
        final double lat = loc.getLatitude();
        final float acc = loc.hasAccuracy() ? loc.getAccuracy() : 0f;

        double[] xy = Projection.gps2Xy(lon, lat, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
        if (xy == null || xy.length < 2) return;

        double[] kb = LL.hua_Fs(app.data.pqx, xy[0], xy[1]);
        final String stationStr = kb[0] < 0 ? "超出范围" : LL.hua_Num2K(kb[0]);
        final String offsetStr = kb[0] < 0 ? "--" : String.format("%.3f m (%s)", Math.abs(kb[1]), kb[1] < 0 ? "左" : "右");

        runOnUiThread(new Runnable() {
            @Override public void run() {
                String js = String.format("window.updateGps(%.8f, %.8f, %.2f, '%s', '%s');", lat, lon, acc, stationStr, offsetStr);
                webView.evaluateJavascript(js, null);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startGps();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 修复报错：把 removeListener 改回 removeUpdates
        if (lm != null && listener != null) { 
            try { lm.removeUpdates(listener); } catch (Exception e) {} 
        }
        if (webView != null) { webView.destroy(); }
    }
}
