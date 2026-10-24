package com.hdm;

import android.Manifest;
import android.app.Activity;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MapActivity extends Activity {

    private MyApp app;
    private TextView tvInfo;
    private MapView mapView;

    private LocationManager lm;
    private LocationListener listener;

    private static final int REQ_LOCATION = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (MyApp) getApplication();

        if (app.data == null) {
            Toast.makeText(this, "项目未加载", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);

        tvInfo = new TextView(this);
        tvInfo.setText("项目: " + app.currentProjectName);
        root.addView(tvInfo);

        FrameLayout chartBox = new FrameLayout(this);
        LinearLayout.LayoutParams lpBox = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        chartBox.setLayoutParams(lpBox);

        mapView = new MapView(this);
        mapView.setBackgroundColor(0xFFFFFFFF);
        FrameLayout.LayoutParams lpView = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        mapView.setLayoutParams(lpView);
        chartBox.addView(mapView);

        TextView btnReset = new TextView(this);
        btnReset.setText("⌂");
        btnReset.setTextSize(32);
        btnReset.setTextColor(0xFF0078D4);
        btnReset.setBackgroundColor(0xCCFFFFFF);
        btnReset.setPadding(20, 5, 20, 10);
        btnReset.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lpReset = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        lpReset.topMargin = 10;
        lpReset.rightMargin = 10;
        btnReset.setLayoutParams(lpReset);
        chartBox.addView(btnReset);

        root.addView(chartBox);
        setContentView(root);

        btnReset.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mapView.resetView();
                }
            });

        loadRoute();
        startGps();
    }

    // ============================================================
    // 从设置页面回来后，自动重新检查并开启 GPS
    // ============================================================
    @Override
    protected void onResume() {
        super.onResume();
        // 如果已经有定位权限，且之前没在监听（说明是刚从没开GPS的设置页退回来）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (lm != null && listener == null) {
                    startGps();
                }
            }
        }
    }

    private void loadRoute() {
        try {
            List<double[]> route = LL.dantiaoxianlu(app.data.pqx, 0, 10.0);
            mapView.setRoute(route, 1000.0);
            tvInfo.setText("线路已加载，共 " + route.size() + " 个点");
        } catch (Exception e) {
            tvInfo.setText("线路加载失败: " + e.getMessage());
        }
    }

    private void startGps() {
        // 1. 动态权限检查
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                tvInfo.setText("正在申请 GPS 权限...");
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, REQ_LOCATION);
                return;
            }
        }

        lm = (LocationManager) getSystemService(LOCATION_SERVICE);

        // 2. 【核心新增】检查手机系统 GPS 开关是否打开
        boolean isGpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!isGpsEnabled) {
            tvInfo.setText("❌ 手机 GPS 未开启");
            
            // 弹窗提示并跳转到设置页
            new AlertDialog.Builder(this)
                    .setTitle("位置服务未开启")
                    .setMessage("为了获取实时桩号和偏距，请打开系统的位置信息（GPS）开关。")
                    .setPositiveButton("去开启", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // 跳转到系统定位设置页
                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return; // 没开 GPS，直接返回
        }

        // 3. 权限和开关都OK，开始定位
        tvInfo.setText("等待 GPS 定位...");

        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location loc) {
                handleLocation(loc);
            }
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override
            public void onProviderEnabled(String provider) {}
            @Override
            public void onProviderDisabled(String provider) {}
        };

        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, listener);
        } catch (Exception e) {
            tvInfo.setText("无法获取 GPS：" + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startGps();
            } else {
                tvInfo.setText("❌ 权限被拒绝，无法使用实时定位功能。");
                Toast.makeText(this, "请去系统设置中允许定位权限", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void handleLocation(Location loc) {
        double lon = loc.getLongitude();
        double lat = loc.getLatitude();

        double[] xy = Projection.gps2Xy(
                lon, lat, app.data.projection,
                app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);

        if (xy == null || xy.length < 2) return;

        double x = xy[0];
        double y = xy[1];

        double[] kb = LL.hua_Fs(app.data.pqx, x, y);
        double k = kb[0];
        double b = kb[1];

        final String stationStr;
        final String offsetStr;
        if (k < 0) {
            stationStr = "超出线路范围";
            offsetStr = "";
        } else {
            stationStr = LL.hua_Num2K(k);
            offsetStr = String.format("偏距 %.3f m (%s)", Math.abs(b), b < 0 ? "左" : "右");
        }

        float acc = loc.hasAccuracy() ? loc.getAccuracy() : 0f;
        mapView.setCurrent(x, y, stationStr, offsetStr, acc);
        
        final String info = "X=" + String.format("%.3f", x)
                + " Y=" + String.format("%.3f", y)
                + " Z=" + String.format("%.3f", loc.getAltitude())
                + "\n精度: ±" + String.format("%.1f", acc) + " m";

        runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tvInfo.setText(info + "\n" + stationStr + "  " + offsetStr);
                }
            });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lm != null && listener != null) {
            try { lm.removeUpdates(listener); } catch (Exception e) {}
        }
    }
}