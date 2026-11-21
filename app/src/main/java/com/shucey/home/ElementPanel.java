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
    private final TextView tile, name, sub, shellsText, note, noteTitle;
    private final TextView[] statValue = new TextView[6];
    private final ShellView shell;
    private final LinearLayout noteCard;
    private static final String[] STAT_LABELS = {"原子量", "価電子", "電気陰性度", "第一イオン化E", "融点", "沸点"};

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

        // --- 数値タイル 3×2 ---
        for (int rowI = 0; rowI < 2; rowI++) {
            LinearLayout row = new LinearLayout(c);
            for (int col = 0; col < 3; col++) {
                int i = rowI * 3 + col;
                LinearLayout cell = new LinearLayout(c);
                cell.setOrientation(VERTICAL);
                cell.setBackground(Ui.round(Ui.surfaceHigh, 16));
                cell.setPadding(Ui.dp(12), Ui.dp(9), Ui.dp(8), Ui.dp(10));
                cell.addView(text(c, 11, Ui.onSurfaceVar, false, STAT_LABELS[i]));
                statValue[i] = text(c, 16, Ui.onSurface, true);
                statValue[i].setSingleLine(true);
                cell.addView(statValue[i]);
                LayoutParams lp = new LayoutParams(0, -2, 1f);
                lp.setMargins(col == 0 ? 0 : Ui.dp(8), Ui.dp(rowI == 0 ? 14 : 8), 0, 0);
                row.addView(cell, lp);
            }
            addView(row);
        }

        // --- 電子殻 ---
        LinearLayout shellCard = new LinearLayout(c);
        shellCard.setGravity(Gravity.CENTER_VERTICAL);
        shellCard.setBackground(Ui.round(Ui.surfaceHigh, 16));
        shellCard.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        shell = new ShellView(c);
        shellCard.addView(shell, new LayoutParams(Ui.dp(104), Ui.dp(104)));
        shellsText = text(c, 14, Ui.onSurface, false);
        shellsText.setLineSpacing(0, 1.2f);
        shellsText.setPadding(Ui.dp(12), 0, 0, 0);
        shellCard.addView(shellsText, new LayoutParams(0, -2, 1f));
        LayoutParams slp = new LayoutParams(-1, -2);
        slp.topMargin = Ui.dp(8);
        addView(shellCard, slp);

        // --- 入試ポイント ---
        noteCard = new LinearLayout(c);
        noteCard.setOrientation(VERTICAL);
        noteCard.setBackground(Ui.round(Ui.over(Ui.AMBER, Ui.surface, 0.14f), 16));
        noteCard.setPadding(Ui.dp(14), Ui.dp(11), Ui.dp(14), Ui.dp(13));
        noteTitle = text(c, 12, Ui.AMBER, true, "入試ポイント");
        note = text(c, 15, Ui.onSurface, false);
        note.setLineSpacing(0, 1.25f);
        note.setPadding(0, Ui.dp(3), 0, 0);
        noteCard.addView(noteTitle);
        noteCard.addView(note);
        LayoutParams nlp = new LayoutParams(-1, -2);
        nlp.topMargin = Ui.dp(8);
        addView(noteCard, nlp);
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
        sub.setText("原子番号 " + e.number + " ・ 第" + e.period + "周期 " + e.group + "族\n" + e.categoryJa());

        int v = e.valenceElectrons();
        statValue[0].setText(e.mass);
        statValue[1].setText(v >= 0 ? v + " 個" : "—");
        statValue[2].setText(Double.isNaN(e.electronegativity) ? "—" : String.format(Locale.ROOT, "%.2f", e.electronegativity));
        statValue[3].setText(unit(e.ionization, " kJ/mol"));
        statValue[4].setText(unit(e.melt, " °C"));
        statValue[5].setText(unit(e.boil, " °C"));

        shell.bind(e);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        label(sb, "電子殻\n");
        sb.append(e.shellsText()).append("\n");
        label(sb, "電子配置\n");
        sb.append(e.configPretty());
        label(sb, "\n常温 ");
        sb.append(e.phase);
        shellsText.setText(sb);

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
