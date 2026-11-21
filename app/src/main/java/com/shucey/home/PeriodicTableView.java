package com.shucey.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/**
 * 周期表。指でなぞると元素を次々に選べ（拡大表示は MagnifierView が担当）、離した元素が確定する。
 */
public class PeriodicTableView extends View {

    interface Listener {
        /** なぞっている最中。e == null は元素のない場所。座標はこの View 内のセル上端中央。 */
        void onScrub(Element e, float cx, float top);
        void onScrubEnd();
        void onElementTap(Element e);
    }

    static final int MODE_CATEGORY = 0, MODE_EN = 1, MODE_IE = 2, MODE_PHASE = 3;

    private static final float ROW_GAP = 0.3f;   // 本表と f ブロックの間
    private static final float HEADER = 0.5f;    // 族番号の行
    private static final float ASPECT = 1.22f;   // セル高さ / 幅
    private static final float ROWS = 9 + ROW_GAP + HEADER;
    private static final int NO_DATA = 0xFF39414D;

    private List<Element> elements;
    private Listener listener;
    private Element selected, pressed, flashOk, flashNg;
    private boolean quizMode;
    private int mode = MODE_CATEGORY;
    private String filter; // 分類または状態。null = 全表示
    private float dim = 1f;

    // アニメーション状態
    private int[] fromColor, toColor;
    private float[] fromAlpha, toAlpha;
    private float blend = 1f, appear = 1f, selectPop = 1f, flashT = 1f;
    private ValueAnimator blendAnim, appearAnim, popAnim, flashAnim;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float cw, ch;
    private final float pad;

    public PeriodicTableView(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        pad = Ui.dpf(4);
        setHapticFeedbackEnabled(true);
    }

    // ------------------------------------------------------------ 設定

    void setElements(List<Element> list) {
        elements = list;
        int n = list.size();
        fromColor = new int[n]; toColor = new int[n];
        fromAlpha = new float[n]; toAlpha = new float[n];
        computeTargets();
        System.arraycopy(toColor, 0, fromColor, 0, n);
        System.arraycopy(toAlpha, 0, fromAlpha, 0, n);
        invalidate();
    }

    void setListener(Listener l) { listener = l; }
    Element getSelected() { return selected; }
    int getMode() { return mode; }

    void setSelected(Element e) {
        if (selected == e) return;
        selected = e;
        if (popAnim != null) popAnim.cancel();
        popAnim = animate01(280, Ui.SPRING, v -> selectPop = v);
    }

    void setMode(int m) {
        if (mode == m) return;
        mode = m;
        filter = null;
        retarget();
    }

    void setFilter(String f) {
        filter = f;
        retarget();
    }

    void setQuizMode(boolean q) {
        quizMode = q;
        flashOk = flashNg = null;
        pressed = null;
        invalidate();
    }

    void flash(Element ok, Element ng) {
        flashOk = ok;
        flashNg = ng;
        if (flashAnim != null) flashAnim.cancel();
        if (ok != null || ng != null) flashAnim = animate01(380, Ui.SPRING, v -> flashT = v);
        else invalidate();
    }

    /** 夜間は全体を暗くする (0..1)。 */
    void setDim(float d) {
        if (dim == d) return;
        dim = d;
        invalidate();
    }

    /** 左上から波のように現れる。 */
    void playAppear() {
        if (appearAnim != null) appearAnim.cancel();
        appearAnim = animate01(700, new android.view.animation.LinearInterpolator(), v -> appear = v);
    }

    private interface FloatSetter { void set(float v); }

    private ValueAnimator animate01(long ms, android.animation.TimeInterpolator ip, FloatSetter s) {
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(ms);
        a.setInterpolator(ip);
        a.addUpdateListener(an -> { s.set((float) an.getAnimatedValue()); invalidate(); });
        s.set(0f);
        a.start();
        return a;
    }

    // ------------------------------------------------------------ 色

    static int categoryColor(String c) {
        switch (c) {
            case "alkali": return 0xFFEF6F6C;
            case "alkaline_earth": return 0xFFFFB454;
            case "transition": return 0xFF8FA3B8;
            case "lanthanoid": return 0xFF45C4B0;
            case "actinoid": return 0xFFF06A9B;
            case "halogen": return 0xFFFFD84D;
            case "noble": return 0xFFB985E8;
            case "nonmetal": return 0xFF56BDF5;
            default: return 0xFF7BCB7F; // 典型金属
        }
    }

    static int phaseColor(String ph) {
        switch (ph) {
            case "固体": return 0xFF8FA3B8;
            case "液体": return 0xFF56BDF5;
            case "気体": return 0xFFFFB454;
            default: return NO_DATA;
        }
    }

    /** 0..1 を 紺 → 青緑 → 黄 の色に。傾向が一目で分かるように。 */
    static int heat(float t) {
        if (t < 0.5f) return Ui.lerp(0xFF31408F, 0xFF1FA69A, t * 2);
        return Ui.lerp(0xFF1FA69A, 0xFFFFE04A, (t - 0.5f) * 2);
    }

    static final float EN_MIN = 0.7f, EN_MAX = 4.0f, IE_MIN = 370f, IE_MAX = 2400f;

    private int colorOf(Element e) {
        switch (mode) {
            case MODE_EN:
                return Double.isNaN(e.electronegativity) ? NO_DATA
                        : heat(((float) e.electronegativity - EN_MIN) / (EN_MAX - EN_MIN));
            case MODE_IE:
                return e.ionization == Element.NONE ? NO_DATA : heat((e.ionization - IE_MIN) / (IE_MAX - IE_MIN));
            case MODE_PHASE:
                return phaseColor(e.phase);
            default:
                return categoryColor(e.category);
        }
    }

    private boolean isHeat() { return mode == MODE_EN || mode == MODE_IE; }

    private void computeTargets() {
        for (int i = 0; i < elements.size(); i++) {
            Element e = elements.get(i);
            int base = colorOf(e);
            boolean match = filter == null
                    || (mode == MODE_PHASE ? filter.equals(e.phase) : filter.equals(e.category));
            toColor[i] = base;
            // ヒートマップは塗りを濃く、分類はやわらかく
            toAlpha[i] = !match ? 0.10f : (isHeat() ? (base == NO_DATA ? 0.5f : 0.92f) : 0.34f);
        }
    }

    private void retarget() {
        if (elements == null) return;
        for (int i = 0; i < fromColor.length; i++) {
            fromColor[i] = Ui.lerp(fromColor[i], toColor[i], blend);
            fromAlpha[i] = fromAlpha[i] + (toAlpha[i] - fromAlpha[i]) * blend;
        }
        computeTargets();
        if (blendAnim != null) blendAnim.cancel();
        blendAnim = animate01(320, Ui.EASE_IN_OUT, v -> blend = v);
    }

    // ------------------------------------------------------------ 配置

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        float cellW = (w - 2 * pad) / 18f;
        int h = (int) Math.ceil(cellW * ASPECT * ROWS + 2 * pad);
        if (MeasureSpec.getMode(hSpec) != MeasureSpec.UNSPECIFIED) h = Math.min(h, MeasureSpec.getSize(hSpec));
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cw = Math.min((w - 2 * pad) / 18f, (h - 2 * pad) / ROWS / ASPECT);
        ch = cw * ASPECT;
    }

    private float left() { return (getWidth() - cw * 18) / 2f; }

    private float rowTop(float row) { // 0..6 = 第1-7周期, 7 = ランタノイド, 8 = アクチノイド
        return pad + ch * HEADER + (row >= 7 ? row + ROW_GAP : row) * ch;
    }

    private static float rowOf(Element e) { return e.y <= 7 ? e.y - 1 : e.y - 2; }

    private void cellRect(Element e, RectF out) {
        float l = left() + (e.x - 1) * cw, t = rowTop(rowOf(e));
        float gap = Math.max(1f, cw * 0.045f);
        out.set(l + gap, t + gap, l + cw - gap, t + ch - gap);
    }

    private Element elementAt(float x, float y) {
        Element best = null;
        float bestD = cw * 0.85f; // 少し外れても近いセルに吸着
        for (Element e : elements) {
            float cx = left() + (e.x - 0.5f) * cw, cy = rowTop(rowOf(e)) + ch / 2;
            float d = Math.max(Math.abs(x - cx), Math.abs(y - cy) / ASPECT);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    // ------------------------------------------------------------ 描画

    private int dimmed(int c) {
        if (dim >= 1f) return c;
        return android.graphics.Color.argb(c >>> 24, (int) (((c >> 16) & 0xFF) * dim),
                (int) (((c >> 8) & 0xFF) * dim), (int) ((c & 0xFF) * dim));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (elements == null || cw <= 0) return;
        float corner = cw * 0.2f;
        int bg = 0xFF0E1218;

        // 族番号
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(ch * HEADER * 0.52f);
        text.setColor(dimmed(Ui.alpha(Ui.onSurfaceVar, 0.8f * Math.min(1f, appear * 3))));
        for (int g = 1; g <= 18; g++) {
            canvas.drawText(String.valueOf(g), left() + (g - 0.5f) * cw, pad + ch * HEADER * 0.66f, text);
        }

        drawPlaceholder(canvas, 5, "57–71", "lanthanoid", corner);
        drawPlaceholder(canvas, 6, "89–103", "actinoid", corner);

        Element top = null; // 選択中のセルは最後に（手前に）描く
        for (int i = 0; i < elements.size(); i++) {
            Element e = elements.get(i);
            if (e == selected && !quizMode) { top = e; continue; }
            drawCell(canvas, e, i, corner, bg, false);
        }
        if (top != null) drawCell(canvas, top, top.number - 1, corner, bg, true);
    }

    private void drawCell(Canvas canvas, Element e, int i, float corner, int bg, boolean isSelected) {
        // 出現アニメーション: 左上から斜めに波が走る
        float delay = (e.x + rowOf(e) * 1.4f) / 30f * 0.62f;
        float a = Math.max(0f, Math.min(1f, (appear - delay) / 0.38f));
        if (a <= 0f) return;
        float ease = 1f - (1f - a) * (1f - a);

        cellRect(e, rect);
        int base = Ui.lerp(fromColor[i], toColor[i], blend);
        float al = fromAlpha[i] + (toAlpha[i] - fromAlpha[i]) * blend;
        int fillColor = Ui.over(base, bg, al);
        int fg = al < 0.2f ? Ui.alpha(Ui.onSurface, 0.35f) : (Ui.isLight(fillColor) ? 0xFF0B0E14 : 0xFFF2F5F8);

        float scale = 0.6f + 0.4f * ease;
        if (isSelected) {
            scale *= 1f + 0.22f * selectPop;
            fillColor = Ui.lerp(fillColor, base, selectPop);
            fg = Ui.isLight(base) ? 0xFF0B0E14 : 0xFFFFFFFF;
        }
        boolean ok = e == flashOk, ng = e == flashNg;
        if (ok || ng) {
            fillColor = ok ? Ui.GOOD : Ui.BAD;
            fg = 0xFF0B0E14;
            scale *= 1f + 0.2f * flashT;
        }
        if (e == pressed) scale *= 0.9f;

        canvas.save();
        canvas.scale(scale, scale, rect.centerX(), rect.centerY());
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(dimmed(Ui.alpha(fillColor, ease)));
        if (isSelected) fill.setShadowLayer(cw * 0.5f, 0, cw * 0.12f, 0x99000000);
        canvas.drawRoundRect(rect, corner, corner, fill);
        fill.clearShadowLayer();
        if (e == pressed) {
            ring.setStrokeWidth(Math.max(2f, cw * 0.08f));
            ring.setColor(0xFFFFFFFF);
            canvas.drawRoundRect(rect, corner, corner, ring);
        }

        boolean reveal = !quizMode || ok || ng;
        if (reveal) {
            text.setColor(dimmed(Ui.alpha(fg, ease)));
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(cw * 0.44f);
            canvas.drawText(e.symbol, rect.centerX(), rect.top + rect.height() * 0.76f, text);
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(cw * 0.24f);
            text.setColor(dimmed(Ui.alpha(fg, ease * 0.8f)));
            canvas.drawText(String.valueOf(e.number), rect.centerX(), rect.top + rect.height() * 0.32f, text);
        }
        canvas.restore();
    }

    private void drawPlaceholder(Canvas canvas, int row, String label, String cat, float corner) {
        float l = left() + 2 * cw, t = rowTop(row);
        float gap = Math.max(1f, cw * 0.045f);
        rect.set(l + gap, t + gap, l + cw - gap, t + ch - gap);
        int c = dimmed(Ui.alpha(categoryColor(cat), 0.7f * Math.min(1f, appear * 2)));
        ring.setColor(c);
        ring.setStrokeWidth(Math.max(1f, cw * 0.04f));
        canvas.drawRoundRect(rect, corner, corner, ring);
        text.setColor(c);
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(cw * 0.2f);
        canvas.drawText(label, rect.centerX(), rect.centerY() + cw * 0.07f, text);
    }

    // ------------------------------------------------------------ タッチ

    private void scrubTo(Element e) {
        if (e == pressed) return;
        pressed = e;
        if (e != null) Ui.tick(this);
        if (listener != null) {
            if (e == null) listener.onScrub(null, 0, 0);
            else listener.onScrub(e, left() + (e.x - 0.5f) * cw, rowTop(rowOf(e)));
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (elements == null) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                scrubTo(elementAt(ev.getX(), ev.getY()));
                return true;
            case MotionEvent.ACTION_MOVE:
                scrubTo(elementAt(ev.getX(), ev.getY()));
                return true;
            case MotionEvent.ACTION_UP: {
                Element e = pressed;
                pressed = null;
                if (listener != null) listener.onScrubEnd();
                invalidate();
                if (e != null) {
                    performClick();
                    if (listener != null) listener.onElementTap(e);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                pressed = null;
                if (listener != null) listener.onScrubEnd();
                invalidate();
                return true;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public boolean performClick() { return super.performClick(); }
}
