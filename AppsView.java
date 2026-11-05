package com.wearlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Recents + all apps. Honeycomb of round icons, or a list, switchable. */
public class AppsView extends FrameLayout {
    private static class App {
        String label;
        ComponentName cn;
        Drawable icon;      // null for our built-ins
        String glyph;       // svg path for built-ins
        int pastel;
        boolean builtin;
    }

    private final Host host;
    private final EdgeScrollView scroll;
    private final LinearLayout col;
    private final List<App> apps = new ArrayList<>();
    private boolean showAll = false;

    public AppsView(Context c, Host host) {
        super(c);
        this.host = host;
        setBackgroundColor(Theme.BG);
        scroll = new EdgeScrollView(c);
        col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Theme.dp(c, 16);
        col.setPadding(pad, Theme.dp(c, 12), pad, Theme.dp(c, 36));
        scroll.addView(col, new ViewGroup.LayoutParams(-1, -2));
        addView(scroll, new LayoutParams(-1, -1));
    }

    public EdgeScrollView scroller() { return scroll; }

    /** Called when the panel opens. Reloads installed apps. */
    public void open() {
        showAll = false;
        load();
        rebuild();
        scroll.scrollTo(0, 0);
    }

    /** Back inside the panel: All apps -> Recents. Returns true if it handled it. */
    public boolean onBack() {
        if (showAll) {
            showAll = false;
            rebuild();
            return true;
        }
        return false;
    }

    private void load() {
        Context c = getContext();
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        apps.clear();
        List<App> builtins = new ArrayList<>();
        List<App> others = new ArrayList<>();
        for (ResolveInfo ri : ris) {
            String cls = ri.activityInfo.name;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName())) {
                App a = builtin(cls);
                if (a != null) builtins.add(a);
                continue;
            }
            App a = new App();
            a.label = String.valueOf(ri.loadLabel(pm));
            a.cn = new ComponentName(pkg, cls);
            a.icon = ri.loadIcon(pm);
            others.add(a);
        }
        Collections.sort(builtins, (x, y) -> order(x.cn.getShortClassName()) - order(y.cn.getShortClassName()));
        Collections.sort(others, (x, y) -> x.label.compareToIgnoreCase(y.label));
        apps.addAll(builtins);
        apps.addAll(others);
    }

    private static final String[] ORDER = {".PhoneActivity", ".MessagesActivity", ".CameraActivity",
            ".GalleryActivity", ".ClockActivity", ".FlashlightActivity", ".SettingsActivity"};

    private static int order(String shortCls) {
        for (int i = 0; i < ORDER.length; i++) if (ORDER[i].equals(shortCls)) return i;
        return 99;
    }

    private App builtin(String cls) {
        App a = new App();
        a.builtin = true;
        a.cn = new ComponentName(getContext().getPackageName(), cls);
        String s = a.cn.getShortClassName();
        switch (s) {
            case ".PhoneActivity": a.label = "Phone"; a.glyph = Icons.PHONE; a.pastel = Theme.BLUE; break;
            case ".MessagesActivity": a.label = "Messages"; a.glyph = Icons.MESSAGE; a.pastel = Theme.MINT; break;
            case ".CameraActivity": a.label = "Camera"; a.glyph = Icons.CAMERA; a.pastel = Theme.LILAC; break;
            case ".GalleryActivity": a.label = "Gallery"; a.glyph = Icons.PHOTO; a.pastel = Theme.PEACH; break;
            case ".ClockActivity": a.label = "Clock"; a.glyph = Icons.TIMER; a.pastel = Theme.CREAM; break;
            case ".FlashlightActivity": a.label = "Flashlight"; a.glyph = Icons.FLASHLIGHT; a.pastel = Theme.LIME; break;
            case ".SettingsActivity": a.label = "Settings"; a.glyph = Icons.SETTINGS; a.pastel = Theme.ROSE; break;
            default: return null;   // LauncherActivity etc.
        }
        return a;
    }

    private void launch(App a) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(a.cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Store.pushRecent(getContext(), a.cn.flattenToShortString());
        host.startApp(i);
    }

    private View icon(App a, int sizeDp) {
        Context c = getContext();
        if (a.builtin) return UiKit.bubble(c, a.glyph, a.pastel, sizeDp);
        FrameLayout f = new FrameLayout(c);
        int px = Theme.dp(c, sizeDp);
        f.setLayoutParams(new LinearLayout.LayoutParams(px, px));
        f.setBackground(Theme.circle(Theme.SURFACE_HI));
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(a.icon);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setClipToOutline(true);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
        });
        int inset = Math.round(px * 0.06f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px - 2 * inset, px - 2 * inset);
        lp.leftMargin = inset; lp.topMargin = inset;
        f.addView(iv, lp);
        return f;
    }

    private void rebuild() {
        Context c = getContext();
        col.removeAllViews();
        if (!showAll) recents(c); else all(c);
    }

    private App find(String flat) {
        ComponentName cn = ComponentName.unflattenFromString(flat);
        if (cn == null) return null;
        for (App a : apps) if (a.cn.equals(cn)) return a;
        return null;
    }

    private void recents(Context c) {
        TextView t = UiKit.text(c, "Recents", 20, Theme.TEXT, Theme.MEDIUM);
        col.addView(t, new LinearLayout.LayoutParams(-2, -2));
        col.addView(UiKit.spacer(c, 16));
        String rec = Store.recents(c);
        int shown = 0;
        if (!rec.isEmpty()) {
            for (String flat : rec.split(",")) {
                App a = find(flat);
                if (a == null) continue;
                LinearLayout pill = pillFor(c, a);
                col.addView(pill, rowLp(c));
                shown++;
            }
        }
        if (shown == 0) {
            TextView e = UiKit.text(c, "Nothing recent yet", 15, Theme.TEXT_DIM, Theme.REGULAR);
            col.addView(e, UiKit.withTop(c, 8));
            col.addView(UiKit.spacer(c, 20));
        }
        TextView all = UiKit.bigButton(c, "All apps", Theme.BLUE, Theme.ON_PASTEL);
        all.setOnClickListener(v -> { showAll = true; rebuild(); scroll.scrollTo(0, 0); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Theme.dp(c, 10);
        col.addView(all, lp);
    }

    private LinearLayout pillFor(Context c, App a) {
        LinearLayout pill = UiKit.pill(c, a.builtin ? a.glyph : null, a.pastel, a.label, null);
        if (!a.builtin) {
            View ic = icon(a, 40);
            ((LinearLayout.LayoutParams) ic.getLayoutParams()).rightMargin = Theme.dp(c, 14);
            pill.addView(ic, 0);
        }
        pill.setOnClickListener(v -> launch(a));
        return pill;
    }

    private LinearLayout.LayoutParams rowLp(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Theme.dp(c, 8);
        return lp;
    }

    private void all(Context c) {
        boolean grid = Store.gridView(c);
        TextView t = UiKit.text(c, "Apps", 20, Theme.TEXT, Theme.MEDIUM);
        col.addView(t, new LinearLayout.LayoutParams(-2, -2));
        col.addView(UiKit.spacer(c, 16));
        if (grid) honeycomb(c); else list(c);
        TextView toggle = UiKit.bigButton(c, grid ? "List view" : "Grid view", Theme.SURFACE_HI, Theme.TEXT);
        toggle.setOnClickListener(v -> {
            Store.setGridView(c, !grid);
            rebuild();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Theme.dp(c, 16);
        col.addView(toggle, lp);
    }

    private void honeycomb(Context c) {
        int size = 66, gap = 10;
        int i = 0, row = 0;
        while (i < apps.size()) {
            int n = (row % 2 == 0) ? 4 : 3;
            LinearLayout r = new LinearLayout(c);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER);
            for (int k = 0; k < n && i < apps.size(); k++, i++) {
                final App a = apps.get(i);
                View v = icon(a, size);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(c, size), Theme.dp(c, size));
                lp.leftMargin = lp.rightMargin = Theme.dp(c, gap / 2f);
                Theme.pressable(v);
                v.setOnClickListener(x -> launch(a));
                v.setOnLongClickListener(x -> {
                    Toast.makeText(c, a.label, Toast.LENGTH_SHORT).show();
                    return true;
                });
                r.addView(v, lp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            if (row > 0) rlp.topMargin = -Theme.dp(c, 6);
            col.addView(r, rlp);
            row++;
        }
    }

    private void list(Context c) {
        for (App a : apps) col.addView(pillFor(c, a), rowLp(c));
    }
}
