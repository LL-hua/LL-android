package com.hdm;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;

public class MainActivity extends BaseNavActivity {

    private static final String PROJECT_DIR = "1hdm";
    private static final int COLOR_CARD    = 0xFFFFFFFF;
    private static final int COLOR_SUBTEXT = 0xFF757575;

    private MyApp app;
    private TextView tvInfo;
    private Spinner spProject;

    @Override
    protected String currentTab() { return "proj"; }

    @Override
    protected String pageTitle() { return "项目"; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(0xFF1976D2);
        }
        super.onCreate(savedInstanceState);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } else if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, 100);
            }
        }

        app = (MyApp) getApplication();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout cardProj = new LinearLayout(this);
        cardProj.setOrientation(LinearLayout.VERTICAL);
        cardProj.setBackground(makeRoundRect(COLOR_CARD, dp(12)));
        cardProj.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView tvProjLabel = new TextView(this);
        tvProjLabel.setText("项目文件");
        tvProjLabel.setTextSize(13);
        tvProjLabel.setTextColor(COLOR_SUBTEXT);
        cardProj.addView(tvProjLabel);

        spProject = new Spinner(this);
        LinearLayout.LayoutParams lpSp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
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
        setContent(content);

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

    private GradientDrawable makeRoundRect(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
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
