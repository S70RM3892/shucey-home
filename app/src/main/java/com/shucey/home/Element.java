package com.shucey.home;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 1 元素分のデータ。assets/elements.json から読み込む。 */
final class Element {
    final int number;
    final String symbol, ja, en, mass, category, config, phase, note;
    final int x, y, period, group;
    final int[] shells;
    final double electronegativity; // NaN = データなし
    /** 第一イオン化エネルギー [kJ/mol]、融点・沸点 [°C]。データなしは MIN_VALUE。 */
    final int ionization, melt, boil;
    static final int NONE = Integer.MIN_VALUE;

    private Element(JSONObject o) throws Exception {
        number = o.getInt("n");
        symbol = o.getString("s");
        ja = o.getString("ja");
        en = o.getString("en");
        mass = o.getString("m");
        x = o.getInt("x");
        y = o.getInt("y");
        period = o.getInt("p");
        group = o.getInt("g");
        category = o.getString("c");
        config = o.getString("cfg");
        phase = o.getString("ph");
        note = o.getString("note");
        JSONArray sh = o.getJSONArray("sh");
        shells = new int[sh.length()];
        for (int i = 0; i < shells.length; i++) shells[i] = sh.getInt(i);
        electronegativity = o.isNull("en_p") ? Double.NaN : o.getDouble("en_p");
        ionization = o.isNull("ie") ? NONE : o.getInt("ie");
        melt = o.isNull("mp") ? NONE : o.getInt("mp");
        boil = o.isNull("bp") ? NONE : o.getInt("bp");
    }

    boolean isTransition() {
        return category.equals("transition") || category.equals("lanthanoid") || category.equals("actinoid");
    }

    /** 典型元素の価電子数。遷移元素は -1。 */
    int valenceElectrons() {
        if (isTransition()) return -1;
        if (group == 18) return 0;
        if (group <= 2) return group;
        return group - 10;
    }

    /** 「3d6 4s2」→「3d⁶ 4s²」。 */
    String configPretty() {
        final String sup = "⁰¹²³⁴⁵⁶⁷⁸⁹";
        StringBuilder sb = new StringBuilder();
        boolean afterOrbital = false;
        for (int i = 0; i < config.length(); i++) {
            char ch = config.charAt(i);
            if (afterOrbital && ch >= '0' && ch <= '9') {
                sb.append(sup.charAt(ch - '0'));
            } else {
                sb.append(ch);
                afterOrbital = ch == 's' || ch == 'p' || ch == 'd' || ch == 'f';
            }
        }
        return sb.toString();
    }

    String categoryShort() {
        switch (category) {
            case "alkali": return "アルカリ金属";
            case "alkaline_earth": return "アルカリ土類金属";
            case "transition": return "遷移元素";
            case "lanthanoid": return "ランタノイド";
            case "actinoid": return "アクチノイド";
            case "halogen": return "ハロゲン";
            case "noble": return "貴ガス";
            case "nonmetal": return "典型非金属";
            default: return "典型金属";
        }
    }

    String categoryJa() {
        switch (category) {
            case "alkali": return "アルカリ金属（典型・金属）";
            case "alkaline_earth": return "アルカリ土類金属（典型・金属）";
            case "transition": return "遷移元素（金属）";
            case "lanthanoid": return "ランタノイド（遷移元素・金属）";
            case "actinoid": return "アクチノイド（遷移元素・金属）";
            case "halogen": return number >= 117 ? "ハロゲン（典型）" : "ハロゲン（典型・非金属）";
            case "noble": return "貴ガス（典型・非金属）";
            case "nonmetal": return "典型元素・非金属";
            default: return "典型元素・金属";
        }
    }

    String shellsText() {
        String[] names = {"K", "L", "M", "N", "O", "P", "Q"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shells.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(names[i]).append(shells[i]);
        }
        return sb.toString();
    }

    static List<Element> load(Context ctx) {
        List<Element> list = new ArrayList<>();
        try (InputStream in = ctx.getAssets().open("elements.json")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) bos.write(buf, 0, r);
            JSONArray arr = new JSONArray(new String(bos.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) list.add(new Element(arr.getJSONObject(i)));
        } catch (Exception e) {
            throw new RuntimeException("elements.json の読み込みに失敗", e);
        }
        return list;
    }
}
