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

    @Override
    protected String currentTab() { return "layout"; }

    @Override
    protected String pageTitle() { return "边坡放样"; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (MyApp) getApplication();

        if (app.data == null) {
            Toast.makeText(this, "项目未加载", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);

        etX = new EditText(this);
        etX.setHint("X");
        etX.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        inputRow.addView(etX);

        etY = new EditText(this);
        etY.setHint("Y");
        etY.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        inputRow.addView(etY);

        etZ = new EditText(this);
        etZ.setHint("Z");
        etZ.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        inputRow.addView(etZ);

        Button btnCalc = new Button(this);
        btnCalc.setText("放样");
        inputRow.addView(btnCalc);

        content.addView(inputRow);

        tvResult = new TextView(this);
        tvResult.setTextSize(14);
        tvResult.setText("输入实测点，点\"放样\"");
        content.addView(tvResult);

        FrameLayout chartBox = new FrameLayout(this);
        chartBox.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        sectionView = new LayoutView(this);
        sectionView.setBackgroundColor(0xFFFFFFFF);
        sectionView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        chartBox.addView(sectionView);

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

        content.addView(chartBox);
        setContent(content);

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { sectionView.resetView(); }
        });

        btnCalc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    double x = Double.parseDouble(etX.getText().toString().trim());
                    double y = Double.parseDouble(etY.getText().toString().trim());
                    double z = Double.parseDouble(etZ.getText().toString().trim());
                    doLayout(x, y, z);
                } catch (Exception e) {
                    Toast.makeText(LayoutActivity.this, "输入格式错误",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void doLayout(double x, double y, double z) {
        double[] kb = LL.hua_Fs(app.data.pqx, x, y);
        double k = kb[0];
        double b = kb[1];

        if (k < 0) {
            tvResult.setText("桩号超出线路范围");
            return;
        }

        double centerY = LL.hua_H(app.data.sqx, k);
        if (centerY < 0) {
            tvResult.setText("桩号超出竖曲线范围");
            return;
        }

        LuJiCheDaoJieGuoBao dao = LuJiYaoSuYinQing.getcrosectonxy(
                k, centerY, app.data.leftWidths, app.data.rightWidths);

        if (dao.zuoCeCheDaoJueDui == null || dao.zuoCeCheDaoJueDui.length == 0
                || dao.youCeCheDaoJueDui == null || dao.youCeCheDaoJueDui.length == 0) {
            tvResult.setText("路基宽度数据缺失");
            return;
        }

        double[] lOuter = dao.zuoCeCheDaoJueDui[0];
        double[] rOuter = dao.youCeCheDaoJueDui[dao.youCeCheDaoJueDui.length - 1];
        double[][] leftSlabs = dao.zuoBanKuai;
        double[][] rightSlabs = dao.youBanKuai;

        BianPoDuanLuo bpCfg = BianPoYinQing.k2BianPo(app.data.slopeData, k);
        if (bpCfg == null) {
            tvResult.setText("边坡段落为空");
            return;
        }
        BianPoHouXuanBao candidates = BianPoYinQing.getAbsolute(
                bpCfg, lOuter[0], lOuter[1], rOuter[0], rOuter[1]);

        boolean isLeft = b < 0;
        double outerY = isLeft ? lOuter[1] : rOuter[1];
        boolean isFill = z <= outerY;

        double[][] slope;
        if (isLeft) {
            slope = isFill ? candidates.zuoTianJueDui : candidates.zuoWaJueDui;
        } else {
            slope = isFill ? candidates.youTianJueDui : candidates.youWaJueDui;
        }

        List<double[]> outline = new ArrayList<double[]>();

        if (isLeft) {
            if (slope != null) {
                for (int i = 0; i < slope.length; i++)
                    outline.add(new double[]{slope[i][0], slope[i][1]});
            }
            outline.add(new double[]{lOuter[0], lOuter[1]});
            double curX = lOuter[0], curY = lOuter[1];
            if (leftSlabs != null) {
                for (int i = 0; i < leftSlabs.length; i++) {
                    double w = leftSlabs[i][0];
                    double sl = leftSlabs[i][1];
                    double nextX = curX + w;
                    double nextY = curY + w * (-sl / 100.0);
                    outline.add(new double[]{nextX, nextY});
                    curX = nextX; curY = nextY;
                }
            }
        } else {
            outline.add(new double[]{0, centerY});
            double curX = 0, curY = centerY;
            if (rightSlabs != null) {
                for (int i = 0; i < rightSlabs.length; i++) {
                    double w = rightSlabs[i][0];
                    double sl = rightSlabs[i][1];
                    double nextX = curX + w;
                    double nextY = curY + w * (sl / 100.0);
                    outline.add(new double[]{nextX, nextY});
                    curX = nextX; curY = nextY;
                }
            }
            outline.add(new double[]{rOuter[0], rOuter[1]});
            if (slope != null) {
                for (int i = 0; i < slope.length; i++)
                    outline.add(new double[]{slope[i][0], slope[i][1]});
            }
        }

        double[][] outlineMat = new double[outline.size()][2];
        for (int i = 0; i < outline.size(); i++) {
            outlineMat[i][0] = outline.get(i)[0];
            outlineMat[i][1] = outline.get(i)[1];
        }

        double designY = LL.FromXgetY(outlineMat, b);
        double verticalMove = z - designY;

        double[] xs = LL.FromYgetX(outlineMat, z);
        double horizontalMove = 0;
        boolean hasHorizontal = false;
        if (xs.length > 0) {
            hasHorizontal = true;
            double targetX = b;
            double minDist = Double.MAX_VALUE;
            for (int i = 0; i < xs.length; i++) {
                double d = Math.abs(xs[i] - b);
                if (d < minDist) { minDist = d; targetX = xs[i]; }
            }
            horizontalMove = targetX - b;
        }

        String side = isLeft ? "左" : "右";
        String fillOrCut = isFill ? "填方" : "挖方";

        StringBuilder sb = new StringBuilder();
        sb.append("桩号: ").append(LL.hua_Num2K(k)).append("\n");
        sb.append("偏距: ").append(String.format("%.3f", b))
                .append(" m（").append(side).append("）\n");
        sb.append("判断: ").append(side).append(fillOrCut).append("\n");
        sb.append("实测高程: ").append(String.format("%.3f", z)).append("\n");
        sb.append("设计高程: ").append(String.format("%.3f", designY)).append("\n");
        sb.append("─────────────\n");

        if (verticalMove > 0) {
            sb.append("垂直移动: ↓ 挖 ").append(String.format("%.3f", verticalMove)).append(" m\n");
        } else if (verticalMove < 0) {
            sb.append("垂直移动: ↑ 填 ").append(String.format("%.3f", Math.abs(verticalMove))).append(" m\n");
        } else {
            sb.append("垂直移动: 已在设计面上\n");
        }
        if (hasHorizontal) {
            if (horizontalMove > 0) {
                sb.append("水平移动: → 往右 ").append(String.format("%.3f", horizontalMove)).append(" m\n");
            } else if (horizontalMove < 0) {
                sb.append("水平移动: ← 往左 ").append(String.format("%.3f", Math.abs(horizontalMove))).append(" m\n");
            } else {
                sb.append("水平移动: 0\n");
            }
        } else {
            sb.append("水平移动: 超出边坡范围\n");
        }

        tvResult.setText(sb.toString());

        sectionView.setLayoutData(centerY, lOuter, rOuter, leftSlabs, rightSlabs);
        sectionView.setLayoutSide(isLeft);
        sectionView.setLayoutSlope(slope);
        sectionView.setLayoutResult(designY, hasHorizontal ? horizontalMove : null);
        sectionView.setMarker(new double[]{b, z});
    }
}
