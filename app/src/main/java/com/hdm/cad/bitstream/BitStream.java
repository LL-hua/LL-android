package com.hdm.cad.bitstream;

import java.io.EOFException;

public class BitStream {
    public byte[] src;
    public int pos;
    public int sub;
    public boolean legacyBT;
    private final int textLimit = 1 << 20;

    public BitStream(byte[] data) {
        this.src = data;
        this.pos = 0;
        this.sub = 0;
        this.legacyBT = false;
    }

    public long tellBits() {
        return ((long) pos) * 8 + sub;
    }

    public long totalBits() {
        return ((long) src.length) * 8;
    }

    public int[] cursor() {
        return new int[]{pos, sub};
    }

    public void restore(int byteIdx, int bitIdx) {
        if (bitIdx > 7) bitIdx = 7;
        this.pos = byteIdx;
        this.sub = bitIdx;
    }

    public void setBitPos(long bitPos) {
        this.pos = (int) (bitPos / 8);
        this.sub = (int) (bitPos % 8);
    }

    public void alignByte() {
        if (sub != 0) {
            sub = 0;
            pos++;
        }
    }

    public int readB() throws EOFException {
        if (pos >= src.length) throw new EOFException("bitstream: 意外结束");
        int v = ((src[pos] & 0xFF) & (0x80 >>> sub)) >>> (7 - sub);
        if (sub == 7) {
            sub = 0;
            pos++;
        } else {
            sub++;
        }
        return v;
    }

    public int readBB() throws EOFException {
        return (int) readBitsMsb(2);
    }

    public int read3B() throws EOFException {
        return (int) readBitsMsb(3);
    }

    public long readBitsMsb(int n) throws EOFException {
        if (n > 64) throw new IllegalArgumentException("bitstream: 位读取宽度超限 " + n);
        long value = 0;
        int remaining = n;

        if (sub != 0 && remaining > 0) {
            if (pos >= src.length) throw new EOFException("bitstream: 意外结束");
            int avail = 8 - sub;
            int take = Math.min(avail, remaining);
            long seg = ((src[pos] & 0xFF) >>> (avail - take)) & ((1L << take) - 1);
            value = (value << take) | seg;
            remaining -= take;
            sub += take;
            if (sub == 8) {
                sub = 0;
                pos++;
            }
        }

        while (remaining >= 8) {
            if (pos >= src.length) throw new EOFException("bitstream: 意外结束");
            value = (value << 8) | (src[pos] & 0xFFL);
            pos++;
            remaining -= 8;
        }

        while (remaining > 0) {
            if (pos >= src.length) throw new EOFException("bitstream: 意外结束");
            value = (value << 1) | (((src[pos] & 0xFF) >>> (7 - sub)) & 1L);
            if (sub == 7) {
                sub = 0;
                pos++;
            } else {
                sub++;
            }
            remaining--;
        }
        return value;
    }

    public int readRC() throws EOFException {
        if (sub == 0) {
            if (pos >= src.length) throw new EOFException("bitstream: 意外结束");
            int b = src[pos] & 0xFF;
            pos++;
            return b;
        }
        if (pos >= src.length) throw new EOFException("bitstream: 意外结束");
        int joined = ((src[pos] & 0xFF) << sub);
        if (pos + 1 < src.length) {
            joined |= ((src[pos + 1] & 0xFF) >>> (8 - sub));
        }
        pos++;
        return joined & 0xFF;
    }

    public byte[] collectRaw(int n) throws EOFException {
        if (sub == 0) {
            if (pos + n > src.length) throw new EOFException("bitstream: 意外结束");
            byte[] out = new byte[n];
            System.arraycopy(src, pos, out, 0, n);
            pos += n;
            return out;
        }
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) out[i] = (byte) readRC();
        return out;
    }

    public byte[] readRCS(int count) throws EOFException {
        if (count == 0) return new byte[0];
        return collectRaw(count);
    }

    public int readRS() throws EOFException {
        int lo = readRC();
        int hi = readRC();
        return (hi << 8) | lo;
    }

    public long readRL() throws EOFException {
        int low = readRS();
        int high = readRS();
        return (((long) high) << 16) | low;
    }

    public double readRD() throws EOFException {
        byte[] raw = collectRaw(8);
        return Utils.leF64(raw);
    }

    public double readBD() throws EOFException {
        int prefix = readBB();
        if (prefix == 0) return readRD();
        if (prefix == 1) return 1.0;
        if (prefix == 2) return 0.0;
        return 0.0;
    }

    public double[] read3BD() throws EOFException {
        return new double[]{readBD(), readBD(), readBD()};
    }

    public double readDD(double def) throws EOFException {
        int prefix = readBB();
        if (prefix == 0) return def;
        if (prefix == 3) return readRD();

        byte[] data = Utils.f64LeBytes(def);
        if (prefix == 1) {
            for (int i = 0; i < 4; i++) data[i] = (byte) readRC();
        } else if (prefix == 2) {
            for (int i = 4; i < 6; i++) data[i] = (byte) readRC();
            for (int i = 0; i < 4; i++) data[i] = (byte) readRC();
        }
        return Utils.leF64(data);
    }

    public double readBT() throws EOFException {
        if (legacyBT) return readBD();
        int mode = readB();
        if (mode == 1) return 0.0;
        return readBD();
    }

    public double[] readBE() throws EOFException {
        if (legacyBT) return read3BD();
        int flag = readB();
        if (flag == 1) return new double[]{0.0, 0.0, 1.0};
        return read3BD();
    }

    public int readBS() throws EOFException {
        int prefix = readBB();
        if (prefix == 0) return readRS();
        if (prefix == 1) return readRC();
        if (prefix == 3) return 256;
        return 0;
    }

    public long readBL() throws EOFException {
        int prefix = readBB();
        if (prefix == 0) return readRL();
        if (prefix == 1) return readRC();
        if (prefix == 3) return 256;
        return 0;
    }

    public long readBLL() throws EOFException {
        int length = read3B();
        if (length == 1) return readRC();
        if (length == 2) return readRS();
        if (length == 4) return readRL();

        long value = 0;
        for (int i = 0; i < length; i++) {
            value |= ((long) readRC()) << (8 * i);
        }
        return value;
    }

    public long readMS() throws EOFException {
        int first = readRS();
        long value = first & 0x7FFF;
        if ((first & 0x8000) == 0) return value;
        int second = readRS();
        return value | (((long) (second & 0x7FFF)) << 15);
    }

    public long readUMC() throws EOFException {
        long value = 0;
        int shift = 0;
        while (true) {
            int b = readRC();
            value |= ((long) (b & 0x7F)) << shift;
            if ((b & 0x80) == 0) return value;
            if (shift >= 28) return value;
            shift += 7;
        }
    }

    public int readOT() throws EOFException {
        int opcode = readBB();
        if (opcode == 0) return readRC();
        if (opcode == 1) return readRC() + 0x01F0;
        return readRS();
    }

    public HandleRef readH() throws EOFException {
        int b = readRC();
        HandleRef ref = new HandleRef(b >>> 4, b & 0x0F, 0);
        if (ref.counter > 4) throw new IllegalArgumentException("bitstream: 非法句柄字节数 " + ref.counter);
        for (int i = 0; i < ref.counter; i++) {
            ref.value = (ref.value << 8) | readRC();
        }
        return ref;
    }

    public byte[] stripNUL(byte[] raw) {
        int count = 0;
        for (byte b : raw) if (b != 0) count++;
        if (count == raw.length) return raw;
        byte[] out = new byte[count];
        int idx = 0;
        for (byte b : raw) {
            if (b != 0) out[idx++] = b;
        }
        return out;
    }

    public String readTV(int codepage) throws EOFException {
        int length = readBS();
        if (length > textLimit) throw new IllegalArgumentException("bitstream: TV 文本长度异常 " + length);
        byte[] raw = collectRaw(length);
        return Utils.decodeCodepage(stripNUL(raw), codepage);
    }

    public String readTU() throws EOFException {
        int length = readBS();
        if (length > textLimit) throw new IllegalArgumentException("bitstream: TU 文本长度异常 " + length);
        byte[] raw = collectRaw(length * 2);
        int[] units = new int[length];
        for (int i = 0; i < length; i++) {
            units[i] = (raw[2 * i] & 0xFF) | ((raw[2 * i + 1] & 0xFF) << 8);
        }
        return Utils.utf16Decode(units);
    }

    public int readCRC() throws EOFException {
        if (sub > 0) restore(pos + 1, 0);
        return readRS();
    }
}
