package com.hdm;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import android.os.Environment;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RealMapActivity extends BaseNavActivity {

    private MyApp app;
    private CanvasMapView mapView;
    private LinearLayout content;

    private LocationManager lm;
    private LocationListener listener;
    private static final int REQ_LOCATION = 200;
    private static final int REQ_BLUETOOTH = 201;

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private float currentAzimuth = 0f;
    private float targetBearing = 0f;
    private float currentOffset = 180.0f;
    private long lastGpsBearingTime = 0L;
    
    private EditText etOffset;

    private List<MapRoadData> allRoadsList = new ArrayList<>();
    private int currentSelectedRoad = 0;
    private boolean isShowRoadNames = false;
    
    private String currentMapSourceName = "";
    private String lastSelectedRoadName = "主线";

    private AlertDialog settingsDialog; 
    
    private String rtkStatus = "未连接";
    private TextView tvRtkStatus;

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

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }

        initUI();
        startGps();
        setupRtk();
    }

    @Override
    protected void onProjectLoaded() {
        initUI();
        if (lm != null && listener == null) startGps();
    }

    private void setupRtk() {
        app.rtkManager.setCallback(new BluetoothRtkManager.RtkCallback() {
            @Override
            public void onRtkData(double lat, double lon, double alt, int fixQuality) {
                app.currentRtkLat = lat;
                app.currentRtkLon = lon;
                app.currentRtkAlt = alt;
                app.currentRtkFix = fixQuality;
                app.hasRtkData = true;
                app.rtkConnected = true; 

                String fixStr = fixQuality == 4 ? "固定解" : (fixQuality == 5 ? "浮点解" : "单点");
                rtkStatus = "RTK " + fixStr;
                if (tvRtkStatus != null) tvRtkStatus.setText(rtkStatus);

                updateMapLocation(lat, lon);
            }

            @Override
            public void onRtkStatus(String status) {
                rtkStatus = status;
                if (status.startsWith("已连接")) {
                    app.rtkConnected = true;
                } else if (status.equals("未连接") || status.equals("连接失败") || status.equals("连接断开")) {
                    app.rtkConnected = false;
                    app.hasRtkData = false;
                }
                if (tvRtkStatus != null) tvRtkStatus.setText(rtkStatus);
            }
        });
    }

    private void updateMapLocation(double lat, double lon) {
        if (app.data == null || allRoadsList.isEmpty() || mapView == null) return;
        
        MapRoadData currentRoad = allRoadsList.get(currentSelectedRoad);

        double[] xy = Projection.gps2Xy(lon, lat, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
        if (xy == null || xy.length < 2) return;

        if (app.hasRtkData && app.currentRtkFix > 0) {
            xy[0] += app.rtkOffsetX;
            xy[1] += app.rtkOffsetY;
        }

        double[] kb = LL.hua_Fs(currentRoad.pqx, xy[0], xy[1]);
        String stationStr = kb[0] < 0 ? "超出范围" : LL.hua_Num2K(kb[0]);
        String offsetStr = kb[0] < 0 ? "--" : String.format("%.3f m (%s)", Math.abs(kb[1]), kb[1] < 0 ? "左" : "右");

        if (app.hasRtkData && app.currentRtkFix > 0) {
            stationStr += " [RTK]";
        }

        mapView.updateGps(lat, lon, stationStr, offsetStr);
    }

    private GradientDrawable makeRoundRectBg(int color, float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(radiusDp));
        return gd;
    }

    private List<MapSource> loadMapSources() {
        List<MapSource> sources = new ArrayList<>();
        File sd = Environment.getExternalStorageDirectory();
        File dir = new File(sd, "1hdm");
        if (!dir.exists()) dir.mkdirs();
        
        File file = new File(dir, "maps.txt");
        if (!file.exists()) {
            String defaultTxt = "谷歌路网,https://mt1.google.com/vt/lyrs=r&hl=zh-CN&gl=cn&x={x}&y={y}&z={z}\n" +
                    "谷歌卫星,https://mt1.google.com/vt/lyrs=y&hl=zh-CN&gl=cn&x={x}&y={y}&z={z}\n" +
                    "地形图,https://a.tile.opentopomap.org/{z}/{x}/{y}.png\n" +
                    "OSM,https://tile.openstreetmap.org/{z}/{x}/{y}.png\n";
            try {
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(defaultTxt.getBytes("UTF-8"));
                fos.close();
            } catch (Exception e) {}
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    sources.add(new MapSource(parts[0].trim(), parts[1].trim()));
                }
            }
            br.close();
        } catch (Exception e) {}

        if (sources.isEmpty()) {
            sources.add(new MapSource("谷歌路网", "https://mt1.google.com/vt/lyrs=r&hl=zh-CN&gl=cn&x={x}&y={y}&z={z}"));
        }
        return sources;
    }

    private void initUI() {
        content.removeAllViews();

        SharedPreferences sp = getSharedPreferences("hdm_config", MODE_PRIVATE);
        currentMapSourceName = sp.getString("last_map_source_name", "谷歌路网");
        currentOffset = sp.getFloat("compass_offset", 180.0f);
        
        String projName = (app != null && app.currentProjectName != null) ? app.currentProjectName : "default";
        String spRoadKey = "last_road_name_" + projName;
        lastSelectedRoadName = sp.getString(spRoadKey, "主线");

        FrameLayout mapBox = new FrameLayout(this);
        mapBox.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        mapView = new CanvasMapView(this, null);
        
        List<MapSource> sources = loadMapSources();
        mapView.setMapSources(sources);
        
        int targetIdx = 0;
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).name.equals(currentMapSourceName)) {
                targetIdx = i; break;
            }
        }
        mapView.setMapSourceIndex(targetIdx);
        currentMapSourceName = sources.get(targetIdx).name;

        mapBox.addView(mapView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        int fabSize = dp(48);

        TextView btnMenu = new TextView(this);
        btnMenu.setText("☰");
        btnMenu.setTextSize(24);
        btnMenu.setTextColor(0xFF333333);
        btnMenu.setBackground(makeRoundRectBg(0xF5FFFFFF, 8));
        btnMenu.setElevation(dp(4));
        btnMenu.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lpMenu = new FrameLayout.LayoutParams(fabSize, fabSize, Gravity.TOP | Gravity.LEFT);
        lpMenu.topMargin = dp(16); lpMenu.leftMargin = dp(16);
        btnMenu.setLayoutParams(lpMenu);
        mapBox.addView(btnMenu);

        tvRtkStatus = new TextView(this);
        tvRtkStatus.setText(rtkStatus);
        tvRtkStatus.setTextSize(12);
        tvRtkStatus.setTextColor(0xFF555555);
        tvRtkStatus.setBackground(makeRoundRectBg(0xE0FFFFFF, 8));
        tvRtkStatus.setPadding(dp(12), dp(6), dp(12), dp(6));
        FrameLayout.LayoutParams lpRtk = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lpRtk.topMargin = dp(16);
        tvRtkStatus.setLayoutParams(lpRtk);
        mapBox.addView(tvRtkStatus);

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

        FrameLayout.LayoutParams lpOffset = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
        lpOffset.topMargin = dp(16); lpOffset.rightMargin = dp(16);
        offsetLayout.setLayoutParams(lpOffset);
        mapBox.addView(offsetLayout);

        etOffset.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                try {
                    if (s.length() > 0 && !s.toString().equals("-") && !s.toString().equals(".")) {
                        currentOffset = Float.parseFloat(s.toString());
                        getSharedPreferences("hdm_config", MODE_PRIVATE).edit().putFloat("compass_offset", currentOffset).apply();
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

        TextView btnFitAll = new TextView(this);
        btnFitAll.setText("⛶");
        btnFitAll.setTextSize(22);
        btnFitAll.setTextColor(0xFF0078D4);
        btnFitAll.setBackground(makeRoundRectBg(0xF5FFFFFF, 24));
        btnFitAll.setElevation(dp(4));
        btnFitAll.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lpFit = new FrameLayout.LayoutParams(fabSize, fabSize, Gravity.BOTTOM | Gravity.LEFT);
        lpFit.bottomMargin = dp(24); lpFit.leftMargin = dp(16);
        btnFitAll.setLayoutParams(lpFit);
        mapBox.addView(btnFitAll);

        TextView btnLocate = new TextView(this);
        btnLocate.setText("⌂");
        btnLocate.setTextSize(28);
        btnLocate.setTextColor(0xFF0078D4);
        btnLocate.setBackground(makeRoundRectBg(0xF5FFFFFF, 24));
        btnLocate.setElevation(dp(4));
        btnLocate.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lpLoc = new FrameLayout.LayoutParams(fabSize, fabSize, Gravity.BOTTOM | Gravity.RIGHT);
        lpLoc.bottomMargin = dp(24); lpLoc.rightMargin = dp(16);
        btnLocate.setLayoutParams(lpLoc);
        mapBox.addView(btnLocate);

        content.addView(mapBox);

        btnMenu.setOnClickListener(v -> showSettingsDialog());
        btnFitAll.setOnClickListener(v -> mapView.zoomToFitAll());
        btnLocate.setOnClickListener(v -> {
            if (app.rtkConnected) {
                if (mapView != null) mapView.locateMe();
                return;
            }
            if (lm != null && !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(RealMapActivity.this, "请先打开手机的GPS定位服务", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } else if (mapView != null) {
                mapView.locateMe();
            }
        });

        if (app.data != null) loadAllRoutes();
    }

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
                if (rawAzimuthDeg < 0) rawAzimuthDeg += 360;
                
                float sensorBearing = (rawAzimuthDeg + currentOffset) % 360f;
                if (sensorBearing < 0) sensorBearing += 360f;

                if (System.currentTimeMillis() - lastGpsBearingTime > 3000) {
                    targetBearing = sensorBearing;
                }

                float diff = targetBearing - currentAzimuth;
                if (diff > 180) diff -= 360;
                if (diff < -180) diff += 360;
                currentAzimuth = currentAzimuth + 0.15f * diff;
                
                if (currentAzimuth < 0) currentAzimuth += 360;
                if (currentAzimuth >= 360) currentAzimuth -= 360;
                if (mapView != null) mapView.setGpsBearing(currentAzimuth);
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

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

        TextView btnRtk = new TextView(this);
        btnRtk.setText("连接蓝牙 RTK (当前: " + rtkStatus + ")");
        btnRtk.setTextSize(16);
        btnRtk.setTextColor(0xFF0078D4);
        btnRtk.setPadding(0, dp(16), 0, dp(16));
        btnRtk.setOnClickListener(v -> checkAndRequestBluetooth());
        layout.addView(btnRtk);

        TextView btnCorrection = new TextView(this);
        btnCorrection.setText("RTK 点校正 (当前偏移: X=" + String.format("%.3f", app.rtkOffsetX) + ", Y=" + String.format("%.3f", app.rtkOffsetY) + ")");
        btnCorrection.setTextSize(16);
        btnCorrection.setTextColor(0xFFD32F2F); 
        btnCorrection.setPadding(0, 0, 0, dp(16));
        btnCorrection.setOnClickListener(v -> showCorrectionDialog());
        layout.addView(btnCorrection);
        
        View line0 = new View(this);
        line0.setBackgroundColor(0xFFEEEEEE);
        layout.addView(line0, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        
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
        tvMapTypeTitle.setText("地图图源 (可修改 1hdm/maps.txt 扩充)");
        tvMapTypeTitle.setTextSize(16);
        tvMapTypeTitle.setTextColor(0xFF333333);
        tvMapTypeTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tvMapTypeTitle);
        
        final List<MapSource> sources = mapView.getMapSources();
        final List<TextView> mapTypeBtns = new ArrayList<>();
        
        LinearLayout mapTypeLayout = new LinearLayout(this);
        mapTypeLayout.setOrientation(LinearLayout.HORIZONTAL);
        mapTypeLayout.setPadding(0, dp(12), 0, dp(16));
        
        for (int i = 0; i < sources.size(); i++) {
            final int index = i;
            MapSource src = sources.get(i);
            
            if (i > 0 && i % 3 == 0) {
                layout.addView(mapTypeLayout);
                mapTypeLayout = new LinearLayout(this);
                mapTypeLayout.setOrientation(LinearLayout.HORIZONTAL);
                mapTypeLayout.setPadding(0, 0, 0, dp(12));
            }
            
            TextView btn = new TextView(this);
            btn.setText(src.name);
            btn.setTextSize(14);
            btn.setGravity(Gravity.CENTER);
            boolean isSelected = mapView.getMapSourceIndex() == i;
            btn.setTextColor(isSelected ? Color.WHITE : 0xFF555555);
            btn.setBackground(makeRoundRectBg(isSelected ? 0xFF0078D4 : 0xFFF0F0F0, 16));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i % 3 != 0) lp.leftMargin = dp(8);
            btn.setLayoutParams(lp);
            btn.setPadding(0, dp(8), 0, dp(8));
            
            btn.setOnClickListener(v -> {
                if (mapView == null) return;
                mapView.setMapSourceIndex(index);
                currentMapSourceName = src.name;
                getSharedPreferences("hdm_config", MODE_PRIVATE).edit()
                        .putString("last_map_source_name", currentMapSourceName).apply();
                
                for (int j = 0; j < mapTypeBtns.size(); j++) {
                    boolean sel = (mapView.getMapSourceIndex() == j);
                    mapTypeBtns.get(j).setTextColor(sel ? Color.WHITE : 0xFF555555);
                    mapTypeBtns.get(j).setBackground(makeRoundRectBg(sel ? 0xFF0078D4 : 0xFFF0F0F0, 16));
                }
            });
            mapTypeBtns.add(btn);
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
                if (isSelected) btnRoute.setBackground(makeRoundRectBg(0x1A0078D4, 8)); 
                
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(4);
                btnRoute.setLayoutParams(lp);
                
                btnRoute.setOnClickListener(v -> {
                    currentSelectedRoad = index;
                    lastSelectedRoadName = roadName;
                    
                    String projName = (app != null && app.currentProjectName != null) ? app.currentProjectName : "default";
                    String spRoadKey = "last_road_name_" + projName;
                    getSharedPreferences("hdm_config", MODE_PRIVATE).edit().putString(spRoadKey, lastSelectedRoadName).apply();

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

    // ================= RTK 点校正对话框 =================
    private void showCorrectionDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView tvTips = new TextView(this);
        tvTips.setText("将 RTK 杆放在已知控制点上，点击“读取当前RTK”填入当前坐标，再输入该控制点的设计(X,Y)坐标，点击保存即可算出平移量。");
        tvTips.setTextSize(14);
        tvTips.setTextColor(0xFF666666);
        layout.addView(tvTips);

        // 输入框：当前 RTK 坐标
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, dp(16), 0, dp(8));
        TextView tv1 = new TextView(this); tv1.setText("当前RTK: "); tv1.setTextSize(16);
        row1.addView(tv1);
        final EditText etCurX = new EditText(this); etCurX.setHint("X"); etCurX.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        etCurX.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); row1.addView(etCurX);
        final EditText etCurY = new EditText(this); etCurY.setHint("Y"); etCurY.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        etCurY.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); row1.addView(etCurY);
        layout.addView(row1);

        // 读取当前 RTK 按钮
        TextView btnRead = new TextView(this);
        btnRead.setText("↺ 读取当前 RTK 平面坐标");
        btnRead.setTextColor(0xFF0078D4);
        btnRead.setTextSize(14);
        btnRead.setPadding(0, 0, 0, dp(16));
        btnRead.setOnClickListener(v -> {
            if (!app.hasRtkData || app.currentRtkFix == 0) {
                Toast.makeText(this, "未收到 RTK 信号", Toast.LENGTH_SHORT).show(); return;
            }
            double[] xy = Projection.gps2Xy(app.currentRtkLon, app.currentRtkLat, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
            if (xy != null && xy.length >= 2) {
                etCurX.setText(String.format("%.3f", xy[0]));
                etCurY.setText(String.format("%.3f", xy[1]));
            }
        });
        layout.addView(btnRead);

        // 输入框：已知点坐标
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, 0, 0, dp(16));
        TextView tv2 = new TextView(this); tv2.setText("已知点值: "); tv2.setTextSize(16);
        row2.addView(tv2);
        final EditText etTarX = new EditText(this); etTarX.setHint("设计 X"); etTarX.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        etTarX.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); row2.addView(etTarX);
        final EditText etTarY = new EditText(this); etTarY.setHint("设计 Y"); etTarY.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        etTarY.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); row2.addView(etTarY);
        layout.addView(row2);

        // ================= 新增：读取控制点文件 =================
        File ctrlFile = new File(Environment.getExternalStorageDirectory(), "1hdm/控制点.txt");
        final List<String> ctrlNames = new ArrayList<>();
        final List<double[]> ctrlCoords = new ArrayList<>();

        if (ctrlFile.exists()) {
            try {
                FileInputStream fis = new FileInputStream(ctrlFile);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split(",");
                    if (parts.length >= 3) {
                        ctrlNames.add(parts[0].trim());
                        double x = Double.parseDouble(parts[1].trim());
                        double y = Double.parseDouble(parts[2].trim());
                        ctrlCoords.add(new double[]{x, y});
                    }
                }
                br.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (!ctrlNames.isEmpty()) {
            TextView btnSelectCtrl = new TextView(this);
            btnSelectCtrl.setText("↓ 从 控制点.txt 选择 (共" + ctrlNames.size() + "个)");
            btnSelectCtrl.setTextColor(0xFF0078D4);
            btnSelectCtrl.setTextSize(14);
            btnSelectCtrl.setPadding(0, 0, 0, dp(16));
            btnSelectCtrl.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("选择已知控制点")
                        .setItems(ctrlNames.toArray(new String[0]), (dialog, which) -> {
                            etTarX.setText(String.format("%.3f", ctrlCoords.get(which)[0]));
                            etTarY.setText(String.format("%.3f", ctrlCoords.get(which)[1]));
                        })
                        .show();
            });
            layout.addView(btnSelectCtrl);
        } else {
            TextView tvNoCtrl = new TextView(this);
            tvNoCtrl.setText("(未发现 1hdm/控制点.txt，可手动输入)");
            tvNoCtrl.setTextColor(0xFF999999);
            tvNoCtrl.setTextSize(12);
            tvNoCtrl.setPadding(0, 0, 0, dp(16));
            layout.addView(tvNoCtrl);
        }
        // ========================================================

        // ================= 新增：改正数显示与手动输入 =================
        View lineDiv = new View(this);
        lineDiv.setBackgroundColor(0xFFEEEEEE);
        layout.addView(lineDiv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setPadding(0, dp(16), 0, dp(8));
        TextView tv3 = new TextView(this); tv3.setText("改正数: "); tv3.setTextSize(16); tv3.setTextColor(0xFFD32F2F);
        row3.addView(tv3);
        final EditText etDX = new EditText(this); etDX.setHint("ΔX"); etDX.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        etDX.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); row3.addView(etDX);
        final EditText etDY = new EditText(this); etDY.setHint("ΔY"); etDY.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        etDY.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); row3.addView(etDY);
        layout.addView(row3);

        // 初始化当前的偏移量
        etDX.setText(String.format("%.3f", app.rtkOffsetX));
        etDY.setText(String.format("%.3f", app.rtkOffsetY));

        // 双向绑定逻辑
        final boolean[] isUpdating = {false};
        
        // 监听 当前值和设计值，自动算改正数
        TextWatcher valWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (isUpdating[0]) return;
                isUpdating[0] = true;
                try {
                    double cx = etCurX.getText().toString().isEmpty() || etCurX.getText().toString().equals("-") ? 0 : Double.parseDouble(etCurX.getText().toString());
                    double cy = etCurY.getText().toString().isEmpty() || etCurY.getText().toString().equals("-") ? 0 : Double.parseDouble(etCurY.getText().toString());
                    double tx = etTarX.getText().toString().isEmpty() || etTarX.getText().toString().equals("-") ? 0 : Double.parseDouble(etTarX.getText().toString());
                    double ty = etTarY.getText().toString().isEmpty() || etTarY.getText().toString().equals("-") ? 0 : Double.parseDouble(etTarY.getText().toString());
                    etDX.setText(String.format("%.3f", tx - cx));
                    etDY.setText(String.format("%.3f", ty - cy));
                } catch (Exception e) {}
                isUpdating[0] = false;
            }
        };
        etCurX.addTextChangedListener(valWatcher);
        etCurY.addTextChangedListener(valWatcher);
        etTarX.addTextChangedListener(valWatcher);
        etTarY.addTextChangedListener(valWatcher);

        // 监听改正数，反推设计值
        TextWatcher offsetWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (isUpdating[0]) return;
                isUpdating[0] = true;
                try {
                    double cx = etCurX.getText().toString().isEmpty() || etCurX.getText().toString().equals("-") ? 0 : Double.parseDouble(etCurX.getText().toString());
                    double cy = etCurY.getText().toString().isEmpty() || etCurY.getText().toString().equals("-") ? 0 : Double.parseDouble(etCurY.getText().toString());
                    double dx = etDX.getText().toString().isEmpty() || etDX.getText().toString().equals("-") ? 0 : Double.parseDouble(etDX.getText().toString());
                    double dy = etDY.getText().toString().isEmpty() || etDY.getText().toString().equals("-") ? 0 : Double.parseDouble(etDY.getText().toString());
                    etTarX.setText(String.format("%.3f", cx + dx));
                    etTarY.setText(String.format("%.3f", cy + dy));
                } catch (Exception e) {}
                isUpdating[0] = false;
            }
        };
        etDX.addTextChangedListener(offsetWatcher);
        etDY.addTextChangedListener(offsetWatcher);
        // ========================================================

        new AlertDialog.Builder(this)
                .setTitle("RTK 单点平移校正")
                .setView(layout)
                .setPositiveButton("保存校正", (dialog, which) -> {
                    try {
                        // 这里直接读取改正数，因为在双向绑定中，改正数已经被算好了
                        double dx = etDX.getText().toString().isEmpty() ? 0 : Double.parseDouble(etDX.getText().toString());
                        double dy = etDY.getText().toString().isEmpty() ? 0 : Double.parseDouble(etDY.getText().toString());

                        app.rtkOffsetX = dx;
                        app.rtkOffsetY = dy;
                        
                        getSharedPreferences("hdm_config", MODE_PRIVATE).edit()
                                .putFloat("rtk_offset_x", (float) app.rtkOffsetX)
                                .putFloat("rtk_offset_y", (float) app.rtkOffsetY).apply();

                        Toast.makeText(this, "校正成功！偏移已保存", Toast.LENGTH_SHORT).show();
                        
                        if (app.hasRtkData) updateMapLocation(app.currentRtkLat, app.currentRtkLon);
                        
                        if (settingsDialog != null) settingsDialog.dismiss();

                    } catch (Exception e) {
                        Toast.makeText(this, "输入格式错误", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("清除校正", (dialog, which) -> {
                    app.rtkOffsetX = 0;
                    app.rtkOffsetY = 0;
                    getSharedPreferences("hdm_config", MODE_PRIVATE).edit()
                            .putFloat("rtk_offset_x", 0f)
                            .putFloat("rtk_offset_y", 0f).apply();
                    Toast.makeText(this, "已清除校正", Toast.LENGTH_SHORT).show();
                    if (app.hasRtkData) updateMapLocation(app.currentRtkLat, app.currentRtkLon);
                    if (settingsDialog != null) settingsDialog.dismiss();
                })
                .show();
    }

    private void checkAndRequestBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                }, REQ_BLUETOOTH);
                return;
            }
        }
        showRtkDeviceDialog();
    }

    private void showRtkDeviceDialog() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!adapter.isEnabled()) {
            Toast.makeText(this, "请先打开手机蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
            List<String> deviceNames = new ArrayList<>();
            List<BluetoothDevice> devices = new ArrayList<>();

            deviceNames.add("断开连接");
            devices.add(null);

            for (BluetoothDevice device : pairedDevices) {
                deviceNames.add(device.getName() + "\n" + device.getAddress());
                devices.add(device);
            }

            new AlertDialog.Builder(this)
                    .setTitle("选择 RTK 蓝牙设备 (需先在系统蓝牙中配对)")
                    .setItems(deviceNames.toArray(new String[0]), (dialog, which) -> {
                        if (which == 0) {
                            app.rtkManager.disconnect();
                            rtkStatus = "未连接";
                            app.rtkConnected = false;
                            app.hasRtkData = false;
                            if (tvRtkStatus != null) tvRtkStatus.setText(rtkStatus);
                        } else {
                            app.rtkManager.connect(devices.get(which));
                        }
                    })
                    .show();
        } catch (SecurityException e) {
            Toast.makeText(this, "缺少蓝牙权限，请检查系统设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadAllRoutes() {
        if (mapView == null) return;
        try {
            allRoadsList.clear();
            List<String> roadNames = new ArrayList<>();
            List<List<Object[]>> multiData = new ArrayList<>();

            MapRoadData mainRoad = new MapRoadData("主线", app.data.pqx);
            allRoadsList.add(mainRoad);

            if (app.data.extraRoads != null) allRoadsList.addAll(app.data.extraRoads);

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

            for (MapRoadData road : allRoadsList) {
                roadNames.add(road.name);
                List<double[]> route = LL.dantiaoxianlu(road.pqx, 0, 10.0);
                List<Object[]> mapData = new ArrayList<>();

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
            if (targetIndex != 0) mapView.switchRoad(targetIndex);

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
                if (!app.rtkConnected && !app.hasRtkData) {
                    final double lon = loc.getLongitude();
                    final double lat = loc.getLatitude();
                    updateMapLocation(lat, lon);

                    if (loc.hasBearing() && loc.getSpeed() > 1.2f) {
                        targetBearing = loc.getBearing();
                        lastGpsBearingTime = System.currentTimeMillis();
                    }
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {
                try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, listener); } catch (Exception e) {}
            }

            @Override
            public void onProviderDisabled(String provider) {}
        };

        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, listener); } catch (Exception e) {}
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startGps();
        } else if (requestCode == REQ_BLUETOOTH) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showRtkDeviceDialog();
            } else {
                Toast.makeText(this, "需要蓝牙权限才能连接 RTK", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lm != null && listener != null) {
            try { lm.removeUpdates(listener); } catch (Exception e) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lm != null && listener != null) {
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, listener); } catch (Exception e) {}
        }
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(sensorListener, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(sensorListener);
    }
}