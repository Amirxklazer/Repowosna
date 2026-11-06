package com.wearlauncher;

import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

/**
 * HOME screen = Wear OS launcher resized for phones (no watch face).
 *   swipe down at the top -> quick settings    swipe left  -> tiles
 *   swipe right           -> notifications     buttons in the top bar do the same
 */
public class LauncherActivity extends WearActivity implements Host {
    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final int DUR = 340;

    private FrameLayout root;
    private HomeView home;
    private NotificationsView notifs;
    private QuickSettingsView qs;
    private TilesView tiles;
    private String current;
    private GestureDetector gd;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver pkgRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { home.reload(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);

        home = new HomeView(this, this);
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));

        notifs = new NotificationsView(this, this);
        qs = new QuickSettingsView(this, this);
        tiles = new TilesView(this, this);
        for (View p : new View[]{notifs, qs, tiles}) {
            p.setVisibility(View.GONE);
            root.addView(p, new FrameLayout.LayoutParams(-1, -1));
        }
        qs.setSwipeUpListener(this::closePanel);
        NotifService.onChange = () -> { notifs.rebuild(); home.setBadge(NotifService.count); };
        notifs.scroller().setPullListener(new EdgeScrollView.PullListener() {
            @Override public void onPull(float dy) { notifs.setTranslationY(dy * 0.9f); }
            @Override public void onRelease(float dy) {
                if (dy > Theme.dp(LauncherActivity.this, 110)) closePanel();
                else notifs.animate().translationY(0).setDuration(220).setInterpolator(EASE).start();
            }
        });

        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent a, MotionEvent e, float vx, float vy) {
                if (a == null || e == null || current != null) return false;
                float dx = e.getX() - a.getX(), dy = e.getY() - a.getY();
                int dist = Theme.dp(LauncherActivity.this, 90);
                if (Math.abs(dx) > dist && Math.abs(dx) > 2.2f * Math.abs(dy) && Math.abs(vx) > 1100) {
                    openPanel(dx < 0 ? "tiles" : "notifs");
                    return true;
                }
                if (dy > Theme.dp(LauncherActivity.this, 110) && dy > 2f * Math.abs(dx) && vy > 1400
                        && a.getY() < Theme.dp(LauncherActivity.this, 220) + insetTop && !home.canScrollUp()) {
                    openPanel("qs");
                    return true;
                }
                return false;
            }
        });

        setRoot(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        ContextCompat.registerReceiver(this, pkgRx, f, ContextCompat.RECEIVER_EXPORTED);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gd != null) gd.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(pkgRx); } catch (Exception ignored) {}
        super.onDestroy();
    }

    // ---------- panels ----------
    private View panel(String n) {
        switch (n) {
            case "notifs": return notifs;
            case "qs": return qs;
            default: return tiles;
        }
    }

    @Override public void openPanel(String name) {
        if ("apps".equals(name)) { closePanel(); home.scrollToApps(); return; }
        if (name.equals(current)) return;
        if (current != null) { View old = panel(current); old.animate().cancel(); old.setVisibility(View.GONE); }
        View p = panel(name);
        if (name.equals("qs")) qs.refresh();
        else if (name.equals("notifs")) { notifs.rebuild(); notifs.scroller().scrollTo(0, 0); }
        else tiles.reset();
        if (Store.haptics(this)) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        float W = Math.max(1, root.getWidth()), H = Math.max(1, root.getHeight());
        p.setVisibility(View.VISIBLE);
        p.setAlpha(1f);
        p.setTranslationX(name.equals("tiles") ? W : (name.equals("notifs") ? -W : 0));
        p.setTranslationY(name.equals("qs") ? -H : 0);
        p.animate().translationX(0).translationY(0).setDuration(DUR).setInterpolator(EASE).setListener(null).start();
        home.animate().scaleX(0.94f).scaleY(0.94f).alpha(0.3f).setDuration(DUR).setInterpolator(EASE).start();
        current = name;
    }

    @Override public void closePanel() {
        if (current == null) return;
        final View p = panel(current);
        final String name = current;
        current = null;
        float W = Math.max(1, root.getWidth()), H = Math.max(1, root.getHeight());
        p.animate().translationX(name.equals("tiles") ? W : (name.equals("notifs") ? -W : 0))
                .translationY(name.equals("qs") ? -H : 0)
                .setDuration(260).setInterpolator(EASE)
                .withEndAction(() -> { if (!name.equals(current)) p.setVisibility(View.GONE); }).start();
        home.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260).setInterpolator(EASE).start();
    }

    @Override public void startApp(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Can't open that", android.widget.Toast.LENGTH_SHORT).show();
        }
        handler.postDelayed(() -> {
            if (current != null) {
                View p = panel(current);
                p.setVisibility(View.GONE);
                current = null;
                home.setScaleX(1f); home.setScaleY(1f); home.setAlpha(1f);
            }
        }, 350);
    }

    // ---------- default-home helper ----------
    private boolean isDefaultHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
            return rm != null && rm.isRoleHeld(RoleManager.ROLE_HOME);
        }
        return false;
    }

    @Override public void requestHome() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 77);
            } else {
                startActivity(new Intent(android.provider.Settings.ACTION_HOME_SETTINGS));
            }
        } catch (Exception e) {
            try { startActivity(new Intent(android.provider.Settings.ACTION_HOME_SETTINGS)); } catch (Exception ignored) {}
        }
    }

    // ---------- lifecycle ----------
    @Override protected void onResume() {
        super.onResume();
        home.setShowBanner(!isDefaultHome());
        home.setBadge(NotifService.count);
        home.refresh();
        if (Store.aod(this)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            closePanel();
            home.onBack();
        }
    }

    @Override public void onBackPressed() {
        if (current != null) { closePanel(); return; }
        home.onBack();
    }
}
