package com.wearlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Circular progress ring, drawn as vector arcs. */
public class RingView extends View {
    private float frac;
    private int color = Theme.MINT;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public RingView(Context c) { super(c); }

    public void setFrac(float f) { frac = Math.max(0f, Math.min(1f, f)); invalidate(); }
    public void setColor(int c) { color = c; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        float s = Theme.dpf(getContext(), 14);
        r.set(s / 2, s / 2, getWidth() - s / 2, getHeight() - s / 2);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFF26262C);
        c.drawArc(r, -90, 360, false, p);
        p.setColor(color);
        if (frac > 0f) c.drawArc(r, -90, 360f * frac, false, p);
    }
}
