package com.hdm;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public abstract class BaseNavActivity extends Activity {

    protected static final String[] TAB_KEYS   = {"proj", "earth", "layout", "calc", "map"};
    protected static final String[] TAB_LABELS = {"项目", "土方", "放样", "正反算", "线路图"};
    protected static final String[] TAB_ICONS  = {"▤", "土", "放", "算", "图"};

    private static final int COLOR_PRIMARY  = 0xFF1976D2;
    private static final int COLOR_NAV_BG   = 0xFFFFFFFF;
    private static final int COLOR_NAV_LINE = 0xFFE0E0E0;
    private static final int COLOR_TAB_ON   = 0xFF1976D2;
    private static final int COLOR_TAB_OFF  = 0xFF9E9E9E;

    private FrameLayout contentBox;

    protected abstract String currentTab();
    protected abstract String pageTitle();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ===== 沉浸式状态栏设置 =====
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(COLOR_PRIMARY); // 状态栏背景设为蓝色
            
            // 如果 Android 6.0 以上，强制状态栏字体为白色
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decor = window.getDecorView();
                decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR); 
                // 注意：SYSTEM_UI_FLAG_LIGHT_STATUS_BAR 是让字体变黑的。
                // 如果要纯白字体，传入 0 即可。我们这里是蓝底，所以要清除这个 flag，保证白字。
                decor.setSystemUiVisibility(0);
            }
        }
        // ===========================

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

        TextView tvProj = new TextView(this);
        MyApp app = (MyApp) getApplication();
        String projName = (app != null && app.currentProjectName != null)
                ? app.currentProjectName : "";
        tvProj.setText(projName);
        tvProj.setTextSize(12);
        tvProj.setTextColor(0xCCFFFFFF);
        tvProj.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        tvProj.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(tvProj);

        rootLayout.addView(header);

        contentBox = new FrameLayout(this);
        contentBox.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        rootLayout.addView(contentBox);

        rootLayout.addView(buildBottomNav());

        setContentView(rootLayout);
    }

    protected void setContent(View view) {
        contentBox.removeAllViews();
        contentBox.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

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

        for (int i = 0; i < 5; i++) {
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
        if ("proj".equals(key))   target = MainActivity.class;
        if ("earth".equals(key))  target = EarthworkActivity.class;
        if ("layout".equals(key)) target = LayoutActivity.class;
        if ("calc".equals(key))   target = CalcActivity.class;
        if ("map".equals(key))    target = MapActivity.class;
        if (target == null) return;

        MyApp app = (MyApp) getApplication();
        if (!"proj".equals(key) && (app == null || app.data == null)) {
            Toast.makeText(this, "请先在\"项目\"里选择项目", Toast.LENGTH_SHORT).show();
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
