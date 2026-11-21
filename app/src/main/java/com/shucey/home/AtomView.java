package com.shucey.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * 立体の原子模型。各電子殻を傾いた軌道として描き、電子が周回する。ドラッグで回せる。
 * 内側から K, L, M … の順で、点の数がその殻の電子数。
 */
public class AtomView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] shells = {};
    private String symbol = "";
    private int color = 0xFFFFFFFF;
    private float yaw = 0.5f, pitch = 1.05f, spin, lastT, lastX, lastY;
    private static final int SEG = 56;
    private final float[] ring = new float[(SEG + 1) * 3];
    private RadialGradient glow, ball;
    private float cachedCore, cachedCx, cachedCy;
    private int cachedColor;

    public AtomView(Context c) { super(c); }

    void bind(Element e) {
        shells = e.shells;
        symbol = e.symbol;
        color = PeriodicTableView.categoryColor(e.category);
        invalidate();
    }

    // 殻 i の軌道面の傾き（殻ごとに向きを変えて立体感を出す）
    private static float tiltA(int i) { return 0.55f * (float) Math.sin(i * 2.4 + 0.4); }
    private static float tiltB(int i) { return 0.55f * (float) Math.cos(i * 1.7 + 1.0); }

    /** 軌道面上の角度 th の点を視点座標へ。out = {x, y, z}（z が大きいほど手前）。 */
    private void point(int i, float r, double th, float[] out, int o) {
        float x = r * (float) Math.cos(th), y = 0, z = r * (float) Math.sin(th);
        // 軌道面の傾き: X 軸まわり a、Z 軸まわり b
        float a = tiltA(i), b = tiltB(i);
        float y1 = y * (float) Math.cos(a) - z * (float) Math.sin(a), z1 = y * (float) Math.sin(a) + z * (float) Math.cos(a);
        float x2 = x * (float) Math.cos(b) - y1 * (float) Math.sin(b), y2 = x * (float) Math.sin(b) + y1 * (float) Math.cos(b);
        // 全体の回転: Y 軸まわり yaw → X 軸まわり pitch
        float x3 = x2 * (float) Math.cos(yaw) + z1 * (float) Math.sin(yaw), z3 = -x2 * (float) Math.sin(yaw) + z1 * (float) Math.cos(yaw);
        float y4 = y2 * (float) Math.cos(pitch) - z3 * (float) Math.sin(pitch), z4 = y2 * (float) Math.sin(pitch) + z3 * (float) Math.cos(pitch);
        out[o] = x3;
        out[o + 1] = y4;
        out[o + 2] = z4;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = shells.length;
        if (n == 0) return;
        float now = Fx.time();
        if (Fx.awake()) {
            float dt = Math.min(0.05f, now - lastT);
            spin += dt;
            yaw += dt * 0.25f;
        }
        lastT = now;

        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float max = Math.min(cx, cy) - Ui.dpf(4);
        float core = max * 0.17f;
        float step = (max - core) / n;
        float focal = max * 4.5f;

        for (int pass = 0; pass < 2; pass++) { // 0 = 奥側、1 = 手前側。間に原子核を描く
            boolean front = pass == 1;
            for (int i = 0; i < n; i++) {
                float r = core + step * (i + 0.8f);
                // 軌道の線
                for (int k = 0; k <= SEG; k++) point(i, r, 2 * Math.PI * k / SEG, ring, k * 3);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dpf(1));
                p.setShader(null);
                for (int k = 0; k < SEG; k++) {
                    float z = (ring[k * 3 + 2] + ring[k * 3 + 5]) / 2;
                    if ((z >= 0) != front) continue;
                    float s0 = focal / (focal - ring[k * 3 + 2]), s1 = focal / (focal - ring[k * 3 + 5]);
                    p.setColor(Ui.alpha(Ui.onSurfaceVar, front ? 0.55f : 0.22f));
                    canvas.drawLine(cx + ring[k * 3] * s0, cy + ring[k * 3 + 1] * s0,
                            cx + ring[k * 3 + 3] * s1, cy + ring[k * 3 + 4] * s1, p);
                }
                // 電子
                int count = shells[i];
                float dot = Math.min(Ui.dpf(3f), (float) (Math.PI * r / count) * 0.6f);
                double phase = spin * 1.5 / Math.pow(i + 1, 0.7) * (i % 2 == 0 ? 1 : -1);
                p.setStyle(Paint.Style.FILL);
                for (int k = 0; k < count; k++) {
                    point(i, r, phase + 2 * Math.PI * k / count, ring, 0);
                    if ((ring[2] >= 0) != front) continue;
                    float s = focal / (focal - ring[2]);
                    float ex = cx + ring[0] * s, ey = cy + ring[1] * s;
                    float depth = 0.5f + 0.5f * (ring[2] / r); // 0 = 奥, 1 = 手前
                    p.setColor(Ui.alpha(color, 0.10f + 0.20f * depth));
                    canvas.drawCircle(ex, ey, dot * s * 2.4f, p); // にじみ
                    p.setColor(Ui.lerp(Ui.alpha(Ui.onSurface, 0.45f), 0xFFFFFFFF, depth));
                    canvas.drawCircle(ex, ey, dot * s * (0.75f + 0.35f * depth), p);
                }
            }
            if (!front) drawNucleus(canvas, cx, cy, core);
        }
        if (Fx.awake()) postInvalidateOnAnimation();
    }

    private void drawNucleus(Canvas canvas, float cx, float cy, float core) {
        p.setStyle(Paint.Style.FILL);
        if (glow == null || cachedCore != core || cachedColor != color || cachedCx != cx || cachedCy != cy) {
            glow = new RadialGradient(cx, cy, core * 2.6f,
                    new int[]{Ui.alpha(color, 0.55f), Ui.alpha(color, 0f)}, null, Shader.TileMode.CLAMP);
            ball = new RadialGradient(cx - core * 0.3f, cy - core * 0.35f, core * 1.3f,
                    new int[]{Ui.lerp(color, 0xFFFFFFFF, 0.55f), color, Ui.lerp(color, 0xFF000000, 0.35f)},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            cachedCore = core; cachedColor = color; cachedCx = cx; cachedCy = cy;
        }
        p.setShader(glow);
        canvas.drawCircle(cx, cy, core * 2.6f, p);
        p.setShader(ball);
        canvas.drawCircle(cx, cy, core, p);
        p.setShader(null);
        p.setColor(Ui.isLight(color) ? 0xFF0B0E14 : 0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(core * (symbol.length() > 1 ? 0.85f : 1.05f));
        canvas.drawText(symbol, cx, cy + p.getTextSize() * 0.36f, p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                lastX = ev.getX();
                lastY = ev.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                yaw += (ev.getX() - lastX) / Ui.dpf(70);
                pitch = Math.max(0.1f, Math.min(1.5f, pitch + (ev.getY() - lastY) / Ui.dpf(70)));
                lastX = ev.getX();
                lastY = ev.getY();
                Fx.wake();
                invalidate();
                return true;
        }
        return super.onTouchEvent(ev);
    }
}
