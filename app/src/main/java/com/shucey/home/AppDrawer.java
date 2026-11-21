package com.shucey.home;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 下から引き出すアプリ一覧。指に追従して開閉し、離した速度で開くか閉じるかを決める。
 */
public class AppDrawer extends FrameLayout implements AppField.Listener {

    interface Listener {
        /** 0 = 閉、1 = 全開。ホーム側の奥行き演出に使う。 */
        void onProgress(float p);
        void onDockChanged();
    }

    static final class App {
        final ComponentName cn;
        final String label;
        final Drawable icon;
        App(ComponentName cn, String label, Drawable icon) { this.cn = cn; this.label = label; this.icon = icon; }
    }

    private final List<App> all = new ArrayList<>();
    private final LinearLayout sheet;
    private final EditText search;
    private final AppField field;
    private final View anchor;
    private final FrameLayout body;
    private final TextView empty, hint;
    private Listener listener;
    private boolean open, loading;

    // ドラッグ状態
    private final int slop;
    private float downX, downY, startTy;
    private boolean dragging;
    private VelocityTracker tracker;

    public AppDrawer(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setVisibility(INVISIBLE);

        sheet = new LinearLayout(c);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.alpha(Ui.surface, android.os.Build.VERSION.SDK_INT >= 31 ? 0.80f : 0.97f));
        float r = Ui.dpf(28);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setClickable(true); // 背後へタッチを抜かさない
        addView(sheet, new LayoutParams(-1, -1));

        View handle = new View(c);
        handle.setBackground(Ui.round(Ui.alpha(Ui.onSurfaceVar, 0.5f), 2));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Ui.dp(36), Ui.dp(4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = Ui.dp(10);
        sheet.addView(handle, hlp);

        search = new EditText(c);
        search.setHint("アプリを検索");
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setTextColor(Ui.onSurface);
        search.setHintTextColor(Ui.alpha(Ui.onSurfaceVar, 0.8f));
        search.setBackground(Ui.round(Ui.surfaceHigh, 26));
        search.setPadding(Ui.dp(20), 0, Ui.dp(20), 0);
        search.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, Ui.dp(52));
        slp.setMargins(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(4));
        sheet.addView(search, slp);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            public void onTextChanged(CharSequence s, int a, int b, int d) {}
            public void afterTextChanged(Editable s) { filter(); }
        });
        search.setOnEditorActionListener((v, id, ev) -> {
            launchTop(); // 先頭の候補を起動
            return true;
        });

        body = new FrameLayout(c);
        field = new AppField(c);
        field.setListener(this);
        body.addView(field, new LayoutParams(-1, -1));
        anchor = new View(c); // 長押しメニューの出る位置
        body.addView(anchor, new LayoutParams(1, 1));
        empty = ElementPanel.text(c, 14, Ui.onSurfaceVar, false, "該当するアプリがない");
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(GONE);
        body.addView(empty, new LayoutParams(-1, Ui.dp(120)));
        // 最初の数回だけ操作のヒントを出す
        hint = ElementPanel.text(c, 12, Ui.onSurfaceVar, false, "なぞって離すと起動 ・ 長押しでメニュー ・ 下へ払うと閉じる");
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.dp(4), 0, 0);
        sheet.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        sheet.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    void setListener(Listener l) { listener = l; }
    void invalidateField() { field.invalidate(); }
    boolean isOpen() { return open; }

    /** システムバーやキーボードの分だけ内側に寄せる。 */
    void setInsets(int top, int bottom) {
        setPadding(0, top + Ui.dp(8), 0, 0);
        sheet.setPadding(0, 0, 0, bottom);
    }

    // ------------------------------------------------------------ アプリ読み込み

    void reload() {
        if (loading) return;
        loading = true;
        Context c = getContext().getApplicationContext();
        new Thread(() -> {
            PackageManager pm = c.getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<App> list = new ArrayList<>();
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
                if (ri.activityInfo.packageName.equals(c.getPackageName())) continue;
                list.add(new App(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                        String.valueOf(ri.loadLabel(pm)), ri.loadIcon(pm)));
            }
            Collator col = Collator.getInstance(Locale.JAPANESE);
            list.sort((x, y) -> col.compare(x.label, y.label));
            List<App> ordered = order(c, list);
            post(() -> {
                loading = false;
                all.clear();
                all.addAll(ordered);
                field.setApps(all);
                filter();
            });
        }, "apps").start();
    }

    /**
     * よく使う順に並べる。ただし位置の記憶が崩れないよう、並びは 1 日単位で固定する
     * （日付が変わったときだけ使用回数で並べ直す。新しく入ったアプリは末尾に足す）。
     */
    private static List<App> order(Context c, List<App> alpha) {
        android.content.SharedPreferences prefs = Dock.prefs(c);
        long day = System.currentTimeMillis() / 86400000L;
        List<App> out = new ArrayList<>();
        String saved = prefs.getString("order", "");
        if (prefs.getLong("order_day", -1) == day && !saved.isEmpty()) {
            for (String key : saved.split("\n")) {
                for (App a : alpha) if (a.cn.flattenToString().equals(key)) { out.add(a); break; }
            }
            for (App a : alpha) if (!out.contains(a)) out.add(a);
        } else {
            out.addAll(alpha);
            // 安定ソートなので、同じ回数なら名前順のまま
            out.sort((x, y) -> Integer.compare(uses(prefs, y.cn), uses(prefs, x.cn)));
        }
        StringBuilder sb = new StringBuilder();
        for (App a : out) sb.append(a.cn.flattenToString()).append('\n');
        prefs.edit().putString("order", sb.toString()).putLong("order_day", day).apply();
        return out;
    }

    private static int uses(android.content.SharedPreferences prefs, ComponentName cn) {
        return prefs.getInt("use_" + cn.flattenToString(), 0);
    }

    private void launchTop() { field.launchTop(); }

    private void filter() {
        field.setQuery(search.getText().toString());
        empty.setVisibility(field.shownCount() == 0 && !all.isEmpty() ? VISIBLE : GONE);
    }

    /** from 内の矩形 (x, y, size) から広がるように起動し、使用回数を数える。 */
    static void start(Context c, ComponentName cn, View from, int x, int y, int size) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            c.startActivity(i, from == null ? null
                    : ActivityOptions.makeClipRevealAnimation(from, x, y, size, size).toBundle());
            android.content.SharedPreferences prefs = Dock.prefs(c);
            String key = "use_" + cn.flattenToString();
            prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply();
        } catch (Exception e) {
            Toast.makeText(c, "起動できない", Toast.LENGTH_SHORT).show();
        }
    }

    static void start(Context c, ComponentName cn, View from) {
        start(c, cn, from, 0, 0, from == null ? 0 : from.getWidth());
    }

    // ---- AppField からの通知 ----

    @Override
    public void onLaunch(App a, int x, int y, int size) {
        Ui.tap(this);
        start(getContext(), a.cn, field, x, y, size);
        postDelayed(() -> close(false), 350); // 戻ってきたときはホームに
    }

    @Override
    public void onMenu(App a, float x, float y) {
        anchor.setTranslationX(x);
        anchor.setTranslationY(y);
        showMenu(a, anchor);
    }

    @Override
    public void onCloseRequest() { close(true); }

    /** ドックからの一筆書きを一覧へ引き継ぐ。 */
    void steer(float rawX, float rawY, boolean release, boolean deliberate) {
        field.steer(rawX, rawY, release, deliberate);
    }

    private void showMenu(App a, View anchor) {
        Context c = getContext();
        boolean inDock = Dock.contains(c, a.cn);
        PopupMenu m = new PopupMenu(c, anchor, Gravity.CENTER_HORIZONTAL);
        m.getMenu().add(0, 1, 0, inDock ? "ドックから外す" : "ドックに追加");
        m.getMenu().add(0, 2, 1, "アプリ情報");
        m.getMenu().add(0, 3, 2, "アンインストール");
        m.setOnMenuItemClickListener(item -> {
            Uri pkg = Uri.fromParts("package", a.cn.getPackageName(), null);
            switch (item.getItemId()) {
                case 1:
                    if (inDock) Dock.remove(c, a.cn);
                    else if (!Dock.add(c, a.cn)) {
                        Toast.makeText(c, "ドックは最大 " + Dock.MAX + " 個。先に外すこと", Toast.LENGTH_SHORT).show();
                    }
                    if (listener != null) listener.onDockChanged();
                    break;
                case 2:
                    c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg));
                    break;
                case 3:
                    try {
                        c.startActivity(new Intent(Intent.ACTION_DELETE, pkg));
                    } catch (Exception e) {
                        Toast.makeText(c, "アンインストールできない", Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
            return true;
        });
        m.show();
    }

    // ------------------------------------------------------------ 開閉

    private float closedY() { return getHeight() > 0 ? getHeight() : Ui.dpf(900); }

    private void applyTy(float ty) {
        ty = Math.max(0f, Math.min(closedY(), ty));
        sheet.setTranslationY(ty);
        progress(1f - ty / closedY());
    }

    private void progress(float p) {
        field.setReveal(p);
        if (listener != null) listener.onProgress(p);
    }

    /** ドックからの引き上げ開始。 */
    void beginDrag() {
        sheet.animate().cancel();
        if (getVisibility() != VISIBLE) {
            search.setText("");
            android.content.SharedPreferences prefs = Dock.prefs(getContext());
            int seen = prefs.getInt("drawer_opens", 0);
            hint.setVisibility(seen < 5 ? VISIBLE : GONE);
            prefs.edit().putInt("drawer_opens", seen + 1).apply();
            sheet.setTranslationY(closedY());
            setVisibility(VISIBLE);
        }
    }

    /** 閉位置から dy だけ引き上げた状態にする。 */
    void dragBy(float dyUp) { applyTy(closedY() - dyUp); }

    /** 指を離した。速度（上向きが負）と位置で開閉を決める。 */
    void settle(float velocityY) {
        float ty = sheet.getTranslationY();
        boolean toOpen = Math.abs(velocityY) > Ui.dpf(400) ? velocityY < 0 : ty < closedY() * 0.55f;
        animateTo(toOpen, velocityY);
    }

    void open(boolean focusSearch) {
        beginDrag();
        animateTo(true, 0);
        if (focusSearch) {
            search.requestFocus();
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            search.postDelayed(() -> imm.showSoftInput(search, 0), 200);
        }
    }

    void close(boolean animated) {
        if (getVisibility() != VISIBLE) return;
        hideKeyboard();
        if (animated) animateTo(false, 0);
        else {
            sheet.animate().cancel();
            open = false;
            applyTy(closedY());
            setVisibility(INVISIBLE);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindowToken(), 0);
        search.clearFocus();
    }

    private void animateTo(boolean toOpen, float velocityY) {
        boolean was = open;
        open = toOpen;
        if (was != toOpen) Ui.tick(this);
        if (!toOpen) hideKeyboard();
        float target = toOpen ? 0f : closedY();
        float dist = Math.abs(target - sheet.getTranslationY());
        long ms = (long) Math.max(160, Math.min(420, 420 * dist / closedY() + 120 - Math.abs(velocityY) / Ui.dpf(40)));
        sheet.animate().translationY(target).setDuration(ms).setInterpolator(Ui.EASE_OUT)
                .setUpdateListener(a -> progress(1f - sheet.getTranslationY() / closedY()))
                .withEndAction(() -> {
                    progress(toOpen ? 1f : 0f);
                    if (!toOpen) setVisibility(INVISIBLE);
                })
                .start();
    }

    // 検索欄より上を下に引くと閉じる（一覧の中では下へ強く払う）
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                float dy = ev.getY() - downY, dx = ev.getX() - downX;
                boolean onHeader = downY < body.getTop() + getPaddingTop();
                if (dy > slop && dy > Math.abs(dx) * 1.3f && onHeader) {
                    dragging = true;
                    startTy = sheet.getTranslationY();
                    downY = ev.getY();
                    sheet.animate().cancel();
                    return true;
                }
                break;
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!dragging) return super.onTouchEvent(ev);
        if (tracker == null) tracker = VelocityTracker.obtain();
        tracker.addMovement(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                applyTy(startTy + ev.getY() - downY);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                tracker.computeCurrentVelocity(1000);
                settle(tracker.getYVelocity());
                tracker.recycle();
                tracker = null;
                dragging = false;
                break;
        }
        return true;
    }
}
