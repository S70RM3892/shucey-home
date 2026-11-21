package com.shucey.home;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

public class MainActivity extends Activity implements PeriodicTableView.Listener, AppDrawer.Listener {

    private static final String[] MODES = {"分類", "電気陰性度", "イオン化E", "状態"};
    private static final String[][] CATEGORIES = {
            {"alkali", "アルカリ金属"}, {"alkaline_earth", "アルカリ土類"}, {"transition", "遷移元素"},
            {"metal", "典型金属"}, {"nonmetal", "典型非金属"}, {"halogen", "ハロゲン"},
            {"noble", "貴ガス"}, {"lanthanoid", "ランタノイド"}, {"actinoid", "アクチノイド"}};

    private List<Element> elements;
    private SharedPreferences prefs;
    private Quiz quiz;
    private boolean quizOn, answered;

    private FrameLayout root;
    private LinearLayout home, header, modeBar, legend, homeCards, quizPanel, quizFeedback, poolRow;
    private HorizontalScrollView legendScroll;
    private PeriodicTableView table;
    private MagnifierView magnifier;
    private ScrollView panelScroll;
    private ElementPanel elementPanel, quizElementPanel;
    private TextView nightCard, quizQuestion, quizStats, quizResult, quizHint, quizBest, todayTitle, todayNote, todayTile;
    private DockBar dock;
    private AppDrawer drawer;
    private Element today;
    private String filter;
    private BackgroundFx bg;
    private TextClock clock, date;
    private FlameView flameRow;
    private boolean landscape;
    private int insetL, insetT, insetR, insetB;
    private boolean wasAwake;

    // 端末の傾き（表の立体的な傾きと光沢に使う）
    private android.hardware.SensorManager sensors;
    private android.hardware.Sensor gravity;
    private float baseX, baseY, tiltX, tiltY, sentX, sentY;
    private boolean haveBase;
    private final android.hardware.SensorEventListener tiltListener = new android.hardware.SensorEventListener() {
        @Override public void onSensorChanged(android.hardware.SensorEvent ev) {
            float gx = ev.values[0] / 9.81f, gy = ev.values[1] / 9.81f, x, y;
            switch (getWindowManager().getDefaultDisplay().getRotation()) { // 画面の向きに合わせて軸を入れ替える
                case android.view.Surface.ROTATION_90: x = -gy; y = gx; break;
                case android.view.Surface.ROTATION_270: x = gy; y = -gx; break;
                case android.view.Surface.ROTATION_180: x = -gx; y = -gy; break;
                default: x = gx; y = gy;
            }
            if (!haveBase) { baseX = x; baseY = y; haveBase = true; }
            // 基準はゆっくり今の持ち方に追従させる → 動かした瞬間だけ傾き、やがて戻る
            baseX += (x - baseX) * 0.03f;
            baseY += (y - baseY) * 0.03f;
            float tx = Math.max(-1f, Math.min(1f, (baseX - x) * 3.2f)), ty = Math.max(-1f, Math.min(1f, (y - baseY) * 3.2f));
            tiltX += (tx - tiltX) * 0.25f;
            tiltY += (ty - tiltY) * 0.25f;
            if (Math.abs(tiltX - sentX) + Math.abs(tiltY - sentY) > 0.006f) { // 静止中は再描画しない
                sentX = tiltX;
                sentY = tiltY;
                table.setTilt(tiltX, tiltY);
            }
        }
        @Override public void onAccuracyChanged(android.hardware.Sensor s, int a) {}
    };

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateNight(); }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            drawer.reload();
            rebuildDock();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        prefs = Dock.prefs(this);
        elements = Element.load(this);
        quiz = new Quiz(elements, prefs, new Random());
        initDefaultDock();

        // 画面の端まで描く（システムバーの裏にも背景を通す）
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        setContentView(buildUi());
        arrange(getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE);
        showHome(false);
        sensors = (android.hardware.SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensors != null) {
            gravity = sensors.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY);
            if (gravity == null) gravity = sensors.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER);
        }
        table.playAppear();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        register(packages, pf);
        drawer.reload();
    }

    private void register(BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(r, f);
    }

    // ================================================================ 画面構築

    private View buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.scrim);

        bg = new BackgroundFx(this);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        home = new LinearLayout(this);
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));

        buildHeader();
        table = new PeriodicTableView(this);
        table.setElements(elements);
        table.setListener(this);

        buildModeBar();
        legendScroll = new HorizontalScrollView(this);
        legendScroll.setHorizontalScrollBarEnabled(false);
        legendScroll.setClipToPadding(false);
        legendScroll.setPadding(Ui.dp(12), 0, Ui.dp(12), 0);
        legend = new LinearLayout(this);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legendScroll.addView(legend, new FrameLayout.LayoutParams(-2, Ui.dp(40)));
        rebuildLegend();

        // --- 下半分: ホーム / 元素詳細 / クイズ を切り替える ---
        panelScroll = new ScrollView(this);
        panelScroll.setVerticalScrollBarEnabled(false);
        panelScroll.setVerticalFadingEdgeEnabled(true);
        panelScroll.setFadingEdgeLength(Ui.dp(20));
        panelScroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        panelScroll.setClipToPadding(false);
        panelScroll.setPadding(Ui.dp(12), 0, Ui.dp(12), 0);
        FrameLayout panel = new FrameLayout(this);
        panel.setPadding(0, Ui.dp(4), 0, Ui.dp(14)); // 余白は中身側に（フェードの位置がずれないように）
        panelScroll.addView(panel, new FrameLayout.LayoutParams(-1, -2));

        buildHomeCards();
        panel.addView(homeCards, new FrameLayout.LayoutParams(-1, -2));
        elementPanel = new ElementPanel(this);
        elementPanel.setBackground(Ui.round(Ui.surface, 24));
        elementPanel.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));
        panel.addView(elementPanel, new FrameLayout.LayoutParams(-1, -2));
        buildQuizPanel();
        panel.addView(quizPanel, new FrameLayout.LayoutParams(-1, -2));

        // --- ドック ---
        dock = new DockBar(this);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setBackground(Ui.round(Ui.alpha(Ui.surface, 0.92f), 30));
        dock.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(8));

        magnifier = new MagnifierView(this);
        root.addView(magnifier, new FrameLayout.LayoutParams(-1, -1));

        drawer = new AppDrawer(this);
        drawer.setListener(this);
        dock.setDrawer(drawer);
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int ime;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                insetL = bars.left;
                insetT = bars.top;
                insetR = bars.right;
                insetB = bars.bottom;
                ime = Math.max(insetB, insets.getInsets(WindowInsets.Type.ime()).bottom);
            } else {
                insetL = insets.getSystemWindowInsetLeft();
                insetT = insets.getSystemWindowInsetTop();
                insetR = insets.getSystemWindowInsetRight();
                ime = insets.getSystemWindowInsetBottom();
                insetB = insets.getStableInsetBottom();
            }
            home.setPadding(insetL, insetT, insetR, insetB);
            drawer.setInsets(insetT, ime);
            return insets;
        });
        return root;
    }

    private void buildHeader() {
        header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(20), Ui.dp(10), Ui.dp(8), Ui.dp(6));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        clock = new TextClock(this);
        clock.setFormat24Hour("H:mm");
        clock.setFormat12Hour("H:mm");
        clock.setTextSize(44);
        clock.setTextColor(Ui.onSurface);
        clock.setIncludeFontPadding(false);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        col.addView(clock);
        date = new TextClock(this);
        date.setFormat24Hour("M月d日 EEEE");
        date.setFormat12Hour("M月d日 EEEE");
        date.setTextColor(Ui.onSurfaceVar);
        date.setTextSize(14);
        date.setPadding(Ui.dp(2), Ui.dp(2), 0, 0);
        col.addView(date);
        header.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        // 時計 → 時計アプリ、日付 → カレンダー、下へスワイプ → 通知パネル
        clock.setOnClickListener(v -> { Ui.tap(v); open(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)); });
        date.setOnClickListener(v -> { Ui.tap(v); open(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)); });
        View.OnTouchListener pull = new View.OnTouchListener() {
            float downY;
            boolean fired;
            @Override public boolean onTouch(View v, android.view.MotionEvent ev) {
                if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) { downY = ev.getRawY(); fired = false; }
                else if (ev.getActionMasked() == android.view.MotionEvent.ACTION_MOVE && !fired && ev.getRawY() - downY > Ui.dpf(28)) {
                    fired = true;
                    v.cancelLongPress();
                    v.setPressed(false);
                    expandNotifications();
                }
                return fired;
            }
        };
        clock.setOnTouchListener(pull);
        date.setOnTouchListener(pull);

        TextView menu = ElementPanel.text(this, 22, Ui.onSurfaceVar, true, "⋮");
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(Ui.ripple(0x00000000, 22));
        menu.setContentDescription("メニュー");
        menu.setOnClickListener(v -> {
            Ui.tap(v);
            PopupMenu m = new PopupMenu(this, v);
            m.getMenu().add(0, 1, 0, "ホームアプリの設定");
            m.getMenu().add(0, 2, 1, "壁紙を変更");
            m.getMenu().add(0, 3, 2, "通知バッジを使う（通知へのアクセスを許可）");
            m.setOnMenuItemClickListener(item -> {
                try {
                    startActivity(item.getItemId() == 1 ? new Intent(Settings.ACTION_HOME_SETTINGS)
                            : item.getItemId() == 3 ? new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            : Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "壁紙を変更"));
                } catch (Exception ignored) {
                    // 対応する設定画面がない端末
                }
                return true;
            });
            m.show();
        });
        header.addView(menu, new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44)));
    }

    private void open(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "対応するアプリがない", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** 通知パネルを開く。公開 API が無いため、使えない端末では何もしない。 */
    @android.annotation.SuppressLint({"WrongConstant", "PrivateApi"})
    private void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb);
            Ui.tick(header);
        } catch (Exception ignored) {
            // 非対応端末
        }
    }

    // ================================================================ 縦・横の配置

    private static void detach(View v) {
        if (v.getParent() != null) ((android.view.ViewGroup) v.getParent()).removeView(v);
    }

    /**
     * 同じ部品を縦画面・横画面それぞれの配置に並べ替える（状態はそのまま）。
     * 縦: 時計 / 周期表 / 切替 / 詳細 / ドック。横: 左に周期表、右に時計・切替・詳細、右端に縦ドック。
     */
    private void arrange(boolean land) {
        landscape = land;
        for (View v : new View[]{header, table, modeBar, legendScroll, panelScroll, dock}) detach(v);
        home.removeAllViews();
        clock.setTextSize(land ? 30 : 44);
        for (int i = 0; i < modeBar.getChildCount(); i++) ((TextView) modeBar.getChildAt(i)).setTextSize(land ? 11 : 13);
        date.setTextSize(land ? 12 : 14);
        header.setPadding(Ui.dp(land ? 14 : 20), Ui.dp(land ? 4 : 10), Ui.dp(land ? 0 : 8), Ui.dp(land ? 0 : 6));
        elementPanel.setColumns(land ? 2 : 3);
        quizElementPanel.setColumns(land ? 2 : 3);
        dock.setOrientation(land ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        dock.setGravity(land ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.setMargins(Ui.dp(12), Ui.dp(land ? 4 : 6), Ui.dp(12), 0);
        if (land) {
            home.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -1, 1.75f);
            tlp.setMargins(Ui.dp(8), Ui.dp(4), 0, Ui.dp(4));
            home.addView(table, tlp);
            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.addView(header);
            right.addView(modeBar, mlp);
            right.addView(legendScroll, new LinearLayout.LayoutParams(-1, -2));
            right.addView(panelScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
            home.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(68), -1);
            dlp.setMargins(0, Ui.dp(8), Ui.dp(10), Ui.dp(8));
            home.addView(dock, dlp);
        } else {
            home.setOrientation(LinearLayout.VERTICAL);
            home.addView(header);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
            tlp.setMargins(Ui.dp(4), 0, Ui.dp(4), 0);
            home.addView(table, tlp);
            home.addView(modeBar, mlp);
            home.addView(legendScroll, new LinearLayout.LayoutParams(-1, -2));
            home.addView(panelScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, Ui.dp(72));
            dlp.setMargins(Ui.dp(12), 0, Ui.dp(12), Ui.dp(10));
            home.addView(dock, dlp);
        }
        rebuildDock();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        boolean land = c.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (land != landscape) {
            drawer.close(false);
            magnifier.hide();
            arrange(land);
        }
    }

    /** 触った瞬間に演出を起こす。放置後に止まっていた描画もここで再開する。 */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        boolean was = Fx.awake();
        Fx.wake();
        if (!was) kick(root);
        return super.dispatchTouchEvent(ev);
    }

    private void kick(View v) {
        if (v instanceof BackgroundFx || v instanceof FlameView || v instanceof OrbitalView || v instanceof AtomView) v.invalidate();
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) kick(g.getChildAt(i));
        }
    }

    /** 元素の位置から、表の波と背景の衝撃波を出す。 */
    private void burst(Element e, int color) {
        table.wave(e);
        bg.setColor(color);
        float[] c = table.centerOf(e);
        int[] a = new int[2], b = new int[2];
        table.getLocationInWindow(a);
        bg.getLocationInWindow(b);
        bg.pulse(a[0] - b[0] + c[0], a[1] - b[1] + c[1]);
    }

    private void buildModeBar() {
        modeBar = new LinearLayout(this);
        modeBar.setBackground(Ui.round(Ui.alpha(Ui.surface, 0.92f), 20));
        modeBar.setPadding(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(4));
        for (int i = 0; i < MODES.length; i++) {
            final int m = i;
            TextView t = ElementPanel.text(this, 13, Ui.onSurfaceVar, true, MODES[i]);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> {
                if (table.getMode() == m) return;
                Ui.tick(v);
                filter = null;
                table.setMode(m);
                styleModeBar();
                rebuildLegend();
            });
            modeBar.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(32), 1f));
        }
        styleModeBar();
    }

    private void styleModeBar() {
        for (int i = 0; i < modeBar.getChildCount(); i++) {
            TextView t = (TextView) modeBar.getChildAt(i);
            boolean on = i == table.getMode();
            t.setBackground(on ? Ui.round(Ui.accent, 16) : Ui.ripple(0x00000000, 16));
            t.setTextColor(on ? Ui.onAccent : Ui.onSurfaceVar);
        }
    }

    /** 色分けモードに応じた凡例。分類・状態は押すと絞り込める。 */
    private void rebuildLegend() {
        legend.removeAllViews();
        legendScroll.scrollTo(0, 0);
        int mode = table.getMode();
        if (mode == PeriodicTableView.MODE_CATEGORY) {
            for (String[] c : CATEGORIES) legend.addView(chip(c[0], c[1], PeriodicTableView.categoryColor(c[0])));
        } else if (mode == PeriodicTableView.MODE_PHASE) {
            for (String ph : new String[]{"固体", "液体", "気体"}) legend.addView(chip(ph, ph + "（常温）", PeriodicTableView.phaseColor(ph)));
        } else {
            boolean en = mode == PeriodicTableView.MODE_EN;
            legend.addView(ElementPanel.text(this, 12, Ui.onSurfaceVar, false, "小"));
            View bar = new View(this);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{PeriodicTableView.heat(0f), PeriodicTableView.heat(0.5f), PeriodicTableView.heat(1f)});
            g.setCornerRadius(Ui.dpf(5));
            bar.setBackground(g);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(72), Ui.dp(10));
            blp.setMargins(Ui.dp(6), 0, Ui.dp(6), 0);
            legend.addView(bar, blp);
            legend.addView(ElementPanel.text(this, 12, Ui.onSurfaceVar, false, "大"));
            TextView cap = ElementPanel.text(this, 12, Ui.onSurface, false,
                    en ? "右上ほど大きい。最大は F（貴ガスは値なし）" : "右上ほど大きい。最大は He、同周期では貴ガス");
            cap.setPadding(Ui.dp(12), 0, 0, 0);
            legend.addView(cap);
        }
    }

    private View chip(String key, String label, int color) {
        boolean on = key.equals(filter);
        SpannableStringBuilder sb = new SpannableStringBuilder("● ");
        sb.setSpan(new ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(label);
        TextView t = ElementPanel.text(this, 12, on ? Ui.onSurface : Ui.onSurfaceVar, on, sb);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(11), 0, Ui.dp(13), 0);
        t.setBackground(Ui.ripple(on ? Ui.over(color, Ui.surface, 0.32f) : Ui.alpha(Ui.surface, 0.92f), 15));
        t.setOnClickListener(v -> {
            Ui.tick(v);
            filter = key.equals(filter) ? null : key;
            table.setFilter(filter);
            int x = legendScroll.getScrollX();
            rebuildLegend();
            legendScroll.post(() -> legendScroll.scrollTo(x, 0));
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(30));
        lp.rightMargin = Ui.dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    // ---------------------------------------------------------------- ホームのカード

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.round(Ui.surface, 24));
        c.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView pillButton(String label, boolean filled) {
        TextView t = ElementPanel.text(this, 14, filled ? Ui.onAccent : Ui.accent, true, label);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(20), 0, Ui.dp(20), 0);
        t.setBackground(Ui.ripple(filled ? Ui.accent : Ui.surfaceHigh, 22));
        t.setMinHeight(Ui.dp(44));
        Ui.pressable(t);
        return t;
    }

    private void buildHomeCards() {
        homeCards = new LinearLayout(this);
        homeCards.setOrientation(LinearLayout.VERTICAL);

        nightCard = ElementPanel.text(this, 14, 0xFFFFCC80, false,
                "23時を過ぎた。スマホを置いて寝ろ。今日覚えた内容は睡眠中に定着する。");
        nightCard.setBackground(Ui.round(0xFF2B1D0E, 20));
        nightCard.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12));
        nightCard.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.bottomMargin = Ui.dp(8);
        nightCard.setVisibility(View.GONE);
        homeCards.addView(nightCard, nlp);

        // 今日の元素
        LinearLayout todayCard = card();
        todayCard.setOrientation(LinearLayout.HORIZONTAL);
        todayCard.setGravity(Gravity.CENTER_VERTICAL);
        todayCard.setBackground(Ui.ripple(Ui.surface, 24));
        todayTile = ElementPanel.text(this, 24, 0xFF0B0E14, true);
        todayTile.setGravity(Gravity.CENTER);
        todayTile.setIncludeFontPadding(false);
        todayCard.addView(todayTile, new LinearLayout.LayoutParams(Ui.dp(56), Ui.dp(56)));
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        tcol.setPadding(Ui.dp(14), 0, 0, 0);
        todayTitle = ElementPanel.text(this, 16, Ui.onSurface, true);
        todayNote = ElementPanel.text(this, 13, Ui.onSurfaceVar, false);
        todayNote.setMaxLines(2);
        todayNote.setEllipsize(TextUtils.TruncateAt.END);
        todayNote.setLineSpacing(0, 1.15f);
        tcol.addView(todayTitle);
        tcol.addView(todayNote);
        todayCard.addView(tcol, new LinearLayout.LayoutParams(0, -2, 1f));
        todayCard.setOnClickListener(v -> { Ui.tap(v); select(today); });
        Ui.pressable(todayCard);
        homeCards.addView(todayCard);

        // 炎色反応: 7 つの炎を実際の色で燃やす
        LinearLayout flames = card();
        flames.setPadding(Ui.dp(12), Ui.dp(12), Ui.dp(12), Ui.dp(10));
        TextView ft = ElementPanel.text(this, 12, Ui.onSurfaceVar, true, "炎色反応");
        ft.setPadding(Ui.dp(4), 0, 0, 0);
        flames.addView(ft);
        int[] fc = new int[Quiz.FLAME.length];
        String[] fl = new String[Quiz.FLAME.length];
        for (int i = 0; i < fc.length; i++) {
            fc[i] = (int) Quiz.FLAME[i][2];
            fl[i] = elements.get((int) Quiz.FLAME[i][0] - 1).symbol + "\n" + Quiz.FLAME[i][1];
        }
        flameRow = new FlameView(this);
        flameRow.setFlames(fc, fl);
        flames.addView(flameRow, new LinearLayout.LayoutParams(-1, Ui.dp(92)));
        homeCards.addView(flames);

        // クイズ
        LinearLayout q = card();
        LinearLayout qhead = new LinearLayout(this);
        qhead.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout qcol = new LinearLayout(this);
        qcol.setOrientation(LinearLayout.VERTICAL);
        qcol.addView(ElementPanel.text(this, 16, Ui.onSurface, true, "位置当てクイズ"));
        quizBest = ElementPanel.text(this, 13, Ui.onSurfaceVar, false);
        qcol.addView(quizBest);
        qhead.addView(qcol, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView start = pillButton("スタート", true);
        start.setOnClickListener(v -> { Ui.confirm(v); startQuiz(); });
        qhead.addView(start, new LinearLayout.LayoutParams(-2, Ui.dp(44)));
        q.addView(qhead);
        poolRow = new LinearLayout(this);
        poolRow.setBackground(Ui.round(Ui.surfaceHigh, 18));
        poolRow.setPadding(Ui.dp(3), Ui.dp(3), Ui.dp(3), Ui.dp(3));
        for (int i = 0; i < Quiz.POOLS.length; i++) {
            final int p = i;
            TextView t = ElementPanel.text(this, 12, Ui.onSurfaceVar, true, Quiz.POOLS[i]);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> { Ui.tick(v); quiz.setPool(p); stylePools(); });
            poolRow.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(30), 1f));
        }
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.topMargin = Ui.dp(12);
        q.addView(poolRow, plp);
        homeCards.addView(q);
        stylePools();

        // 語呂
        LinearLayout tips = card();
        tips.addView(ElementPanel.text(this, 12, Ui.onSurfaceVar, true, "語呂（1〜20番）"));
        TextView goro = ElementPanel.text(this, 14, Ui.onSurface, false,
                "水兵リーベ僕の船　七曲がるシップスクラークか\nH He Li Be B C N O F Ne Na Mg Al Si P S Cl Ar K Ca");
        goro.setLineSpacing(0, 1.25f);
        goro.setPadding(0, Ui.dp(2), 0, 0);
        tips.addView(goro);
        homeCards.addView(tips);
    }

    private void stylePools() {
        for (int i = 0; i < poolRow.getChildCount(); i++) {
            TextView t = (TextView) poolRow.getChildAt(i);
            boolean on = i == quiz.pool;
            t.setBackground(on ? Ui.round(Ui.over(Ui.accent, Ui.surfaceHigh, 0.28f), 15) : Ui.ripple(0x00000000, 15));
            t.setTextColor(on ? Ui.onSurface : Ui.onSurfaceVar);
        }
        int best = quiz.best();
        quizBest.setText(best > 0 ? "最高 " + best + " 連続正解（" + Quiz.POOLS[quiz.pool] + "）" : "記号を隠した表で、場所を答える");
    }

    private void bindToday() {
        List<Element> pool = new ArrayList<>();
        for (Element e : elements) if (!e.note.isEmpty()) pool.add(e);
        Calendar c = Calendar.getInstance();
        Element t = pool.get((c.get(Calendar.YEAR) * 366 + c.get(Calendar.DAY_OF_YEAR)) % pool.size());
        if (t == today) return;
        today = t;
        int color = PeriodicTableView.categoryColor(t.category);
        todayTile.setBackground(Ui.round(color, 18));
        todayTile.setTextColor(Ui.isLight(color) ? 0xFF0B0E14 : 0xFFFFFFFF);
        todayTile.setText(t.symbol);
        SpannableStringBuilder sb = new SpannableStringBuilder("今日の元素  ");
        sb.setSpan(new ForegroundColorSpan(Ui.onSurfaceVar), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(0.78f), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.NORMAL), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(t.ja);
        todayTitle.setText(sb);
        todayNote.setText(t.note);
    }

    // ---------------------------------------------------------------- クイズのパネル

    private void buildQuizPanel() {
        quizPanel = new LinearLayout(this);
        quizPanel.setOrientation(LinearLayout.VERTICAL);

        LinearLayout q = card();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        quizStats = ElementPanel.text(this, 13, Ui.onSurfaceVar, false);
        top.addView(quizStats, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView end = ElementPanel.text(this, 13, Ui.accent, true, "終了");
        end.setGravity(Gravity.CENTER);
        end.setBackground(Ui.ripple(Ui.surfaceHigh, 16));
        end.setOnClickListener(v -> { Ui.tap(v); showHome(true); });
        top.addView(end, new LinearLayout.LayoutParams(Ui.dp(64), Ui.dp(32)));
        q.addView(top);
        quizQuestion = ElementPanel.text(this, 20, Ui.onSurface, false);
        quizQuestion.setPadding(0, Ui.dp(6), 0, Ui.dp(2));
        q.addView(quizQuestion);
        quizHint = ElementPanel.text(this, 13, Ui.onSurfaceVar, false, "記号と番号は隠してある。族と周期から場所を割り出せ。");
        q.addView(quizHint);
        quizPanel.addView(q);

        quizFeedback = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        quizResult = ElementPanel.text(this, 16, Ui.GOOD, true);
        row.addView(quizResult, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView next = pillButton("次へ", true);
        next.setOnClickListener(v -> { Ui.tap(v); nextQuestion(); });
        row.addView(next, new LinearLayout.LayoutParams(-2, Ui.dp(44)));
        quizFeedback.addView(row);
        quizElementPanel = new ElementPanel(this);
        quizElementPanel.setPadding(0, Ui.dp(12), 0, 0);
        quizFeedback.addView(quizElementPanel);
        quizPanel.addView(quizFeedback);
    }

    // ================================================================ 画面の切り替え

    private void swap(View show, boolean animate) {
        for (View v : new View[]{homeCards, elementPanel, quizPanel}) {
            v.animate().cancel();
            v.setVisibility(v == show ? View.VISIBLE : View.GONE);
        }
        panelScroll.scrollTo(0, 0);
        if (animate) enter(show);
        else { show.setAlpha(1f); show.setTranslationY(0); }
    }

    /** 下から少し浮き上がりながら現れる。 */
    private void enter(View v) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(Ui.dpf(18));
        v.animate().alpha(1f).translationY(0).setDuration(300).setInterpolator(Ui.EASE_OUT).start();
    }

    private void setChrome(boolean visible) {
        int vis = visible ? View.VISIBLE : View.GONE;
        modeBar.setVisibility(vis);
        legendScroll.setVisibility(vis);
    }

    private void showHome(boolean animate) {
        quizOn = false;
        table.setQuizMode(false);
        table.setSelected(null);
        setChrome(true);
        bg.setColor(Ui.accent);
        bindToday();
        stylePools();
        swap(homeCards, animate);
    }

    private void select(Element e) {
        boolean wasShown = elementPanel.getVisibility() == View.VISIBLE;
        table.setSelected(e);
        burst(e, PeriodicTableView.categoryColor(e.category));
        elementPanel.bind(e);
        if (wasShown) {
            panelScroll.smoothScrollTo(0, 0);
            elementPanel.animate().cancel();
            elementPanel.setAlpha(0.35f);
            elementPanel.setTranslationY(Ui.dpf(8));
            elementPanel.animate().alpha(1f).translationY(0).setDuration(220).setInterpolator(Ui.EASE_OUT).start();
        } else {
            swap(elementPanel, true);
        }
    }

    // ================================================================ 周期表からの通知

    @Override
    public void onScrub(Element e, float cx, float top) {
        if (e == null) { magnifier.hide(); return; }
        int[] a = new int[2], b = new int[2];
        table.getLocationInWindow(a);
        magnifier.getLocationInWindow(b);
        magnifier.show(e, a[0] - b[0] + cx, a[1] - b[1] + top, quizOn && !answered);
    }

    @Override
    public void onScrubEnd() { magnifier.hide(); }

    @Override
    public void onElementTap(Element e) {
        if (quizOn) {
            if (!answered) answer(e);
            return;
        }
        Ui.tap(table);
        select(e);
    }

    // ================================================================ クイズ

    private void startQuiz() {
        quizOn = true;
        quiz.start();
        filter = null;
        table.setMode(PeriodicTableView.MODE_CATEGORY);
        table.setFilter(null);
        styleModeBar();
        rebuildLegend();
        table.setSelected(null);
        table.setQuizMode(true);
        setChrome(false);
        bg.setColor(Ui.accent);
        swap(quizPanel, true);
        nextQuestion();
    }

    private void nextQuestion() {
        answered = false;
        table.flash(null, null);
        Quiz.Question q = quiz.next();
        SpannableStringBuilder sb = new SpannableStringBuilder(q.lead);
        int st = sb.length();
        sb.append(q.key);
        sb.setSpan(new StyleSpan(Typeface.BOLD), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(1.35f), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(Ui.accent), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(q.tail);
        quizQuestion.setText(sb);
        quizHint.setVisibility(View.VISIBLE);
        quizFeedback.setVisibility(View.GONE);
        panelScroll.scrollTo(0, 0);
        enter(quizQuestion);
        renderStats();
    }

    private void renderStats() {
        quizStats.setText(Quiz.POOLS[quiz.pool] + " ・ 連続 " + quiz.streak + " ・ 最高 " + quiz.best()
                + " ・ 正答 " + quiz.correct + "/" + quiz.asked);
    }

    private void answer(Element tapped) {
        answered = true;
        Element target = quiz.current.answer;
        boolean ok = quiz.answer(tapped);
        table.flash(target, ok ? null : tapped);
        burst(target, ok ? Ui.GOOD : Ui.BAD);
        renderStats();
        quizHint.setVisibility(View.GONE);
        if (ok) {
            Ui.confirm(table);
            quizResult.setTextColor(Ui.GOOD);
            quizResult.setText("正解");
        } else {
            Ui.reject(table);
            quizResult.setTextColor(Ui.BAD);
            quizResult.setText("不正解。そこは " + tapped.ja + "（" + tapped.symbol + "）");
            // 表を左右に振る
            float d = Ui.dpf(1);
            android.animation.ObjectAnimator shake = android.animation.ObjectAnimator.ofFloat(table, "translationX",
                    0, 12 * d, -10 * d, 7 * d, -4 * d, 2 * d, 0);
            shake.setDuration(420);
            shake.start();
        }
        quizElementPanel.bind(target);
        quizFeedback.setVisibility(View.VISIBLE);
        enter(quizFeedback);
    }

    // ================================================================ ドック

    /** 初回だけ、電話・メッセージ・ブラウザ・カメラをドックに入れておく。 */
    private void initDefaultDock() {
        if (prefs.getBoolean("dock_init", false)) return;
        prefs.edit().putBoolean("dock_init", true).apply();
        PackageManager pm = getPackageManager();
        Intent[] intents = {
                new Intent(Intent.ACTION_DIAL),
                new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com")),
                new Intent(MediaStore.ACTION_IMAGE_CAPTURE)};
        for (Intent i : intents) {
            try {
                ResolveInfo ri = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
                if (ri == null || ri.activityInfo == null || "android".equals(ri.activityInfo.packageName)) continue;
                Intent launch = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
                if (launch != null && launch.getComponent() != null) Dock.add(this, launch.getComponent());
            } catch (Exception ignored) {
                // 見つからないものは入れない
            }
        }
    }

    private void rebuildDock() {
        if (dock == null) return;
        dock.removeAllViews();
        PackageManager pm = getPackageManager();
        int count = 0;
        for (ComponentName cn : Dock.get(this)) {
            ImageView iv = new BadgeIcon(this, cn.getPackageName());
            try {
                iv.setImageDrawable(pm.getActivityIcon(cn));
            } catch (PackageManager.NameNotFoundException e) {
                Dock.remove(this, cn); // アンインストール済み
                continue;
            }
            iv.setPadding(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(4));
            iv.setOnClickListener(v -> { Ui.tap(v); AppDrawer.start(this, cn, v); });
            iv.setOnLongClickListener(v -> {
                Ui.heavy(v);
                PopupMenu m = new PopupMenu(this, v);
                m.getMenu().add("ドックから外す");
                m.setOnMenuItemClickListener(item -> { Dock.remove(this, cn); rebuildDock(); return true; });
                m.show();
                return true;
            });
            Ui.pressable(iv);
            dock.addView(iv, landscape ? new LinearLayout.LayoutParams(Ui.dp(48), 0, 1f)
                    : new LinearLayout.LayoutParams(0, Ui.dp(56), 1f));
            count++;
        }
        if (count == 0) {
            // 空のとき: 縦画面では使い方を表示、横画面（縦長のドック）では余白だけ
            TextView hint = ElementPanel.text(this, 13, Ui.onSurfaceVar, false,
                    landscape ? "" : "上にスワイプでアプリ一覧。長押しでここに追加");
            hint.setPadding(Ui.dp(10), 0, 0, 0);
            dock.addView(hint, landscape ? new LinearLayout.LayoutParams(-1, 0, 1f) : new LinearLayout.LayoutParams(0, -2, 1f));
        }
        View apps = new AppsIcon(this);
        apps.setBackground(Ui.ripple(Ui.surfaceHigh, 24));
        apps.setContentDescription("アプリ一覧");
        apps.setOnClickListener(v -> { Ui.tap(v); drawer.open(false); });
        apps.setOnLongClickListener(v -> { Ui.heavy(v); drawer.open(true); return true; });
        Ui.pressable(apps);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48));
        if (landscape) alp.topMargin = Ui.dp(6);
        else alp.leftMargin = Ui.dp(6);
        dock.addView(apps, alp);
    }

    /** 通知があるときに右上へ点を描くアイコン。 */
    private static final class BadgeIcon extends ImageView {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String pkg;
        BadgeIcon(Context c, String pkg) { super(c); this.pkg = pkg; }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!NotifyService.has(pkg)) return;
            float s = Math.min(getWidth(), getHeight()), cx = getWidth() / 2f + s * 0.32f, cy = getHeight() / 2f - s * 0.32f;
            p.setColor(Ui.surface);
            canvas.drawCircle(cx, cy, Ui.dpf(7), p);
            p.setColor(0xFFFF6B5E);
            canvas.drawCircle(cx, cy, Ui.dpf(5), p);
        }
    }

    /** 3×3 の点で描く「アプリ一覧」アイコン。 */
    private static final class AppsIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        AppsIcon(Context c) { super(c); }
        @Override protected void onDraw(Canvas canvas) {
            p.setColor(Ui.onSurface);
            float cx = getWidth() / 2f, cy = getHeight() / 2f, s = Ui.dpf(7);
            for (int i = -1; i <= 1; i++)
                for (int j = -1; j <= 1; j++) canvas.drawCircle(cx + i * s, cy + j * s, Ui.dpf(2.1f), p);
        }
    }

    // ================================================================ ドロワー連動

    @Override
    public void onProgress(float p) {
        // 一覧が上がるほどホームは奥に引いて暗くなる
        home.setScaleX(1f - 0.06f * p);
        home.setScaleY(1f - 0.06f * p);
        home.setAlpha(1f - 0.6f * p);
        if (Build.VERSION.SDK_INT >= 31) { // 背後を実際にぼかしてガラス越しのように見せる
            float r = Ui.dpf(22) * p;
            android.graphics.RenderEffect fx = p < 0.02f ? null
                    : android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP);
            home.setRenderEffect(fx);
            bg.setRenderEffect(fx);
        }
    }

    @Override
    public void onDockChanged() { rebuildDock(); }

    private void refreshBadges() {
        for (int i = 0; i < dock.getChildCount(); i++) dock.getChildAt(i).invalidate();
        drawer.invalidateField();
    }

    // ================================================================ 夜間

    private void updateNight() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean night = h >= 23 || h < 5;
        nightCard.setVisibility(night ? View.VISIBLE : View.GONE);
        table.setDim(night ? 0.6f : 1f);
        if (!quizOn && homeCards.getVisibility() == View.VISIBLE) bindToday();
    }

    // ================================================================ ライフサイクル

    @Override
    protected void onResume() {
        super.onResume();
        Fx.setResumed(true);
        NotifyService.setOnChange(() -> runOnUiThread(this::refreshBadges));
        kick(root);
        haveBase = false;
        if (gravity != null) sensors.registerListener(tiltListener, gravity, android.hardware.SensorManager.SENSOR_DELAY_GAME);
        rebuildDock();
        updateNight();
        register(tick, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    @Override
    protected void onPause() {
        super.onPause();
        Fx.setResumed(false);
        NotifyService.setOnChange(null);
        if (gravity != null) sensors.unregisterListener(tiltListener);
        table.setTilt(0, 0);
        try { unregisterReceiver(tick); } catch (IllegalArgumentException ignored) { /* 未登録 */ }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // ホームボタン: 一覧を閉じ、詳細を開いていれば最初の状態へ
        if (drawer.isOpen()) drawer.close(true);
        else if (!quizOn && elementPanel.getVisibility() == View.VISIBLE) showHome(true);
        else panelScroll.smoothScrollTo(0, 0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // ランチャーなので終了しない
        if (drawer.isOpen()) drawer.close(true);
        else if (quizOn || elementPanel.getVisibility() == View.VISIBLE) showHome(true);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(packages); } catch (IllegalArgumentException ignored) { /* 未登録 */ }
        super.onDestroy();
    }
}
