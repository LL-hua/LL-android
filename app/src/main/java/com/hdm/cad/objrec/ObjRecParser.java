package com.hdm.cad.objrec;

import com.hdm.cad.bitstream.BitStream;
import com.hdm.cad.bitstream.HandleRef;
import java.io.EOFException;
import java.util.ArrayList;
import java.util.List;

public class ObjRecParser {

    public static long readHandleReference(BitStream r, long base) throws EOFException {
        HandleRef h = r.readH();
        switch (h.code) {
            case 0x06: return base + 1;
            case 0x08: return base == 0 ? 0 : base - 1;
            case 0x0A: return base + h.value;
            case 0x0C: return h.value > base ? 0 : base - h.value;
            default: return h.value;
        }
    }

    public static List<ObjectRef> parseObjectMapHandles(byte[] data) throws Exception {
        List<ObjectRef> objects = new ArrayList<>();
        int pos = 0;
        while (true) {
            if (data.length - pos < 2) break;
            int chunkSize = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            pos += 2;
            if (chunkSize == 2) break;
            if (chunkSize < 2 || data.length - pos < chunkSize - 2) {
                throw new Exception("cad: 对象图块大小非法 " + chunkSize);
            }
            objects.addAll(decodeObjectMapChunk(data, pos, chunkSize - 2));
            pos += chunkSize - 2;
            if (data.length - pos < 2) break;
            pos += 2; // skip CRC
        }
        return objects;
    }

    private static List<ObjectRef> decodeObjectMapChunk(byte[] chunk, int start, int len) throws Exception {
        List<ObjectRef> out = new ArrayList<>();
        long lastHandle = 0, lastOffset = 0;
        int pos = 0;
        while (pos < len) {
            long prevHandle = lastHandle, prevOffset = lastOffset;
            int[] p = new int[]{pos};
            long dHandle = readUnsignedModularChar(chunk, start, p);
            long dOffset = readModularChar(chunk, start, p);
            pos = p[0];
            
            lastHandle += dHandle;
            lastOffset += dOffset;
            if (lastHandle < 0 || lastOffset < 0 || lastOffset > 0xFFFFFFFFL) {
                lastHandle = prevHandle; lastOffset = prevOffset;
                continue;
            }
            out.add(new ObjectRef(lastHandle, (int) lastOffset));
        }
        return out;
    }

    private static long readUnsignedModularChar(byte[] data, int start, int[] pos) throws Exception {
        long value = 0;
        int shift = 0;
        while (true) {
            if (start + pos[0] >= data.length) throw new Exception("EOF");
            int b = data[start + pos[0]] & 0xFF;
            pos[0]++;
            value |= (long)(b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
            if (shift >= 28) return value;
            shift += 7;
        }
    }

    private static long readModularChar(byte[] data, int start, int[] pos) throws Exception {
        long value = 0;
        for (int shift = 0; shift < 28; shift += 7) {
            if (start + pos[0] >= data.length) throw new Exception("EOF");
            int b = data[start + pos[0]] & 0xFF;
            pos[0]++;
            if ((b & 0x80) == 0) {
                boolean negative = (b & 0x40) != 0;
                if (negative) b &= 0xBF;
                value |= (long)b << shift;
                return negative ? -value : value;
            }
            value |= (long)(b & 0x7F) << shift;
        }
        return value;
    }

    public static ObjectRecord parseObjectRecord(byte[] objectsData, ObjectRef ref, boolean r2010Plus) throws Exception {
        int offset = ref.offset;
        if (offset >= objectsData.length) throw new Exception("cad: 对象偏移超出数据段");
        
        BitStream r = new BitStream(objectsData);
        r.setBitPos((long)offset * 8);
        long size = r.readMS();
        if (size == 0) throw new Exception("cad: 对象记录大小为 0");

        ObjectRecord rec = new ObjectRecord();
        rec.offset = offset;
        rec.size = size;
        rec.bodyBitOffset = r.sub;
        rec.r2010Plus = r2010Plus;

        long bodyStart = r.pos; // MS 之后的起点，绝对不要动！
        long bodyBits = size * 8;
        if (r2010Plus) {
            long fieldStart = r.tellBits();
            long hss = r.readUMC();
            rec.handleStreamSizeBits = hss;
            rec.handleSizeFieldBits = r.tellBits() - fieldStart;
            bodyBits += rec.handleSizeFieldBits;
            // bodyStart 不要更新！保留包含 UMC 在内的完整 body！
        }
        
        long bodyEnd = bodyStart + (bodyBits + 7) / 8;
        if (bodyEnd + 2 > objectsData.length) throw new Exception("cad: 对象记录越界");
        
        rec.body = new byte[(int)(bodyEnd - bodyStart)];
        System.arraycopy(objectsData, (int)bodyStart, rec.body, 0, rec.body.length);
        return rec;
    }

    public static ObjHeader parseObjHeader(ObjectRecord rec) throws Exception {
        ObjHeader h = new ObjHeader();
        h.rec = rec;
        BitStream r = rec.bodyBitStream();
        if (rec.r2010Plus) {
            r.readUMC();
            int ot = r.readOT();
            if (ot == 0) throw new Exception("对象类型码为0");
            h.typeCode = ot;
            h.dataStartBit = r.tellBits();
            return h;
        }
        int tc = r.readBS();
        if (tc != 0) {
            h.typeCode = tc;
            h.dataStartBit = r.tellBits();
            return h;
        }
        // fallback R2010+
        BitStream r2 = rec.bodyBitStream();
        r2.readUMC();
        int ot = r2.readOT();
        if (ot != 0) {
            h.typeCode = ot;
            h.dataStartBit = r2.tellBits();
            return h;
        }
        throw new Exception("对象类型码为0");
    }
}
