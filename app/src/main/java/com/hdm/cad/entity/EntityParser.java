package com.hdm.cad.entity;

import com.hdm.cad.bitstream.BitStream;
import com.hdm.cad.container.DwgVersion;
import com.hdm.cad.objrec.ObjRecParser;
import static com.hdm.cad.entity.EntityBase.*;

public class EntityParser {

    public static long[] decodeOwnerLayer(BitStream r, CommonEntityHead head) throws Exception {
        int[] saved = r.cursor();
        r.setBitPos(head.objSizeBit);
        long owner = 0, layer = 0;
        try {
            long[] res = EntityHeadParser.parseCommonEntityHandles(r, head);
            owner = res[0]; layer = res[1];
        } catch (Exception e) {}
        r.restore(saved[0], saved[1]);
        return new long[]{owner, layer};
    }

    public static EntityBase decodeEntityByTypeVer(BitStream r, CommonEntityHead head, int typeCode, DwgVersion ver, int codepage) throws Exception {
        switch (typeCode) {
            case 0x13: return decodeLine(r, head);
            case 0x12: return decodeCircle(r, head);
            case 0x11: return decodeArc(r, head);
            case 0x1B: return decodePoint(r, head);
            case 0x23: return decodeEllipse(r, head);
            case 0x4D: return decodeLwPolyline(r, head, ver);
            case 0x01: return decodeText(r, head, codepage, ver);
            case 0x2C: return decodeMText(r, head, codepage, ver);
            case 0x07: return decodeInsert(r, head, ver); // INSERT
            default:
                EntityBase dummy = new EntityBase();
                dummy.handle = head.handle; dummy.color = head.color; dummy.mode = head.entityMode;
                return dummy;
        }
    }

    public static String readStringAreaStrings(BitStream r, CommonEntityHead head, int max) throws Exception {
        if (head.objSizeBit < 34) return "";
        BitStream r2 = new BitStream(r.src);
        r2.setBitPos(head.objSizeBit - 1);
        if (r2.readB() != 1) return "";
        r2.setBitPos(head.objSizeBit - 17);
        int ds = r2.readRS();
        if ((ds & 0x8000) != 0) {
            r2.setBitPos(head.objSizeBit - 33);
            int hi = r2.readRS();
            ds = (ds & 0x7fff) | (hi << 15);
        }
        long areaStart = head.objSizeBit - 17 - ds;
        if (areaStart < 0) return "";
        r2.setBitPos(areaStart);
        return r2.readTU();
    }

    public static BasicEntities.EntLine decodeLine(BitStream r, CommonEntityHead head) throws Exception {
        int zIsZero = r.readB();
        double xs = r.readRD(), xe = r.readDD(xs);
        double ys = r.readRD(), ye = r.readDD(ys);
        double zs = 0, ze = 0;
        if (zIsZero == 0) { zs = r.readRD(); ze = r.readDD(zs); }
        r.readBT(); r.readBE();
        long[] ol = decodeOwnerLayer(r, head);
        BasicEntities.EntLine e = new BasicEntities.EntLine();
        e.handle = head.handle; e.color = head.color; e.mode = head.entityMode; e.owner = ol[0]; e.layer = ol[1];
        e.start = new Point3(xs, ys, zs); e.end = new Point3(xe, ye, ze);
        return e;
    }

    public static BasicEntities.EntCircle decodeCircle(BitStream r, CommonEntityHead head) throws Exception {
        double[] center = r.read3BD(); double radius = r.readBD();
        r.readBT(); r.readBE();
        long[] ol = decodeOwnerLayer(r, head);
        BasicEntities.EntCircle e = new BasicEntities.EntCircle();
        e.handle = head.handle; e.color = head.color; e.mode = head.entityMode; e.owner = ol[0]; e.layer = ol[1];
        e.center = new Point3(center[0], center[1], center[2]); e.radius = radius;
        return e;
    }

    public static BasicEntities.EntArc decodeArc(BitStream r, CommonEntityHead head) throws Exception {
        double[] center = r.read3BD(); double radius = r.readBD();
        r.readBT(); r.readBE();
        double a0 = r.readBD(); double a1 = r.readBD();
        long[] ol = decodeOwnerLayer(r, head);
        BasicEntities.EntArc e = new BasicEntities.EntArc();
        e.handle = head.handle; e.color = head.color; e.mode = head.entityMode; e.owner = ol[0]; e.layer = ol[1];
        e.center = new Point3(center[0], center[1], center[2]); e.radius = radius; e.angleStart = a0; e.angleEnd = a1;
        return e;
    }

    public static BasicEntities.EntPoint decodePoint(BitStream r, CommonEntityHead head) throws Exception {
        double[] loc = r.read3BD(); r.readBT(); r.readBE(); double rotation = r.readBD();
        long[] ol = decodeOwnerLayer(r, head);
        BasicEntities.EntPoint e = new BasicEntities.EntPoint();
        e.handle = head.handle; e.color = head.color; e.mode = head.entityMode; e.owner = ol[0]; e.layer = ol[1];
        e.location = new Point3(loc[0], loc[1], loc[2]); e.rotation = rotation;
        return e;
    }

    public static BasicEntities.EntEllipse decodeEllipse(BitStream r, CommonEntityHead head) throws Exception {
        double[] center = r.read3BD(); double[] major = r.read3BD(); double[] ext = r.read3BD();
        double ratio = r.readBD(); double a0 = r.readBD(); double a1 = r.readBD();
        long[] ol = decodeOwnerLayer(r, head);
        BasicEntities.EntEllipse e = new BasicEntities.EntEllipse();
        e.handle = head.handle; e.color = head.color; e.mode = head.entityMode; e.owner = ol[0]; e.layer = ol[1];
        e.center = new Point3(center[0], center[1], center[2]); e.majorAxis = new Point3(major[0], major[1], major[2]);
        e.extrusion = new Point3(ext[0], ext[1], ext[2]); e.ratio = ratio; e.startAng = a0; e.endAng = a1;
        return e;
    }

    public static BasicEntities.EntLwPolyline decodeLwPolyline(BitStream r, CommonEntityHead head, DwgVersion ver) throws Exception {
        BasicEntities.EntLwPolyline e = new BasicEntities.EntLwPolyline();
        e.handle = head.handle; e.color = head.color; e.mode = head.entityMode;
        int flags = r.readBS(); e.flags = flags;
        if ((flags & 0x04) != 0) e.constWidth = r.readBD();
        if ((flags & 0x08) != 0) e.elevation = r.readBD();
        if ((flags & 0x02) != 0) e.thickness = r.readBD();
        if ((flags & 0x01) != 0) r.read3BD();

        int vertCount = (int) r.readBL();
        int numBulges = ((flags & 0x10) != 0) ? (int) r.readBL() : 0;
        int numVertexIDs = ((flags & 0x400) != 0) ? (int) r.readBL() : 0;
        int numWidths = ((flags & 0x20) != 0) ? (int) r.readBL() : 0;

        boolean r2000Plus = (ver != DwgVersion.VerR13 && ver != DwgVersion.VerR14);
        if (vertCount > 0) {
            if (r2000Plus) {
                double x0 = r.readRD(), y0 = r.readRD();
                e.vertices.add(new Point2(x0, y0));
                for (int i = 1; i < vertCount; i++) {
                    Point2 last = e.vertices.get(e.vertices.size() - 1);
                    e.vertices.add(new Point2(r.readDD(last.x), r.readDD(last.y)));
                }
            } else {
                for (int i = 0; i < vertCount; i++) e.vertices.add(new Point2(r.readRD(), r.readRD()));
            }
        }
        if (numBulges > 0) {
            e.bulges = new double[vertCount];
            for (int i = 0; i < numBulges && i < vertCount; i++) e.bulges[i] = r.readBD();
        }
        for (int i = 0; i < numVertexIDs; i++) r.readBL();
        for (int i = 0; i < numWidths; i++) { r.readBD(); r.readBD(); }
        long[] ol = decodeOwnerLayer(r, head); e.owner = ol[0]; e.layer = ol[1];
        return e;
    }

    public static BasicEntities.EntText decodeText(BitStream r, CommonEntityHead head, int codepage, DwgVersion ver) throws Exception {
        BasicEntities.EntText e = new BasicEntities.EntText();
        e.handle = head.handle; e.color = head.color; e.mode = head.entityMode;
        int dataFlags = r.readRC();
        double elevation = 0; if ((dataFlags & 0x01) == 0) elevation = r.readRD();
        double ix = r.readRD(); double iy = r.readRD();
        if ((dataFlags & 0x02) == 0) { r.readDD(ix); r.readDD(iy); }
        double[] ext = r.readBE(); r.readBT();
        if ((dataFlags & 0x04) == 0) r.readRD();
        double rotation = 0; if ((dataFlags & 0x08) == 0) rotation = r.readRD();
        double height = r.readRD();
        double widthFactor = 1.0; if ((dataFlags & 0x10) == 0) widthFactor = r.readRD();

        String text = "";
        if (ver.ordinal() >= DwgVersion.VerR2007.ordinal()) text = readStringAreaStrings(r, head, 1);
        if (text.isEmpty()) text = r.readTV(codepage);

        int gen = 0, hAlign = 0, vAlign = 0;
        if ((dataFlags & 0x20) == 0) gen = r.readBS();
        if ((dataFlags & 0x40) == 0) hAlign = r.readBS();
        if ((dataFlags & 0x80) == 0) vAlign = r.readBS();

        long[] ol = decodeOwnerLayer(r, head); e.owner = ol[0]; e.layer = ol[1];
        e.insertion = new Point3(ix, iy, elevation); e.height = height; e.rotation = rotation; e.text = text;
        e.hAlign = hAlign; e.vAlign = vAlign; e.gen = gen; e.widthFactor = widthFactor;
        e.extrusion = new Point3(ext[0], ext[1], ext[2]);
        return e;
    }

    public static BasicEntities.EntMText decodeMText(BitStream r, CommonEntityHead head, int codepage, DwgVersion ver) throws Exception {
        BasicEntities.EntMText e = new BasicEntities.EntMText();
        e.handle = head.handle; e.color = head.color; e.mode = head.entityMode;
        double[] ins = r.read3BD(); r.read3BD(); r.read3BD();
        double rectWidth = r.readBD(); r.readBD(); double textHeight = r.readBD();
        r.readBS(); r.readBS(); r.readBD(); r.readBD();

        String text = "";
        if (ver.ordinal() >= DwgVersion.VerR2007.ordinal()) text = readStringAreaStrings(r, head, 1);
        if (text.isEmpty()) text = r.readTV(codepage);

        long[] ol = decodeOwnerLayer(r, head); e.owner = ol[0]; e.layer = ol[1];
        e.insertion = new Point3(ins[0], ins[1], ins[2]); e.rectWidth = rectWidth; e.textHeight = textHeight; e.text = text;
        return e;
    }

    public static BasicEntities.EntInsert decodeInsert(BitStream r, CommonEntityHead head, DwgVersion ver) throws Exception {
        BasicEntities.EntInsert e = new BasicEntities.EntInsert();
        e.handle = head.handle; e.color = head.color; e.mode = head.entityMode;
        
        double[] pos = r.read3BD();
        double sx = 1, sy = 1, sz = 1;
        
        boolean r13r14 = (ver == DwgVersion.VerR13 || ver == DwgVersion.VerR14);
        if (r13r14) {
            sx = r.readBD(); sy = r.readBD(); sz = r.readBD();
        } else {
            int dataFlags = r.readBB();
            if ((dataFlags & 0x01) != 0) { sy = r.readDD(1.0); sz = r.readDD(1.0); }
            else if ((dataFlags & 0x02) != 0) { sx = r.readRD(); sy = sx; sz = sx; }
            else if ((dataFlags & 0x03) != 0) { sx = r.readRD(); sy = r.readDD(sx); sz = r.readDD(sx); }
        }
        
        double rotation = r.readBD();
        r.read3BD(); // extrusion
        r.readB(); // hasAttribs

        // 块头句柄在 handle 流中
        long blockHeader = 0;
        int[] saved = r.cursor();
        r.setBitPos(head.objSizeBit);
        try {
            EntityHeadParser.parseCommonEntityHandles(r, head);
            blockHeader = ObjRecParser.readHandleReference(r, head.handle);
        } catch (Exception ex) {}
        r.restore(saved[0], saved[1]);

        e.position = new Point3(pos[0], pos[1], pos[2]);
        e.scale = new Point3(sx, sy, sz);
        e.rotation = rotation;
        e.blockHeader = blockHeader;
        return e;
    }
}
