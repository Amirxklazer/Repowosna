package com.wearlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.BatteryManager;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Draws the watch faces. Square display disc centred in the screen, like a watch on a phone. */
public class FaceView extends View {
    public static final int FACE_COUNT = 3;
    public static final String[] NAMES = {"Analog", "Ring", "Digital"};
    public static final int[] ACCENTS = {0xFFF3B6C9, 0xFF7FE3C4, 0xFFD2EC8F};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int face;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            invalidate();
            long now = System.currentTimeMillis();
            postDelayed(this, 1000 - (now % 1000) + 5);
        }
    };

    public FaceView(Context c) {
        super(c);
        face = Store.face(c);
    }

    public void setFace(int i) {
        face = ((i % FACE_COUNT) + FACE_COUNT) % FACE_COUNT;
        Store.setFace(getContext(), face);
        invalidate();
    }

    public int getFace() { return face; }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private int battery() {
        BatteryManager bm = (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
        int v = bm == null ? 100 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return v <= 0 ? 100 : v;
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float side = Math.min(w - Theme.dpf(getContext(), 28), h * 0.66f);
        float R = side / 2f;
        float cx = w / 2f, cy = h * 0.46f;

        // display disc
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF08080A);
        c.drawCircle(cx, cy, R, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(getContext(), 1.5f));
        p.setColor(0xFF1C1C21);
        c.drawCircle(cx, cy, R, p);

        Calendar cal = Calendar.getInstance();
        switch (face) {
            case 0: analog(c, cx, cy, R, cal); break;
            case 1: ring(c, cx, cy, R, cal); break;
            default: digital(c, cx, cy, R, cal); break;
        }

        // face name hint is drawn by the activity; nothing else here
    }

    // ---------- helpers ----------
    private void text(Canvas c, String s, float x, float y, float size, int color,
                      android.graphics.Typeface tf, Paint.Align a) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setTextAlign(a);
        c.drawText(s, x, y, p);
    }

    private float textH(float size, android.graphics.Typeface tf) {
        p.setTypeface(tf);
        p.setTextSize(size);
        Paint.FontMetrics fm = p.getFontMetrics();
        return -(fm.ascent + fm.descent) / 2f;   // offset to centre vertically
    }

    private void comp(Canvas c, float x, float y, float rad, int fill) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawCircle(x, y, rad, p);
    }

    private void icon(Canvas c, String path, float x, float y, float size, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        r.set(x - size / 2, y - size / 2, x + size / 2, y + size / 2);
        Icons.draw(c, path, r, p);
    }

    // ---------- face 0: analog ----------
    private void analog(Canvas c, float cx, float cy, float R, Calendar cal) {
        int accent = ACCENTS[0];
        // minute dots
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6 - 90);
            boolean major = i % 5 == 0;
            p.setColor(major ? 0xFFE6E1E9 : 0xFF4A4A52);
            float rr = R * 0.9f;
            c.drawCircle((float) (cx + Math.cos(a) * rr), (float) (cy + Math.sin(a) * rr),
                    major ? R * 0.022f : R * 0.012f, p);
        }

        // complications (right column)
        float cr = R * 0.15f;
        float bx = cx + R * 0.5f;
        comp(c, bx, cy - R * 0.5f, cr, 0xFF1E1E23);
        icon(c, Icons.LOCATION, bx, cy - R * 0.5f, cr * 1.1f, accent);
        comp(c, bx + R * 0.1f, cy, cr * 1.15f, 0xFF1E1E23);
        String mon = new SimpleDateFormat("MMM", Locale.getDefault()).format(new Date()).toUpperCase(Locale.getDefault());
        float s1 = R * 0.085f;
        text(c, mon, bx + R * 0.1f, cy - R * 0.02f, s1, accent, Theme.MEDIUM, Paint.Align.CENTER);
        text(c, String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), bx + R * 0.1f, cy + R * 0.115f,
                R * 0.12f, Theme.TEXT, Theme.MEDIUM, Paint.Align.CENTER);
        comp(c, bx, cy + R * 0.5f, cr, 0xFF1E1E23);
        icon(c, Icons.BELL, bx, cy + R * 0.5f - R * 0.035f, cr * 0.9f, accent);
        text(c, String.valueOf(NotifService.count), bx, cy + R * 0.5f + R * 0.09f,
                R * 0.07f, Theme.TEXT, Theme.MEDIUM, Paint.Align.CENTER);

        // hands
        float sec = cal.get(Calendar.SECOND);
        float min = cal.get(Calendar.MINUTE) + sec / 60f;
        float hr = (cal.get(Calendar.HOUR) % 12) + min / 60f;
        hand(c, cx, cy, hr * 30f, R * 0.5f, R * 0.075f, accent);
        hand(c, cx, cy, min * 6f, R * 0.78f, R * 0.05f, 0xFFF4EFF4);
        // second tick
        double sa = Math.toRadians(sec * 6 - 90);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(R * 0.014f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFFFFB59E);
        c.drawLine(cx, cy, (float) (cx + Math.cos(sa) * R * 0.84f), (float) (cy + Math.sin(sa) * R * 0.84f), p);
        comp(c, cx, cy, R * 0.045f, 0xFF08080A);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(R * 0.02f);
        p.setColor(accent);
        c.drawCircle(cx, cy, R * 0.045f, p);
    }

    private void hand(Canvas c, float cx, float cy, float deg, float len, float width, int color) {
        double a = Math.toRadians(deg - 90);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(width);
        p.setColor(color);
        c.drawLine(cx, cy, (float) (cx + Math.cos(a) * len), (float) (cy + Math.sin(a) * len), p);
    }

    // ---------- face 1: ring ----------
    private void ring(Canvas c, float cx, float cy, float R, Calendar cal) {
        int accent = ACCENTS[1];
        int bat = battery();
        float sw = R * 0.1f;
        r.set(cx - R * 0.86f, cy - R * 0.86f, cx + R * 0.86f, cy + R * 0.86f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(sw);
        p.setColor(0xFF1E2B26);
        c.drawArc(r, -90, 360, false, p);
        p.setColor(accent);
        c.drawArc(r, -90, 360f * bat / 100f, false, p);

        boolean h24 = Store.is24h(getContext());
        int hour = h24 ? cal.get(Calendar.HOUR_OF_DAY) : (cal.get(Calendar.HOUR) == 0 ? 12 : cal.get(Calendar.HOUR));
        String hh = String.format(Locale.US, "%02d", hour);
        String mm = String.format(Locale.US, "%02d", cal.get(Calendar.MINUTE));
        float big = R * 0.62f;
        float off = textH(big, Theme.LIGHT);
        text(c, hh, cx, cy - R * 0.2f + off, big, accent, Theme.LIGHT, Paint.Align.CENTER);
        text(c, mm, cx, cy + R * 0.38f + off, big, accent, Theme.LIGHT, Paint.Align.CENTER);
        icon(c, Icons.BATTERY, cx - R * 0.5f, cy - R * 0.62f, R * 0.11f, 0xFFE6E1E9);
        text(c, String.valueOf(bat), cx - R * 0.2f, cy - R * 0.62f + textH(R * 0.09f, Theme.MEDIUM),
                R * 0.09f, 0xFFE6E1E9, Theme.MEDIUM, Paint.Align.CENTER);
    }

    // ---------- face 2: digital ----------
    private void digital(Canvas c, float cx, float cy, float R, Calendar cal) {
        int accent = ACCENTS[2];
        String date = new SimpleDateFormat("EEE  MMM d", Locale.getDefault()).format(new Date()).toUpperCase(Locale.getDefault());
        text(c, date, cx, cy - R * 0.5f + textH(R * 0.13f, Theme.MEDIUM), R * 0.13f, 0xFFE6E1E9,
                Theme.MEDIUM, Paint.Align.CENTER);
        boolean h24 = Store.is24h(getContext());
        int hour = h24 ? cal.get(Calendar.HOUR_OF_DAY) : (cal.get(Calendar.HOUR) == 0 ? 12 : cal.get(Calendar.HOUR));
        String t = hour + ":" + String.format(Locale.US, "%02d", cal.get(Calendar.MINUTE));
        float big = R * 0.62f;
        text(c, t, cx, cy - R * 0.08f + textH(big, Theme.CONDENSED), big, accent, Theme.CONDENSED, Paint.Align.CENTER);

        // 3 complications
        float cr = R * 0.2f;
        float y = cy + R * 0.58f;
        int bat = battery();
        float dx = R * 0.5f;
        // ring complication
        comp(c, cx - dx, y, cr, 0xFF1E1E23);
        r.set(cx - dx - cr * 0.8f, y - cr * 0.8f, cx - dx + cr * 0.8f, y + cr * 0.8f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(R * 0.035f);
        p.setColor(0xFF39402A);
        c.drawArc(r, -90, 360, false, p);
        p.setColor(accent);
        c.drawArc(r, -90, 360f * bat / 100f, false, p);
        text(c, String.valueOf(bat), cx - dx, y + textH(R * 0.1f, Theme.MEDIUM), R * 0.1f,
                Theme.TEXT, Theme.MEDIUM, Paint.Align.CENTER);
        // battery icon complication
        comp(c, cx, y, cr, 0xFF1E1E23);
        icon(c, Icons.BATTERY, cx, y, cr * 1.0f, accent);
        // notifications complication
        comp(c, cx + dx, y, cr, 0xFF1E1E23);
        icon(c, Icons.BELL, cx + dx, y - cr * 0.3f, cr * 0.7f, accent);
        text(c, String.valueOf(NotifService.count), cx + dx, y + cr * 0.62f, R * 0.085f,
                Theme.TEXT, Theme.MEDIUM, Paint.Align.CENTER);
    }
}
