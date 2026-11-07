package com.hdm.cad.object;

import com.hdm.cad.bitstream.BitStream;
import com.hdm.cad.container.DwgVersion;
import java.io.EOFException;

public class GfRead {
    public BitStream r;
    public DwgVersion ver;

    public GfRead(BitStream r, DwgVersion ver) {
        this.r = r;
        this.ver = ver;
    }

    public void T(String key, ObjGeneric g) throws EOFException {
        String s = r.readTV(0); // 简化：暂不处理 R2007+ 独立字符串流
        g.addField(key, s);
    }

    public void BS(String key, ObjGeneric g) throws EOFException { g.addField(key, (long)r.readBS()); }
    public void BSd(String key, ObjGeneric g) throws EOFException { g.addField(key, (long)(short)r.readBS()); }
    public void BD(String key, ObjGeneric g) throws EOFException { g.addField(key, r.readBD()); }
    public void RC(String key, ObjGeneric g) throws EOFException { g.addField(key, (long)r.readRC()); }
    public void B(String key, ObjGeneric g) throws EOFException { g.addField(key, r.readB() != 0); }
    public void BL(String key, ObjGeneric g) throws EOFException { g.addField(key, r.readBL()); }
    public void BLd(String key, ObjGeneric g) throws EOFException { g.addField(key, (long)(int)r.readBL()); }
    
    public void RD2(String key, ObjGeneric g) throws EOFException {
        g.addField(key, new double[]{r.readRD(), r.readRD()});
    }
    public void Point3(String key, ObjGeneric g) throws EOFException {
        g.addField(key, r.read3BD());
    }
    public void CMC(String key, ObjGeneric g) throws EOFException {
        g.addField(key, (long)r.readBS()); // 简化版颜色
    }
    public long BLv(String key, ObjGeneric g) throws EOFException {
        long v = r.readBL(); g.addField(key, v); return v;
    }
    public boolean Bv(String key, ObjGeneric g) throws EOFException {
        boolean v = r.readB() != 0; g.addField(key, v); return v;
    }
}
