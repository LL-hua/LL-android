package com.hdm.cad.object;

import com.hdm.cad.bitstream.BitStream;
import com.hdm.cad.container.DwgVersion;
import com.hdm.cad.objrec.ObjRecParser;
import com.hdm.cad.objrec.ObjectRecord;
import java.util.HashMap;
import java.util.Map;

public class ObjectParser {
    // 注册表
    public static final Map<Integer, InternalObjectSpec> FIXED_DECODERS = new HashMap<>();
    public static final Map<String, InternalObjectSpec> CLASS_DECODERS = new HashMap<>();

    // 注册字典对象
    static {
        FIXED_DECODERS.put(0x2A, new InternalObjectSpec(
            (r, ver, fr, g) -> decodeDictionary(r, ver, fr, g),
            (r, ver, fr, g) -> decodeDictionaryHdl(r, ver, fr, g)
        ));
    }

    // ====== 核心调度逻辑 ======
    public static ObjGeneric decodeInternalObject(BitStream r, ObjectRecord rec, DwgVersion ver, boolean r2013Plus, int typeCode, String className) throws Exception {
        InternalObjectSpec spec = FIXED_DECODERS.get(typeCode);
        if (spec == null && className != null) spec = CLASS_DECODERS.get(className);
        if (spec == null) throw new Exception("无解码器: " + typeCode);

        ObjGeneric g = new ObjGeneric();
        g.name = className != null ? className : "UNKNOWN";
        g.controlType = typeCode;

        // 读 bitsize (R2000-R2007 内联)
        boolean isHead = (ver == DwgVersion.VerR2000 || ver == DwgVersion.VerR2004 || ver == DwgVersion.VerR2007);
        if (isHead) {
            g.objSizeBit = r.readRL();
        }

        // 读 handle
        g.handle = r.readH().value;
        
        // 读 EED (简化：直接跳过)
        skipEEDChain(r, ver);

        // 读 num_reactors
        g.numReactors = (int) r.readBL();
        if (ver.ordinal() >= DwgVersion.VerR2004.ordinal()) {
            g.xdicMissing = r.readB() == 1;
        }

        // 执行专有字段解码
        GfRead fr = new GfRead(r, ver);
        spec.decode.decode(r, ver, fr, g);

        // 定位到 handle 流起点
        if (ver.ordinal() >= DwgVersion.VerR2010.ordinal()) {
            g.objSizeBit = rec.dataEndBit() - rec.bodyBitOffset - rec.handleSizeFieldBits;
            r.setBitPos(rec.dataEndBit());
        } else {
            r.setBitPos(rec.bodyBitOffset + g.objSizeBit);
        }

        // 读 handle 流：owner + reactors + xdic
        g.owner = ObjRecParser.readHandleReference(r, g.handle);
        for (int i = 0; i < g.numReactors; i++) ObjRecParser.readHandleReference(r, g.handle);
        if (!g.xdicMissing) ObjRecParser.readHandleReference(r, g.handle);

        // 执行附加 handle 流
        if (spec.hdl != null) {
            spec.hdl.hdl(r, ver, fr, g);
        } else {
            int n = spec.extraHandles;
            if (!spec.handleVectorKey.isEmpty() && g.getField(spec.handleVectorKey) != null) {
                n += ((Number) g.getField(spec.handleVectorKey)).intValue();
            }
            for (int i = 0; i < n; i++) {
                g.handles.add(ObjRecParser.readHandleReference(r, g.handle));
            }
        }

        return g;
    }

    // ====== 字典对象 (DICTIONARY 0x2A) 解码器 ======
    public static void decodeDictionary(BitStream r, DwgVersion ver, GfRead fr, ObjGeneric g) throws Exception {
        long numItems = r.readBL();
        g.addField("numitems", numItems);

        if (ver.ordinal() >= DwgVersion.VerR2000.ordinal() && ver != DwgVersion.VerR13 && ver != DwgVersion.VerR14) {
            g.addField("cloning", (long) r.readBS());
        }
        if (ver != DwgVersion.VerR13) {
            g.addField("is_hardowner", r.readRC() != 0);
        }

        // 读 key 列表
        for (int i = 0; i < numItems; i++) {
            fr.T("keys[" + i + "]", g);
        }
    }

    public static void decodeDictionaryHdl(BitStream r, DwgVersion ver, GfRead fr, ObjGeneric g) throws Exception {
        long numItems = (Long) g.getField("numitems");
        for (int i = 0; i < numItems; i++) {
            g.handles.add(ObjRecParser.readHandleReference(r, g.handle));
        }
    }

    // ====== 工具：跳过 EED ======
    public static void skipEEDChain(BitStream r, DwgVersion ver) throws Exception {
        while (true) {
            int sz = r.readBS();
            if (sz == 0) break;
            r.readH();
            for (int i = 0; i < sz; i++) r.readRC();
        }
    }
}
