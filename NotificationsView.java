package com.wearlauncher;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.NotificationManagerCompat;

/** The Wear OS "notification stream": rounded cards + a big pastel Clear all pill. */
public class NotificationsView extends FrameLayout {
    private final Host host;
    private final EdgeScrollView scroll;
    private final LinearLayout col;

    public NotificationsView(Context c, Host host) {
        super(c);
        this.host = host;
        setBackgroundColor(Theme.BG);
        scroll = new EdgeScrollView(c);
        col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(c, 16);
        col.setPadding(pad, Theme.dp(c, 12), pad, Theme.dp(c, 32));
        scroll.addView(col, new ViewGroup.LayoutParams(-1, -2));
        addView(scroll, new LayoutParams(-1, -1));
        NotifService.onChange = this::rebuild;
        rebuild();
    }

    public EdgeScrollView scroller() { return scroll; }

    public void rebuild() {
        Context c = getContext();
        col.removeAllViews();
        TextView title = UiKit.text(c, "Notifications", 20, Theme.TEXT, Theme.MEDIUM);
        title.setGravity(Gravity.CENTER);
        col.addView(title, new LinearLayout.LayoutParams(-1, -2));
        col.addView(UiKit.spacer(c, 14));

        boolean granted = NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.getPackageName());
        if (!granted) {
            LinearLayout pill = UiKit.pill(c, Icons.BELL, Theme.PEACH, "Allow access", "Tap to let Wear Launcher show notifications");
            pill.setOnClickListener(v -> host.startApp(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
            col.addView(pill, new LinearLayout.LayoutParams(-1, -2));
            col.addView(UiKit.spacer(c, 10));
        }

        if (NotifService.items.isEmpty()) {
            LinearLayout empty = new LinearLayout(c);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Theme.dp(c, 80), 0, 0);
            empty.addView(UiKit.bubble(c, Icons.BELL_OFF, Theme.LILAC, 72));
            TextView t = UiKit.text(c, "No notifications", 17, Theme.TEXT_DIM, Theme.MEDIUM);
            empty.addView(t, UiKit.withTop(c, 16));
            col.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        for (NotifService.Item it : NotifService.items) {
            col.addView(card(c, it), cardLp(c));
        }
        TextView clear = UiKit.bigButton(c, "Clear all", Theme.LILAC, Theme.ON_PASTEL);
        clear.setOnClickListener(v -> {
            NotifService.clearAll();
            host.closePanel();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Theme.dp(c, 8);
        col.addView(clear, lp);
    }

    private LinearLayout.LayoutParams cardLp(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Theme.dp(c, 8);
        return lp;
    }

    private View card(Context c, NotifService.Item it) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Theme.dp(c, 16), Theme.dp(c, 14), Theme.dp(c, 16), Theme.dp(c, 14));
        card.setBackground(Theme.round(Theme.SURFACE, Theme.dpf(c, 26)));

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (it.icon != null) {
            android.widget.ImageView iv = new android.widget.ImageView(c);
            iv.setImageDrawable(it.icon);
            top.addView(iv, new LinearLayout.LayoutParams(Theme.dp(c, 24), Theme.dp(c, 24)));
        }
        TextView app = UiKit.text(c, it.app == null ? "" : it.app, 13, Theme.TEXT_DIM, Theme.MEDIUM);
        app.setSingleLine(true);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(0, -2, 1f);
        alp.leftMargin = Theme.dp(c, 10);
        top.addView(app, alp);
        top.addView(UiKit.text(c, ago(it.time), 13, Theme.TEXT_DIM, Theme.REGULAR));
        card.addView(top, new LinearLayout.LayoutParams(-1, -2));

        if (!it.title.isEmpty()) {
            TextView t = UiKit.text(c, it.title, 17, Theme.TEXT, Theme.MEDIUM);
            t.setMaxLines(2);
            card.addView(t, UiKit.withTop(c, 10));
        }
        if (!it.text.isEmpty()) {
            TextView t = UiKit.text(c, it.text, 14, Theme.TEXT_DIM, Theme.REGULAR);
            t.setMaxLines(3);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(t, UiKit.withTop(c, 4));
        }
        Theme.pressable(card);
        card.setOnClickListener(v -> {
            host.closePanel();
            try { if (it.intent != null) it.intent.send(); } catch (Exception ignored) {}
        });
        return card;
    }

    private static String ago(long t) {
        long m = Math.max(0, (System.currentTimeMillis() - t) / 60000);
        if (m < 1) return "now";
        if (m < 60) return m + "m";
        long h = m / 60;
        if (h < 24) return h + "h";
        return (h / 24) + "d";
    }
}
