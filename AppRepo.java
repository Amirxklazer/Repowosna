package com.wearlauncher;

import android.app.DownloadManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.util.LruCache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads the app list + icons OFF the main thread so scrolling never lags. */
public final class AppRepo {
    private AppRepo() {}

    public static class App {
        public String label, pkg, key, glyph;
        public ComponentName cn;
        public int pastel;
        public boolean builtin, stock;
        public Intent intent;          // set for built-ins and shortcuts
        public ResolveInfo ri;         // set for installed apps

        public Intent launchIntent() {
            if (intent != null) return new Intent(intent);
            return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
    }

    private static volatile List<App> ALL = new ArrayList<>();
    private static volatile boolean loaded = false;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(Math.max(2048, (int) (Runtime.getRuntime().maxMemory() / 1024 / 10))) {
                @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount() / 1024; }
            };

    public static List<App> all() { return ALL; }
    public static boolean isLoaded() { return loaded; }

    public static void load(Context ctx, Runnable done) {
        final Context c = ctx.getApplicationContext();
        POOL.execute(() -> {
            List<App> out = new ArrayList<>();
            try { out = build(c); } catch (Exception ignored) {}
            ALL = out;
            loaded = true;
            MAIN.post(done);
        });
    }

    private static final String[] KNOWN_STOCK = {
            "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer", "com.android.contacts",
            "com.google.android.contacts", "com.samsung.android.app.contacts", "com.google.android.apps.messaging",
            "com.android.mms", "com.samsung.android.messaging", "com.google.android.calendar", "com.android.calendar",
            "com.samsung.android.calendar", "com.google.android.deskclock", "com.android.deskclock",
            "com.sec.android.app.clockpackage", "com.google.android.calculator", "com.android.calculator2",
            "com.sec.android.app.popupcalculator", "com.android.camera", "com.android.camera2",
            "com.google.android.GoogleCamera", "com.sec.android.app.camera", "com.google.android.apps.photos",
            "com.google.android.gallery3d", "com.android.gallery3d", "com.sec.android.gallery3d", "com.miui.gallery",
            "com.google.android.apps.nbu.files", "com.android.soundrecorder"
    };

    private static Set<String> stockPackages(PackageManager pm, String self) {
        Set<String> s = new HashSet<>(Arrays.asList(KNOWN_STOCK));
        List<Intent> probes = new ArrayList<>();
        probes.add(new Intent(Intent.ACTION_DIAL));
        probes.add(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
        probes.add(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
        String[] cats = {Intent.CATEGORY_APP_MESSAGING, Intent.CATEGORY_APP_GALLERY, Intent.CATEGORY_APP_CALENDAR,
                Intent.CATEGORY_APP_CONTACTS, Intent.CATEGORY_APP_CALCULATOR};
        for (String cat : cats) probes.add(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, cat));
        for (Intent i : probes) {
            try {
                for (ResolveInfo ri : pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY)) {
                    ApplicationInfo ai = ri.activityInfo.applicationInfo;
                    String pkg = ri.activityInfo.packageName;
                    if (ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && !pkg.equals(self)) s.add(pkg);
                }
            } catch (Exception ignored) {}
        }
        return s;
    }

    private static void builtin(List<App> l, Context c, String label, String glyph, int pastel, Class<?> cls) {
        App a = new App();
        a.label = label; a.glyph = glyph; a.pastel = pastel; a.builtin = true;
        a.pkg = c.getPackageName();
        a.cn = new ComponentName(c, cls);
        a.key = a.cn.flattenToShortString();
        a.intent = new Intent(c, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        l.add(a);
    }

    private static void shortcut(List<App> l, PackageManager pm, String label, String glyph, int pastel, Intent i) {
        try {
            if (pm.resolveActivity(i, 0) == null) return;
        } catch (Exception e) { return; }
        App a = new App();
        a.label = label; a.glyph = glyph; a.pastel = pastel; a.builtin = true;
        a.key = "sc:" + label;
        a.intent = i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        l.add(a);
    }

    private static List<App> build(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> stock = stockPackages(pm, c.getPackageName());
        List<App> out = new ArrayList<>();
        builtin(out, c, "Phone", Icons.PHONE, Theme.BLUE, PhoneActivity.class);
        builtin(out, c, "Messages", Icons.MESSAGE, Theme.MINT, MessagesActivity.class);
        builtin(out, c, "Camera", Icons.CAMERA, Theme.LILAC, CameraActivity.class);
        builtin(out, c, "Gallery", Icons.PHOTO, Theme.PEACH, GalleryActivity.class);
        builtin(out, c, "Contacts", Icons.PERSON, Theme.CREAM, ContactsActivity.class);
        builtin(out, c, "Calendar", Icons.CALENDAR, Theme.ROSE, CalendarActivity.class);
        builtin(out, c, "Clock", Icons.TIMER, Theme.LIME, ClockActivity.class);
        builtin(out, c, "Calculator", Icons.CALCULATOR, Theme.BLUE, CalculatorActivity.class);
        builtin(out, c, "Weather", Icons.SUN, Theme.PEACH, WeatherActivity.class);
        builtin(out, c, "Fitness", Icons.WALK, Theme.MINT, StepsActivity.class);
        builtin(out, c, "Compass", Icons.COMPASS, Theme.LILAC, CompassActivity.class);
        builtin(out, c, "Notes", Icons.NOTE, Theme.CREAM, NotesActivity.class);
        builtin(out, c, "Flashlight", Icons.FLASHLIGHT, Theme.LIME, FlashlightActivity.class);
        builtin(out, c, "Settings", Icons.SETTINGS, Theme.ROSE, SettingsActivity.class);

        Intent voice = new Intent(Intent.ACTION_VOICE_COMMAND);
        if (pm.resolveActivity(voice, 0) == null) voice = new Intent(Intent.ACTION_ASSIST);
        shortcut(out, pm, "Assistant", Icons.SPARK, Theme.LIME, voice);
        shortcut(out, pm, "Maps", Icons.LOCATION, Theme.MINT, new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")));
        shortcut(out, pm, "Browser", Icons.GLOBE, Theme.BLUE, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER));
        shortcut(out, pm, "Music", Icons.MUSIC, Theme.LILAC, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC));
        shortcut(out, pm, "Email", Icons.MAIL, Theme.PEACH, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL));
        shortcut(out, pm, "Files", Icons.FOLDER, Theme.CREAM, new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));

        List<App> others = new ArrayList<>();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName())) continue;
            App a = new App();
            a.label = String.valueOf(ri.loadLabel(pm));
            a.pkg = pkg;
            a.cn = new ComponentName(pkg, ri.activityInfo.name);
            a.key = a.cn.flattenToShortString();
            a.ri = ri;
            a.stock = stock.contains(pkg);
            others.add(a);
        }
        Collections.sort(others, (x, y) -> x.label.compareToIgnoreCase(y.label));
        out.addAll(others);
        return out;
    }

    // ---------- icons ----------
    public static Bitmap cached(App a, int px) { return CACHE.get(a.key + "@" + px); }

    public static void fetch(Context ctx, App a, int px, Runnable done) {
        if (a.ri == null) return;
        final Context c = ctx.getApplicationContext();
        POOL.execute(() -> {
            String k = a.key + "@" + px;
            if (CACHE.get(k) == null) {
                try {
                    Drawable d = a.ri.loadIcon(c.getPackageManager());
                    Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
                    Canvas cv = new Canvas(b);
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    p.setColor(Theme.SURFACE_HI);
                    cv.drawCircle(px / 2f, px / 2f, px / 2f, p);
                    Path clip = new Path();
                    clip.addCircle(px / 2f, px / 2f, px / 2f, Path.Direction.CW);
                    cv.save();
                    cv.clipPath(clip);
                    int inset = (d instanceof AdaptiveIconDrawable) ? 0 : Math.round(px * 0.16f);
                    d.setBounds(inset, inset, px - inset, px - inset);
                    d.draw(cv);
                    cv.restore();
                    CACHE.put(k, b);
                } catch (Exception ignored) {}
            }
            MAIN.post(done);
        });
    }
}
