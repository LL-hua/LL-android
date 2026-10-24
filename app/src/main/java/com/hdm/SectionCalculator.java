package com.hdm;

import java.util.*;

/// 单桩号断面计算核心
public class SectionCalculator {

    // 输入
    public final double[][] mesh;
    public final double[][] pqx;
    public final double[][] sqx;
    public final List<DuanMianShuJu> leftWidths;
    public final List<DuanMianShuJu> rightWidths;
    public final List<BianPoDuanLuo> slopeData;
    public final double[][] leftCrossfalls;
    public final double[][] rightCrossfalls;
    public final List<LeftJiegoucengConfig> leftStructures;
    public final List<RightJiegoucengConfig> rightStructures;
    public final double clearDepth;

    public SectionCalculator(
            double[][] mesh, double[][] pqx, double[][] sqx,
            List<DuanMianShuJu> leftWidths, List<DuanMianShuJu> rightWidths,
            List<BianPoDuanLuo> slopeData,
            double[][] leftCrossfalls, double[][] rightCrossfalls,
            List<LeftJiegoucengConfig> leftStructures,
            List<RightJiegoucengConfig> rightStructures,
            double clearDepth) {
        this.mesh = mesh;
        this.pqx = pqx;
        this.sqx = sqx;
        this.leftWidths = leftWidths;
        this.rightWidths = rightWidths;
        this.slopeData = slopeData;
        this.leftCrossfalls = leftCrossfalls;
        this.rightCrossfalls = rightCrossfalls;
        this.leftStructures = leftStructures;
        this.rightStructures = rightStructures;
        this.clearDepth = clearDepth;
    }

    public ComputeResult Compute(double station) {
        double centerY = LL.hua_H(sqx, station);

        LuJiCheDaoJieGuoBao dao = LuJiYaoSuYinQing.getcrosectonxy(
                station, centerY, leftWidths, rightWidths);

        double[][] zuoDian = dao.zuoCeCheDaoJueDui;
        double[][] youDian = dao.youCeCheDaoJueDui;

        if (zuoDian == null || zuoDian.length == 0
                || youDian == null || youDian.length == 0) {
            return err("路基宽度数据缺失");
        }

        double lOuterX = zuoDian[0][0];
        double lOuterY = zuoDian[0][1];
        int nYou = youDian.length;
        double rOuterX = youDian[nYou - 1][0];
        double rOuterY = youDian[nYou - 1][1];

        double[][] ground = LL.getMatchedBZ(mesh, station, 3.0);
        if (ground == null || ground.length == 0)
            return err("地面线为空");

        double[][] cleared = LL.hua_OffsetPolyline(ground, clearDepth);

        BianPoDuanLuo bpCfg = BianPoYinQing.k2BianPo(slopeData, station);
        if (bpCfg == null) return err("边坡段落为空");

        BianPoHouXuanBao candidates = BianPoYinQing.getAbsolute(
                bpCfg, lOuterX, lOuterY, rOuterX, rOuterY);
        double lGroundY = LL.FromXgetY(ground, lOuterX);
        double[][] finalLeft = (lOuterY > lGroundY)
                ? candidates.zuoTianJueDui : candidates.zuoWaJueDui;
        double rGroundY = LL.FromXgetY(ground, rOuterX);
        double[][] finalRight = (rOuterY > rGroundY)
                ? candidates.youTianJueDui : candidates.youWaJueDui;

        // 拼设计线（未裁剪）
        List<double[]> rawDesign = new ArrayList<>();
        if (finalLeft != null)
            for (double[] p : finalLeft) rawDesign.add(new double[] { p[0], p[1] });
        for (double[] p : zuoDian) rawDesign.add(new double[] { p[0], p[1] });
        rawDesign.add(new double[] { 0.0, centerY });
        for (double[] p : youDian) rawDesign.add(new double[] { p[0], p[1] });
        if (finalRight != null)
            for (double[] p : finalRight) rawDesign.add(new double[] { p[0], p[1] });

        double[][] designTop = toMat(rawDesign);

        // 左结构层
        double leftCrossfall = LumianSlopeManager.InterpolateCrossfall(leftCrossfalls, station);
        double[] leftCenter = new double[] { 0.0, centerY };
        double[][][] leftSubOut = new double[1][][];
        List<double[][]> leftPolygons = LeftJiegoucengManager.ComputeCoordinates(
                station, leftCenter, leftCrossfall, leftStructures, leftSubOut);
        double[][] leftSubgrade = leftSubOut[0];

        if (leftPolygons.isEmpty()) return err("左幅结构层段落为空");

        double leftInnerX = leftSubgrade[leftSubgrade.length - 1][0];
        double newH = LL.FromXgetY(designTop, leftInnerX);
        leftCenter = new double[] { 0.0, newH };
        leftPolygons = LeftJiegoucengManager.ComputeCoordinates(
                station, leftCenter, leftCrossfall, leftStructures, leftSubOut);
        leftSubgrade = leftSubOut[0];

        // 右结构层
        double rightCrossfall = LumianSlopeManager.InterpolateCrossfall(rightCrossfalls, station);
        double[] rightCenter = new double[] { 0.0, centerY };
        double[][][] rightSubOut = new double[1][][];
        List<double[][]> rightPolygons = RightJiegoucengManager.ComputeCoordinates(
                station, rightCenter, rightCrossfall, rightStructures, rightSubOut);
        double[][] rightSubgrade = rightSubOut[0];

        if (rightPolygons.isEmpty()) return err("右幅结构层段落为空");

        double rightInnerX = rightSubgrade[0][0];
        newH = LL.FromXgetY(designTop, rightInnerX);
        rightCenter = new double[] { 0.0, newH };
        rightPolygons = RightJiegoucengManager.ComputeCoordinates(
                station, rightCenter, rightCrossfall, rightStructures, rightSubOut);
        rightSubgrade = rightSubOut[0];

        // 拼最终设计线
        double leftOuterAnchor = leftSubgrade.length > 0 ? leftSubgrade[0][0] : lOuterX;
        double rightOuterAnchor = rightSubgrade.length > 0
                ? rightSubgrade[rightSubgrade.length - 1][0] : rOuterX;

        List<double[]> designList = new ArrayList<>();
        addOuterPart(rawDesign, designList, leftOuterAnchor, -1);
        for (double[] p : leftSubgrade) designList.add(new double[] { p[0], p[1] });

        double leftInnerAnchor = leftSubgrade[leftSubgrade.length - 1][0];
        double rightInnerAnchor = rightSubgrade[0][0];
        for (double[] p : rawDesign) {
            if (p[0] >= leftInnerAnchor && p[0] <= rightInnerAnchor)
                designList.add(p);
        }

        for (double[] p : rightSubgrade) designList.add(new double[] { p[0], p[1] });
        addOuterPart(rawDesign, designList, rightOuterAnchor, 1);

        double[][] designMat = toMat(designList);

        // 算面积
        double[] res = LL.hua_CutAndFillArea(cleared, designMat, 8);
        double fill = Math.abs(res[0]);
        double cut = Math.abs(res[1]);
        double minX = res[2], maxX = res[3], minY = res[4];
        double[][] clearPoly = LL.BuildClearPolygon(ground, cleared, minX, maxX);
        double clearArea = LL.hua_PolygonArea(clearPoly);

        int count = (int) res[6];
        if (count == 0) return err("设计线与地面线无有效交点");

        double[][] finalDesign = new double[count][2];
        int idx = 7;
        for (int i = 0; i < count; i++) {
            finalDesign[i][0] = res[idx++];
            finalDesign[i][1] = res[idx++];
        }

        // 结构层面积 + cleared1
        List<String> layerAreaTexts = new ArrayList<>();
        List<double[][]> layerPolygons = new ArrayList<>();

        List<double[]> temp = new ArrayList<>();
        for (double[] c : cleared) {
            if (c[0] >= minX && c[0] <= maxX)
                temp.add(new double[] { c[0], c[1] });
        }
        int n = temp.size();
        double[][] cleared1 = new double[n + 2][2];
        cleared1[0][0] = finalDesign[0][0];
        cleared1[0][1] = finalDesign[0][1];
        for (int i = 0; i < n; i++) {
            cleared1[i + 1][0] = temp.get(i)[0];
            cleared1[i + 1][1] = temp.get(i)[1];
        }
        cleared1[n + 1][0] = finalDesign[finalDesign.length - 1][0];
        cleared1[n + 1][1] = finalDesign[finalDesign.length - 1][1];

        for (int i = 0; i < leftPolygons.size(); i++) {
            double[][] poly = leftPolygons.get(i);
            double area = Math.abs(LL.hua_PolygonArea(poly));
            String name = (i < leftStructures.size()
                    && leftStructures.get(i).layerName != null
                    && !leftStructures.get(i).layerName.isEmpty())
                    ? leftStructures.get(i).layerName
                    : "L_Lay" + (i + 1);
            layerAreaTexts.add(String.format("L:%s:%.3f", name, area));
            layerPolygons.add(poly);
        }

        for (int i = 0; i < rightPolygons.size(); i++) {
            double[][] poly = rightPolygons.get(i);
            double area = Math.abs(LL.hua_PolygonArea(poly));
            String name = (i < rightStructures.size()
                    && rightStructures.get(i).layerName != null
                    && !rightStructures.get(i).layerName.isEmpty())
                    ? rightStructures.get(i).layerName
                    : "R_Lay" + (i + 1);
            layerAreaTexts.add(String.format("R:%s:%.3f", name, area));
            layerPolygons.add(poly);
        }

        // 边坡裁剪
        double lToeX = finalDesign[0][0];
        double lToeY = finalDesign[0][1];
        double rToeX = finalDesign[finalDesign.length - 1][0];
        double rToeY = finalDesign[finalDesign.length - 1][1];

        List<double[]> leftTrimmed = new ArrayList<>();
        leftTrimmed.add(new double[] { lToeX, lToeY });
        if (finalLeft != null) {
            for (double[] p : finalLeft) {
                if (p[0] >= lToeX)
                    leftTrimmed.add(new double[] { p[0], p[1] });
            }
        }
        leftTrimmed.add(new double[] { lOuterX, lOuterY });

        List<double[]> rightTrimmed = new ArrayList<>();
        rightTrimmed.add(new double[] { rOuterX, rOuterY });
        if (finalRight != null) {
            for (double[] p : finalRight) {
                if (p[0] <= rToeX)
                    rightTrimmed.add(new double[] { p[0], p[1] });
            }
        }
        rightTrimmed.add(new double[] { rToeX, rToeY });

        // 组装
        SectionResult sr = new SectionResult();
        sr.station = station;
        sr.centerY = centerY;
        sr.lOuter = new double[] { lOuterX, lOuterY };
        sr.rOuter = new double[] { rOuterX, rOuterY };
        sr.lToe = new double[] { lToeX, lToeY };
        sr.rToe = new double[] { rToeX, rToeY };
        sr.ground = ground;
        sr.cleared = cleared1;
        sr.design = finalDesign;
        sr.leftSubgrade = leftSubgrade;
        sr.rightSubgrade = rightSubgrade;
        sr.layers = layerPolygons;
        sr.leftSlabs = dao.zuoBanKuai;
        sr.rightSlabs = dao.youBanKuai;
        sr.leftSlopeRaw = finalLeft != null ? finalLeft : new double[0][2];
        sr.leftSlopeTrimmed = toMat(leftTrimmed);
        sr.rightSlopeRaw = finalRight != null ? finalRight : new double[0][2];
        sr.rightSlopeTrimmed = toMat(rightTrimmed);
        sr.fillArea = fill;
        sr.cutArea = cut;
        sr.clearArea = clearArea;
        sr.layerAreas = layerAreaTexts;
        sr.bounds = new double[] { minX, minY, maxX, res[5] };

        ComputeResult cr = new ComputeResult();
        cr.success = true;
        cr.result = sr;
        return cr;
    }

    // ============================================================

    private static double[][] toMat(List<double[]> list) {
        double[][] m = new double[list.size()][2];
        for (int i = 0; i < list.size(); i++) {
            m[i][0] = list.get(i)[0];
            m[i][1] = list.get(i)[1];
        }
        return m;
    }

    private static void addOuterPart(List<double[]> raw, List<double[]> design,
                                      double anchor, int side) {
        for (int i = 0; i < raw.size(); i++) {
            double[] p = raw.get(i);
            boolean condition = side < 0 ? p[0] <= anchor : p[0] >= anchor;
            if (condition) design.add(p);

            if (i > 0) {
                double[] prev = raw.get(i - 1);
                if ((side < 0 && prev[0] < anchor && p[0] > anchor)
                        || (side > 0 && prev[0] > anchor && p[0] < anchor)) {
                    double x1 = prev[0], y1 = prev[1], x2 = p[0], y2 = p[1];
                    double y = y1 + (y2 - y1) * (anchor - x1) / (x2 - x1);
                    design.add(new double[] { anchor, y });
                }
            }
        }
    }

    private static ComputeResult err(String msg) {
        ComputeResult cr = new ComputeResult();
        cr.success = false;
        cr.errorMessage = msg;
        return cr;
    }
}
