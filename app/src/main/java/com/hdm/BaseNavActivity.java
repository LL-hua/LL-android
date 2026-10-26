package com.hdm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseNavActivity extends Activity {

    // 核心：删掉"平面图"，只留 4 个
    protected static final String[] TAB_KEYS   = {"earth", "layout", "calc", "realmap"};
    protected static final String[] TAB_LABELS = {"土方", "放样", "正反算", "在线地图"};
    protected static final String[] TAB_ICONS  = {"土", "放", "算", "地"};

    private static final int COLOR_PRIMARY  = 0xFF1976D2;
    private static final int COLOR_NAV_BG   = 0xFFFFFFFF;
    private static final int COLOR_NAV_LINE = 0xFFE0E0E0;
    private static final int COLOR_TAB_ON   = 0xFF1976D2;
    private static final int COLOR_TAB_OFF  = 0xFF9E9E9E;

    protected FrameLayout contentBox;
    protected TextView tvProj;

    protected abstract String currentTab();
    protected abstract String pageTitle();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(COLOR_PRIMARY);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.getDecorView().setSystemUiVisibility(0);
            }
        }

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(0xFFF0F2F5);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(COLOR_PRIMARY);
        header.setPadding(dp(20), dp(14), dp(20), dp(14));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(pageTitle());
        tvTitle.setTextSize(18);
        tvTitle.setTextColor(Color.WHITE);
        header.addView(tvTitle);

        tvProj = new TextView(this);
        updateProjName();
        tvProj.setTextSize(13);
        tvProj.setTextColor(0xCCFFFFFF);
        tvProj.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        tvProj.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvProj.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showProjectDialog();
            }
        });
        header.addView(tvProj);

        rootLayout.addView(header);

        contentBox = new FrameLayout(this);
        contentBox.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        rootLayout.addView(contentBox);

        rootLayout.addView(buildBottomNav());

        setContentView(rootLayout);

        autoLoadLastProject();
    }

    protected void setContent(View view) {
        contentBox.removeAllViews();
        contentBox.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void autoLoadLastProject() {
        SharedPreferences sp = getSharedPreferences("hdm_config", MODE_PRIVATE);
        String lastPath = sp.getString("last_project", null);
        if (lastPath != null) {
            File file = new File(lastPath);
            if (file.exists()) {
                loadProject(file, true);
            }
        }
    }

    protected void showProjectDialog() {
        File sd = Environment.getExternalStorageDirectory();
        File dir = new File(sd, "1hdm");
        if (!dir.exists()) dir.mkdirs();

        File[] files = dir.listFiles();
        final List<File> xlsxFiles = new ArrayList<>();
        final List<String> xlsxNames = new ArrayList<>();

        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".xlsx")) {
                    xlsxFiles.add(f);
                    xlsxNames.add(f.getName());
                }
            }
        }

        if (xlsxFiles.isEmpty()) {
            Toast.makeText(this, "请把 .xlsx 放到 /sdcard/1hdm/", Toast.LENGTH_LONG).show();
            return;
        }

        final String[] items = xlsxNames.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("选择项目文件")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        loadProject(xlsxFiles.get(which), false);
                    }
                })
                .show();
    }

    private void loadProject(final File xlsxFile, final boolean isSilent) {
        if (!isSilent) tvProj.setText("加载中...");
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ProjectData d = XlsxProjectData.Load(xlsxFile.getAbsolutePath());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MyApp app = (MyApp) getApplication();
                            app.data = d;
                            app.currentProjectName = xlsxFile.getName();
                            app.currentProjectFile = xlsxFile;
                            
                            SharedPreferences sp = getSharedPreferences("hdm_config", MODE_PRIVATE);
                            sp.edit().putString("last_project", xlsxFile.getAbsolutePath()).apply();

                            updateProjName();
                            if (!isSilent) {
                                Toast.makeText(BaseNavActivity.this, "已加载: " + xlsxFile.getName(), Toast.LENGTH_SHORT).show();
                            }
                            onProjectLoaded();
                        }
                    });
                } catch (final Throwable e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateProjName();
                            if (!isSilent) {
                                Toast.makeText(BaseNavActivity.this, "加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    protected void updateProjName() {
        if (tvProj == null) return;
        MyApp app = (MyApp) getApplication();
        String projName = (app != null && app.currentProjectName != null)
                ? app.currentProjectName : "未选择项目";
        tvProj.setText(projName);
    }

    protected void onProjectLoaded() {}

    private View buildBottomNav() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        View line = new View(this);
        line.setBackgroundColor(COLOR_NAV_LINE);
        wrapper.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(COLOR_NAV_BG);
        nav.setPadding(0, dp(6), 0, dp(8));

        String cur = currentTab();

        for (int i = 0; i < 4; i++) {
            final String key = TAB_KEYS[i];
            boolean active = key.equals(cur);

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(4), 0, dp(4));
            item.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView icon = new TextView(this);
            icon.setText(TAB_ICONS[i]);
            icon.setTextSize(20);
            icon.setGravity(Gravity.CENTER);
            icon.setTextColor(active ? COLOR_TAB_ON : COLOR_TAB_OFF);
            item.addView(icon);

            TextView label = new TextView(this);
            label.setText(TAB_LABELS[i]);
            label.setTextSize(11);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(2), 0, 0);
            label.setTextColor(active ? COLOR_TAB_ON : COLOR_TAB_OFF);
            item.addView(label);

            if (active) {
                icon.setTypeface(null, android.graphics.Typeface.BOLD);
                label.setTypeface(null, android.graphics.Typeface.BOLD);
            }

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { goTab(key); }
            });

            nav.addView(item);
        }

        wrapper.addView(nav);
        return wrapper;
    }

    protected void goTab(String key) {
        if (key.equals(currentTab())) return;

        Class<?> target = null;
        if ("earth".equals(key))   target = EarthworkActivity.class;
        if ("layout".equals(key))  target = LayoutActivity.class;
        if ("calc".equals(key))    target = CalcActivity.class;
        if ("realmap".equals(key)) target = RealMapActivity.class;
        
        if (target == null) return;

        MyApp app = (MyApp) getApplication();
        
        // 除了 realmap，其余必须选项目
        if (!"realmap".equals(key) && (app == null || app.data == null)) {
            Toast.makeText(this, "请先点击右上角选择项目", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent it = new Intent(this, target);
        it.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(it);
        finish();
    }

    protected int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
