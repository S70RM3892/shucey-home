package com.shucey.home;

import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

/** 色・寸法・動き・触覚フィードバックの共通部品。 */
final class Ui {
    /** Material 3 の emphasized-decelerate。入ってくる動き用。 */
    static final TimeInterpolator EASE_OUT = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    static final TimeInterpolator EASE_IN_OUT = new PathInterpolator(0.2f, 0f, 0f, 1f);
    /** 少し行き過ぎて戻る。押下の戻りなど。 */
    static final TimeInterpolator SPRING = new PathInterpolator(0.3f, 1.6f, 0.4f, 1f);

    static int scrim = 0xDB0B0E14;       // 壁紙の上に重ねる幕
    static int surface = 0xFF161B22;
    static int surfaceHigh = 0xFF222832;
    static int onSurface = 0xFFE6EDF3;
    static int onSurfaceVar = 0xFF9DA7B3;
    static int accent = 0xFF7CC4FA;
    static int onAccent = 0xFF06283D;
    static final int AMBER = 0xFFFFD54F, GOOD = 0xFF66BB6A, BAD = 0xFFEF5350;

    private static float density = 1f;

    /** Android 12 以降は端末のテーマ色（Material You）に合わせる。 */
    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                surface = c.getColor(android.R.color.system_neutral1_900);
                surfaceHigh = c.getColor(android.R.color.system_neutral1_800);
                onSurface = c.getColor(android.R.color.system_neutral1_50);
                onSurfaceVar = c.getColor(android.R.color.system_neutral2_300);
                accent = c.getColor(android.R.color.system_accent1_200);
                onAccent = c.getColor(android.R.color.system_accent1_800);
            } catch (Exception ignored) {
                // 取得できなければ既定色のまま
            }
        }
    }

    static int dp(float v) { return Math.round(v * density); }
    static float dpf(float v) { return v * density; }

    static int alpha(int color, float a) {
        return (color & 0x00FFFFFF) | (Math.round(255 * a) << 24);
    }

    static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.argb(
                (int) (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    /** color を bg の上に不透明度 a で重ねた色。 */
    static int over(int color, int bg, float a) { return lerp(bg | 0xFF000000, color | 0xFF000000, a); }

    static boolean isLight(int c) {
        return (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) > 150;
    }

    static GradientDrawable round(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dpf(radiusDp));
        return d;
    }

    /** 角丸の背景 + リップル。 */
    static RippleDrawable ripple(int color, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(alpha(onSurface, 0.18f)),
                round(color, radiusDp), round(0xFFFFFFFF, radiusDp));
    }

    /** 押している間すこし縮み、離すと弾んで戻る。クリック処理は邪魔しない。 */
    @SuppressLint("ClickableViewAccessibility")
    static void pressable(View v) {
        v.setOnTouchListener((view, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).setInterpolator(EASE_OUT).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(SPRING).start();
                    break;
            }
            return false;
        });
    }

    // ---- 触覚フィードバック（端末の設定に従う） ----
    static void tick(View v) { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); }
    static void tap(View v) { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); }
    static void heavy(View v) { v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); }
    static void confirm(View v) {
        v.performHapticFeedback(Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
    }
    static void reject(View v) {
        v.performHapticFeedback(Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS);
    }
}
