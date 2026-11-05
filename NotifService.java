package com.wearlauncher;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/** Feeds the watch-style notification stream. User grants access once in system settings. */
public class NotifService extends NotificationListenerService {
    public static class Item {
        public String key, pkg, app, title, text;
        public long time;
        public Drawable icon;
        public PendingIntent intent;
        public boolean ongoing;
    }

    public static volatile int count = 0;
    public static final List<Item> items = new ArrayList<>();
    public static Runnable onChange;
    private static NotifService instance;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override public void onListenerConnected() {
        instance = this;
        refresh();
    }

    @Override public void onListenerDisconnected() {
        instance = null;
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) { refresh(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { refresh(); }

    private void refresh() {
        List<Item> fresh = new ArrayList<>();
        try {
            StatusBarNotification[] all = getActiveNotifications();
            PackageManager pm = getPackageManager();
            if (all != null) {
                for (StatusBarNotification s : all) {
                    Notification n = s.getNotification();
                    if (n == null) continue;
                    if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                    Item it = new Item();
                    it.key = s.getKey();
                    it.pkg = s.getPackageName();
                    it.time = s.getPostTime();
                    it.intent = n.contentIntent;
                    it.ongoing = s.isOngoing();
                    CharSequence t = n.extras.getCharSequence(Notification.EXTRA_TITLE);
                    CharSequence x = n.extras.getCharSequence(Notification.EXTRA_TEXT);
                    it.title = t == null ? "" : t.toString();
                    it.text = x == null ? "" : x.toString();
                    try {
                        ApplicationInfo ai = pm.getApplicationInfo(it.pkg, 0);
                        it.app = pm.getApplicationLabel(ai).toString();
                        it.icon = pm.getApplicationIcon(ai);
                    } catch (Exception e) {
                        it.app = it.pkg;
                    }
                    if (it.title.isEmpty() && it.text.isEmpty()) continue;
                    fresh.add(it);
                }
            }
        } catch (Exception ignored) {}
        java.util.Collections.sort(fresh, (a, b) -> Long.compare(b.time, a.time));
        MAIN.post(() -> {
            items.clear();
            items.addAll(fresh);
            int n = 0;
            for (Item i : fresh) if (!i.ongoing) n++;
            count = n;
            if (onChange != null) onChange.run();
        });
    }

    public static void clearAll() {
        if (instance != null) {
            try { instance.cancelAllNotifications(); } catch (Exception ignored) {}
        }
    }
}
