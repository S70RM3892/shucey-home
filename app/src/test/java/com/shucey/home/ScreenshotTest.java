package com.shucey.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w411dp-h891dp-xxhdpi")
public class ScreenshotTest {

    @Test
    public void dataIsComplete() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        List<Element> els = Element.load(a);
        assertEquals(118, els.size());
        for (int i = 0; i < els.size(); i++) assertEquals(i + 1, els.get(i).number);
    }

    @Test
    public void renderAndTap() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        View root = a.getWindow().getDecorView();
        shot(root, "home.png");

        PeriodicTableView table = find(root, PeriodicTableView.class);
        assertTrue(table.getWidth() > 0);
        // 鉄 (8族, 第4周期) をタップ
        float cw = table.getWidth() / 18f;
        tap(table, 7.5f * cw, 0.0f);
        shot(root, "detail_fe.png");
    }

    private static void tap(PeriodicTableView t, float x, float yIgnored) {
        float cw = (t.getWidth()) / 18f;
        float ch = cw * 1.25f;
        float y = ch * 0.45f + 3.5f * ch;
        t.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0));
        t.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, x, y, 0));
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
        v.draw(new Canvas(b));
        File out = new File(System.getProperty("user.dir"), "build/shots/" + name);
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }
}
