package com.hdm.cad.objrec;

import java.util.HashMap;
import java.util.Map;

public class ObjTypeCode {
    public static final Map<Integer, String> TYPE_CODES = new HashMap<>();
    
    static {
        TYPE_CODES.put(0x01, "TEXT"); TYPE_CODES.put(0x02, "ATTRIB"); TYPE_CODES.put(0x03, "ATTDEF");
        TYPE_CODES.put(0x04, "BLOCK"); TYPE_CODES.put(0x05, "ENDBLK"); TYPE_CODES.put(0x06, "SEQEND");
        TYPE_CODES.put(0x07, "INSERT"); TYPE_CODES.put(0x08, "MINSERT"); TYPE_CODES.put(0x0A, "VERTEX_2D");
        TYPE_CODES.put(0x0B, "VERTEX_3D"); TYPE_CODES.put(0x0C, "VERTEX_MESH"); TYPE_CODES.put(0x0D, "VERTEX_PFACE");
        TYPE_CODES.put(0x0E, "VERTEX_PFACE_FACE"); TYPE_CODES.put(0x0F, "POLYLINE_2D"); TYPE_CODES.put(0x10, "POLYLINE_3D");
        TYPE_CODES.put(0x11, "ARC"); TYPE_CODES.put(0x12, "CIRCLE"); TYPE_CODES.put(0x13, "LINE");
        TYPE_CODES.put(0x14, "DIM_ORDINATE"); TYPE_CODES.put(0x15, "DIM_LINEAR"); TYPE_CODES.put(0x16, "DIM_ALIGNED");
        TYPE_CODES.put(0x17, "DIM_ANG3PT"); TYPE_CODES.put(0x18, "DIM_ANG2LN"); TYPE_CODES.put(0x19, "DIM_RADIUS");
        TYPE_CODES.put(0x1A, "DIM_DIAMETER"); TYPE_CODES.put(0x1B, "POINT"); TYPE_CODES.put(0x1C, "3DFACE");
        TYPE_CODES.put(0x1D, "POLYLINE_PFACE"); TYPE_CODES.put(0x1E, "POLYLINE_MESH"); TYPE_CODES.put(0x1F, "SOLID");
        TYPE_CODES.put(0x20, "TRACE"); TYPE_CODES.put(0x21, "SHAPE"); TYPE_CODES.put(0x22, "VIEWPORT");
        TYPE_CODES.put(0x23, "ELLIPSE"); TYPE_CODES.put(0x24, "SPLINE"); TYPE_CODES.put(0x28, "RAY");
        TYPE_CODES.put(0x29, "XLINE"); TYPE_CODES.put(0x2A, "DICTIONARY"); TYPE_CODES.put(0x2C, "MTEXT");
        TYPE_CODES.put(0x2D, "LEADER"); TYPE_CODES.put(0x2E, "TOLERANCE"); TYPE_CODES.put(0x2F, "MLINE");
        TYPE_CODES.put(0x30, "BLOCK_CONTROL"); TYPE_CODES.put(0x31, "BLOCK_HEADER"); TYPE_CODES.put(0x32, "LAYER_CONTROL");
        TYPE_CODES.put(0x33, "LAYER"); TYPE_CODES.put(0x34, "STYLE_CONTROL"); TYPE_CODES.put(0x35, "STYLE");
        TYPE_CODES.put(0x38, "LTYPE_CONTROL"); TYPE_CODES.put(0x39, "LTYPE"); TYPE_CODES.put(0x3C, "VIEW_CONTROL");
        TYPE_CODES.put(0x3D, "VIEW"); TYPE_CODES.put(0x3E, "UCS_CONTROL"); TYPE_CODES.put(0x3F, "UCS");
        TYPE_CODES.put(0x40, "VPORT_CONTROL"); TYPE_CODES.put(0x41, "VPORT"); TYPE_CODES.put(0x42, "APPID_CONTROL");
        TYPE_CODES.put(0x43, "APPID"); TYPE_CODES.put(0x44, "DIMSTYLE_CONTROL"); TYPE_CODES.put(0x45, "DIMSTYLE");
        TYPE_CODES.put(0x46, "VX_CONTROL"); TYPE_CODES.put(0x47, "VX_TABLE_RECORD"); TYPE_CODES.put(0x48, "GROUP");
        TYPE_CODES.put(0x49, "MLINESTYLE"); TYPE_CODES.put(0x4D, "LWPOLYLINE"); TYPE_CODES.put(0x4F, "XRECORD");
        TYPE_CODES.put(0x50, "PLACEHOLDER"); TYPE_CODES.put(0x52, "LAYOUT"); TYPE_CODES.put(0x1F3, "PROXY_OBJECT");
    }

    public static String entityTypeName(int code, Map<Integer, String> dynamic) {
        if (TYPE_CODES.containsKey(code)) return TYPE_CODES.get(code);
        if (dynamic != null && dynamic.containsKey(code)) return dynamic.get(code);
        return "";
    }

    public static boolean isEntityType(int code, Map<Integer, String> dynamic) {
        String name = TYPE_CODES.get(code);
        if (name != null) {
            switch (name) {
                case "TEXT": case "ATTRIB": case "ATTDEF": case "INSERT": case "MINSERT":
                case "ARC": case "CIRCLE": case "LINE": case "POINT": case "ELLIPSE":
                case "MTEXT": case "LWPOLYLINE": case "VERTEX_2D": case "VERTEX_3D":
                case "VERTEX_MESH": case "VERTEX_PFACE": case "VERTEX_PFACE_FACE":
                case "POLYLINE_2D": case "POLYLINE_3D": case "SEQEND": case "BLOCK":
                case "ENDBLK": case "SPLINE": case "LEADER": case "3DFACE": case "SOLID":
                case "RAY": case "XLINE": case "MLINE": case "TOLERANCE": case "POLYLINE_PFACE":
                case "POLYLINE_MESH": case "SHAPE": case "VIEWPORT": case "REGION": case "3DSOLID":
                case "BODY": case "OLEFRAME": case "OLE2FRAME": case "PROXY_ENTITY":
                case "DIM_ORDINATE": case "DIM_LINEAR": case "DIM_ALIGNED": case "DIM_ANG3PT":
                case "DIM_ANG2LN": case "DIM_RADIUS": case "DIM_DIAMETER":
                    return true;
            }
        }
        if (dynamic != null && dynamic.containsKey(code)) {
            String dName = dynamic.get(code);
            switch (dName) {
                case "ACDBLINE": case "ACDBCIRCLE": case "ACDBARC": case "ACDBPOINT":
                case "ACDBELLIPSE": case "ACDBMTEXT": case "ACDBTEXT": case "ACDBLWPOLYLINE":
                case "ACDBINSERT": case "ACDBATTRIB": case "ACDBPOLYLINE": case "ACDBSPLINE":
                case "ACDBHATCH": case "ACDBDIMENSION": case "HATCH": case "REGION":
                case "3DSOLID": case "BODY": case "ACDBWIPEOUT": case "WIPEOUT":
                case "IMAGE": case "ACDBRASTERIMAGE": case "LWPOLYLINE": case "MPOLYGON":
                case "ACDBMPOLYGON": case "ACDBTABLE": case "ACAD_TABLE": case "ACDBARCALIGNEDTEXT":
                case "ARC_DIMENSION": case "MULTILEADER": case "LARGE_RADIAL_DIMENSION":
                case "ACDBMLINESTYLE": case "HELIX": case "PDFUNDERLAY": case "DGNUNDERLAY":
                case "DWFUNDERLAY": case "LIGHT": case "ACDBLIGHT":
                    return true;
            }
        }
        return false;
    }
}
