package com.hdm.cad.container;

public class Lz77Decompressor {
    private byte[] src;
    private int at;

    public Lz77Decompressor(byte[] src) {
        this.src = src;
        this.at = 0;
    }

    private int take() throws Exception {
        if (at >= src.length) throw new Exception("cad: 压缩流意外结束");
        return src[at++] & 0xFF;
    }

    private int[] splitWord() throws Exception {
        int b1 = take();
        int b2 = take();
        int offset = (b1 >>> 2) | (b2 << 6);
        int litCount = b1 & 0x03;
        return new int[]{offset, litCount};
    }

    private int extendedCount() throws Exception {
        int b = take();
        int total = 0;
        if (b == 0x00) {
            total = 0xFF;
            while (true) {
                int next = take();
                if (next != 0x00) {
                    b = next;
                    break;
                }
                total += 0xFF;
            }
        }
        return total + b;
    }

    private int[] literalRun() throws Exception {
        int b = take();
        if (b >= 0x01 && b <= 0x0F) return new int[]{b + 3, 0};
        if ((b & 0xF0) != 0) return new int[]{0, b};
        if (b == 0x00) {
            int length = 0x0F;
            while (true) {
                int next = take();
                if (next != 0x00) return new int[]{length + next + 3, 0};
                length += 0xFF;
            }
        }
        return new int[]{0, 0};
    }

    private static class Lz77Form {
        int lo, hi, lenBase, opMask, offBase;
        boolean ext;
        Lz77Form(int lo, int hi, int lenBase, int opMask, boolean ext, int offBase) {
            this.lo = lo; this.hi = hi; this.lenBase = lenBase;
            this.opMask = opMask; this.ext = ext; this.offBase = offBase;
        }
    }

    private static final Lz77Form[] FORMS = {
        new Lz77Form(0x10, 0x10, 9, 0, true, 0x3FFF),
        new Lz77Form(0x12, 0x1F, 2, 0x0F, false, 0x3FFF),
        new Lz77Form(0x20, 0x20, 0x21, 0, true, 0),
        new Lz77Form(0x21, 0x3F, -0x1E, 0xFF, false, 0)
    };

    private Lz77Form formOf(int op) {
        for (Lz77Form f : FORMS) {
            if (op >= f.lo && op <= f.hi) return f;
        }
        return null;
    }

    public static byte[] decompress(byte[] src, int dstSize) throws Exception {
        if (dstSize == 0) return new byte[0];
        Lz77Decompressor d = new Lz77Decompressor(src);
        byte[] out = new byte[dstSize];
        int outLen = 0;

        int[] litRun = d.literalRun();
        int litLen = litRun[0];
        int nextOp = litRun[1];

        for (int i = 0; i < litLen; i++) {
            if (d.at >= d.src.length) throw new Exception("字面量游程越界");
            if (outLen < dstSize) out[outLen++] = d.src[d.at];
            d.at++;
        }

        while (d.at < d.src.length) {
            if (nextOp == 0x00) nextOp = d.take();
            int op = nextOp;
            nextOp = 0x00;

            if (op == 0x11) return fitSize(out, outLen, dstSize);

            int copyLen = 0;
            int backOff = 0;

            if (op >= 0x40) {
                copyLen = ((op & 0xF0) >>> 4) - 1;
                int op2 = d.take();
                backOff = (op2 << 2) | ((op & 0x0C) >>> 2);
                if ((op & 0x03) != 0) {
                    litLen = op & 0x03;
                } else {
                    litRun = d.literalRun();
                    litLen = litRun[0];
                    nextOp = litRun[1];
                }
            } else {
                Lz77Form form = d.formOf(op);
                if (form == null) throw new Exception("非法 LZ77 opcode 0x" + Integer.toHexString(op));
                copyLen = form.lenBase;
                if (form.opMask == 0xFF) copyLen += op;
                else if (form.opMask == 0x0F) copyLen += (op & form.opMask);
                if (form.ext) copyLen += d.extendedCount();

                int[] word = d.splitWord();
                backOff = word[0] + form.offBase;
                int litCount = word[1];
                if (litCount == 0) {
                    litRun = d.literalRun();
                    litLen = litRun[0];
                    nextOp = litRun[1];
                } else {
                    litLen = litCount;
                }
            }

            int dist = backOff + 1;
            for (int i = 0; i < copyLen; i++) {
                if (outLen < dstSize) {
                    int base = outLen - dist;
                    out[outLen++] = (base >= 0) ? out[base] : 0;
                }
            }

            for (int i = 0; i < litLen; i++) {
                if (d.at >= d.src.length) throw new Exception("字面量游程越界");
                if (outLen < dstSize) out[outLen++] = d.src[d.at];
                d.at++;
            }
        }
        return fitSize(out, outLen, dstSize);
    }

    private static byte[] fitSize(byte[] out, int outLen, int dstSize) {
        if (outLen == dstSize) return out;
        byte[] res = new byte[dstSize];
        System.arraycopy(out, 0, res, 0, Math.min(outLen, dstSize));
        return res;
    }
}
