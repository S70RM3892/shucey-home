package com.shucey.home;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 位置当てクイズの出題ロジック。ミスした元素ほど出やすい。 */
final class Quiz {
    static final String[] POOLS = {"1〜20番", "入試頻出", "全118", "炎色反応"};
    static final int POOL_FLAME = 3;
    /** 入試頻出: 1〜36 に加えて無機化学で頻出の重元素 */
    private static final int[] FREQUENT_EXTRA = {47, 50, 53, 55, 56, 78, 79, 80, 82};
    /** 炎色反応（原子番号, 色） */
    static final Object[][] FLAME = {
            {3, "赤", 0xFFE53935}, {11, "黄", 0xFFFDD835}, {19, "赤紫", 0xFFC063C8}, {20, "橙赤", 0xFFFF7043},
            {38, "紅", 0xFFD81B60}, {56, "黄緑", 0xFF9CCC65}, {29, "青緑", 0xFF26C6A6}};

    static final class Question {
        final Element answer;
        final String lead, key, tail; // 例: "", "マンガン", " はどこ？"
        Question(Element a, String lead, String key, String tail) {
            answer = a; this.lead = lead; this.key = key; this.tail = tail;
        }
    }

    private final List<Element> elements;
    private final SharedPreferences prefs;
    private final Random random;
    int pool;
    int streak, asked, correct;
    Question current;

    Quiz(List<Element> elements, SharedPreferences prefs, Random random) {
        this.elements = elements;
        this.prefs = prefs;
        this.random = random;
        pool = Math.min(prefs.getInt("pool", 1), POOLS.length - 1);
    }

    void setPool(int p) {
        pool = p;
        prefs.edit().putInt("pool", p).apply();
    }

    int best() { return best(pool); }
    int best(int p) { return prefs.getInt("best_" + p, 0); }

    void start() { streak = asked = correct = 0; current = null; }

    List<Element> poolElements() {
        List<Element> l = new ArrayList<>();
        for (Element e : elements) {
            boolean in = false;
            if (pool == 0) in = e.number <= 20;
            else if (pool == 1) {
                in = e.number <= 36;
                for (int n : FREQUENT_EXTRA) if (n == e.number) in = true;
            } else if (pool == 2) in = true;
            else for (Object[] f : FLAME) if ((int) f[0] == e.number) in = true;
            if (in) l.add(e);
        }
        return l;
    }

    private int misses(Element e) { return prefs.getInt("miss_" + e.number, 0); }

    Question next() {
        List<Element> l = poolElements();
        Element prev = current == null ? null : current.answer;
        int total = 0;
        int[] w = new int[l.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = l.get(i) == prev ? 0 : 1 + 3 * Math.min(misses(l.get(i)), 5);
            total += w[i];
        }
        Element target = l.get(0);
        int r = random.nextInt(total);
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r < 0) { target = l.get(i); break; }
        }
        if (pool == POOL_FLAME) {
            String color = "";
            for (Object[] f : FLAME) if ((int) f[0] == target.number) color = (String) f[1];
            current = new Question(target, "炎色反応が ", color, " の元素は？");
        } else if (random.nextInt(10) < 3) {
            current = new Question(target, "", target.symbol, " はどこ？");
        } else {
            current = new Question(target, "", target.ja, " はどこ？");
        }
        return current;
    }

    /** @return 正解なら true */
    boolean answer(Element tapped) {
        Element target = current.answer;
        boolean ok = tapped == target;
        SharedPreferences.Editor ed = prefs.edit();
        asked++;
        if (ok) {
            correct++;
            streak++;
            if (streak > best()) ed.putInt("best_" + pool, streak);
            ed.putInt("miss_" + target.number, Math.max(0, misses(target) - 1));
        } else {
            streak = 0;
            ed.putInt("miss_" + target.number, misses(target) + 1);
        }
        ed.apply();
        return ok;
    }
}
