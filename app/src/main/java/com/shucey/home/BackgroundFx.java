package com.shucey.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * 背景のエネルギー場。選んだ元素の色を帯びた霧がゆっくり流れ、タップ位置から衝撃波が広がる。
 * 触った直後だけ動き、放置すると静止する。
 */
public class BackgroundFx extends View {
    private final Shader shader = Fx.shader(Fx.FIELD);
    private final Paint paint = new Paint();
    private int color = Ui.accent, fromColor = Ui.accent;
    private long colorAt, waveAt = -10000;
    private float tapX, tapY;
    private static final long WAVE_MS = 1100, COLOR_MS = 500;

    public BackgroundFx(Context c) {
        super(c);
        if (shader != null) paint.setShader(shader);
    }

    void setColor(int c) {
        if (c == color) return;
        fromColor = current();
        color = c;
        colorAt = SystemClock.uptimeMillis();
        invalidate();
    }

    private int current() {
        float t = Math.min(1f, (SystemClock.uptimeMillis() - colorAt) / (float) COLOR_MS);
        return Ui.lerp(fromColor, color, t);
    }

    /** この View 内の座標から衝撃波を出す。 */
    void pulse(float x, float y) {
        tapX = x;
        tapY = y;
        waveAt = SystemClock.uptimeMillis();
        Fx.wake();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (shader == null || !canvas.isHardwareAccelerated()) return;
        long now = SystemClock.uptimeMillis();
        float wave = (now - waveAt) / (float) WAVE_MS;
        boolean waving = wave < 1f;
        // 起きている間のエネルギー（終わり際 2 秒でなだらかに落とす）
        Fx.set(shader, "uRes", getWidth(), getHeight());
        Fx.set(shader, "uTime", Fx.time());
        Fx.set(shader, "uTap", tapX, tapY);
        Fx.set(shader, "uWave", waving ? wave : -1f);
        Fx.set(shader, "uEnergy", Fx.awake() ? 1f : 0.35f);
        Fx.color(shader, "uColor", current());
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        if (waving || Fx.awake() || now - colorAt < COLOR_MS) postInvalidateOnAnimation();
    }
}
