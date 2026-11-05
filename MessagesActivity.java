package com.wearlauncher;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SMS: conversation list, chat bubbles, send. Handles sms:/smsto: links. */
public class MessagesActivity extends WearActivity {
    private static class Msg { String address, body; long date; boolean out; }

    private FrameLayout holder, headerHolder;
    private String openAddress;           // null = list

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        headerHolder = new FrameLayout(this);
        root.addView(headerHolder, new LinearLayout.LayoutParams(-1, -2));
        holder = new FrameLayout(this);
        root.addView(holder, new LinearLayout.LayoutParams(-1, 0, 1f));
        setRoot(root);

        Uri d = getIntent().getData();
        if (d != null && ("sms".equals(d.getScheme()) || "smsto".equals(d.getScheme()))) {
            openAddress = d.getSchemeSpecificPart();
            if (openAddress != null && openAddress.contains("?")) openAddress = openAddress.substring(0, openAddress.indexOf('?'));
        }
        render();
    }

    @Override public void onBackPressed() {
        if (openAddress != null) { openAddress = null; render(); }
        else finish();
    }

    private void render() {
        headerHolder.removeAllViews();
        holder.removeAllViews();
        if (openAddress == null) {
            headerHolder.addView(UiKit.header(this, "Messages", this::finish));
            holder.addView(list());
        } else {
            headerHolder.addView(UiKit.header(this, name(openAddress), () -> { openAddress = null; render(); }));
            holder.addView(thread(openAddress));
        }
    }

    private boolean can(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    @Override public void onRequestPermissionsResult(int c, String[] p, int[] r) {
        super.onRequestPermissionsResult(c, p, r);
        render();
    }

    private String name(String address) {
        if (address == null) return "";
        if (!can(Manifest.permission.READ_CONTACTS)) return address;
        try (Cursor c = getContentResolver().query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)),
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return address;
    }

    private List<Msg> readAll() {
        List<Msg> out = new ArrayList<>();
        try (Cursor c = getContentResolver().query(Telephony.Sms.CONTENT_URI,
                new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE},
                null, null, Telephony.Sms.DATE + " DESC LIMIT 600")) {
            while (c != null && c.moveToNext()) {
                Msg m = new Msg();
                m.address = c.getString(0);
                m.body = c.getString(1);
                m.date = c.getLong(2);
                int type = c.getInt(3);
                m.out = type == Telephony.Sms.MESSAGE_TYPE_SENT || type == Telephony.Sms.MESSAGE_TYPE_OUTBOX
                        || type == Telephony.Sms.MESSAGE_TYPE_QUEUED;
                if (m.address != null) out.add(m);
            }
        } catch (Exception ignored) {}
        return out;
    }

    // ---------- conversation list ----------
    private View list() {
        EdgeScrollView sv = new EdgeScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(this, 16);
        col.setPadding(pad, Theme.dp(this, 6), pad, Theme.dp(this, 100));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));

        if (!can(Manifest.permission.READ_SMS)) {
            LinearLayout ask = UiKit.pill(this, Icons.MESSAGE, Theme.PEACH, "Allow access", "To read and send messages");
            ask.setOnClickListener(v -> requestPermissions(new String[]{
                    Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS}, 5));
            col.addView(ask, rowLp());
        } else {
            Map<String, Msg> latest = new LinkedHashMap<>();
            for (Msg m : readAll()) if (!latest.containsKey(m.address)) latest.put(m.address, m);
            if (latest.isEmpty()) {
                TextView e = UiKit.text(this, "No messages", 16, Theme.TEXT_DIM, Theme.MEDIUM);
                e.setGravity(Gravity.CENTER);
                col.addView(e, new LinearLayout.LayoutParams(-1, -2));
            }
            for (Msg m : latest.values()) {
                String title = name(m.address);
                LinearLayout row = UiKit.pill(this, null, 0, title, (m.out ? "You: " : "") + m.body);
                TextView av = UiKit.text(this, title.isEmpty() ? "#" : title.substring(0, 1).toUpperCase(), 18, Theme.ON_PASTEL, Theme.MEDIUM);
                av.setGravity(Gravity.CENTER);
                av.setBackground(Theme.circle(Theme.PASTELS[Math.abs(m.address.hashCode()) % Theme.PASTELS.length]));
                LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Theme.dp(this, 40), Theme.dp(this, 40));
                alp.rightMargin = Theme.dp(this, 14);
                row.addView(av, 0, alp);
                final String addr = m.address;
                row.setOnClickListener(v -> { openAddress = addr; render(); });
                col.addView(row, rowLp());
            }
        }
        FrameLayout f = new FrameLayout(this);
        f.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout fab = UiKit.roundButton(this, Icons.ADD, Theme.MINT, Theme.ON_PASTEL, 64);
        fab.setOnClickListener(v -> newMessage());
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Theme.dp(this, 64), Theme.dp(this, 64),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        flp.bottomMargin = Theme.dp(this, 20);
        f.addView(fab, flp);
        return f;
    }

    private void newMessage() {
        EditText to = new EditText(this);
        to.setHint("Number");
        to.setInputType(InputType.TYPE_CLASS_PHONE);
        to.setTextColor(Theme.TEXT);
        to.setHintTextColor(Theme.TEXT_DIM);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(Theme.dp(this, 24), Theme.dp(this, 8), Theme.dp(this, 24), 0);
        box.addView(to);
        new androidx.appcompat.app.AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("New message").setView(box)
                .setPositiveButton("Next", (d, w) -> {
                    String n = to.getText().toString().trim();
                    if (!n.isEmpty()) { openAddress = n; render(); }
                }).setNegativeButton("Cancel", null).show();
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Theme.dp(this, 8);
        return lp;
    }

    // ---------- thread ----------
    private View thread(String address) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);

        EdgeScrollView sv = new EdgeScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(this, 14);
        col.setPadding(pad, Theme.dp(this, 6), pad, Theme.dp(this, 10));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        l.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        List<Msg> mine = new ArrayList<>();
        String key = digits(address);
        if (can(Manifest.permission.READ_SMS)) {
            for (Msg m : readAll()) if (digits(m.address).endsWith(key) || key.endsWith(digits(m.address))) mine.add(m);
        }
        java.util.Collections.reverse(mine);       // oldest first
        for (Msg m : mine) col.addView(bubble(m));
        sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Theme.dp(this, 12), Theme.dp(this, 6), Theme.dp(this, 12), Theme.dp(this, 10));
        EditText input = new EditText(this);
        input.setHint("Message");
        input.setHintTextColor(Theme.TEXT_DIM);
        input.setTextColor(Theme.TEXT);
        input.setTextSize(16);
        input.setBackground(Theme.round(Theme.SURFACE, Theme.dpf(this, 26)));
        input.setPadding(Theme.dp(this, 18), Theme.dp(this, 12), Theme.dp(this, 18), Theme.dp(this, 12));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(4);
        FrameLayout send = UiKit.roundButton(this, Icons.SEND, Theme.MINT, Theme.ON_PASTEL, 48);
        send.setOnClickListener(v -> {
            String t = input.getText().toString().trim();
            if (t.isEmpty()) return;
            send(address, t);
            input.setText("");
            Msg m = new Msg();
            m.address = address; m.body = t; m.out = true; m.date = System.currentTimeMillis();
            col.addView(bubble(m));
            sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
        });
        bar.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Theme.dp(this, 48), Theme.dp(this, 48));
        slp.leftMargin = Theme.dp(this, 8);
        bar.addView(send, slp);
        l.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        return l;
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    private View bubble(Msg m) {
        TextView t = UiKit.text(this, m.body == null ? "" : m.body, 15, m.out ? Theme.ON_PASTEL : Theme.TEXT, Theme.REGULAR);
        t.setBackground(Theme.round(m.out ? Theme.BLUE : Theme.SURFACE, Theme.dpf(this, 20)));
        t.setPadding(Theme.dp(this, 14), Theme.dp(this, 10), Theme.dp(this, 14), Theme.dp(this, 10));
        t.setMaxWidth(Theme.dp(this, 270));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.gravity = m.out ? Gravity.END : Gravity.START;
        lp.bottomMargin = Theme.dp(this, 6);
        t.setLayoutParams(lp);
        return t;
    }

    private void send(String to, String text) {
        if (!can(Manifest.permission.SEND_SMS)) {
            requestPermissions(new String[]{Manifest.permission.SEND_SMS}, 6);
            return;
        }
        try {
            SmsManager sm = android.os.Build.VERSION.SDK_INT >= 31
                    ? getSystemService(SmsManager.class) : SmsManager.getDefault();
            ArrayList<String> parts = sm.divideMessage(text);
            sm.sendMultipartTextMessage(to, null, parts, null, null);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Couldn't send", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
