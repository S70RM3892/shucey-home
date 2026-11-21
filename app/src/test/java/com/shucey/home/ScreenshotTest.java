package com.shucey.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/** 実際の Activity を描画して画面を確認する（app/build/shots に PNG を出力）。 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w411dp-h891dp-xxhdpi")
public class ScreenshotTest {

    @Test
    public void dataIsComplete() {
        List<Element> els = Element.load(RuntimeEnvironment.getApplication());
        assertEquals(118, els.size());
        for (int i = 0; i < els.size(); i++) {
            Element e = els.get(i);
            assertEquals(i + 1, e.number);
            int sum = 0;
            for (int s : e.shells) sum += s;
            assertEquals("電子殻の合計 " + e.symbol, e.number, sum);
        }
        assertEquals("[Ar] 3d¹⁰ 4s¹", els.get(28).configPretty());
        assertEquals(1, els.get(10).valenceElectrons());
        assertEquals(0, els.get(17).valenceElectrons());
    }

    @Test
    public void quizWeightsAndScoring() {
        Context c = RuntimeEnvironment.getApplication();
        List<Element> els = Element.load(c);
        Quiz q = new Quiz(els, Dock.prefs(c), new Random(1));
        q.setPool(0);
        q.start();
        Element prev = null;
        for (int i = 0; i < 200; i++) {
            Quiz.Question qu = q.next();
            assertTrue(qu.answer.number <= 20);
            assertTrue("同じ元素が連続しない", qu.answer != prev);
            prev = qu.answer;
            assertTrue(q.answer(qu.answer));
        }
        assertEquals(200, q.streak);
        assertEquals(200, q.best());
        q.next();
        assertFalse(q.answer(els.get(117)));
        assertEquals(0, q.streak);
        q.setPool(Quiz.POOL_FLAME);
        assertEquals(7, q.poolElements().size());
    }

    @Test
    public void screens() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        String[] names = {"カメラ", "Chrome", "電卓", "マップ", "時計", "設定", "フォト", "YouTube", "連絡帳", "ファイル"};
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (int i = 0; i < names.length; i++) {
            ResolveInfo ri = new ResolveInfo();
            ri.activityInfo = new ActivityInfo();
            ri.activityInfo.packageName = "com.example.app" + i;
            ri.activityInfo.name = "Main";
            ri.activityInfo.applicationInfo = new ApplicationInfo();
            ri.activityInfo.applicationInfo.packageName = ri.activityInfo.packageName;
            ri.nonLocalizedLabel = names[i];
            shadowOf(app.getPackageManager()).addResolveInfoForIntent(launcher, ri);
        }

        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = a.getWindow().getDecorView();
        settle();
        shot(root, "1_home.png");

        PeriodicTableView table = find(root, PeriodicTableView.class);
        float cw = (table.getWidth() - Ui.dpf(8)) / 18f, ch = cw * 1.22f;
        float feX = Ui.dpf(4) + 7.5f * cw, feY = Ui.dpf(4) + ch * 0.5f + 3.5f * ch;

        // なぞっている最中（拡大表示）
        touch(table, MotionEvent.ACTION_DOWN, feX - cw, feY);
        touch(table, MotionEvent.ACTION_MOVE, feX, feY);
        settle();
        shot(root, "2_scrub.png");
        touch(table, MotionEvent.ACTION_UP, feX, feY);
        settle();
        assertEquals("Fe", table.getSelected().symbol);
        shot(root, "3_detail.png");

        click(root, "電気陰性度");
        settle();
        assertEquals(PeriodicTableView.MODE_EN, table.getMode());
        shot(root, "4_heatmap.png");

        click(root, "分類");
        a.onBackPressed();
        settle();
        click(root, "● ハロゲン");
        settle();
        shot(root, "5_filter.png");
        click(root, "● ハロゲン");

        click(root, "スタート");
        settle();
        shot(root, "6_quiz.png");
        // わざと外す（左上の H）。正解が H の場合は He を押す
        float hX = Ui.dpf(4) + 0.5f * cw, hY = Ui.dpf(4) + ch;
        touch(table, MotionEvent.ACTION_DOWN, hX, hY);
        touch(table, MotionEvent.ACTION_UP, hX, hY);
        settle();
        shot(root, "7_quiz_answer.png");
        assertNotNull(findText(root, "次へ"));
        click(root, "終了");
        settle();

        AppDrawer drawer = find(root, AppDrawer.class);
        drawer.reload();
        Thread.sleep(500);
        drawer.open(false);
        settle();
        assertTrue(drawer.isOpen());
        shot(root, "8_drawer.png");
        a.onBackPressed();
        settle();
        assertFalse(drawer.isOpen());
        assertEquals(View.INVISIBLE, drawer.getVisibility());
    }

    private static void settle() {
        for (int i = 0; i < 4; i++) shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
    }

    private static void touch(View v, int action, float x, float y) {
        long t = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(t, t, action, x, y, 0);
        v.dispatchTouchEvent(ev);
        ev.recycle();
    }

    private static void click(View root, String text) {
        TextView t = findText(root, text);
        assertNotNull("見つからない: " + text, t);
        assertTrue(t.performClick());
    }

    private static TextView findText(View v, String text) {
        if (v.getVisibility() != View.VISIBLE) return null;
        if (v instanceof TextView && ((TextView) v).getText().toString().equals(text)) return (TextView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView r = findText(g.getChildAt(i), text);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static <T> T find(View v, Class<T> cls) {
        if (cls.isInstance(v)) return cls.cast(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                T r = find(g.getChildAt(i), cls);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static void shot(View v, String name) throws Exception {
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF2B3A55); // 壁紙の代わり
        v.draw(c);
        File out = new File(System.getProperty("user.dir"), "build/shots/" + name);
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }
}
