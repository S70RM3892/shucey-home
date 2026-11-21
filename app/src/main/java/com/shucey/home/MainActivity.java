package com.shucey.home;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity implements PeriodicTableView.OnElementTapListener {

    private static final int FG = 0xFFE6EDF3, FG2 = 0xFF9DA7B3, BG = 0xFF0E1116, CARD = 0xFF161B22;
    private static final String[] POOL_NAMES = {"1〜20番", "入試頻出", "全118元素"};
    /** 入試頻出: 1〜36 に加えて無機化学で頻出の重元素 */
    private static final int[] FREQUENT_EXTRA = {47, 50, 53, 55, 56, 78, 79, 80, 82};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private List<Element> elements;
    private PeriodicTableView table;
    private TextView detail, quizText, nightBanner, legend;
    private Button quizButton, poolButton;
    private LinearLayout quizBar, dock;
    private ScrollView scroll;
    private float dp;
    private SharedPreferences prefs;

    private boolean quiz;
    private int pool, streak;
    private Element target;
    private boolean answering;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateNight(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        prefs = Dock.prefs(this);
        pool = prefs.getInt("pool", 1);
        elements = Element.load(this);
        setContentView(buildUi());
        showWelcome();
    }

    // ---------------------------------------------------------------- UI

    private int px(float v) { return (int) (v * dp); }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);

        // 時計
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(px(16), px(12), px(16), px(4));
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextSize(44);
        clock.setTextColor(FG);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setOnLongClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            return true;
        });
        top.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("M月d日（E）");
        date.setFormat12Hour("M月d日（E）");
        date.setTextColor(FG2);
        date.setTextSize(14);
        top.addView(date);
        root.addView(top);

        nightBanner = new TextView(this);
        nightBanner.setTextColor(0xFFFFCC80);
        nightBanner.setTextSize(14);
        nightBanner.setPadding(px(16), px(8), px(16), px(8));
        nightBanner.setBackgroundColor(0xFF2A1A0A);
        nightBanner.setVisibility(View.GONE);
        root.addView(nightBanner, new LinearLayout.LayoutParams(-1, -2));

        // クイズバー
        quizBar = new LinearLayout(this);
        quizBar.setOrientation(LinearLayout.HORIZONTAL);
        quizBar.setGravity(Gravity.CENTER_VERTICAL);
        quizBar.setPadding(px(16), px(6), px(8), px(6));
        quizBar.setVisibility(View.GONE);
        quizText = new TextView(this);
        quizText.setTextColor(FG);
        quizText.setTextSize(17);
        quizBar.addView(quizText, new LinearLayout.LayoutParams(0, -2, 1f));
        poolButton = smallButton("");
        poolButton.setOnClickListener(v -> {
            pool = (pool + 1) % POOL_NAMES.length;
            prefs.edit().putInt("pool", pool).apply();
            updatePoolButton();
            nextQuestion();
        });
        quizBar.addView(poolButton);
        root.addView(quizBar, new LinearLayout.LayoutParams(-1, -2));

        // 本体（周期表 + 凡例 + 詳細）
        scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(px(4), 0, px(4), px(12));

        table = new PeriodicTableView(this);
        table.setElements(elements);
        table.setOnElementTapListener(this);
        body.addView(table, new LinearLayout.LayoutParams(-1, -2));

        legend = new TextView(this);
        legend.setTextSize(11);
        legend.setPadding(px(8), px(4), px(8), px(8));
        legend.setText(buildLegend());
        body.addView(legend);

        detail = new TextView(this);
        detail.setTextColor(FG);
        detail.setTextSize(15);
        detail.setLineSpacing(0, 1.15f);
        detail.setPadding(px(16), px(14), px(16), px(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(px(12));
        detail.setBackground(bg);
        detail.setTextIsSelectable(false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.setMargins(px(8), px(4), px(8), 0);
        body.addView(detail, dlp);

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ドック
        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(px(8), px(6), px(8), px(8));
        dock.setBackgroundColor(CARD);
        root.addView(dock, new LinearLayout.LayoutParams(-1, -2));

        quizButton = smallButton("クイズ");
        quizButton.setOnClickListener(v -> setQuiz(!quiz));
        updatePoolButton();
        return root;
    }

    private Button smallButton(String label) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(13);
        btn.setTextColor(FG);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF21262D);
        bg.setCornerRadius(px(18));
        btn.setBackground(bg);
        btn.setMinHeight(px(40));
        btn.setMinimumHeight(px(40));
        btn.setPadding(px(14), 0, px(14), 0);
        return btn;
    }

    private CharSequence buildLegend() {
        String[][] items = {
                {"alkali", "アルカリ金属"}, {"alkaline_earth", "アルカリ土類"}, {"transition", "遷移元素"},
                {"metal", "典型金属"}, {"nonmetal", "典型非金属"}, {"halogen", "ハロゲン"},
                {"noble", "貴ガス"}, {"lanthanoid", "ランタノイド"}, {"actinoid", "アクチノイド"}};
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (String[] it : items) {
            int s = sb.length();
            sb.append("■\u2060");
            sb.setSpan(new ForegroundColorSpan(PeriodicTableView.categoryColor(it[0])), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s = sb.length();
            sb.append(it[1]).append("　");
            sb.setSpan(new ForegroundColorSpan(FG2), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private void rebuildDock() {
        dock.removeAllViews();
        PackageManager pm = getPackageManager();
        for (ComponentName cn : Dock.get(this)) {
            ImageView iv = new ImageView(this);
            try {
                iv.setImageDrawable(pm.getActivityIcon(cn));
            } catch (PackageManager.NameNotFoundException e) {
                Dock.remove(this, cn); // アンインストール済み
                continue;
            }
            iv.setPadding(px(6), px(6), px(6), px(6));
            iv.setOnClickListener(v -> launch(cn));
            iv.setOnLongClickListener(v -> {
                Dock.remove(this, cn);
                rebuildDock();
                Toast.makeText(this, "ドックから外した", Toast.LENGTH_SHORT).show();
                return true;
            });
            dock.addView(iv, new LinearLayout.LayoutParams(px(52), px(52)));
        }
        View spacer = new View(this);
        dock.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        if (quizButton.getParent() != null) ((LinearLayout) quizButton.getParent()).removeView(quizButton);
        dock.addView(quizButton);
        Button apps = smallButton("アプリ");
        apps.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = px(8);
        dock.addView(apps, lp);
    }

    private void launch(ComponentName cn) {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (Exception e) {
            Toast.makeText(this, "起動できない", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------- 学習

    private void showWelcome() {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        bold(sb, "元素をタップ", 1.2f);
        sb.append("\n性質・電子配置・入試ポイントを表示する。\n\n");
        bold(sb, "語呂（1〜20）", 1f);
        sb.append("\n水兵リーベ僕の船　七曲がるシップスクラークか\nH He Li Be B C N O F Ne Na Mg Al Si P S Cl Ar K Ca\n\n");
        bold(sb, "炎色反応", 1f);
        sb.append("\nLi 赤／Na 黄／K 赤紫／Ca 橙赤／Sr 紅／Ba 黄緑／Cu 青緑\n\n");
        sb.append("「クイズ」で位置当て練習。ミスした元素ほど多く出題される。\n");
        sb.append("アプリ一覧で長押し→ドックに追加。時計を長押し→ホームアプリ設定。");
        detail.setText(sb);
    }

    private static void bold(SpannableStringBuilder sb, String s, float size) {
        int st = sb.length();
        sb.append(s);
        sb.setSpan(new StyleSpan(Typeface.BOLD), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (size != 1f) sb.setSpan(new RelativeSizeSpan(size), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void showDetail(Element e) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int st = sb.length();
        sb.append(e.symbol);
        sb.setSpan(new RelativeSizeSpan(2.2f), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(PeriodicTableView.categoryColor(e.category)), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("  ");
        bold(sb, e.ja, 1.3f);
        sb.append("  ").append(e.en).append('\n');

        row(sb, "原子番号", String.valueOf(e.number));
        row(sb, "原子量", e.mass);
        row(sb, "位置", "第" + e.period + "周期・" + e.group + "族");
        row(sb, "分類", e.categoryJa());
        row(sb, "電子殻", e.shellsText());
        int v = e.valenceElectrons();
        if (v >= 0) row(sb, "価電子", v + "個");
        row(sb, "電子配置", e.config);
        row(sb, "電気陰性度", Double.isNaN(e.electronegativity) ? "—" : String.format(Locale.ROOT, "%.2f（ポーリング）", e.electronegativity));
        row(sb, "常温の状態", e.phase);
        if (!e.note.isEmpty()) {
            sb.append('\n');
            int s2 = sb.length();
            sb.append("入試ポイント\n");
            sb.setSpan(new ForegroundColorSpan(0xFFFFD54F), s2, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new StyleSpan(Typeface.BOLD), s2, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(e.note);
        }
        detail.setText(sb);
    }

    private static void row(SpannableStringBuilder sb, String k, String v) {
        int st = sb.length();
        sb.append(k).append("　");
        sb.setSpan(new ForegroundColorSpan(FG2), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(v).append('\n');
    }

    @Override
    public void onElementTap(Element e) {
        if (quiz) {
            answer(e);
            return;
        }
        table.setSelected(e);
        showDetail(e);
    }

    // ---------------------------------------------------------- クイズ

    private void setQuiz(boolean on) {
        quiz = on;
        handler.removeCallbacksAndMessages(null);
        table.setQuizMode(on);
        table.setSelected(null);
        quizBar.setVisibility(on ? View.VISIBLE : View.GONE);
        quizButton.setText(on ? "クイズ終了" : "クイズ");
        if (on) {
            streak = 0;
            nextQuestion();
        } else {
            showWelcome();
        }
    }

    private void updatePoolButton() { poolButton.setText(POOL_NAMES[pool]); }

    private List<Element> poolElements() {
        List<Element> l = new ArrayList<>();
        for (Element e : elements) {
            boolean in;
            if (pool == 0) in = e.number <= 20;
            else if (pool == 1) {
                in = e.number <= 36;
                for (int n : FREQUENT_EXTRA) if (n == e.number) in = true;
            } else in = true;
            if (in) l.add(e);
        }
        return l;
    }

    private int misses(Element e) { return prefs.getInt("miss_" + e.number, 0); }

    private void nextQuestion() {
        answering = false;
        table.flash(null, null);
        List<Element> l = poolElements();
        // ミスが多い元素ほど出やすい重み付き抽選（同じ元素の連続は避ける）
        int total = 0;
        int[] w = new int[l.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = l.get(i) == target ? 0 : 1 + 3 * Math.min(misses(l.get(i)), 5);
            total += w[i];
        }
        int r = random.nextInt(total);
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r < 0) { target = l.get(i); break; }
        }
        renderQuestion();
        detail.setText("記号と番号は隠してある。位置で答えろ。\n族と周期を数えて場所を特定すること。");
    }

    private void renderQuestion() {
        int best = prefs.getInt("best_" + pool, 0);
        SpannableStringBuilder sb = new SpannableStringBuilder("Q. ");
        bold(sb, target.ja, 1.25f);
        sb.append(" はどこ？\n");
        int st = sb.length();
        sb.append("連続正解 ").append(String.valueOf(streak)).append("（最高 ").append(String.valueOf(best)).append("）");
        sb.setSpan(new ForegroundColorSpan(FG2), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(0.75f), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        quizText.setText(sb);
    }

    private void answer(Element tapped) {
        if (answering) return;
        answering = true;
        SharedPreferences.Editor ed = prefs.edit();
        boolean ok = tapped == target;
        if (ok) {
            streak++;
            if (streak > prefs.getInt("best_" + pool, 0)) ed.putInt("best_" + pool, streak);
            ed.putInt("miss_" + target.number, Math.max(0, misses(target) - 1));
            table.flash(target, null);
        } else {
            streak = 0;
            ed.putInt("miss_" + target.number, misses(target) + 1);
            table.flash(target, tapped);
        }
        ed.apply();
        renderQuestion();
        showDetail(target);
        if (!ok) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            int st = sb.length();
            sb.append("✗ そこは ").append(tapped.ja).append("（").append(tapped.symbol).append("）。正解は緑。\n\n");
            sb.setSpan(new ForegroundColorSpan(0xFFEF9A9A), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(detail.getText());
            detail.setText(sb);
        }
        handler.postDelayed(this::nextQuestion, ok ? 900 : 3500);
    }

    // ---------------------------------------------------------- 夜間

    private void updateNight() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean night = h >= 23 || h < 5;
        if (night) {
            nightBanner.setText("23時を過ぎた。スマホを置いて寝ろ。覚えた内容は睡眠中に定着する。");
            nightBanner.setVisibility(View.VISIBLE);
        } else {
            nightBanner.setVisibility(View.GONE);
        }
        table.setDim(night ? 0.55f : 1f);
    }

    // ---------------------------------------------------------- ライフサイクル

    @Override
    protected void onResume() {
        super.onResume();
        rebuildDock();
        updateNight();
        registerReceiver(tick, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(tick); } catch (IllegalArgumentException ignored) {}
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // ホームボタン再押下で先頭へ
        if (scroll != null) scroll.smoothScrollTo(0, 0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // ランチャーなので終了しない。クイズ中なら終了、選択中なら解除。
        if (quiz) setQuiz(false);
        else {
            table.setSelected(null);
            showWelcome();
            scroll.smoothScrollTo(0, 0);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
