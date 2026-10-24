package com.hdm;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class EarthworkActivity extends Activity {

    private MyApp app;
    private TextView tvInfo;
    private EditText etStation;
    private EarthworkView sectionView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (MyApp) getApplication();

        if (app.data == null) {
            Toast.makeText(this, "项目未加载", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ---- 根布局 ----
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);

        // 信息
        tvInfo = new TextView(this);
        tvInfo.setText("项目: " + app.currentProjectName
                       + "，桩号总数: " + app.data.Stations().length);
        root.addView(tvInfo);

        // 输入行
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);

        etStation = new EditText(this);
        etStation.setHint("桩号（米）");
        etStation.setText("103200");
        LinearLayout.LayoutParams lpEt = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        etStation.setLayoutParams(lpEt);
        inputRow.addView(etStation);

        Button btnCalc = new Button(this);
        btnCalc.setText("计算");
        inputRow.addView(btnCalc);

        root.addView(inputRow);

        // ---- 断面图 ----
        FrameLayout chartBox = new FrameLayout(this);
        LinearLayout.LayoutParams lpBox = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        chartBox.setLayoutParams(lpBox);

        sectionView = new EarthworkView(this);
        sectionView.setBackgroundColor(0xFFFFFFFF);
        FrameLayout.LayoutParams lpView = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
        sectionView.setLayoutParams(lpView);
        chartBox.addView(sectionView);

        // 重置按钮
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

        // ---- 重置 ----
        btnReset.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sectionView.resetView();
                }
            });

        // ---- 计算 ----
        btnCalc.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String input = etStation.getText().toString().trim();
                    double station;
                    try {
                        station = Double.parseDouble(input);
                    } catch (Exception e) {
                        Toast.makeText(EarthworkActivity.this, "桩号格式错误",
                                       Toast.LENGTH_SHORT).show();
                        return;
                    }

                    ComputeResult r;
                    try {
                        r = app.data.Query(station);
                    } catch (Exception e) {
                        Toast.makeText(EarthworkActivity.this, "计算异常: " + e.getMessage(),
                                       Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (!r.success) {
                        Toast.makeText(EarthworkActivity.this, "失败: " + r.errorMessage,
                                       Toast.LENGTH_LONG).show();
                        sectionView.setData(null);
                        return;
                    }

                    sectionView.setData(r.result);
                }
            });
    }
}
