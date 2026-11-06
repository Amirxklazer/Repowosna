package com.wearlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** One lightweight round app icon: SVG glyph bubble for Wear apps, cached bitmap for others. */
public class AppIcon extends View {
    private AppRepo.App app;
    private Bitmap bmp;
    private int size;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    public AppIcon(Context c) {
        super(c);
        ink.setColor(Theme.ON_PASTEL);
        ink.setStyle(Paint.Style.FILL);
    }

    public void bind(AppRepo.App a, int px) {
        app = a;
        size = px;
        bmp = null;
        if (!a.builtin) {
            bmp = AppRepo.cached(a, px);
            if (bmp == null) {
                AppRepo.fetch(getContext(), a, px, () -> {
                    if (app == a) { bmp = AppRepo.cached(a, px); invalidate(); }
                });
            }
        }
        requestLayout();
        invalidate();
    }

    public AppRepo.App app() { return app; }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(size, size); }

    @Override protected void onDraw(Canvas c) {
        if (app == null) return;
        float w = getWidth(), h = getHeight();
        if (app.builtin) {
            p.setColor(app.pastel);
            c.drawCircle(w / 2, h / 2, w / 2, p);
            float pad = w * 0.27f;
            box.set(pad, pad, w - pad, h - pad);
            Icons.draw(c, app.glyph, box, ink);
        } else if (bmp != null) {
            c.drawBitmap(bmp, 0, 0, null);
        } else {
            p.setColor(Theme.SURFACE_HI);
            c.drawCircle(w / 2, h / 2, w / 2, p);
        }
    }
}
