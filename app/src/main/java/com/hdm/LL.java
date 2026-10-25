package com.hdm;

import java.util.*;

public class LL {

    // ============================================================
    // 1. 桩号 / 角度工具
    // ============================================================

    /// 米 → K 桩号字符串
    public static String hua_Num2K(double meters) {
        int km = (int) Math.floor(meters / 1000);
        double m = meters - km * 1000;
        m = Math.round(m * 100) / 100.0;
        if (m == (int) m)
            return String.format("K%d+%03d", km, (int) m);
        else
            return String.format("K%d+%06.2f", km, m);
    }

/// 度分秒（DDD.MMSS）→ 弧度 (修复精度丢失Bug)
    public static double hua_DmsToRadians(double dms) {
        // 保留正负号，因为测量方位角有时会有负数
        int sign = dms >= 0 ? 1 : -1;
        dms = Math.abs(dms);

        // 提取度，并四舍五入去掉浮点误差
        int degrees = (int) dms;

        // 剩下的分秒部分
        double fractional = dms - degrees;
        double ms = fractional * 100.0;

        // 核心修复：用 Math.round 对分进行四舍五入，解决 28.99999 变成 28 的问题
        int minutes = (int) Math.round(ms);

        // 计算秒
        double seconds = (ms - minutes) * 100.0;

        // 如果秒接近60（比如 59.999秒），进1分
        if (seconds >= 59.9999) {
            seconds = 0;
            minutes += 1;
        }

        // 如果分到了60，进1度
        if (minutes >= 60) {
            minutes = 0;
            degrees += 1;
        }

        // 组合
        double totalDegrees = degrees + minutes / 60.0 + seconds / 3600.0;
        return sign * totalDegrees * Math.PI / 180.0;
    }

/// 弧度 → 度分秒（DDD.MMSS）(修复进位Bug)
    public static double hua_radiansToDMS(double radians) {
        double degrees = radians * (180 / Math.PI);

        // 处理负数
        int sign = degrees >= 0 ? 1 : -1;
        degrees = Math.abs(degrees);

        int d = (int) degrees;
        double remaining = (degrees - d) * 60;
        int m = (int) remaining;
        double s = (remaining - m) * 60;

        // 核心修复：如果秒四舍五入后是60，秒归零，分进一
        s = Math.round(s * 10.0) / 10.0;
        if (s >= 60.0) {
            s = 0.0;
            m += 1;
        }
        if (m >= 60) {
            m = 0;
            d += 1;
        }

        String mm = m < 10 ? "0" + m : "" + m;
        String ss = s < 10 ? String.format("0%.1f", s) : String.format("%.1f", s);

        String dfm = d + mm + ss;
        return sign * (Math.round(Double.parseDouble(dfm)) / 10000.0);
    }

    // ============================================================
    // 2. 方位角 + 距离
    // ============================================================

    /// 方位角 + 距离
    public static double[] hua_Fwj(double x0, double y0, double x1, double y1) {
        double x = x1 - x0;
        double y = y1 - y0;
        double cd = Math.sqrt(x * x + y * y);
        double hd = Math.atan2(y, x);
        if (hd < 0) hd += 2 * Math.PI;
        return new double[]{cd, hd};
    }

    // ============================================================
    // 3. 线路计算：hua_Zs（高斯积分）
    // ============================================================

    // 高斯积分系数
    private static final double[] GaussRr = {0.1739274226, 0.3260725774, 0.3260725774, 0.1739274226};
    private static final double[] GaussVv = {0.0694318442, 0.3300094782, 0.6699905218, 0.9305681558};

    /// 线路坐标 + 方位角
    public static double[] hua_Zs(
            double xyk, double xyx, double xyy, double xyhd, double xycd,
            double xyqdr, double xyzdr, double xyzy, double jsk,
            double jsb, double jd) {
        jd = hua_DmsToRadians(jd);
        double w = jsk - xyk;

        // 圆曲线
        if (Math.abs(xyqdr - xyzdr) < 0.01 && xyqdr > 0) {
            double a = xyhd + xyzy * Math.PI / 2;
            double da = w / xyqdr * xyzy;
            double ta = xyhd + da;
            if (ta < 0) ta += 2 * Math.PI;
            double x = xyx + xyqdr * Math.cos(a) - xyqdr * Math.cos(a + da)
                    + jsb * Math.cos(a + da - xyzy * Math.PI / 2 + jd);
            double y = xyy + xyqdr * Math.sin(a) - xyqdr * Math.sin(a + da)
                    + jsb * Math.sin(a + da - xyzy * Math.PI / 2 + jd);
            return new double[]{x, y, ta};
        }

        // 直线
        if (xyqdr < 0.01 && xyzdr < 0.01 && xyzy < 0.01) {
            double x = xyx + w * Math.cos(xyhd) + jsb * Math.cos(xyhd + jd);
            double y = xyy + w * Math.sin(xyhd) + jsb * Math.sin(xyhd + jd);
            return new double[]{x, y, xyhd};
        }

        // 缓和曲线
        double qr = xyqdr < 0.001 ? 99999999 : xyqdr;
        double zr = xyzdr < 0.001 ? 99999999 : xyzdr;
        double c = 1 / qr;
        double d = (qr - zr) / (2 * xycd * qr * zr);
        double xs = 0, ys = 0;
        for (int i = 0; i < 4; i++) {
            double v = GaussVv[i];
            double r = GaussRr[i];
            double f = xyhd + xyzy * v * w * (c + v * w * d);
            xs += r * Math.cos(f);
            ys += r * Math.sin(f);
        }
        double fhz3 = xyhd + xyzy * w * (c + w * d);
        fhz3 = (fhz3 % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
        double x2 = xyx + w * xs + jsb * Math.cos(fhz3 + jd);
        double y2 = xyy + w * ys + jsb * Math.sin(fhz3 + jd);
        return new double[]{x2, y2, fhz3};
    }

    /// 按里程定位段落并计算坐标
    public static double[] hua_Dantiaoxianludange(double[][] pqx, double k, double b, double z) {
        int segCount = pqx.length;
        for (int i = 0; i < segCount; i++) {
            if (k >= pqx[i][0] && k <= pqx[i][0] + pqx[i][4]) {
                double rad = hua_DmsToRadians(pqx[i][3]);
                double[] r = hua_Zs(
                pqx[i][0], pqx[i][1], pqx[i][2], rad,
                pqx[i][4], pqx[i][5], pqx[i][6], pqx[i][7],
                k, b, z);
                return new double[]{
                        Math.round(r[0] * 1000) / 1000.0,
                        Math.round(r[1] * 1000) / 1000.0,
                        r[2]
                };
            }
        }
        return new double[]{0, 0, 0};
    }

    /// 单点反算：坐标 → 桩号 + 偏距
    public static double[] hua_Fs(double[][] pqx, double fsx, double fsy) {
        double[] p0 = hua_Dantiaoxianludange(pqx, pqx[0][0], 0, 0);
        double cx = p0[0], cy = p0[1], cAngle = p0[2];
        double[] jljd = hua_Fwj(cx, cy, fsx, fsy);
        double k = pqx[0][0];
        double cz = jljd[0] * Math.cos(jljd[1] - cAngle);
        double pj = jljd[0] * Math.sin(jljd[1] - cAngle);

        int hang = pqx.length - 1;
        double qdlc = pqx[0][0];
        double zdlc = pqx[hang][0] + pqx[hang][4];
        int iter = 0;

        while (Math.abs(cz) > 0.01 && iter < 15) {
            k += cz;
            iter++;
            if (k < qdlc) return new double[]{-1, -1};
            if (k > zdlc) return new double[]{-2, -2};

            p0 = hua_Dantiaoxianludange(pqx, k, 0, 0);
            cx = p0[0];
            cy = p0[1];
            cAngle = p0[2];
            jljd = hua_Fwj(cx, cy, fsx, fsy);
            cz = jljd[0] * Math.cos(jljd[1] - cAngle);
            pj = jljd[0] * Math.sin(jljd[1] - cAngle);
        }

        return new double[]{
                Math.round(k * 1000) / 1000.0,
                Math.round(pj * 1000) / 1000.0
        };
    }

    /// 批量反算：点云 → [桩号, 偏距, 高程]，按桩号升序
    public static double[][] hua_Fs_Batch(double[][] pqx, double[][] xy) {
        if (pqx == null || pqx.length == 0 || xy == null || xy.length == 0)
            return new double[0][];

        int segCount = pqx.length;
        double[] startK = new double[segCount];
        double[] endK = new double[segCount];
        double[] startX = new double[segCount];
        double[] startY = new double[segCount];
        double[] angleRad = new double[segCount];
        double[] len = new double[segCount];
        double[] A = new double[segCount];
        double[] B = new double[segCount];
        double[] C = new double[segCount];

        for (int i = 0; i < segCount; i++) {
            startK[i] = pqx[i][0];
            len[i] = pqx[i][4];
            endK[i] = startK[i] + len[i];
            startX[i] = pqx[i][1];
            startY[i] = pqx[i][2];
            angleRad[i] = hua_DmsToRadians(pqx[i][3]);
            A[i] = pqx[i][5];
            B[i] = pqx[i][6];
            C[i] = pqx[i][7];
        }

        int pointCount = xy.length;
        double[][] results = new double[pointCount][];

        for (int i = 0; i < pointCount; i++) {
            double fsx = xy[i][0];
            double fsy = xy[i][1];
            double fsz = xy[i][2];

            double[] row = new double[3];

            double[] p0 = hua_Zs(startK[0], startX[0], startY[0], angleRad[0], len[0],
            A[0], B[0], C[0], startK[0], 0, 0);
            double cx = p0[0], cy = p0[1], cAngle = p0[2];

            double[] fw = hua_Fwj(cx, cy, fsx, fsy);
            double dist = fw[0], az = fw[1];
            double k = startK[0];
            double cz = dist * Math.cos(az - cAngle);
            double pj = dist * Math.sin(az - cAngle);

            double qdlc = startK[0];
            double zdlc = endK[segCount - 1];
            int iter = 0;
            final int maxIter = 15;

            while (Math.abs(cz) > 0.01 && iter < maxIter) {
                k += cz;
                iter++;

                if (k < qdlc) {
                    row[0] = -1;
                    row[1] = -1;
                    row[2] = fsz;
                    results[i] = row;
                    break;
                }
                if (k > zdlc) {
                    row[0] = -2;
                    row[1] = -2;
                    row[2] = fsz;
                    results[i] = row;
                    break;
                }

                // 线性查找所在段
                int segIdx = 0;
                for (int j = 0; j < segCount; j++) {
                    if (k >= startK[j] && k <= endK[j]) {
                        segIdx = j;
                        break;
                    }
                }

                p0 = hua_Zs(startK[segIdx], startX[segIdx], startY[segIdx],
                angleRad[segIdx], len[segIdx],
                A[segIdx], B[segIdx], C[segIdx],
                k, 0, 0);
                cx = p0[0];
                cy = p0[1];
                cAngle = p0[2];

                fw = hua_Fwj(cx, cy, fsx, fsy);
                dist = fw[0];
                az = fw[1];
                cz = dist * Math.cos(az - cAngle);
                pj = dist * Math.sin(az - cAngle);
            }

            if (row[0] == 0 && row[1] == 0) {
                row[0] = Math.round(k * 1000) / 1000.0;
                row[1] = Math.round(pj * 1000) / 1000.0;
                row[2] = fsz;
            }
            results[i] = row;
        }

        // 按第一列升序
        // 原来
// Arrays.sort(results, (a, b) -> Double.compare(a[0], b[0]));

// 改成
        Arrays.sort(results, new Comparator<double[]>() {
            @Override
            public int compare(double[] a, double[] b) {
                return Double.compare(a[0], b[0]);
            }
        });

        return results;
    }

    // ============================================================
// 4. 匹配 / 折线工具
// ============================================================

/// 按桩号取横断面：二分定位 + 插入排序
    public static double[][] getMatchedBZ(double[][] data, double target, double tolerance) {
        int rows = data.length;
        if (rows == 0) return new double[0][2];

        double lower = target - tolerance;
        int left = 0, right = rows;
        while (left < right) {
            int mid = (left + right) >>> 1;
            if (data[mid][0] < lower) left = mid + 1;
            else right = mid;
        }

        double upper = target + tolerance;

        int count = 0;
        for (int i = left; i < rows && data[i][0] <= upper; i++) count++;
        if (count == 0) return new double[0][2];

        double[][] result = new double[count][2];
        int n = 0;
        for (int i = left; i < rows && data[i][0] <= upper; i++) {
            double b = data[i][1];
            double z = data[i][2];
            int pos = n - 1;
            while (pos >= 0 && result[pos][0] > b) {
                result[pos + 1][0] = result[pos][0];
                result[pos + 1][1] = result[pos][1];
                pos--;
            }
            result[pos + 1][0] = b;
            result[pos + 1][1] = z;
            n++;
        }
        return result;
    }

    /// 沿线路按步长取点
/// 返回：List<double[]>，每项 = [桩号, X, Y, 方位角]
    public static List<double[]> dantiaoxianlu(double[][] pqx, double b, double step) {
        java.util.List<double[]> xys = new java.util.ArrayList<double[]>();

        if (pqx == null || pqx.length == 0) return xys;

        double k = pqx[0][0];

        // 起点
        double[] xy = hua_Dantiaoxianludange(pqx, k, b, 90);
        xys.add(new double[]{k, xy[0], xy[1], xy[2]});

        // k 向上取整到 step 倍数
        k = (int) ((k + step - 1) / step) * step;

        // 逐段遍历
        for (int i = 0; i < pqx.length; i++) {
            double dtk = pqx[i][0];
            double dtcd = pqx[i][4];
            double dtEnd = dtk + dtcd;

            while (k <= dtEnd) {
                xy = hua_Dantiaoxianludange(pqx, k, b, 90);
                xys.add(new double[]{k, xy[0], xy[1], xy[2]});
                k += step;
            }
        }

        // 终点
        int last = pqx.length - 1;
        double lastK = pqx[last][0] + pqx[last][4];
        xy = hua_Dantiaoxianludange(pqx, lastK, b, 90);
        xys.add(new double[]{lastK, xy[0], xy[1], xy[2]});

        return xys;
    }

/// 按 X 二分 + 线性插值求 Y
    public static double FromXgetY(double[][] points, double targetX) {
        final double tolerance = 1e-6;
        int n = points.length;
        if (n == 0) return 0.0;

        int low = 0, high = n - 1, index = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            double midX = points[mid][0];
            if (midX == targetX) {
                index = mid;
                break;
            }
            if (midX < targetX) low = mid + 1;
            else high = mid - 1;
        }
        if (index >= 0) return points[index][1];

        int rightIndex = low;
        int leftIndex = rightIndex - 1;

        if (rightIndex >= n) return points[n - 1][1];
        if (leftIndex < 0) return points[0][1];

        double xLeft = points[leftIndex][0];
        double xRight = points[rightIndex][0];
        if (Math.abs(xRight - xLeft) < tolerance)
            return Math.max(points[leftIndex][1], points[rightIndex][1]);

        double yLeft = points[leftIndex][1];
        double yRight = points[rightIndex][1];
        return yLeft + (yRight - yLeft) * (targetX - xLeft) / (xRight - xLeft);
    }

/// 按 Y 求所有交点 X 坐标
    public static double[] FromYgetX(double[][] points, double targetY) {
        final double tolerance = 1e-12;
        java.util.List<Double> result = new java.util.ArrayList<>();
        int n = points.length;
        if (n == 0) return new double[0];

        for (int i = 0; i < n - 1; i++) {
            double x1 = points[i][0], y1 = points[i][1];
            double x2 = points[i + 1][0], y2 = points[i + 1][1];

            boolean y1Below = y1 <= targetY + tolerance;
            boolean y1Above = y1 >= targetY - tolerance;
            boolean y2Below = y2 <= targetY + tolerance;
            boolean y2Above = y2 >= targetY - tolerance;
            boolean cross = (y1Below && y2Above) || (y1Above && y2Below);
            if (!cross) continue;

            if (Math.abs(y2 - y1) < tolerance) {
                if (result.isEmpty() || Math.abs(result.get(result.size() - 1) - x1) > tolerance)
                    result.add(x1);
                if (i == n - 2 && (result.isEmpty()
                                || Math.abs(result.get(result.size() - 1) - x2) > tolerance))
                    result.add(x2);
                continue;
            }

            double t = (targetY - y1) / (y2 - y1);
            double xIntersect = x1 + t * (x2 - x1);
            if (result.isEmpty() || Math.abs(result.get(result.size() - 1) - xIntersect) > tolerance)
                result.add(xIntersect);
        }

        double[] arr = new double[result.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = result.get(i);
        return arr;
    }

/// 清表偏移：折线整体法向平移
    public static double[][] hua_OffsetPolyline(double[][] points, double offset) {
        if (points == null) return null;
        int ptCount = points.length;
        if (ptCount < 2) return points.clone();

        int segmentCount = ptCount - 1;
        double[][] segLines = new double[segmentCount][4];
        boolean[] validSeg = new boolean[segmentCount];

        for (int i = 0; i < segmentCount; i++) {
            double dx = points[i + 1][0] - points[i][0];
            double dy = points[i + 1][1] - points[i][1];
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-8) continue;
            validSeg[i] = true;
            double nx = -dy / len;
            double ny = dx / len;
            segLines[i][0] = points[i][0] + nx * offset;
            segLines[i][1] = points[i][1] + ny * offset;
            segLines[i][2] = points[i + 1][0] + nx * offset;
            segLines[i][3] = points[i + 1][1] + ny * offset;
        }

        int firstIdx = -1;
        for (int i = 0; i < segmentCount; i++)
            if (validSeg[i]) {
                firstIdx = i;
                break;
            }
        if (firstIdx == -1) return new double[0][2];

        double[][] rawVertices = new double[segmentCount + 1][2];
        int rawCount = 0;
        rawVertices[rawCount][0] = segLines[firstIdx][0];
        rawVertices[rawCount][1] = segLines[firstIdx][1];
        rawCount++;

        int prev = firstIdx;
        for (int i = firstIdx + 1; i < segmentCount; i++) {
            if (!validSeg[i]) continue;

            double x1 = segLines[prev][0], y1 = segLines[prev][1];
            double x2 = segLines[prev][2], y2 = segLines[prev][3];
            double x3 = segLines[i][0], y3 = segLines[i][1];
            double x4 = segLines[i][2], y4 = segLines[i][3];
            double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);

            if (Math.abs(denom) > 1e-8) {
                double t1 = x1 * y2 - y1 * x2;
                double t2 = x3 * y4 - y3 * x4;
                rawVertices[rawCount][0] = (t1 * (x3 - x4) - (x1 - x2) * t2) / denom;
                rawVertices[rawCount][1] = (t1 * (y3 - y4) - (y1 - y2) * t2) / denom;
            } else {
                rawVertices[rawCount][0] = (x2 + x3) / 2.0;
                rawVertices[rawCount][1] = (y2 + y3) / 2.0;
            }
            rawCount++;
            prev = i;
        }
        rawVertices[rawCount][0] = segLines[prev][2];
        rawVertices[rawCount][1] = segLines[prev][3];
        rawCount++;

        double[][] cleanVertices = new double[rawCount][2];
        int cleanCount = 0;
        if (rawCount > 0) {
            cleanVertices[0][0] = rawVertices[0][0];
            cleanVertices[0][1] = rawVertices[0][1];
            cleanCount = 1;
        }

        for (int i = 1; i < rawCount; i++) {
            double cx = rawVertices[i][0];
            double cy = rawVertices[i][1];

            while (cleanCount > 1) {
                double lastX = cleanVertices[cleanCount - 1][0];
                if (cx < lastX - 1e-5) cleanCount--;
                else break;
            }

            double topX = cleanVertices[cleanCount - 1][0];
            double topY = cleanVertices[cleanCount - 1][1];
            double ddx = cx - topX, ddy = cy - topY;
            double dist = Math.sqrt(ddx * ddx + ddy * ddy);

            if (dist > 1e-4) {
                if (cx < topX + 1e-4 && cleanCount > 1) {
                    cleanVertices[cleanCount - 1][0] = (topX + cx) / 2.0;
                    cleanVertices[cleanCount - 1][1] = (topY + cy) / 2.0;
                } else {
                    cleanVertices[cleanCount][0] = cx;
                    cleanVertices[cleanCount][1] = cy;
                    cleanCount++;
                }
            }
        }

        double[][] output = new double[cleanCount][2];
        for (int i = 0; i < cleanCount; i++) {
            output[i][0] = cleanVertices[i][0];
            output[i][1] = cleanVertices[i][1];
        }
        return output;
    }

/// 构建清表面积多边形
    public static double[][] BuildClearPolygon(double[][] ground, double[][] cleared, double minX, double maxX) {
        java.util.List<double[]> list = new java.util.ArrayList<>();
        list.add(new double[]{minX, FromXgetY(ground, minX)});
        for (double[] g : ground)
            if (g[0] > minX && g[0] < maxX)
                list.add(new double[]{g[0], g[1]});
        list.add(new double[]{maxX, FromXgetY(ground, maxX)});
        list.add(new double[]{maxX, FromXgetY(cleared, maxX)});
        for (int i = cleared.length - 1; i >= 0; i--)
            if (cleared[i][0] > minX && cleared[i][0] < maxX)
                list.add(new double[]{cleared[i][0], cleared[i][1]});
        list.add(new double[]{minX, FromXgetY(cleared, minX)});

        double[][] mat = new double[list.size()][2];
        for (int i = 0; i < list.size(); i++) {
            mat[i][0] = list.get(i)[0];
            mat[i][1] = list.get(i)[1];
        }
        return mat;
    }

// ============================================================
// 5. 面积 / 几何工具
// ============================================================

/// 多边形面积（鞋带公式）
    public static double hua_PolygonArea(double[][] poly) {
        if (poly == null || poly.length < 3) return 0;
        double area = 0;
        int n = poly.length;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += poly[i][0] * poly[j][1];
            area -= poly[i][1] * poly[j][0];
        }
        return Math.abs(area) / 2.0;
    }

/// 核心填挖面积：设计线 vs 地面线求交 + 鞋带累加
/// 返回: [fill, cut, minX, maxX, minY, maxY, count, (x,y)...]
    public static double[] hua_CutAndFillArea(double[][] dmx, double[][] sjx, double extendDist) {
        if (extendDist > 0) {
            int n = dmx.length;
            if (n >= 2) {
                double x1 = dmx[0][0], y1 = dmx[0][1];
                double x2 = dmx[1][0], y2 = dmx[1][1];
                double dx = x1 - x2, dy = y1 - y2;
                double len = Math.sqrt(dx * dx + dy * dy);
                if (len > 1e-12) {
                    dx /= len;
                    dy /= len;
                    dmx[0][0] = x1 + dx * extendDist;
                    dmx[0][1] = y1 + dy * extendDist;
                }
                x1 = dmx[n - 2][0];
                y1 = dmx[n - 2][1];
                x2 = dmx[n - 1][0];
                y2 = dmx[n - 1][1];
                dx = x2 - x1;
                dy = y2 - y1;
                len = Math.sqrt(dx * dx + dy * dy);
                if (len > 1e-12) {
                    dx /= len;
                    dy /= len;
                    dmx[n - 1][0] = x2 + dx * extendDist;
                    dmx[n - 1][1] = y2 + dy * extendDist;
                }
            }
        }

        java.util.List<double[]> xys = new java.util.ArrayList<>();
        double fill = 0;
        double cut = 0;
        int lenA = sjx.length;
        int lenB = dmx.length;

        for (int i = 0; i < lenA - 1; i++) {
            double x1 = sjx[i][0], y1 = sjx[i][1];
            double x2 = sjx[i + 1][0], y2 = sjx[i + 1][1];
            for (int j = 0; j < lenB - 1; j++) {
                double x3 = dmx[j][0], y3 = dmx[j][1];
                double x4 = dmx[j + 1][0], y4 = dmx[j + 1][1];
                double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
                if (denom != 0) {
                    double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom;
                    double u = ((x1 - x3) * (y1 - y2) - (y1 - y3) * (x1 - x2)) / denom;
                    if (t >= 0 && t <= 1 && u >= 0 && u <= 1) {
                        double px = x1 + t * (x2 - x1);
                        double py = y1 + t * (y2 - y1);
                        xys.add(new double[]{px, py, (double) i, (double) j});
                    }
                }
            }
        }

        double minX = 0, maxX = 0, minY = 0, maxY = 0;
        int leftXysIdx = 0, rightXysIdx = 0;

        if (!xys.isEmpty()) {
            minX = xys.get(0)[0];
            maxX = xys.get(0)[0];
            minY = xys.get(0)[1];
            maxY = xys.get(0)[1];
            for (int i = 1; i < xys.size(); i++) {
                double x = xys.get(i)[0], y = xys.get(i)[1];
                if (x < minX) {
                    minX = x;
                    leftXysIdx = i;
                } else if (x > maxX) {
                    maxX = x;
                    rightXysIdx = i;
                }
                if (y < minY) minY = y;
                else if (y > maxY) maxY = y;
            }
        }

        for (int idx = 0; idx < xys.size() - 1; idx++) {
            double[] xy0 = xys.get(idx);
            double[] xy1 = xys.get(idx + 1);
            int i0 = (int) xy0[2], i1 = (int) xy1[2];
            int j0 = (int) xy0[3], j1 = (int) xy1[3];

            double px = xy0[0], py = xy0[1];
            double signedArea = 0;
            double nx, ny;

            for (int k = i0 + 1; k <= i1; k++) {
                nx = sjx[k][0];
                ny = sjx[k][1];
                signedArea += px * ny - py * nx;
                px = nx;
                py = ny;
            }
            signedArea += px * xy1[1] - py * xy1[0];
            px = xy1[0];
            py = xy1[1];

            for (int k = j1; k > j0; k--) {
                nx = dmx[k][0];
                ny = dmx[k][1];
                signedArea += px * ny - py * nx;
                px = nx;
                py = ny;
            }
            signedArea += px * xy0[1] - py * xy0[0];

            if (signedArea > 0) cut += signedArea / 2.0;
            else fill += signedArea / 2.0;
        }

        java.util.List<double[]> finalSjxList = new java.util.ArrayList<>();
        if (xys.size() >= 2) {
            double[] leftIntersection = xys.get(leftXysIdx);
            double[] rightIntersection = xys.get(rightXysIdx);
            int leftSjxSegIdx = (int) leftIntersection[2];
            int rightSjxSegIdx = (int) rightIntersection[2];

            finalSjxList.add(new double[]{leftIntersection[0], leftIntersection[1]});
            for (int k = leftSjxSegIdx + 1; k <= rightSjxSegIdx; k++)
                finalSjxList.add(new double[]{sjx[k][0], sjx[k][1]});
            finalSjxList.add(new double[]{rightIntersection[0], rightIntersection[1]});
        } else {
            for (int k = 0; k < lenA; k++)
                finalSjxList.add(new double[]{sjx[k][0], sjx[k][1]});
        }

        // 组装结果
        java.util.List<Double> finalResults = new java.util.ArrayList<>();
        finalResults.add(Math.round(fill * 10000) / 10000.0);
        finalResults.add(Math.round(cut * 10000) / 10000.0);
        finalResults.add(minX);
        finalResults.add(maxX);
        finalResults.add(minY);
        finalResults.add(maxY);
        finalResults.add((double) finalSjxList.size());
        for (double[] pt : finalSjxList) {
            finalResults.add(pt[0]);
            finalResults.add(pt[1]);
        }

        double[] arr = new double[finalResults.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = finalResults.get(i);
        return arr;
    }

// ============================================================
// 6. 竖曲线 / 横坡
// ============================================================

/// 设计竖曲线高程
    public static double hua_H(double[][] sqx, double k) {
        int n = sqx.length;
        if (n == 0) throw new IllegalArgumentException("设计竖曲线 wrong");
        double firstZ = sqx[0][0], lastZ = sqx[n - 1][0];
        if (k < firstZ || k > lastZ) return -1;
        if (n == 1) return Math.abs(k - firstZ) < 1e-9 ? sqx[0][1] : -1;

        int idx = -1;
        for (int i = 0; i < n - 1; i++)
            if (k >= sqx[i][0] && k <= sqx[i + 1][0]) {
                idx = i;
                break;
            }
        if (idx == -1) return -1;

        double z1 = sqx[idx][0], h1 = sqx[idx][1];
        double z2 = sqx[idx + 1][0], h2 = sqx[idx + 1][1];
        double slope = (h2 - h1) / (z2 - z1);
        double result = h1 + slope * (k - z1);

        int[] points = {idx, idx + 1};
        for (int p : points) {
            if (p <= 0 || p >= n - 1) continue;
            double r = sqx[p][2];
            if (r <= 0) continue;
            double zv = sqx[p][0], hv = sqx[p][1];
            double zPrev = sqx[p - 1][0], hPrev = sqx[p - 1][1];
            double i1 = (hv - hPrev) / (zv - zPrev);
            double zNext = sqx[p + 1][0], hNext = sqx[p + 1][1];
            double i2 = (hNext - hv) / (zNext - zv);
            double omega = i2 - i1;
            double T = r * Math.abs(omega) / 2.0;
            double start = zv - T, end = zv + T;
            if (k >= start && k <= end) {
                double tanElev = (k <= zv) ? hv + i1 * (k - zv) : hv + i2 * (k - zv);
                double x = (k <= zv) ? (k - start) : (end - k);
                double y = x * x / (2.0 * r) * Math.signum(omega);
                result = tanElev + y;
            }
        }
        return Math.round(result * 1000) / 1000.0;
    }

/// 线性插值计算横坡
    public static double hua_slope(double mileage, double[][] points) {
        if (points == null || points.length == 0) {
            throw new IllegalArgumentException("点表为空");
        }

        int n = points.length;

        // 边界处理：小于起点直接返回起点值，大于终点直接返回终点值
        if (mileage <= points[0][0]) return points[0][1];
        if (mileage >= points[n - 1][0]) return points[n - 1][1];

        // 二分查找：快速定位 mileage 所在的区间 [left, right]
        int left = 0, right = n - 1;
        while (right - left > 1) {
            int mid = (left + right) >>> 1;
            if (points[mid][0] <= mileage) {
                left = mid;
            } else {
                right = mid;
            }
        }

        // 此时 mileage 一定在 points[left] 和 points[right] 之间
        double k1 = points[left][0], s1 = points[left][1];
        double k2 = points[right][0], s2 = points[right][1];

        // 防除零（虽然你之前加了递增校验，但这里兜个底更安全）
        if (Math.abs(k2 - k1) < 1e-9) return s1;

        // 线性插值公式
        double t = (mileage - k1) / (k2 - k1);
        return s1 + t * (s2 - s1);
    }

// ============================================================
// 7. 按列排序
// ============================================================

/// 按指定列对 double[][] 升序排序
    public static void SortRowsByColumn(double[][] data, final int col) {
        if (data == null) return;
        int rows = data.length;
        if (rows <= 1) return;
        java.util.Arrays.sort(data, new java.util.Comparator<double[]>() {
            @Override
            public int compare(double[] a, double[] b) {
                return Double.compare(a[col], b[col]);
            }
        });
    }
}
