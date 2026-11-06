package com.wearlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Compass: rotating dial drawn with vector ticks. */
public class CompassActivity extends WearActivity implements SensorEventListener {
    private static final String[] CARD = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private static class Dial extends View {
        float az;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path tri = new Path();
        Dial(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float cx = w / 2, cy = h / 2;
            float r = Math.min(w, h) / 2 - Theme.dpf(getContext(), 22);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Theme.SURFACE);
            c.drawCircle(cx, cy, r + Theme.dpf(getContext(), 6), p);

            c.save();
            c.rotate(-az, cx, cy);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            for (int d = 0; d < 360; d += 5) {
                boolean major = d % 30 == 0, mid = d % 10 == 0;
                float len = Theme.dpf(getContext(), major ? 16 : mid ? 10 : 6);
                p.setStrokeWidth(Theme.dpf(getContext(), major ? 3 : 2));
                p.setColor(major ? Theme.TEXT : Theme.TEXT_DIM);
                c.save();
                c.rotate(d, cx, cy);
                c.drawLine(cx, cy - r, cx, cy - r + len, p);
                c.restore();
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Theme.dpf(getContext(), 22));
            p.setTypeface(Theme.MEDIUM);
            String[] l = {"N", "E", "S", "W"};
            for (int i = 0; i < 4; i++) {
                p.setColor(i == 0 ? Theme.PEACH : Theme.TEXT);
                c.save();
                c.rotate(i * 90, cx, cy);
                c.drawText(l[i], cx, cy - r + Theme.dpf(getContext(), 44), p);
                c.restore();
            }
            c.restore();

            p.setColor(Theme.PEACH);
            tri.reset();
            float t = Theme.dpf(getContext(), 9);
            tri.moveTo(cx - t, cy - r - Theme.dpf(getContext(), 14));
            tri.lineTo(cx + t, cy - r - Theme.dpf(getContext(), 14));
            tri.lineTo(cx, cy - r + Theme.dpf(getContext(), 2));
            tri.close();
            c.drawPath(tri, p);

            int deg = Math.round(az) % 360;
            p.setColor(Theme.TEXT);
            p.setTypeface(Theme.LIGHT);
            p.setTextSize(Theme.dpf(getContext(), 52));
            c.drawText(deg + "\u00B0", cx, cy + Theme.dpf(getContext(), 6), p);
            p.setTypeface(Theme.MEDIUM);
            p.setColor(Theme.LILAC);
            p.setTextSize(Theme.dpf(getContext(), 22));
            c.drawText(CARD[Math.round(az / 45f) % 8], cx, cy + Theme.dpf(getContext(), 38), p);
        }
    }

    private Dial dial;
    private SensorManager sm;
    private Sensor sensor;
    private boolean first = true;
    private float az = 0;
    private final float[] rm = new float[9], o = new float[3];

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(UiKit.header(this, "Compass", this::finish), new LinearLayout.LayoutParams(-1, -2));
        FrameLayout center = new FrameLayout(this);
        dial = new Dial(this);
        center.addView(dial, new FrameLayout.LayoutParams(-1, -1));
        if (sensor == null) {
            center.addView(UiKit.text(this, "No compass sensor on this phone", 16, Theme.TEXT_DIM, Theme.MEDIUM),
                    new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        }
        root.addView(center, new LinearLayout.LayoutParams(-1, 0, 1f));
        setRoot(root);
    }

    @Override protected void onResume() {
        super.onResume();
        if (sensor != null) sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override protected void onPause() {
        super.onPause();
        sm.unregisterListener(this);
    }

    @Override public void onSensorChanged(SensorEvent e) {
        SensorManager.getRotationMatrixFromVector(rm, e.values);
        SensorManager.getOrientation(rm, o);
        float a = (float) Math.toDegrees(o[0]);
        if (a < 0) a += 360;
        if (first) { az = a; first = false; }
        else {
            float d = ((a - az + 540) % 360) - 180;
            az = (az + d * 0.2f + 360) % 360;
        }
        dial.az = az;
        dial.invalidate();
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}
}
