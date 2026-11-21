package com.shucey.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** インストール済みアプリの一覧（ドロワー）。 */
public class AppsActivity extends Activity {

    static final class App {
        final ComponentName cn;
        final String label;
        Drawable icon;
        App(ComponentName cn, String label) { this.cn = cn; this.label = label; }
    }

    private final List<App> all = new ArrayList<>();
    private final List<App> shown = new ArrayList<>();
    private Adapter adapter;
    private float dp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0E1116);
        int p = (int) (12 * dp);
        root.setPadding(p, p, p, 0);

        EditText search = new EditText(this);
        search.setHint("アプリを検索");
        search.setSingleLine(true);
        search.setTextColor(0xFFE6EDF3);
        search.setHintTextColor(0xFF6E7681);
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));

        GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing((int) (12 * dp));
        grid.setPadding(0, (int) (8 * dp), 0, (int) (8 * dp));
        grid.setClipToPadding(false);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, pos, id) -> launch(shown.get(pos).cn));
        grid.setOnItemLongClickListener((parent, view, pos, id) -> {
            showMenu(shown.get(pos));
            return true;
        });
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            public void onTextChanged(CharSequence s, int a, int c, int d) {}
            public void afterTextChanged(Editable s) { filter(s.toString()); }
        });

        new Thread(this::loadApps).start();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<App> list = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            App a = new App(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                    String.valueOf(ri.loadLabel(pm)));
            a.icon = ri.loadIcon(pm);
            list.add(a);
        }
        Collator col = Collator.getInstance(Locale.JAPANESE);
        list.sort((x, y) -> col.compare(x.label, y.label));
        runOnUiThread(() -> {
            all.clear();
            all.addAll(list);
            filter("");
        });
    }

    private void filter(String q) {
        shown.clear();
        String lq = q.toLowerCase(Locale.ROOT).trim();
        for (App a : all) {
            if (TextUtils.isEmpty(lq) || a.label.toLowerCase(Locale.ROOT).contains(lq)) shown.add(a);
        }
        adapter.notifyDataSetChanged();
    }

    private void launch(ComponentName cn) {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "起動できない: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showMenu(App a) {
        boolean inDock = Dock.contains(this, a.cn);
        String[] items = {inDock ? "ドックから外す" : "ドックに追加", "アプリ情報"};
        new AlertDialog.Builder(this)
                .setTitle(a.label)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        if (inDock) Dock.remove(this, a.cn);
                        else if (!Dock.add(this, a.cn))
                            Toast.makeText(this, "ドックは最大" + Dock.MAX + "個", Toast.LENGTH_SHORT).show();
                    } else {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", a.cn.getPackageName(), null)));
                    }
                })
                .show();
    }

    private class Adapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int i) { return shown.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View v, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;
            if (v == null) {
                cell = new LinearLayout(AppsActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                icon = new ImageView(AppsActivity.this);
                int s = (int) (48 * dp);
                cell.addView(icon, new LinearLayout.LayoutParams(s, s));
                label = new TextView(AppsActivity.this);
                label.setTextColor(0xFFE6EDF3);
                label.setTextSize(11);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(2);
                label.setEllipsize(TextUtils.TruncateAt.END);
                cell.addView(label, new LinearLayout.LayoutParams(-1, -2));
                cell.setTag(new Object[]{icon, label});
            } else {
                cell = (LinearLayout) v;
            }
            Object[] t = (Object[]) cell.getTag();
            icon = (ImageView) t[0];
            label = (TextView) t[1];
            App a = shown.get(pos);
            icon.setImageDrawable(a.icon);
            label.setText(a.label);
            return cell;
        }
    }
}
