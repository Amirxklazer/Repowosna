package com.wearlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Rounded horizontal progress bar (brightness, battery...). */
public class Bar extends View {
    private float value;      // 0..1
    private int color = Theme.LIME;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public Bar(Context c) { super(c); }

    public void setValue(float v) { value = Math.max(0f, Math.min(1f, v)); invalidate(); }
    public void setColor(int c) { color = c; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF2A2A30);
        r.set(0, 0, w, h);
        c.drawRoundRect(r, h / 2, h / 2, p);
        p.setColor(color);
        r.set(0, 0, Math.max(h, w * value), h);
        c.drawRoundRect(r, h / 2, h / 2, p);
    }
}
