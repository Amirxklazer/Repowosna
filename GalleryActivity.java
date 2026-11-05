package com.wearlauncher;

import android.Manifest;
import android.app.RecoverableSecurityException;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Photos + videos grid with a full-screen viewer. Also the system picker / viewer. */
public class GalleryActivity extends WearActivity {
    private static class Item { Uri uri; boolean video; }

    private final List<Item> items = new ArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<>(120);

    private FrameLayout stage;           // grid area
    private FrameLayout viewer;          // full-screen viewer overlay
    private ImageView bigImage;
    private VideoView bigVideo;
    private int viewing = -1;
    private boolean picking;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String act = getIntent().getAction();
        picking = Intent.ACTION_PICK.equals(act) || Intent.ACTION_GET_CONTENT.equals(act);

        FrameLayout root = new FrameLayout(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(UiKit.header(this, picking ? "Choose" : "Gallery", this::finish), new LinearLayout.LayoutParams(-1, -2));
        stage = new FrameLayout(this);
        page.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        buildViewer();
        root.addView(viewer, new FrameLayout.LayoutParams(-1, -1));
        setRoot(root);

        Uri d = getIntent().getData();
        if (Intent.ACTION_VIEW.equals(act) && d != null) {
            Item it = new Item();
            it.uri = d;
            String type = getIntent().getType();
            it.video = type != null && type.startsWith("video");
            items.add(it);
            show(0);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (items.size() <= 1 && Intent.ACTION_VIEW.equals(getIntent().getAction())) return;
        load();
    }

    @Override protected void onDestroy() {
        pool.shutdownNow();
        super.onDestroy();
    }

    // ---------- loading ----------
    private boolean hasMedia() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void askMedia() {
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}, 20);
        else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 20);
    }

    @Override public void onRequestPermissionsResult(int c, String[] p, int[] r) {
        super.onRequestPermissionsResult(c, p, r);
        load();
    }

    private void load() {
        stage.removeAllViews();
        if (!hasMedia()) {
            LinearLayout col = new LinearLayout(this);
            col.setPadding(Theme.dp(this, 16), Theme.dp(this, 10), Theme.dp(this, 16), 0);
            LinearLayout ask = UiKit.pill(this, Icons.PHOTO, Theme.PEACH, "Allow access", "To show your photos and videos");
            ask.setOnClickListener(v -> askMedia());
            col.addView(ask, new LinearLayout.LayoutParams(-1, -2));
            stage.addView(col, new FrameLayout.LayoutParams(-1, -2));
            return;
        }
        items.clear();
        Uri files = MediaStore.Files.getContentUri("external");
        String sel = MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                + " OR " + MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
        try (Cursor c = getContentResolver().query(files,
                new String[]{MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE},
                sel, null, MediaStore.Files.FileColumns.DATE_ADDED + " DESC")) {
            while (c != null && c.moveToNext() && items.size() < 400) {
                Item it = new Item();
                it.video = c.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
                it.uri = ContentUris.withAppendedId(it.video
                        ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0));
                items.add(it);
            }
        } catch (Exception ignored) {}

        if (items.isEmpty()) {
            TextView e = UiKit.text(this, "No photos yet", 17, Theme.TEXT_DIM, Theme.MEDIUM);
            stage.addView(e, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            return;
        }
        stage.addView(grid(), new FrameLayout.LayoutParams(-1, -1));
    }

    private View grid() {
        EdgeScrollView sv = new EdgeScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dp(this, 10);
        col.setPadding(pad, Theme.dp(this, 4), pad, Theme.dp(this, 30));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        int cols = 3;
        int cell = (getResources().getDisplayMetrics().widthPixels - 2 * pad - Theme.dp(this, 6) * (cols * 2)) / cols;
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                col.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            row.addView(thumb(i, cell));
        }
        return sv;
    }

    private View thumb(int index, int cell) {
        Item it = items.get(index);
        FrameLayout f = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cell, cell);
        lp.setMargins(Theme.dp(this, 3), Theme.dp(this, 3), Theme.dp(this, 3), Theme.dp(this, 3));
        f.setLayoutParams(lp);
        f.setBackground(Theme.round(Theme.SURFACE, Theme.dpf(this, 20)));
        f.setClipToOutline(true);
        final float r = Theme.dpf(this, 20);
        f.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        if (it.video) {
            FrameLayout badge = UiKit.roundButton(this, Icons.PLAY, 0x99000000, 0xFFFFFFFF, 26);
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Theme.dp(this, 26), Theme.dp(this, 26), Gravity.BOTTOM | Gravity.START);
            bl.setMargins(Theme.dp(this, 6), 0, 0, Theme.dp(this, 6));
            f.addView(badge, bl);
        }
        loadThumb(it, iv, cell);
        Theme.pressable(f);
        f.setOnClickListener(v -> {
            if (picking) {
                setResult(RESULT_OK, new Intent().setData(it.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                finish();
            } else show(index);
        });
        return f;
    }

    private void loadThumb(Item it, ImageView iv, int size) {
        String key = it.uri.toString();
        Bitmap hit = cache.get(key);
        if (hit != null) { iv.setImageBitmap(hit); return; }
        pool.execute(() -> {
            Bitmap bm = null;
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    bm = getContentResolver().loadThumbnail(it.uri, new Size(size, size), null);
                } else if (!it.video) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 4;
                    try (InputStream is = getContentResolver().openInputStream(it.uri)) {
                        bm = BitmapFactory.decodeStream(is, null, o);
                    }
                }
            } catch (Exception ignored) {}
            final Bitmap res = bm;
            if (res != null) {
                cache.put(key, res);
                main.post(() -> iv.setImageBitmap(res));
            }
        });
    }

    // ---------- viewer ----------
    private void buildViewer() {
        viewer = new FrameLayout(this);
        viewer.setBackgroundColor(0xFF000000);
        viewer.setVisibility(View.GONE);
        bigImage = new ImageView(this);
        bigImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        viewer.addView(bigImage, new FrameLayout.LayoutParams(-1, -1));
        bigVideo = new VideoView(this);
        bigVideo.setVisibility(View.GONE);
        viewer.addView(bigVideo, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        FrameLayout back = UiKit.roundButton(this, Icons.BACK, Theme.SURFACE, Theme.TEXT, 56);
        back.setOnClickListener(v -> hideViewer());
        FrameLayout share = UiKit.roundButton(this, Icons.SHARE, Theme.SURFACE, Theme.TEXT, 56);
        share.setOnClickListener(v -> share());
        FrameLayout del = UiKit.roundButton(this, Icons.DELETE, Theme.PEACH, Theme.ON_PASTEL, 56);
        del.setOnClickListener(v -> delete());
        for (View v : new View[]{back, share, del}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(this, 56), Theme.dp(this, 56));
            lp.setMargins(Theme.dp(this, 10), 0, Theme.dp(this, 10), 0);
            bar.addView(v, lp);
        }
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        blp.bottomMargin = Theme.dp(this, 36);
        viewer.addView(bar, blp);

        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                Item it = viewing >= 0 ? items.get(viewing) : null;
                if (it != null && it.video) {
                    if (bigVideo.isPlaying()) bigVideo.pause(); else bigVideo.start();
                }
                return true;
            }
            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || b == null) return false;
                float dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > Theme.dp(GalleryActivity.this, 60)) {
                    show(Math.max(0, Math.min(items.size() - 1, viewing + (dx < 0 ? 1 : -1))));
                    return true;
                }
                if (dy > Theme.dp(GalleryActivity.this, 90)) { hideViewer(); return true; }
                return false;
            }
        });
        bigImage.setOnTouchListener((v, e) -> gd.onTouchEvent(e));
        bigVideo.setOnTouchListener((v, e) -> gd.onTouchEvent(e));
    }

    private void show(int index) {
        if (index < 0 || index >= items.size()) return;
        viewing = index;
        Item it = items.get(index);
        viewer.setVisibility(View.VISIBLE);
        if (it.video) {
            bigImage.setVisibility(View.GONE);
            bigVideo.setVisibility(View.VISIBLE);
            bigVideo.setVideoURI(it.uri);
            bigVideo.start();
        } else {
            bigVideo.stopPlayback();
            bigVideo.setVisibility(View.GONE);
            bigImage.setVisibility(View.VISIBLE);
            bigImage.setImageURI(null);
            bigImage.setImageURI(it.uri);
        }
    }

    private void hideViewer() {
        bigVideo.stopPlayback();
        viewer.setVisibility(View.GONE);
        viewing = -1;
        if (Intent.ACTION_VIEW.equals(getIntent().getAction()) && items.size() == 1) finish();
    }

    private void share() {
        if (viewing < 0) return;
        Item it = items.get(viewing);
        Intent s = new Intent(Intent.ACTION_SEND).setType(it.video ? "video/*" : "image/*")
                .putExtra(Intent.EXTRA_STREAM, it.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(s, "Share"));
    }

    private void delete() {
        if (viewing < 0) return;
        Item it = items.get(viewing);
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                java.util.ArrayList<Uri> l = new java.util.ArrayList<>();
                l.add(it.uri);
                startIntentSenderForResult(MediaStore.createDeleteRequest(getContentResolver(), l).getIntentSender(), 30, null, 0, 0, 0);
            } else {
                getContentResolver().delete(it.uri, null, null);
                hideViewer();
                load();
            }
        } catch (RecoverableSecurityException e) {
            try { startIntentSenderForResult(e.getUserAction().getActionIntent().getIntentSender(), 30, null, 0, 0, 0); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 30 && res == RESULT_OK) { hideViewer(); load(); }
    }

    @Override public void onBackPressed() {
        if (viewer.getVisibility() == View.VISIBLE) hideViewer(); else super.onBackPressed();
    }
}
