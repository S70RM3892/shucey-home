package com.shucey.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/** 電子殻の模式図（ボーア模型）。K 殻から順に電子の数を点で示す。 */
public class ShellView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] shells = {};
    private String symbol = "";
    private int color = 0xFFFFFFFF;
    private float spin = 1f;
    private ValueAnimator anim;

    public ShellView(Context c) { super(c); }

    void bind(Element e) {
        shells = e.shells;
        symbol = e.symbol;
        color = PeriodicTableView.categoryColor(e.category);
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(900);
        anim.setInterpolator(Ui.EASE_OUT);
        anim.addUpdateListener(a -> { spin = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = shells.length;
        if (n == 0) return;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float max = Math.min(cx, cy) - Ui.dpf(3);
        float core = max * 0.2f;
        float step = (max - core) / n;

        // 原子核
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.alpha(color, 0.9f));
        canvas.drawCircle(cx, cy, core, p);
        p.setColor(Ui.isLight(color) ? 0xFF0B0E14 : 0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(core * (symbol.length() > 1 ? 0.85f : 1.05f));
        canvas.drawText(symbol, cx, cy + p.getTextSize() * 0.36f, p);

        for (int i = 0; i < n; i++) {
            float r = core + step * (i + 0.75f);
            float grow = Math.max(0f, Math.min(1f, spin * (n + 2) / (i + 2f)));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dpf(1));
            p.setColor(Ui.alpha(Ui.onSurfaceVar, 0.35f * grow));
            canvas.drawCircle(cx, cy, r, p);

            int count = shells[i];
            float dot = Math.min(Ui.dpf(2.6f), (float) (Math.PI * r / count) * 0.62f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.alpha(Ui.onSurface, grow));
            // 内側ほど大きく回り込んでから止まる
            double offset = -Math.PI / 2 + (1 - spin) * (1.2 + 0.5 * (n - i)) * (i % 2 == 0 ? 1 : -1);
            for (int k = 0; k < count; k++) {
                double ang = offset + 2 * Math.PI * k / count;
                canvas.drawCircle(cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r, dot, p);
            }
        }
    }
}
