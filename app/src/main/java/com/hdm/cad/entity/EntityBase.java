package com.hdm.cad.entity;

import java.util.HashMap;
import java.util.Map;

public class EntityBase {
    public static class Point2 {
        public double x, y;
        public Point2(double x, double y) { this.x = x; this.y = y; }
    }

    public static class Point3 {
        public double x, y, z;
        public Point3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    public static class EntColor {
        public int index;
        public boolean hasIndex;
        public long trueColor;
        public boolean hasTrue;
        public int flag;
        public long alphaRaw;
        public int alphaType;
        public int alpha;
        public boolean hasAlpha;
    }

    public static class CommonEntityHead {
        public long objSizeBit;
        public long auditBitsize;
        public long handle;
        public EntColor color = new EntColor();
        public int entityMode;
        public long numReactors;
        public boolean xdicMissing;
        public boolean hasDsBinary;
        public int ltypeFlags;
        public int plotstyleFlags;
        public int materialFlags;
        public double ltypeScale;
        public int shadowFlags;
        public boolean[] visualStyle = new boolean[3];
        public short invisible;
        public int linewt;
        public boolean r13r14;
        public boolean previewExists;
        public byte[] preview;
        public boolean noLinks;
        public boolean prevNextLinks;
        public boolean isByLayerLtype;
        public long prevEntity;
        public long nextEntity;
        public long xdicObjHandle;
        public long plotstyleHandle;
    }

    public long handle;
    public EntColor color;
    public long layer;
    public long owner;
    public int mode;
    public long objSizeBit;
    public long recSize;
    public CommonEntityHead head;
    public Map<String, Object> extra = new HashMap<>();
    public String typeName;
    public int typeCode;
    public boolean nolinks;
    public boolean isbylayerlt;
    public boolean previewExists;
}
