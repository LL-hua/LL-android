package com.hdm.cad.entity;

import com.hdm.cad.bitstream.BitStream;
import com.hdm.cad.objrec.ObjRecParser; // <--- 补上这行核心导入
import static com.hdm.cad.entity.EntityBase.*;

public class EntityHeadParser {

    public static CommonEntityHead parseEntityHead(BitStream r, long dataEnd, int feats) throws Exception {
        CommonEntityHead head = new CommonEntityHead();
        head.objSizeBit = dataEnd;
        head.auditBitsize = dataEnd;

        if ((feats & 0x001) != 0) { // ObjSizeInStream
            head.objSizeBit = r.readRL();
            head.auditBitsize = head.objSizeBit;
        }

        head.handle = r.readH().value;

        // 跳过 EED
        while (true) {
            int sz = r.readBS();
            if (sz == 0) break;
            r.readH();
            r.readRCS(sz);
        }

        int picFlag = r.readB();
        head.previewExists = picFlag == 1;
        if (picFlag == 1) {
            long graphicSize = ((feats & 0x002) != 0) ? r.readRL() : r.readBLL();
            head.preview = r.readRCS((int) graphicSize);
        }

        head.entityMode = r.readBB();
        head.numReactors = r.readBL();
        
        if ((feats & 0x100) != 0) { // NolinksBit (仅 R2000)
            head.noLinks = r.readB() == 1;
            head.prevNextLinks = true;
        } else {
            head.xdicMissing = r.readB() == 1;
        }

        if ((feats & 0x040) != 0) { // DSBinary (R2013+)
            head.hasDsBinary = r.readB() == 1;
        }

        parseEntityColorHead(r, head);

        head.ltypeScale = r.readBD();
        head.ltypeFlags = r.readBB();
        head.plotstyleFlags = r.readBB();
        
        if ((feats & 0x004) != 0) head.materialFlags = r.readBB();
        if ((feats & 0x008) != 0) head.shadowFlags = r.readRC();
        if ((feats & 0x010) != 0) {
            for (int i = 0; i < 3; i++) head.visualStyle[i] = r.readB() != 0;
        }
        
        head.invisible = (short) r.readBS();
        
        if ((feats & 0x020) != 0) head.linewt = r.readRC();

        return head;
    }

    public static void parseEntityColorHead(BitStream r, CommonEntityHead head) throws Exception {
        int noLinks = r.readB();
        head.color.hasIndex = true;
        if (noLinks != 0) {
            int second = r.readB();
            if (second == 1) { head.color.index = 256; head.color.flag = 1; }
            else { head.color.index = 0; head.color.flag = 0; }
            return;
        }
        int mode = r.readB();
        if (mode == 1) {
            head.color.index = r.readRC();
            head.color.flag = 0;
            return;
        }
        int flags = r.readRS();
        head.color.index = flags & 0x01FF;
        head.color.flag = flags >>> 8;
        
        if ((flags & 0x2000) != 0) {
            long raw = r.readBL();
            head.color.alphaRaw = raw;
            head.color.alphaType = (int) (raw >>> 24);
            head.color.alpha = (int) (raw & 0xFF);
            head.color.hasAlpha = true;
        }
        if ((flags & 0x4000) == 0 && (flags & 0x8000) != 0) {
            head.color.trueColor = r.readBL();
            head.color.hasTrue = true;
        }
    }

    public static long[] parseCommonEntityHandles(BitStream r, CommonEntityHead head) throws Exception {
        long owner = 0, layer = 0;
        if (head.entityMode == 0) {
            owner = ObjRecParser.readHandleReference(r, head.handle);
        }
        for (int i = 0; i < head.numReactors; i++) {
            ObjRecParser.readHandleReference(r, head.handle);
        }
        if (!head.xdicMissing) {
            head.xdicObjHandle = ObjRecParser.readHandleReference(r, head.handle);
        }
        if (head.r13r14) {
            layer = ObjRecParser.readHandleReference(r, head.handle);
            if (head.ltypeFlags == 3) ObjRecParser.readHandleReference(r, head.handle);
            if (!head.noLinks) {
                head.prevEntity = ObjRecParser.readHandleReference(r, head.handle);
                head.nextEntity = ObjRecParser.readHandleReference(r, head.handle);
            }
        } else {
            if (head.prevNextLinks && !head.noLinks) {
                head.prevEntity = ObjRecParser.readHandleReference(r, head.handle);
                head.nextEntity = ObjRecParser.readHandleReference(r, head.handle);
            }
            layer = ObjRecParser.readHandleReference(r, head.handle);
            if (head.ltypeFlags == 3) ObjRecParser.readHandleReference(r, head.handle);
            if (head.materialFlags == 3) ObjRecParser.readHandleReference(r, head.handle);
            if (head.shadowFlags == 3) ObjRecParser.readHandleReference(r, head.handle);
            if (head.plotstyleFlags == 3) head.plotstyleHandle = ObjRecParser.readHandleReference(r, head.handle);
            for (int i = 0; i < 3; i++) {
                if (head.visualStyle[i]) ObjRecParser.readHandleReference(r, head.handle);
            }
        }
        return new long[]{owner, layer};
    }
}
