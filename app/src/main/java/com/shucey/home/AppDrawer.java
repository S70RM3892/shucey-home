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
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
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
public class AppDrawer extends FrameLayout {

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
    private final List<App> shown = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private final LinearLayout sheet;
    private final EditText search;
    private final GridView grid;
    private final FrameLayout body;
    private final TextView empty;
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
            if (!shown.isEmpty()) launch(shown.get(0), search); // 先頭の候補を起動
            return true;
        });

        body = new FrameLayout(c);
        grid = new GridView(c);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(Ui.dp(84));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(0);
        grid.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(24));
        grid.setClipToPadding(false);
        grid.setSelector(Ui.ripple(0x00000000, 18));
        grid.setDrawSelectorOnTop(false);
        grid.setVerticalScrollBarEnabled(false);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> launch(shown.get(pos), v));
        grid.setOnItemLongClickListener((p, v, pos, id) -> { showMenu(shown.get(pos), v); return true; });
        body.addView(grid, new LayoutParams(-1, -1));
        empty = ElementPanel.text(c, 14, Ui.onSurfaceVar, false, "該当するアプリがない");
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(GONE);
        body.addView(empty, new LayoutParams(-1, Ui.dp(120)));
        sheet.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    void setListener(Listener l) { listener = l; }
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
            post(() -> {
                loading = false;
                all.clear();
                all.addAll(list);
                filter();
            });
        }, "apps").start();
    }

    private void filter() {
        shown.clear();
        String q = search.getText().toString().toLowerCase(Locale.ROOT).trim();
        for (App a : all) {
            if (q.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(q)) shown.add(a);
        }
        empty.setVisibility(shown.isEmpty() && !all.isEmpty() ? VISIBLE : GONE);
        adapter.notifyDataSetChanged();
    }

    static void start(Context c, ComponentName cn, View from) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            // アイコンの位置から広がるように起動する
            c.startActivity(i, from == null ? null
                    : ActivityOptions.makeClipRevealAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle());
        } catch (Exception e) {
            Toast.makeText(c, "起動できない", Toast.LENGTH_SHORT).show();
        }
    }

    private void launch(App a, View from) {
        Ui.tap(this);
        start(getContext(), a.cn, from);
        postDelayed(() -> close(false), 350); // 戻ってきたときはホームに
    }

    private void showMenu(App a, View anchor) {
        Ui.heavy(this);
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
        if (listener != null) listener.onProgress(1f - ty / closedY());
    }

    /** ドックからの引き上げ開始。 */
    void beginDrag() {
        sheet.animate().cancel();
        if (getVisibility() != VISIBLE) {
            search.setText("");
            grid.setSelection(0);
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
                .setUpdateListener(a -> { if (listener != null) listener.onProgress(1f - sheet.getTranslationY() / closedY()); })
                .withEndAction(() -> {
                    if (listener != null) listener.onProgress(toOpen ? 1f : 0f);
                    if (!toOpen) setVisibility(INVISIBLE);
                })
                .start();
    }

    // 一覧が先頭にあるとき（または検索欄より上）を下に引くと閉じる
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
                if (dy > slop && dy > Math.abs(dx) * 1.3f && (onHeader || !grid.canScrollVertically(-1))) {
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

    // ------------------------------------------------------------ 一覧

    private class Adapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int i) { return shown.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View v, ViewGroup parent) {
            LinearLayout cell = (LinearLayout) v;
            if (cell == null) {
                Context c = getContext();
                cell = new LinearLayout(c);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(Ui.dp(4), Ui.dp(10), Ui.dp(4), Ui.dp(8));
                ImageView icon = new ImageView(c);
                cell.addView(icon, new LinearLayout.LayoutParams(Ui.dp(54), Ui.dp(54)));
                TextView label = ElementPanel.text(c, 12, Ui.onSurface, false);
                label.setGravity(Gravity.CENTER_HORIZONTAL);
                label.setLines(2);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setPadding(0, Ui.dp(6), 0, 0);
                cell.addView(label, new LinearLayout.LayoutParams(-1, -2));
            }
            App a = shown.get(pos);
            ((ImageView) cell.getChildAt(0)).setImageDrawable(a.icon);
            ((TextView) cell.getChildAt(1)).setText(a.label);
            return cell;
        }
    }
}
