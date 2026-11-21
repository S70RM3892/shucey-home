package com.shucey.home;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RuntimeShader;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/** AGSL シェーダーがコンパイルでき、実際に絵が出ることを確認する。 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class ShaderTest {

    @Test
    public void shadersCompileAndDraw() throws Exception {
        int s = 240;
        android.media.ImageReader reader = android.media.ImageReader.newInstance(s * 6, s, android.graphics.PixelFormat.RGBA_8888, 1);
        android.graphics.HardwareRenderer hr = new android.graphics.HardwareRenderer();
        hr.setSurface(reader.getSurface());
        android.graphics.RenderNode node = new android.graphics.RenderNode("shaders");
        node.setPosition(0, 0, s * 6, s);
        Canvas c = node.beginRecording();
        c.drawColor(0xFF0E1218);
        Paint p = new Paint();

        // new RuntimeShader は構文エラーがあれば例外を投げる
        android.graphics.Shader flame = new RuntimeShader(Fx.FLAME);
        Fx.set(flame, "uRes", s, s);
        Fx.set(flame, "uTime", 1.3f);
        Fx.set(flame, "uSeed", 3f);
        Fx.color(flame, "uColor", 0xFF9CCC65);
        p.setShader(flame);
        c.drawRect(0, 0, s, s, p);

        for (int l = 0; l < 4; l++) {
            android.graphics.Shader orb = new RuntimeShader(Fx.ORBITAL);
            Fx.set(orb, "uRes", s, s);
            Fx.set(orb, "uL", l);
            Fx.set(orb, "uRot", 0.6f, 0.45f);
            Fx.color(orb, "uColor", 0xFF56BDF5);
            Fx.color(orb, "uColor2", 0xFFFFB454);
            p.setShader(orb);
            c.save();
            c.translate(s * (l + 1), 0);
            c.drawRect(0, 0, s, s, p);
            c.restore();
        }

        android.graphics.Shader field = new RuntimeShader(Fx.FIELD);
        Fx.set(field, "uRes", s, s);
        Fx.set(field, "uTime", 4f);
        Fx.set(field, "uTap", s / 2f, s / 2f);
        Fx.set(field, "uWave", 0.2f);
        Fx.set(field, "uEnergy", 1f);
        Fx.color(field, "uColor", 0xFF7CC4FA);
        p.setShader(field);
        c.save();
        c.translate(s * 5, 0);
        c.drawRect(0, 0, s, s, p);
        c.restore();

        node.endRecording();
        hr.setContentRoot(node);
        hr.createRenderRequest().syncAndDraw();
        android.media.Image img = reader.acquireNextImage();
        android.media.Image.Plane plane = img.getPlanes()[0];
        Bitmap b = Bitmap.createBitmap(plane.getRowStride() / plane.getPixelStride(), s, Bitmap.Config.ARGB_8888);
        b.copyPixelsFromBuffer(plane.getBuffer());
        img.close();
        File out = new File(System.getProperty("user.dir"), "build/shots/shaders.png");
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertNotNull(Fx.shader(Fx.FLAME));
        // 炎の根元は明るい（シェーダーが実際に描いている）
        int px = b.getPixel(s / 2, s - 30);
        assertTrue("炎が描かれていない", ((px >> 8) & 0xFF) > 120);
    }
}
