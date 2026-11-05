package com.wearlauncher;

import android.app.role.RoleManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Deque;

/** Wear OS-style settings: big pills, drill-down pages, toggles. */
public class SettingsActivity extends WearActivity {
    private LinearLayout col;
    private FrameLayout headerHolder;
    private EdgeScrollView scroll;
    private final Deque<String> stack = new ArrayDeque<>();
    private String page = "main";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        headerHolder = new FrameLayout(this);
        root.addView(headerHolder, new LinearLayout.LayoutParams(-1, -2));
        scroll = new EdgeScrollView(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(this, 16);
        col.setPadding(pad, Theme.dp(this, 6), pad, Theme.dp(this, 40));
        scroll.addView(col, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setRoot(root);
        String start = getIntent().getStringExtra("page");
        show(start == null ? "main" : start, false);
    }

    @Override protected void onResume() {
        super.onResume();
        if (col != null) show(page, false);     // refresh after returning from a system screen
    }

    private void show(String p, boolean push) {
        if (push) stack.push(page);
        page = p;
        col.removeAllViews();
        headerHolder.removeAllViews();
        boolean top = p.equals("main");
        headerHolder.addView(UiKit.header(this, title(p), top ? this::finish : this::back));
        build(p);
        scroll.scrollTo(0, 0);
    }

    private void back() {
        if (!stack.isEmpty()) show(stack.pop(), false);
        else finish();
    }

    @Override public void onBackPressed() { back(); }

    private String title(String p) {
        switch (p) {
            case "connectivity": return "Connectivity";
            case "display": return "Display";
            case "brightness": return "Brightness";
            case "face": return "Watch face";
            case "apps": return "Apps & defaults";
            case "touch": return "Gestures";
            case "battery": return "Battery";
            case "about": return "About";
            default: return "Settings";
        }
    }

    // ----- row helpers -----
    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.bottomMargin = Theme.dp(this, 8);
        return l;
    }

    private void nav(String icon, int pastel, String title, String sub, Runnable r) {
        LinearLayout row = UiKit.pill(this, icon, pastel, title, sub);
        row.setOnClickListener(v -> r.run());
        col.addView(row, lp());
    }

    private void toggle(String icon, int pastel, String title, String sub, boolean on, java.util.function.Consumer<Boolean> cb) {
        LinearLayout row = UiKit.pill(this, icon, pastel, title, sub);
        WearSwitch sw = new WearSwitch(this, on, pastel);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.leftMargin = Theme.dp(this, 8);
        row.addView(sw, slp);
        row.setOnClickListener(v -> {
            sw.setOn(!sw.isOn(), true);
            cb.accept(sw.isOn());
        });
        col.addView(row, lp());
    }

    private void sys(String action) {
        try { startActivity(new Intent(action)); } catch (Exception e) {
            android.widget.Toast.makeText(this, "Not available on this phone", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void build(String p) {
        switch (p) {
            case "connectivity":
                nav(Icons.WIFI, Theme.BLUE, "Wi-Fi", "Networks", () -> sys(Settings.ACTION_WIFI_SETTINGS));
                nav(Icons.BLUETOOTH, Theme.LILAC, "Bluetooth", "Devices", () -> sys(Settings.ACTION_BLUETOOTH_SETTINGS));
                nav(Icons.AIRPLANE, Theme.PEACH, "Airplane mode", null, () -> sys(Settings.ACTION_AIRPLANE_MODE_SETTINGS));
                break;
            case "display":
                nav(Icons.BRIGHTNESS, Theme.LIME, "Brightness", null, () -> show("brightness", true));
                toggle(Icons.CLOCK, Theme.CREAM, "24-hour time", "On the watch face", Store.is24h(this), v -> Store.set24h(this, v));
                toggle(Icons.MOON, Theme.LILAC, "Keep screen on", "Always-on launcher", Store.aod(this), v -> Store.setAod(this, v));
                break;
            case "brightness": brightness(); break;
            case "face":
                for (int i = 0; i < FaceView.FACE_COUNT; i++) {
                    final int idx = i;
                    boolean sel = Store.face(this) == i;
                    LinearLayout row = UiKit.pill(this, sel ? Icons.CHECK : Icons.CLOCK, FaceView.ACCENTS[i],
                            FaceView.NAMES[i], sel ? "Current" : "Tap to use");
                    row.setOnClickListener(v -> { Store.setFace(this, idx); show("face", false); });
                    col.addView(row, lp());
                }
                break;
            case "apps": apps(); break;
            case "touch":
                toggle(Icons.GESTURE, Theme.MINT, "Haptic feedback", "Tick when panels open", Store.haptics(this), v -> Store.setHaptics(this, v));
                break;
            case "battery": battery(); break;
            case "about": about(); break;
            default:
                nav(Icons.CLOCK, Theme.CREAM, "Watch face", FaceView.NAMES[Store.face(this)], () -> show("face", true));
                nav(Icons.WIFI, Theme.BLUE, "Connectivity", "Wi-Fi, Bluetooth, Airplane", () -> show("connectivity", true));
                nav(Icons.BRIGHTNESS, Theme.LIME, "Display", "Brightness, time", () -> show("display", true));
                nav(Icons.GESTURE, Theme.MINT, "Gestures", "Haptics", () -> show("touch", true));
                nav(Icons.GRID, Theme.PEACH, "Apps & defaults", "Notifications, default apps", () -> show("apps", true));
                nav(Icons.BATTERY, Theme.LIME, "Battery", null, () -> show("battery", true));
                nav(Icons.INFO, Theme.ROSE, "About", "Wear Launcher 1.0", () -> show("about", true));
        }
    }

    private void brightness() {
        if (!Settings.System.canWrite(this)) {
            nav(Icons.BRIGHTNESS, Theme.PEACH, "Allow brightness control", "Tap, then switch on for Wear Launcher",
                    () -> startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                            .setData(android.net.Uri.parse("package:" + getPackageName()))));
            return;
        }
        int cur = 128;
        try { cur = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS); } catch (Exception ignored) {}
        final int[] val = {cur};
        TextView big = UiKit.text(this, Math.round(cur / 2.55f) + "%", 56, Theme.TEXT, Theme.LIGHT);
        big.setGravity(Gravity.CENTER);
        col.addView(UiKit.spacer(this, 30));
        col.addView(big, new LinearLayout.LayoutParams(-1, -2));
        col.addView(UiKit.spacer(this, 28));

        Bar bar = new Bar(this);
        bar.setValue(cur / 255f);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout minus = UiKit.roundButton(this, Icons.REMOVE, Theme.LILAC, Theme.ON_PASTEL, 56);
        FrameLayout plus = UiKit.roundButton(this, Icons.ADD, Theme.LILAC, Theme.ON_PASTEL, 56);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, Theme.dp(this, 16), 1f);
        blp.leftMargin = blp.rightMargin = Theme.dp(this, 14);
        row.addView(minus);
        row.addView(bar, blp);
        row.addView(plus);
        col.addView(row, new LinearLayout.LayoutParams(-1, -2));

        Runnable apply = () -> {
            try {
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, val[0]);
            } catch (Exception ignored) {}
            bar.setValue(val[0] / 255f);
            big.setText(Math.round(val[0] / 2.55f) + "%");
        };
        minus.setOnClickListener(v -> { val[0] = Math.max(8, val[0] - 25); apply.run(); });
        plus.setOnClickListener(v -> { val[0] = Math.min(255, val[0] + 25); apply.run(); });

        col.addView(UiKit.spacer(this, 24));
        boolean auto = false;
        try { auto = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE) == 1; } catch (Exception ignored) {}
        toggle(Icons.BRIGHTNESS, Theme.LIME, "Auto", "Adjust to surroundings", auto, v -> {
            try {
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                        v ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            } catch (Exception ignored) {}
        });
    }

    private void apps() {
        boolean granted = androidx.core.app.NotificationManagerCompat
                .getEnabledListenerPackages(this).contains(getPackageName());
        nav(Icons.BELL, Theme.PEACH, "Notification access", granted ? "Allowed" : "Needed for the stream and media tile",
                () -> sys(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        nav(Icons.GRID, Theme.BLUE, "Home app", "Make Wear Launcher your home", () -> {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 7);
                } else sys(Settings.ACTION_HOME_SETTINGS);
            } catch (Exception e) { sys(Settings.ACTION_HOME_SETTINGS); }
        });
        nav(Icons.PHONE, Theme.MINT, "Default apps", "Phone, Messages, Camera, Gallery", () -> sys(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        nav(Icons.SETTINGS, Theme.LILAC, "App info", "Permissions for Wear Launcher", () -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {}
        });
    }

    private void battery() {
        android.os.BatteryManager bm = (android.os.BatteryManager) getSystemService(BATTERY_SERVICE);
        int v = bm == null ? 100 : bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
        TextView big = UiKit.text(this, v + "%", 60, Theme.TEXT, Theme.LIGHT);
        big.setGravity(Gravity.CENTER);
        col.addView(UiKit.spacer(this, 24));
        col.addView(big, new LinearLayout.LayoutParams(-1, -2));
        col.addView(UiKit.spacer(this, 22));
        Bar bar = new Bar(this);
        bar.setColor(Theme.MINT);
        bar.setValue(v / 100f);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, Theme.dp(this, 14));
        blp.leftMargin = blp.rightMargin = Theme.dp(this, 10);
        col.addView(bar, blp);
        TextView sub = UiKit.text(this, "Charge remaining", 14, Theme.TEXT_DIM, Theme.REGULAR);
        sub.setGravity(Gravity.CENTER);
        col.addView(sub, UiKit.withTop(this, 12));
        col.addView(UiKit.spacer(this, 24));
        nav(Icons.BATTERY, Theme.LIME, "Battery saver", "Opens system setting", () -> sys(Settings.ACTION_BATTERY_SAVER_SETTINGS));
    }

    private void about() {
        nav(Icons.INFO, Theme.ROSE, "Wear Launcher", "Version 1.0", () -> {});
        nav(Icons.PERSON, Theme.BLUE, "Device", Build.MANUFACTURER + " " + Build.MODEL, () -> {});
        nav(Icons.SETTINGS, Theme.LILAC, "Android", "Version " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")", () -> {});
    }
}
