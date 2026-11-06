package com.wearlauncher;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Weather from open-meteo.com using the phone's last known location. */
public class WeatherActivity extends WearActivity {
    private LinearLayout col;
    private LocationListener fix;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(UiKit.header(this, "Weather", this::finish), new LinearLayout.LayoutParams(-1, -2));
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
        load();
    }

    @Override protected void onPause() {
        super.onPause();
        stopFix();
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        load();
    }

    private void note(String text) {
        col.removeAllViews();
        TextView t = UiKit.text(this, text, 16, Theme.TEXT_DIM, Theme.MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Theme.dp(this, 60), 0, Theme.dp(this, 16));
        col.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    private void load() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            note("Weather needs your location");
            TextView b = UiKit.bigButton(this, "Allow location", Theme.BLUE, Theme.ON_PASTEL);
            b.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION}, 9));
            col.addView(b, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        Location l = lastLoc();
        if (l != null) { fetch(l); return; }
        note("Finding your location\u2026");
        requestFix();
    }

    private Location lastLoc() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) {}
        return best;
    }

    @SuppressWarnings("deprecation")
    private void requestFix() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        String prov = null;
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) prov = LocationManager.NETWORK_PROVIDER;
            else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) prov = LocationManager.GPS_PROVIDER;
        } catch (Exception ignored) {}
        if (prov == null) {
            note("Turn on location to see weather");
            TextView b = UiKit.bigButton(this, "Location settings", Theme.BLUE, Theme.ON_PASTEL);
            b.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
            col.addView(b, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        fix = new LocationListener() {
            @Override public void onLocationChanged(Location loc) { stopFix(); fetch(loc); }
            @Override public void onStatusChanged(String p, int s, Bundle e) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };
        try { lm.requestSingleUpdate(prov, fix, Looper.getMainLooper()); }
        catch (SecurityException e) { note("Location not allowed"); }
    }

    private void stopFix() {
        if (fix == null) return;
        try { ((LocationManager) getSystemService(LOCATION_SERVICE)).removeUpdates(fix); } catch (Exception ignored) {}
        fix = null;
    }

    private void fetch(Location l) {
        note("Loading weather\u2026");
        final boolean f = Store.fahrenheit(this);
        final String url = "https://api.open-meteo.com/v1/forecast?latitude=" + String.format(Locale.US, "%.3f", l.getLatitude())
                + "&longitude=" + String.format(Locale.US, "%.3f", l.getLongitude())
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=5&timezone=auto"
                + (f ? "&temperature_unit=fahrenheit&wind_speed_unit=mph" : "");
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                final JSONObject j = new JSONObject(sb.toString());
                runOnUiThread(() -> render(j, f));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    note("Couldn't load weather");
                    TextView b = UiKit.bigButton(this, "Try again", Theme.BLUE, Theme.ON_PASTEL);
                    b.setOnClickListener(v -> load());
                    col.addView(b, new LinearLayout.LayoutParams(-1, -2));
                });
            }
        }).start();
    }

    private void render(JSONObject j, boolean f) {
        try {
            col.removeAllViews();
            JSONObject cur = j.getJSONObject("current");
            int code = cur.getInt("weather_code");
            boolean day = cur.getInt("is_day") == 1;
            String deg = f ? "\u00B0F" : "\u00B0C";

            WxView wx = new WxView(this);
            wx.setKind(WxView.kindFor(code, day));
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(Theme.dp(this, 150), Theme.dp(this, 150));
            wlp.gravity = Gravity.CENTER_HORIZONTAL;
            col.addView(wx, wlp);

            TextView temp = UiKit.text(this, Math.round(cur.getDouble("temperature_2m")) + "\u00B0", 72, Theme.TEXT, Theme.LIGHT);
            temp.setGravity(Gravity.CENTER);
            col.addView(temp, new LinearLayout.LayoutParams(-1, -2));
            TextView name = UiKit.text(this, WxView.nameFor(code), 18, Theme.PEACH, Theme.MEDIUM);
            name.setGravity(Gravity.CENTER);
            col.addView(name, new LinearLayout.LayoutParams(-1, -2));
            TextView det = UiKit.text(this, "Feels " + Math.round(cur.getDouble("apparent_temperature")) + "\u00B0   \u00B7   Humidity "
                    + Math.round(cur.getDouble("relative_humidity_2m")) + "%   \u00B7   Wind "
                    + Math.round(cur.getDouble("wind_speed_10m")) + (f ? " mph" : " km/h"), 13, Theme.TEXT_DIM, Theme.REGULAR);
            det.setGravity(Gravity.CENTER);
            col.addView(det, UiKit.withTop(this, 8));
            col.addView(UiKit.spacer(this, 18));

            JSONObject d = j.getJSONObject("daily");
            JSONArray times = d.getJSONArray("time"), codes = d.getJSONArray("weather_code"),
                    hi = d.getJSONArray("temperature_2m_max"), lo = d.getJSONArray("temperature_2m_min");
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("EEEE", Locale.getDefault());
            for (int i = 0; i < times.length(); i++) {
                String label = i == 0 ? "Today" : out.format(in.parse(times.getString(i)));
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Theme.dp(this, 20), Theme.dp(this, 8), Theme.dp(this, 20), Theme.dp(this, 8));
                row.setBackground(Theme.round(Theme.SURFACE, Theme.dpf(this, 28)));
                row.addView(UiKit.text(this, label, 16, Theme.TEXT, Theme.MEDIUM), new LinearLayout.LayoutParams(0, -2, 1f));
                WxView ic = new WxView(this);
                ic.setKind(WxView.kindFor(codes.getInt(i), true));
                row.addView(ic, new LinearLayout.LayoutParams(Theme.dp(this, 44), Theme.dp(this, 44)));
                TextView t = UiKit.text(this, Math.round(hi.getDouble(i)) + "\u00B0 / " + Math.round(lo.getDouble(i)) + "\u00B0", 16, Theme.TEXT, Theme.MEDIUM);
                t.setGravity(Gravity.END);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Theme.dp(this, 90), -2);
                tlp.leftMargin = Theme.dp(this, 8);
                row.addView(t, tlp);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                rlp.bottomMargin = Theme.dp(this, 8);
                col.addView(row, rlp);
            }
            TextView unit = UiKit.bigButton(this, f ? "Switch to \u00B0C" : "Switch to \u00B0F", Theme.SURFACE_HI, Theme.TEXT);
            unit.setOnClickListener(v -> { Store.setFahrenheit(this, !f); load(); });
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(-1, -2);
            ulp.topMargin = Theme.dp(this, 8);
            col.addView(unit, ulp);
        } catch (Exception e) {
            note("Couldn't read weather");
        }
    }
}
