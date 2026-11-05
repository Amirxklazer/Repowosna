package com.wearlauncher;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Dialer: keypad, recents, contacts. Handles tel: links. */
public class PhoneActivity extends WearActivity {
    private FrameLayout content;
    private TextView tabKeys, tabRecents, tabContacts;
    private final StringBuilder number = new StringBuilder();
    private TextView numberView;
    private int tab = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(UiKit.header(this, "Phone", this::finish), new LinearLayout.LayoutParams(-1, -2));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setBackground(Theme.round(Theme.SURFACE, Theme.dpf(this, 24)));
        int tp = Theme.dp(this, 4);
        tabs.setPadding(tp, tp, tp, tp);
        tabKeys = tab("Keypad");
        tabRecents = tab("Recents");
        tabContacts = tab("Contacts");
        tabKeys.setOnClickListener(v -> select(0));
        tabRecents.setOnClickListener(v -> select(1));
        tabContacts.setOnClickListener(v -> select(2));
        int tw = Theme.dp(this, 100);
        tabs.addView(tabKeys, new LinearLayout.LayoutParams(tw, -2));
        tabs.addView(tabRecents, new LinearLayout.LayoutParams(tw, -2));
        tabs.addView(tabContacts, new LinearLayout.LayoutParams(tw, -2));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = Theme.dp(this, 6);
        root.addView(tabs, tlp);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        setRoot(root);

        Uri d = getIntent().getData();
        if (d != null && "tel".equals(d.getScheme())) number.append(d.getSchemeSpecificPart());
        select(0);
    }

    private TextView tab(String s) {
        TextView t = UiKit.text(this, s, 14, Theme.TEXT_DIM, Theme.MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Theme.dp(this, 10), 0, Theme.dp(this, 10));
        return t;
    }

    private void select(int i) {
        tab = i;
        TextView[] ts = {tabKeys, tabRecents, tabContacts};
        for (int k = 0; k < 3; k++) {
            ts[k].setBackground(k == i ? Theme.round(Theme.BLUE, Theme.dpf(this, 20)) : null);
            ts[k].setTextColor(k == i ? Theme.ON_PASTEL : Theme.TEXT_DIM);
        }
        content.removeAllViews();
        if (i == 0) content.addView(keypad());
        else if (i == 1) content.addView(recents());
        else content.addView(contacts());
    }

    // ---------- keypad ----------
    private View keypad() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(Theme.dp(this, 20), Theme.dp(this, 8), Theme.dp(this, 20), Theme.dp(this, 20));

        FrameLayout top = new FrameLayout(this);
        numberView = UiKit.text(this, number.toString(), 32, Theme.TEXT, Theme.LIGHT);
        numberView.setSingleLine(true);
        numberView.setGravity(Gravity.CENTER);
        top.addView(numberView, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        FrameLayout del = UiKit.roundButton(this, Icons.BACKSPACE, 0x00000000, Theme.TEXT_DIM, 48);
        del.setOnClickListener(v -> { if (number.length() > 0) number.deleteCharAt(number.length() - 1); refreshNumber(); });
        del.setOnLongClickListener(v -> { number.setLength(0); refreshNumber(); return true; });
        top.addView(del, new FrameLayout.LayoutParams(Theme.dp(this, 48), Theme.dp(this, 48), Gravity.END | Gravity.CENTER_VERTICAL));
        l.addView(top, new LinearLayout.LayoutParams(-1, Theme.dp(this, 56)));

        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"*", "0", "#"}};
        for (String[] row : keys) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER);
            for (String k : row) {
                TextView key = UiKit.text(this, k, 26, Theme.TEXT, Theme.REGULAR);
                key.setGravity(Gravity.CENTER);
                key.setBackground(Theme.circle(Theme.SURFACE));
                Theme.pressable(key);
                key.setOnClickListener(v -> { number.append(k); refreshNumber(); });
                if (k.equals("0")) key.setOnLongClickListener(v -> { number.append('+'); refreshNumber(); return true; });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(this, 70), Theme.dp(this, 70));
                lp.setMargins(Theme.dp(this, 8), Theme.dp(this, 5), Theme.dp(this, 8), Theme.dp(this, 5));
                r.addView(key, lp);
            }
            l.addView(r);
        }
        TextView call = UiKit.bigButton(this, "Call", Theme.MINT, Theme.ON_PASTEL);
        call.setOnClickListener(v -> call(number.toString()));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Theme.dp(this, 220), -2);
        clp.topMargin = Theme.dp(this, 14);
        l.addView(call, clp);
        return l;
    }

    private void refreshNumber() {
        if (numberView != null) numberView.setText(number.toString());
    }

    private void call(String n) {
        if (n == null || n.isEmpty()) return;
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pending = n;
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, 1);
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(n))));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n))));
        }
    }

    private String pending;

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 1 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED && pending != null) call(pending);
        else if (code == 2 || code == 3) select(tab);
    }

    // ---------- lists ----------
    private View listPage(String needPerm, int reqCode, String msg, Runnable fill, LinearLayout col) {
        EdgeScrollView sv = new EdgeScrollView(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(this, 16);
        col.setPadding(pad, Theme.dp(this, 10), pad, Theme.dp(this, 30));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        if (checkSelfPermission(needPerm) != PackageManager.PERMISSION_GRANTED) {
            LinearLayout ask = UiKit.pill(this, Icons.PERSON, Theme.PEACH, "Allow access", msg);
            ask.setOnClickListener(v -> requestPermissions(new String[]{needPerm}, reqCode));
            col.addView(ask, rowLp());
        } else {
            fill.run();
        }
        return sv;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Theme.dp(this, 8);
        return lp;
    }

    private View recents() {
        LinearLayout col = new LinearLayout(this);
        return listPage(Manifest.permission.READ_CALL_LOG, 2, "To show your recent calls", () -> {
            try (Cursor c = getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE},
                    null, null, CallLog.Calls.DATE + " DESC")) {
                int n = 0;
                while (c != null && c.moveToNext() && n < 40) {
                    String num = c.getString(0), name = c.getString(1);
                    int type = c.getInt(2);
                    String title = name != null && !name.isEmpty() ? name : (num == null || num.isEmpty() ? "Unknown" : num);
                    String sub = (type == CallLog.Calls.MISSED_TYPE ? "Missed" : type == CallLog.Calls.OUTGOING_TYPE ? "Outgoing" : "Incoming")
                            + " · " + android.text.format.DateUtils.getRelativeTimeSpanString(c.getLong(3),
                            System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS);
                    String glyph = type == CallLog.Calls.MISSED_TYPE ? Icons.CALL_END : Icons.PHONE;
                    int pastel = type == CallLog.Calls.MISSED_TYPE ? Theme.PEACH : Theme.MINT;
                    LinearLayout row = UiKit.pill(this, glyph, pastel, title, sub);
                    final String dial = num;
                    row.setOnClickListener(v -> call(dial));
                    col.addView(row, rowLp());
                    n++;
                }
                if (n == 0) col.addView(UiKit.text(this, "No recent calls", 16, Theme.TEXT_DIM, Theme.MEDIUM));
            } catch (Exception e) {
                col.addView(UiKit.text(this, "Can't read calls", 16, Theme.TEXT_DIM, Theme.MEDIUM));
            }
        }, col);
    }

    private View contacts() {
        LinearLayout col = new LinearLayout(this);
        return listPage(Manifest.permission.READ_CONTACTS, 3, "To show your contacts", () -> {
            try (Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")) {
                int n = 0;
                String last = "";
                while (c != null && c.moveToNext() && n < 300) {
                    String name = c.getString(0), num = c.getString(1);
                    if (name == null || name.equals(last)) continue;
                    last = name;
                    LinearLayout row = UiKit.pill(this, null, 0, name, num);
                    String letter = name.substring(0, 1).toUpperCase();
                    TextView av = UiKit.text(this, letter, 18, Theme.ON_PASTEL, Theme.MEDIUM);
                    av.setGravity(Gravity.CENTER);
                    av.setBackground(Theme.circle(Theme.PASTELS[Math.abs(name.hashCode()) % Theme.PASTELS.length]));
                    LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Theme.dp(this, 40), Theme.dp(this, 40));
                    alp.rightMargin = Theme.dp(this, 14);
                    row.addView(av, 0, alp);
                    final String dial = num;
                    row.setOnClickListener(v -> call(dial));
                    col.addView(row, rowLp());
                    n++;
                }
                if (n == 0) col.addView(UiKit.text(this, "No contacts", 16, Theme.TEXT_DIM, Theme.MEDIUM));
            } catch (Exception e) {
                col.addView(UiKit.text(this, "Can't read contacts", 16, Theme.TEXT_DIM, Theme.MEDIUM));
            }
        }, col);
    }
}
