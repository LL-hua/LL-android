package com.hdm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;

import java.util.ArrayList;
import java.util.List;
import android.graphics.Paint;

/**
 * 土方断面视图。
 */
public class EarthworkView extends BaseSectionView {

    private SectionResult data;

    public EarthworkView(Context context) {
        super(context);
    }

    public void setData(SectionResult d) {
        this.data = d;
        if (d != null) tryFitOnce();
        invalidate();
    }

    @Override
    protected boolean hasData() {
        return data != null;
    }

    @Override
    protected List<double[][]> getLinesToFit() {
        List<double[][]> lines = new ArrayList<double[][]>();
        if (data == null) return lines;
        lines.add(data.ground);
        lines.add(data.cleared);
        lines.add(data.design);
        lines.add(data.leftSubgrade);
        lines.add(data.rightSubgrade);
        return lines;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.WHITE);

        if (data == null) return;

        // 结构层
        if (data.layers != null) {
            for (int k = 0; k < data.layers.size(); k++) {
                double[][] poly = data.layers.get(k);
                if (poly == null || poly.length < 3) continue;
                Path path = new Path();
                path.moveTo(sx(poly[0][0]), sy(poly[0][1]));
                for (int i = 1; i < poly.length; i++) {
                    path.lineTo(sx(poly[i][0]), sy(poly[i][1]));
                }
                path.close();
                canvas.drawPath(path, pLayerFill);
                canvas.drawPath(path, pLayerStroke);
            }
        }

        drawPolyline(canvas, data.ground, pGround);

        pCleared.setPathEffect(new android.graphics.DashPathEffect(new float[]{10, 6}, 0));
        drawPolyline(canvas, data.cleared, pCleared);
        pCleared.setPathEffect(null);

        drawPolyline(canvas, data.design, pDesign);

        // 顶面标注
        if (data.lOuter != null) {
            drawVerticalLabel(canvas, data.lOuter,
                new String[]{
                    "w=" + String.format("%.3f", Math.abs(data.lOuter[0])),
                    String.format("%.3f", data.lOuter[1])
                }, 0xFF0078D4);
        }
        drawVerticalLabel(canvas, new double[]{0, data.centerY},
            new String[]{ String.format("%.3f", data.centerY) },
            0xFF0078D4);
        if (data.rOuter != null) {
            drawVerticalLabel(canvas, data.rOuter,
                new String[]{
                    "w=" + String.format("%.3f", Math.abs(data.rOuter[0])),
                    String.format("%.3f", data.rOuter[1])
                }, 0xFF0078D4);
        }

        drawSlopeLabels(canvas, data.leftSlopeTrimmed, 0xFFDD0000);
        drawSlopeLabels(canvas, data.rightSlopeTrimmed, 0xFFDD0000);
        drawCrossfallLabels(canvas, data.leftSlabs, 0, data.centerY, true, 0xFF0050A0);
        drawCrossfallLabels(canvas, data.rightSlabs, 0, data.centerY, false, 0xFF0050A0);

        // 左上角
        pText.setTextSize(30f);
        pText.setColor(0xFF333333);
        pText.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(LL.hua_Num2K(data.station), 20, 40, pText);

        pText.setTextSize(24f);
        pText.setColor(0xFF00AA00);
        canvas.drawText("填 " + String.format("%.3f", data.fillArea), 20, 70, pText);
        pText.setColor(0xFFDD0000);
        canvas.drawText("挖 " + String.format("%.3f", data.cutArea), 20, 100, pText);
        pText.setColor(0xFF888888);
        canvas.drawText("清 " + String.format("%.3f", data.clearArea), 20, 130, pText);
    }
}
