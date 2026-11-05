package com.wearlauncher;

import android.app.role.RoleManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * HOME screen. Watch face in the middle:
 *   swipe down  -> quick settings     swipe up    -> notifications
 *   swipe left  -> tiles              bottom pill -> apps
 *   long press  -> change watch face
 */
public class LauncherActivity extends WearActivity implements Host {
    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final int DUR = 360;

    private FrameLayout root;
    private FaceView face;
    private LinearLayout dock;
    private TextView homeHint, faceName;
    private AppsView apps;
    private NotificationsView notifs;
    private QuickSettingsView qs;
    private TilesView tiles;
    private String current;
    private int w, h;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);

        face = new FaceView(this);
        face.setClickable(true);
        root.addView(face, new FrameLayout.LayoutParams(-1, -1));

        faceName = UiKit.text(this, "", 14, Theme.TEXT_DIM, Theme.MEDIUM);
        faceName.setAlpha(0f);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        flp.topMargin = Theme.dp(this, 96);
        root.addView(faceName, flp);

        // dock: optional "set as home" pill + the Apps button
        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setGravity(Gravity.CENTER_HORIZONTAL);
        homeHint = UiKit.bigButton(this, "Set as home app", Theme.SURFACE, Theme.TEXT);
        homeHint.setTextSize(14);
        homeHint.setOnClickListener(v -> requestHome());
        dock.addView(homeHint, new LinearLayout.LayoutParams(-2, -2));
        TextView appsBtn = UiKit.bigButton(this, "Apps", Theme.BLUE, Theme.ON_PASTEL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Theme.dp(this, 200), -2);
        alp.topMargin = Theme.dp(this, 12);
        dock.addView(appsBtn, alp);
        appsBtn.setOnClickListener(v -> openPanel("apps"));
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        dlp.bottomMargin = Theme.dp(this, 28);
        root.addView(dock, dlp);

        apps = new AppsView(this, this);
        notifs = new NotificationsView(this, this);
        qs = new QuickSettingsView(this, this);
        tiles = new TilesView(this, this);
        for (View p : new View[]{apps, notifs, qs, tiles}) {
            p.setVisibility(View.GONE);
            root.addView(p, new FrameLayout.LayoutParams(-1, -1));
        }
        qs.setSwipeUpListener(this::closePanel);
        wirePull(notifs.scroller(), notifs);
        wirePull(apps.scroller(), apps);

        setupFaceGestures();
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int t = ins.getSystemWindowInsetTop(), bt = ins.getSystemWindowInsetBottom();
            for (View p : new View[]{apps, notifs, qs, tiles}) p.setPadding(0, t, 0, bt);
            ((FrameLayout.LayoutParams) dock.getLayoutParams()).bottomMargin = bt + Theme.dp(this, 20);
            dock.requestLayout();
            return ins;
        });
        root.addOnLayoutChangeListener((v, l, t, r, bo, ol, ot, or, ob) -> { w = r - l; h = bo - t; });
    }

    private void wirePull(EdgeScrollView sv, View panel) {
        sv.setPullListener(new EdgeScrollView.PullListener() {
            @Override public void onPull(float dy) { panel.setTranslationY(dy * 0.9f); }
            @Override public void onRelease(float dy) {
                if (dy > Theme.dp(LauncherActivity.this, 110)) closePanel();
                else panel.animate().translationY(0).setDuration(220).setInterpolator(EASE).start();
            }
        });
    }

    private void setupFaceGestures() {
        final int dist = Theme.dp(this, 70);
        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || b == null) return false;
                float dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
                if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > dist) {
                    openPanel(dy > 0 ? "qs" : "notifs");
                    return true;
                }
                if (Math.abs(dx) > dist) {
                    if (dx < 0) openPanel("tiles");
                    return true;
                }
                return false;
            }

            @Override public void onLongPress(MotionEvent e) {
                face.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                face.setFace(face.getFace() + 1);
                faceName.setText(FaceView.NAMES[face.getFace()]);
                faceName.animate().cancel();
                faceName.setAlpha(1f);
                faceName.animate().alpha(0f).setStartDelay(1100).setDuration(400).start();
            }
        });
        face.setOnTouchListener((v, e) -> gd.onTouchEvent(e));
    }

    // ---------- panel management ----------
    private View panel(String n) {
        switch (n) {
            case "apps": return apps;
            case "notifs": return notifs;
            case "qs": return qs;
            default: return tiles;
        }
    }

    @Override public void openPanel(String name) {
        if (name.equals(current)) return;
        if (current != null) hideNow(panel(current));
        View p = panel(name);
        if (name.equals("apps")) apps.open();
        else if (name.equals("qs")) qs.refresh();
        else if (name.equals("notifs")) { notifs.rebuild(); notifs.scroller().scrollTo(0, 0); }
        else tiles.reset();
        if (Store.haptics(this)) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        float W = w == 0 ? root.getWidth() : w, H = h == 0 ? root.getHeight() : h;
        p.setVisibility(View.VISIBLE);
        p.setAlpha(1f);
        p.setTranslationX(name.equals("tiles") ? W : 0);
        p.setTranslationY(name.equals("tiles") ? 0 : (name.equals("qs") ? -H : H));
        p.animate().translationX(0).translationY(0).setDuration(DUR).setInterpolator(EASE).setListener(null).start();
        face.animate().scaleX(0.92f).scaleY(0.92f).alpha(0.35f).setDuration(DUR).setInterpolator(EASE).start();
        dock.animate().alpha(0f).setDuration(200).start();
        current = name;
    }

    @Override public void closePanel() {
        if (current == null) return;
        final View p = panel(current);
        final String name = current;
        current = null;
        float W = root.getWidth(), H = root.getHeight();
        p.animate().translationX(name.equals("tiles") ? W : 0)
                .translationY(name.equals("tiles") ? 0 : (name.equals("qs") ? -H : H))
                .setDuration(280).setInterpolator(EASE)
                .withEndAction(() -> { if (!name.equals(current)) p.setVisibility(View.GONE); }).start();
        face.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(280).setInterpolator(EASE).start();
        dock.animate().alpha(1f).setStartDelay(120).setDuration(220).start();
    }

    private void hideNow(View p) {
        p.animate().cancel();
        p.setVisibility(View.GONE);
    }

    @Override public void startApp(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Can't open that", android.widget.Toast.LENGTH_SHORT).show();
        }
        // slide everything away behind the launched app
        handler.postDelayed(() -> {
            if (current != null) {
                View p = panel(current);
                p.setVisibility(View.GONE);
                current = null;
                face.setScaleX(1f); face.setScaleY(1f); face.setAlpha(1f);
                dock.setAlpha(1f);
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

    private void requestHome() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 77);
            } else {
                startActivity(new Intent(android.provider.Settings.ACTION_HOME_SETTINGS));
            }
        } catch (Exception e) {
            startActivity(new Intent(android.provider.Settings.ACTION_HOME_SETTINGS));
        }
    }

    // ---------- lifecycle ----------
    @Override protected void onResume() {
        super.onResume();
        homeHint.setVisibility(isDefaultHome() ? View.GONE : View.VISIBLE);
        if (Store.aod(this)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        face.invalidate();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed again: return to the watch face.
        if (Intent.ACTION_MAIN.equals(intent.getAction())) closePanel();
    }

    @Override public void onBackPressed() {
        if (current == null) return;                 // we are home; nothing to go back to
        if (current.equals("apps") && apps.onBack()) return;
        closePanel();
    }
}
