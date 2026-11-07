package com.hdm.cad.bitstream;

import java.nio.charset.StandardCharsets;

public class Utils {
    public static double leF64(byte[] b) {
        long bits = (b[0] & 0xFFL) | ((b[1] & 0xFFL) << 8) | ((b[2] & 0xFFL) << 16) | ((b[3] & 0xFFL) << 24) |
                    ((b[4] & 0xFFL) << 32) | ((b[5] & 0xFFL) << 40) | ((b[6] & 0xFFL) << 48) | ((b[7] & 0xFFL) << 56);
        return Double.longBitsToDouble(bits);
    }

    public static byte[] f64LeBytes(double f) {
        long bits = Double.doubleToLongBits(f);
        byte[] b = new byte[8];
        for (int i = 0; i < 8; i++) {
            b[i] = (byte) (bits >> (i * 8));
        }
        return b;
    }

    public static String decodeCodepage(byte[] b, int codepage) {
        if (b == null || b.length == 0) return "";
        try {
            if (codepage == 0 || codepage == 1) {
                return new String(b, StandardCharsets.US_ASCII);
            } else if (codepage == 31 || codepage == 39) {
                return new String(b, "GBK");
            } else if (codepage == 30 || codepage == 2 || codepage == 11 || codepage == 12) {
                return new String(b, StandardCharsets.ISO_8859_1);
            }
        } catch (Exception e) {}
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    public static String utf16Decode(int[] units) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < units.length; i++) {
            int u = units[i] & 0xFFFF;
            if (u >= 0xD800 && u < 0xDC00 && i + 1 < units.length) {
                int u2 = units[i + 1] & 0xFFFF;
                if (u2 >= 0xDC00 && u2 < 0xE000) {
                    sb.appendCodePoint((((u - 0xD800) << 10) | (u2 - 0xDC00)) + 0x10000);
                    i++;
                    continue;
                }
                sb.append('\uFFFD');
            } else if (u >= 0xD800 && u < 0xE000) {
                sb.append('\uFFFD');
            } else {
                sb.append((char) u);
            }
        }
        return sb.toString();
    }
}
