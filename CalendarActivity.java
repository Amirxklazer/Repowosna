package com.wearlauncher;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Calendar: today's date up top, next 14 days of events below. */
public class CalendarActivity extends WearActivity {
    private LinearLayout col;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(UiKit.header(this, "Calendar", this::finish), new LinearLayout.LayoutParams(-1, -2));
        EdgeScrollView sv = new EdgeScrollView(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(this, 16);
        col.setPadding(pad, Theme.dp(this, 6), pad, Theme.dp(this, 40));
        sv.addView(col, new ViewGroup.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setRoot(root);
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        render();
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.bottomMargin = Theme.dp(this, 8);
        return l;
    }

    private void render() {
        col.removeAllViews();
        Date now = new Date();
        TextView dow = UiKit.text(this, new SimpleDateFormat("EEEE", Locale.getDefault()).format(now), 16, Theme.TEXT_DIM, Theme.MEDIUM);
        dow.setGravity(Gravity.CENTER);
        col.addView(dow, new LinearLayout.LayoutParams(-1, -2));
        TextView day = UiKit.text(this, new SimpleDateFormat("d", Locale.getDefault()).format(now), 72, Theme.ROSE, Theme.LIGHT);
        day.setGravity(Gravity.CENTER);
        col.addView(day, new LinearLayout.LayoutParams(-1, -2));
        TextView mon = UiKit.text(this, new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(now), 16, Theme.TEXT, Theme.MEDIUM);
        mon.setGravity(Gravity.CENTER);
        col.addView(mon, new LinearLayout.LayoutParams(-1, -2));
        col.addView(UiKit.spacer(this, 20));

        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            LinearLayout pill = UiKit.pill(this, Icons.CALENDAR, Theme.ROSE, "Allow calendar", "Tap to show your events");
            pill.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, 4));
            col.addView(pill, lp());
            return;
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();
        long end = start + 14L * 86400000L;
        Uri.Builder bld = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(bld, start);
        ContentUris.appendId(bld, end);
        int count = 0;
        String lastDay = "";
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
        SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
        long today0 = start;
        try (Cursor c = getContentResolver().query(bld.build(),
                new String[]{CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, "calendar_color"},
                null, null, "begin ASC")) {
            while (c != null && c.moveToNext()) {
                String title = c.getString(0);
                long begin = c.getLong(1), fin = c.getLong(2);
                boolean allDay = c.getInt(3) == 1;
                int color = c.getInt(4);
                if (allDay) {
                    begin -= TimeZone.getDefault().getOffset(begin);
                    fin -= TimeZone.getDefault().getOffset(fin);
                }
                String label;
                long diff = (begin - today0) / 86400000L;
                if (begin >= today0 && diff == 0) label = "Today";
                else if (begin >= today0 && diff == 1) label = "Tomorrow";
                else label = dayFmt.format(new Date(begin));
                if (!label.equals(lastDay)) {
                    lastDay = label;
                    TextView h = UiKit.text(this, label, 15, Theme.TEXT_DIM, Theme.MEDIUM);
                    h.setPadding(Theme.dp(this, 6), 0, 0, 0);
                    LinearLayout.LayoutParams hlp = UiKit.withTop(this, 10);
                    hlp.bottomMargin = Theme.dp(this, 8);
                    col.addView(h, hlp);
                }
                String sub = allDay ? "All day" : timeFmt.format(new Date(begin)) + " \u2013 " + timeFmt.format(new Date(fin));
                LinearLayout pill = UiKit.pill(this, null, 0, title == null || title.isEmpty() ? "(No title)" : title, sub);
                View bar = new View(this);
                bar.setBackground(Theme.round(color == 0 ? Theme.BLUE : (0xFF000000 | color), Theme.dpf(this, 3)));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Theme.dp(this, 6), Theme.dp(this, 38));
                blp.rightMargin = Theme.dp(this, 14);
                pill.addView(bar, 0, blp);
                col.addView(pill, lp());
                count++;
            }
        } catch (Exception ignored) {}
        if (count == 0) {
            TextView e = UiKit.text(this, "No upcoming events", 16, Theme.TEXT_DIM, Theme.MEDIUM);
            e.setGravity(Gravity.CENTER);
            col.addView(e, new LinearLayout.LayoutParams(-1, -2));
            col.addView(UiKit.spacer(this, 16));
        }
        TextView add = UiKit.bigButton(this, "New event", Theme.LIME, Theme.ON_PASTEL);
        add.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)); }
            catch (Exception ignored) {}
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = Theme.dp(this, 10);
        col.addView(add, alp);
    }
}
