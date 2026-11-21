package com.shucey.home;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** 元素の詳細カード一式。 */
public class ElementPanel extends LinearLayout {
    private final TextView tile, name, sub, configText, atomCap, orbitalCap, note, flameText;
    private final LinearLayout[] statCell = new LinearLayout[6];
    private final TextView[] statValue = new TextView[6];
    private final LinearLayout stats, noteCard, flameCard, orbitalCol;
    private final AtomView atom;
    private final OrbitalView orbital;
    private final FlameView flame;
    private int columns;
    private Element bound;
    private static final String[] STAT_LABELS = {"原子量", "価電子", "電気陰性度", "第一イオン化E", "融点", "沸点"};
    private static final String[] ORBITAL_NAMES = {"s", "p", "d", "f"};

    public ElementPanel(Context c) {
        super(c);
        setOrientation(VERTICAL);

        // --- 見出し ---
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        tile = new TextView(c);
        tile.setGravity(Gravity.CENTER);
        tile.setTypeface(Typeface.DEFAULT_BOLD);
        tile.setTextSize(30);
        tile.setIncludeFontPadding(false);
        head.addView(tile, new LayoutParams(Ui.dp(68), Ui.dp(68)));
        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(VERTICAL);
        titles.setPadding(Ui.dp(14), 0, 0, 0);
        name = text(c, 22, Ui.onSurface, true);
        sub = text(c, 13, Ui.onSurfaceVar, false);
        sub.setLineSpacing(0, 1.15f);
        titles.addView(name);
        titles.addView(sub);
        head.addView(titles, new LayoutParams(0, -2, 1f));
        addView(head);

        // --- 数値タイル（列数は画面幅に応じて変える） ---
        stats = new LinearLayout(c);
        stats.setOrientation(VERTICAL);
        for (int i = 0; i < 6; i++) {
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(VERTICAL);
            cell.setBackground(Ui.round(Ui.surfaceHigh, 16));
            cell.setPadding(Ui.dp(12), Ui.dp(9), Ui.dp(8), Ui.dp(10));
            cell.addView(text(c, 11, Ui.onSurfaceVar, false, STAT_LABELS[i]));
            statValue[i] = text(c, 16, Ui.onSurface, true);
            statValue[i].setSingleLine(true);
            cell.addView(statValue[i]);
            statCell[i] = cell;
        }
        LayoutParams stlp = new LayoutParams(-1, -2);
        stlp.topMargin = Ui.dp(6);
        addView(stats, stlp);
        setColumns(3);

        // --- 立体の原子模型と電子軌道 ---
        LinearLayout visual = new LinearLayout(c);
        visual.setOrientation(VERTICAL);
        visual.setBackground(Ui.round(Ui.over(0xFF000000, Ui.surfaceHigh, 0.35f), 16));
        visual.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(12));
        LinearLayout pair = new LinearLayout(c);
        LinearLayout atomCol = new LinearLayout(c);
        atomCol.setOrientation(VERTICAL);
        atom = new AtomView(c);
        atomCol.addView(atom, new LayoutParams(-1, Ui.dp(136)));
        atomCap = text(c, 12, Ui.onSurfaceVar, false);
        atomCap.setGravity(Gravity.CENTER_HORIZONTAL);
        atomCol.addView(atomCap, new LayoutParams(-1, -2));
        pair.addView(atomCol, new LayoutParams(0, -2, 1f));
        orbitalCol = new LinearLayout(c);
        orbitalCol.setOrientation(VERTICAL);
        orbital = new OrbitalView(c);
        orbitalCol.addView(orbital, new LayoutParams(-1, Ui.dp(136)));
        orbitalCap = text(c, 12, Ui.onSurfaceVar, false);
        orbitalCap.setGravity(Gravity.CENTER_HORIZONTAL);
        orbitalCol.addView(orbitalCap, new LayoutParams(-1, -2));
        pair.addView(orbitalCol, new LayoutParams(0, -2, 1f));
        if (!OrbitalView.supported()) orbitalCol.setVisibility(GONE);
        visual.addView(pair);
        configText = text(c, 14, Ui.onSurface, false);
        configText.setGravity(Gravity.CENTER_HORIZONTAL);
        configText.setPadding(Ui.dp(4), Ui.dp(10), Ui.dp(4), 0);
        visual.addView(configText, new LayoutParams(-1, -2));
        LayoutParams vlp = new LayoutParams(-1, -2);
        vlp.topMargin = Ui.dp(8);
        addView(visual, vlp);

        // --- 炎色反応（該当する元素だけ） ---
        flameCard = new LinearLayout(c);
        flameCard.setGravity(Gravity.CENTER_VERTICAL);
        flameCard.setBackground(Ui.round(Ui.over(0xFF000000, Ui.surfaceHigh, 0.35f), 16));
        flameCard.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(14), Ui.dp(6));
        flame = new FlameView(c);
        flameCard.addView(flame, new LayoutParams(Ui.dp(84), Ui.dp(104)));
        flameText = text(c, 15, Ui.onSurface, false);
        flameText.setPadding(Ui.dp(12), 0, 0, 0);
        flameText.setLineSpacing(0, 1.2f);
        flameCard.addView(flameText, new LayoutParams(0, -2, 1f));
        LayoutParams flp = new LayoutParams(-1, -2);
        flp.topMargin = Ui.dp(8);
        addView(flameCard, flp);

        // --- 入試ポイント ---
        noteCard = new LinearLayout(c);
        noteCard.setOrientation(VERTICAL);
        noteCard.setBackground(Ui.round(Ui.over(Ui.AMBER, Ui.surface, 0.14f), 16));
        noteCard.setPadding(Ui.dp(14), Ui.dp(11), Ui.dp(14), Ui.dp(13));
        note = text(c, 15, Ui.onSurface, false);
        note.setLineSpacing(0, 1.25f);
        note.setPadding(0, Ui.dp(3), 0, 0);
        noteCard.addView(text(c, 12, Ui.AMBER, true, "入試ポイント"));
        noteCard.addView(note);
        LayoutParams nlp = new LayoutParams(-1, -2);
        nlp.topMargin = Ui.dp(8);
        addView(noteCard, nlp);
    }

    /** 数値タイルの列数。幅の狭い横画面の右ペインでは 2 列にする。 */
    void setColumns(int n) {
        if (n == columns) return;
        columns = n;
        for (int i = 0; i < stats.getChildCount(); i++) ((LinearLayout) stats.getChildAt(i)).removeAllViews();
        stats.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < 6; i++) {
            if (i % n == 0) {
                row = new LinearLayout(getContext());
                stats.addView(row);
            }
            LayoutParams lp = new LayoutParams(0, -2, 1f);
            lp.setMargins(i % n == 0 ? 0 : Ui.dp(8), Ui.dp(8), 0, 0);
            row.addView(statCell[i], lp);
        }
        if (bound != null) bind(bound);
    }

    static TextView text(Context c, float sp, int color, boolean bold) { return text(c, sp, color, bold, ""); }

    static TextView text(Context c, float sp, int color, boolean bold, CharSequence s) {
        TextView t = new TextView(c);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setText(s);
        return t;
    }

    void bind(Element e) {
        bound = e;
        int color = PeriodicTableView.categoryColor(e.category);
        tile.setBackground(Ui.round(color, 20));
        tile.setTextColor(Ui.isLight(color) ? 0xFF0B0E14 : 0xFFFFFFFF);
        tile.setText(e.symbol);

        SpannableStringBuilder nb = new SpannableStringBuilder(e.ja);
        int st = nb.length();
        nb.append("  ").append(e.en);
        nb.setSpan(new RelativeSizeSpan(0.6f), st, nb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        nb.setSpan(new ForegroundColorSpan(Ui.onSurfaceVar), st, nb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        name.setText(nb);
        sub.setText(columns == 2
                ? "原子番号 " + e.number + "\n第" + e.period + "周期 " + e.group + "族\n" + e.categoryShort()
                : "原子番号 " + e.number + " ・ 第" + e.period + "周期 " + e.group + "族\n" + e.categoryJa());

        int v = e.valenceElectrons();
        statValue[0].setText(e.mass);
        statValue[1].setText(v >= 0 ? v + " 個" : "—");
        statValue[2].setText(Double.isNaN(e.electronegativity) ? "—" : String.format(Locale.ROOT, "%.2f", e.electronegativity));
        statValue[3].setText(unit(e.ionization, " kJ/mol"));
        statValue[4].setText(unit(e.melt, " °C"));
        statValue[5].setText(unit(e.boil, " °C"));

        atom.bind(e);
        atomCap.setText(e.shellsText());
        int l = e.block();
        orbital.bind(l, color);
        orbitalCap.setText(e.orbitalName() + " 軌道（" + ORBITAL_NAMES[l] + "ブロック）");
        SpannableStringBuilder sb = new SpannableStringBuilder();
        label(sb, "電子配置  ");
        sb.append(e.configPretty());
        label(sb, "　常温  ");
        sb.append(e.phase);
        configText.setText(sb);

        Object[] f = Quiz.flame(e.number);
        flameCard.setVisibility(f == null ? GONE : VISIBLE);
        if (f != null) {
            flame.setFlames(new int[]{(int) f[2]}, null);
            SpannableStringBuilder fb = new SpannableStringBuilder();
            label(fb, "炎色反応\n");
            int s2 = fb.length();
            fb.append((String) f[1]);
            fb.setSpan(new ForegroundColorSpan((int) f[2]), s2, fb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            fb.setSpan(new RelativeSizeSpan(1.6f), s2, fb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            fb.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), s2, fb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            flameText.setText(fb);
        }

        noteCard.setVisibility(e.note.isEmpty() ? GONE : VISIBLE);
        note.setText(e.note);
    }

    private static CharSequence unit(int value, String unit) {
        if (value == Element.NONE) return "—";
        SpannableStringBuilder sb = new SpannableStringBuilder(String.valueOf(value).replace('-', '−'));
        int st = sb.length();
        sb.append(unit);
        sb.setSpan(new RelativeSizeSpan(0.62f), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(Ui.onSurfaceVar), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private static void label(SpannableStringBuilder sb, String s) {
        int st = sb.length();
        sb.append(s);
        sb.setSpan(new ForegroundColorSpan(Ui.onSurfaceVar), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(0.8f), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
