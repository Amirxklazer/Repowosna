package com.wearlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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
    public static boolean hideStock(Context c) { return sp(c).getBoolean("hideStock", true); }
    public static void setHideStock(Context c, boolean b) { sp(c).edit().putBoolean("hideStock", b).apply(); }

    public static Set<String> hidden(Context c) {
        String s = sp(c).getString("hidden", "");
        Set<String> r = new HashSet<>();
        if (!s.isEmpty()) for (String x : s.split("\\|")) r.add(x);
        return r;
    }
    public static void hide(Context c, String key) {
        Set<String> h = hidden(c);
        h.add(key);
        sp(c).edit().putString("hidden", android.text.TextUtils.join("|", h)).apply();
    }
    public static void clearHidden(Context c) { sp(c).edit().putString("hidden", "").apply(); }

    public static boolean fahrenheit(Context c) {
        return sp(c).getBoolean("fahrenheit", Locale.getDefault().getCountry().equals("US"));
    }
    public static void setFahrenheit(Context c, boolean b) { sp(c).edit().putBoolean("fahrenheit", b).apply(); }

    public static int stepGoal(Context c) { return sp(c).getInt("stepGoal", 6000); }
    public static void setStepGoal(Context c, int g) { sp(c).edit().putInt("stepGoal", g).apply(); }
    public static String stepsDay(Context c) { return sp(c).getString("stepsDay", ""); }
    public static float stepsBase(Context c) { return sp(c).getFloat("stepsBase", 0f); }
    public static void setStepsBase(Context c, String day, float base) {
        sp(c).edit().putString("stepsDay", day).putFloat("stepsBase", base).apply();
    }

    public static String notes(Context c) { return sp(c).getString("notes", "[]"); }
    public static void setNotes(Context c, String json) { sp(c).edit().putString("notes", json).apply(); }
}
