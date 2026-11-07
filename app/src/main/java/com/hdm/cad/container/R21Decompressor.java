package com.hdm.cad.container;

public class R21Decompressor {
    private byte[] src;
    private int at;
    private byte[] dst;
    private int put;

    public R21Decompressor(byte[] src, int dstSize) {
        this.src = src;
        this.at = 0;
        this.dst = new byte[dstSize];
        this.put = 0;
    }

    private int u8() throws Exception {
        if (at >= src.length) throw new Exception("R2007 压缩流读取越界");
        return src[at++] & 0xFF;
    }

    private int literalRun(int opcode) throws Exception {
        int length = opcode + 8;
        if (length != 0x17) return length;
        int n = u8();
        length += n;
        if (n != 0xFF) return length;
        while (true) {
            if (at + 2 > src.length) throw new Exception("R2007 字面量扩展越界");
            int lo = src[at] & 0xFF;
            int hi = src[at + 1] & 0xFF;
            at += 2;
            n = lo | (hi << 8);
            length += n;
            if (n != 0xFFFF) return length;
        }
    }

    private static class Instr {
        int nextOp, offset, length;
        Instr(int nextOp, int offset, int length) {
            this.nextOp = nextOp; this.offset = offset; this.length = length;
        }
    }

    private Instr instr(int op) throws Exception {
        int b, offset, length;
        switch (op >>> 4) {
            case 0:
                b = u8(); offset = b; b = u8();
                // 修复：加上括号，确保先左移再加 1
                return new Instr(b, (((b & 0x78) << 5) + 1) + offset, (op & 0x0F) + 0x13 + ((b >>> 3) & 0x10));
            case 1:
                b = u8(); offset = b; b = u8();
                // 修复：加上括号
                return new Instr(b, (((b & 0xF8) << 5) + 1) + offset, (op & 0x0F) + 0x03);
            case 2:
                b = u8(); offset = b; b = u8();
                offset |= (b << 8) & 0xFF00;
                length = op & 0x07;
                if ((op & 0x08) == 0) {
                    int tail = u8();
                    length += tail & 0xF8;
                    return new Instr(tail, offset, length);
                }
                offset++;
                b = u8(); length += b << 3;
                int tail = u8();
                // 修复：加上括号
                return new Instr(tail, offset, (((tail & 0xF8) << 8) + length) + 0x100);
            default:
                b = u8();
                // 修复：加上括号
                return new Instr(b, (((b & 0xF8) << 1) + (op & 0x0F)) + 1, op >>> 4);
        }
    }

    private void copyRun(int offset, int length) throws Exception {
        int srcPos = put - offset;
        if (srcPos < 0) throw new Exception("R2007 回溯偏移超前缀 (" + offset + " > " + put + ")");
        if (put + length > dst.length) throw new Exception("R2007 输出写入越界");
        for (int i = 0; i < length; i++) {
            dst[put + i] = dst[srcPos + i];
        }
        put += length;
    }

    private int[] copyRuns() throws Exception {
        int op = u8();
        Instr ins = instr(op);
        while (true) {
            copyRun(ins.offset, ins.length);
            int litPending = ins.nextOp & 0x07;
            if (litPending != 0 || at >= src.length) return new int[]{litPending, op};
            op = u8();
            if ((op >>> 4) == 0) return new int[]{0, op};
            if ((op >>> 4) == 15) op &= 0x0F;
            ins = instr(op);
        }
    }

    private static int chunkPermute(int n, int j) {
        switch (n) {
            case 2: return 1 - j;
            case 3: return 2 - j;
            case 16: return j < 8 ? j + 8 : j - 8;
            default: return j;
        }
    }

    private static final int[][][] COPY_PLANS = {
        {}, {{1, 0}}, {{2, 0}}, {{3, 0}}, {{4, 0}},
        {{1, 4}, {4, 0}}, {{1, 5}, {4, 1}, {1, 0}}, {{2, 5}, {4, 1}, {1, 0}},
        {{4, 0}, {4, 4}}, {{1, 8}, {8, 0}}, {{1, 9}, {8, 1}, {1, 0}}, {{2, 9}, {8, 1}, {1, 0}},
        {{4, 8}, {8, 0}}, {{1, 12}, {4, 8}, {8, 0}}, {{1, 13}, {4, 9}, {8, 1}, {1, 0}}, {{2, 13}, {4, 9}, {8, 1}, {1, 0}},
        {{16, 0}}, {{8, 9}, {1, 8}, {8, 0}}, {{1, 17}, {16, 1}, {1, 0}}, {{3, 16}, {16, 0}},
        {{4, 16}, {16, 0}}, {{1, 20}, {4, 16}, {16, 0}}, {{2, 20}, {4, 16}, {16, 0}}, {{3, 20}, {4, 16}, {16, 0}},
        {{8, 16}, {16, 0}}, {{8, 17}, {1, 16}, {16, 0}}, {{1, 25}, {8, 17}, {1, 16}, {16, 0}}, {{2, 25}, {8, 17}, {1, 16}, {16, 0}},
        {{4, 24}, {8, 16}, {16, 0}}, {{1, 28}, {4, 24}, {8, 16}, {16, 0}}, {{2, 28}, {4, 24}, {8, 16}, {16, 0}},
        {{1, 30}, {4, 26}, {8, 18}, {16, 2}, {2, 0}}
    };

    private void emitBlock(int srcAt, int n) throws Exception {
        for (int j = 0; j < n; j++) {
            int si = srcAt + chunkPermute(n, j);
            int o = put + j;
            if (si >= src.length || o >= dst.length) throw new Exception("R2007 拷贝越界");
            dst[o] = src[si];
        }
        put += n;
    }

    private void emitChunk(int srcAt, int length) throws Exception {
        while (length >= 32) {
            emitBlock(srcAt + 16, 16);
            emitBlock(srcAt, 16);
            srcAt += 32;
            length -= 32;
        }
        for (int[] step : COPY_PLANS[length]) {
            emitBlock(srcAt + step[1], step[0]);
        }
    }

    public static byte[] decompress(byte[] src, int dstSize) throws Exception {
        if (dstSize == 0) return new byte[0];
        if (src.length == 0) throw new Exception("R2007 压缩流为空");
        R21Decompressor d = new R21Decompressor(src, dstSize);

        int lead = d.u8();
        int litOpcode = lead;
        int litPending = 0;
        if ((lead & 0xF0) == 0x20) {
            d.at += 2;
            if (d.at >= d.src.length) throw new Exception("R2007 opcode 引导越界");
            litPending = d.src[d.at++] & 0x07;
        }

        while (d.at < d.src.length) {
            int length = litPending;
            if (length == 0) length = d.literalRun(litOpcode);
            if (d.put + length > dstSize) break;
            
            d.emitChunk(d.at, length);
            d.at += length;
            if (d.at >= d.src.length) break;
            
            int[] next = d.copyRuns();
            litPending = next[0];
            litOpcode = next[1];
        }
        return d.dst;
    }
}
