package com.hdm;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CadGLSurfaceView extends GLSurfaceView {
    private CadRenderer mRenderer;

    public static class CadLine {
        public float mx1, my1, mx2, my2;
        public CadLine(float x1, float y1, float x2, float y2) {
            this.mx1 = x1; this.my1 = y1; this.mx2 = x2; this.my2 = y2;
        }
    }

    public CadGLSurfaceView(Context context) { super(context); init(); }
    public CadGLSurfaceView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setEGLContextClientVersion(2);
        setZOrderOnTop(true);
        getHolder().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        mRenderer = new CadRenderer();
        setRenderer(mRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void setCadLines(List<CadLine> lines) {
        mRenderer.setLines(lines);
        requestRender();
    }

    public void updateMapState(double centerLat, double centerLng, float zoom, float scaleFactor, int width, int height) {
        mRenderer.updateMapState(centerLat, centerLng, zoom, scaleFactor, width, height);
        requestRender();
    }

    private static class CadRenderer implements GLSurfaceView.Renderer {
        private int program;
        private FloatBuffer vertexBuffer;
        private int positionHandle, mvpMatrixHandle;
        private float[] mvpMatrix = new float[16];
        private List<CadLine> lines = new ArrayList<>();
        
        private double centerLat = -4.8, centerLng = 32.5;
        private float zoom = 15.0f, scaleFactor = 2.0f;
        private int viewWidth = 1080, viewHeight = 1920;

        private final String vertexShaderCode = "uniform mat4 uMVPMatrix; attribute vec4 vPosition; void main() { gl_Position = uMVPMatrix * vPosition; }";
        private final String fragmentShaderCode = "precision mediump float; uniform vec4 vColor; void main() { gl_FragColor = vColor; }";

        public void setLines(List<CadLine> newLines) { this.lines = newLines; vertexBuffer = null; }
        public void updateMapState(double lat, double lng, float z, float s, int w, int h) { this.centerLat = lat; this.centerLng = lng; this.zoom = z; this.scaleFactor = s; this.viewWidth = w; this.viewHeight = h; }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);
            positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
            mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            if (lines.isEmpty()) return;

            if (vertexBuffer == null) {
                float[] vertices = new float[lines.size() * 4];
                int idx = 0;
                for (CadLine line : lines) {
                    vertices[idx++] = line.mx1; vertices[idx++] = line.my1;
                    vertices[idx++] = line.mx2; vertices[idx++] = line.my2;
                }
                ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
                bb.order(ByteOrder.nativeOrder());
                vertexBuffer = bb.asFloatBuffer();
                vertexBuffer.put(vertices);
                vertexBuffer.position(0);
            }

            GLES20.glUseProgram(program);
            android.opengl.Matrix.setIdentityM(mvpMatrix, 0);
            
            // 粗略缩放以适应屏幕，可根据实际效果微调
            android.opengl.Matrix.translateM(mvpMatrix, 0, -0.5f, -0.5f, 0f); 
            android.opengl.Matrix.scaleM(mvpMatrix, 0, 100f, 100f, 1.0f);

            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
            GLES20.glUniform4f(GLES20.glGetUniformLocation(program, "vColor"), 1.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, lines.size() * 2);
            GLES20.glDisableVertexAttribArray(positionHandle);
        }

        private int loadShader(int type, String shaderCode) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, shaderCode);
            GLES20.glCompileShader(shader);
            return shader;
        }
    }
}