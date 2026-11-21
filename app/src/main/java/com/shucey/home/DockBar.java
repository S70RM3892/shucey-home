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
    private boolean dragging;
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
                if (startIfSwipe(ev)) drawer.dragBy(downY - ev.getRawY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    tracker.computeCurrentVelocity(1000);
                    drawer.settle(tracker.getYVelocity());
                }
                tracker.recycle();
                tracker = null;
                dragging = false;
                return true;
        }
        return super.onTouchEvent(ev);
    }
}
