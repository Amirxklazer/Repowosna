package com.wearlauncher;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.math.BigDecimal;
import java.math.MathContext;

/** Calculator with big round pastel keys. */
public class CalculatorActivity extends WearActivity {
    private TextView display;
    private String cur = "0";
    private double acc = 0;
    private char op = 0;
    private boolean fresh = true;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(UiKit.header(this, "Calculator", this::finish), new LinearLayout.LayoutParams(-1, -2));

        display = UiKit.text(this, "0", 56, Theme.TEXT, Theme.LIGHT);
        display.setGravity(Gravity.END | Gravity.BOTTOM);
        display.setSingleLine(true);
        display.setPadding(Theme.dp(this, 24), 0, Theme.dp(this, 24), Theme.dp(this, 10));
        root.addView(display, new LinearLayout.LayoutParams(-1, 0, 1f));

        int sw = getResources().getDisplayMetrics().widthPixels;
        int gap = Theme.dp(this, 10);
        int size = Math.min(Theme.dp(this, 80), (sw - Theme.dp(this, 32) - gap * 3) / 4);
        String[][] keys = {
                {"AC", "\u00B1", "%", "\u00F7"},
                {"7", "8", "9", "\u00D7"},
                {"4", "5", "6", "\u2212"},
                {"1", "2", "3", "+"},
                {"0", ".", "<", "="}};
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setGravity(Gravity.CENTER_HORIZONTAL);
        pad.setPadding(0, Theme.dp(this, 6), 0, Theme.dp(this, 20));
        for (String[] r : keys) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            for (String k : r) {
                FrameLayout key = key(k, size);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMargins(gap / 2, gap / 2, gap / 2, gap / 2);
                row.addView(key, lp);
            }
            pad.addView(row);
        }
        root.addView(pad, new LinearLayout.LayoutParams(-1, -2));
        setRoot(root);
    }

    private FrameLayout key(String k, int size) {
        boolean opKey = k.equals("\u00F7") || k.equals("\u00D7") || k.equals("\u2212") || k.equals("+");
        int bg = k.equals("=") ? Theme.LIME : opKey ? Theme.BLUE : (k.equals("AC") || k.equals("\u00B1") || k.equals("%")) ? Theme.LILAC : Theme.SURFACE;
        int fg = (bg == Theme.SURFACE) ? Theme.TEXT : Theme.ON_PASTEL;
        FrameLayout f = new FrameLayout(this);
        f.setBackground(Theme.circle(bg));
        if (k.equals("<")) {
            Icons.IconView iv = new Icons.IconView(this, Icons.BACKSPACE, fg);
            iv.setInsetFraction(0.28f);
            f.addView(iv, new FrameLayout.LayoutParams(size, size));
        } else {
            TextView t = UiKit.text(this, k, k.equals("AC") ? 20 : 26, fg, Theme.MEDIUM);
            t.setGravity(Gravity.CENTER);
            f.addView(t, new FrameLayout.LayoutParams(-1, -1));
        }
        Theme.pressable(f);
        f.setOnClickListener(v -> press(k));
        return f;
    }

    private void press(String k) {
        switch (k) {
            case "AC": cur = "0"; acc = 0; op = 0; fresh = true; break;
            case "\u00B1":
                if (!cur.equals("0") && !cur.equals("Error")) cur = cur.startsWith("-") ? cur.substring(1) : "-" + cur;
                break;
            case "%": cur = fmt(parse(cur) / 100.0); fresh = true; break;
            case "\u00F7": case "\u00D7": case "\u2212": case "+": oper(k.charAt(0)); break;
            case "=": equal(); break;
            case "<":
                if (!fresh && cur.length() > 1) { cur = cur.substring(0, cur.length() - 1); if (cur.equals("-")) cur = "0"; }
                else { cur = "0"; }
                break;
            case ".": digit("."); break;
            default: digit(k);
        }
        show();
    }

    private void digit(String d) {
        if (cur.equals("Error")) { cur = "0"; fresh = true; }
        if (fresh) { cur = d.equals(".") ? "0." : d; fresh = false; return; }
        if (d.equals(".") && cur.contains(".")) return;
        if (cur.equals("0") && !d.equals(".")) cur = d;
        else if (cur.replace("-", "").replace(".", "").length() < 12) cur += d;
    }

    private void oper(char o) {
        double v = parse(cur);
        if (op != 0 && !fresh) { acc = apply(acc, op, v); cur = fmt(acc); }
        else acc = v;
        op = o;
        fresh = true;
    }

    private void equal() {
        if (op == 0) return;
        acc = apply(acc, op, parse(cur));
        cur = fmt(acc);
        op = 0;
        fresh = true;
    }

    private static double apply(double a, char o, double b) {
        switch (o) {
            case '+': return a + b;
            case '\u2212': return a - b;
            case '\u00D7': return a * b;
            default: return b == 0 ? Double.NaN : a / b;
        }
    }

    private static double parse(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private static String fmt(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "Error";
        String s = new BigDecimal(d).round(new MathContext(10)).stripTrailingZeros().toPlainString();
        if (s.length() > 12) s = String.format(java.util.Locale.US, "%.5e", d);
        return s;
    }

    private void show() {
        display.setText(cur);
        int n = cur.length();
        display.setTextSize(n <= 7 ? 56 : n <= 10 ? 44 : 34);
    }
}
