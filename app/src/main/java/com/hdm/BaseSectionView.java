package com.hdm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 断面视图基类。
 * 负责：坐标变换 / 手势 / 自适应 / 绘制工具。
 * 子类实现：onDraw 具体画什么。
 */
public abstract class BaseSectionView extends View {

    // 是否已 fit
    protected boolean hasFitted = false;

    // 手势
    protected GestureController gesture;

    // 画笔（子类共用）
    protected Paint pGround = new Paint();
    protected Paint pCleared = new Paint();
    protected Paint pDesign = new Paint();
    protected Paint pLayerFill = new Paint();
    protected Paint pLayerStroke = new Paint();
    protected Paint pText = new Paint();
    protected Paint pRed = new Paint();

    public BaseSectionView(Context context) {
        super(context);
        initPaints();

        gesture = new GestureController(new GestureController.Listener() {
                @Override
                public void onTransform() {
                    invalidate();
                }
            });
    }

    protected void initPaints() {
        pGround.setColor(0xFF00AA00);
        pGround.setStrokeWidth(3f);
        pGround.setStyle(Paint.Style.STROKE);
        pGround.setAntiAlias(true);

        pCleared.setColor(0xFF888888);
        pCleared.setStrokeWidth(2f);
        pCleared.setStyle(Paint.Style.STROKE);
        pCleared.setAntiAlias(true);

        pDesign.setColor(0xFFDD0000);
        pDesign.setStrokeWidth(4f);
        pDesign.setStyle(Paint.Style.STROKE);
        pDesign.setAntiAlias(true);

        pLayerFill.setColor(0xFFCFE4F7);
        pLayerFill.setStyle(Paint.Style.FILL);

        pLayerStroke.setColor(0xFF4A90D9);
        pLayerStroke.setStrokeWidth(1f);
        pLayerStroke.setStyle(Paint.Style.STROKE);

        pText.setColor(0xFF333333);
        pText.setTextSize(30f);
        pText.setAntiAlias(true);

        pRed.setColor(0xFFDD0000);
        pRed.setStrokeWidth(4f);
        pRed.setStyle(Paint.Style.STROKE);
        pRed.setAntiAlias(true);
    }

    // ============================================================
    // 子类实现
    // ============================================================
    /** 返回"要自适应"的折线列表 */
    protected abstract List<double[][]> getLinesToFit();

    /** 是否已加载数据 */
    protected abstract boolean hasData();

    /** 自适应时的边距（可覆盖） */
    protected double getPadYFactor() {
        return 0.15;
    }

    // ============================================================
    // 公共 API
    // ============================================================
    public void resetView() {
        fitToView();
        hasFitted = true;
        invalidate();
    }

    public void tryFitOnce() {
        if (!hasFitted) {
            fitToView();
            hasFitted = true;
        }
    }

    // ============================================================
    // 自适应
    // ============================================================
    protected void fitToView() {
        if (!hasData()) return;

        double bMinX = Double.MAX_VALUE, bMaxX = -Double.MAX_VALUE;
        double bMinY = Double.MAX_VALUE, bMaxY = -Double.MAX_VALUE;

        List<double[][]> lines = getLinesToFit();
        if (lines == null) return;

        for (int k = 0; k < lines.size(); k++) {
            double[][] pts = lines.get(k);
            if (pts == null) continue;
            for (int i = 0; i < pts.length; i++) {
                double x = pts[i][0];
                double y = pts[i][1];
                if (x < bMinX) bMinX = x;
                if (x > bMaxX) bMaxX = x;
                if (y < bMinY) bMinY = y;
                if (y > bMaxY) bMaxY = y;
            }
        }

        if (bMinX > bMaxX) return;

        double padX = (bMaxX - bMinX) * 0.08;
        double padY = (bMaxY - bMinY) * getPadYFactor();

        double minX = bMinX - padX;
        double maxX = bMaxX + padX;
        double minY = bMinY - padY;
        double maxY = bMaxY + padY;

        double worldW = maxX - minX;
        double worldH = maxY - minY;

        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW == 0 || viewH == 0) return;

        float scale = (float) Math.min(viewW / worldW, viewH / worldH);
        double cx = (minX + maxX) / 2;
        double cy = (minY + maxY) / 2;
        float offX = viewW / 2f - (float) (cx * scale);
        float offY = viewH / 2f + (float) (cy * scale);

        gesture.setTransform(offX, offY, scale);
    }

    // ============================================================
    // 坐标变换
    // ============================================================
    protected float sx(double wx) {
        return (float) (wx * gesture.getScale() + gesture.getOffsetX());
    }

    protected float sy(double wy) {
        return (float) (-wy * gesture.getScale() + gesture.getOffsetY());
    }

    // ============================================================
    // 尺寸变化
    // ============================================================
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (hasData() && !hasFitted) {
            fitToView();
            hasFitted = true;
        }
    }

    // ============================================================
    // 触摸
    // ============================================================
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gesture.onTouchEvent(event);
    }

    // ============================================================
    // 绘制工具（子类共用）
    // ============================================================
    protected void drawPolyline(Canvas canvas, double[][] pts, Paint p) {
        if (pts == null || pts.length < 2) return;
        Path path = new Path();
        path.moveTo(sx(pts[0][0]), sy(pts[0][1]));
        for (int i = 1; i < pts.length; i++) {
            path.lineTo(sx(pts[i][0]), sy(pts[i][1]));
        }
        canvas.drawPath(path, p);
    }

    protected void drawVerticalLabel(Canvas canvas, double[] worldPt, String[] lines, int color) {
        if (worldPt == null || lines == null || lines.length == 0) return;

        float x = sx(worldPt[0]);
        float y = sy(worldPt[1]);

        canvas.save();
        canvas.translate(x, y - 20);
        canvas.rotate(-90);

        pText.setColor(color);
        pText.setTextSize(22f);
        pText.setTextAlign(Paint.Align.LEFT);

        float lineGap = 26f;
        for (int i = 0; i < lines.length; i++) {
            canvas.drawText(lines[i], 0, i * lineGap, pText);
        }

        canvas.restore();
    }

    protected void drawSlopeLabels(Canvas canvas, double[][] pts, int color) {
        if (pts == null || pts.length < 2) return;

        pText.setColor(color);
        pText.setTextSize(20f);
        pText.setTextAlign(Paint.Align.CENTER);

        for (int i = 0; i < pts.length - 1; i++) {
            double x1 = pts[i][0], y1 = pts[i][1];
            double x2 = pts[i + 1][0], y2 = pts[i + 1][1];
            double dx = x2 - x1, dy = y2 - y1;
            if (Math.abs(dy) < 1e-6) continue;

            double ratio = Math.abs(dx / dy);
            String label = "1:" + String.format("%.2f", ratio);

            double mx = (x1 + x2) / 2;
            double my = (y1 + y2) / 2;

            double angleDeg = -Math.atan2(dy, dx) * 180 / Math.PI;

            float cx = sx(mx);
            float cy = sy(my);

            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate((float) angleDeg);
            canvas.drawText(label, 0, 0, pText);
            canvas.restore();
        }

        pText.setTextAlign(Paint.Align.LEFT);
    }

    protected void drawCrossfallLabels(Canvas canvas, double[][] slabs,
                                        double startX, double startY,
                                        boolean isLeft, int color) {
        if (slabs == null || slabs.length == 0) return;

        pText.setColor(color);
        pText.setTextSize(18f);
        pText.setTextAlign(Paint.Align.CENTER);

        double curX = startX;
        double curY = startY;

        int n = slabs.length;
        for (int idx = 0; idx < n; idx++) {
            int i = isLeft ? (n - 1 - idx) : idx;

            double w = slabs[i][0];
            double slope = slabs[i][1];

            double nextX = isLeft ? (curX - w) : (curX + w);
            double nextY = curY + w * (slope / 100.0);

            double midX = (curX + nextX) / 2;
            double midY = (curY + nextY) / 2;

            double visualSlope = isLeft ? -slope : slope;
            double angleDeg = -Math.atan2(visualSlope / 100.0, 1.0) * 180 / Math.PI;

            double norm = Math.sqrt(1 + (visualSlope / 100.0) * (visualSlope / 100.0));
            double nx = -(visualSlope / 100.0) / norm;
            double ny = 1.0 / norm;
            double offsetWorld = 0.3;

            float cx = sx(midX + nx * offsetWorld);
            float cy = sy(midY + ny * offsetWorld);

            String label = String.format("%.2f%%", slope);

            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate((float) angleDeg);
            canvas.drawText(label, 0, 0, pText);
            canvas.restore();

            curX = nextX;
            curY = nextY;
        }

        pText.setTextAlign(Paint.Align.LEFT);
    }
}