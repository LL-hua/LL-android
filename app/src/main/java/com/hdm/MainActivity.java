package com.hdm;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity {

    private static final String PROJECT_DIR = "1hdm";

    private static final int COLOR_PRIMARY    = 0xFF1976D2;
    private static final int COLOR_PRIMARY_LT = 0xFFE3F2FD;
    private static final int COLOR_BG         = 0xFFF0F2F5;
    private static final int COLOR_CARD       = 0xFFFFFFFF;
    private static final int COLOR_TEXT       = 0xFF212121;
    private static final int COLOR_SUBTEXT    = 0xFF757575;
    private static final int COLOR_ARROW      = 0xFFBDBDBD;

    private MyApp app;
    private TextView tvInfo;
    private Spinner spProject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(COLOR_PRIMARY);
        }
        super.onCreate(savedInstanceState);
        
        // ===== 申请全盘文件访问权限 (Android 11+) =====
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } else if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, 100);
            }
        }
        // ===== 权限申请结束 =====

        app = (MyApp) getApplication();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(COLOR_PRIMARY);
        header.setPadding(dp(24), dp(50), dp(24), dp(24));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("hdm 断面计算");
        tvTitle.setTextSize(24);
        tvTitle.setTextColor(Color.WHITE);
        header.addView(tvTitle);

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText("横断面 · 放样 · 线路图");
        tvSubtitle.setTextSize(13);
        tvSubtitle.setTextColor(0xCCFFFFFF);
        tvSubtitle.setPadding(0, dp(4), 0, 0);
        header.addView(tvSubtitle);
        root.addView(header);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.addView(content);

        LinearLayout cardProj = new LinearLayout(this);
        cardProj.setOrientation(LinearLayout.VERTICAL);
        cardProj.setBackground(makeRoundRect(COLOR_CARD, dp(12)));
        cardProj.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lpProj = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpProj.bottomMargin = dp(12);
        cardProj.setLayoutParams(lpProj);

        TextView tvProjLabel = new TextView(this);
        tvProjLabel.setText("项目文件");
        tvProjLabel.setTextSize(13);
        tvProjLabel.setTextColor(COLOR_SUBTEXT);
        cardProj.addView(tvProjLabel);

        spProject = new Spinner(this);
        LinearLayout.LayoutParams lpSp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpSp.topMargin = dp(6);
        spProject.setLayoutParams(lpSp);
        cardProj.addView(spProject);

        tvInfo = new TextView(this);
        tvInfo.setText("正在扫描项目...");
        tvInfo.setTextSize(12);
        tvInfo.setTextColor(COLOR_SUBTEXT);
        tvInfo.setPadding(0, dp(8), 0, 0);
        cardProj.addView(tvInfo);

        content.addView(cardProj);

        content.addView(makeCardButton("土方计算", "计算断面土方量", "土", new Runnable() {
                                @Override public void run() { openIfReady(EarthworkActivity.class); }
                            }));

        content.addView(makeCardButton("边坡放样", "放样边坡坡脚 / 坡顶", "放", new Runnable() {
                                @Override public void run() { openIfReady(LayoutActivity.class); }
                            }));

        content.addView(makeCardButton("正反算", "坐标与桩号互算", "算", new Runnable() {
                                @Override public void run() { openIfReady(CalcActivity.class); }
                            }));

        content.addView(makeCardButton("线路图", "查看线路中线与定位", "图", new Runnable() {
                                @Override public void run() { openIfReady(MapActivity.class); }
                            }));

        setContentView(root);
        scanProjects();

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, app.projectNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProject.setAdapter(adapter);

        spProject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    if (pos < app.projectFiles.size()) {
                        loadProject(app.projectFiles.get(pos));
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
    }

    private void openIfReady(Class<?> cls) {
        if (app.data == null) {
            Toast.makeText(this, "请先选择项目", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, cls));
    }

    private GradientDrawable makeRoundRect(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private StateListDrawable makePressableBg(int normalColor, int pressColor, float radius) {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{ android.R.attr.state_pressed }, makeRoundRect(pressColor, radius));
        sld.addState(new int[]{}, makeRoundRect(normalColor, radius));
        return sld;
    }

    private View makeCardButton(String title, String subtitle, String badge, final Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(makePressableBg(COLOR_CARD, COLOR_PRIMARY_LT, dp(12)));

        LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpRow.bottomMargin = dp(10);
        row.setLayoutParams(lpRow);

        TextView badgeView = new TextView(this);
        badgeView.setText(badge);
        badgeView.setTextSize(18);
        badgeView.setTextColor(Color.WHITE);
        badgeView.setGravity(Gravity.CENTER);
        badgeView.setBackground(makeRoundRect(COLOR_PRIMARY, dp(10)));
        LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(dp(44), dp(44));
        badgeView.setLayoutParams(lpBadge);
        row.addView(badgeView);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpMid = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lpMid.leftMargin = dp(14);
        mid.setLayoutParams(lpMid);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(17);
        tvTitle.setTextColor(COLOR_TEXT);
        mid.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextSize(12);
        tvSub.setTextColor(COLOR_SUBTEXT);
        tvSub.setPadding(0, dp(2), 0, 0);
        mid.addView(tvSub);
        row.addView(mid);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(28);
        arrow.setTextColor(COLOR_ARROW);
        row.addView(arrow);

        row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { onClick.run(); }
            });
        return row;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void scanProjects() {
        app.projectFiles.clear();
        app.projectNames.clear();

        File sd = Environment.getExternalStorageDirectory();
        File dir = new File(sd, PROJECT_DIR);

        if (!dir.exists()) dir.mkdirs();

        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".xlsx")) {
                    app.projectFiles.add(f);
                    app.projectNames.add(f.getName());
                }
            }
        }

        if (app.projectFiles.isEmpty()) {
            app.projectNames.add("（无 .xlsx）");
            tvInfo.setText("请把 .xlsx 放到 /sdcard/1hdm/");
        } else {
            tvInfo.setText("找到 " + app.projectFiles.size() + " 个项目");
        }
    }

    private void loadProject(final File xlsxFile) {
        if (xlsxFile == null || !xlsxFile.exists()) return;

        tvInfo.setText("正在加载 " + xlsxFile.getName() + " ...");
        app.data = null;
        app.currentProjectName = null;
        app.currentProjectFile = null;

        new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final ProjectData d = XlsxProjectData.Load(xlsxFile.getAbsolutePath());
                        runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    app.data = d;
                                    app.currentProjectName = xlsxFile.getName();
                                    app.currentProjectFile = xlsxFile;
                                    tvInfo.setText("已加载 " + xlsxFile.getName()
                                                   + "，桩号总数: " + d.Stations().length);
                                }
                            });
                    } catch (final Throwable e) {
                        runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tvInfo.setText("加载失败: " + e.getMessage());
                                }
                            });
                    }
                }
            }).start();
    }
}
