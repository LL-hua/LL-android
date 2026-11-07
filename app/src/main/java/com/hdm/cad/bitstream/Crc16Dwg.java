package com.hdm.cad.bitstream;

public class Crc16Dwg {
    private static final int[] CRC_TABLE = new int[256];
    static {
        for (int i = 0; i < 256; i++) {
            int c = i;
            for (int j = 0; j < 8; j++) {
                if ((c & 1) != 0) c = (c >> 1) ^ 0xA001;
                else c >>= 1;
            }
            CRC_TABLE[i] = c;
        }
    }

    public static int calc(int seed, byte[] data, int offset, int length) {
        int crc = seed & 0xFFFF;
        for (int i = offset; i < offset + length; i++) {
            crc = (crc >>> 8) ^ CRC_TABLE[(crc ^ data[i]) & 0xFF];
        }
        return crc & 0xFFFF;
    }

    public static int calc(int seed, byte[] data) {
        return calc(seed, data, 0, data.length);
    }
}
