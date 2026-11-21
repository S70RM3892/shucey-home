package com.shucey.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * アプリを六角格子（グラフェンと同じハニカム構造）に並べ、指の周りを魚眼レンズのように拡大する。
 * よく使うアプリほど親指に近い下中央に置く。なぞって離したアプリが起動する。
 */
public class AppField extends View {

    interface Listener {
        /** x, y, size はこの View 内でのアイコンの位置（起動アニメーションの起点）。 */
        void onLaunch(AppDrawer.App app, int x, int y, int size);
        void onMenu(AppDrawer.App app, float x, float y);
        void onCloseRequest();
    }

    private static final class Node {
        AppDrawer.App app;
        Bitmap bmp;
        float bx, by;        // 格子上の定位置
        float x, y, s = 1f;  // 現在の表示位置と倍率（毎フレーム目標へ寄せる）
        float a;             // 不透明度
        boolean on;          // 検索に一致して表示中か
        CharSequence shortLabel; // 格子の幅に収めた名前
        float dist;          // 起点からの距離（出現の順番）
    }

    private final List<Node> nodes = new ArrayList<>();
    private final List<Node> shown = new ArrayList<>();
    private final List<int[]> bonds = new ArrayList<>();
    private Listener listener;
    private String query = "";
    private float pitch, originX, originY, maxDist = 1f, reveal = 1f;
    private boolean placed;
    private float scroll, maxScroll; // 入りきらない段を見せるための縦スクロール量
    private float labelAlpha = 1f; // なぞっている間は名前を消す（レンズで位置が動くため）

    // タッチ
    private boolean touching, moved, menuShown;
    private float fx, fy, downX, downY;
    private long downAt;
    private Node focus;
    private VelocityTracker tracker;
    private final int slop;
    private final Runnable longPress = () -> {
        if (touching && !moved && focus != null && listener != null) {
            menuShown = true;
            Ui.heavy(this);
            listener.onMenu(focus.app, focus.x, focus.y + scroll);
            endTouch();
        }
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int iconPx;

    public AppField(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        iconPx = Ui.dp(56);
        line.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        setHapticFeedbackEnabled(true);
    }

    void setListener(Listener l) { listener = l; }

    /** apps は優先順（よく使う順）。 */
    void setApps(List<AppDrawer.App> apps) {
        List<Node> old = new ArrayList<>(nodes);
        nodes.clear();
        for (AppDrawer.App a : apps) {
            Node n = null;
            for (Node o : old) if (o.app.cn.equals(a.cn)) { n = o; break; }
            if (n == null) {
                n = new Node();
                n.bmp = toBitmap(a.icon);
            }
            n.app = a;
            nodes.add(n);
        }
        placed = false;
        layoutNodes();
    }

    private Bitmap toBitmap(Drawable d) {
        Bitmap b = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888);
        if (d != null) {
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, iconPx, iconPx);
            d.draw(c);
        }
        return b;
    }

    /** 検索語。一致したアプリだけが起点の近くに並び直す。 */
    void setQuery(String q) {
        q = q.toLowerCase(Locale.ROOT).trim();
        if (q.equals(query)) return;
        query = q;
        scroll = 0f;
        layoutNodes();
    }

    int shownCount() { return shown.size(); }

    /** 0 = 閉、1 = 全開。起点から結晶が成長するように現れる。 */
    void setReveal(float r) {
        if (r == reveal) return;
        if (r < 0.05f) scroll = 0f; // 開くたびに先頭（よく使う段）から
        reveal = r;
        invalidate();
    }

    // ------------------------------------------------------------ 配置

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        placed = false;
        layoutNodes();
    }

    private void layoutNodes() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        shown.clear();
        for (Node n : nodes) {
            n.on = query.isEmpty() || n.app.label.toLowerCase(Locale.ROOT).contains(query);
            if (n.on) shown.add(n);
        }
        // 列数は画面幅から決め、段は下（親指側）から上へ埋める。偶数段と奇数段を半分ずらしてハニカムにする。
        // 全部が見える範囲で間隔を詰め、それでも入らなければ上へ伸ばしてスクロールで見せる。
        float side = Ui.dpf(10);
        int cols = Math.max(4, (int) ((w - 2 * side) / Ui.dpf(74)));
        float d, rowH;
        int rows;
        while (true) {
            d = (w - 2 * side) / cols;
            rowH = d * 0.92f;
            rows = rowsFor(shown.size(), cols);
            float need = Ui.dpf(8) + Math.min(Ui.dpf(48), d * 0.62f) + Ui.dpf(22) + (rows - 1) * rowH;
            if (need <= h || d <= Ui.dpf(62)) break;
            cols++;
        }
        pitch = d;
        float icon = iconSize();
        originX = w / 2f;
        originY = h - icon / 2f - Ui.dpf(24);

        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(Ui.dpf(10.5f));
        int k = 0;
        float minY = originY;
        maxDist = 1f;
        for (int j = 0; k < shown.size(); j++) {
            int count = j % 2 == 0 ? cols : cols - 1;
            // 段の中では中央から外へ（よく使うものほど中央寄り）
            for (int c = 0; c < count && k < shown.size(); c++) {
                float off = count % 2 == 1
                        ? (c % 2 == 0 ? 1 : -1) * ((c + 1) / 2)        // 0, -1, +1, -2, +2 …
                        : (c % 2 == 0 ? 1 : -1) * (c / 2 + 0.5f);       // +0.5, -0.5, +1.5 …
                Node n = shown.get(k++);
                n.bx = originX + off * d;
                n.by = originY - j * rowH;
                n.dist = j * rowH + Math.abs(off) * d * 0.35f;
                n.shortLabel = TextUtils.ellipsize(n.app.label, text, d * 0.96f, TextUtils.TruncateAt.END);
                maxDist = Math.max(maxDist, n.dist);
                minY = Math.min(minY, n.by);
                if (!placed) { n.x = n.bx; n.y = n.by; n.a = 1f; }
            }
        }
        maxScroll = Math.max(0f, Ui.dpf(8) + icon / 2f - minY);
        scroll = Math.min(scroll, maxScroll);
        placed = true;

        // 隣り合う格子点を結ぶ「結合」
        bonds.clear();
        for (int a = 0; a < shown.size(); a++) {
            for (int b = a + 1; b < shown.size(); b++) {
                Node p = shown.get(a), q = shown.get(b);
                if (Math.hypot(p.bx - q.bx, p.by - q.by) < d * 1.1f) bonds.add(new int[]{a, b});
            }
        }
        invalidate();
    }

    private static int rowsFor(int n, int cols) {
        int rows = 0;
        for (int left = n; left > 0; rows++) left -= rows % 2 == 0 ? cols : cols - 1;
        return Math.max(1, rows);
    }

    /** 上に隠れている段があるか（スクロールで出せる）。 */
    boolean canScroll() { return maxScroll > 0f; }

    // ------------------------------------------------------------ 描画

    @Override
    protected void onDraw(Canvas canvas) {
        boolean busy = false;
        float lens = pitch * 1.9f;
        // なぞっている指が上端・下端に近づいたら自動で送る
        if (touching && maxScroll > 0f) {
            float edge = Ui.dpf(72), before = scroll;
            if (fy < edge) scroll = Math.min(maxScroll, scroll + Ui.dpf(9) * (1f - Math.max(0f, fy) / edge));
            else if (fy > getHeight() - edge) scroll = Math.max(0f, scroll - Ui.dpf(9) * (1f - Math.max(0f, getHeight() - fy) / edge));
            if (scroll != before) {
                busy = true;
                Node n = nearest(fx, fy);
                if (n != focus) { focus = n; if (n != null) Ui.tick(this); }
            }
        }
        float fyc = fy - scroll; // 指の位置（中身の座標）
        for (Node n : nodes) {
            float tx = n.bx, ty = n.by, ts = 1f, ta = n.on ? 1f : 0f;
            if (touching && n.on) {
                float vx = n.bx - fx, vy = n.by - fyc;
                float r = (float) Math.hypot(vx, vy);
                float k = (float) Math.exp(-(r * r) / (lens * lens));
                ts = 1f + 0.85f * k;
                if (r > 1f) { // 指の周りを押し広げる
                    float push = pitch * 1.25f * (r / lens) * k;
                    tx += vx / r * push;
                    ty += vy / r * push;
                }
            }
            n.x += (tx - n.x) * 0.3f;
            n.y += (ty - n.y) * 0.3f;
            n.s += (ts - n.s) * 0.3f;
            n.a += (ta - n.a) * 0.25f;
            if (Math.abs(tx - n.x) + Math.abs(ty - n.y) > 0.5f || Math.abs(ts - n.s) > 0.004f || Math.abs(ta - n.a) > 0.01f) busy = true;
        }

        canvas.save();
        canvas.translate(0, scroll);
        // 結合線
        line.setStrokeWidth(Ui.dpf(1.2f));
        for (int[] b : bonds) {
            Node p = shown.get(b[0]), q = shown.get(b[1]);
            float a = Math.min(appear(p), appear(q)) * Math.min(p.a, q.a);
            if (a <= 0.01f) continue;
            line.setColor(Ui.alpha(Ui.accent, 0.16f * a));
            canvas.drawLine(p.x, p.y, q.x, q.y, line);
        }

        float base = iconSize();
        float la = touching ? 0f : 1f;
        labelAlpha += (la - labelAlpha) * 0.25f;
        if (Math.abs(la - labelAlpha) > 0.01f) busy = true;
        for (Node n : nodes) if (n != focus) drawNode(canvas, n, base);
        if (focus != null) {
            paint.setColor(Ui.alpha(Ui.accent, 0.28f));
            canvas.drawCircle(focus.x, focus.y, base * focus.s * 0.72f, paint);
            drawNode(canvas, focus, base);
        }
        canvas.restore();
        if (focus != null) drawBubble(canvas, focus);
        if (scroll < maxScroll - 1f) { // まだ上に続きがある印
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(Ui.dpf(11));
            text.setColor(Ui.alpha(Ui.onSurfaceVar, 0.9f));
            canvas.drawText("︿ 上の端で止めると続きが出る", getWidth() / 2f, Ui.dpf(14), text);
        }
        if (busy) postInvalidateOnAnimation();
    }

    /** 名前を下に書けるよう、格子間隔に対して小さめにする。 */
    private float iconSize() { return Math.min(Ui.dpf(50), pitch * 0.62f); }

    /** 起点からの距離に応じて遅れて現れる（0..1）。 */
    private float appear(Node n) {
        float t = (reveal * 1.45f - n.dist / maxDist * 0.9f) / 0.45f;
        return Math.max(0f, Math.min(1f, t));
    }

    private void drawNode(Canvas canvas, Node n, float base) {
        float ap = appear(n);
        float alpha = n.a * ap;
        if (alpha <= 0.01f) return;
        float pop = 1f - (1f - ap) * (1f - ap);
        float half = base * n.s * (0.4f + 0.6f * pop) * (0.6f + 0.4f * n.a) / 2f;
        float lift = Ui.dpf(4);
        rect.set(n.x - half, n.y - half - lift, n.x + half, n.y + half - lift);
        paint.setAlpha((int) (255 * alpha));
        canvas.drawBitmap(n.bmp, null, rect, paint);
        paint.setAlpha(255);
        float la = alpha * labelAlpha * (n.on ? 1f : 0f);
        if (la > 0.02f && n.shortLabel != null) {
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(Ui.dpf(10.5f));
            text.setColor(Ui.alpha(Ui.onSurface, 0.85f * la));
            canvas.drawText(n.shortLabel, 0, n.shortLabel.length(), n.x, n.y + base / 2f + Ui.dpf(9.5f), text);
            drawBadge(canvas, n, half, lift);
        } else {
            drawBadge(canvas, n, half, lift);
        }
    }

    /** 通知のあるアプリに点を付ける。 */
    private void drawBadge(Canvas canvas, Node n, float half, float lift) {
        if (!n.on || !NotifyService.has(n.app.cn.getPackageName())) return;
        float r = Math.max(Ui.dpf(4.5f), half * 0.24f);
        float bx = n.x + half * 0.82f, by = n.y - half * 0.82f - lift;
        paint.setColor(Ui.surface);
        canvas.drawCircle(bx, by, r + Ui.dpf(2), paint);
        paint.setColor(0xFFFF6B5E);
        canvas.drawCircle(bx, by, r, paint);
    }

    private void drawBubble(Canvas canvas, Node n) {
        float w = Ui.dpf(132), h = Ui.dpf(112), m = Ui.dpf(8);
        float cx = Math.max(m + w / 2, Math.min(getWidth() - m - w / 2, fx));
        float bottom = fy - pitch * 1.15f;
        if (bottom - h < m) bottom = Math.min(getHeight() - m, fy + pitch * 1.15f + h); // 上端では指の下へ
        rect.set(cx - w / 2, bottom - h, cx + w / 2, bottom);
        paint.setColor(Ui.alpha(Ui.surfaceHigh, 0.96f));
        canvas.drawRoundRect(rect, Ui.dpf(24), Ui.dpf(24), paint);
        float ic = Ui.dpf(60);
        float top = rect.top + Ui.dpf(12);
        RectF r = new RectF(cx - ic / 2, top, cx + ic / 2, top + ic);
        canvas.drawBitmap(n.bmp, null, r, paint);
        text.setColor(Ui.onSurface);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(Ui.dpf(14));
        CharSequence label = TextUtils.ellipsize(n.app.label, text, w - Ui.dpf(16), TextUtils.TruncateAt.END);
        canvas.drawText(label, 0, label.length(), cx, rect.bottom - Ui.dpf(14), text);
    }

    // ------------------------------------------------------------ タッチ

    private Node nearest(float x, float y) {
        Node best = null;
        float bestD = pitch * 0.75f;
        for (Node n : shown) {
            float d = (float) Math.hypot(n.bx - x, n.by - (y - scroll));
            if (d < bestD) { bestD = d; best = n; }
        }
        return best;
    }

    private void focusAt(float x, float y) {
        fx = x;
        fy = y;
        Node n = nearest(x, y);
        if (n != focus) {
            focus = n;
            if (n != null) Ui.tick(this);
        }
        invalidate();
    }

    private void endTouch() {
        touching = false;
        focus = null;
        removeCallbacks(longPress);
        invalidate();
    }

    private void launch(Node n) {
        if (n == null || listener == null) return;
        int size = Math.round(iconSize());
        listener.onLaunch(n.app, Math.round(n.bx - size / 2f), Math.round(n.by + scroll - size / 2f), size);
    }

    /** 検索の先頭候補（起点に最も近いもの）を起動する。 */
    void launchTop() { if (!shown.isEmpty()) launch(shown.get(0)); }

    /**
     * ドックからの一筆書き。指を離さずここまで滑らせてきたときの座標（画面座標）を受け取る。
     * release = true で指が離れた。deliberate は「狙って止めた」かどうか。
     */
    void steer(float rawX, float rawY, boolean release, boolean deliberate) {
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        float x = rawX - loc[0], y = rawY - loc[1];
        if (!release) {
            touching = true;
            focusAt(x, y);
        } else {
            Node n = focus;
            endTouch();
            if (deliberate && n != null) launch(n);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (tracker == null) tracker = VelocityTracker.obtain();
        tracker.addMovement(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                touching = true;
                moved = false;
                menuShown = false;
                downX = ev.getX();
                downY = ev.getY();
                downAt = SystemClock.uptimeMillis();
                focusAt(downX, downY);
                postDelayed(longPress, 480);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!touching) return true;
                if (Math.hypot(ev.getX() - downX, ev.getY() - downY) > slop) {
                    moved = true;
                    removeCallbacks(longPress);
                }
                focusAt(ev.getX(), ev.getY());
                return true;
            case MotionEvent.ACTION_UP: {
                tracker.computeCurrentVelocity(1000);
                float vy = tracker.getYVelocity();
                tracker.recycle();
                tracker = null;
                if (!touching) return true;
                Node n = focus;
                endTouch();
                if (menuShown) return true;
                if (vy > Ui.dpf(1500) && ev.getY() - downY > pitch) { // 下へ強く払う → 閉じる
                    if (listener != null) listener.onCloseRequest();
                } else if (n != null) {
                    launch(n);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                if (tracker != null) { tracker.recycle(); tracker = null; }
                endTouch();
                return true;
        }
        return super.onTouchEvent(ev);
    }
}
