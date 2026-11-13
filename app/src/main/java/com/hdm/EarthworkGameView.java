package com.hdm; // 注意：这里换成你项目的真实包名

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import java.util.Random;

public class EarthworkGameView extends View {
    private static final int COLS = 12; // 横断面网格列数（对应左右偏距）
    private static final int ROWS = 20; // 高程网格行数
    private static final int DESIGN_ROW = 13; // 设计路基高程线所在行

    private int[][] grid = new int[ROWS][COLS];
    private int[][] curShape;
    private int curRow, curCol;
    private boolean isExcavator = false;

    private double fillVolume = 0.0;
    private double cutVolume = 0.0;
    private int score = 100;
    private boolean isGameOver = false;

    private float cellSize, offsetX, offsetY;
    private float touchStartX, touchStartY;
    private long touchStartTime;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final int[][][] SHAPES = {
        {{1, 1, 1, 1}},
        {{1, 1}, {1, 1}},
        {{0, 1, 0}, {1, 1, 1}},
        {{1, 0}, {1, 0}, {1, 1}},
        {{1}}
    };

    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (!isGameOver) {
                stepDown();
                invalidate();
                handler.postDelayed(this, 550);
            }
        }
    };

    public EarthworkGameView(Context context) {
        super(context);
        dashPaint.setColor(Color.RED);
        dashPaint.setStrokeWidth(4f);
        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setPathEffect(new DashPathEffect(new float[]{15, 10}, 0));
        initTerrain();
        spawnPiece();
    }

    private void initTerrain() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) grid[r][c] = 0;
        }
        int groundHeight = DESIGN_ROW + 2;
        for (int c = 0; c < COLS; c++) {
            groundHeight += random.nextInt(3) - 1;
            groundHeight = Math.max(DESIGN_ROW - 2, Math.min(ROWS - 1, groundHeight));
            for (int r = groundHeight; r < ROWS; r++) {
                grid[r][c] = (r < DESIGN_ROW) ? 2 : 1;
            }
        }
        fillVolume = 0;
        cutVolume = 0;
        score = 100;
        isGameOver = false;
    }

    private void spawnPiece() {
        isExcavator = random.nextInt(100) < 35;
        int[][] template = isExcavator ? new int[][]{{1, 1}, {1, 0}} : SHAPES[random.nextInt(SHAPES.length)];
        curShape = new int[template.length][template[0].length];
        for (int i = 0; i < template.length; i++) {
            System.arraycopy(template[i], 0, curShape[i], 0, template[0].length);
        }
        curRow = 0;
        curCol = COLS / 2 - curShape[0].length / 2;

        if (!isValidPosition(curRow, curCol, curShape)) {
            isGameOver = true;
            showResultDialog();
        }
    }

    private void stepDown() {
        if (isValidPosition(curRow + 1, curCol, curShape)) {
            curRow++;
        } else {
            settlePiece();
            checkLevelingComplete();
            spawnPiece();
        }
    }

    private void settlePiece() {
        for (int r = 0; r < curShape.length; r++) {
            for (int c = 0; c < curShape[0].length; c++) {
                if (curShape[r][c] == 0) continue;
                int gr = curRow + r;
                int gc = curCol + c;
                if (gr < 0 || gr >= ROWS || gc < 0 || gc >= COLS) continue;

                if (isExcavator) {
                    if (gr < ROWS - 1 && grid[gr + 1][gc] != 0) {
                        if (grid[gr + 1][gc] == 2) {
                            cutVolume += 12.5;
                            score += 5;
                        } else {
                            cutVolume += 12.5;
                            score -= 5;
                        }
                        grid[gr + 1][gc] = 0;
                    }
                } else {
                    if (gr >= DESIGN_ROW) {
                        grid[gr][gc] = 1;
                        fillVolume += 12.5;
                        score += 5;
                    } else {
                        grid[gr][gc] = 2;
                        score -= 8;
                    }
                }
            }
        }
    }

    private void checkLevelingComplete() {
        boolean allFlat = true;
        for (int c = 0; c < COLS; c++) {
            if (grid[DESIGN_ROW][c] != 1 || grid[DESIGN_ROW - 1][c] != 0) {
                allFlat = false;
                break;
            }
        }
        if (allFlat) {
            score += 50;
            for (int r = ROWS - 1; r >= 2; r--) {
                System.arraycopy(grid[r - 2], 0, grid[r], 0, COLS);
            }
            for (int r = 0; r < 2; r++) {
                for (int c = 0; c < COLS; c++) grid[r][c] = 0;
            }
        }
    }

    private boolean isValidPosition(int targetRow, int targetCol, int[][] shape) {
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[0].length; c++) {
                if (shape[r][c] == 0) continue;
                int gr = targetRow + r;
                int gc = targetCol + c;
                if (gc < 0 || gc >= COLS || gr >= ROWS) return false;
                if (gr >= 0 && grid[gr][gc] != 0) return false;
            }
        }
        return true;
    }

    private void rotatePiece() {
        int rows = curShape.length, cols = curShape[0].length;
        int[][] rotated = new int[cols][rows];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                rotated[c][rows - 1 - r] = curShape[r][c];
            }
        }
        if (isValidPosition(curRow, curCol, rotated)) {
            curShape = rotated;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float topMargin = h * 0.14f;
        cellSize = Math.min((w * 0.92f) / COLS, (h - topMargin - 40) / ROWS);
        offsetX = (w - cellSize * COLS) / 2f;
        offsetY = topMargin;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.parseColor("#1E2229"));

        paint.setColor(Color.WHITE);
        paint.setTextSize(36f);
        canvas.drawText("断面整平施工 | 评分: " + score, offsetX, offsetY * 0.42f, paint);
        paint.setTextSize(30f);
        paint.setColor(Color.parseColor("#A5D6A7"));
        canvas.drawText(String.format("填方: %.1fm³   挖方: %.1fm³", fillVolume, cutVolume), offsetX, offsetY * 0.78f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(Color.parseColor("#2E3440"));
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                float left = offsetX + c * cellSize;
                float top = offsetY + r * cellSize;
                canvas.drawRect(left, top, left + cellSize, top + cellSize, paint);
            }
        }

        paint.setStyle(Paint.Style.FILL);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c] == 0) continue;
                paint.setColor(grid[r][c] == 1 ? Color.parseColor("#8D6E63") : Color.parseColor("#E64A19"));
                drawBlock(canvas, r, c);
            }
        }

        if (!isGameOver && curShape != null) {
            paint.setColor(isExcavator ? Color.parseColor("#29B6F6") : Color.parseColor("#FFB74D"));
            for (int r = 0; r < curShape.length; r++) {
                for (int c = 0; c < curShape[0].length; c++) {
                    if (curShape[r][c] == 1) {
                        drawBlock(canvas, curRow + r, curCol + c);
                    }
                }
            }
        }

        float designY = offsetY + DESIGN_ROW * cellSize;
        canvas.drawLine(offsetX - 15, designY, offsetX + COLS * cellSize + 15, designY, dashPaint);
        paint.setColor(Color.RED);
        paint.setTextSize(26f);
        canvas.drawText("设计高程 H=100.000", offsetX + 8, designY - 8, paint);
    }

    private void drawBlock(Canvas canvas, int r, int c) {
        float pad = 2f;
        float left = offsetX + c * cellSize + pad;
        float top = offsetY + r * cellSize + pad;
        canvas.drawRect(left, top, left + cellSize - pad * 2, top + cellSize - pad * 2, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isGameOver) return true;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getX();
                touchStartY = event.getY();
                touchStartTime = System.currentTimeMillis();
                return true;
            case MotionEvent.ACTION_UP:
                float dx = event.getX() - touchStartX;
                float dy = event.getY() - touchStartY;
                long dt = System.currentTimeMillis() - touchStartTime;
                if (Math.abs(dx) < 25 && Math.abs(dy) < 25 && dt < 300) {
                    rotatePiece();
                } else if (Math.abs(dx) > Math.abs(dy)) {
                    int step = dx > 0 ? 1 : -1;
                    if (isValidPosition(curRow, curCol + step, curShape)) curCol += step;
                } else if (dy > 60) {
                    while (isValidPosition(curRow + 1, curCol, curShape)) curRow++;
                    stepDown();
                }
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void showResultDialog() {
        String eval = score >= 150 ? "【特级测量员】路基平整如镜，监理直接签字！" :
                      score >= 80  ? "【合格施工员】土方基本平衡，略有超欠挖。" :
                      "【返工警告】超填严重，请重测横断面！";
        new AlertDialog.Builder(getContext())
                .setTitle("横断面竣工结算")
                .setMessage(String.format("最终评分：%d\n累计填方：%.1f m³\n累计挖方：%.1f m³\n\n%s",
                        score, fillVolume, cutVolume, eval))
                .setPositiveButton("重新放样", (d, w) -> {
                    initTerrain();
                    spawnPiece();
                    handler.post(gameLoop);
                })
                .setNegativeButton("返回干活", null)
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.post(gameLoop);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(gameLoop);
    }
}