package com.hdm;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

public class CalcActivity extends Activity {

    private MyApp app;

    private RadioGroup rgMode;
    private RadioButton rbForward;
    private RadioButton rbReverse;

    private LinearLayout boxForward;
    private EditText etK, etB;

    private LinearLayout boxReverse;
    private EditText etX, etY;

    // 结果区（多条可点）
    private LinearLayout resultBox;

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

        // 信息
        TextView tvInfo = new TextView(this);
        tvInfo.setText("项目: " + app.currentProjectName);
        root.addView(tvInfo);

        // 模式选择
        rgMode = new RadioGroup(this);
        rgMode.setOrientation(RadioGroup.HORIZONTAL);

        rbForward = new RadioButton(this);
        rbForward.setText("正算（K+B → XY）");
        rbForward.setId(1);
        rbForward.setChecked(true);
        rgMode.addView(rbForward);

        rbReverse = new RadioButton(this);
        rbReverse.setText("反算（XY → K+B）");
        rbReverse.setId(2);
        rgMode.addView(rbReverse);

        root.addView(rgMode);

        // ---- 正算输入 ----
        boxForward = new LinearLayout(this);
        boxForward.setOrientation(LinearLayout.VERTICAL);

        LinearLayout rowK = new LinearLayout(this);
        rowK.setOrientation(LinearLayout.HORIZONTAL);
        TextView tvK = new TextView(this);
        tvK.setText("桩号 K: ");
        tvK.setWidth(150);
        rowK.addView(tvK);
        etK = new EditText(this);
        etK.setHint("米");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        etK.setLayoutParams(lp);
        rowK.addView(etK);
        boxForward.addView(rowK);

        LinearLayout rowB = new LinearLayout(this);
        rowB.setOrientation(LinearLayout.HORIZONTAL);
        TextView tvB = new TextView(this);
        tvB.setText("偏距 B: ");
        tvB.setWidth(150);
        rowB.addView(tvB);
        etB = new EditText(this);
        etB.setHint("米（左负右正）");
        etB.setLayoutParams(lp);
        rowB.addView(etB);
        boxForward.addView(rowB);

        root.addView(boxForward);

        // ---- 反算输入 ----
        boxReverse = new LinearLayout(this);
        boxReverse.setOrientation(LinearLayout.VERTICAL);
        boxReverse.setVisibility(View.GONE);

        LinearLayout rowX = new LinearLayout(this);
        rowX.setOrientation(LinearLayout.HORIZONTAL);
        TextView tvX = new TextView(this);
        tvX.setText("X: ");
        tvX.setWidth(150);
        rowX.addView(tvX);
        etX = new EditText(this);
        etX.setHint("X 坐标");
        etX.setLayoutParams(lp);
        rowX.addView(etX);
        boxReverse.addView(rowX);

        LinearLayout rowY = new LinearLayout(this);
        rowY.setOrientation(LinearLayout.HORIZONTAL);
        TextView tvY = new TextView(this);
        tvY.setText("Y: ");
        tvY.setWidth(150);
        rowY.addView(tvY);
        etY = new EditText(this);
        etY.setHint("Y 坐标");
        etY.setLayoutParams(lp);
        rowY.addView(etY);
        boxReverse.addView(rowY);

        root.addView(boxReverse);

        // ---- 计算按钮 ----
        Button btnCalc = new Button(this);
        btnCalc.setText("计算");
        root.addView(btnCalc);

        // ---- 结果区（可点复制） ----
        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        resultBox.setPadding(10, 10, 10, 10);
        root.addView(resultBox);

        setContentView(root);

        // ---- 模式切换 ----
        rgMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    if (checkedId == 1) {
                        boxForward.setVisibility(View.VISIBLE);
                        boxReverse.setVisibility(View.GONE);
                    } else {
                        boxForward.setVisibility(View.GONE);
                        boxReverse.setVisibility(View.VISIBLE);
                    }
                    resultBox.removeAllViews();
                }
            });

        // ---- 计算 ----
        btnCalc.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (rbForward.isChecked()) {
                        doForward();
                    } else {
                        doReverse();
                    }
                }
            });
    }

    // ============================================================
    // 正算：K + B → X, Y
    // ============================================================
    private void doForward() {
        double k, b;
        try {
            k = Double.parseDouble(etK.getText().toString().trim());
            b = Double.parseDouble(etB.getText().toString().trim());
        } catch (Exception e) {
            Toast.makeText(this, "输入格式错误", Toast.LENGTH_SHORT).show();
            return;
        }

        double startK = app.data.pqx[0][0];
        int lastIdx = app.data.pqx.length - 1;
        double endK = app.data.pqx[lastIdx][0] + app.data.pqx[lastIdx][4];

        if (k < startK || k > endK) {
            clearResult();
            addResultLine("桩号超出线路范围", null, 0xFFDD0000);
            addResultLine("范围: " + String.format("%.2f", startK)
                          + " ~ " + String.format("%.2f", endK),
                          null, 0xFFDD0000);
            return;
        }

        double[] xy = LL.hua_Dantiaoxianludange(app.data.pqx, k, b, 90);
        double x = xy[0];
        double y = xy[1];
        double h = LL.hua_H(app.data.sqx, k);

        clearResult();

        addResultLine("桩号: " + LL.hua_Num2K(k), null, 0xFF333333);
        addResultLine("偏距: " + String.format("%.3f", b) + " m", null, 0xFF333333);

        // 可复制的行（标题 + 值）
        addResultLine("X = " + String.format("%.3f", x),
                      String.format("%.3f", x), 0xFF0078D4);
        addResultLine("Y = " + String.format("%.3f", y),
                      String.format("%.3f", y), 0xFF0078D4);
        addResultLine("中桩高程 H = " + String.format("%.3f", h),
                      String.format("%.3f", h), 0xFF0078D4);

        addResultLine("（点蓝色行可复制）", null, 0xFF999999);
    }

    // ============================================================
    // 反算：X, Y → K + B
    // ============================================================
    private void doReverse() {
        double x, y;
        try {
            x = Double.parseDouble(etX.getText().toString().trim());
            y = Double.parseDouble(etY.getText().toString().trim());
        } catch (Exception e) {
            Toast.makeText(this, "输入格式错误", Toast.LENGTH_SHORT).show();
            return;
        }

        double[] kb = LL.hua_Fs(app.data.pqx, x, y);
        double k = kb[0];
        double b = kb[1];

        clearResult();

        if (k == -1) {
            addResultLine("坐标超出线路起点范围", null, 0xFFDD0000);
            return;
        }
        if (k == -2) {
            addResultLine("坐标超出线路终点范围", null, 0xFFDD0000);
            return;
        }

        double h = LL.hua_H(app.data.sqx, k);

        // 桩号数值（可复制）
        String kStr = String.format("%.3f", k);
        addResultLine("桩号 = " + LL.hua_Num2K(k),
                      kStr, 0xFF0078D4);
        addResultLine("偏距 = " + String.format("%.3f", b) + " m（"
                      + (b < 0 ? "左" : "右") + "）",
                      String.format("%.3f", b), 0xFF0078D4);
        addResultLine("中桩高程 H = " + String.format("%.3f", h),
                      String.format("%.3f", h), 0xFF0078D4);

        addResultLine("（点蓝色行可复制）", null, 0xFF999999);
    }

    // ============================================================
    // 结果区：加一行
    // ============================================================
    private void addResultLine(String display, final String copyValue, int color) {
        TextView tv = new TextView(this);
        tv.setText(display);
        tv.setTextSize(16);
        tv.setTextColor(color);
        tv.setPadding(15, 12, 15, 12);
        resultBox.addView(tv);

        // 可复制
        if (copyValue != null) {
            tv.setBackgroundColor(0xFFF0F8FF);   // 浅蓝底
            tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        copyToClipboard(copyValue);
                        Toast.makeText(CalcActivity.this,
                                       "已复制: " + copyValue,
                                       Toast.LENGTH_SHORT).show();
                    }
                });
        }
    }

    private void clearResult() {
        resultBox.removeAllViews();
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("hdm", text));
    }
}
