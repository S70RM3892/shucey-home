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
        String[] names = new String[46];
        String[] baseNames = {"カメラ", "Chrome", "電卓", "マップ", "時計", "設定", "フォト", "YouTube", "連絡帳", "ファイル"};
        for (int i = 0; i < names.length; i++) names[i] = i < baseNames.length ? baseNames[i] : "アプリ" + i;
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
        List<Element> els = Element.load(a);
        float cw = (table.getWidth() - Ui.dpf(8)) / 18f;
        float feX = table.centerOf(els.get(25))[0], feY = table.centerOf(els.get(25))[1];

        // なぞっている最中（拡大表示）
        touch(table, MotionEvent.ACTION_DOWN, feX - cw, feY);
        touch(table, MotionEvent.ACTION_MOVE, feX, feY);
        settle();
        shot(root, "2_scrub.png");
        touch(table, MotionEvent.ACTION_UP, feX, feY);
        settle();
        assertEquals("Fe", table.getSelected().symbol);
        shot(root, "3_detail.png");
        float[] na = table.centerOf(els.get(10));
        touch(table, MotionEvent.ACTION_DOWN, na[0], na[1]);
        touch(table, MotionEvent.ACTION_UP, na[0], na[1]);
        settle();
        android.widget.ScrollView sv = find(root, android.widget.ScrollView.class);
        sv.scrollTo(0, sv.getChildAt(0).getHeight());
        assertTrue("詳細が下までスクロールできる", sv.getScrollY() > 0);
        shot(find(root, ElementPanel.class), "3b_panel_na.png");
        touch(table, MotionEvent.ACTION_DOWN, feX, feY);
        touch(table, MotionEvent.ACTION_UP, feX, feY);
        settle();

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
        float hX = table.centerOf(els.get(0))[0], hY = table.centerOf(els.get(0))[1];
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
        AppField field = find(root, AppField.class);
        assertEquals(46, field.shownCount());
        shot(root, "8_drawer.png");

        // なぞっている最中（魚眼レンズ）
        float fx = field.getWidth() * 0.42f, fy = field.getHeight() - Ui.dpf(250);
        touch(field, MotionEvent.ACTION_DOWN, fx - 60, fy + 40);
        touch(field, MotionEvent.ACTION_MOVE, fx, fy);
        for (int i = 0; i < 40; i++) { field.invalidate(); shotFrame(field); } // 追従アニメーションを進める
        shot(root, "9_drawer_fisheye.png");
        touch(field, MotionEvent.ACTION_UP, fx, fy);
        settle();
        Intent started = shadowOf(a).getNextStartedActivity();
        assertNotNull("離したアプリが起動する", started);
        assertTrue(started.getComponent().getPackageName().startsWith("com.example.app"));
        assertFalse("起動後は一覧が閉じる", drawer.isOpen());

        // 検索: 一致したものだけが並び直す
        drawer.open(false);
        settle();
        find(drawer, android.widget.EditText.class).setText("アプリ1");
        for (int i = 0; i < 40; i++) { field.invalidate(); shotFrame(field); }
        assertEquals(10, field.shownCount()); // アプリ10〜19
        shot(root, "10_drawer_search.png");
        a.onBackPressed();
        settle();
        assertFalse(drawer.isOpen());
        assertEquals(View.INVISIBLE, drawer.getVisibility());
    }

    /** 1 フレームぶん描画して、描画に連動するアニメーションを進める。 */
    private static void shotFrame(View v) {
        android.graphics.RenderNode node = new android.graphics.RenderNode("f");
        node.setPosition(0, 0, v.getWidth(), v.getHeight());
        v.draw(node.beginRecording());
        node.endRecording();
    }

    @Test
    @Config(sdk = 34, qualifiers = "w891dp-h411dp-land-xxhdpi")
    public void landscape() throws Exception {
        org.robolectric.android.controller.ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        View root = a.getWindow().getDecorView();
        settle();
        shot(root, "L1_home.png");
        PeriodicTableView table = find(root, PeriodicTableView.class);
        assertTrue("横画面で表が左側に収まる", table.getWidth() < root.getWidth() * 0.7f);
        float[] ba = table.centerOf(Element.load(a).get(55)); // Ba
        touch(table, MotionEvent.ACTION_DOWN, ba[0], ba[1]);
        touch(table, MotionEvent.ACTION_UP, ba[0], ba[1]);
        settle();
        assertEquals("Ba", table.getSelected().symbol);
        shot(root, "L2_detail.png");

        // 横画面のアプリ一覧（高さが足りず上に続きがある → 端で自動スクロール）
        AppDrawer drawer = find(root, AppDrawer.class);
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (int i = 0; i < 60; i++) {
            ResolveInfo ri = new ResolveInfo();
            ri.activityInfo = new ActivityInfo();
            ri.activityInfo.packageName = "com.example.land" + i;
            ri.activityInfo.name = "Main";
            ri.activityInfo.applicationInfo = new ApplicationInfo();
            ri.activityInfo.applicationInfo.packageName = ri.activityInfo.packageName;
            ri.nonLocalizedLabel = "アプリ" + i;
            shadowOf(a.getPackageManager()).addResolveInfoForIntent(launcher, ri);
        }
        drawer.reload();
        Thread.sleep(500);
        drawer.open(false);
        settle();
        AppField field = find(root, AppField.class);
        assertEquals(60, field.shownCount());
        assertTrue("入りきらない分はスクロールで出せる", field.canScroll());
        shot(root, "L4_drawer.png");
        touch(field, MotionEvent.ACTION_DOWN, field.getWidth() / 2f, field.getHeight() / 2f);
        touch(field, MotionEvent.ACTION_MOVE, field.getWidth() / 2f, Ui.dpf(10));
        for (int i = 0; i < 60; i++) { field.invalidate(); shotFrame(field); }
        shot(root, "L5_drawer_scrolled.png");
        touch(field, MotionEvent.ACTION_CANCEL, 0, 0);
        a.onBackPressed();
        settle();

        // 回転しても状態を保ったまま並べ替わる
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-port-xxhdpi");
        c.configurationChange(a.getResources().getConfiguration());
        settle();
        assertEquals("Ba", table.getSelected().symbol);
        assertTrue(table.getWidth() > root.getWidth() * 0.9f);
        shot(root, "L3_rotated_back.png");
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

    /** 実機と同じハードウェア描画経路で撮る（GPU シェーダーも写る）。 */
    static void shot(View v, String name) throws Exception {
        int w = v.getWidth(), h = v.getHeight();
        android.media.ImageReader reader = android.media.ImageReader.newInstance(w, h, android.graphics.PixelFormat.RGBA_8888, 1);
        android.graphics.HardwareRenderer hr = new android.graphics.HardwareRenderer();
        hr.setSurface(reader.getSurface());
        android.graphics.RenderNode node = new android.graphics.RenderNode("shot");
        node.setPosition(0, 0, w, h);
        Canvas c = node.beginRecording();
        c.drawColor(0xFF2B3A55); // 壁紙の代わり
        v.draw(c);
        node.endRecording();
        hr.setContentRoot(node);
        hr.createRenderRequest().syncAndDraw();
        android.media.Image img = reader.acquireNextImage();
        android.media.Image.Plane plane = img.getPlanes()[0];
        Bitmap full = Bitmap.createBitmap(plane.getRowStride() / plane.getPixelStride(), h, Bitmap.Config.ARGB_8888);
        full.copyPixelsFromBuffer(plane.getBuffer());
        img.close();
        Bitmap b = Bitmap.createBitmap(full, 0, 0, w, h);
        File out = new File(System.getProperty("user.dir"), "build/shots/" + name);
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }
}
