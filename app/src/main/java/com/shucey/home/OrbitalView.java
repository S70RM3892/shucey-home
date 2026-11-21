package com.shucey.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/**
 * 電子軌道（s / p / d / f）の電子雲を立体的に描く。色の違いは波動関数の位相（＋／−）。
 * ドラッグで回せる。
 */
public class OrbitalView extends View {
    private final Shader shader = Fx.shader(Fx.ORBITAL);
    private final Paint paint = new Paint();
    private int l, color = 0xFF56BDF5;
    private float yaw = 0.6f, pitch = 0.45f, lastX, lastY, lastT;

    public OrbitalView(Context c) {
        super(c);
        if (shader != null) paint.setShader(shader);
    }

    static boolean supported() { return Fx.SHADERS; }

    void bind(int l, int color) {
        this.l = l;
        this.color = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (shader == null || !canvas.isHardwareAccelerated()) return;
        float now = Fx.time();
        if (Fx.awake()) yaw += Math.min(0.05f, now - lastT) * 0.55f; // ゆっくり自転
        lastT = now;
        Fx.set(shader, "uRes", getWidth(), getHeight());
        Fx.set(shader, "uL", l);
        Fx.set(shader, "uRot", yaw, pitch);
        Fx.color(shader, "uColor", color);
        Fx.color(shader, "uColor2", 0xFFFFA24D);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        if (Fx.awake()) postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                lastX = ev.getX();
                lastY = ev.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                yaw += (ev.getX() - lastX) / Ui.dpf(60);
                pitch = Math.max(-1.5f, Math.min(1.5f, pitch + (ev.getY() - lastY) / Ui.dpf(60)));
                lastX = ev.getX();
                lastY = ev.getY();
                Fx.wake();
                invalidate();
                return true;
        }
        return super.onTouchEvent(ev);
    }
}
