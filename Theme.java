package com.wearlauncher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

/** Design tokens: Pixel-Watch style dark Material You palette. */
public final class Theme {
    private Theme() {}

    public static final int BG = 0xFF000000;
    public static final int SURFACE = 0xFF1B1B1F;        // pill / card
    public static final int SURFACE_HI = 0xFF2A2A30;     // pressed / raised
    public static final int TEXT = 0xFFE6E1E9;
    public static final int TEXT_DIM = 0xFFA9A5B0;
    public static final int ON_PASTEL = 0xFF1B1B1F;

    // Pastel accents (circle bubbles in the Wear OS app grid)
    public static final int BLUE = 0xFFA8C8FF;
    public static final int MINT = 0xFF7FE3C4;
    public static final int LILAC = 0xFFCDB8F5;
    public static final int PEACH = 0xFFFFB59E;
    public static final int CREAM = 0xFFF2E3C8;
    public static final int ROSE = 0xFFF5B5D0;
    public static final int LIME = 0xFFD2EC8F;

    public static final int[] PASTELS = {BLUE, MINT, LILAC, PEACH, CREAM, ROSE, LIME};

    public static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    public static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    public static final Typeface CONDENSED = Typeface.create("sans-serif-condensed", Typeface.BOLD);

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static float dpf(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /** Subtle press feedback: scale down + dim while touched. */
    public static void pressable(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                    break;
            }
            return false;
        });
    }

    public static int alpha(int color, int a) {
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}
