package com.wearlauncher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Fitness: today's steps from the phone's step counter, in a big ring. */
public class StepsActivity extends WearActivity implements SensorEventListener {
    private RingView ring;
    private TextView stepsTv, goalTv, distTv, kcalTv, status;
    private SensorManager sm;
    private Sensor sensor;
    private int steps = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(UiKit.header(this, "Fitness", this::finish), new LinearLayout.LayoutParams(-1, -2));

        FrameLayout center = new FrameLayout(this);
        int size = Theme.dp(this, 260);
        ring = new RingView(this);
        center.addView(ring, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        inner.addView(Icons.view(this, Icons.WALK, Theme.MINT, 30));
        stepsTv = UiKit.text(this, "0", 52, Theme.TEXT, Theme.LIGHT);
        inner.addView(stepsTv, UiKit.withTop(this, 4));
        goalTv = UiKit.text(this, "", 14, Theme.TEXT_DIM, Theme.REGULAR);
        inner.addView(goalTv, UiKit.withTop(this, 4));
        center.addView(inner, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        root.addView(center, new LinearLayout.LayoutParams(-1, 0, 1.2f));

        LinearLayout stats = new LinearLayout(this);
        stats.setGravity(Gravity.CENTER);
        distTv = stat(stats, "Distance");
        kcalTv = stat(stats, "Calories");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(Theme.dp(this, 16), 0, Theme.dp(this, 16), Theme.dp(this, 10));
        root.addView(stats, slp);

        LinearLayout goal = new LinearLayout(this);
        goal.setGravity(Gravity.CENTER_VERTICAL);
        goal.setPadding(Theme.dp(this, 20), Theme.dp(this, 10), Theme.dp(this, 14), Theme.dp(this, 10));
        goal.setBackground(Theme.round(Theme.SURFACE, Theme.dpf(this, 32)));
        TextView gl = UiKit.text(this, "Daily goal", 17, Theme.TEXT, Theme.MEDIUM);
        goal.addView(gl, new LinearLayout.LayoutParams(0, -2, 1f));
        FrameLayout minus = UiKit.roundButton(this, Icons.REMOVE, Theme.LILAC, Theme.ON_PASTEL, 44);
        FrameLayout plus = UiKit.roundButton(this, Icons.ADD, Theme.LILAC, Theme.ON_PASTEL, 44);
        ((LinearLayout.LayoutParams) plus.getLayoutParams()).leftMargin = Theme.dp(this, 10);
        goal.addView(minus);
        goal.addView(plus);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, -2);
        glp.setMargins(Theme.dp(this, 16), 0, Theme.dp(this, 16), Theme.dp(this, 8));
        root.addView(goal, glp);
        minus.setOnClickListener(v -> { Store.setStepGoal(this, Math.max(1000, Store.stepGoal(this) - 500)); update(); });
        plus.setOnClickListener(v -> { Store.setStepGoal(this, Math.min(30000, Store.stepGoal(this) + 500)); update(); });

        status = UiKit.text(this, "", 13, Theme.TEXT_DIM, Theme.REGULAR);
        status.setGravity(Gravity.CENTER);
        status.setPadding(Theme.dp(this, 24), 0, Theme.dp(this, 24), Theme.dp(this, 16));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        setRoot(root);
        update();
    }

    private TextView stat(LinearLayout parent, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, Theme.dp(this, 12), 0, Theme.dp(this, 12));
        box.setBackground(Theme.round(Theme.SURFACE, Theme.dpf(this, 28)));
        TextView v = UiKit.text(this, "0", 22, Theme.TEXT, Theme.MEDIUM);
        TextView l = UiKit.text(this, label, 12, Theme.TEXT_DIM, Theme.REGULAR);
        box.addView(v);
        box.addView(l, UiKit.withTop(this, 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.leftMargin = lp.rightMargin = Theme.dp(this, 4);
        parent.addView(box, lp);
        return v;
    }

    private boolean permitted() {
        return Build.VERSION.SDK_INT < 29
                || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override protected void onResume() {
        super.onResume();
        if (sensor == null) { status.setText("This phone has no step counter sensor."); return; }
        if (!permitted()) {
            status.setText("Allow physical activity to count steps.");
            requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, 6);
            return;
        }
        status.setText("Steps are counted from the first time you open Fitness each day.");
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override protected void onPause() {
        super.onPause();
        sm.unregisterListener(this);
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        onResume();
    }

    @Override public void onSensorChanged(SensorEvent e) {
        float total = e.values[0];
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        float base = Store.stepsBase(this);
        if (!today.equals(Store.stepsDay(this)) || base > total) {
            Store.setStepsBase(this, today, total);
            base = total;
        }
        steps = Math.max(0, (int) (total - base));
        update();
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    private void update() {
        int goal = Store.stepGoal(this);
        stepsTv.setText(String.format(Locale.getDefault(), "%,d", steps));
        goalTv.setText("of " + String.format(Locale.getDefault(), "%,d", goal) + " steps");
        ring.setFrac(steps / (float) goal);
        ring.setColor(steps >= goal ? Theme.LIME : Theme.MINT);
        double meters = steps * 0.762;
        if (Store.fahrenheit(this)) distTv.setText(String.format(Locale.getDefault(), "%.2f mi", meters / 1609.34));
        else distTv.setText(String.format(Locale.getDefault(), "%.2f km", meters / 1000.0));
        kcalTv.setText(String.valueOf(Math.round(steps * 0.04)));
    }
}
