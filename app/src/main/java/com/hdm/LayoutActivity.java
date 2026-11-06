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

import java.util.ArrayList;
import java.util.List;

public class LayoutActivity extends BaseNavActivity {

    private MyApp app;
    private TextView tvResult;
    private EditText etX, etY, etZ;
    private LayoutView sectionView;
    private LinearLayout content;

    @Override
    protected String currentTab() { return "layout"; }

    @Override
    protected String pageTitle() { return "边坡放样"; }

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
    protected void onProjectLoaded() { initUI(); }

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

        // ================= 输入区 =================
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        etX = new EditText(this); etX.setHint("X");
        etX.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        inputRow.addView(etX);

        etY = new EditText(this); etY.setHint("Y");
        etY.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        inputRow.addView(etY);

        etZ = new EditText(this); etZ.setHint("Z");
        etZ.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        inputRow.addView(etZ);

        // 新增：RTK 按钮
        Button btnRtk = new Button(this);
        btnRtk.setText("RTK");
        btnRtk.setTextSize(12);
        btnRtk.setPadding(dp(8), 0, dp(8), 0);
        btnRtk.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        inputRow.addView(btnRtk);

        // 原有：计算按钮
        Button btnCalc = new Button(this); 
        btnCalc.setText("放样");
        btnCalc.setPadding(dp(8), 0, dp(8), 0);
        btnCalc.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        inputRow.addView(btnCalc);
        
        content.addView(inputRow);

        tvResult = new TextView(this); 
        tvResult.setTextSize(14);
        tvResult.setText("输入实测点，点\"放样\"\n也可连接 RTK 后直接点击 \"RTK\" 按钮获取");
        tvResult.setPadding(0, dp(10), 0, dp(10));
        content.addView(tvResult);

        // ================= 图形区 =================
        FrameLayout chartBox = new FrameLayout(this);
        chartBox.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        sectionView = new LayoutView(this);
        sectionView.setBackgroundColor(0xFFFFFFFF);
        sectionView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        chartBox.addView(sectionView);

        TextView btnReset = new TextView(this);
        btnReset.setText("⌂"); btnReset.setTextSize(32); btnReset.setTextColor(0xFF0078D4);
        btnReset.setBackgroundColor(0xCCFFFFFF); btnReset.setPadding(20, 5, 20, 10);
        btnReset.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lpReset = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
        lpReset.topMargin = 10; lpReset.rightMargin = 10;
        btnReset.setLayoutParams(lpReset); chartBox.addView(btnReset);
        content.addView(chartBox);

        // ================= 事件绑定 =================
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sectionView.resetView(); }
        });

        btnCalc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    double x = Double.parseDouble(etX.getText().toString().trim());
                    double y = Double.parseDouble(etY.getText().toString().trim());
                    double z = Double.parseDouble(etZ.getText().toString().trim());
                    doLayout(x, y, z);
                } catch (Exception e) {
                    Toast.makeText(LayoutActivity.this, "输入格式错误", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 新增：RTK 获取逻辑
        btnRtk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MyApp app = (MyApp) getApplication();
                if (!app.hasRtkData || app.currentRtkFix == 0) {
                    Toast.makeText(LayoutActivity.this, "未收到 RTK 信号，请先在地图页面连接 RTK", Toast.LENGTH_LONG).show();
                    return;
                }

                // 将 RTK 的经纬度转换成工程坐标
                double[] xy = Projection.gps2Xy(app.currentRtkLon, app.currentRtkLat, 
                        app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                
                if (xy == null || xy.length < 2) {
                    Toast.makeText(LayoutActivity.this, "坐标转换失败，请检查控制点配置", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 填入输入框
                etX.setText(String.format("%.3f", xy[0]));
                etY.setText(String.format("%.3f", xy[1]));
                etZ.setText(String.format("%.3f", app.currentRtkAlt)); // 暂用 RTK 椭球高，视工程需求可加高程异常改正

                // 自动触发计算
                doLayout(xy[0], xy[1], app.currentRtkAlt);
                
                String fixStr = app.currentRtkFix == 4 ? "固定解" : "浮点解";
                Toast.makeText(LayoutActivity.this, "已获取 RTK " + fixStr + " 坐标", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void doLayout(double x, double y, double z) {
        double[] kb = LL.hua_Fs(app.data.pqx, x, y);
        double k = kb[0], b = kb[1];

        if (k < 0) { tvResult.setText("桩号超出线路范围"); return; }
        double centerY = LL.hua_H(app.data.sqx, k);
        if (centerY < 0) { tvResult.setText("桩号超出竖曲线范围"); return; }

        LuJiCheDaoJieGuoBao dao = LuJiYaoSuYinQing.getcrosectonxy(k, centerY, app.data.leftWidths, app.data.rightWidths);
        if (dao.zuoCeCheDaoJueDui == null || dao.zuoCeCheDaoJueDui.length == 0
                || dao.youCeCheDaoJueDui == null || dao.youCeCheDaoJueDui.length == 0) {
            tvResult.setText("路基宽度数据缺失"); return;
        }

        double[] lOuter = dao.zuoCeCheDaoJueDui[0];
        double[] rOuter = dao.youCeCheDaoJueDui[dao.youCeCheDaoJueDui.length - 1];
        double[][] leftSlabs = dao.zuoBanKuai;
        double[][] rightSlabs = dao.youBanKuai;

        BianPoDuanLuo bpCfg = BianPoYinQing.k2BianPo(app.data.slopeData, k);
        if (bpCfg == null) { tvResult.setText("边坡段落为空"); return; }
        BianPoHouXuanBao candidates = BianPoYinQing.getAbsolute(bpCfg, lOuter[0], lOuter[1], rOuter[0], rOuter[1]);

        boolean isLeft = b < 0;
        double outerY = isLeft ? lOuter[1] : rOuter[1];
        boolean isFill = z <= outerY;

        double[][] slope = isLeft ? (isFill ? candidates.zuoTianJueDui : candidates.zuoWaJueDui)
                                   : (isFill ? candidates.youTianJueDui : candidates.youWaJueDui);

        List<double[]> outline = new ArrayList<double[]>();
        if (isLeft) {
            if (slope != null) for (double[] p : slope) outline.add(p);
            outline.add(new double[]{lOuter[0], lOuter[1]});
            double curX = lOuter[0], curY = lOuter[1];
            if (leftSlabs != null) {
                for (double[] s : leftSlabs) {
                    double w = s[0], sl = s[1];
                    double nextX = curX + w, nextY = curY + w * (-sl / 100.0);
                    outline.add(new double[]{nextX, nextY}); curX = nextX; curY = nextY;
                }
            }
        } else {
            outline.add(new double[]{0, centerY});
            double curX = 0, curY = centerY;
            if (rightSlabs != null) {
                for (double[] s : rightSlabs) {
                    double w = s[0], sl = s[1];
                    double nextX = curX + w, nextY = curY + w * (sl / 100.0);
                    outline.add(new double[]{nextX, nextY}); curX = nextX; curY = nextY;
                }
            }
            outline.add(new double[]{rOuter[0], rOuter[1]});
            if (slope != null) for (double[] p : slope) outline.add(p);
        }

        double[][] outlineMat = new double[outline.size()][2];
        for (int i = 0; i < outline.size(); i++) { outlineMat[i][0] = outline.get(i)[0]; outlineMat[i][1] = outline.get(i)[1]; }

        double designY = LL.FromXgetY(outlineMat, b);
        double verticalMove = z - designY;
        double[] xs = LL.FromYgetX(outlineMat, z);
        double horizontalMove = 0; boolean hasHorizontal = false;
        if (xs.length > 0) {
            hasHorizontal = true; double targetX = b, minDist = Double.MAX_VALUE;
            for (double xv : xs) { if (Math.abs(xv - b) < minDist) { minDist = Math.abs(xv - b); targetX = xv; } }
            horizontalMove = targetX - b;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("桩号: ").append(LL.hua_Num2K(k)).append("\n");
        sb.append("偏距: ").append(String.format("%.3f", b)).append(" m（").append(isLeft ? "左" : "右").append("）\n");
        sb.append("判断: ").append(isLeft ? "左" : "右").append(isFill ? "填方" : "挖方").append("\n");
        sb.append("实测高程: ").append(String.format("%.3f", z)).append("\n");
        sb.append("设计高程: ").append(String.format("%.3f", designY)).append("\n");
        sb.append("─────────────\n");

        if (verticalMove > 0) sb.append("垂直移动: ↓ 挖 ").append(String.format("%.3f", verticalMove)).append(" m\n");
        else if (verticalMove < 0) sb.append("垂直移动: ↑ 填 ").append(String.format("%.3f", Math.abs(verticalMove))).append(" m\n");
        else sb.append("垂直移动: 已在设计面上\n");

        if (hasHorizontal) {
            if (horizontalMove > 0) sb.append("水平移动: → 往右 ").append(String.format("%.3f", horizontalMove)).append(" m\n");
            else if (horizontalMove < 0) sb.append("水平移动: ← 往左 ").append(String.format("%.3f", Math.abs(horizontalMove))).append(" m\n");
            else sb.append("水平移动: 0\n");
        } else sb.append("水平移动: 超出边坡范围\n");

        tvResult.setText(sb.toString());
        sectionView.setLayoutData(centerY, lOuter, rOuter, leftSlabs, rightSlabs);
        sectionView.setLayoutSide(isLeft);
        sectionView.setLayoutSlope(slope);
        sectionView.setLayoutResult(designY, hasHorizontal ? horizontalMove : null);
        sectionView.setMarker(new double[]{b, z});
    }
}