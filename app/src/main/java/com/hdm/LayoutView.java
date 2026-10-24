package com.hdm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.ArrayList;
import java.util.List;

/**
 * 放样断面视图。
 */
public class LayoutView extends BaseSectionView {

    private double centerY = 0;
    private double[] lOuter = null;
    private double[] rOuter = null;
    private double[][] leftSlabs = null;
    private double[][] rightSlabs = null;
    private double[][] slope = null;       // 选中的单侧边坡（不含 lOuter/rOuter）
    private Boolean isLeft = null;
    private double[] markerPoint = null;
    private Double designY = null;
    private Double horizontalMove = null;

    public LayoutView(Context context) {
        super(context);
    }

    public void setLayoutData(double centerY,
                              double[] lOuter, double[] rOuter,
                              double[][] leftSlabs, double[][] rightSlabs) {
        this.centerY = centerY;
        this.lOuter = lOuter;
        this.rOuter = rOuter;
        this.leftSlabs = leftSlabs;
        this.rightSlabs = rightSlabs;
        tryFitOnce();
        invalidate();
    }

    public void setLayoutSide(boolean isLeft) {
        this.isLeft = isLeft;
        invalidate();
    }

    public void setLayoutSlope(double[][] slope) {
        this.slope = slope;
        invalidate();
    }

    public void setLayoutResult(Double designY, Double horizontalMove) {
        this.designY = designY;
        this.horizontalMove = horizontalMove;
        invalidate();
    }

    public void setMarker(double[] pt) {
        this.markerPoint = pt;
        invalidate();
    }

    @Override
    protected boolean hasData() {
        return lOuter != null && rOuter != null;
    }

    @Override
    protected double getPadYFactor() {
        return 0.08;
    }

    @Override
    protected List<double[][]> getLinesToFit() {
        List<double[][]> lines = new ArrayList<double[][]>();
        if (lOuter == null || rOuter == null) return lines;
        lines.add(new double[][]{ lOuter, new double[]{ 0, centerY }, rOuter });
        if (slope != null) lines.add(slope);
        if (markerPoint != null) lines.add(new double[][]{ markerPoint });
        return lines;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.WHITE);

        if (!hasData()) return;

        // ---- 顶面 ----
        List<double[]> road = new ArrayList<double[]>();
        road.add(new double[]{ lOuter[0], lOuter[1] });

        double curX = lOuter[0], curY = lOuter[1];
        if (leftSlabs != null) {
            for (int i = 0; i < leftSlabs.length; i++) {
                double w = leftSlabs[i][0];
                double sl = leftSlabs[i][1];
                double nextX = curX + w;
                double nextY = curY + w * (-sl / 100.0);
                road.add(new double[]{ nextX, nextY });
                curX = nextX; curY = nextY;
            }
        }
        road.add(new double[]{ 0, centerY });
        curX = 0; curY = centerY;
        if (rightSlabs != null) {
            for (int i = 0; i < rightSlabs.length; i++) {
                double w = rightSlabs[i][0];
                double sl = rightSlabs[i][1];
                double nextX = curX + w;
                double nextY = curY + w * (sl / 100.0);
                road.add(new double[]{ nextX, nextY });
                curX = nextX; curY = nextY;
            }
        }
        road.add(new double[]{ rOuter[0], rOuter[1] });

        if (road.size() >= 2) {
            Path p = new Path();
            p.moveTo(sx(road.get(0)[0]), sy(road.get(0)[1]));
            for (int i = 1; i < road.size(); i++) {
                p.lineTo(sx(road.get(i)[0]), sy(road.get(i)[1]));
            }
            canvas.drawPath(p, pRed);
        }

        // ---- 边坡 ----
        if (isLeft != null && slope != null) {
            // 拼"完整边坡折线"（含 lOuter/rOuter）
            List<double[]> slopeLine = new ArrayList<double[]>();
            if (isLeft) {
                for (int i = 0; i < slope.length; i++) {
                    slopeLine.add(new double[]{ slope[i][0], slope[i][1] });
                }
                slopeLine.add(new double[]{ lOuter[0], lOuter[1] });
            } else {
                slopeLine.add(new double[]{ rOuter[0], rOuter[1] });
                for (int i = 0; i < slope.length; i++) {
                    slopeLine.add(new double[]{ slope[i][0], slope[i][1] });
                }
            }

            if (slopeLine.size() >= 2) {
                Path p = new Path();
                p.moveTo(sx(slopeLine.get(0)[0]), sy(slopeLine.get(0)[1]));
                for (int i = 1; i < slopeLine.size(); i++) {
                    p.lineTo(sx(slopeLine.get(i)[0]), sy(slopeLine.get(i)[1]));
                }
                canvas.drawPath(p, pRed);
            }

            // 坡率（"1:X"）—— 用"完整折线"，每段都标
            double[][] slopeLineMat = new double[slopeLine.size()][2];
            for (int i = 0; i < slopeLine.size(); i++) {
                slopeLineMat[i][0] = slopeLine.get(i)[0];
                slopeLineMat[i][1] = slopeLine.get(i)[1];
            }
            drawSlopeLabels(canvas, slopeLineMat, 0xFFDD0000);

            // 每个点 w/h（只标"坡点"，lOuter/rOuter 由顶面标注负责）
            drawSlopePointLabels(canvas, slope, 0xFF0078D4);
        }

        // ---- 中桩 ----
        float ccx = sx(0);
        float ccy = sy(centerY);
        Paint pCenter = new Paint();
        pCenter.setColor(0xFF333333);
        pCenter.setStrokeWidth(2f);
        pCenter.setStyle(Paint.Style.STROKE);
        canvas.drawLine(ccx, ccy - 30, ccx, ccy, pCenter);
        canvas.drawCircle(ccx, ccy, 5, pCenter);

        // ---- 实测点 + 虚线 ----
        if (markerPoint != null) {
            float mx = sx(markerPoint[0]);
            float my = sy(markerPoint[1]);

            if (designY != null) {
                float dy = sy(designY);

                Paint pDashV = new Paint();
                pDashV.setStyle(Paint.Style.STROKE);
                pDashV.setStrokeWidth(3f);
                pDashV.setAntiAlias(true);
                pDashV.setPathEffect(new android.graphics.DashPathEffect(new float[]{12, 8}, 0));

                boolean isCut = markerPoint[1] > designY;
                pDashV.setColor(isCut ? 0xFFDD0000 : 0xFF00AA00);

                canvas.drawLine(mx, my, mx, dy, pDashV);

                Paint pDesignPt = new Paint();
                pDesignPt.setColor(pDashV.getColor());
                pDesignPt.setStyle(Paint.Style.FILL);
                pDesignPt.setAntiAlias(true);
                canvas.drawCircle(mx, dy, 8, pDesignPt);

                String vTxt = isCut
                        ? "挖 " + String.format("%.3f", Math.abs(markerPoint[1] - designY))
                        : "填 " + String.format("%.3f", Math.abs(markerPoint[1] - designY));

                canvas.save();
                canvas.translate(mx, (my + dy) / 2);
                canvas.rotate(-90);
                pText.setTextSize(22f);
                pText.setColor(pDashV.getColor());
                pText.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(vTxt, 0, -6, pText);
                canvas.restore();

                pText.setTextSize(20f);
                pText.setColor(pDashV.getColor());
                pText.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(
                    String.format("w=%s h=%s",
                        String.format("%.3f", markerPoint[0]),
                        String.format("%.3f", designY)),
                    mx + 15, dy + 6, pText);
            }

            if (horizontalMove != null && Math.abs(horizontalMove) > 0.001) {
                float tx = sx(markerPoint[0] + horizontalMove);

                Paint pDashH = new Paint();
                pDashH.setColor(0xFF888888);
                pDashH.setStyle(Paint.Style.STROKE);
                pDashH.setStrokeWidth(3f);
                pDashH.setAntiAlias(true);
                pDashH.setPathEffect(new android.graphics.DashPathEffect(new float[]{12, 8}, 0));

                canvas.drawLine(mx, my, tx, my, pDashH);

                Paint pTarget = new Paint();
                pTarget.setColor(0xFF888888);
                pTarget.setStyle(Paint.Style.FILL);
                pTarget.setAntiAlias(true);
                canvas.drawCircle(tx, my, 8, pTarget);

                String hTxt = horizontalMove > 0
                        ? "→ " + String.format("%.3f", horizontalMove)
                        : "← " + String.format("%.3f", Math.abs(horizontalMove));

                pText.setTextSize(20f);
                pText.setColor(0xFF666666);
                pText.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(hTxt, (mx + tx) / 2, my - 12, pText);
            }

            Paint pMarker = new Paint();
            pMarker.setColor(0xFFFF6600);
            pMarker.setStyle(Paint.Style.FILL);
            pMarker.setAntiAlias(true);
            canvas.drawCircle(mx, my, 14, pMarker);

            Paint pWhite = new Paint();
            pWhite.setColor(0xFFFFFFFF);
            pWhite.setStyle(Paint.Style.STROKE);
            pWhite.setStrokeWidth(3f);
            pWhite.setAntiAlias(true);
            canvas.drawCircle(mx, my, 14, pWhite);

            pText.setTextSize(20f);
            pText.setColor(0xFF333333);
            pText.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(String.format("实测 %.3f", markerPoint[1]),
                            mx + 20, my - 20, pText);
        }

        // ---- 中桩 / 左右外缘 ----
        drawVerticalLabel(canvas, new double[]{0, centerY},
            new String[]{ String.format("%.3f", centerY) },
            0xFF0078D4);

        if (lOuter != null) {
            drawVerticalLabel(canvas, lOuter,
                new String[]{
                    "w=" + String.format("%.3f", Math.abs(lOuter[0])),
                    String.format("%.3f", lOuter[1])
                }, 0xFF0078D4);
        }
        if (rOuter != null) {
            drawVerticalLabel(canvas, rOuter,
                new String[]{
                    "w=" + String.format("%.3f", Math.abs(rOuter[0])),
                    String.format("%.3f", rOuter[1])
                }, 0xFF0078D4);
        }
    }

    // ============================================================
    // 边坡每个点的 "w=... h=..."（旋转 -90°）
    // ============================================================
    private void drawSlopePointLabels(Canvas canvas, double[][] slope, int color) {
        if (slope == null || slope.length == 0) return;

        pText.setColor(color);
        pText.setTextSize(20f);
        pText.setTextAlign(Paint.Align.LEFT);

        for (int i = 0; i < slope.length; i++) {
            double wx = slope[i][0];
            double wy = slope[i][1];

            String txt = String.format("w=%.3f h=%.3f", wx, wy);

            float x = sx(wx);
            float y = sy(wy);

            canvas.save();
            canvas.translate(x, y - 15);
            canvas.rotate(-90);
            canvas.drawText(txt, 0, 0, pText);
            canvas.restore();
        }
    }
}