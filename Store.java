package com.wearlauncher;

import android.content.Context;
import android.content.SharedPreferences;

/** Tiny wrapper around SharedPreferences for launcher + settings state. */
public final class Store {
    private Store() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("wear", Context.MODE_PRIVATE);
    }

    public static int face(Context c) { return sp(c).getInt("face", 0); }
    public static void setFace(Context c, int i) { sp(c).edit().putInt("face", i).apply(); }

    public static boolean is24h(Context c) { return sp(c).getBoolean("h24", false); }
    public static void set24h(Context c, boolean b) { sp(c).edit().putBoolean("h24", b).apply(); }

    public static boolean gridView(Context c) { return sp(c).getBoolean("grid", true); }
    public static void setGridView(Context c, boolean b) { sp(c).edit().putBoolean("grid", b).apply(); }

    public static boolean aod(Context c) { return sp(c).getBoolean("aod", false); }
    public static void setAod(Context c, boolean b) { sp(c).edit().putBoolean("aod", b).apply(); }

    public static boolean haptics(Context c) { return sp(c).getBoolean("haptics", true); }
    public static void setHaptics(Context c, boolean b) { sp(c).edit().putBoolean("haptics", b).apply(); }

    public static boolean tiltWake(Context c) { return sp(c).getBoolean("tilt", true); }
    public static void setTiltWake(Context c, boolean b) { sp(c).edit().putBoolean("tilt", b).apply(); }

    public static int accent(Context c) { return sp(c).getInt("accent", 0); }
    public static void setAccent(Context c, int i) { sp(c).edit().putInt("accent", i).apply(); }

    public static String recents(Context c) { return sp(c).getString("recents", ""); }

    /** Remember last launched components (flattened, most recent first, max 3). */
    public static void pushRecent(Context c, String pkg) {
        String[] old = recents(c).isEmpty() ? new String[0] : recents(c).split(",");
        StringBuilder sb = new StringBuilder(pkg);
        int n = 1;
        for (String s : old) {
            if (s.equals(pkg) || n >= 3) continue;
            sb.append(',').append(s);
            n++;
        }
        sp(c).edit().putString("recents", sb.toString()).apply();
    }
}
