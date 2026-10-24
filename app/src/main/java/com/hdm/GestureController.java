package com.hdm;

import android.view.MotionEvent;

/**
 * 手势控制器：单指拖动 + 双指缩放（以双指中心为锚点）。
 * 只负责"变换"，不负责"绘制"。
 */
public class GestureController {

    public interface Listener {
        void onTransform();
    }

    private Listener listener;

    // 当前变换
    private float scale = 1f;
    private float offsetX = 0f, offsetY = 0f;

    // 单指基准
    private float downTouchX = 0f, downTouchY = 0f;
    private float downOffsetX = 0f, downOffsetY = 0f;

    // 双指基准
    private float baseScale = 1f;
    private float baseOffsetX = 0f, baseOffsetY = 0f;
    private float baseDist = 0f;
    private float baseMidX = 0f, baseMidY = 0f;

    public GestureController(Listener listener) {
        this.listener = listener;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // ---- 状态 ----
    public float getScale() { return scale; }
    public float getOffsetX() { return offsetX; }
    public float getOffsetY() { return offsetY; }

    public void setTransform(float ox, float oy, float s) {
        this.offsetX = ox;
        this.offsetY = oy;
        this.scale = s;
        if (listener != null) listener.onTransform();
    }

    public void reset() {
        scale = 1f;
        offsetX = 0f;
        offsetY = 0f;
        if (listener != null) listener.onTransform();
    }

    // ---- 触摸 ----
    private float distance(MotionEvent event) {
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            downTouchX = event.getX();
            downTouchY = event.getY();
            downOffsetX = offsetX;
            downOffsetY = offsetY;
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (event.getPointerCount() >= 2) {
                baseDist = distance(event);
                baseMidX = (event.getX(0) + event.getX(1)) / 2f;
                baseMidY = (event.getY(0) + event.getY(1)) / 2f;
                baseScale = scale;
                baseOffsetX = offsetX;
                baseOffsetY = offsetY;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (event.getPointerCount() == 1) {
                float dx = event.getX() - downTouchX;
                float dy = event.getY() - downTouchY;
                offsetX = downOffsetX + dx;
                offsetY = downOffsetY + dy;
                if (listener != null) listener.onTransform();
            } else if (event.getPointerCount() == 2) {
                float newDist = distance(event);
                if (newDist < 10f || baseDist < 10f) return true;

                float newScale = baseScale * (newDist / baseDist);
                if (newScale < 0.01f) newScale = 0.01f;
                if (newScale > 500f) newScale = 500f;

                float cx = (event.getX(0) + event.getX(1)) / 2f;
                float cy = (event.getY(0) + event.getY(1)) / 2f;

                double worldX = (baseMidX - baseOffsetX) / baseScale;
                double worldY = -(baseMidY - baseOffsetY) / baseScale;

                scale = newScale;
                offsetX = (float) (cx - worldX * scale);
                offsetY = (float) (cy + worldY * scale);

                if (listener != null) listener.onTransform();
            }
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_UP) {
            int remainIdx = (event.getActionIndex() == 0) ? 1 : 0;
            if (remainIdx < event.getPointerCount()) {
                downTouchX = event.getX(remainIdx);
                downTouchY = event.getY(remainIdx);
                downOffsetX = offsetX;
                downOffsetY = offsetY;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL) {
            return true;
        }
        return false;
    }
}