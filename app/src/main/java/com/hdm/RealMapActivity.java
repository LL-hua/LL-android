package com.hdm;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class RealMapActivity extends BaseNavActivity {

    private MyApp app;
    private CanvasMapView mapView;
    private LinearLayout content;
    private Spinner spRoads;
    private TextView btnType;
    private TextView btnRoadNames; // 新增：路名开关

    private LocationManager lm;
    private LocationListener listener;
    private static final int REQ_LOCATION = 200;

    private List<MapRoadData> allRoadsList = new ArrayList<>();
    private int currentSelectedRoad = 0;
    private boolean isShowRoadNames = false; // 默认不显示

    @Override
    protected String currentTab() {
        return "realmap";
    }

    @Override
    protected String pageTitle() {
        return "在线地图";
    }

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

        // 左上角：路名开关
        btnRoadNames = new TextView(this);
        btnRoadNames.setText("显示路名");
        btnRoadNames.setTextSize(14);
        btnRoadNames.setTextColor(0xFF0078D4);
        btnRoadNames.setBackgroundColor(0xEEFFFFFF);
        btnRoadNames.setPadding(dp(16), dp(8), dp(16), dp(8));
        btnRoadNames.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lpNames = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT);
        lpNames.topMargin = dp(16);
        lpNames.leftMargin = dp(16);
        btnRoadNames.setLayoutParams(lpNames);
        mapBox.addView(btnRoadNames);

        btnRoadNames.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isShowRoadNames = !isShowRoadNames;
                btnRoadNames.setText(isShowRoadNames ? "隐藏路名" : "显示路名");
                if (mapView != null) {
                    mapView.setShowRoadNames(isShowRoadNames);
                }
            }
        });

        // 右上角：路线选择下拉框
        spRoads = new Spinner(this);
        spRoads.setBackgroundColor(0xEEFFFFFF);
        FrameLayout.LayoutParams lpSp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        lpSp.topMargin = dp(16);
        lpSp.rightMargin = dp(16);
        spRoads.setLayoutParams(lpSp);
        mapBox.addView(spRoads);

        spRoads.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentSelectedRoad = position;
                if (mapView != null) mapView.switchRoad(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 图源切换按钮
        btnType = new TextView(this);
        btnType.setText("路网");
        btnType.setTextSize(14);
        btnType.setTextColor(0xFF0078D4);
        btnType.setBackgroundColor(0xEEFFFFFF);
        btnType.setPadding(dp(16), dp(8), dp(16), dp(8));
        btnType.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lpType = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        lpType.topMargin = dp(60);
        lpType.rightMargin = dp(16);
        btnType.setLayoutParams(lpType);
        mapBox.addView(btnType);

        btnType.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMapTypeDialog();
            }
        });

        // 左下角：缩放全部道路按钮
        TextView btnFitAll = new TextView(this);
        btnFitAll.setText("⛶");
        btnFitAll.setTextSize(24);
        btnFitAll.setTextColor(0xFF0078D4);
        btnFitAll.setBackgroundColor(0xEEFFFFFF);
        btnFitAll.setPadding(dp(10), dp(8), dp(10), dp(8));
        btnFitAll.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams lpFit = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.LEFT);
        lpFit.bottomMargin = dp(30);
        lpFit.leftMargin = dp(16);
        btnFitAll.setLayoutParams(lpFit);
        mapBox.addView(btnFitAll);

        btnFitAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mapView != null) mapView.zoomToFitAll();
            }
        });

        // 右下角：一键定位
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
            @Override
            public void onClick(View v) {
                if (lm != null && !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    Toast.makeText(RealMapActivity.this, "请先打开手机的GPS定位服务", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                } else if (mapView != null) {
                    mapView.locateMe();
                }
            }
        });

        content.addView(mapBox);

        if (app.data != null) {
            loadAllRoutes();
        }
    }

    private void showMapTypeDialog() {
        final String[] items = {"谷歌路网", "谷歌卫星", "地形图", "OSM 地图"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择底图");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (mapView == null) return;
                switch (which) {
                    case 0:
                        mapView.setMapType(CanvasMapView.MAP_ROAD);
                        btnType.setText("路网");
                        break;
                    case 1:
                        mapView.setMapType(CanvasMapView.MAP_SATELLITE);
                        btnType.setText("卫星");
                        break;
                    case 2:
                        mapView.setMapType(CanvasMapView.MAP_TOPO);
                        btnType.setText("地形");
                        break;
                    case 3:
                        mapView.setMapType(CanvasMapView.MAP_OSM);
                        btnType.setText("OSM");
                        break;
                }
            }
        });
        builder.show();
    }

    private void loadAllRoutes() {
        if (mapView == null) return;
        try {
            allRoadsList.clear();
            List<String> roadNames = new ArrayList<>();
            List<List<Object[]>> multiData = new ArrayList<>();

            MapRoadData mainRoad = new MapRoadData("主线", app.data.pqx);
            allRoadsList.add(mainRoad);

            if (app.data.extraRoads != null) {
                allRoadsList.addAll(app.data.extraRoads);
            }

            double[][] lonlat = app.data.controlLonLat;
            double[][] xy = app.data.controlXy;
            if (lonlat == null || xy == null || lonlat.length == 0 || lonlat.length != xy.length)
                return;

            double[] sourceXY = new double[lonlat.length * 2];
            double[] targetXY = new double[lonlat.length * 2];
            double center = 0.0;
            for (double[] ll : lonlat) center += ll[0];
            center = center / lonlat.length;

            boolean isUtm = app.data.projection.toLowerCase().contains("utm");

            for (int i = 0; i < lonlat.length; i++) {
                double[] projected = isUtm ? Projection.utm_proj(lonlat[i][0], lonlat[i][
                        1]) : Projection.gauss_proj(lonlat[i][0], lonlat[i][1], center);
                sourceXY[i * 2] = projected[0];
                sourceXY[i * 2 + 1] = projected[1];
                targetXY[i * 2] = xy[i][0];
                targetXY[i * 2 + 1] = xy[i][1];
            }

            double[] cs4 = Projection.cs4(targetXY, sourceXY);
            int zone = (int) Math.floor((center + 180) / 6) + 1;
            boolean north = lonlat[0][1] >= 0;

            for (MapRoadData road : allRoadsList) {
                roadNames.add(road.name);
                List<double[]> route = LL.dantiaoxianlu(road.pqx, 0, 10.0);
                List<Object[]> mapData = new ArrayList<>();

                for (int i = 0; i < route.size(); i++) {
                    double[] pt = route.get(i);
                    double[] lsxy = Projection.fourParameterTransform(pt[1], pt[2], cs4[0], cs4[
                            1], cs4[2], cs4[3]);
                    double[] lb = isUtm ? Projection.utm_unproj(lsxy[0], lsxy[
                            1], north, zone) : Projection.gauss_unproj(lsxy[0], lsxy[1], center);

                    double lon = lb[0];
                    double lat = lb[1];
                    if (Double.isNaN(lon) || Double.isNaN(lat) || lon == 0.0) continue;

                    String kStr = LL.hua_Num2K(pt[0]);
                    mapData.add(new Object[]{lat, lon, kStr, pt[3]});
                }
                multiData.add(mapData);
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roadNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spRoads.setAdapter(adapter);

            // 传入数据并初始化路名显示状态
            mapView.setMultiRouteData(multiData, roadNames);
            mapView.setShowRoadNames(isShowRoadNames);

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
        if (lm == null) return;

        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location loc) {
                final double lon = loc.getLongitude();
                final double lat = loc.getLatitude();

                runOnUiThread(() -> {
                    if (app.data == null || allRoadsList.isEmpty()) return;
                    MapRoadData currentRoad = allRoadsList.get(currentSelectedRoad);

                    double[] xy = Projection.gps2Xy(lon, lat, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                    if (xy == null || xy.length < 2) return;

                    double[] kb = LL.hua_Fs(currentRoad.pqx, xy[0], xy[1]);
                    String stationStr = kb[0] < 0 ? "超出范围" : LL.hua_Num2K(kb[0]);
                    String offsetStr = kb[0] < 0 ? "--" : String.format("%.3f m (%s)", Math.abs(kb[
                            1]), kb[1] < 0 ? "左" : "右");

                    mapView.updateGps(lat, lon, stationStr, offsetStr);
                });
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {
                try {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, listener);
                } catch (Exception e) {
                }
            }

            @Override
            public void onProviderDisabled(String provider) {}
        };

        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, listener);
        } catch (Exception e) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0 && grantResults[
                0] == PackageManager.PERMISSION_GRANTED) startGps();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lm != null && listener != null) {
            try {
                lm.removeUpdates(listener);
            } catch (Exception e) {
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lm != null && listener != null) {
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, listener);
            } catch (Exception e) {
            }
        }
    }
}