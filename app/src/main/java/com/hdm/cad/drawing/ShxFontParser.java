package com.hdm.cad.drawing;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShxFontParser {
    public static class Glyph {
        public List<float[]> strokes;
        public float advance;

        public Glyph(List<float[]> strokes, float advance) {
            this.strokes = strokes;
            this.advance = advance;
        }
    }

    private final Map<String, Glyph> fontMap = new HashMap<>();
    // 【修复】将 isBigFont 提为成员变量，避免作用域找不到
    private boolean isBigFont = false;

    public ShxFontParser(InputStream... streams) {
        for (InputStream is : streams) {
            if (is != null) {
                try {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    int nRead;
                    byte[] data = new byte[16384];
                    while ((nRead = is.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    buffer.flush();
                    parseFontData(buffer.toByteArray());
                    is.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void parseFontData(byte[] data) {
        if (data.length < 128) return;
        int headerEnd = -1;
        for (int i = 0; i < data.length - 2; i++) {
            if ((data[i] & 0xFF) == 0x0D && (data[i + 1] & 0xFF) == 0x0A && (data[i + 2] & 0xFF) == 0x1A) {
                headerEnd = i + 3;
                break;
            }
        }
        if (headerEnd == -1) return;
        String headerStr = new String(data, 0, headerEnd, StandardCharsets.US_ASCII);
        // 赋值成员变量
        this.isBigFont = headerStr.toLowerCase().contains("bigfont");
        
        int pos = headerEnd;

        if (this.isBigFont) {
            int itemLen = readInt16(data, pos); pos += 2;
            int count = readInt16(data, pos); pos += 2;
            int changeNumber = readInt16(data, pos); pos += 2;
            if (count <= 0) return;
            pos += changeNumber * 4;

            List<int[]> items = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int code = readUint16(data, pos); pos += 2;
                int length = readUint16(data, pos); pos += 2;
                int offset = readInt32(data, pos); pos += 4;
                if (code != 0 || length != 0 || offset != 0) items.add(new int[]{code, length, offset});
            }
            for (int[] item : items) {
                int code = item[0], length = item[1], offset = item[2];
                if (offset >= 0 && offset + length <= data.length) {
                    byte[] glyphBytes = new byte[length];
                    System.arraycopy(data, offset, glyphBytes, 0, length);
                    byte[] bytecode = splitShapeNameAndBytecode(glyphBytes);
                    Glyph glyph = parseShape(bytecode, false);
                    if (glyph != null && !glyph.strokes.isEmpty()) {
                        String ch = "";
                        try { ch = new String(new byte[]{(byte) (code >> 8), (byte) code}, "GBK"); } catch (Exception ignored) {}
                        if (!ch.isEmpty()) fontMap.put(ch, glyph);
                    }
                }
            }
        } else {
            pos += 4; 
            int count = readInt16(data, pos); pos += 2;
            if (count <= 0) return;
            List<int[]> items = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int code = readUint16(data, pos); pos += 2;
                int length = readUint16(data, pos); pos += 2;
                if (length > 0) items.add(new int[]{code, length});
            }
            for (int[] item : items) {
                int code = item[0], length = item[1];
                if (pos + length <= data.length) {
                    byte[] glyphBytes = new byte[length];
                    System.arraycopy(data, pos, glyphBytes, 0, length);
                    pos += length;
                    byte[] bytecode = splitShapeNameAndBytecode(glyphBytes);
                    Glyph glyph = parseShape(bytecode, true);
                    if (glyph != null && !glyph.strokes.isEmpty()) {
                        String ch = String.valueOf((char) code);
                        fontMap.put(ch, glyph);
                    }
                } else { break; }
            }
        }
    }

    private Glyph parseShape(byte[] data, boolean initialPenDown) {
        List<float[]> polylines = new ArrayList<>();
        float x = 0, y = 0, scale = 1.0f;
        boolean isPenDown = initialPenDown;
        List<float[]> currentPolyline = new ArrayList<>();
        float[][] sp = new float[4][2];
        int spIdx = 0;

        if (isPenDown) currentPolyline.add(new float[]{x, y});

        for (int i = 0; i < data.length; i++) {
            int cb = data[i] & 0xFF;
            if (cb <= 0x0F) {
                switch (cb) {
                    case 0: // END_OF_SHAPE
                        flushPolyline(polylines, currentPolyline);
                        isPenDown = false;
                        break;
                    case 1: // PEN_DOWN
                        if (!isPenDown) currentPolyline.add(new float[]{x, y});
                        isPenDown = true;
                        break;
                    case 2: // PEN_UP
                        isPenDown = false;
                        flushPolyline(polylines, currentPolyline);
                        break;
                    case 3: // DIVIDE_VECTOR
                        i++; if (i < data.length) scale /= byteToSByte(data[i]);
                        break;
                    case 4: // MULTIPLY_VECTOR
                        i++; if (i < data.length) scale *= byteToSByte(data[i]);
                        break;
                    case 5: // PUSH_STACK
                        if (spIdx < 4) { sp[spIdx][0] = x; sp[spIdx][1] = y; spIdx++; }
                        break;
                    case 6: // POP_STACK
                        if (spIdx > 0) {
                            spIdx--; x = sp[spIdx][0]; y = sp[spIdx][1];
                            flushPolyline(polylines, currentPolyline);
                            if (isPenDown) currentPolyline.add(new float[]{x, y});
                        }
                        break;
                    case 7: // DRAW_SUBSHAPE
                        i++;
                        break;
                    case 8: // XY_DISPLACEMENT
                        if (i + 2 < data.length) {
                            float dx = byteToSByte(data[++i]) * scale;
                            float dy = byteToSByte(data[++i]) * scale;
                            x += dx; y += dy;
                            if (isPenDown) currentPolyline.add(new float[]{x, y});
                        }
                        break;
                    case 9: // POLY_XY_DISPLACEMENT
                        while (i + 2 < data.length) {
                            float dx = byteToSByte(data[++i]);
                            float dy = byteToSByte(data[++i]);
                            if (dx == 0 && dy == 0) break;
                            x += dx * scale; y += dy * scale;
                            if (isPenDown) currentPolyline.add(new float[]{x, y});
                        }
                        break;
                    case 10: { // OCTANT_ARC
                        if (i + 2 < data.length) {
                            float radius = (data[++i] & 0xFF) * scale;
                            int flag = byteToSByte(data[++i]);
                            int startOctant = (flag & 0x70) >> 4;
                            int octantCount = flag & 0x07;
                            boolean isClockwise = flag < 0;
                            
                            float startRadian = (float)(Math.PI / 4.0 * startOctant);
                            float cx = x - (float)(Math.cos(startRadian) * radius);
                            float cy = y - (float)(Math.sin(startRadian) * radius);
                            
                            List<float[]> arcPoints = tessellateOctant(cx, cy, radius, startOctant, octantCount, isClockwise);
                            
                            if (isPenDown) {
                                if (!currentPolyline.isEmpty()) currentPolyline.remove(currentPolyline.size() - 1);
                                currentPolyline.addAll(arcPoints);
                            }
                            if (!arcPoints.isEmpty()) {
                                float[] last = arcPoints.get(arcPoints.size() - 1);
                                x = last[0]; y = last[1];
                            }
                        }
                        break;
                    }
                    case 11: { // FRACTIONAL_ARC
                        if (i + 4 < data.length) {
                            int startOffset = data[++i] & 0xFF;
                            int endOffset = data[++i] & 0xFF;
                            int hr = data[++i] & 0xFF;
                            int lr = data[++i] & 0xFF;
                            float radius = ((hr * 255) + lr) * scale;
                            int flag = byteToSByte(data[++i]);
                            int startOctant = (flag & 0x70) >> 4;
                            int octantCount = flag & 0x07;
                            if (octantCount == 0) octantCount = 8;
                            if (endOffset != 0) octantCount--;
                            
                            float pi_4 = (float)(Math.PI / 4.0);
                            float span = pi_4 * octantCount;
                            float delta = (float)(Math.PI / 18.0);
                            float sign = 1;
                            if (flag < 0) {
                                delta = -delta;
                                span = -span;
                                sign = -1;
                            }
                            
                            float startRadian = pi_4 * startOctant;
                            float endRadian = startRadian + span;
                            startRadian += (pi_4 * startOffset / 256f) * sign;
                            endRadian += (pi_4 * endOffset / 256f) * sign;
                            
                            float cx = x - radius * (float)Math.cos(startRadian);
                            float cy = y - radius * (float)Math.sin(startRadian);
                            
                            if (isPenDown) {
                                float currentRadian = startRadian;
                                currentPolyline.add(new float[]{cx + radius * (float)Math.cos(currentRadian), cy + radius * (float)Math.sin(currentRadian)});
                                if (delta > 0) {
                                    while (currentRadian + delta < endRadian) {
                                        currentRadian += delta;
                                        currentPolyline.add(new float[]{cx + radius * (float)Math.cos(currentRadian), cy + radius * (float)Math.sin(currentRadian)});
                                    }
                                } else {
                                    while (currentRadian + delta > endRadian) {
                                        currentRadian += delta;
                                        currentPolyline.add(new float[]{cx + radius * (float)Math.cos(currentRadian), cy + radius * (float)Math.sin(currentRadian)});
                                    }
                                }
                                currentPolyline.add(new float[]{cx + radius * (float)Math.cos(endRadian), cy + radius * (float)Math.sin(endRadian)});
                            }
                            x = cx + radius * (float)Math.cos(endRadian);
                            y = cy + radius * (float)Math.sin(endRadian);
                        }
                        break;
                    }
                    case 12: { // BULGE_ARC
                        if (i + 3 < data.length) {
                            float dx = byteToSByte(data[++i]) * scale;
                            float dy = byteToSByte(data[++i]) * scale;
                            float bulge = byteToSByte(data[++i]);
                            
                            float endX = x + dx;
                            float endY = y + dy;
                            
                            if (isPenDown) {
                                if (bulge == 0) {
                                    currentPolyline.add(new float[]{endX, endY});
                                } else {
                                    List<float[]> arcPoints = tessellateBulge(x, y, endX, endY, bulge / 127.0f);
                                    for (int k = 1; k < arcPoints.size(); k++) {
                                        currentPolyline.add(arcPoints.get(k));
                                    }
                                }
                            }
                            x = endX; y = endY;
                        }
                        break;
                    }
                    case 13: { // POLY_BULGE_ARC
                        while (i + 2 < data.length) {
                            float dx = byteToSByte(data[++i]) * scale;
                            float dy = byteToSByte(data[++i]) * scale;
                            if (dx == 0 && dy == 0) break;
                            float bulge = byteToSByte(data[++i]);
                            
                            float endX = x + dx;
                            float endY = y + dy;
                            
                            if (isPenDown) {
                                if (bulge == 0) {
                                    currentPolyline.add(new float[]{endX, endY});
                                } else {
                                    List<float[]> arcPoints = tessellateBulge(x, y, endX, endY, bulge / 127.0f);
                                    for (int k = 1; k < arcPoints.size(); k++) {
                                        currentPolyline.add(arcPoints.get(k));
                                    }
                                }
                            }
                            x = endX; y = endY;
                        }
                        break;
                    }
                    case 14: { // DUAL_ORIENTATION_FLAG
                        i++;
                        if (i < data.length) {
                            int skipCmd = data[i] & 0xFF;
                            if (skipCmd == 0x00 || skipCmd == 0x01 || skipCmd == 0x02 || skipCmd == 0x05 || skipCmd == 0x06 || skipCmd == 0x0E) {
                                // No params
                            } else if (skipCmd == 0x03 || skipCmd == 0x04) {
                                i++;
                            } else if (skipCmd == 0x07) {
                                i += (isBigFont ? 2 : 1); // 【修复】这里使用成员变量 isBigFont
                            } else if (skipCmd == 0x08) {
                                i += 2;
                            } else if (skipCmd == 0x09) {
                                while (i + 2 < data.length) {
                                    if (data[i + 1] == 0 && data[i + 2] == 0) { i += 2; break; }
                                    i += 2;
                                }
                            } else if (skipCmd == 0x0A) {
                                i += 2;
                            } else if (skipCmd == 0x0B) {
                                i += 5;
                            } else if (skipCmd == 0x0C) {
                                i += 3;
                            } else if (skipCmd == 0x0D) {
                                while (i + 3 < data.length) {
                                    if (data[i + 1] == 0 && data[i + 2] == 0) { i += 3; break; }
                                    i += 3;
                                }
                            }
                        }
                        break;
                    }
                }
            } else {
                int len = (cb & 0xF0) >> 4;
                int dir = cb & 0x0F;
                float dx = 0, dy = 0;
                switch (dir) {
                    case 0: dx = 1; dy = 0; break;
                    case 1: dx = 1; dy = 0.5f; break;
                    case 2: dx = 1; dy = 1; break;
                    case 3: dx = 0.5f; dy = 1; break;
                    case 4: dx = 0; dy = 1; break;
                    case 5: dx = -0.5f; dy = 1; break;
                    case 6: dx = -1; dy = 1; break;
                    case 7: dx = -1; dy = 0.5f; break;
                    case 8: dx = -1; dy = 0; break;
                    case 9: dx = -1; dy = -0.5f; break;
                    case 10: dx = -1; dy = -1; break;
                    case 11: dx = -0.5f; dy = -1; break;
                    case 12: dx = 0; dy = -1; break;
                    case 13: dx = 0.5f; dy = -1; break;
                    case 14: dx = 1; dy = -1; break;
                    case 15: dx = 1; dy = -0.5f; break;
                }
                x += dx * len * scale;
                y += dy * len * scale;
                if (isPenDown) currentPolyline.add(new float[]{x, y});
            }
        }

        return new Glyph(polylines, x);
    }

    private List<float[]> tessellateOctant(float cx, float cy, float r, int startOct, int octCount, boolean isClockwise) {
        List<float[]> points = new ArrayList<>();
        float startAngle = (float)(Math.PI / 4 * startOct);
        int spanCount = (octCount == 0) ? 8 : octCount;
        float span = (float)(Math.PI / 4 * spanCount) * (isClockwise ? -1 : 1);
        float endAngle = startAngle + span;

        float includedAngle = Math.abs(endAngle - startAngle);
        float circleSpan = (float)(Math.PI / 18);
        int numSegments = Math.max(1, (int)Math.floor(includedAngle / circleSpan));

        points.add(new float[]{cx + r * (float)Math.cos(startAngle), cy + r * (float)Math.sin(startAngle)});
        for (int i = 1; i < numSegments; i++) {
            float t = (float)i / numSegments;
            float angle = isClockwise ? startAngle - t * includedAngle : startAngle + t * includedAngle;
            points.add(new float[]{cx + r * (float)Math.cos(angle), cy + r * (float)Math.sin(angle)});
        }
        points.add(new float[]{cx + r * (float)Math.cos(endAngle), cy + r * (float)Math.sin(endAngle)});
        return points;
    }

    private List<float[]> tessellateBulge(float startX, float startY, float endX, float endY, float bulge) {
        List<float[]> points = new ArrayList<>();
        bulge = Math.max(-1, Math.min(1, bulge));

        float distanceX = endX - startX;
        float distanceY = endY - startY;
        float D = (float)Math.sqrt(distanceX * distanceX + distanceY * distanceY);
        float H = (Math.abs(bulge) * D) / 2;

        if (H == 0) {
            points.add(new float[]{startX, startY});
            points.add(new float[]{endX, endY});
            return points;
        }

        boolean isClockwise = bulge < 0;
        float theta = 4 * (float)Math.atan(Math.abs(bulge));
        float radius = D / (2 * (float)Math.sin(theta / 2));

        float midX = startX + distanceX / 2;
        float midY = startY + distanceY / 2;

        float nx = -distanceY;
        float ny = distanceX;
        float normalLen = (float)Math.sqrt(nx * nx + ny * ny);
        nx /= normalLen; ny /= normalLen;
        nx *= Math.abs(radius * (float)Math.cos(theta / 2));
        ny *= Math.abs(radius * (float)Math.cos(theta / 2));

        float cx = isClockwise ? midX - nx : midX + nx;
        float cy = isClockwise ? midY - ny : midY + ny;

        float startAngle = (float)Math.atan2(startY - cy, startX - cx);
        float endAngle = (float)Math.atan2(endY - cy, endX - cx);

        if (isClockwise) {
            if (endAngle >= startAngle) endAngle -= 2 * Math.PI;
        } else {
            if (endAngle <= startAngle) endAngle += 2 * Math.PI;
        }

        float includedAngle = Math.abs(endAngle - startAngle);
        float circleSpan = (float)(Math.PI / 18);
        int numSegments = Math.max(1, (int)Math.floor(includedAngle / circleSpan));

        points.add(new float[]{startX, startY});
        for (int i = 1; i < numSegments; i++) {
            float t = (float)i / numSegments;
            float angle = isClockwise ? startAngle - t * includedAngle : startAngle + t * includedAngle;
            points.add(new float[]{cx + radius * (float)Math.cos(angle), cy + radius * (float)Math.sin(angle)});
        }
        points.add(new float[]{endX, endY});
        return points;
    }

    private void flushPolyline(List<float[]> polylines, List<float[]> currentPolyline) {
        if (currentPolyline.size() > 1) {
            for (int i = 0; i < currentPolyline.size() - 1; i++) {
                float[] p1 = currentPolyline.get(i);
                float[] p2 = currentPolyline.get(i + 1);
                polylines.add(new float[]{p1[0], p1[1], p2[0], p2[1]});
            }
        }
        currentPolyline.clear();
    }

    private byte[] splitShapeNameAndBytecode(byte[] raw) {
        int nulIdx = -1;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == 0x00) { nulIdx = i; break; }
        }
        if (nulIdx >= 0 && nulIdx < raw.length - 1) {
            byte[] bytecode = new byte[raw.length - nulIdx - 1];
            System.arraycopy(raw, nulIdx + 1, bytecode, 0, bytecode.length);
            return bytecode;
        }
        return raw;
    }

    public static int byteToSByte(byte value) {
        return (value & 127) - ((value & 128) != 0 ? 128 : 0);
    }

    private int readInt16(byte[] d, int p) { return (short) ((d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8)); }
    private int readUint16(byte[] d, int p) { return ((d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8)); }
    private int readInt32(byte[] d, int p) { return ((d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8) | ((d[p + 2] & 0xFF) << 16) | ((d[p + 3] & 0xFF) << 24)); }

    public Glyph getGlyph(String str) { return fontMap.get(str); }
}