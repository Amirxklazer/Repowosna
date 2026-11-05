package com.wearlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.List;

/** Swipeable tiles: quick start, media, battery, timer. */
public class TilesView extends FrameLayout {
    private final Host host;
    private final ViewPager pager;
    private final LinearLayout dots;
    private final List<View> pages = new java.util.ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // media widgets
    private TextView mediaTitle, mediaArtist;
    private FrameLayout playBtn;
    private Icons.IconView playIcon;
    private TextView batteryText;
    private android.widget.ProgressBar batteryBar;

    public TilesView(Context c, Host host) {
        super(c);
        this.host = host;
        setBackgroundColor(Theme.BG);

        pages.add(quickStart(c));
        pages.add(media(c));
        pages.add(battery(c));
        pages.add(timer(c));

        pager = new ViewPager(c);
        pager.setAdapter(new PagerAdapter() {
            @Override public int getCount() { return pages.size(); }
            @Override public boolean isViewFromObject(View v, Object o) { return v == o; }
            @Override public Object instantiateItem(ViewGroup g, int i) { g.addView(pages.get(i)); return pages.get(i); }
            @Override public void destroyItem(ViewGroup g, int i, Object o) { g.removeView((View) o); }
        });
        pager.setPageMargin(Theme.dp(c, 12));
        pager.setOffscreenPageLimit(4);
        addView(pager, new LayoutParams(-1, -1));

        dots = new LinearLayout(c);
        dots.setGravity(Gravity.CENTER);
        LayoutParams dlp = new LayoutParams(-1, -2, Gravity.BOTTOM);
        dlp.bottomMargin = Theme.dp(c, 28);
        addView(dots, dlp);
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override public void onPageSelected(int p) { drawDots(p); }
        });
        drawDots(0);

        FrameLayout hdr = UiKit.header(c, "", host::closePanel);
        addView(hdr, new LayoutParams(-1, -2, Gravity.TOP));
    }

    private void drawDots(int sel) {
        Context c = getContext();
        dots.removeAllViews();
        for (int i = 0; i < pages.size(); i++) {
            View d = new View(c);
            d.setBackground(Theme.circle(i == sel ? Theme.TEXT : 0xFF45454C));
            int s = Theme.dp(c, i == sel ? 8 : 6);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.leftMargin = lp.rightMargin = Theme.dp(c, 4);
            dots.addView(d, lp);
        }
    }

    private LinearLayout page(Context c, String title) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(Theme.dp(c, 20), Theme.dp(c, 90), Theme.dp(c, 20), Theme.dp(c, 70));
        TextView t = UiKit.text(c, title, 14, Theme.TEXT_DIM, Theme.MEDIUM);
        l.addView(t, new LinearLayout.LayoutParams(-2, -2));
        return l;
    }

    // ---- tile 1: quick start ----
    private View quickStart(Context c) {
        LinearLayout l = page(c, "Quick start");
        l.addView(UiKit.spacer(c, 24));
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER);
        FrameLayout torch = UiKit.roundButton(c, Icons.FLASHLIGHT, Theme.CREAM, Theme.ON_PASTEL, 96);
        torch.setOnClickListener(v -> Torch.toggle(c));
        FrameLayout cam = UiKit.roundButton(c, Icons.CAMERA, Theme.LILAC, Theme.ON_PASTEL, 96);
        cam.setOnClickListener(v -> host.startApp(new Intent(c, CameraActivity.class)));
        row.addView(torch);
        row.addView(UiKit.spacer(c, 0), new LinearLayout.LayoutParams(Theme.dp(c, 18), 1));
        row.addView(cam);
        l.addView(row);
        l.addView(UiKit.spacer(c, 22));
        LinearLayout row2 = new LinearLayout(c);
        row2.setGravity(Gravity.CENTER);
        FrameLayout ph = UiKit.roundButton(c, Icons.PHONE, Theme.BLUE, Theme.ON_PASTEL, 96);
        ph.setOnClickListener(v -> host.startApp(new Intent(c, PhoneActivity.class)));
        FrameLayout msg = UiKit.roundButton(c, Icons.MESSAGE, Theme.MINT, Theme.ON_PASTEL, 96);
        msg.setOnClickListener(v -> host.startApp(new Intent(c, MessagesActivity.class)));
        row2.addView(ph);
        row2.addView(UiKit.spacer(c, 0), new LinearLayout.LayoutParams(Theme.dp(c, 18), 1));
        row2.addView(msg);
        l.addView(row2);
        return l;
    }

    // ---- tile 2: media ----
    private View media(Context c) {
        LinearLayout l = page(c, "Media");
        l.addView(UiKit.spacer(c, 20));
        mediaTitle = UiKit.text(c, "Nothing playing", 26, Theme.TEXT, Theme.MEDIUM);
        mediaTitle.setGravity(Gravity.CENTER);
        mediaTitle.setMaxLines(2);
        l.addView(mediaTitle, new LinearLayout.LayoutParams(-1, -2));
        mediaArtist = UiKit.text(c, "Start music in any app", 16, Theme.TEXT_DIM, Theme.REGULAR);
        mediaArtist.setGravity(Gravity.CENTER);
        l.addView(mediaArtist, UiKit.withTop(c, 6));
        l.addView(UiKit.spacer(c, 40));
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout prev = UiKit.roundButton(c, "M6 6h2v12H6zm3.5 6l8.5 6V6z", Theme.SURFACE_HI, Theme.TEXT, 64);
        prev.setOnClickListener(v -> withController(mc -> mc.getTransportControls().skipToPrevious()));
        playBtn = UiKit.roundButton(c, Icons.PLAY, Theme.LILAC, Theme.ON_PASTEL, 88);
        playIcon = (Icons.IconView) playBtn.getChildAt(0);
        playBtn.setOnClickListener(v -> withController(mc -> {
            PlaybackState ps = mc.getPlaybackState();
            if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) mc.getTransportControls().pause();
            else mc.getTransportControls().play();
        }));
        FrameLayout next = UiKit.roundButton(c, "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z", Theme.SURFACE_HI, Theme.TEXT, 64);
        next.setOnClickListener(v -> withController(mc -> mc.getTransportControls().skipToNext()));
        row.addView(prev);
        row.addView(UiKit.spacer(c, 0), new LinearLayout.LayoutParams(Theme.dp(c, 12), 1));
        row.addView(playBtn);
        row.addView(UiKit.spacer(c, 0), new LinearLayout.LayoutParams(Theme.dp(c, 12), 1));
        row.addView(next);
        l.addView(row);
        return l;
    }

    private interface Use { void run(MediaController mc); }

    private MediaController controller() {
        Context c = getContext();
        try {
            MediaSessionManager sm = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> l = sm.getActiveSessions(new ComponentName(c, NotifService.class));
            if (l != null && !l.isEmpty()) return l.get(0);
        } catch (SecurityException ignored) {
            return null;
        } catch (Exception ignored) {}
        return null;
    }

    private void withController(Use u) {
        MediaController mc = controller();
        if (mc != null) u.run(mc);
        else host.startApp(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            updateMedia();
            updateBattery();
            handler.postDelayed(this, 1500);
        }
    };

    private void updateMedia() {
        MediaController mc = controller();
        if (mc == null) {
            mediaTitle.setText("Nothing playing");
            boolean granted = androidx.core.app.NotificationManagerCompat
                    .getEnabledListenerPackages(getContext()).contains(getContext().getPackageName());
            mediaArtist.setText(!granted ? "Allow notification access to control media" : "Start music in any app");
            playIcon.setPath(Icons.PLAY);
            return;
        }
        MediaMetadata md = mc.getMetadata();
        CharSequence t = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_TITLE);
        CharSequence a = md == null ? null : md.getText(MediaMetadata.METADATA_KEY_ARTIST);
        mediaTitle.setText(t == null ? "Unknown" : t);
        mediaArtist.setText(a == null ? "" : a);
        PlaybackState ps = mc.getPlaybackState();
        boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
        playIcon.setPath(playing ? Icons.PAUSE : Icons.PLAY);
    }

    // ---- tile 3: battery ----
    private View battery(Context c) {
        LinearLayout l = page(c, "Battery");
        l.addView(UiKit.spacer(c, 40));
        batteryText = UiKit.text(c, "100%", 64, Theme.TEXT, Theme.LIGHT);
        l.addView(batteryText);
        l.addView(UiKit.spacer(c, 24));
        batteryBar = new android.widget.ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        batteryBar.setMax(100);
        batteryBar.setProgressDrawable(barDrawable(c));
        l.addView(batteryBar, new LinearLayout.LayoutParams(-1, Theme.dp(c, 12)));
        TextView sub = UiKit.text(c, "Charge remaining", 14, Theme.TEXT_DIM, Theme.REGULAR);
        l.addView(sub, UiKit.withTop(c, 12));
        return l;
    }

    private android.graphics.drawable.Drawable barDrawable(Context c) {
        float r = Theme.dpf(c, 6);
        android.graphics.drawable.GradientDrawable bg = Theme.round(0xFF2A2A30, r);
        android.graphics.drawable.GradientDrawable fg = Theme.round(Theme.MINT, r);
        android.graphics.drawable.ClipDrawable clip = new android.graphics.drawable.ClipDrawable(fg, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{bg, clip});
        ld.setId(0, android.R.id.background);
        ld.setId(1, android.R.id.progress);
        return ld;
    }

    private void updateBattery() {
        BatteryManager bm = (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
        int v = bm == null ? 100 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        batteryText.setText(v + "%");
        batteryBar.setProgress(v);
    }

    // ---- tile 4: timer shortcuts ----
    private View timer(Context c) {
        LinearLayout l = page(c, "Timer");
        l.addView(UiKit.spacer(c, 20));
        int[] mins = {1, 5, 10};
        for (int m : mins) {
            LinearLayout p = UiKit.pill(c, Icons.TIMER, Theme.CREAM, m + " min", "Start a timer");
            p.setOnClickListener(v -> host.startApp(new Intent(c, ClockActivity.class)
                    .putExtra("timer_seconds", m * 60)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Theme.dp(c, 10);
            l.addView(p, lp);
        }
        return l;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.post(poll);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(poll);
        super.onDetachedFromWindow();
    }

    public void reset() { pager.setCurrentItem(0, false); }
}
