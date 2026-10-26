package com.hdm;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class RealMapActivity extends BaseNavActivity {

    private MyApp app;
    private CanvasMapView mapView;
    private LinearLayout content;

    private LocationManager lm;
    private LocationListener listener;
    private static final int REQ_LOCATION = 200;

    @Override
    protected String currentTab() { return "realmap"; }

    @Override
    protected String pageTitle() { return "在线地图"; }

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
    protected void onProjectLoaded() {
        initUI();
        if (lm != null && listener == null) startGps();
    }

    private void initUI() {
        content.removeAllViews();

        FrameLayout mapBox = new FrameLayout(this);
        mapBox.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        mapView = new CanvasMapView(this, null);
        mapBox.addView(mapView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 右上角：图源切换
        TextView btnType = new TextView(this);
        btnType.setText("卫星");
        btnType.setTextSize(14);
        btnType.setTextColor(0xFF0078D4);
        btnType.setBackgroundColor(0xEEFFFFFF);
        btnType.setPadding(dp(16), dp(8), dp(16), dp(8));
        btnType.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lpType = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        lpType.topMargin = dp(16);
        lpType.rightMargin = dp(16);
        btnType.setLayoutParams(lpType);
        mapBox.addView(btnType);

        btnType.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (mapView != null) {
                    String txt = btnType.getText().toString();
                    if (txt.equals("卫星")) {
                        mapView.setMapType(CanvasMapView.MAP_SATELLITE);
                        btnType.setText("地形");
                    } else if (txt.equals("地形")) {
                        mapView.setMapType(CanvasMapView.MAP_TOPO);
                        btnType.setText("路网");
                    } else {
                        mapView.setMapType(CanvasMapView.MAP_ROAD);
                        btnType.setText("卫星");
                    }
                }
            }
        });

        // 右下角：定位按钮 (⌂)
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
                if (mapView != null) mapView.locateMe();
            }
        });

        content.addView(mapBox);

        if (app.data != null) {
            loadRouteToMap();
        }
    }

    private void loadRouteToMap() {
        if (mapView == null) return;
        try {
            List<double[]> route = LL.dantiaoxianlu(app.data.pqx, 0, 10.0);
            List<Object[]> mapData = new ArrayList<>();
            
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

            for (int i = 0; i < route.size(); i++) {
                double[] pt = route.get(i);
                double[] lsxy = Projection.fourParameterTransform(pt[1], pt[2], cs4[0], cs4[1], cs4[2], cs4[3]);
                double[] lb = isUtm ? Projection.utm_unproj(lsxy[0], lsxy[1], north, zone) : Projection.gauss_unproj(lsxy[0], lsxy[1], center);

                double lon = lb[0];
                double lat = lb[1];

                if (Double.isNaN(lon) || Double.isNaN(lat) || lon == 0.0) continue;

                String kStr = LL.hua_Num2K(pt[0]);
                mapData.add(new Object[]{lat, lon, kStr, pt[3]});
            }
            mapView.setRouteData(mapData);
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
            @Override public void onLocationChanged(Location loc) {
                final double lon = loc.getLongitude();
                final double lat = loc.getLatitude();

                runOnUiThread(() -> {
                    if (app.data == null) {
                        mapView.updateGps(lat, lon, "未选项目", "");
                        return;
                    }
                    double[] xy = Projection.gps2Xy(lon, lat, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                    if (xy == null || xy.length < 2) return;

                    double[] kb = LL.hua_Fs(app.data.pqx, xy[0], xy[1]);
                    String stationStr = kb[0] < 0 ? "超出范围" : LL.hua_Num2K(kb[0]);
                    String offsetStr = kb[0] < 0 ? "--" : String.format("%.3f m (%s)", Math.abs(kb[1]), kb[1] < 0 ? "左" : "右");
                    
                    mapView.updateGps(lat, lon, stationStr, offsetStr);
                });
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, listener); } catch (Exception e) {}
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startGps();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lm != null && listener != null) { try { lm.removeUpdates(listener); } catch (Exception e) {} }
    }
}
