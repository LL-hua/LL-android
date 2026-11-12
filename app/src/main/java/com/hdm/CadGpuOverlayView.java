package com.hdm;

import android.content.Context;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;

import com.hdm.cad.entity.ColorResolver;
import com.hdm.cad.entity.EntityBase;
import com.hdm.cad.entity.BasicEntities;
import com.hdm.cad.entity.EntityBase.Point2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CadGpuOverlayView extends GLSurfaceView {

    private CadRenderer renderer;
    private float scaleFactor = 2.0f;

    public CadGpuOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);

        renderer = new CadRenderer();
        renderer.scaleFactor = this.scaleFactor;
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void updateViewport(double lat, double lng, float zoom) {
        renderer.updateViewport(lat, lng, zoom);
        requestRender();
    }

    public void setCadEntities(List<EntityBase> entities, MyApp app) {
        if (entities == null || entities.isEmpty() || app == null || app.data == null) {
            renderer.clearData();
        } else {
            renderer.buildStaticGeometry(entities, app);
        }
        requestRender();
    }

    // === 补上缺失的 SHX 字体设置接口 ===
    public void setShxFont(com.hdm.cad.drawing.ShxFontParser parser) {
        renderer.shxFont = parser;
    }

    public void setCadVisibility(boolean line, boolean circle) {
        requestRender();
    }

    public void setLayerVisibility(long layerHandle, boolean visible) {
        requestRender();
    }

    public boolean hasCadData() {
        return renderer.hasData();
    }

    public void clearCad() {
        renderer.clearData();
        requestRender();
    }

    public boolean isShowCadLine() {
        return true;
    }

    public boolean isShowCadCircle() {
        return true;
    }

    public List<Long> getActiveLayerHandles() {
        return new ArrayList<>();
    }

    // ===== 滑动条参数控制接口 =====
    public void updateTextParams(float enScale, float enSpacing, float punctSpacing) {
        renderer.enScale = enScale;
        renderer.enSpacing = enSpacing;
        renderer.punctSpacing = punctSpacing;
        // 只设置标记，把重建任务交给 onDrawFrame 去异步执行
        renderer.needsRebuild = true;
        requestRender();
    }

    public float getEnScale() {
        return renderer.enScale;
    }

    public float getEnSpacing() {
        return renderer.enSpacing;
    }

    public float getPunctSpacing() {
        return renderer.punctSpacing;
    }

    private static class CadRenderer implements GLSurfaceView.Renderer {
        private int program;
        private int aPosition, aColor, uMatrix;
        private FloatBuffer vertexBuffer, colorBuffer;
        private int vertexCount = 0;

        private final float[] mvpMatrix = new float[16];

        private double centerLat = -4.8, centerLng = 32.5;
        private float zoom = 15f;
        public float scaleFactor = 2.0f;
        private int viewW = 1, viewH = 1;

        // 缓存原始数据，供动态调参使用
        public List<EntityBase> rawEntities = null;
        public MyApp rawApp = null;

        // 字体解析器
        public com.hdm.cad.drawing.ShxFontParser shxFont = null;

        // 动态调参变量
        // 把之前默认的 1.35, 1.0, 0.4 改成你调好的完美参数
        public float enScale = 1.85f;
        public float enSpacing = 1.13f;
        public float punctSpacing = 0.63f;

// 新增一个标记，告诉 GL 线程需要重建几何
        public boolean needsRebuild = false;

        // 局部坐标基准
        private double originMercX = 0, originMercY = 0;

        public boolean hasData() {
            return vertexCount > 0;
        }

        public void updateViewport(double lat, double lng, float z) {
            this.centerLat = lat;
            this.centerLng = lng;
            this.zoom = z;
        }

        public void clearData() {
            vertexCount = 0;
            vertexBuffer = null;
            colorBuffer = null;
            rawEntities = null;
            rawApp = null;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

            String vCode = "uniform mat4 u_Matrix;" +
                    "attribute vec4 a_Position;" +
                    "attribute vec4 a_Color;" +
                    "varying vec4 v_Color;" +
                    "void main() {" +
                    "  gl_Position = u_Matrix * a_Position;" +
                    "  v_Color = a_Color;" +
                    "}";

            String fCode = "precision mediump float;" +
                    "varying vec4 v_Color;" +
                    "void main() { gl_FragColor = v_Color; }";

            int vs = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
            GLES20.glShaderSource(vs, vCode);
            GLES20.glCompileShader(vs);
            int fs = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
            GLES20.glShaderSource(fs, fCode);
            GLES20.glCompileShader(fs);
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vs);
            GLES20.glAttachShader(program, fs);
            GLES20.glLinkProgram(program);

            aPosition = GLES20.glGetAttribLocation(program, "a_Position");
            aColor = GLES20.glGetAttribLocation(program, "a_Color");
            uMatrix = GLES20.glGetUniformLocation(program, "u_Matrix");
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            this.viewW = width;
            this.viewH = height;
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            // 【新增】在 GL 线程安全地重建几何，UI 线程不卡顿
            if (needsRebuild && rawEntities != null && rawApp != null) {
                buildStaticGeometry(rawEntities, rawApp);
                needsRebuild = false;
            }

            if (vertexCount == 0 || vertexBuffer == null || colorBuffer == null) return;
            // ... 下面原有的绘制代码 ...
            int tileZoom = (int) Math.floor(zoom);
            double worldSize = 256.0 * Math.pow(2.0, tileZoom);
            double[] centerWorld = latLngToWorldPixel(centerLat, centerLng, tileZoom);
            float subZoom = (float) Math.pow(2.0, zoom - tileZoom);
            float totalScale = scaleFactor * subZoom;

            double startX = centerWorld[0] - (viewW / 2.0) / totalScale;
            double startY = centerWorld[1] - (viewH / 2.0) / totalScale;

            float[] projMat = new float[16];
            float[] transMat = new float[16];
            float[] scaleMat = new float[16];
            float[] tempMat = new float[16];

            Matrix.orthoM(projMat, 0, 0f, (float) viewW, (float) viewH, 0f, -1.0f, 1.0f);
            Matrix.setIdentityM(transMat, 0);
            Matrix.translateM(transMat, 0, (float) (-startX + originMercX * worldSize), (float) (-startY + originMercY * worldSize), 0f);
            Matrix.scaleM(transMat, 0, (float) worldSize, (float) worldSize, 1f);

            Matrix.setIdentityM(scaleMat, 0);
            Matrix.scaleM(scaleMat, 0, totalScale, totalScale, 1f);

            Matrix.multiplyMM(tempMat, 0, scaleMat, 0, transMat, 0);
            Matrix.multiplyMM(mvpMatrix, 0, projMat, 0, tempMat, 0);

            GLES20.glUseProgram(program);
            GLES20.glUniformMatrix4fv(uMatrix, 1, false, mvpMatrix, 0);

            GLES20.glEnableVertexAttribArray(aPosition);
            GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
            GLES20.glEnableVertexAttribArray(aColor);
            GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, 0, colorBuffer);

            GLES20.glDrawArrays(GLES20.GL_LINES, 0, vertexCount);

            GLES20.glDisableVertexAttribArray(aPosition);
            GLES20.glDisableVertexAttribArray(aColor);
        }

        public void buildStaticGeometry(List<EntityBase> entities, MyApp app) {
            this.rawEntities = entities;
            this.rawApp = app;
            if (entities == null || entities.isEmpty() || app == null || app.data == null) return;

            EntityBase first = entities.get(0);
            double originCadX = 0, originCadY = 0;
            if (first instanceof BasicEntities.EntLine) {
                originCadX = ((BasicEntities.EntLine) first).start.x;
                originCadY = ((BasicEntities.EntLine) first).start.y;
            } else if (first instanceof BasicEntities.EntLwPolyline) {
                BasicEntities.EntLwPolyline p = (BasicEntities.EntLwPolyline) first;
                if (p.vertices != null && !p.vertices.isEmpty()) {
                    originCadX = p.vertices.get(0).x;
                    originCadY = p.vertices.get(0).y;
                }
            } else if (first instanceof BasicEntities.EntArc) {
                BasicEntities.EntArc a = (BasicEntities.EntArc) first;
                originCadX = a.center.x;
                originCadY = a.center.y;
            }

            double[] lbOrigin = Projection.xy2Gps(originCadY, originCadX, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
            if (lbOrigin != null && lbOrigin[0] != 0) {
                originMercX = (lbOrigin[0] + 180.0) / 360.0;
                originMercY = 0.5 - Math.log(Math.tan((90.0 + lbOrigin[
                                                1]) * Math.PI / 360.0)) / (2.0 * Math.PI);
            }

            List<float[]> pts = new ArrayList<>();
            List<float[]> cls = new ArrayList<>();

            for (EntityBase ent : entities) {
                float[] rgb = getColor(ent);

                if (ent instanceof BasicEntities.EntLine) {
                    BasicEntities.EntLine l = (BasicEntities.EntLine) ent;
                    double[] lb1 = Projection.xy2Gps(l.start.y, l.start.x, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                    double[] lb2 = Projection.xy2Gps(l.end.y, l.end.x, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                    if (lb1 != null && lb2 != null && lb1.length >= 2 && lb2.length >= 2 && lb1[
                                    0] != 0 && lb2[0] != 0) {
                        pts.add(latLngToMercOffset(lb1[1], lb1[0]));
                        pts.add(latLngToMercOffset(lb2[1], lb2[0]));
                        cls.add(rgb);
                        cls.add(rgb);
                    }
                } else if (ent instanceof BasicEntities.EntLwPolyline) {
                    BasicEntities.EntLwPolyline p = (BasicEntities.EntLwPolyline) ent;
                    if (p.vertices != null && p.vertices.size() > 1) {
                        for (int i = 0; i < p.vertices.size() - 1; i++) {
                            Point2 pt1 = p.vertices.get(i), pt2 = p.vertices.get(i + 1);
                            double b = (p.bulges != null && i < p.bulges.length) ? p.bulges[
                                    i] : 0.0;
                            if (Math.abs(b) < 1e-6) {
                                double[] lb1 = Projection.xy2Gps(pt1.y, pt1.x, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                                double[] lb2 = Projection.xy2Gps(pt2.y, pt2.x, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                                if (lb1 != null && lb2 != null && lb1.length >= 2 && lb2.length >= 2 && lb1[
                                                0] != 0 && lb2[0] != 0) {
                                    pts.add(latLngToMercOffset(lb1[1], lb1[0]));
                                    pts.add(latLngToMercOffset(lb2[1], lb2[0]));
                                    cls.add(rgb);
                                    cls.add(rgb);
                                }
                            } else {
                                List<Point2> arcs = new ArrayList<>();
                                addArcPts(arcs, pt1.x, pt1.y, pt2.x, pt2.y, b);
                                for (int j = 0; j < arcs.size() - 1; j++) {
                                    double[] lb1 = Projection.xy2Gps(arcs.get(j).y, arcs.get(j).x, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                                    double[] lb2 = Projection.xy2Gps(arcs.get(j + 1).y, arcs.get(j + 1).x, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                                    if (lb1 != null && lb2 != null && lb1.length >= 2 && lb2.length >= 2 && lb1[
                                                    0] != 0 && lb2[0] != 0) {
                                        pts.add(latLngToMercOffset(lb1[1], lb1[0]));
                                        pts.add(latLngToMercOffset(lb2[1], lb2[0]));
                                        cls.add(rgb);
                                        cls.add(rgb);
                                    }
                                }
                            }
                        }
                    }
                } else if (ent instanceof BasicEntities.EntArc) {
                    BasicEntities.EntArc arc = (BasicEntities.EntArc) ent;
                    double startA = arc.angleStart, endA = arc.angleEnd;
                    while (endA < startA) endA += 2 * Math.PI;
                    double step = (endA - startA) / 32;
                    for (int i = 0; i < 32; i++) {
                        double a1 = startA + step * i, a2 = startA + step * (i + 1);
                        double[] lb1 = Projection.xy2Gps(arc.center.y + arc.radius * Math.sin(a1), arc.center.x + arc.radius * Math.cos(a1), app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                        double[] lb2 = Projection.xy2Gps(arc.center.y + arc.radius * Math.sin(a2), arc.center.x + arc.radius * Math.cos(a2), app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                        if (lb1 != null && lb2 != null && lb1.length >= 2 && lb2.length >= 2 && lb1[
                                        0] != 0 && lb2[0] != 0) {
                            pts.add(latLngToMercOffset(lb1[1], lb1[0]));
                            pts.add(latLngToMercOffset(lb2[1], lb2[0]));
                            cls.add(rgb);
                            cls.add(rgb);
                        }
                    }
                } else if (ent instanceof BasicEntities.EntText) {
                    BasicEntities.EntText txt = (BasicEntities.EntText) ent;
                    if (txt.text == null || txt.text.isEmpty() || shxFont == null) continue;

                    double height = txt.height;
                    double rotation = txt.rotation;
                    if (Math.abs(rotation) > 6.28) rotation = Math.toRadians(rotation);

                    double currentX = txt.insertion.x;
                    double currentY = txt.insertion.y;
                    double cos = Math.cos(rotation);
                    double sin = Math.sin(rotation);

                    double baseScale = height / 100.0;

                    for (int i = 0; i < txt.text.length(); i++) {
                        String ch = String.valueOf(txt.text.charAt(i));

                        if (ch.equals(" ")) {
                            currentX += height * 0.5 * cos;
                            currentY += height * 0.5 * sin;
                            continue;
                        }

                        com.hdm.cad.drawing.ShxFontParser.Glyph glyph = shxFont.getGlyph(ch);
                        if (glyph == null || glyph.strokes.isEmpty()) continue;

                        boolean isChinese = ch.charAt(0) > 127;
                        double currentScale = isChinese ? baseScale : (baseScale * enScale);

                        for (float[] s : glyph.strokes) {
                            double sx1 = s[0] * currentScale, sy1 = s[1] * currentScale;
                            double sx2 = s[2] * currentScale, sy2 = s[3] * currentScale;

                            double lx1 = currentX + (sx1 * cos - sy1 * sin);
                            double ly1 = currentY + (sx1 * sin + sy1 * cos);
                            double lx2 = currentX + (sx2 * cos - sy2 * sin);
                            double ly2 = currentY + (sx2 * sin + sy2 * cos);

                            double[] lb1 = Projection.xy2Gps(ly1, lx1, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);
                            double[] lb2 = Projection.xy2Gps(ly2, lx2, app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter);

                            if (lb1 != null && lb2 != null && lb1[0] != 0 && lb2[0] != 0) {
                                pts.add(latLngToMercOffset(lb1[1], lb1[0]));
                                pts.add(latLngToMercOffset(lb2[1], lb2[0]));
                                cls.add(rgb);
                                cls.add(rgb);
                            }
                        }

                        double advance = glyph.advance * currentScale * enSpacing;
                        if (ch.equals(".") || ch.equals("+") || ch.equals("-")) {
                            advance *= punctSpacing;
                        }
                        if (advance < 0.1) {
                            advance = height * 0.5;
                        }

                        currentX += advance * cos;
                        currentY += advance * sin;
                    }
                }
            }

            if (pts.isEmpty()) {
                synchronized (this) {
                    this.vertexCount = 0;
                }
                return;
            }

            float[] vArr = new float[pts.size() * 2];
            float[] cArr = new float[pts.size() * 4];
            for (int i = 0; i < pts.size(); i++) {
                vArr[i * 2] = pts.get(i)[0];
                vArr[i * 2 + 1] = pts.get(i)[1];
                float[] color = cls.get(i);
                cArr[i * 4] = color[0];
                cArr[i * 4 + 1] = color[1];
                cArr[i * 4 + 2] = color[2];
                cArr[i * 4 + 3] = 1.0f;
            }

            ByteBuffer vbb = ByteBuffer.allocateDirect(vArr.length * 4).order(ByteOrder.nativeOrder());
            FloatBuffer vBuffer = vbb.asFloatBuffer().put(vArr);
            vBuffer.position(0);

            ByteBuffer cbb = ByteBuffer.allocateDirect(cArr.length * 4).order(ByteOrder.nativeOrder());
            FloatBuffer cBuffer = cbb.asFloatBuffer().put(cArr);
            cBuffer.position(0);

            synchronized (this) {
                this.vertexBuffer = vBuffer;
                this.colorBuffer = cBuffer;
                this.vertexCount = pts.size();
            }
        }

        private void addArcPts(List<
                        Point2> out, double x1, double y1, double x2, double y2, double b) {
            if (Math.abs(b) < 1e-6) {
                out.add(new Point2(x2, y2));
                return;
            }
            double dx = x2 - x1, dy = y2 - y1;
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d < 1e-6) {
                out.add(new Point2(x2, y2));
                return;
            }
            double coef = (1.0 - b * b) / (4.0 * b);
            double cx = (x1 + x2) / 2.0 - dy * coef;
            double cy = (y1 + y2) / 2.0 + dx * coef;
            double r = Math.sqrt((x1 - cx) * (x1 - cx) + (y1 - cy) * (y1 - cy));
            double startAngle = Math.atan2(y1 - cy, x1 - cx);
            double theta = 4.0 * Math.atan(b);
            int segments = 32;
            double angleStep = theta / segments;
            for (int i = 1; i <= segments; i++) {
                double angle = startAngle + i * angleStep;
                out.add(new Point2(cx + r * Math.cos(angle), cy + r * Math.sin(angle)));
            }
        }

        private float[] getColor(EntityBase ent) {
            int c = 0xFF000000;
            if (ent.color != null && ent.color.hasIndex) {
                int[] res = ColorResolver.aciColor(ent.color.index == 256 ? 7 : ent.color.index, true);
                if (res[3] == 1) c = Color.rgb(res[0], res[1], res[2]);
            }
            if (c == 0xFF000000) c = 0xFFE53935;
            return new float[]{Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f};
        }

        // 【核心修复】返回类型改为 float[]，解决全篇类型不匹配的报错
        private float[] latLngToMercOffset(double lat, double lng) {
            double mx = (lng + 180.0) / 360.0;
            if (lat > 85.05112878) lat = 85.05112878;
            if (lat < -85.05112878) lat = -85.05112878;
            double my = 0.5 - Math.log(Math.tan((90.0 + lat) * Math.PI / 360.0)) / (2.0 * Math.PI);
            return new float[]{(float) (mx - originMercX), (float) (my - originMercY)};
        }

        private double[] latLngToWorldPixel(double lat, double lng, float z) {
            double worldSize = 256 * Math.pow(2, z);
            double x = (lng + 180.0) / 360.0 * worldSize;
            double sinLat = Math.sin(lat * Math.PI / 180.0);
            double y = (0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI)) * worldSize;
            return new double[]{x, y};
        }
    }
}

/**
 * package com.hdm;
 *
 * <p>import android.content.Context; import android.graphics.Color; import android.opengl.GLES20;
 * import android.opengl.GLSurfaceView; import android.opengl.Matrix; import
 * android.util.AttributeSet; import com.hdm.cad.entity.ColorResolver; import
 * com.hdm.cad.entity.EntityBase; import com.hdm.cad.entity.BasicEntities; import
 * com.hdm.cad.entity.EntityBase.Point2; import java.nio.ByteBuffer; import java.nio.ByteOrder;
 * import java.nio.FloatBuffer; import java.util.ArrayList; import java.util.List; import
 * java.util.concurrent.ExecutorService; import java.util.concurrent.Executors; import
 * javax.microedition.khronos.egl.EGLConfig; import javax.microedition.khronos.opengles.GL10;
 *
 * <p>public class CadGpuOverlayView extends GLSurfaceView { private final CadRenderer renderer =
 * new CadRenderer(); private final ExecutorService initExecutor =
 * Executors.newSingleThreadExecutor();
 *
 * <p>public CadGpuOverlayView(Context context, AttributeSet attrs) { super(context, attrs);
 * setEGLContextClientVersion(2); setEGLConfigChooser(8, 8, 8, 8, 16, 0);
 * getHolder().setFormat(android.graphics.PixelFormat.TRANSLUCENT); setZOrderOnTop(true);
 * setRenderer(renderer); setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY); }
 *
 * <p>public void updateViewport(double lat, double lng, float zoom) {
 * renderer.updateViewportMatrix(lat, lng, zoom); requestRender(); }
 *
 * <p>public void setCadEntities(final List<EntityBase> entities, final MyApp app) { if (entities ==
 * null || entities.isEmpty() || app == null || app.data == null) { renderer.clearCadData();
 * requestRender(); } else { initExecutor.submit(() -> { renderer.buildStaticGeometry(entities,
 * app); requestRender(); }); } }
 *
 * <p>public void setCadVisibility(boolean line, boolean circle) { requestRender(); } public void
 * setLayerVisibility(long layerHandle, boolean visible) { requestRender(); } public boolean
 * hasCadData() { return renderer.hasData(); } public void clearCad() { renderer.clearCadData();
 * requestRender(); } public boolean isShowCadLine() { return true; } public boolean
 * isShowCadCircle() { return true; } public List<Long> getActiveLayerHandles() { return new
 * ArrayList<>(); }
 *
 * <p>private static class CadRenderer implements GLSurfaceView.Renderer { private int program,
 * aPosition, aColor, uMatrix; private volatile FloatBuffer vertexBuffer, colorBuffer; private
 * volatile int vertexCount = 0; private double centerLat = -4.8, centerLng = 32.5; private float
 * zoom = 15f; private int viewW = 1, viewH = 1; private final float[] mvpMatrix = new float[16];
 *
 * <p>public boolean hasData() { return vertexCount > 0; }
 *
 * <p>public synchronized void updateViewportMatrix(double lat, double lng, float z) {
 * this.centerLat = lat; this.centerLng = lng; this.zoom = z; }
 *
 * <p>public synchronized void clearCadData() { vertexCount = 0; vertexBuffer = null; colorBuffer =
 * null; } @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
 * GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f); String vCode = "uniform mat4 u_Matrix;attribute vec4
 * a_Position;attribute vec4 a_Color;varying vec4 v_Color;void
 * main(){gl_Position=u_Matrix*a_Position;v_Color=a_Color;}"; String fCode = "precision mediump
 * float;varying vec4 v_Color;void main(){gl_FragColor=v_Color;}"; int vs =
 * GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER); GLES20.glShaderSource(vs, vCode);
 * GLES20.glCompileShader(vs); int fs = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
 * GLES20.glShaderSource(fs, fCode); GLES20.glCompileShader(fs); program = GLES20.glCreateProgram();
 * GLES20.glAttachShader(program, vs); GLES20.glAttachShader(program, fs);
 * GLES20.glLinkProgram(program); aPosition = GLES20.glGetAttribLocation(program, "a_Position");
 * aColor = GLES20.glGetAttribLocation(program, "a_Color"); uMatrix =
 * GLES20.glGetUniformLocation(program, "u_Matrix"); } @Override public void onSurfaceChanged(GL10
 * gl, int width, int height) { GLES20.glViewport(0, 0, width, height); synchronized (this) {
 * this.viewW = width; this.viewH = height; } } @Override public void onDrawFrame(GL10 gl) {
 * GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT); FloatBuffer vBuf = this.vertexBuffer; FloatBuffer
 * cBuf = this.colorBuffer; int count = this.vertexCount; if (count == 0 || vBuf == null || cBuf ==
 * null) return; double clat, clng; float cz; int w, h; synchronized (this) { clat = centerLat; clng
 * = centerLng; cz = zoom; w = viewW; h = viewH; }
 *
 * <p>int tileZoom = (int) Math.floor(cz); double worldSize = 256.0 * Math.pow(2.0, tileZoom);
 * double[] centerWorld = latLngToWorldPixel(clat, clng, tileZoom); float subZoom = (float)
 * Math.pow(2.0, cz - tileZoom); double startX = centerWorld[0] - (w / 2.0) / (2.0f * subZoom);
 * double startY = centerWorld[1] - (h / 2.0) / (2.0f * subZoom);
 *
 * <p>float[] projMat = new float[16]; float[] transMat = new float[16]; float[] scaleMat = new
 * float[16]; float[] tempMat = new float[16]; Matrix.orthoM(projMat, 0, 0f, (float) w, (float) h,
 * 0f, -1.0f, 1.0f); Matrix.setIdentityM(transMat, 0); Matrix.translateM(transMat, 0, (float)
 * -startX, (float) -startY, 0f); Matrix.scaleM(transMat, 0, (float) worldSize, (float) worldSize,
 * 1f); Matrix.setIdentityM(scaleMat, 0); Matrix.scaleM(scaleMat, 0, 2.0f * subZoom, 2.0f * subZoom,
 * 1f); Matrix.multiplyMM(tempMat, 0, scaleMat, 0, transMat, 0); Matrix.multiplyMM(mvpMatrix, 0,
 * projMat, 0, tempMat, 0);
 *
 * <p>GLES20.glUseProgram(program); GLES20.glUniformMatrix4fv(uMatrix, 1, false, mvpMatrix, 0);
 * GLES20.glEnableVertexAttribArray(aPosition); GLES20.glVertexAttribPointer(aPosition, 2,
 * GLES20.GL_FLOAT, false, 0, vBuf); GLES20.glEnableVertexAttribArray(aColor);
 * GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, 0, cBuf);
 * GLES20.glDrawArrays(GLES20.GL_LINES, 0, count); GLES20.glDisableVertexAttribArray(aPosition);
 * GLES20.glDisableVertexAttribArray(aColor); } ///////
 *
 * <p>public void buildStaticGeometry(List<EntityBase> entities, MyApp app) { if (entities == null
 * || entities.isEmpty() || app == null || app.data == null) return; List<float[]> pts = new
 * ArrayList<>(); List<float[]> cls = new ArrayList<>();
 *
 * <p>for (EntityBase ent : entities) { float[] rgb = getColor(ent); if (ent instanceof
 * BasicEntities.EntLine) { BasicEntities.EntLine l = (BasicEntities.EntLine) ent; double[] lb1 =
 * Projection.xy2Gps(l.start.y, l.start.x, app.data.projection, app.data.controlLonLat,
 * app.data.controlXy, app.data.lonCenter); double[] lb2 = Projection.xy2Gps(l.end.y, l.end.x,
 * app.data.projection, app.data.controlLonLat, app.data.controlXy, app.data.lonCenter); if (lb1 !=
 * null && lb2 != null && lb1.length >= 2 && lb2.length >= 2 && lb1[0] != 0 && lb2[0] != 0) {
 * pts.add(latLngToMercator(lb1[1], lb1[0])); pts.add(latLngToMercator(lb2[1], lb2[0]));
 * cls.add(rgb); cls.add(rgb); } } else if (ent instanceof BasicEntities.EntLwPolyline) {
 * BasicEntities.EntLwPolyline p = (BasicEntities.EntLwPolyline) ent; if (p.vertices != null &&
 * p.vertices.size() > 1) { for (int i = 0; i < p.vertices.size() - 1; i++) { Point2 pt1 =
 * p.vertices.get(i), pt2 = p.vertices.get(i + 1); double b = (p.bulges != null && i <
 * p.bulges.length) ? p.bulges[i] : 0.0; if (Math.abs(b) < 1e-6) { double[] lb1 =
 * Projection.xy2Gps(pt1.y, pt1.x, app.data.projection, app.data.controlLonLat, app.data.controlXy,
 * app.data.lonCenter); double[] lb2 = Projection.xy2Gps(pt2.y, pt2.x, app.data.projection,
 * app.data.controlLonLat, app.data.controlXy, app.data.lonCenter); if (lb1 != null && lb2 != null
 * && lb1.length >= 2 && lb2.length >= 2 && lb1[0] != 0 && lb2[0] != 0) {
 * pts.add(latLngToMercator(lb1[1], lb1[0])); pts.add(latLngToMercator(lb2[1], lb2[0]));
 * cls.add(rgb); cls.add(rgb); } } else { List<Point2> arcs = new ArrayList<>(); addArcPts(arcs,
 * pt1.x, pt1.y, pt2.x, pt2.y, b); for (int j = 0; j < arcs.size() - 1; j++) { double[] lb1 =
 * Projection.xy2Gps(arcs.get(j).y, arcs.get(j).x, app.data.projection, app.data.controlLonLat,
 * app.data.controlXy, app.data.lonCenter); double[] lb2 = Projection.xy2Gps(arcs.get(j + 1).y,
 * arcs.get(j + 1).x, app.data.projection, app.data.controlLonLat, app.data.controlXy,
 * app.data.lonCenter); if (lb1 != null && lb2 != null && lb1.length >= 2 && lb2.length >= 2 &&
 * lb1[0] != 0 && lb2[0] != 0) { pts.add(latLngToMercator(lb1[1], lb1[0]));
 * pts.add(latLngToMercator(lb2[1], lb2[0])); cls.add(rgb); cls.add(rgb); } } } } } } else if (ent
 * instanceof BasicEntities.EntArc) { // 完美植入：从文件 4 中无损抽取的圆弧打散转换逻辑 BasicEntities.EntArc arc =
 * (BasicEntities.EntArc) ent; double startA = arc.angleStart, endA = arc.angleEnd; while (endA <
 * startA) endA += 2 * Math.PI; double step = (endA - startA) / 32; for (int i = 0; i < 32; i++) {
 * double a1 = startA + step * i, a2 = startA + step * (i + 1); double[] lb1 =
 * Projection.xy2Gps(arc.center.y + arc.radius * Math.sin(a1), arc.center.x + arc.radius *
 * Math.cos(a1), app.data.projection, app.data.controlLonLat, app.data.controlXy,
 * app.data.lonCenter); double[] lb2 = Projection.xy2Gps(arc.center.y + arc.radius * Math.sin(a2),
 * arc.center.x + arc.radius * Math.cos(a2), app.data.projection, app.data.controlLonLat,
 * app.data.controlXy, app.data.lonCenter); if (lb1 != null && lb2 != null && lb1.length >= 2 &&
 * lb2.length >= 2 && lb1[0] != 0 && lb2[0] != 0) { pts.add(latLngToMercator(lb1[1], lb1[0]));
 * pts.add(latLngToMercator(lb2[1], lb2[0])); cls.add(rgb); cls.add(rgb); } } } } if (pts.isEmpty())
 * return; float[] vArr = new float[pts.size() * 2]; float[] cArr = new float[pts.size() * 4]; for
 * (int i = 0; i < pts.size(); i++) { vArr[i * 2] = pts.get(i)[0]; vArr[i * 2 + 1] = pts.get(i)[1];
 * float[] rgb = cls.get(i); cArr[i * 4] = rgb[0]; cArr[i * 4 + 1] = rgb[1]; cArr[i * 4 + 2] =
 * rgb[2]; cArr[i * 4 + 3] = 1.0f; } ByteBuffer vbb = ByteBuffer.allocateDirect(vArr.length *
 * 4).order(ByteOrder.nativeOrder()); FloatBuffer vBuffer = vbb.asFloatBuffer().put(vArr);
 * vBuffer.position(0); ByteBuffer cbb = ByteBuffer.allocateDirect(cArr.length *
 * 4).order(ByteOrder.nativeOrder()); FloatBuffer cBuffer = cbb.asFloatBuffer().put(cArr);
 * cBuffer.position(0); synchronized (this) { this.vertexBuffer = vBuffer; this.colorBuffer =
 * cBuffer; this.vertexCount = pts.size(); } }
 *
 * <p>private float[] latLngToMercator(double lat, double lng) { float mx = (float) ((lng + 180.0) /
 * 360.0); if (lat > 85.05112878) lat = 85.05112878; if (lat < -85.05112878) lat = -85.05112878;
 * float my = (float) (0.5 - Math.log(Math.tan((90.0 + lat) * Math.PI / 360.0)) / (2.0 * Math.PI));
 * return new float[]{ mx, my }; }
 *
 * <p>private void addArcPts(List out, double x1, double y1, double x2, double y2, double b) {
 * double dx = x2 - x1, dy = y2 - y1, d = Math.sqrt(dx * dx + dy * dy); if (d < 1e-6) return; double
 * cx = (x1 + x2) / 2.0 - dy * ((1.0 - b * b) / (4.0 * b)), cy = (y1 + y2) / 2.0 + dx * ((1.0 - b *
 * b) / (4.0 * b)); double r = Math.sqrt((x1 - cx) * (x1 - cx) + (y1 - cy) * (y1 - cy)), start =
 * Math.atan2(y1 - cy, x1 - cx), sweep = 4.0 * Math.atan(b); for (int i = 0; i <= 32; i++) { double
 * a = start + i * (sweep / 32.0); out.add(new Point2(cx + r * Math.cos(a), cy + r * Math.sin(a)));
 * } }
 *
 * <p>private float[] getColor(EntityBase ent) { int c = 0xFFE53935; if (ent.color != null &&
 * ent.color.hasIndex) { int[] res = ColorResolver.aciColor(ent.color.index == 256 ? 7 :
 * ent.color.index, true); if (res[3] == 1) c = Color.rgb(res[0], res[1], res[2]); } return new
 * float[]{ Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f }; }
 *
 * <p>private double[] latLngToWorldPixel(double lat, double lng, float z) { double worldSize = 256
 * * Math.pow(2, z); return new double[]{ (lng + 180.0) / 360.0 * worldSize, (0.5 -
 * Math.log(Math.tan((90.0 + lat) * Math.PI / 360.0)) / (2.0 * Math.PI)) * worldSize }; } } } *
 */