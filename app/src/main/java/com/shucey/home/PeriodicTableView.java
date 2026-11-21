package com.shucey.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/** 周期表を描画し、タップされた元素を通知する View。 */
public class PeriodicTableView extends View {

    interface OnElementTapListener {
        void onElementTap(Element e);
    }

    private static final float ROW_GAP = 0.35f;     // 本表と f ブロックの間
    private static final float HEADER = 0.45f;      // 族番号の行
    private static final float ASPECT = 1.25f;      // セル高さ / 幅

    private List<Element> elements;
    private OnElementTapListener listener;
    private Element selected;
    private Element flashOk, flashNg;
    private boolean quizMode;
    private float dim = 1f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float cw, ch, pad;

    public PeriodicTableView(Context c) {
        super(c);
        stroke.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        pad = 2 * getResources().getDisplayMetrics().density;
    }

    void setElements(List<Element> list) { elements = list; invalidate(); }
    void setOnElementTapListener(OnElementTapListener l) { listener = l; }
    void setSelected(Element e) { selected = e; invalidate(); }
    void setQuizMode(boolean q) { quizMode = q; flashOk = flashNg = null; invalidate(); }
    void flash(Element ok, Element ng) { flashOk = ok; flashNg = ng; invalidate(); }
    /** 夜間は色を暗くする (0..1)。 */
    void setDim(float d) { dim = d; invalidate(); }

    static int categoryColor(String c) {
        switch (c) {
            case "alkali": return 0xFFE57373;
            case "alkaline_earth": return 0xFFFFB74D;
            case "transition": return 0xFF90A4AE;
            case "lanthanoid": return 0xFF4DB6AC;
            case "actinoid": return 0xFFF06292;
            case "halogen": return 0xFFFFD54F;
            case "noble": return 0xFFBA68C8;
            case "nonmetal": return 0xFF4FC3F7;
            default: return 0xFF81C784; // 典型金属
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        float cellW = (w - 2 * pad) / 18f;
        float cellH = cellW * ASPECT;
        int h = (int) (cellH * (9 + ROW_GAP + HEADER) + 2 * pad);
        if (MeasureSpec.getMode(hSpec) != MeasureSpec.UNSPECIFIED) {
            int maxH = MeasureSpec.getSize(hSpec);
            if (h > maxH) { // 横画面など高さが足りないときは高さ基準に縮める
                cellH = (maxH - 2 * pad) / (9 + ROW_GAP + HEADER);
                h = maxH;
            }
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float byW = (w - 2 * pad) / 18f;
        float byH = (h - 2 * pad) / (9 + ROW_GAP + HEADER) / ASPECT;
        cw = Math.min(byW, byH);
        ch = cw * ASPECT;
    }

    private float left() { return (getWidth() - cw * 18) / 2f; }

    private float rowTop(float row) {
        // row: 0..6 = 第1-7周期, 7 = ランタノイド, 8 = アクチノイド
        float r = row >= 7 ? row + ROW_GAP : row;
        return pad + ch * HEADER + r * ch;
    }

    private void cellRect(Element e, RectF out) {
        float row = e.y <= 7 ? e.y - 1 : e.y - 2; // y=9 -> 7, y=10 -> 8
        float l = left() + (e.x - 1) * cw, t = rowTop(row);
        out.set(l + 1, t + 1, l + cw - 1, t + ch - 1);
    }

    private int applyDim(int c) {
        if (dim >= 1f) return c;
        return Color.argb(Color.alpha(c), (int) (Color.red(c) * dim), (int) (Color.green(c) * dim), (int) (Color.blue(c) * dim));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (elements == null || cw <= 0) return;
        float corner = cw * 0.12f;

        // 族番号
        text.setColor(applyDim(0xFF9DA7B3));
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(ch * HEADER * 0.6f);
        for (int g = 1; g <= 18; g++) {
            float cx = left() + (g - 0.5f) * cw;
            canvas.drawText(String.valueOf(g), cx, pad + ch * HEADER * 0.72f, text);
        }

        // f ブロックの位置を示すプレースホルダ
        drawPlaceholder(canvas, 5, "57-71", "lanthanoid", corner);
        drawPlaceholder(canvas, 6, "89-103", "actinoid", corner);

        for (Element e : elements) {
            cellRect(e, rect);
            int base = categoryColor(e.category);
            boolean hide = quizMode; // クイズ中は記号を隠す
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(applyDim(e == selected && !quizMode ? 0xFFFFFFFF : (base & 0x00FFFFFF) | 0x44000000));
            canvas.drawRoundRect(rect, corner, corner, fill);
            stroke.setColor(applyDim(base));
            stroke.setStrokeWidth(Math.max(1f, cw * 0.05f));
            canvas.drawRoundRect(rect, corner, corner, stroke);

            if (e == flashOk || e == flashNg) {
                fill.setColor(e == flashOk ? 0xCC2E7D32 : 0xCCC62828);
                canvas.drawRoundRect(rect, corner, corner, fill);
                hide = false;
            }

            int fg = e == selected && !quizMode ? 0xFF0E1116 : 0xFFE6EDF3;
            if (!hide) {
                text.setColor(applyDim(fg));
                text.setTypeface(Typeface.DEFAULT_BOLD);
                text.setTextSize(cw * 0.42f);
                canvas.drawText(e.symbol, rect.centerX(), rect.top + ch * 0.72f, text);
            }
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(cw * 0.24f);
            text.setColor(applyDim(fg));
            canvas.drawText(String.valueOf(e.number), rect.centerX(), rect.top + ch * 0.3f, text);
        }
    }

    private void drawPlaceholder(Canvas canvas, int row, String label, String cat, float corner) {
        float l = left() + 2 * cw, t = rowTop(row);
        rect.set(l + 1, t + 1, l + cw - 1, t + ch - 1);
        stroke.setColor(applyDim(categoryColor(cat)));
        stroke.setStrokeWidth(Math.max(1f, cw * 0.05f));
        canvas.drawRoundRect(rect, corner, corner, stroke);
        text.setColor(applyDim(categoryColor(cat)));
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(cw * 0.2f);
        canvas.drawText(label, rect.centerX(), rect.centerY() + cw * 0.07f, text);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (ev.getAction() == MotionEvent.ACTION_UP && elements != null) {
            for (Element e : elements) {
                cellRect(e, rect);
                rect.inset(-1.5f, -1.5f);
                if (rect.contains(ev.getX(), ev.getY())) {
                    performClick();
                    if (listener != null) listener.onElementTap(e);
                    return true;
                }
            }
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public boolean performClick() { return super.performClick(); }
}
