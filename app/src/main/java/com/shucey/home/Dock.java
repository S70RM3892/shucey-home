package com.shucey.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** ホーム下部に並べるお気に入りアプリ（最大 5 個）。 */
final class Dock {
    static final int MAX = 5;
    private static final String KEY = "dock";

    static List<ComponentName> get(Context c) {
        List<ComponentName> out = new ArrayList<>();
        String s = prefs(c).getString(KEY, "");
        for (String part : s.split(";")) {
            ComponentName cn = ComponentName.unflattenFromString(part);
            if (cn != null) out.add(cn);
        }
        return out;
    }

    static boolean contains(Context c, ComponentName cn) { return get(c).contains(cn); }

    /** @return 追加できたら true（満杯なら false） */
    static boolean add(Context c, ComponentName cn) {
        List<ComponentName> l = get(c);
        if (l.contains(cn)) return true;
        if (l.size() >= MAX) return false;
        l.add(cn);
        save(c, l);
        return true;
    }

    static void remove(Context c, ComponentName cn) {
        List<ComponentName> l = get(c);
        l.remove(cn);
        save(c, l);
    }

    private static void save(Context c, List<ComponentName> l) {
        StringBuilder sb = new StringBuilder();
        for (ComponentName cn : l) {
            if (sb.length() > 0) sb.append(';');
            sb.append(cn.flattenToString());
        }
        prefs(c).edit().putString(KEY, sb.toString()).apply();
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("home", Context.MODE_PRIVATE);
    }
}
