package com.wearlauncher;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Pull-down quick settings: 3x3 round toggles, pastel when on, like Wear OS 4. */
public class QuickSettingsView extends FrameLayout {
    private final Host host;
    private final Tile[] tiles = new Tile[9];
    private final TextView battery;
    private final TextView date;

    private static class Tile {
        FrameLayout bubble;
        Icons.IconView icon;
        TextView label;
        int pastel;
    }

    private android.view.GestureDetector swipe;
    private Runnable onSwipeUp;

    public void setSwipeUpListener(Runnable r) {
        onSwipeUp = r;
        swipe = new android.view.GestureDetector(getContext(), new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(android.view.MotionEvent a, android.view.MotionEvent b, float vx, float vy) {
                if (a != null && b != null && vy < -900 && a.getY() - b.getY() > Theme.dp(getContext(), 60)
                        && Math.abs(vy) > Math.abs(vx)) {
                    if (onSwipeUp != null) onSwipeUp.run();
                    return true;
                }
                return false;
            }
        });
    }

    @Override public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        if (swipe != null) swipe.onTouchEvent(e);
        return super.dispatchTouchEvent(e);
    }

    public QuickSettingsView(Context c, Host host) {
        super(c);
        this.host = host;
        setBackgroundColor(Theme.BG);

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Theme.dp(c, 20);
        root.setPadding(pad, Theme.dp(c, 36), pad, Theme.dp(c, 24));

        date = UiKit.text(c, "", 15, Theme.TEXT_DIM, Theme.MEDIUM);
        root.addView(date, new LinearLayout.LayoutParams(-2, -2));
        battery = UiKit.text(c, "", 34, Theme.TEXT, Theme.LIGHT);
        root.addView(battery, UiKit.withTop(c, 4));
        root.addView(UiKit.spacer(c, 28));

        String[] icons = {Icons.WIFI, Icons.BLUETOOTH, Icons.AIRPLANE,
                Icons.FLASHLIGHT, Icons.BRIGHTNESS, Icons.DND,
                Icons.BELL, Icons.BATTERY, Icons.SETTINGS};
        String[] names = {"Wi-Fi", "Bluetooth", "Airplane", "Flashlight", "Brightness",
                "Do not disturb", "Sound", "Battery saver", "Settings"};
        int[] pastels = {Theme.BLUE, Theme.LILAC, Theme.PEACH, Theme.CREAM, Theme.LIME,
                Theme.ROSE, Theme.MINT, Theme.LIME, Theme.BLUE};

        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 3; k++) {
                final int idx = r * 3 + k;
                Tile t = new Tile();
                t.pastel = pastels[idx];
                LinearLayout cell = new LinearLayout(c);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                t.bubble = new FrameLayout(c);
                int sz = Theme.dp(c, 70);
                t.icon = new Icons.IconView(c, icons[idx], Theme.TEXT);
                t.icon.setInsetFraction(0.28f);
                t.bubble.addView(t.icon, new FrameLayout.LayoutParams(sz, sz));
                cell.addView(t.bubble, new LinearLayout.LayoutParams(sz, sz));
                t.label = UiKit.text(c, names[idx], 12, Theme.TEXT_DIM, Theme.MEDIUM);
                t.label.setGravity(Gravity.CENTER);
                t.label.setSingleLine(true);
                cell.addView(t.label, UiKit.withTop(c, 8));
                Theme.pressable(t.bubble);
                t.bubble.setOnClickListener(v -> onTile(idx));
                tiles[idx] = t;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                lp.bottomMargin = Theme.dp(c, 22);
                row.addView(cell, lp);
            }
            root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        addView(root, new LayoutParams(-1, -2));
        refresh();
    }

    private void set(int i, boolean on) {
        Tile t = tiles[i];
        t.bubble.setBackground(Theme.circle(on ? t.pastel : Theme.SURFACE));
        t.icon.setColor(on ? Theme.ON_PASTEL : Theme.TEXT);
    }

    /** Re-reads every system state. Called whenever the panel opens. */
    public void refresh() {
        Context c = getContext();
        boolean wifi = false, bt = false, air = false, dnd = false, mute = false, saver = false;
        try { wifi = ((WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).isWifiEnabled(); } catch (Exception ignored) {}
        try { bt = BluetoothAdapter.getDefaultAdapter() != null && BluetoothAdapter.getDefaultAdapter().isEnabled(); } catch (Exception ignored) {}
        try { air = Settings.Global.getInt(c.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1; } catch (Exception ignored) {}
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            dnd = nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL
                    && nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
        } catch (Exception ignored) {}
        try { mute = ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE)).getRingerMode() != AudioManager.RINGER_MODE_NORMAL; } catch (Exception ignored) {}
        try { saver = ((android.os.PowerManager) c.getSystemService(Context.POWER_SERVICE)).isPowerSaveMode(); } catch (Exception ignored) {}
        set(0, wifi); set(1, bt); set(2, air);
        set(3, Torch.isOn(c)); set(4, false); set(5, dnd);
        set(6, mute); set(7, saver); set(8, false);
        tiles[6].icon.setPath(mute ? Icons.BELL_OFF : Icons.BELL);
        tiles[6].label.setText(mute ? "Vibrate" : "Sound");

        BatteryManager bm = (BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
        int lvl = bm == null ? 100 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        battery.setText(lvl + "%");
        date.setText(new SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(new Date()));
    }

    private void onTile(int i) {
        Context c = getContext();
        switch (i) {
            case 0:
                host.startApp(new Intent(android.os.Build.VERSION.SDK_INT >= 29
                        ? Settings.Panel.ACTION_INTERNET_CONNECTIVITY : Settings.ACTION_WIFI_SETTINGS));
                break;
            case 1: host.startApp(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); break;
            case 2: host.startApp(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)); break;
            case 3:
                if (!Torch.toggle(c)) android.widget.Toast.makeText(c, "No flashlight found", android.widget.Toast.LENGTH_SHORT).show();
                postDelayed(this::refresh, 150);
                break;
            case 4: host.startApp(new Intent(c, SettingsActivity.class).putExtra("page", "brightness")); break;
            case 5: toggleDnd(); break;
            case 6: toggleSound(); break;
            case 7: host.startApp(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)); break;
            case 8: host.startApp(new Intent(c, SettingsActivity.class)); break;
        }
    }

    private void toggleDnd() {
        Context c = getContext();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (!nm.isNotificationPolicyAccessGranted()) {
            host.startApp(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            return;
        }
        boolean on = nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
        nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_ALL
                : NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        postDelayed(this::refresh, 200);
    }

    private void toggleSound() {
        Context c = getContext();
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            am.setRingerMode(am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL
                    ? AudioManager.RINGER_MODE_VIBRATE : AudioManager.RINGER_MODE_NORMAL);
        } catch (Exception e) {
            host.startApp(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        }
        postDelayed(this::refresh, 150);
    }
}
