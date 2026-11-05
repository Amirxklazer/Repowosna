package com.wearlauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Material-You style switch: pill track, round thumb that grows when on. */
public class WearSwitch extends View {
    private boolean on;
    private float t;                       // 0 = off, 1 = on
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int onColor = Theme.BLUE;

    public WearSwitch(Context c, boolean on, int onColor) {
        super(c);
        this.on = on;
        this.t = on ? 1f : 0f;
        this.onColor = onColor;
    }

    public boolean isOn() { return on; }

    public void setOn(boolean v, boolean animate) {
        if (v == on) return;
        on = v;
        if (!animate) { t = on ? 1f : 0f; invalidate(); return; }
        ValueAnimator a = ValueAnimator.ofFloat(t, on ? 1f : 0f);
        a.setDuration(200);
        a.addUpdateListener(x -> { t = (float) x.getAnimatedValue(); invalidate(); });
        a.start();
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(Theme.dp(getContext(), 52), Theme.dp(getContext(), 32));
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        r.set(0, 0, w, h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(blend(0xFF3A3A42, onColor, t));
        c.drawRoundRect(r, h / 2, h / 2, p);
        float rad = h * (0.30f + 0.17f * t);
        float cx = h / 2f + (w - h) * t;
        p.setColor(blend(0xFF9A96A0, 0xFF1B1B1F, t));
        c.drawCircle(cx, h / 2f, rad, p);
    }

    private static int blend(int a, int b, float f) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * f) << 16) | ((int) (ag + (bg - ag) * f) << 8) | (int) (ab + (bb - ab) * f);
    }
}
