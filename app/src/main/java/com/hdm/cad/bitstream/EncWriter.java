package com.hdm.cad.bitstream;

import java.util.Arrays;

public class EncWriter {
    private byte[] data = new byte[1024];
    private int size = 0;
    private int bit = 0;

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > data.length) {
            int newCapacity = data.length * 2;
            if (newCapacity < minCapacity) newCapacity = minCapacity;
            data = Arrays.copyOf(data, newCapacity);
        }
    }

    public void writeBitsMsb(long v, int n) {
        for (int i = n - 1; i >= 0; i--) {
            int bitVal = (int) ((v >>> i) & 1);
            if (bit == 0) {
                ensureCapacity(size + 1);
                data[size++] = 0;
            }
            if (bitVal != 0) {
                data[size - 1] |= (byte) (1 << (7 - bit));
            }
            bit = (bit + 1) % 8;
        }
    }

    public void writeRC(int v) { writeBitsMsb(v & 0xFFL, 8); }
    
    public void writeRS(int v) {
        writeRC(v & 0xFF);
        writeRC((v >>> 8) & 0xFF);
    }
    
    public void writeRL(long v) {
        writeRC((int) (v & 0xFF));
        writeRC((int) ((v >>> 8) & 0xFF));
        writeRC((int) ((v >>> 16) & 0xFF));
        writeRC((int) ((v >>> 24) & 0xFF));
    }

    public void writeBLLc(long v) {
        int n = 8;
        while (n > 0 && (v >>> (8 * (n - 1))) == 0) n--;
        if (n == 0) n = 1;
        write3B(n);
        if (n == 1) writeRC((int) (v & 0xFF));
        else if (n == 2) writeRS((int) (v & 0xFFFF));
        else if (n == 4) writeRL(v & 0xFFFFFFFFL);
        else {
            for (int i = 0; i < n; i++) writeRC((int) ((v >>> (8 * i)) & 0xFF));
        }
    }

    public void writeRD(double f) {
        long bits = Double.doubleToLongBits(f);
        for (int i = 0; i < 8; i++) writeBitsMsb((bits >>> (8 * i)) & 0xFF, 8);
    }

    public void writeB(boolean v) { writeBitsMsb(v ? 1 : 0, 1); }
    public void writeBB(int v) { writeBitsMsb(v, 2); }
    public void write3B(int v) { writeBitsMsb(v, 3); }

    public void writeBS(int v) {
        if (v <= 255) {
            if (v == 0) writeBB(2);
            else { writeBB(1); writeRC(v); }
        } else if (v == 256) writeBB(3);
        else { writeBB(0); writeRS(v); }
    }

    public void writeBL(long v) {
        if (v == 0) writeBB(2);
        else if (v == 256) writeBB(3);
        else if (v <= 255) { writeBB(1); writeRC((int) v); }
        else { writeBB(0); writeRL(v); }
    }

    public void writeBLLv(long v) { writeBLLc(v); }

    public void writeBD(double f) {
        if (f == 1.0) writeBB(1);
        else if (f == 0.0) writeBB(2);
        else { writeBB(0); writeRD(f); }
    }

    public void writeDD(double v, double def) {
        byte[] nv = Utils.f64LeBytes(v);
        byte[] dv = Utils.f64LeBytes(def);
        if (Arrays.equals(nv, dv)) { writeBB(0); return; }

        boolean same68 = (nv[6] == dv[6] && nv[7] == dv[7]);
        boolean same46 = (nv[4] == dv[4] && nv[5] == dv[5]);
        
        if (same68) {
            if (same46) {
                writeBB(1);
                for (int i = 0; i < 4; i++) writeRC(nv[i]);
                return;
            }
            writeBB(2);
            int[] order = {4, 5, 0, 1, 2, 3};
            for (int i : order) writeRC(nv[i]);
            return;
        }
        writeBB(3);
        writeRD(v);
    }

    public void writeH(int code, int counter, long value) {
        writeBitsMsb(code & 0xF, 4);
        writeBitsMsb(counter & 0xF, 4);
        for (int i = counter - 1; i >= 0; i--) writeRC((int) ((value >>> (8 * i)) & 0xFF));
    }

    public void writeTV(String s) {
        byte[] b;
        try { b = s.getBytes("GBK"); } catch (Exception e) { b = s.getBytes(); }
        writeBS(b.length);
        for (byte c : b) writeRC(c & 0xFF);
    }

    public void writeTU(String s) {
        char[] chars = s.toCharArray();
        writeBS(chars.length + 1);
        for (char c : chars) writeRS(c);
        writeRS(0);
    }

    public void writeTF(byte[] b) {
        for (byte c : b) writeRC(c & 0xFF);
    }

    public void alignByte() {
        if (bit != 0) writeBitsMsb(0, 8 - bit);
    }

    public int writeCRCSeed(long startBit, int seed) {
        alignByte();
        long start = (startBit + 7) / 8;
        if (start > size) start = size;
        byte[] range = Arrays.copyOfRange(data, (int)start, size);
        int crc = Crc16Dwg.calc(seed, range);
        writeRS(crc);
        return crc;
    }

    public long tellBits() {
        if (bit != 0) return ((long) size - 1) * 8 + bit;
        return ((long) size) * 8;
    }

    public byte[] getBytes() {
        return Arrays.copyOf(data, size);
    }
}
