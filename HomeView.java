package com.wearlauncher;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The home screen: Wear OS launcher resized for a phone (no watch face).
 * Top bar (clock + buttons), Recents pills, then the honeycomb / list of every app.
 * One RecyclerView, icons load in the background -> smooth.
 */
public class HomeView extends FrameLayout {
    private static final int T_TITLE = 0, T_PILL = 1, T_ROW = 2, T_BANNER = 3, T_EMPTY = 4;

    private static class Item {
        int type;
        AppRepo.App app;
        List<AppRepo.App> row;
        String title;
        boolean toggle;
        int rowIndex;
    }

    private final Host host;
    private final RecyclerView list;
    private final TextView timeTv, dateTv;
    private final LinearLayout searchRow;
    private final EditText searchEt;
    private final View badge;
    private final int iconPx, gap;
    private final Adapter adapter = new Adapter();
    private List<Item> items = new ArrayList<>();
    private String query = "";
    private boolean showBanner;
    private int appsIndex = 0;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateHeader();
            postDelayed(this, 20000);
        }
    };

    public HomeView(Context c, Host host) {
        super(c);
        this.host = host;
        setBackgroundColor(Theme.BG);
        int sw = c.getResources().getDisplayMetrics().widthPixels;
        gap = Theme.dp(c, 12);
        iconPx = Math.min(Theme.dp(c, 84), (sw - Theme.dp(c, 32)) / 4 - gap);

        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);

        // ----- top bar -----
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Theme.dp(c, 20), Theme.dp(c, 10), Theme.dp(c, 12), Theme.dp(c, 8));
        LinearLayout tcol = new LinearLayout(c);
        tcol.setOrientation(LinearLayout.VERTICAL);
        timeTv = UiKit.text(c, "", 32, Theme.TEXT, Theme.LIGHT);
        dateTv = UiKit.text(c, "", 13, Theme.TEXT_DIM, Theme.REGULAR);
        tcol.addView(timeTv);
        tcol.addView(dateTv, UiKit.withTop(c, 2));
        bar.addView(tcol, new LinearLayout.LayoutParams(0, -2, 1f));

        FrameLayout bSearch = button(bar, Icons.SEARCH, this::toggleSearch);
        FrameLayout bBell = button(bar, Icons.BELL, () -> host.openPanel("notifs"));
        button(bar, Icons.TUNE, () -> host.openPanel("qs"));
        button(bar, Icons.WIDGETS, () -> host.openPanel("tiles"));
        badge = new View(c);
        badge.setBackground(Theme.circle(Theme.PEACH));
        badge.setVisibility(GONE);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Theme.dp(c, 11), Theme.dp(c, 11), Gravity.TOP | Gravity.END);
        blp.setMargins(0, Theme.dp(c, 9), Theme.dp(c, 9), 0);
        bBell.addView(badge, blp);
        content.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        // ----- search row -----
        searchRow = new LinearLayout(c);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackground(Theme.round(Theme.SURFACE, Theme.dpf(c, 28)));
        searchRow.setPadding(Theme.dp(c, 16), 0, Theme.dp(c, 6), 0);
        searchRow.setVisibility(GONE);
        searchRow.addView(Icons.view(c, Icons.SEARCH, Theme.TEXT_DIM, 22));
        searchEt = new EditText(c);
        searchEt.setBackground(null);
        searchEt.setHint("Search apps");
        searchEt.setHintTextColor(Theme.TEXT_DIM);
        searchEt.setTextColor(Theme.TEXT);
        searchEt.setTextSize(17);
        searchEt.setSingleLine(true);
        searchEt.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchEt.setPadding(Theme.dp(c, 12), Theme.dp(c, 14), Theme.dp(c, 8), Theme.dp(c, 14));
        searchEt.setOnEditorActionListener((v, a, e) -> { hideIme(); return true; });
        searchEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable e) {
                query = e.toString();
                rebuildItems();
            }
        });
        searchRow.addView(searchEt, new LinearLayout.LayoutParams(0, -2, 1f));
        FrameLayout clear = UiKit.roundButton(c, Icons.CLOSE, Theme.SURFACE_HI, Theme.TEXT, 36);
        clear.setOnClickListener(v -> closeSearch());
        searchRow.addView(clear);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(Theme.dp(c, 16), Theme.dp(c, 4), Theme.dp(c, 16), Theme.dp(c, 4));
        content.addView(searchRow, slp);

        // ----- list -----
        list = new RecyclerView(c);
        list.setLayoutManager(new LinearLayoutManager(c));
        list.setAdapter(adapter);
        list.setItemAnimator(null);
        list.setOverScrollMode(OVER_SCROLL_NEVER);
        list.setClipToPadding(false);
        list.setPadding(Theme.dp(c, 16), Theme.dp(c, 4), Theme.dp(c, 16), Theme.dp(c, 40));
        list.setItemViewCacheSize(12);
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        addView(content, new LayoutParams(-1, -1));
        rebuildItems();
        reload();
    }

    private FrameLayout button(LinearLayout bar, String icon, Runnable r) {
        Context c = getContext();
        FrameLayout b = UiKit.roundButton(c, icon, Theme.SURFACE, Theme.TEXT, 44);
        ((LinearLayout.LayoutParams) b.getLayoutParams()).leftMargin = Theme.dp(c, 8);
        b.setOnClickListener(v -> r.run());
        bar.addView(b);
        return b;
    }

    // ---------- public api ----------
    public void reload() { AppRepo.load(getContext(), this::rebuildItems); }

    public void refresh() {
        updateHeader();
        if (AppRepo.isLoaded()) rebuildItems(); else reload();
    }

    public void setBadge(int n) { badge.setVisibility(n > 0 ? VISIBLE : GONE); }

    public void setShowBanner(boolean b) {
        if (b == showBanner) return;
        showBanner = b;
        rebuildItems();
    }

    public void scrollToApps() {
        ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(appsIndex, 0);
    }

    public boolean canScrollUp() { return list.canScrollVertically(-1); }

    public boolean onBack() {
        if (searchRow.getVisibility() == VISIBLE) { closeSearch(); return true; }
        if (list.canScrollVertically(-1)) { list.scrollToPosition(0); return true; }
        return false;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    // ---------- header ----------
    private void updateHeader() {
        Context c = getContext();
        Date d = new Date();
        timeTv.setText(new SimpleDateFormat(Store.is24h(c) ? "HH:mm" : "h:mm", Locale.getDefault()).format(d));
        BatteryManager bm = (BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
        int lvl = bm == null ? 100 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        dateTv.setText(new SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(d) + "  \u00B7  " + lvl + "%");
    }

    // ---------- search ----------
    private void toggleSearch() {
        if (searchRow.getVisibility() == VISIBLE) { closeSearch(); return; }
        searchRow.setVisibility(VISIBLE);
        searchEt.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(searchEt, InputMethodManager.SHOW_IMPLICIT);
    }

    private void closeSearch() {
        searchEt.setText("");
        searchRow.setVisibility(GONE);
        query = "";
        hideIme();
        rebuildItems();
    }

    private void hideIme() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchEt.getWindowToken(), 0);
    }

    // ---------- data ----------
    private Item title(String t, boolean toggle) {
        Item i = new Item();
        i.type = T_TITLE; i.title = t; i.toggle = toggle;
        return i;
    }

    private void rebuildItems() {
        Context c = getContext();
        List<Item> out = new ArrayList<>();
        if (!AppRepo.isLoaded()) {
            Item e = new Item(); e.type = T_EMPTY; e.title = "Loading apps\u2026";
            out.add(e);
            items = out;
            adapter.notifyDataSetChanged();
            return;
        }
        boolean hideStock = Store.hideStock(c);
        Set<String> hid = Store.hidden(c);
        String q = query.trim().toLowerCase(Locale.getDefault());
        List<AppRepo.App> vis = new ArrayList<>();
        for (AppRepo.App a : AppRepo.all()) {
            if (hid.contains(a.key)) continue;
            if (q.isEmpty()) { if (hideStock && a.stock) continue; }
            else if (!a.label.toLowerCase(Locale.getDefault()).contains(q)) continue;
            vis.add(a);
        }

        if (!q.isEmpty()) {
            if (vis.isEmpty()) {
                Item e = new Item(); e.type = T_EMPTY; e.title = "No apps found";
                out.add(e);
            }
            for (AppRepo.App a : vis) {
                Item i = new Item(); i.type = T_PILL; i.app = a;
                out.add(i);
            }
            appsIndex = 0;
        } else {
            if (showBanner) {
                Item b = new Item(); b.type = T_BANNER;
                out.add(b);
            }
            // recents
            String rec = Store.recents(c);
            List<AppRepo.App> recents = new ArrayList<>();
            if (!rec.isEmpty()) {
                for (String key : rec.split(",")) {
                    for (AppRepo.App a : vis) if (a.key.equals(key)) { recents.add(a); break; }
                }
            }
            if (!recents.isEmpty()) {
                out.add(title("Recents", false));
                for (AppRepo.App a : recents) {
                    Item i = new Item(); i.type = T_PILL; i.app = a;
                    out.add(i);
                }
            }
            appsIndex = out.size();
            boolean grid = Store.gridView(c);
            out.add(title("Apps", true));
            if (grid) {
                int idx = 0, r = 0;
                while (idx < vis.size()) {
                    int n = (r % 2 == 0) ? 4 : 3;
                    Item it = new Item();
                    it.type = T_ROW;
                    it.row = new ArrayList<>(vis.subList(idx, Math.min(vis.size(), idx + n)));
                    it.rowIndex = r;
                    out.add(it);
                    idx += n;
                    r++;
                }
            } else {
                for (AppRepo.App a : vis) {
                    Item i = new Item(); i.type = T_PILL; i.app = a;
                    out.add(i);
                }
            }
        }
        items = out;
        adapter.notifyDataSetChanged();
    }

    // ---------- actions ----------
    private void launch(AppRepo.App a) {
        Store.pushRecent(getContext(), a.key);
        host.startApp(a.launchIntent());
    }

    private void sheet(AppRepo.App a) {
        final Context c = getContext();
        final Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout wrap = new FrameLayout(c);
        wrap.setPadding(Theme.dp(c, 10), Theme.dp(c, 10), Theme.dp(c, 10), Theme.dp(c, 10));
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(Theme.dp(c, 16), Theme.dp(c, 18), Theme.dp(c, 16), Theme.dp(c, 12));
        l.setBackground(Theme.round(0xFF121214, Theme.dpf(c, 32)));
        TextView t = UiKit.text(c, a.label, 20, Theme.TEXT, Theme.MEDIUM);
        t.setGravity(Gravity.CENTER);
        l.addView(t, new LinearLayout.LayoutParams(-1, -2));

        String pkg = a.pkg != null ? a.pkg : c.getPackageName();
        addSheetPill(l, Icons.INFO, Theme.BLUE, "App info", () -> {
            d.dismiss();
            host.startApp(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
        });
        if (!a.builtin) {
            addSheetPill(l, Icons.DELETE, Theme.PEACH, "Uninstall", () -> {
                d.dismiss();
                host.startApp(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
            });
        }
        addSheetPill(l, Icons.CLOSE, Theme.LILAC, "Hide from home", () -> {
            d.dismiss();
            Store.hide(c, a.key);
            rebuildItems();
        });
        wrap.addView(l, new FrameLayout.LayoutParams(-1, -2));
        d.setContentView(wrap);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
        }
        d.show();
    }

    private void addSheetPill(LinearLayout l, String icon, int pastel, String label, Runnable r) {
        Context c = getContext();
        LinearLayout p = UiKit.pill(c, icon, pastel, label, null);
        p.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Theme.dp(c, 8);
        l.addView(p, lp);
    }

    // ---------- adapter ----------
    private static class VH extends RecyclerView.ViewHolder {
        VH(View v) { super(v); }
    }

    private class TitleView extends LinearLayout {
        final TextView t;
        final View toggleBtn;
        final Icons.IconView toggleIcon;
        TitleView(Context c) {
            super(c);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(Theme.dp(c, 6), 0, 0, 0);
            t = UiKit.text(c, "", 20, Theme.TEXT, Theme.MEDIUM);
            addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            FrameLayout b = UiKit.roundButton(c, Icons.GRID, Theme.SURFACE, Theme.TEXT, 40);
            toggleBtn = b;
            toggleIcon = (Icons.IconView) b.getChildAt(0);
            b.setOnClickListener(v -> {
                Store.setGridView(getContext(), !Store.gridView(getContext()));
                rebuildItems();
            });
            addView(b);
        }
    }

    private class PillRow extends LinearLayout {
        final AppIcon icon;
        final TextView label;
        PillRow(Context c) {
            super(c);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(Theme.dp(c, 12), Theme.dp(c, 8), Theme.dp(c, 16), Theme.dp(c, 8));
            setBackground(Theme.round(Theme.SURFACE, Theme.dpf(c, 32)));
            icon = new AppIcon(c);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-2, -2);
            ilp.rightMargin = Theme.dp(c, 14);
            addView(icon, ilp);
            label = UiKit.text(c, "", 17, Theme.TEXT, Theme.MEDIUM);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
            Theme.pressable(this);
            setOnClickListener(v -> { if (icon.app() != null) launch(icon.app()); });
            setOnLongClickListener(v -> { if (icon.app() != null) sheet(icon.app()); return true; });
        }
        void bind(AppRepo.App a) {
            icon.bind(a, Theme.dp(getContext(), 44));
            label.setText(a.label);
        }
    }

    private class HoneyRow extends LinearLayout {
        final AppIcon[] icons = new AppIcon[4];
        HoneyRow(Context c) {
            super(c);
            setGravity(Gravity.CENTER);
            for (int k = 0; k < 4; k++) {
                final AppIcon ic = new AppIcon(c);
                icons[k] = ic;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.leftMargin = lp.rightMargin = gap / 2;
                Theme.pressable(ic);
                ic.setOnClickListener(v -> { if (ic.app() != null) launch(ic.app()); });
                ic.setOnLongClickListener(v -> { if (ic.app() != null) sheet(ic.app()); return true; });
                addView(ic, lp);
            }
        }
        void bind(List<AppRepo.App> row) {
            for (int k = 0; k < 4; k++) {
                if (k < row.size()) {
                    icons[k].setVisibility(VISIBLE);
                    icons[k].bind(row.get(k), iconPx);
                } else icons[k].setVisibility(GONE);
            }
        }
    }

    private class Adapter extends RecyclerView.Adapter<VH> {
        @Override public int getItemCount() { return items.size(); }
        @Override public int getItemViewType(int p) { return items.get(p).type; }

        @Override public VH onCreateViewHolder(ViewGroup parent, int type) {
            Context c = parent.getContext();
            View v;
            switch (type) {
                case T_TITLE: v = new TitleView(c); break;
                case T_PILL: v = new PillRow(c); break;
                case T_ROW: v = new HoneyRow(c); break;
                case T_BANNER: {
                    TextView b = UiKit.bigButton(c, "Set as home app", Theme.BLUE, Theme.ON_PASTEL);
                    b.setOnClickListener(x -> host.requestHome());
                    v = b;
                    break;
                }
                default: {
                    TextView e = UiKit.text(c, "", 16, Theme.TEXT_DIM, Theme.MEDIUM);
                    e.setGravity(Gravity.CENTER);
                    e.setPadding(0, Theme.dp(c, 60), 0, 0);
                    v = e;
                }
            }
            v.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new VH(v);
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            Item it = items.get(pos);
            Context c = h.itemView.getContext();
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) h.itemView.getLayoutParams();
            lp.topMargin = 0;
            lp.bottomMargin = 0;
            switch (it.type) {
                case T_TITLE: {
                    TitleView tv = (TitleView) h.itemView;
                    tv.t.setText(it.title);
                    tv.toggleBtn.setVisibility(it.toggle ? VISIBLE : GONE);
                    tv.toggleIcon.setPath(Store.gridView(c) ? Icons.LIST : Icons.GRID);
                    lp.topMargin = Theme.dp(c, 10);
                    lp.bottomMargin = Theme.dp(c, 8);
                    break;
                }
                case T_PILL:
                    ((PillRow) h.itemView).bind(it.app);
                    lp.bottomMargin = Theme.dp(c, 8);
                    break;
                case T_ROW:
                    ((HoneyRow) h.itemView).bind(it.row);
                    lp.topMargin = it.rowIndex > 0 ? -Theme.dp(c, 6) : Theme.dp(c, 2);
                    break;
                case T_BANNER:
                    lp.bottomMargin = Theme.dp(c, 8);
                    break;
                default:
                    ((TextView) h.itemView).setText(it.title);
            }
            h.itemView.setLayoutParams(lp);
        }
    }
}
