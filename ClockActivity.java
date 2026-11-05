package com.wearlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Stopwatch and timer with a progress ring. */
public class ClockActivity extends WearActivity {
    private static class Ring extends View {
        float frac = 1f;
        int color = Theme.CREAM;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        Ring(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float s = Theme.dpf(getContext(), 12);
            r.set(s, s, getWidth() - s, getHeight() - s);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0xFF26262C);
            c.drawArc(r, -90, 360, false, p);
            p.setColor(color);
            c.drawArc(r, -90, 360f * frac, false, p);
        }
    }

    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean timerMode = false;

    // stopwatch
    private boolean swRunning = false;
    private long swStart, swAccum;
    // timer
    private boolean tmRunning = false;
    private long tmEnd, tmTotal = 5 * 60_000L, tmLeft = 5 * 60_000L;

    private Ring ring;
    private TextView time, tabSw, tabTm;
    private FrameLayout primary, secondary;
    private Icons.IconView primaryIcon;
    private LinearLayout controls;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            update();
            h.postDelayed(this, 40);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(UiKit.header(this, "Clock", this::finish), new LinearLayout.LayoutParams(-1, -2));

        // segmented tabs
        LinearLayout tabs = new LinearLayout(this);
        tabs.setBackground(Theme.round(Theme.SURFACE, Theme.dpf(this, 24)));
        tabs.setPadding(Theme.dp(this, 4), Theme.dp(this, 4), Theme.dp(this, 4), Theme.dp(this, 4));
        tabSw = tab("Stopwatch");
        tabTm = tab("Timer");
        tabSw.setOnClickListener(v -> setMode(false));
        tabTm.setOnClickListener(v -> setMode(true));
        tabs.addView(tabSw, new LinearLayout.LayoutParams(Theme.dp(this, 130), -2));
        tabs.addView(tabTm, new LinearLayout.LayoutParams(Theme.dp(this, 130), -2));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Theme.dp(this, 8);
        root.addView(tabs, tlp);

        FrameLayout face = new FrameLayout(this);
        ring = new Ring(this);
        int rs = Theme.dp(this, 270);
        face.addView(ring, new FrameLayout.LayoutParams(rs, rs, Gravity.CENTER));
        time = UiKit.text(this, "00:00", 50, Theme.TEXT, Theme.LIGHT);
        face.addView(time, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        root.addView(face, new LinearLayout.LayoutParams(-1, 0, 1f));

        controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        secondary = UiKit.roundButton(this, Icons.CLOSE, Theme.SURFACE_HI, Theme.TEXT, 64);
        primary = UiKit.roundButton(this, Icons.PLAY, Theme.CREAM, Theme.ON_PASTEL, 84);
        primaryIcon = (Icons.IconView) primary.getChildAt(0);
        controls.addView(secondary);
        controls.addView(new View(this), new LinearLayout.LayoutParams(Theme.dp(this, 24), 1));
        controls.addView(primary);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.bottomMargin = Theme.dp(this, 36);
        root.addView(controls, clp);

        primary.setOnClickListener(v -> onPrimary());
        secondary.setOnClickListener(v -> onSecondary());
        setRoot(root);

        int secs = getIntent().getIntExtra("timer_seconds", 0);
        if (secs > 0) {
            tmTotal = tmLeft = secs * 1000L;
            setMode(true);
            onPrimary();
        } else {
            setMode(false);
        }
    }

    private TextView tab(String s) {
        TextView t = UiKit.text(this, s, 14, Theme.TEXT_DIM, Theme.MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Theme.dp(this, 10), 0, Theme.dp(this, 10));
        return t;
    }

    private void setMode(boolean timer) {
        timerMode = timer;
        tabSw.setBackground(timer ? null : Theme.round(Theme.CREAM, Theme.dpf(this, 20)));
        tabTm.setBackground(timer ? Theme.round(Theme.CREAM, Theme.dpf(this, 20)) : null);
        tabSw.setTextColor(timer ? Theme.TEXT_DIM : Theme.ON_PASTEL);
        tabTm.setTextColor(timer ? Theme.ON_PASTEL : Theme.TEXT_DIM);
        update();
    }

    private void onPrimary() {
        if (timerMode) {
            if (tmRunning) { tmLeft = Math.max(0, tmEnd - System.currentTimeMillis()); tmRunning = false; }
            else { if (tmLeft <= 0) tmLeft = tmTotal; tmEnd = System.currentTimeMillis() + tmLeft; tmRunning = true; }
        } else {
            if (swRunning) { swAccum += System.currentTimeMillis() - swStart; swRunning = false; }
            else { swStart = System.currentTimeMillis(); swRunning = true; }
        }
        update();
    }

    private void onSecondary() {
        if (timerMode) {
            if (tmRunning) { tmRunning = false; }
            else { tmTotal = tmTotal >= 60 * 60_000L ? 60_000L : tmTotal + 60_000L; }   // +1 min when idle
            tmLeft = tmTotal;
        } else {
            swRunning = false; swAccum = 0;
        }
        update();
    }

    private void update() {
        boolean running = timerMode ? tmRunning : swRunning;
        primaryIcon.setPath(running ? Icons.PAUSE : Icons.PLAY);
        secondary.removeAllViews();
        Icons.IconView si = new Icons.IconView(this, timerMode && !running ? Icons.ADD : Icons.STOP, Theme.TEXT);
        si.setInsetFraction(0.28f);
        int px = Theme.dp(this, 64);
        secondary.addView(si, new FrameLayout.LayoutParams(px, px));

        if (timerMode) {
            long left = tmRunning ? Math.max(0, tmEnd - System.currentTimeMillis()) : tmLeft;
            if (tmRunning && left == 0) {
                tmRunning = false; tmLeft = 0;
                buzz();
            }
            long s = (left + 999) / 1000;
            time.setText(String.format(Locale.US, "%02d:%02d", s / 60, s % 60));
            ring.frac = tmTotal == 0 ? 0 : left / (float) tmTotal;
        } else {
            long ms = swAccum + (swRunning ? System.currentTimeMillis() - swStart : 0);
            long s = ms / 1000;
            time.setText(String.format(Locale.US, "%02d:%02d.%d", s / 60, s % 60, (ms % 1000) / 100));
            ring.frac = (ms % 60000) / 60000f;
        }
        ring.invalidate();
    }

    private void buzz() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 150, 300, 150, 500}, -1));
        } catch (Exception ignored) {}
    }

    @Override protected void onResume() { super.onResume(); h.post(tick); }
    @Override protected void onPause() { h.removeCallbacks(tick); super.onPause(); }
}
