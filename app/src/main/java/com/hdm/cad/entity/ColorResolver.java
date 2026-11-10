package com.hdm.cad.entity;

public class ColorResolver {

    private static final int[][] ACI_EXTENDED_TABLE = new int[240][3];
    private static final int[][] ACI_STANDARD_TABLE = {
        {0, 0, 0}, {255, 0, 0}, {255, 255, 0}, {0, 255, 0}, {0, 255, 255},
        {0, 0, 255}, {255, 0, 255}, {0, 0, 0}, {128, 128, 128}, {192, 192, 192}
    };

    static {
        for (int slot = 0; slot < 240; slot++) {
            int step = slot % 24;
            int band = slot / 24;
            double hue = (double) step / 24.0;
            double sat = 1.0;
            if (band >= 5) sat = 0.7;
            double val = 1.0 - (double) band * 0.08;
            if (val < 0.28) val = 0.28;
            int[] rgb = hsvTint(hue, sat, val);
            ACI_EXTENDED_TABLE[slot][0] = rgb[0];
            ACI_EXTENDED_TABLE[slot][1] = rgb[1];
            ACI_EXTENDED_TABLE[slot][2] = rgb[2];
        }
    }

    public static int[] hsvTint(double h, double s, double v) {
        int sector = (int) (h * 6) % 6;
        double frac = h * 6 - (int) (h * 6);
        double p = v * (1 - s), q = v * (1 - frac * s), t = v * (1 - (1 - frac) * s);
        double r, g, b;
        switch (sector) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return new int[]{(int) (r * 255 + 0.5), (int) (g * 255 + 0.5), (int) (b * 255 + 0.5)};
    }

    /**
     * 严格还原 AutoCAD 的 ACI 颜色
     * @param backgroundIsWhite 如果为 true，索引 7 (白色) 会被翻转为黑色
     */
    public static int[] aciColor(int index, boolean backgroundIsWhite) {
        if (index == 7) {
            return backgroundIsWhite ? new int[]{0, 0, 0, 1} : new int[]{255, 255, 255, 1};
        }
        if (index >= 1 && index <= 9) {
            return new int[]{ACI_STANDARD_TABLE[index][0], ACI_STANDARD_TABLE[index][1], ACI_STANDARD_TABLE[index][2], 1};
        }
        if (index >= 10 && index <= 249) {
            int[] c = ACI_EXTENDED_TABLE[index - 10];
            return new int[]{c[0], c[1], c[2], 1};
        }
        if (index >= 250 && index <= 255) {
            int gray = (int) (255.0 * (index - 250) / 5.0);
            return new int[]{gray, gray, gray, 1};
        }
        return new int[]{0, 0, 0, 0};
    }

    public static int[] splitTrueColor(long rgb) {
        return new int[]{(int) ((rgb >> 16) & 0xFF), (int) ((rgb >> 8) & 0xFF), (int) (rgb & 0xFF)};
    }
}