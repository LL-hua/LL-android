package com.hdm;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

    private List<MapRoadData> allRoadsList = new ArrayList<>();
    private int currentSelectedRoad = 0;
    private boolean isShowRoadNames = false;
    
    // 持久化状态
    private int currentMapTypeInt = CanvasMapView.MAP_ROAD; 
    private String currentMapTypeName = "路网"; 
    private String lastSelectedRoadName = "主线"; // 记录上次选中的路线名

    private AlertDialog settingsDialog; 

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

    // 辅助方法：生成圆角背景
    private GradientDrawable makeRoundRectBg(int color, float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(radiusDp));
        return gd;
    }

    private void initUI() {
        content.removeAllViews();

        // 1. 读取全局图源
        SharedPreferences sp = getSharedPreferences("hdm_config", MODE_PRIVATE);
        currentMapTypeInt = sp.getInt("last_map_type", CanvasMapView.MAP_ROAD);
        updateMapTypeName(currentMapTypeInt);
        
        // 2. 读取项目专属路线 (按项目名隔离)
        String projName = (app != null && app.currentProjectName != null) ? app.currentProjectName : "default";
        String spRoadKey = "last_road_name_" + projName;
        lastSelectedRoadName = sp.getString(spRoadKey, "主线");

        FrameLayout mapBox = new FrameLayout(this);
        mapBox.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        mapView = new CanvasMapView(this, null);
        // 恢复地图图源
        mapView.setMapType(currentMapTypeInt);
        mapBox.addView(mapView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        int fabSize = dp(48); // 统一悬浮按钮大小

        // ================= 1. 左上角：菜单按钮 (☰) =================
        TextView btnMenu = new TextView(this);
        btnMenu.setText("☰");
        btnMenu.setTextSize(24);
        btnMenu.setTextColor(0xFF333333);
        btnMenu.setBackground(makeRoundRectBg(0xF5FFFFFF, 8));
        btnMenu.setElevation(dp(4));
        btnMenu.setGravity(Gravity.CENTER);
        
        FrameLayout.LayoutParams lpMenu = new FrameLayout.LayoutParams(
                fabSize, fabSize,
                Gravity.TOP | Gravity.LEFT);
        lpMenu.topMargin = dp(16);
        lpMenu.leftMargin = dp(16);
        btnMenu.setLayoutParams(lpMenu);
        mapBox.addView(btnMenu);

        // ================= 2. 左下角：缩放全部 (FAB) =================
        TextView btnFitAll = new TextView(this);
        btnFitAll.setText("⛶");
        btnFitAll.setTextSize(22);
        btnFitAll.setTextColor(0xFF0078D4);
        btnFitAll.setBackground(makeRoundRectBg(0xF5FFFFFF, 24));
        btnFitAll.setElevation(dp(4));
        btnFitAll.setGravity(Gravity.CENTER);
        
        FrameLayout.LayoutParams lpFit = new FrameLayout.LayoutParams(
                fabSize, fabSize,
                Gravity.BOTTOM | Gravity.LEFT);
        lpFit.bottomMargin = dp(24);
        lpFit.leftMargin = dp(16);
        btnFitAll.setLayoutParams(lpFit);
        mapBox.addView(btnFitAll);

        // ================= 3. 右下角：一键定位 (FAB) =================
        TextView btnLocate = new TextView(this);
        btnLocate.setText("⌂");
        btnLocate.setTextSize(28);
        btnLocate.setTextColor(0xFF0078D4);
        btnLocate.setBackground(makeRoundRectBg(0xF5FFFFFF, 24));
        btnLocate.setElevation(dp(4));
        btnLocate.setGravity(Gravity.CENTER);
        
        FrameLayout.LayoutParams lpLoc = new FrameLayout.LayoutParams(
                fabSize, fabSize,
                Gravity.BOTTOM | Gravity.RIGHT);
        lpLoc.bottomMargin = dp(24);
        lpLoc.rightMargin = dp(16);
        btnLocate.setLayoutParams(lpLoc);
        mapBox.addView(btnLocate);

        content.addView(mapBox);

        // ================= 事件绑定 =================
        
        // 点击菜单弹出设置面板
        btnMenu.setOnClickListener(v -> showSettingsDialog());

        btnFitAll.setOnClickListener(v -> {
            if (mapView != null) mapView.zoomToFitAll();
        });

        btnLocate.setOnClickListener(v -> {
            if (lm != null && !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(RealMapActivity.this, "请先打开手机的GPS定位服务", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } else if (mapView != null) {
                mapView.locateMe();
            }
        });

        if (app.data != null) {
            loadAllRoutes();
        }
    }

    private void updateMapTypeName(int type) {
        if (type == CanvasMapView.MAP_ROAD) currentMapTypeName = "路网";
        else if (type == CanvasMapView.MAP_SATELLITE) currentMapTypeName = "卫星";
        else if (type == CanvasMapView.MAP_TOPO) currentMapTypeName = "地形";
        else if (type == CanvasMapView.MAP_OSM) currentMapTypeName = "OSM";
    }

    // 弹出设置控制台面板
    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(24), dp(24), dp(16));
        
        // 1. 标题
        TextView title = new TextView(this);
        title.setText("地图设置");
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(0xFF333333);
        layout.addView(title);
        
        // 2. 路名开关
        LinearLayout rowName = new LinearLayout(this);
        rowName.setOrientation(LinearLayout.HORIZONTAL);
        rowName.setGravity(Gravity.CENTER_VERTICAL);
        rowName.setPadding(0, dp(20), 0, dp(16));
        TextView tvName = new TextView(this);
        tvName.setText("显示道路名称");
        tvName.setTextSize(16);
        tvName.setTextColor(0xFF555555);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        rowName.addView(tvName);
        
        final TextView btnSwitch = new TextView(this);
        btnSwitch.setText(isShowRoadNames ? "已开启" : "已关闭");
        btnSwitch.setTextColor(isShowRoadNames ? Color.WHITE : 0xFF555555);
        btnSwitch.setBackground(makeRoundRectBg(isShowRoadNames ? 0xFF0078D4 : 0xFFE0E0E0, 16));
        btnSwitch.setPadding(dp(16), dp(6), dp(16), dp(6));
        btnSwitch.setOnClickListener(v -> {
            isShowRoadNames = !isShowRoadNames;
            btnSwitch.setText(isShowRoadNames ? "已开启" : "已关闭");
            btnSwitch.setTextColor(isShowRoadNames ? Color.WHITE : 0xFF555555);
            btnSwitch.setBackground(makeRoundRectBg(isShowRoadNames ? 0xFF0078D4 : 0xFFE0E0E0, 16));
            if (mapView != null) mapView.setShowRoadNames(isShowRoadNames);
        });
        rowName.addView(btnSwitch);
        layout.addView(rowName);
        
        // 分割线
        View line1 = new View(this);
        line1.setBackgroundColor(0xFFEEEEEE);
        LinearLayout.LayoutParams lpLine = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lpLine.bottomMargin = dp(16);
        layout.addView(line1, lpLine);
        
        // 3. 图源选择（展开，横排4个按钮）
        TextView tvMapTypeTitle = new TextView(this);
        tvMapTypeTitle.setText("地图图源");
        tvMapTypeTitle.setTextSize(16);
        tvMapTypeTitle.setTextColor(0xFF333333);
        tvMapTypeTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tvMapTypeTitle);
        
        String[] mapTypes = {"路网", "卫星", "地形", "OSM"};
        final int[] mapTypeValues = {
                CanvasMapView.MAP_ROAD, 
                CanvasMapView.MAP_SATELLITE, 
                CanvasMapView.MAP_TOPO, 
                CanvasMapView.MAP_OSM
        };
        LinearLayout mapTypeLayout = new LinearLayout(this);
        mapTypeLayout.setOrientation(LinearLayout.HORIZONTAL);
        mapTypeLayout.setPadding(0, dp(12), 0, dp(16));
        
        final TextView[] mapTypeBtns = new TextView[4];
        for (int i = 0; i < 4; i++) {
            final int index = i;
            TextView btn = new TextView(this);
            btn.setText(mapTypes[i]);
            btn.setTextSize(14);
            btn.setGravity(Gravity.CENTER);
            boolean isSelected = (currentMapTypeInt == mapTypeValues[i]);
            btn.setTextColor(isSelected ? Color.WHITE : 0xFF555555);
            btn.setBackground(makeRoundRectBg(isSelected ? 0xFF0078D4 : 0xFFF0F0F0, 16));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(8);
            btn.setLayoutParams(lp);
            btn.setPadding(0, dp(8), 0, dp(8));
            
            btn.setOnClickListener(v -> {
                if (mapView == null) return;
                currentMapTypeInt = mapTypeValues[index];
                updateMapTypeName(currentMapTypeInt);
                
                // 更新地图
                mapView.setMapType(currentMapTypeInt);
                
                // 保存到 SharedPreferences 持久化
                getSharedPreferences("hdm_config", MODE_PRIVATE).edit()
                        .putInt("last_map_type", currentMapTypeInt).apply();
                
                // 更新按钮选中状态
                for (int j = 0; j < 4; j++) {
                    boolean sel = (currentMapTypeInt == mapTypeValues[j]);
                    mapTypeBtns[j].setTextColor(sel ? Color.WHITE : 0xFF555555);
                    mapTypeBtns[j].setBackground(makeRoundRectBg(sel ? 0xFF0078D4 : 0xFFF0F0F0, 16));
                }
            });
            mapTypeBtns[i] = btn;
            mapTypeLayout.addView(btn);
        }
        layout.addView(mapTypeLayout);
        
        // 分割线
        View line2 = new View(this);
        line2.setBackgroundColor(0xFFEEEEEE);
        layout.addView(line2, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        
        // 4. 路线选择（展开，直接平铺列表）
        TextView tvRouteTitle = new TextView(this);
        tvRouteTitle.setText("当前路线");
        tvRouteTitle.setTextSize(16);
        tvRouteTitle.setTextColor(0xFF333333);
        tvRouteTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvRouteTitle.setPadding(0, dp(16), 0, dp(12));
        layout.addView(tvRouteTitle);
        
        if (allRoadsList.isEmpty()) {
            TextView tvNoRoute = new TextView(this);
            tvNoRoute.setText("未加载路线数据");
            tvNoRoute.setTextColor(0xFF999999);
            tvNoRoute.setPadding(0, dp(12), 0, dp(12));
            layout.addView(tvNoRoute);
        } else {
            for (int i = 0; i < allRoadsList.size(); i++) {
                final int index = i;
                String roadName = allRoadsList.get(i).name;
                boolean isSelected = (currentSelectedRoad == i);
                
                TextView btnRoute = new TextView(this);
                btnRoute.setText(roadName);
                btnRoute.setTextSize(16);
                btnRoute.setTextColor(isSelected ? 0xFF0078D4 : 0xFF333333);
                btnRoute.setPadding(dp(12), dp(12), dp(12), dp(12));
                if (isSelected) {
                    btnRoute.setBackground(makeRoundRectBg(0x1A0078D4, 8)); // 淡蓝色背景
                }
                
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(4);
                btnRoute.setLayoutParams(lp);
                
                btnRoute.setOnClickListener(v -> {
                    currentSelectedRoad = index;
                    lastSelectedRoadName = roadName; // 记录名称
                    
                    // 持久化路线名称（按项目隔离）
                    String projName = (app != null && app.currentProjectName != null) ? app.currentProjectName : "default";
                    String spRoadKey = "last_road_name_" + projName;
                    
                    getSharedPreferences("hdm_config", MODE_PRIVATE).edit()
                            .putString(spRoadKey, lastSelectedRoadName).apply();

                    if (mapView != null) mapView.switchRoad(index);
                    // 选中路线后，自动关闭面板让用户看地图
                    if (settingsDialog != null) settingsDialog.dismiss();
                });
                layout.addView(btnRoute);
            }
        }
        
        scrollView.addView(layout);
        builder.setView(scrollView);
        builder.setPositiveButton("完成", null);
        settingsDialog = builder.create();
        settingsDialog.show();
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

            mapView.setMultiRouteData(multiData, roadNames);
            mapView.setShowRoadNames(isShowRoadNames);

            // 匹配上次选中的路线名称
            int targetIndex = 0;
            for (int i = 0; i < allRoadsList.size(); i++) {
                if (allRoadsList.get(i).name.equals(lastSelectedRoadName)) {
                    targetIndex = i;
                    break;
                }
            }
            currentSelectedRoad = targetIndex;
            lastSelectedRoadName = allRoadsList.get(currentSelectedRoad).name; // 防错更新
            
            // 因为 setMultiRouteData 内部默认调用了 switchRoad(0)，如果 targetIndex 不是 0 才需要单独触发
            if (targetIndex != 0) {
                mapView.switchRoad(targetIndex);
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