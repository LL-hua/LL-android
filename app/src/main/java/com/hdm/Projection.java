package com.hdm;

import java.util.ArrayList;
import java.util.List;

/** 投影 + 四参数转换 方法名保持"原代码风格"。 */
public class Projection {

    // ============================================================
    // 1. 高斯投影
    // ============================================================
    public static double[] gauss_proj(double L, double B, double lonCenter) {
        double pi = 3.141592653589793238463;
        double p0 = 206264.8062470963551564;
        double e = 0.00669438002290;
        double e1 = 0.00673949677548;
        double b = 6356752.3141;
        double a = 6378137.0;

        B = B * pi / 180;
        L = L * pi / 180;

        double L_num;
        double L_center;
        if (lonCenter >= 359) {
            L_num = Math.floor(L * 180 / pi / 3.0 + 0.5);
            L_center = 3 * L_num;
        } else {
            L_center = lonCenter;
        }

        double l = (L * 180 / pi - L_center) * 3600;

        double M0 = a * (1 - e);
        double M2 = 3.0 / 2.0 * e * M0;
        double M4 = 5.0 / 4.0 * e * M2;
        double M6 = 7.0 / 6.0 * e * M4;
        double M8 = 9.0 / 8.0 * e * M6;

        double a0 = M0 + M2 / 2.0 + 3.0 / 8.0 * M4 + 5.0 / 16.0 * M6 + 35.0 / 128.0 * M8;
        double a2 = M2 / 2.0 + M4 / 2 + 15.0 / 32.0 * M6 + 7.0 / 16.0 * M8;
        double a4 = M4 / 8.0 + 3.0 / 16.0 * M6 + 7.0 / 32.0 * M8;
        double a6 = M6 / 32.0 + M8 / 16.0;
        double a8 = M8 / 128.0;

        double Xz = a0 * B - a2 / 2.0 * Math.sin(2 * B) + a4 / 4.0 * Math.sin(4 * B)
                - a6 / 6.0 * Math.sin(6 * B) + a8 / 8.0 * Math.sin(8 * B);

        double c = a * a / b;
        double V = Math.sqrt(1 + e1 * Math.cos(B) * Math.cos(B));
        double N = c / V;
        double t = Math.tan(B);
        double n = e1 * Math.cos(B) * Math.cos(B);

        double m1 = N * Math.cos(B);
        double m2 = N / 2.0 * Math.sin(B) * Math.cos(B);
        double m3 = N / 6.0 * Math.pow(Math.cos(B), 3) * (1 - t * t + n);
        double m4 = N / 24.0 * Math.sin(B) * Math.pow(Math.cos(B), 3) * (5 - t * t + 9 * n);
        double m5 = N / 120.0 * Math.pow(Math.cos(B), 5)
                * (5 - 18 * t * t + Math.pow(t, 4) + 14 * n - 58 * n * t * t);
        double m6 = N / 720.0 * Math.sin(B) * Math.pow(Math.cos(B), 5)
                * (61 - 58 * t * t + Math.pow(t, 4));

        double x = Xz + m2 * l * l / Math.pow(p0, 2)
                + m4 * Math.pow(l, 4) / Math.pow(p0, 4)
                + m6 * Math.pow(l, 6) / Math.pow(p0, 6);
        double y0 = m1 * l / p0 + m3 * Math.pow(l, 3) / Math.pow(p0, 3)
                + m5 * Math.pow(l, 5) / Math.pow(p0, 5);
        double y = y0 + 500000;

        return new double[]{x, y, L_center};
    }

    public static double[] gauss_unproj(double x, double y, double l0) {
        double pi = 3.141592653589793238463;
        double e = 0.00669438002290;
        double e1 = 0.00673949677548;
        double b = 6356752.3141;
        double a = 6378137.0;

        double y1 = y - 500000;

        double M0 = a * (1 - e);
        double M2 = 3.0 / 2.0 * e * M0;
        double M4 = 5.0 / 4.0 * e * M2;
        double M6 = 7.0 / 6.0 * e * M4;
        double M8 = 9.0 / 8.0 * e * M6;

        double a0 = M0 + M2 / 2.0 + 3.0 / 8.0 * M4 + 5.0 / 16.0 * M6 + 35.0 / 128.0 * M8;
        double a2 = M2 / 2.0 + M4 / 2 + 15.0 / 32.0 * M6 + 7.0 / 16.0 * M8;
        double a4 = M4 / 8.0 + 3.0 / 16.0 * M6 + 7.0 / 32.0 * M8;
        double a6 = M6 / 32.0 + M8 / 16.0;

        double Bf = x / a0;
        double B0 = Bf;
        do {
            B0 = Bf;
            double FBf = -a2 / 2.0 * Math.sin(2 * B0) + a4 / 4.0 * Math.sin(4 * B0)
                    - a6 / 6.0 * Math.sin(6 * B0);
            Bf = (x - FBf) / a0;
        } while (Math.abs(Bf - B0) > 0.0000001);

        double t = Math.tan(Bf);
        double c = a * a / b;
        double V = Math.sqrt(1 + e1 * Math.cos(Bf) * Math.cos(Bf));
        double N = c / V;
        double M = c / Math.pow(V, 3);
        double n = e1 * Math.cos(Bf) * Math.cos(Bf);

        double n1 = 1 / (N * Math.cos(Bf));
        double n2 = -t / (2.0 * M * N);
        double n3 = -(1 + 2 * t * t + n) / (6.0 * Math.pow(N, 3) * Math.cos(Bf));
        double n4 = t * (5 + 3 * t * t + n - 9 * n * t * t) / (24.0 * M * Math.pow(N, 3));
        double n5 = (5 + 28 * t * t + 24 * Math.pow(t, 4) + 6 * n + 8 * n * t * t)
                / (120.0 * Math.pow(N, 5) * Math.cos(Bf));
        double n6 = -t * (61 + 90 * t * t + 45 * Math.pow(t, 4)) / (720.0 * M * Math.pow(N, 5));

        double B = (Bf + n2 * y1 * y1 + n4 * Math.pow(y1, 4) + n6 * Math.pow(y1, 6)) / pi * 180;
        double L0 = l0;
        double l = n1 * y1 + n3 * Math.pow(y1, 3) + n5 * Math.pow(y1, 5);
        double L = L0 + l / pi * 180;

        return new double[]{L, B};
    }

    // ============================================================
    // 2. UTM 投影
    // ============================================================
    public static double[] utm_proj(double longitude, double latitude) {
        double EQUATORIAL_RADIUS = 6378137.0;
        double FLATTENING = 1 / 298.257223563;
        double ECC_SQUARED = 2 * FLATTENING - Math.pow(FLATTENING, 2);
        double ECC_PRIME_SQUARED = ECC_SQUARED / (1 - ECC_SQUARED);
        double SCALE_FACTOR = 0.9996;
        double FALSE_EASTING = 500000.0;
        double FALSE_NORTHING_S = 10000000.0;

        int zoneNumber = (int) Math.floor((longitude + 180) / 6) + 1;
        double centralMeridian = (zoneNumber - 1) * 6 - 180 + 3;

        double latRad = latitude * Math.PI / 180.0;
        double lonRad = longitude * Math.PI / 180.0;
        double lonCenterRad = centralMeridian * Math.PI / 180.0;

        double N = EQUATORIAL_RADIUS
                / Math.sqrt(1 - ECC_SQUARED * Math.pow(Math.sin(latRad), 2));
        double T = Math.pow(Math.tan(latRad), 2);
        double C = ECC_PRIME_SQUARED * Math.pow(Math.cos(latRad), 2);
        double A = (lonRad - lonCenterRad) * Math.cos(latRad);

        double M = EQUATORIAL_RADIUS
                * ((1 - ECC_SQUARED / 4 - 3 * Math.pow(ECC_SQUARED, 2) / 64
                                        - 5 * Math.pow(ECC_SQUARED, 3) / 256) * latRad
                        - (3 * ECC_SQUARED / 8 + 3 * Math.pow(ECC_SQUARED, 2) / 32
                                        + 45 * Math.pow(ECC_SQUARED, 3) / 1024) * Math.sin(2 * latRad)
                        + (15 * Math.pow(ECC_SQUARED, 2) / 256
                                        + 45 * Math.pow(ECC_SQUARED, 3) / 1024) * Math.sin(4 * latRad)
                        - (35 * Math.pow(ECC_SQUARED, 3) / 3072) * Math.sin(6 * latRad));

        double easting = SCALE_FACTOR * N * (A
                                + (1 - T + C) * Math.pow(A, 3) / 6
                                + (5 - 18 * T + Math.pow(T, 2) + 72 * C - 58 * ECC_PRIME_SQUARED)
                                        * Math.pow(A, 5) / 120) + FALSE_EASTING;

        double northing = SCALE_FACTOR * (M
                        + N * Math.tan(latRad) * (Math.pow(A, 2) / 2
                                        + (5 - T + 9 * C + 4 * Math.pow(C, 2)) * Math.pow(A, 4) / 24
                                        + (61 - 58 * T + Math.pow(T, 2) + 600 * C - 330 * ECC_PRIME_SQUARED)
                                                * Math.pow(A, 6) / 720));

        if (latitude < 0) {
            northing += FALSE_NORTHING_S;
        }

        return new double[]{northing, easting, zoneNumber};
    }

    public static double[] utm_unproj(double northing, double easting,
            boolean isNorthern, int zoneNumber) {
        double EQUATORIAL_RADIUS = 6378137.0;
        double FLATTENING = 1 / 298.257223563;
        double ECC_SQUARED = 2 * FLATTENING - Math.pow(FLATTENING, 2);
        double ECC_PRIME_SQUARED = ECC_SQUARED / (1 - ECC_SQUARED);
        double SCALE_FACTOR = 0.9996;
        double FALSE_EASTING = 500000.0;
        double FALSE_NORTHING_S = 10000000.0;

        double x = easting - FALSE_EASTING;
        double y = isNorthern ? northing : northing - FALSE_NORTHING_S;

        double centralMeridian = (zoneNumber - 1) * 6 - 180 + 3;
        double lonCenterRad = centralMeridian * Math.PI / 180.0;

        double M = y / SCALE_FACTOR;
        double mu = M / (EQUATORIAL_RADIUS * (1 - ECC_SQUARED / 4
                                - 3 * Math.pow(ECC_SQUARED, 2) / 64.0
                                - 5 * Math.pow(ECC_SQUARED, 3) / 256.0));

        double e1 = (1 - Math.sqrt(1 - ECC_SQUARED)) / (1 + Math.sqrt(1 - ECC_SQUARED));

        double phi1Rad = mu
                + (3 * e1 / 2 - 27 * Math.pow(e1, 3) / 32) * Math.sin(2 * mu)
                + (21 * Math.pow(e1, 2) / 16 - 55 * Math.pow(e1, 4) / 32) * Math.sin(4 * mu)
                + (151 * Math.pow(e1, 3) / 96) * Math.sin(6 * mu);

        double N1 = EQUATORIAL_RADIUS
                / Math.sqrt(1 - ECC_SQUARED * Math.pow(Math.sin(phi1Rad), 2));
        double T1 = Math.pow(Math.tan(phi1Rad), 2);
        double C1 = ECC_PRIME_SQUARED * Math.pow(Math.cos(phi1Rad), 2);
        double R1 = EQUATORIAL_RADIUS * (1 - ECC_SQUARED)
                / Math.pow(1 - ECC_SQUARED * Math.pow(Math.sin(phi1Rad), 2), 1.5);

        double D = x / (N1 * SCALE_FACTOR);

        double latRad = phi1Rad - (N1 * Math.tan(phi1Rad) / R1) * (Math.pow(D, 2) / 2
                                - (5 + 3 * T1 + 10 * C1 - 4 * Math.pow(C1, 2)
                                                - 9 * ECC_PRIME_SQUARED) * Math.pow(D, 4) / 24
                                + (61 + 90 * T1 + 298 * C1 + 45 * Math.pow(T1, 2)
                                                - 252 * ECC_PRIME_SQUARED - 3 * Math.pow(C1, 2))
                                        * Math.pow(D, 6) / 720);

        double lonRad = lonCenterRad
                + (D - (1 + 2 * T1 + C1) * Math.pow(D, 3) / 6
                                + (5 - 2 * C1 + 28 * T1 - 3 * Math.pow(C1, 2)
                                                + 8 * ECC_PRIME_SQUARED + 24 * Math.pow(T1, 2))
                                        * Math.pow(D, 5) / 120) / Math.cos(phi1Rad);

        double longitude = lonRad * 180 / Math.PI;
        double latitude = latRad * 180 / Math.PI;

        return new double[]{longitude, latitude};
    }

    public static int utm_zone(double longitude) {
        return (int) ((longitude + 180) / 6) + 1;
    }

    // ============================================================
    // 3. Web 墨卡托
    // ============================================================
    public static double[] web_proj(double lon, double lat) {
        double x = lon * 20037508.34 / 180;
        double y = Math.log(Math.tan((90 + lat) * Math.PI / 360)) / (Math.PI / 180);
        y = y * 20037508.34 / 180;
        return new double[]{x, y};
    }

    public static double[] web_unproj(double x, double y) {
        double lon = x / 20037508.34 * 180;
        double lat = 180 / Math.PI
                * (2 * Math.atan(Math.exp(y / 20037508.34 * Math.PI * 180)) - Math.PI / 2);
        return new double[]{lon, lat};
    }

    // ============================================================
    // 4. 四参数
    // ============================================================
    public static double[] cs4(double[] source, double[] target) {
        if (source == null || target == null || source.length != target.length) {
            return new double[]{0, 0, 0, 1};
        }
        if (source.length < 4 || source.length % 2 != 0) {
            return new double[]{0, 0, 0, 1};
        }

        int pointCount = source.length / 2;

        double sumX1 = 0, sumY1 = 0, sumX2 = 0, sumY2 = 0;
        for (int i = 0; i < pointCount; i++) {
            sumX1 += source[2 * i];
            sumY1 += source[2 * i + 1];
            sumX2 += target[2 * i];
            sumY2 += target[2 * i + 1];
        }

        double meanX1 = sumX1 / pointCount;
        double meanY1 = sumY1 / pointCount;
        double meanX2 = sumX2 / pointCount;
        double meanY2 = sumY2 / pointCount;

        double[] centeredSource = new double[source.length];
        double[] centeredTarget = new double[target.length];

        for (int i = 0; i < pointCount; i++) {
            centeredSource[2 * i] = source[2 * i] - meanX1;
            centeredSource[2 * i + 1] = source[2 * i + 1] - meanY1;
            centeredTarget[2 * i] = target[2 * i] - meanX2;
            centeredTarget[2 * i + 1] = target[2 * i + 1] - meanY2;
        }

        double H11 = 0, H12 = 0, H21 = 0, H22 = 0;
        double B1 = 0, B2 = 0;

        for (int i = 0; i < pointCount; i++) {
            double x1 = centeredSource[2 * i];
            double y1 = centeredSource[2 * i + 1];
            double x2 = centeredTarget[2 * i];
            double y2 = centeredTarget[2 * i + 1];

            H11 += x1 * x1 + y1 * y1;
            H12 += 0;
            H21 += 0;
            H22 += x1 * x1 + y1 * y1;

            B1 += x1 * x2 + y1 * y2;
            B2 += x1 * y2 - y1 * x2;
        }

        double det = H11 * H22 - H12 * H21;
        if (Math.abs(det) < 1e-15) {
            return new double[]{0, 0, 0, 1};
        }

        double a = (H22 * B1 - H12 * B2) / det;
        double b = (-H21 * B1 + H11 * B2) / det;

        double scale = Math.sqrt(a * a + b * b);
        double rotation = Math.atan2(b, a);

        double deltaX = meanX2 - (a * meanX1 - b * meanY1);
        double deltaY = meanY2 - (b * meanX1 + a * meanY1);

        return new double[]{deltaX, deltaY, rotation, scale};
    }

    public static double[] fourParameterTransform(double x, double y,
            double deltaX, double deltaY,
            double rotation, double scale) {
        double convertedX = scale * (x * Math.cos(rotation) - y * Math.sin(rotation)) + deltaX;
        double convertedY = scale * (x * Math.sin(rotation) + y * Math.cos(rotation)) + deltaY;
        return new double[]{convertedX, convertedY};
    }

    // ============================================================
    // 5. GPS → 当地 XY（组合）
    // ============================================================
    /**
     * @param lon 经度
     * @param lat 纬度
     * @param proj "gauss" / "utm"
     * @param ctrlLonLat 控制点经纬度 N×2
     * @param ctrlXy 控制点 XY N×2
     * @param center 中央子午线（高斯用）
     * @return [X, Y]
     */
    public static double[] gps2Xy(double lon, double lat, String proj,
            double[][] ctrlLonLat, double[][] ctrlXy,
            double center) {
        String p = proj == null ? "" : proj.toLowerCase();

        if (p.contains("gao")) {
            int n = ctrlLonLat.length;
            double[] sourceXy = new double[n * 2];
            double[] targetXy = new double[n * 2];
            for (int i = 0; i < n; i++) {
                double[] ls = gauss_proj(ctrlLonLat[i][0], ctrlLonLat[i][1], center);
                sourceXy[i * 2] = ls[0];
                sourceXy[i * 2 + 1] = ls[1];
                targetXy[i * 2] = ctrlXy[i][0];
                targetXy[i * 2 + 1] = ctrlXy[i][1];
            }
            double[] cs4 = cs4(sourceXy, targetXy);
            double[] lsxy = gauss_proj(lon, lat, center);
            return fourParameterTransform(lsxy[0], lsxy[1],
            cs4[0], cs4[1], cs4[2], cs4[3]);
        }

        if (p.contains("utm")) {
            int n = ctrlLonLat.length;
            double[] sourceXy = new double[n * 2];
            double[] targetXy = new double[n * 2];
            for (int i = 0; i < n; i++) {
                double[] ls = utm_proj(ctrlLonLat[i][0], ctrlLonLat[i][1]);
                // utm_proj 返回 [northing, easting, zone]
                sourceXy[i * 2] = ls[0]; // northing → X
                sourceXy[i * 2 + 1] = ls[1]; // easting  → Y
                targetXy[i * 2] = ctrlXy[i][0];
                targetXy[i * 2 + 1] = ctrlXy[i][1];
            }
            double[] cs4 = cs4(sourceXy, targetXy);
            double[] lsxy = utm_proj(lon, lat);
            return fourParameterTransform(lsxy[0], lsxy[1],
            cs4[0], cs4[1], cs4[2], cs4[3]);
        }

        return new double[]{0, 0};
    }
}