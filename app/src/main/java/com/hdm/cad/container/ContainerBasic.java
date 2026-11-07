package com.hdm.cad.container;

import com.hdm.cad.bitstream.BitStream;
import java.util.ArrayList;
import java.util.List;

public class ContainerBasic {
    public static final int HeaderOffset = 0x80;
    public static final int HeaderSize = 0x6C;
    public static final int SectionPageMapMagic = 0x41630E3B;
    public static final int SectionMapMagic = 0x4163003B;
    public static final int DataSectionMagic = 0x4163043B;
    public static final int systemSectionHdrSize = 0x14;

    public static int readUInt32LE(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off+1] & 0xFF) << 8) | ((b[off+2] & 0xFF) << 16) | ((b[off+3] & 0xFF) << 24);
    }
    
    public static long readUInt64LE(byte[] b, int off) {
        return (readUInt32LE(b, off) & 0xFFFFFFFFL) | ((long) readUInt32LE(b, off+4) << 32);
    }

    public static byte[] lcgKeyStream() {
        byte[] seq = new byte[HeaderSize];
        int seed = 1;
        for (int i = 0; i < seq.length; i++) {
            seed = seed * 0x343FD + 0x269EC3;
            seq[i] = (byte) ((seed >>> 16) & 0xFF);
        }
        return seq;
    }

    public static R2004Header decryptR2004Header(byte[] data) throws Exception {
        if (data.length < HeaderOffset + HeaderSize) throw new Exception("缺少 R2004+ 容器头");
        byte[] keys = lcgKeyStream();
        byte[] plain = new byte[HeaderSize];
        for (int i = 0; i < plain.length; i++) {
            plain[i] = (byte) (data[HeaderOffset + i] ^ keys[i]);
        }
        R2004Header hdr = new R2004Header();
        hdr.sectionPageMapAddress = readUInt64LE(plain, 0x54);
        hdr.sectionMapID = readUInt32LE(plain, 0x5C);
        return hdr;
    }

    public static byte[] unmaskPageHeader(byte[] b, long pageAddress, int offset) {
        byte[] out = new byte[32];
        System.arraycopy(b, offset, out, 0, 32);
        int mask = 0x4164536B ^ (int) pageAddress;
        for (int i = 0; i + 4 <= 32; i += 4) {
            int v = readUInt32LE(out, i) ^ mask;
            out[i] = (byte) (v & 0xFF);
            out[i+1] = (byte) ((v >>> 8) & 0xFF);
            out[i+2] = (byte) ((v >>> 16) & 0xFF);
            out[i+3] = (byte) ((v >>> 24) & 0xFF);
        }
        return out;
    }
    
    public static String readCString(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off);
    }
}
