package com.wearlauncher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small reusable building blocks that give everything the Wear OS 4 / Pixel Watch look. */
public final class UiKit {
    private UiKit() {}

    public static TextView text(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Pastel circle with a dark glyph, like the Wear OS app grid. */
    public static FrameLayout bubble(Context c, String icon, int pastel, int sizeDp) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(Theme.circle(pastel));
        int px = Theme.dp(c, sizeDp);
        f.setLayoutParams(new LinearLayout.LayoutParams(px, px));
        Icons.IconView iv = new Icons.IconView(c, icon, Theme.ON_PASTEL);
        iv.setInsetFraction(0.27f);
        f.addView(iv, new FrameLayout.LayoutParams(px, px));
        return f;
    }

    /** Wide rounded row: [icon bubble] title / subtitle. */
    public static LinearLayout pill(Context c, String icon, int pastel, String title, String sub) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = Theme.dp(c, 14);
        row.setPadding(p, Theme.dp(c, 12), p, Theme.dp(c, 12));
        row.setBackground(Theme.round(Theme.SURFACE, Theme.dpf(c, 28)));
        if (icon != null) {
            FrameLayout b = bubble(c, icon, pastel, 40);
            ((LinearLayout.LayoutParams) b.getLayoutParams()).rightMargin = Theme.dp(c, 14);
            row.addView(b);
        }
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(c, title, 17, Theme.TEXT, Theme.MEDIUM);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(t);
        if (sub != null) {
            TextView s = text(c, sub, 13, Theme.TEXT_DIM, Theme.REGULAR);
            s.setSingleLine(true);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(s, withTop(c, 2));
        }
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Theme.pressable(row);
        return row;
    }

    public static LinearLayout.LayoutParams withTop(Context c, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(c, topDp);
        return lp;
    }

    /** Big full-width pill button, e.g. "All apps", "Clear all". */
    public static TextView bigButton(Context c, String label, int bg, int fg) {
        TextView t = text(c, label, 16, fg, Theme.MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Theme.round(bg, Theme.dpf(c, 28)));
        t.setPadding(Theme.dp(c, 28), Theme.dp(c, 15), Theme.dp(c, 28), Theme.dp(c, 15));
        Theme.pressable(t);
        return t;
    }

    /** Round icon button (e.g. +/-, play/pause). */
    public static FrameLayout roundButton(Context c, String icon, int bg, int fg, int sizeDp) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(Theme.circle(bg));
        int px = Theme.dp(c, sizeDp);
        f.setLayoutParams(new LinearLayout.LayoutParams(px, px));
        Icons.IconView iv = new Icons.IconView(c, icon, fg);
        iv.setInsetFraction(0.28f);
        f.addView(iv, new FrameLayout.LayoutParams(px, px));
        Theme.pressable(f);
        return f;
    }

    /** Title bar used on every app/subpage: back arrow + centered title. */
    public static FrameLayout header(Context c, String title, Runnable onBack) {
        FrameLayout h = new FrameLayout(c);
        TextView t = text(c, title, 18, Theme.TEXT, Theme.MEDIUM);
        t.setGravity(Gravity.CENTER);
        h.addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        if (onBack != null) {
            FrameLayout back = roundButton(c, Icons.BACK, Theme.SURFACE, Theme.TEXT, 44);
            back.setOnClickListener(v -> onBack.run());
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    Theme.dp(c, 44), Theme.dp(c, 44), Gravity.CENTER_VERTICAL | Gravity.START);
            h.addView(back, lp);
        }
        h.setPadding(Theme.dp(c, 16), Theme.dp(c, 10), Theme.dp(c, 16), Theme.dp(c, 10));
        return h;
    }

    public static View spacer(Context c, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, Theme.dp(c, hDp)));
        return v;
    }
}
