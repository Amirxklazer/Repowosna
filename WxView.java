package com.wearlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Weather glyphs drawn with vector shapes (no images). */
public class WxView extends View {
    public static final int SUN = 0, MOON = 1, PARTLY = 2, CLOUD = 3, FOG = 4, RAIN = 5, SNOW = 6, STORM = 7;
    private static final int SUNC = 0xFFFFD66B, CLOUDC = 0xFFDAD6E2, DARKC = 0xFFA9A5B8,
            RAINC = 0xFF7FB6FF, SNOWC = 0xFFFFFFFF, MOONC = 0xFFCDB8F5;

    private int kind = CLOUD;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public WxView(Context c) { super(c); }

    public void setKind(int k) { kind = k; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        draw(c, kind, getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()), p);
    }

    public static void draw(Canvas c, int kind, float cx, float cy, float s, Paint p) {
        switch (kind) {
            case SUN: sun(c, cx, cy, s * 0.95f, p); break;
            case MOON: {
                RectF b = new RectF(cx - s * 0.36f, cy - s * 0.36f, cx + s * 0.36f, cy + s * 0.36f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(MOONC);
                Icons.draw(c, Icons.MOON, b, p);
                break;
            }
            case PARTLY:
                sun(c, cx + s * 0.14f, cy - s * 0.14f, s * 0.65f, p);
                cloud(c, cx - s * 0.04f, cy + s * 0.1f, s * 0.85f, CLOUDC, p);
                break;
            case FOG:
                cloud(c, cx, cy - s * 0.12f, s * 0.85f, DARKC, p);
                line(c, cx - s * 0.3f, cy + s * 0.22f, cx + s * 0.3f, cy + s * 0.22f, s * 0.06f, DARKC, p);
                line(c, cx - s * 0.2f, cy + s * 0.34f, cx + s * 0.3f, cy + s * 0.34f, s * 0.06f, DARKC, p);
                break;
            case RAIN:
                cloud(c, cx, cy - s * 0.12f, s * 0.85f, DARKC, p);
                for (int i = -1; i <= 1; i++)
                    line(c, cx + i * s * 0.2f + s * 0.04f, cy + s * 0.18f, cx + i * s * 0.2f - s * 0.03f, cy + s * 0.34f, s * 0.055f, RAINC, p);
                break;
            case SNOW:
                cloud(c, cx, cy - s * 0.12f, s * 0.85f, DARKC, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(SNOWC);
                for (int i = -1; i <= 1; i++) c.drawCircle(cx + i * s * 0.2f, cy + s * 0.27f + (i == 0 ? s * 0.05f : 0), s * 0.035f, p);
                break;
            case STORM: {
                cloud(c, cx, cy - s * 0.14f, s * 0.85f, DARKC, p);
                RectF b = new RectF(cx - s * 0.17f, cy + s * 0.04f, cx + s * 0.17f, cy + s * 0.4f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(SUNC);
                Icons.draw(c, Icons.FLASH_ON, b, p);
                break;
            }
            default: cloud(c, cx, cy, s * 0.95f, CLOUDC, p);
        }
    }

    private static void line(Canvas c, float x1, float y1, float x2, float y2, float w, int color, Paint p) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(w);
        p.setColor(color);
        c.drawLine(x1, y1, x2, y2, p);
    }

    private static void sun(Canvas c, float cx, float cy, float s, Paint p) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(SUNC);
        c.drawCircle(cx, cy, s * 0.22f, p);
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(k * 45);
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            line(c, cx + dx * s * 0.32f, cy + dy * s * 0.32f, cx + dx * s * 0.45f, cy + dy * s * 0.45f, s * 0.06f, SUNC, p);
        }
    }

    private static void cloud(Canvas c, float cx, float cy, float s, int color, Paint p) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawCircle(cx - s * 0.2f, cy + s * 0.02f, s * 0.17f, p);
        c.drawCircle(cx + s * 0.02f, cy - s * 0.08f, s * 0.24f, p);
        c.drawCircle(cx + s * 0.25f, cy + s * 0.04f, s * 0.18f, p);
        RectF r = new RectF(cx - s * 0.37f, cy + s * 0.02f, cx + s * 0.43f, cy + s * 0.21f);
        c.drawRoundRect(r, s * 0.095f, s * 0.095f, p);
    }

    public static int kindFor(int code, boolean day) {
        if (code == 0) return day ? SUN : MOON;
        if (code == 1 || code == 2) return day ? PARTLY : CLOUD;
        if (code == 3) return CLOUD;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 67) return RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 80 && code <= 82) return RAIN;
        if (code >= 95) return STORM;
        return CLOUD;
    }

    public static String nameFor(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "Cloudy";
    }
}
