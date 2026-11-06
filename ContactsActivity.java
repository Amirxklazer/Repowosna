package com.wearlauncher;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Contacts: searchable list, tap for Call / Message. */
public class ContactsActivity extends WearActivity {
    private static class Person { String name, number; }

    private final List<Person> all = new ArrayList<>();
    private final List<Person> shown = new ArrayList<>();
    private RecyclerView rv;
    private EditText search;
    private FrameLayout body;
    private TextView msg;
    private View detail;

    private final RecyclerView.Adapter<RecyclerView.ViewHolder> ad = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        @Override public int getItemCount() { return shown.size(); }

        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p, int t) {
            Context c = p.getContext();
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Theme.dp(c, 12), Theme.dp(c, 8), Theme.dp(c, 16), Theme.dp(c, 8));
            row.setBackground(Theme.round(Theme.SURFACE, Theme.dpf(c, 32)));
            TextView av = UiKit.text(c, "", 18, Theme.ON_PASTEL, Theme.MEDIUM);
            av.setGravity(Gravity.CENTER);
            av.setTag("av");
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Theme.dp(c, 44), Theme.dp(c, 44));
            alp.rightMargin = Theme.dp(c, 14);
            row.addView(av, alp);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView n = UiKit.text(c, "", 17, Theme.TEXT, Theme.MEDIUM);
            n.setSingleLine(true);
            n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            n.setTag("n");
            TextView num = UiKit.text(c, "", 13, Theme.TEXT_DIM, Theme.REGULAR);
            num.setTag("num");
            col.addView(n);
            col.addView(num, UiKit.withTop(c, 2));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
            lp.bottomMargin = Theme.dp(c, 8);
            row.setLayoutParams(lp);
            Theme.pressable(row);
            return new RecyclerView.ViewHolder(row) {};
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
            final Person p = shown.get(pos);
            View v = h.itemView;
            TextView av = v.findViewWithTag("av");
            TextView n = v.findViewWithTag("n");
            TextView num = v.findViewWithTag("num");
            av.setText(initial(p.name));
            av.setBackground(Theme.circle(color(p.name)));
            n.setText(p.name);
            num.setText(p.number);
            v.setOnClickListener(x -> showDetail(p));
        }
    };

    private static String initial(String n) {
        return n == null || n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private static int color(String n) {
        return Theme.PASTELS[Math.abs(n.hashCode()) % Theme.PASTELS.length];
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(UiKit.header(this, "Contacts", this::finish), new LinearLayout.LayoutParams(-1, -2));

        search = new EditText(this);
        search.setBackground(Theme.round(Theme.SURFACE, Theme.dpf(this, 28)));
        search.setHint("Search contacts");
        search.setHintTextColor(Theme.TEXT_DIM);
        search.setTextColor(Theme.TEXT);
        search.setTextSize(16);
        search.setSingleLine(true);
        search.setPadding(Theme.dp(this, 20), Theme.dp(this, 14), Theme.dp(this, 20), Theme.dp(this, 14));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(Theme.dp(this, 16), Theme.dp(this, 4), Theme.dp(this, 16), Theme.dp(this, 8));
        root.addView(search, slp);

        body = new FrameLayout(this);
        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(ad);
        rv.setItemAnimator(null);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setClipToPadding(false);
        rv.setPadding(Theme.dp(this, 16), 0, Theme.dp(this, 16), Theme.dp(this, 40));
        body.addView(rv, new FrameLayout.LayoutParams(-1, -1));
        msg = UiKit.text(this, "", 16, Theme.TEXT, Theme.MEDIUM);
        msg.setGravity(Gravity.CENTER);
        msg.setVisibility(View.GONE);
        body.addView(msg, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        setRoot(root);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void afterTextChanged(Editable e) { filter(e.toString()); }
        });
        start();
    }

    private void start() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            msg.setText("Tap to allow contacts");
            msg.setBackground(Theme.round(Theme.BLUE, Theme.dpf(this, 28)));
            msg.setTextColor(Theme.ON_PASTEL);
            msg.setPadding(Theme.dp(this, 28), Theme.dp(this, 15), Theme.dp(this, 28), Theme.dp(this, 15));
            msg.setVisibility(View.VISIBLE);
            msg.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 3));
            return;
        }
        msg.setBackground(null);
        msg.setTextColor(Theme.TEXT_DIM);
        msg.setOnClickListener(null);
        msg.setText("Loading\u2026");
        msg.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<Person> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            try (Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")) {
                while (c != null && c.moveToNext()) {
                    String n = c.getString(0), p = c.getString(1);
                    if (n == null || p == null) continue;
                    if (!seen.add(n + "|" + p.replaceAll("\\D", ""))) continue;
                    Person x = new Person();
                    x.name = n; x.number = p;
                    out.add(x);
                }
            } catch (Exception ignored) {}
            runOnUiThread(() -> { all.clear(); all.addAll(out); filter(search.getText().toString()); });
        }).start();
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == 3) start();
    }

    private void filter(String q) {
        String s = q.trim().toLowerCase(Locale.getDefault());
        shown.clear();
        for (Person p : all) {
            if (s.isEmpty() || p.name.toLowerCase(Locale.getDefault()).contains(s) || p.number.replaceAll("\\s", "").contains(s))
                shown.add(p);
        }
        ad.notifyDataSetChanged();
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return;
        if (all.isEmpty()) { msg.setText("No contacts"); msg.setVisibility(View.VISIBLE); }
        else if (shown.isEmpty()) { msg.setText("No matches"); msg.setVisibility(View.VISIBLE); }
        else msg.setVisibility(View.GONE);
    }

    private void showDetail(Person p) {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.VERTICAL);
        d.setGravity(Gravity.CENTER);
        d.setBackgroundColor(Theme.BG);
        d.setClickable(true);
        TextView av = UiKit.text(this, initial(p.name), 44, Theme.ON_PASTEL, Theme.MEDIUM);
        av.setGravity(Gravity.CENTER);
        av.setBackground(Theme.circle(color(p.name)));
        d.addView(av, new LinearLayout.LayoutParams(Theme.dp(this, 110), Theme.dp(this, 110)));
        TextView n = UiKit.text(this, p.name, 26, Theme.TEXT, Theme.MEDIUM);
        n.setGravity(Gravity.CENTER);
        n.setPadding(Theme.dp(this, 24), 0, Theme.dp(this, 24), 0);
        d.addView(n, UiKit.withTop(this, 18));
        d.addView(UiKit.text(this, p.number, 16, Theme.TEXT_DIM, Theme.REGULAR), UiKit.withTop(this, 6));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        FrameLayout call = UiKit.roundButton(this, Icons.PHONE, Theme.MINT, Theme.ON_PASTEL, 72);
        FrameLayout sms = UiKit.roundButton(this, Icons.MESSAGE, Theme.BLUE, Theme.ON_PASTEL, 72);
        ((LinearLayout.LayoutParams) call.getLayoutParams()).rightMargin = Theme.dp(this, 20);
        row.addView(call);
        row.addView(sms);
        d.addView(row, UiKit.withTop(this, 32));
        call.setOnClickListener(v -> call(p.number));
        sms.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(p.number)))); }
            catch (Exception ignored) {}
        });
        TextView back = UiKit.bigButton(this, "Back", Theme.SURFACE, Theme.TEXT);
        back.setOnClickListener(v -> closeDetail());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Theme.dp(this, 180), -2);
        blp.topMargin = Theme.dp(this, 36);
        d.addView(back, blp);
        detail = d;
        body.addView(d, new FrameLayout.LayoutParams(-1, -1));
    }

    private void closeDetail() {
        if (detail != null) { body.removeView(detail); detail = null; }
    }

    private void call(String num) {
        Intent i = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                ? new Intent(Intent.ACTION_CALL) : new Intent(Intent.ACTION_DIAL);
        i.setData(Uri.fromParts("tel", num, null));
        try { startActivity(i); } catch (Exception ignored) {}
    }

    @Override public void onBackPressed() {
        if (detail != null) closeDetail(); else finish();
    }
}
