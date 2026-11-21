package com.shucey.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** 炎色反応の炎。1 本でも複数並べても使える。色は実際の炎色に近づけてある。 */
public class FlameView extends View {
    private int[] colors = {};
    private String[] labels = {};
    private Shader[] shaders = {};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FlameView(Context c) {
        super(c);
        text.setTextAlign(Paint.Align.CENTER);
    }

    /** labels は炎の下に書く（null なら無し）。 */
    void setFlames(int[] colors, String[] labels) {
        this.colors = colors;
        this.labels = labels;
        shaders = new Shader[colors.length];
        for (int i = 0; i < colors.length; i++) shaders[i] = Fx.shader(Fx.FLAME);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = colors.length;
        if (n == 0) return;
        float labelH = labels == null ? 0 : Ui.dpf(34);
        float w = getWidth() / (float) n, h = getHeight() - labelH;
        float fw = Math.min(w, h * 0.8f);
        boolean gpu = canvas.isHardwareAccelerated();
        for (int i = 0; i < n; i++) {
            float cx = w * (i + 0.5f);
            canvas.save();
            canvas.translate(cx - fw / 2, 0);
            Shader sh = shaders[i];
            if (sh != null && gpu) {
                Fx.set(sh, "uRes", fw, h);
                Fx.set(sh, "uTime", Fx.time());
                Fx.set(sh, "uSeed", i * 7.3f + 1f);
                Fx.color(sh, "uColor", colors[i]);
                paint.setShader(sh);
                canvas.drawRect(0, 0, fw, h, paint);
            } else {
                // シェーダーの使えない端末: 色のついた光で代用
                paint.setShader(new RadialGradient(fw / 2, h * 0.7f, fw * 0.5f,
                        new int[]{0xFFFFFFFF, colors[i], Ui.alpha(colors[i], 0f)}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, fw, h, paint);
            }
            canvas.restore();
            if (labels != null) {
                String[] parts = labels[i].split("\n");
                text.setTypeface(Typeface.DEFAULT_BOLD);
                text.setTextSize(Ui.dpf(14));
                text.setColor(Ui.onSurface);
                canvas.drawText(parts[0], cx, h + Ui.dpf(15), text);
                if (parts.length > 1) {
                    text.setTypeface(Typeface.DEFAULT);
                    text.setTextSize(Ui.dpf(11));
                    text.setColor(colors[i]);
                    canvas.drawText(parts[1], cx, h + Ui.dpf(30), text);
                }
            }
        }
        if (Fx.awake() && gpu) postInvalidateOnAnimation();
    }
}
