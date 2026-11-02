package com.hdm;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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

    // 传感器相关
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    
    // 融合算法相关变量
    private float currentAzimuth = 0f;       // 屏幕上最终显示的平滑角度
    private float targetBearing = 0f;        // 目标角度（来源于传感器或GPS）
    private float currentOffset = 180.0f;    // 用户自定义的校准偏移量
    private long lastGpsBearingTime = 0L;    // 上一次收到有效GPS航向的时间
    
    private EditText etOffset;

    private List<MapRoadData> allRoadsList = new ArrayList<>();
    private int currentSelectedRoad = 0;
    private boolean isShowRoadNames = false;
    
    // 持久化状态
    private int currentMapTypeInt = CanvasMapView.MAP_ROAD; 
    private String currentMapTypeName = "路网"; 
    private String lastSelectedRoadName = "主线";

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

        // 初始化传感器
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }

        initUI();
        startGps();
    }

    @Override
    protected void onProjectLoaded() {
        initUI();
        if (lm != null && listener == null) startGps();
    }

    private GradientDrawable makeRoundRectBg(int color, float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(radiusDp));
        return gd;
    }

    private void initUI() {
        content.removeAllViews();

        // 读取持久化状态
        SharedPreferences sp = getSharedPreferences("hdm_config", MODE_PRIVATE);
        currentMapTypeInt = sp.getInt("last_map_type", CanvasMapView.MAP_ROAD);
        currentOffset = sp.getFloat("compass_offset", 180.0f);
        updateMapTypeName(currentMapTypeInt);
        
        String projName = (app != null && app.currentProjectName != null) ? app.currentProjectName : "default";
        String spRoadKey = "last_road_name_" + projName;
        lastSelectedRoadName = sp.getString(spRoadKey, "主线");

        FrameLayout mapBox = new FrameLayout(this);
        mapBox.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        mapView = new CanvasMapView(this, null);
        mapView.setMapType(currentMapTypeInt);
        mapBox.addView(mapView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        int fabSize = dp(48);

        // 1. 左上角：菜单按钮 (☰)
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

        // ================= 2. 右上角：方向校准输入框 =================
        LinearLayout offsetLayout = new LinearLayout(this);
        offsetLayout.setOrientation(LinearLayout.HORIZONTAL);
        offsetLayout.setGravity(Gravity.CENTER_VERTICAL);
        offsetLayout.setBackground(makeRoundRectBg(0xF5FFFFFF, 8));
        offsetLayout.setElevation(dp(4));
        offsetLayout.setPadding(dp(12), dp(4), dp(12), dp(4));

        TextView tvOffsetLabel = new TextView(this);
        tvOffsetLabel.setText("校准:");
        tvOffsetLabel.setTextSize(14);
        tvOffsetLabel.setTextColor(0xFF555555);
        offsetLayout.addView(tvOffsetLabel);

        etOffset = new EditText(this);
        etOffset.setText(String.valueOf(currentOffset));
        etOffset.setTextSize(14);
        etOffset.setTextColor(0xFF333333);
        etOffset.setBackground(null);
        etOffset.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        etOffset.setWidth(dp(60));
        etOffset.setGravity(Gravity.CENTER);
        offsetLayout.addView(etOffset);

        TextView tvDegree = new TextView(this);
        tvDegree.setText("°");
        tvDegree.setTextSize(14);
        tvDegree.setTextColor(0xFF555555);
        offsetLayout.addView(tvDegree);

        FrameLayout.LayoutParams lpOffset = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        lpOffset.topMargin = dp(16);
        lpOffset.rightMargin = dp(16);
        offsetLayout.setLayoutParams(lpOffset);
        mapBox.addView(offsetLayout);

        etOffset.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                try {
                    if (s.length() > 0 && !s.toString().equals("-") && !s.toString().equals(".")) {
                        currentOffset = Float.parseFloat(s.toString());
                        getSharedPreferences("hdm_config", MODE_PRIVATE).edit()
                                .putFloat("compass_offset", currentOffset).apply();
                    }
                } catch (Exception e) {}
            }
        });

        mapView.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && etOffset != null) {
                imm.hideSoftInputFromWindow(etOffset.getWindowToken(), 0);
                etOffset.clearFocus();
            }
        });

        // 3. 左下角：缩放全部 (FAB)
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

        // 4. 右下角：一键定位 (FAB)
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

    // ================= 传感器回调：平滑滤波 =================
    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                float[] rotationMatrix = new float[9];
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                
                float[] orientation = new float[3];
                SensorManager.getOrientation(rotationMatrix, orientation);
                
                float azimuthRad = orientation[0];
                float rawAzimuthDeg = (float) Math.toDegrees(azimuthRad);
                if (rawAzimuthDeg < 0) {
                    rawAzimuthDeg += 360;
                }
                
                // 应用校准偏移量
                float sensorBearing = (rawAzimuthDeg + currentOffset) % 360f;
                if (sensorBearing < 0) sensorBearing += 360f;

                // 融合逻辑：如果超过 3 秒没有收到有效的 GPS 速度航向，才使用传感器的角度
                if (System.currentTimeMillis() - lastGpsBearingTime > 3000) {
                    targetBearing = sensorBearing;
                }

                // 统一平滑处理（无论 targetBearing 来自 GPS 还是传感器，都要平滑）
                float diff = targetBearing - currentAzimuth;
                if (diff > 180) diff -= 360;
                if (diff < -180) diff += 360;

                // 由于 GPS 突变可能很大，稍微加大一点平滑系数让它追上去
                float alpha = 0.15f; 
                currentAzimuth = currentAzimuth + alpha * diff;
                
                if (currentAzimuth < 0) currentAzimuth += 360;
                if (currentAzimuth >= 360) currentAzimuth -= 360;
                
                if (mapView != null) {
                    mapView.setGpsBearing(currentAzimuth);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private void updateMapTypeName(int type) {
        if (type == CanvasMapView.MAP_ROAD) currentMapTypeName = "路网";
        else if (type == CanvasMapView.MAP_SATELLITE) currentMapTypeName = "卫星";
        else if (type == CanvasMapView.MAP_TOPO) currentMapTypeName = "地形";
        else if (type == CanvasMapView.MAP_OSM) currentMapTypeName = "OSM";
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(24), dp(24), dp(16));
        
        TextView title = new TextView(this);
        title.setText("地图设置");
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(0xFF333333);
        layout.addView(title);
        
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
        
        View line1 = new View(this);
        line1.setBackgroundColor(0xFFEEEEEE);
        LinearLayout.LayoutParams lpLine = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lpLine.bottomMargin = dp(16);
        layout.addView(line1, lpLine);
        
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
                
                mapView.setMapType(currentMapTypeInt);
                
                getSharedPreferences("hdm_config", MODE_PRIVATE).edit()
                        .putInt("last_map_type", currentMapTypeInt).apply();
                
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
        
        View line2 = new View(this);
        line2.setBackgroundColor(0xFFEEEEEE);
        layout.addView(line2, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        
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
                    btnRoute.setBackground(makeRoundRectBg(0x1A0078D4, 8)); 
                }
                
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(4);
                btnRoute.setLayoutParams(lp);
                
                btnRoute.setOnClickListener(v -> {
                    currentSelectedRoad = index;
                    lastSelectedRoadName = roadName;
                    
                    String projName = (app != null && app.currentProjectName != null) ? app.currentProjectName : "default";
                    String spRoadKey = "last_road_name_" + projName;
                    
                    getSharedPreferences("hdm_config", MODE_PRIVATE).edit()
                            .putString(spRoadKey, lastSelectedRoadName).apply();

                    if (mapView != null) mapView.switchRoad(index);
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

            int targetIndex = 0;
            for (int i = 0; i < allRoadsList.size(); i++) {
                if (allRoadsList.get(i).name.equals(lastSelectedRoadName)) {
                    targetIndex = i;
                    break;
                }
            }
            currentSelectedRoad = targetIndex;
            lastSelectedRoadName = allRoadsList.get(currentSelectedRoad).name;
            
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

                // ================= 核心融合逻辑：优先使用 GPS 航向 =================
                // loc.hasBearing() 表示 GPS 芯片算出了移动方向
                // loc.getSpeed() 的速度大于 1.2m/s（约 4.3km/h，快步走的速度）时，方向才可靠
                if (loc.hasBearing() && loc.getSpeed() > 1.2f) {
                    targetBearing = loc.getBearing();
                    lastGpsBearingTime = System.currentTimeMillis();
                }

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
        // 恢复传感器监听
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(sensorListener, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 暂停传感器以省电
        if (sensorManager != null) {
            sensorManager.unregisterListener(sensorListener);
        }
    }
}