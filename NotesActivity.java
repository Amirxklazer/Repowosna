package com.wearlauncher;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Notes: quick text notes saved on the phone. */
public class NotesActivity extends WearActivity {
    private FrameLayout headerHolder, body;
    private int editing = -1;
    private EditText editor;
    private boolean inEditor = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        headerHolder = new FrameLayout(this);
        root.addView(headerHolder, new LinearLayout.LayoutParams(-1, -2));
        body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        setRoot(root);
        showList();
    }

    private JSONArray arr() {
        try { return new JSONArray(Store.notes(this)); } catch (Exception e) { return new JSONArray(); }
    }

    private void header(String title, Runnable back) {
        headerHolder.removeAllViews();
        headerHolder.addView(UiKit.header(this, title, back));
    }

    private void showList() {
        inEditor = false;
        editing = -1;
        header("Notes", this::finish);
        body.removeAllViews();
        EdgeScrollView sv = new EdgeScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(this, 16);
        col.setPadding(pad, Theme.dp(this, 6), pad, Theme.dp(this, 40));
        sv.addView(col, new ViewGroup.LayoutParams(-1, -2));
        body.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        TextView add = UiKit.bigButton(this, "New note", Theme.LIME, Theme.ON_PASTEL);
        add.setOnClickListener(v -> edit(-1));
        col.addView(add, new LinearLayout.LayoutParams(-1, -2));
        col.addView(UiKit.spacer(this, 12));

        JSONArray a = arr();
        SimpleDateFormat fmt = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
        for (int i = a.length() - 1; i >= 0; i--) {
            final int idx = i;
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String t = o.optString("t", "");
            String first = t.contains("\n") ? t.substring(0, t.indexOf('\n')) : t;
            LinearLayout pill = UiKit.pill(this, Icons.NOTE, Theme.CREAM, first.isEmpty() ? "(Empty)" : first,
                    fmt.format(new Date(o.optLong("d", 0))));
            pill.setOnClickListener(v -> edit(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Theme.dp(this, 8);
            col.addView(pill, lp);
        }
        if (a.length() == 0) {
            TextView e = UiKit.text(this, "No notes yet", 16, Theme.TEXT_DIM, Theme.MEDIUM);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, Theme.dp(this, 40), 0, 0);
            col.addView(e, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void edit(int idx) {
        inEditor = true;
        editing = idx;
        header(idx < 0 ? "New note" : "Note", this::saveAndClose);
        body.removeAllViews();
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(Theme.dp(this, 16), Theme.dp(this, 6), Theme.dp(this, 16), Theme.dp(this, 12));
        editor = new EditText(this);
        editor.setBackground(Theme.round(Theme.SURFACE, Theme.dpf(this, 28)));
        editor.setHint("Write a note\u2026");
        editor.setHintTextColor(Theme.TEXT_DIM);
        editor.setTextColor(Theme.TEXT);
        editor.setTextSize(17);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(Theme.dp(this, 20), Theme.dp(this, 18), Theme.dp(this, 20), Theme.dp(this, 18));
        if (idx >= 0) {
            JSONObject o = arr().optJSONObject(idx);
            if (o != null) editor.setText(o.optString("t", ""));
        }
        l.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        FrameLayout del = UiKit.roundButton(this, Icons.DELETE, Theme.PEACH, Theme.ON_PASTEL, 56);
        FrameLayout ok = UiKit.roundButton(this, Icons.CHECK, Theme.MINT, Theme.ON_PASTEL, 56);
        ((LinearLayout.LayoutParams) ok.getLayoutParams()).leftMargin = Theme.dp(this, 16);
        row.addView(del);
        row.addView(ok);
        l.addView(row, UiKit.withTop(this, 12));
        body.addView(l, new FrameLayout.LayoutParams(-1, -1));
        del.setOnClickListener(v -> { removeCurrent(); showList(); });
        ok.setOnClickListener(v -> saveAndClose());
        if (idx < 0) editor.requestFocus();
    }

    private void removeCurrent() {
        if (editing < 0) return;
        JSONArray a = arr();
        a.remove(editing);
        Store.setNotes(this, a.toString());
    }

    private void saveAndClose() {
        String text = editor == null ? "" : editor.getText().toString().trim();
        JSONArray a = arr();
        try {
            if (text.isEmpty()) {
                if (editing >= 0) a.remove(editing);
            } else {
                JSONObject o = new JSONObject();
                o.put("t", text);
                o.put("d", System.currentTimeMillis());
                if (editing >= 0) a.put(editing, o); else a.put(o);
            }
            Store.setNotes(this, a.toString());
        } catch (Exception ignored) {}
        showList();
    }

    @Override public void onBackPressed() {
        if (inEditor) saveAndClose(); else finish();
    }
}
