package com.hdm.cad.objrec;

import com.hdm.cad.bitstream.BitStream;

public class ObjectRecord {
    public int offset;
    public long size;
    public byte[] body;
    public long bodyBitOffset;
    public boolean r2010Plus;
    public long handleStreamSizeBits;
    public long handleSizeFieldBits;

    public BitStream bodyBitStream() {
        BitStream r = new BitStream(body);
        r.setBitPos(bodyBitOffset);
        return r;
    }

    public long dataEndBit() {
        long base = bodyBitOffset;
        if (r2010Plus) {
            base += handleSizeFieldBits;
        }
        return base + size * 8 - handleStreamSizeBits;
    }
}
