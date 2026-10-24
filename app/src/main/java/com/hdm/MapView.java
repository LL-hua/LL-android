package com.hdm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 线路图（线路中线 + 桩号刻度 + 实时 GPS 位置 + 精度圈）。
 */
public class MapView extends BaseSectionView {

    // 线路中线（按步长取的点）
    private List<double[]> routePoints = new ArrayList<double[]>();

    // 桩号刻度（每公里）
    private List<double[]> kmMarks = new ArrayList<double[]>();

    // 当前位置（当地 XY）
    private double[] currentPos = null;      // [X, Y]
    private String currentStation = null;    // "K103+200.00"
    private String currentOffset = null;     // "左 2.35 m"
    private float currentAccuracy = 0f;      // GPS 精度（米）

    public MapView(Context context) {
        super(context);
    }

    /** 设置线路中线（沿线取点，含桩号） */
    public void setRoute(List<double[]> points, double kmInterval) {
        this.routePoints = points;
        buildKmMarks(kmInterval);
        tryFitOnce();
        invalidate();
    }

    /** 更新当前位置（accuracy 为 GPS 精度，单位米；传 0 表示不画精度圈） */
    public void setCurrent(double x, double y, String station, String offset, float accuracy) {
        this.currentPos = new double[]{ x, y };
        this.currentStation = station;
        this.currentOffset = offset;
        this.currentAccuracy = accuracy;
        invalidate();
    }

    /** 生成"每公里"刻度点 */
    private void buildKmMarks(double kmInterval) {
        kmMarks.clear();
        if (routePoints == null || routePoints.size() == 0) return;

        double startK = routePoints.get(0)[0];
        double endK = routePoints.get(routePoints.size() - 1)[0];

        double nextKm = Math.ceil(startK / kmInterval) * kmInterval;

        int idx = 0;
        while (nextKm <= endK && idx < routePoints.size()) {
            // 找到最接近 nextKm 的点
            while (idx < routePoints.size() - 1 && routePoints.get(idx)[0] < nextKm) {
                idx++;
            }
            if (idx < routePoints.size()) {
                kmMarks.add(routePoints.get(idx));
            }
            nextKm += kmInterval;
        }
    }

    @Override
    protected boolean hasData() {
        return routePoints != null && routePoints.size() >= 2;
    }

    @Override
    protected List<double[][]> getLinesToFit() {
        List<double[][]> lines = new ArrayList<double[][]>();
        if (routePoints == null || routePoints.size() == 0) return lines;

        double[][] routeMat = new double[routePoints.size()][2];
        for (int i = 0; i < routePoints.size(); i++) {
            routeMat[i][0] = routePoints.get(i)[2];   // X ← 测量 Y
            routeMat[i][1] = routePoints.get(i)[1];   // Y ← 测量 X
        }
        lines.add(routeMat);

        if (currentPos != null) {
            lines.add(new double[][]{ { currentPos[1], currentPos[0] } });   // X←Y, Y←X
        }
        return lines;
    }

    @Override
    protected double getPadYFactor() {
        return 0.10;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.WHITE);

        if (!hasData()) return;

        // ---- 线路中线 ----
        Paint pRoute = new Paint();
        pRoute.setColor(0xFFDD0000);
        pRoute.setStrokeWidth(4f);
        pRoute.setStyle(Paint.Style.STROKE);
        pRoute.setAntiAlias(true);

        Path path = new Path();
        for (int i = 0; i < routePoints.size(); i++) {
            double[] pt = routePoints.get(i);
            float px = sx(pt[2]);   // 屏幕 x ← 测量 Y
            float py = sy(pt[1]);   // 屏幕 y ← 测量 X
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        canvas.drawPath(path, pRoute);

        // ---- 桩号刻度 ----
        Paint pMark = new Paint();
        pMark.setColor(0xFF333333);
        pMark.setStrokeWidth(2f);
        pMark.setStyle(Paint.Style.STROKE);
        pMark.setAntiAlias(true);

        pText.setTextSize(24f);
        pText.setColor(0xFF333333);
        pText.setTextAlign(Paint.Align.LEFT);

        for (int i = 0; i < kmMarks.size(); i++) {
            double[] pt = kmMarks.get(i);
            float px = sx(pt[2]);   // 屏幕 x ← 测量 Y
            float py = sy(pt[1]);   // 屏幕 y ← 测量 X

            // 小圆点
            Paint pDot = new Paint();
            pDot.setColor(0xFF333333);
            pDot.setStyle(Paint.Style.FILL);
            pDot.setAntiAlias(true);
            canvas.drawCircle(px, py, 6, pDot);

            // 桩号文字
            canvas.drawText(LL.hua_Num2K(pt[0]), px + 10, py - 10, pText);
        }

        // ---- 当前位置 ----
        if (currentPos != null) {
            float px = sx(currentPos[1]);   // 屏幕 x ← 测量 Y
            float py = sy(currentPos[0]);   // 屏幕 y ← 测量 X

            // 精度圈（半径 = accuracy 米 × scale 像素/米）
            if (currentAccuracy > 0f) {
                float rPix = (float) (currentAccuracy * gesture.getScale());

                // 半透明填充
                Paint pAccFill = new Paint();
                pAccFill.setColor(0x220078D4);   // 浅蓝，alpha=0x22
                pAccFill.setStyle(Paint.Style.FILL);
                pAccFill.setAntiAlias(true);
                canvas.drawCircle(px, py, rPix, pAccFill);

                // 边框
                Paint pAccStroke = new Paint();
                pAccStroke.setColor(0x660078D4); // 半透明蓝
                pAccStroke.setStyle(Paint.Style.STROKE);
                pAccStroke.setStrokeWidth(2f);
                pAccStroke.setAntiAlias(true);
                canvas.drawCircle(px, py, rPix, pAccStroke);
            }

            // 外圈
            Paint pOuter = new Paint();
            pOuter.setColor(0xFF0078D4);
            pOuter.setStyle(Paint.Style.FILL);
            pOuter.setAntiAlias(true);
            canvas.drawCircle(px, py, 18, pOuter);

            // 白圈
            Paint pWhite = new Paint();
            pWhite.setColor(0xFFFFFFFF);
            pWhite.setStyle(Paint.Style.STROKE);
            pWhite.setStrokeWidth(4f);
            pWhite.setAntiAlias(true);
            canvas.drawCircle(px, py, 18, pWhite);

            // 内圈（橙点）
            Paint pInner = new Paint();
            pInner.setColor(0xFFFF6600);
            pInner.setStyle(Paint.Style.FILL);
            pInner.setAntiAlias(true);
            canvas.drawCircle(px, py, 10, pInner);

            // 文字
            if (currentStation != null) {
                pText.setTextSize(26f);
                pText.setColor(0xFF0078D4);
                pText.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(currentStation, px + 25, py - 15, pText);
            }
            if (currentOffset != null) {
                pText.setTextSize(22f);
                pText.setColor(0xFF666666);
                pText.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(currentOffset, px + 25, py + 15, pText);
            }
        }
    }
}
