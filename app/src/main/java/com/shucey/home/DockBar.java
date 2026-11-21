package com.shucey.home;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

/** ドック。上にスワイプするとアプリ一覧を指に追従して引き出す。 */
public class DockBar extends LinearLayout {
    private final int slop;
    private AppDrawer drawer;
    private float downX, downY;
    private boolean dragging, steering;
    private long steerAt;
    private VelocityTracker tracker;

    public DockBar(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    void setDrawer(AppDrawer d) { drawer = d; }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getRawX();
                downY = ev.getRawY();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (startIfSwipe(ev)) return true;
                break;
        }
        return false;
    }

    private boolean startIfSwipe(MotionEvent ev) {
        float dy = downY - ev.getRawY(), dx = Math.abs(ev.getRawX() - downX);
        if (!dragging && drawer != null && dy > slop && dy > dx * 1.3f) {
            dragging = true;
            steering = false;
            downY = ev.getRawY();
            drawer.beginDrag();
            Ui.tick(this);
        }
        return dragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (tracker == null) tracker = VelocityTracker.obtain();
        tracker.addMovement(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true; // アイコンのない場所からでも引き出せるように
            case MotionEvent.ACTION_MOVE:
                if (startIfSwipe(ev)) {
                    float up = downY - ev.getRawY();
                    if (!steering && up > Ui.dpf(120)) {
                        // 十分に引き上げたら一覧を開ききり、以降は指をそのままアプリ選択に使う（一筆書き）
                        steering = true;
                        steerAt = android.os.SystemClock.uptimeMillis();
                        drawer.open(false);
                    }
                    if (steering) drawer.steer(ev.getRawX(), ev.getRawY(), false, false);
                    else drawer.dragBy(up);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    tracker.computeCurrentVelocity(1000);
                    float speed = (float) Math.hypot(tracker.getXVelocity(), tracker.getYVelocity());
                    if (steering) {
                        // 勢いよく払っただけなら開くだけ。狙って止めたときだけ起動する
                        boolean deliberate = android.os.SystemClock.uptimeMillis() - steerAt > 350 && speed < Ui.dpf(350);
                        drawer.steer(ev.getRawX(), ev.getRawY(), true,
                                deliberate && ev.getActionMasked() == MotionEvent.ACTION_UP);
                    } else {
                        drawer.settle(tracker.getYVelocity());
                    }
                }
                steering = false;
                tracker.recycle();
                tracker = null;
                dragging = false;
                return true;
        }
        return super.onTouchEvent(ev);
    }
}
