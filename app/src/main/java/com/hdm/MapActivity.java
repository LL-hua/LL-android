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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MapActivity extends BaseNavActivity {

    private MyApp app;
    private TextView tvInfo;
    private MapView mapView;
    private LinearLayout content;

    private LocationManager lm;
    private LocationListener listener;
    private static final int REQ_LOCATION = 200;

    @Override
    protected String currentTab() { return "map"; }

    @Override
    protected String pageTitle() { return "平面线路"; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (MyApp) getApplication();

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));
        setContent(content);

        initUI();
        startGps(); // 每次进页面都尝试启动
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从设置页回来后，如果还没监听，重新开启
        if (lm != null && listener == null) {
            startGps();
        }
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

        tvInfo = new TextView(this);
        tvInfo.setText("正在加载线路...");
        content.addView(tvInfo);

        FrameLayout chartBox = new FrameLayout(this);
        chartBox.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        mapView = new MapView(this);
        mapView.setBackgroundColor(0xFFFFFFFF);
        mapView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        chartBox.addView(mapView);

        TextView btnReset = new TextView(this);
        btnReset.setText("⌂"); btnReset.setTextSize(32); btnReset.setTextColor(0xFF0078D4);
        btnReset.setBackgroundColor(0xCCFFFFFF); btnReset.setPadding(20, 5, 20, 10); btnReset.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lpReset = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
        lpReset.topMargin = 10; lpReset.rightMargin = 10;
        btnReset.setLayoutParams(lpReset); chartBox.addView(btnReset);

        content.addView(chartBox);

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { mapView.resetView(); }
        });

        loadRoute();
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                return;
            }
        }

        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return;

        // 如果手机 GPS 开关没开，弹窗提示
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            new AlertDialog.Builder(this)
                .setTitle("位置服务未开启")
                .setMessage("为了获取实时桩号和偏距，请打开系统的位置信息（GPS）开关。")
                .setPositiveButton("去开启", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
            return;
        }

        listener = new LocationListener() {
            @Override public void onLocationChanged(Location loc) { handleLocation(loc); }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, listener); } catch (Exception e) {}
    }

    private void handleLocation(Location loc) {
        if (app.data == null) return;
        double lon = loc.getLongitude();
        double lat = loc.getLatitude();

        double[] xy = Projection.gps2Xy(lon, lat, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
        if (xy == null || xy.length < 2) return;

        double[] kb = LL.hua_Fs(app.data.pqx, xy[0], xy[1]);
        final String stationStr = kb[0] < 0 ? "超出线路" : LL.hua_Num2K(kb[0]);
        final String offsetStr = kb[0] < 0 ? "" : String.format("偏距 %.3f m (%s)", Math.abs(kb[1]), kb[1] < 0 ? "左" : "右");
        float acc = loc.hasAccuracy() ? loc.getAccuracy() : 0f;

        if (mapView != null) mapView.setCurrent(xy[0], xy[1], stationStr, offsetStr, acc);

        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (tvInfo != null) tvInfo.setText(stationStr + "  " + offsetStr);
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
        if (lm != null && listener != null) { try { lm.removeUpdates(listener); } catch (Exception e) {} }
    }
}
