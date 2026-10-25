package com.hdm;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class EarthworkActivity extends BaseNavActivity {

    private MyApp app;
    private TextView tvInfo;
    private EditText etStation;
    private EarthworkView sectionView;
    private LinearLayout content;

    @Override
    protected String currentTab() { return "earth"; }

    @Override
    protected String pageTitle() { return "土方计算"; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (MyApp) getApplication();

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));
        setContent(content);
        
        initUI();
    }

    @Override
    protected void onProjectLoaded() {
        initUI();
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
        tvInfo.setText("桩号总数: " + app.data.Stations().length);
        content.addView(tvInfo);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);

        etStation = new EditText(this);
        etStation.setHint("桩号（米）");
        etStation.setText("103200");
        etStation.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        inputRow.addView(etStation);

        Button btnCalc = new Button(this);
        btnCalc.setText("计算");
        inputRow.addView(btnCalc);

        content.addView(inputRow);

        FrameLayout chartBox = new FrameLayout(this);
        chartBox.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        sectionView = new EarthworkView(this);
        sectionView.setBackgroundColor(0xFFFFFFFF);
        sectionView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        chartBox.addView(sectionView);

        TextView btnReset = new TextView(this);
        btnReset.setText("⌂");
        btnReset.setTextSize(32);
        btnReset.setTextColor(0xFF0078D4);
        btnReset.setBackgroundColor(0xCCFFFFFF);
        btnReset.setPadding(20, 5, 20, 10);
        btnReset.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lpReset = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        lpReset.topMargin = 10;
        lpReset.rightMargin = 10;
        btnReset.setLayoutParams(lpReset);
        chartBox.addView(btnReset);

        content.addView(chartBox);

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { sectionView.resetView(); }
        });

        btnCalc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String input = etStation.getText().toString().trim();
                double station;
                try {
                    station = Double.parseDouble(input);
                } catch (Exception e) {
                    Toast.makeText(EarthworkActivity.this, "桩号格式错误", Toast.LENGTH_SHORT).show();
                    return;
                }

                ComputeResult r;
                try {
                    r = app.data.Query(station);
                } catch (Exception e) {
                    Toast.makeText(EarthworkActivity.this, "计算异常: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }

                if (!r.success) {
                    Toast.makeText(EarthworkActivity.this, "失败: " + r.errorMessage, Toast.LENGTH_LONG).show();
                    sectionView.setData(null);
                    return;
                }

                sectionView.setData(r.result);
            }
        });
    }
}
