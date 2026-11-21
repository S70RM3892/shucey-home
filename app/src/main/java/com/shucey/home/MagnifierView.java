package com.shucey.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** 指の上に出る拡大表示。指で隠れるセルの中身を見せる。 */
public class MagnifierView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path tail = new Path();
    private Element element;
    private boolean hidden; // クイズ中は中身を見せない
    private float ax, ay;   // 吹き出しの先端（この View 内）
    private float show;     // 0..1
    private boolean visible;

    public MagnifierView(Context c) {
        super(c);
        t.setTextAlign(Paint.Align.CENTER);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // 影のため
    }

    void show(Element e, float x, float y, boolean hideContent) {
        element = e;
        hidden = hideContent;
        ax = x;
        ay = y;
        if (!visible) {
            visible = true;
            show = 0.6f;
        }
        postInvalidateOnAnimation();
    }

    void hide() {
        visible = false;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 目標値に寄せていく簡易アニメーション
        float target = visible ? 1f : 0f;
        show += (target - show) * 0.35f;
        if (Math.abs(target - show) > 0.01f) postInvalidateOnAnimation();
        else show = target;
        if (show <= 0.01f || element == null) return;

        float w = Ui.dpf(84), h = Ui.dpf(96), gap = Ui.dpf(14), m = Ui.dpf(8);
        float cx = Math.max(m + w / 2, Math.min(getWidth() - m - w / 2, ax));
        float bottom = ay - gap;
        if (bottom - h < m) bottom = m + h; // 画面上端にぶつかるなら下げる
        r.set(cx - w / 2, bottom - h, cx + w / 2, bottom);

        int color = hidden ? Ui.surfaceHigh : PeriodicTableView.categoryColor(element.category);
        int fg = Ui.isLight(color) ? 0xFF0B0E14 : 0xFFFFFFFF;

        canvas.save();
        canvas.scale(show, show, ax, ay);
        p.setColor(Ui.alpha(color, Math.min(1f, show * 1.2f)));
        p.setShadowLayer(Ui.dpf(12), 0, Ui.dpf(4), 0x80000000);
        float rad = Ui.dpf(20);
        canvas.drawRoundRect(r, rad, rad, p);
        p.clearShadowLayer();
        // しっぽ
        tail.reset();
        float tx = Math.max(r.left + rad, Math.min(r.right - rad, ax));
        tail.moveTo(tx - Ui.dpf(9), r.bottom - 1);
        tail.lineTo(tx, r.bottom + Ui.dpf(9));
        tail.lineTo(tx + Ui.dpf(9), r.bottom - 1);
        tail.close();
        canvas.drawPath(tail, p);

        t.setColor(fg);
        if (hidden) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextSize(Ui.dpf(40));
            canvas.drawText("？", r.centerX(), r.centerY() + Ui.dpf(14), t);
        } else {
            t.setTypeface(Typeface.DEFAULT);
            t.setTextSize(Ui.dpf(13));
            t.setAlpha(200);
            canvas.drawText(String.valueOf(element.number), r.centerX(), r.top + Ui.dpf(22), t);
            t.setAlpha(255);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextSize(Ui.dpf(36));
            canvas.drawText(element.symbol, r.centerX(), r.top + Ui.dpf(60), t);
            t.setTypeface(Typeface.DEFAULT);
            float size = Ui.dpf(element.ja.length() > 6 ? 9 : element.ja.length() > 4 ? 11 : 13);
            t.setTextSize(size);
            canvas.drawText(element.ja, r.centerX(), r.top + Ui.dpf(82), t);
        }
        canvas.restore();
    }
}
