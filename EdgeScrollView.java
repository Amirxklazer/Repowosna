package com.wearlauncher;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.ScrollView;

/** ScrollView that reports a downward pull while already at the top (used to dismiss panels). */
public class EdgeScrollView extends ScrollView {
    public interface PullListener {
        void onPull(float dy);       // live distance while pulling
        void onRelease(float dy);    // final distance
    }

    private PullListener listener;
    private float startY;
    private boolean pulling;

    public EdgeScrollView(Context c) {
        super(c);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(false);
    }

    public void setPullListener(PullListener l) { listener = l; }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (listener != null) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startY = e.getRawY();
                pulling = false;
            } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE
                    && getScrollY() == 0 && e.getRawY() - startY > Theme.dp(getContext(), 14)) {
                pulling = true;
                return true;
            }
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (listener != null) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startY = e.getRawY();
            }
            if (pulling || (e.getActionMasked() == MotionEvent.ACTION_MOVE
                    && getScrollY() == 0 && e.getRawY() - startY > Theme.dp(getContext(), 14))) {
                pulling = true;
                float dy = Math.max(0, e.getRawY() - startY);
                if (e.getActionMasked() == MotionEvent.ACTION_UP
                        || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    pulling = false;
                    listener.onRelease(dy);
                } else {
                    listener.onPull(dy);
                }
                return true;
            }
        }
        return super.onTouchEvent(e);
    }
}
