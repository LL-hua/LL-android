package com.hdm.cad.entity;
import static com.hdm.cad.entity.EntityBase.*;

public class BasicEntities {
    public static class EntLine extends EntityBase { public Point3 start, end; }
    public static class EntCircle extends EntityBase { public Point3 center; public double radius; }
    public static class EntArc extends EntityBase { public Point3 center; public double radius, angleStart, angleEnd; }
    public static class EntPoint extends EntityBase { public Point3 location; public double rotation; }
    public static class EntEllipse extends EntityBase { public Point3 center, majorAxis, extrusion; public double ratio, startAng, endAng; }
    public static class EntText extends EntityBase {
        public String text; public Point3 insertion; public double height; public double rotation;
        public int hAlign, vAlign; public Point2 alignPt; public int gen; public double widthFactor;
        public Point3 extrusion; public long styleHandle;
    }
    public static class EntMText extends EntityBase {
        public String text; public Point3 insertion; public double rectWidth; public double textHeight;
    }
    public static class EntLwPolyline extends EntityBase {
        public int flags; public java.util.List<Point2> vertices = new java.util.ArrayList<>();
        public double[] bulges; public double elevation; public double thickness; public double constWidth;
    }
    public static class EntInsert extends EntityBase {
        public Point3 position; public Point3 scale; public double rotation;
        public long blockHeader; // 引用的块头句柄
    }
}
