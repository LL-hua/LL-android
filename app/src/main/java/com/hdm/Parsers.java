package com.hdm;

import java.util.*;

/// 路基宽度引擎
class LuJiYaoSuYinQing {

    public static LuJiCheDaoJieGuoBao getcrosectonxy(
            double muBiaoZhuangHao,
            double zhongZhuangGaoCheng,
            List<DuanMianShuJu> suoYouZuoData,
            List<DuanMianShuJu> suoYouYouData) {

        LuJiCheDaoJieGuoBao jieGuoBao = new LuJiCheDaoJieGuoBao();

        // ===== 左侧 =====
        if (suoYouZuoData != null && !suoYouZuoData.isEmpty()) {
            double stBefore = suoYouZuoData.get(0).zhuangHao;
            double stAfter = suoYouZuoData.get(suoYouZuoData.size() - 1).zhuangHao;
            DuanMianShuJu dmBefore = suoYouZuoData.get(0);
            DuanMianShuJu dmAfter = suoYouZuoData.get(suoYouZuoData.size() - 1);

            if (muBiaoZhuangHao <= stBefore) {
                dmAfter = dmBefore;
                stAfter = stBefore;
            } else if (muBiaoZhuangHao >= stAfter) {
                dmBefore = dmAfter;
                stBefore = stAfter;
            } else {
                for (int i = 1; i < suoYouZuoData.size(); i++) {
                    if (suoYouZuoData.get(i).zhuangHao >= muBiaoZhuangHao) {
                        stAfter = suoYouZuoData.get(i).zhuangHao;
                        dmAfter = suoYouZuoData.get(i);
                        stBefore = suoYouZuoData.get(i - 1).zhuangHao;
                        dmBefore = suoYouZuoData.get(i - 1);
                        break;
                    }
                }
            }

            double ratio = (stBefore == stAfter) ? 0
                    : (muBiaoZhuangHao - stBefore) / (stAfter - stBefore);
            double curZuoX = 0.0;
            double curZuoY = zhongZhuangGaoCheng;

            List<double[]> dianList = new ArrayList<>();
            List<double[]> banKuaiList = new ArrayList<>();

            for (int k = 0; k < dmBefore.banKuaiJiHe.size(); k++) {
                if (k >= dmAfter.banKuaiJiHe.size()) break;
                double wBefore = dmBefore.banKuaiJiHe.get(k)[0];
                double sBefore = dmBefore.banKuaiJiHe.get(k)[1];
                double wAfter = dmAfter.banKuaiJiHe.get(k)[0];
                double sAfter = dmAfter.banKuaiJiHe.get(k)[1];

                double curWidth = wBefore + ratio * (wAfter - wBefore);
                double curSlopePercent = sBefore + ratio * (sAfter - sBefore);
                double curSlopeVal = curSlopePercent / 100.0;

                curZuoX -= curWidth;
                curZuoY += (curWidth * curSlopeVal);

                dianList.add(new double[]{curZuoX, curZuoY});
                banKuaiList.add(new double[]{curWidth, curSlopePercent});
            }

            Collections.reverse(dianList);
            Collections.reverse(banKuaiList);

            jieGuoBao.zuoCeCheDaoJueDui = listToMat(dianList);
            jieGuoBao.zuoBanKuai = listToMat(banKuaiList);
        } else {
            jieGuoBao.zuoCeCheDaoJueDui = new double[0][2];
            jieGuoBao.zuoBanKuai = new double[0][2];
        }

        // ===== 右侧 =====
        if (suoYouYouData != null && !suoYouYouData.isEmpty()) {
            double stBefore = suoYouYouData.get(0).zhuangHao;
            double stAfter = suoYouYouData.get(suoYouYouData.size() - 1).zhuangHao;
            DuanMianShuJu dmBefore = suoYouYouData.get(0);
            DuanMianShuJu dmAfter = suoYouYouData.get(suoYouYouData.size() - 1);

            if (muBiaoZhuangHao <= stBefore) {
                dmAfter = dmBefore;
                stAfter = stBefore;
            } else if (muBiaoZhuangHao >= stAfter) {
                dmBefore = dmAfter;
                stBefore = stAfter;
            } else {
                for (int i = 1; i < suoYouYouData.size(); i++) {
                    if (suoYouYouData.get(i).zhuangHao >= muBiaoZhuangHao) {
                        stAfter = suoYouYouData.get(i).zhuangHao;
                        dmAfter = suoYouYouData.get(i);
                        stBefore = suoYouYouData.get(i - 1).zhuangHao;
                        dmBefore = suoYouYouData.get(i - 1);
                        break;
                    }
                }
            }

            double ratio = (stBefore == stAfter) ? 0
                    : (muBiaoZhuangHao - stBefore) / (stAfter - stBefore);
            double curYouX = 0.0;
            double curYouY = zhongZhuangGaoCheng;

            List<double[]> dianList = new ArrayList<>();
            List<double[]> banKuaiList = new ArrayList<>();

            for (int k = 0; k < dmBefore.banKuaiJiHe.size(); k++) {
                if (k >= dmAfter.banKuaiJiHe.size()) break;
                double wBefore = dmBefore.banKuaiJiHe.get(k)[0];
                double sBefore = dmBefore.banKuaiJiHe.get(k)[1];
                double wAfter = dmAfter.banKuaiJiHe.get(k)[0];
                double sAfter = dmAfter.banKuaiJiHe.get(k)[1];

                double curWidth = wBefore + ratio * (wAfter - wBefore);
                double curSlopePercent = sBefore + ratio * (sAfter - sBefore);
                double curSlopeVal = curSlopePercent / 100.0;

                curYouX += curWidth;
                curYouY += (curWidth * curSlopeVal);

                dianList.add(new double[]{curYouX, curYouY});
                banKuaiList.add(new double[]{curWidth, curSlopePercent});
            }

            jieGuoBao.youCeCheDaoJueDui = listToMat(dianList);
            jieGuoBao.youBanKuai = listToMat(banKuaiList);
        } else {
            jieGuoBao.youCeCheDaoJueDui = new double[0][2];
            jieGuoBao.youBanKuai = new double[0][2];
        }

        return jieGuoBao;
    }

    static double[][] listToMat(List<double[]> list) {
        double[][] m = new double[list.size()][];
        for (int i = 0; i < list.size(); i++) m[i] = list.get(i);
        return m;
    }
}

/// 边坡引擎
class BianPoYinQing {

    public static BianPoDuanLuo k2BianPo(List<BianPoDuanLuo> suoYouBianPo, double muBiaoZhuangHao) {
        for (BianPoDuanLuo duanLuo : suoYouBianPo) {
            if (muBiaoZhuangHao >= duanLuo.qiShiZhuangHao
                    && muBiaoZhuangHao < duanLuo.jieShuZhuangHao) {
                return duanLuo;
            }
        }
        return null;
    }

    public static BianPoHouXuanBao getAbsolute(
            BianPoDuanLuo dangQianBianPo,
            double zuoYuanX, double zuoYuanY,
            double youYuanX, double youYuanY) {

        BianPoHouXuanBao houxuan = new BianPoHouXuanBao();
        if (dangQianBianPo == null) {
            houxuan.zuoTianJueDui = new double[0][2];
            houxuan.zuoWaJueDui = new double[0][2];
            houxuan.youTianJueDui = new double[0][2];
            houxuan.youWaJueDui = new double[0][2];
            return houxuan;
        }

        houxuan.zuoTianJueDui = accumulate(dangQianBianPo.zuoTian, zuoYuanX, zuoYuanY, true);
        houxuan.zuoWaJueDui = accumulate(dangQianBianPo.zuoWa, zuoYuanX, zuoYuanY, true);
        houxuan.youTianJueDui = accumulate(dangQianBianPo.youTian, youYuanX, youYuanY, false);
        houxuan.youWaJueDui = accumulate(dangQianBianPo.youWa, youYuanX, youYuanY, false);

        return houxuan;
    }

    private static double[][] accumulate(double[][] steps, double startX, double startY, boolean reverse) {
        if (steps == null || steps.length == 0)
            return new double[0][2];

        int n = steps.length;
        double[][] result = new double[n][2];
        double x = startX, y = startY;
        for (int i = 0; i < n; i++) {
            x += steps[i][0];
            y += steps[i][1];
            result[i][0] = x;
            result[i][1] = y;
        }

        if (reverse) {
            for (int i = 0, j = n - 1; i < j; i++, j--) {
                double tx = result[i][0], ty = result[i][1];
                result[i][0] = result[j][0];
                result[i][1] = result[j][1];
                result[j][0] = tx;
                result[j][1] = ty;
            }
        }
        return result;
    }
}

/// 横坡引擎
class LumianSlopeManager {

    public static double InterpolateCrossfall(double[][] records, double currentStation) {
        if (records == null) return 0.0;
        int n = records.length;
        if (n == 0) return 0.0;
        if (n == 1) return records[0][1];

        if (currentStation <= records[0][0]) return records[0][1];
        if (currentStation >= records[n - 1][0]) return records[n - 1][1];

        int low = 0, high = n - 1;
        while (low <= high) {
            int mid = low + ((high - low) >>> 1);
            double midStation = records[mid][0];
            if (Math.abs(midStation - currentStation) < 0.00001) return records[mid][1];
            if (midStation < currentStation) low = mid + 1;
            else high = mid - 1;
        }

        double s1 = records[high][0], v1 = records[high][1];
        double s2 = records[low][0], v2 = records[low][1];
        if (Math.abs(s2 - s1) < 0.00001) return v1;
        return v1 + (v2 - v1) * (currentStation - s1) / (s2 - s1);
    }
}

/// 左结构层引擎
class LeftJiegoucengManager {

    public static List<double[][]> ComputeCoordinates(
            double station, double[] centerPoint, double crossfall,
            List<LeftJiegoucengConfig> configs,
            double[][][] subgradeLineOut) {

        List<double[][]> polygons = new ArrayList<>();

        List<LeftJiegoucengConfig> activeLayers = new ArrayList<>();
        for (LeftJiegoucengConfig cfg : configs) {
            if (station >= cfg.startStation && station <= cfg.endStation) {
                activeLayers.add(cfg);
            }
        }
        // 原来
// activeLayers.sort(Comparator.comparingInt(c -> c.layerIndex));

// 改成
        Collections.sort(activeLayers, new Comparator<LeftJiegoucengConfig>() {
            @Override
            public int compare(LeftJiegoucengConfig a, LeftJiegoucengConfig b) {
                return Integer.compare(a.layerIndex, b.layerIndex);
            }
        });
        if (activeLayers.isEmpty()) {
            subgradeLineOut[0] = new double[0][2];
            return polygons;
        }

        int layerCount = activeLayers.size();
        double k = -crossfall / 100.0;

        double[] topOutX = new double[layerCount];
        double[] topOutY = new double[layerCount];
        double[] botOutX = new double[layerCount];
        double[] botOutY = new double[layerCount];
        double[] topInX = new double[layerCount];
        double[] topInY = new double[layerCount];
        double[] botInX = new double[layerCount];
        double[] botInY = new double[layerCount];

        LeftJiegoucengConfig firstLayer = activeLayers.get(0);
        double curTopInX = centerPoint[0] + firstLayer.innerStepWidth;
        double curTopInY = centerPoint[1];

        double curTopOutX = curTopInX + firstLayer.outerStepWidth;
        double curTopOutY = curTopInY + (curTopOutX - curTopInX) * k;

        for (int i = 0; i < layerCount; i++) {
            LeftJiegoucengConfig cfg = activeLayers.get(i);

            if (i > 0) {
                curTopOutX = botOutX[i - 1] + cfg.outerStepWidth;
                curTopOutY = botOutY[i - 1] + (curTopOutX - botOutX[i - 1]) * k;
                curTopInX = botInX[i - 1] + cfg.innerStepWidth;
                curTopInY = botInY[i - 1] + (curTopInX - botInX[i - 1]) * k;
            }

            topOutX[i] = curTopOutX;
            topOutY[i] = curTopOutY;
            topInX[i] = curTopInX;
            topInY[i] = curTopInY;

            double outN = Math.abs(cfg.outerSlope);
            double dx = -(outN * cfg.thickness) / (1.0 - outN * k);
            double botOutXv = curTopOutX + dx;
            double botOutYv = curTopOutY - cfg.thickness + dx * k;
            botOutX[i] = botOutXv;
            botOutY[i] = botOutYv;

            botInX[i] = curTopInX;
            botInY[i] = curTopInY - cfg.thickness;

            double[][] poly = new double[4][2];
            poly[0][0] = topOutX[i];
            poly[0][1] = topOutY[i];
            poly[1][0] = botOutX[i];
            poly[1][1] = botOutY[i];
            poly[2][0] = botInX[i];
            poly[2][1] = botInY[i];
            poly[3][0] = topInX[i];
            poly[3][1] = topInY[i];
            polygons.add(poly);
        }

        List<double[]> combined = new ArrayList<>();

        for (int i = 0; i < layerCount; i++) {
            combined.add(new double[]{topOutX[i], topOutY[i]});
            combined.add(new double[]{botOutX[i], botOutY[i]});
        }

        List<double[]> innerList = new ArrayList<>();
        innerList.add(new double[]{topInX[0], centerPoint[1]});
        for (int i = 0; i < layerCount; i++) {
            innerList.add(new double[]{topInX[i], topInY[i]});
            innerList.add(new double[]{botInX[i], botInY[i]});
        }
        Collections.reverse(innerList);
        combined.addAll(innerList);

        double[][] subgradeLine = new double[combined.size()][2];
        for (int i = 0; i < combined.size(); i++) {
            subgradeLine[i][0] = combined.get(i)[0];
            subgradeLine[i][1] = combined.get(i)[1];
        }
        subgradeLineOut[0] = subgradeLine;

        return polygons;
    }
}

/// 右结构层引擎
class RightJiegoucengManager {

    public static List<double[][]> ComputeCoordinates(
            double station, double[] centerPoint, double crossfall,
            List<RightJiegoucengConfig> configs,
            double[][][] subgradeLineOut) {

        List<double[][]> polygons = new ArrayList<>();

        List<RightJiegoucengConfig> activeLayers = new ArrayList<>();
        for (RightJiegoucengConfig cfg : configs) {
            if (station >= cfg.startStation && station <= cfg.endStation) {
                activeLayers.add(cfg);
            }
        }
        Collections.sort(activeLayers, new Comparator<RightJiegoucengConfig>() {
            @Override
            public int compare(RightJiegoucengConfig a, RightJiegoucengConfig b) {
                return Integer.compare(a.layerIndex, b.layerIndex);
            }
        });
        if (activeLayers.isEmpty()) {
            subgradeLineOut[0] = new double[0][2];
            return polygons;
        }

        int layerCount = activeLayers.size();
        double k = crossfall / 100.0;

        double[] topOutX = new double[layerCount];
        double[] topOutY = new double[layerCount];
        double[] botOutX = new double[layerCount];
        double[] botOutY = new double[layerCount];
        double[] topInX = new double[layerCount];
        double[] topInY = new double[layerCount];
        double[] botInX = new double[layerCount];
        double[] botInY = new double[layerCount];

        RightJiegoucengConfig firstLayer = activeLayers.get(0);
        double curTopInX = centerPoint[0] + firstLayer.innerStepWidth;
        double curTopInY = centerPoint[1];

        double curTopOutX = curTopInX + firstLayer.outerStepWidth;
        double curTopOutY = curTopInY + (curTopOutX - curTopInX) * k;

        for (int i = 0; i < layerCount; i++) {
            RightJiegoucengConfig cfg = activeLayers.get(i);

            if (i > 0) {
                curTopOutX = botOutX[i - 1] + cfg.outerStepWidth;
                curTopOutY = botOutY[i - 1] + (curTopOutX - botOutX[i - 1]) * k;
                curTopInX = botInX[i - 1] + cfg.innerStepWidth;
                curTopInY = botInY[i - 1] + (curTopInX - botInX[i - 1]) * k;
            }

            topOutX[i] = curTopOutX;
            topOutY[i] = curTopOutY;
            topInX[i] = curTopInX;
            topInY[i] = curTopInY;

            double outN = Math.abs(cfg.outerSlope);
            double dx = (outN * cfg.thickness) / (1.0 + outN * k);
            double botOutXv = curTopOutX + dx;
            double botOutYv = curTopOutY - cfg.thickness + (botOutXv - curTopOutX) * k;
            botOutX[i] = botOutXv;
            botOutY[i] = botOutYv;

            botInX[i] = curTopInX;
            botInY[i] = curTopInY - cfg.thickness;

            double[][] poly = new double[4][2];
            poly[0][0] = topOutX[i];
            poly[0][1] = topOutY[i];
            poly[1][0] = botOutX[i];
            poly[1][1] = botOutY[i];
            poly[2][0] = botInX[i];
            poly[2][1] = botInY[i];
            poly[3][0] = topInX[i];
            poly[3][1] = topInY[i];
            polygons.add(poly);
        }

        List<double[]> combined = new ArrayList<>();

        combined.add(new double[]{topInX[0], centerPoint[1]});
        for (int i = 0; i < layerCount; i++) {
            combined.add(new double[]{topInX[i], topInY[i]});
            combined.add(new double[]{botInX[i], botInY[i]});
        }

        List<double[]> outerList = new ArrayList<>();
        for (int i = 0; i < layerCount; i++) {
            outerList.add(new double[]{topOutX[i], topOutY[i]});
            outerList.add(new double[]{botOutX[i], botOutY[i]});
        }
        Collections.reverse(outerList);
        combined.addAll(outerList);

        double[][] subgradeLine = new double[combined.size()][2];
        for (int i = 0; i < combined.size(); i++) {
            subgradeLine[i][0] = combined.get(i)[0];
            subgradeLine[i][1] = combined.get(i)[1];
        }
        subgradeLineOut[0] = subgradeLine;

        return polygons;
    }
}
