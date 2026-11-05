package com.wearlauncher;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Photo + video camera. Also answers IMAGE_CAPTURE / VIDEO_CAPTURE from other apps. */
public class CameraActivity extends WearActivity {
    private PreviewView preview;
    private ProcessCameraProvider provider;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private Camera camera;
    private boolean back = true;
    private boolean videoMode = false;
    private boolean external;                 // launched by another app for a result
    private Uri outputUri;

    private Shutter shutter;
    private TextView modePhoto, modeVideo, timer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long recStart;

    private static class Shutter extends View {
        boolean video, rec;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Shutter(android.content.Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, R = Math.min(cx, cy);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(R * 0.08f);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(cx, cy, R * 0.92f, p);
            p.setStyle(Paint.Style.FILL);
            if (rec) {
                p.setColor(0xFFFF6B5E);
                float s = R * 0.36f;
                c.drawRoundRect(cx - s, cy - s, cx + s, cy + s, R * 0.14f, R * 0.14f, p);
            } else {
                p.setColor(video ? 0xFFFF6B5E : 0xFFFFFFFF);
                c.drawCircle(cx, cy, R * 0.72f, p);
            }
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String act = getIntent().getAction();
        external = act != null && (act.equals(MediaStore.ACTION_IMAGE_CAPTURE) || act.equals(MediaStore.ACTION_VIDEO_CAPTURE));
        if (MediaStore.ACTION_VIDEO_CAPTURE.equals(act) || "android.media.action.VIDEO_CAMERA".equals(act)) videoMode = true;
        outputUri = getIntent().getParcelableExtra(MediaStore.EXTRA_OUTPUT);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);

        preview = new PreviewView(this);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        preview.setClipToOutline(true);
        final float rad = Theme.dpf(this, 32);
        preview.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad); }
        });
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-1, -1);
        plp.setMargins(Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 8), Theme.dp(this, 168));
        root.addView(preview, plp);

        // back button over the preview
        FrameLayout backBtn = UiKit.roundButton(this, Icons.BACK, 0x66000000, 0xFFFFFFFF, 44);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Theme.dp(this, 44), Theme.dp(this, 44), Gravity.TOP | Gravity.START);
        blp.setMargins(Theme.dp(this, 20), Theme.dp(this, 20), 0, 0);
        backBtn.setOnClickListener(v -> finish());
        root.addView(backBtn, blp);
        root.setTag(backBtn);

        timer = UiKit.text(this, "00:00", 15, 0xFFFFFFFF, Theme.MEDIUM);
        timer.setBackground(Theme.round(0xCCE5484D, Theme.dpf(this, 16)));
        timer.setPadding(Theme.dp(this, 12), Theme.dp(this, 6), Theme.dp(this, 12), Theme.dp(this, 6));
        timer.setVisibility(View.GONE);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tlp.topMargin = Theme.dp(this, 24);
        root.addView(timer, tlp);

        // bottom controls
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout modes = new LinearLayout(this);
        modes.setBackground(Theme.round(Theme.SURFACE, Theme.dpf(this, 22)));
        int mp = Theme.dp(this, 4);
        modes.setPadding(mp, mp, mp, mp);
        modePhoto = modeChip("Photo");
        modeVideo = modeChip("Video");
        modePhoto.setOnClickListener(v -> setMode(false));
        modeVideo.setOnClickListener(v -> setMode(true));
        modes.addView(modePhoto, new LinearLayout.LayoutParams(Theme.dp(this, 96), -2));
        modes.addView(modeVideo, new LinearLayout.LayoutParams(Theme.dp(this, 96), -2));
        bottom.addView(modes, new LinearLayout.LayoutParams(-2, -2));

        FrameLayout row = new FrameLayout(this);
        shutter = new Shutter(this);
        shutter.setOnClickListener(v -> onShutter());
        Theme.pressable(shutter);
        row.addView(shutter, new FrameLayout.LayoutParams(Theme.dp(this, 84), Theme.dp(this, 84), Gravity.CENTER));
        FrameLayout gal = UiKit.roundButton(this, Icons.PHOTO, Theme.SURFACE, Theme.TEXT, 56);
        gal.setOnClickListener(v -> startActivity(new Intent(this, GalleryActivity.class)));
        row.addView(gal, new FrameLayout.LayoutParams(Theme.dp(this, 56), Theme.dp(this, 56), Gravity.START | Gravity.CENTER_VERTICAL));
        FrameLayout flip = UiKit.roundButton(this, Icons.FLIP_CAMERA, Theme.SURFACE, Theme.TEXT, 56);
        flip.setOnClickListener(v -> { back = !back; bind(); });
        row.addView(flip, new FrameLayout.LayoutParams(Theme.dp(this, 56), Theme.dp(this, 56), Gravity.END | Gravity.CENTER_VERTICAL));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, Theme.dp(this, 90));
        rlp.topMargin = Theme.dp(this, 12);
        rlp.leftMargin = rlp.rightMargin = Theme.dp(this, 36);
        bottom.addView(row, rlp);

        FrameLayout.LayoutParams bplp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bplp.bottomMargin = Theme.dp(this, 20);
        root.addView(bottom, bplp);
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            bplp.bottomMargin = ins.getSystemWindowInsetBottom() + Theme.dp(this, 14);
            bottom.setLayoutParams(bplp);
            ((FrameLayout.LayoutParams) backBtn.getLayoutParams()).topMargin = ins.getSystemWindowInsetTop() + Theme.dp(this, 14);
            ((FrameLayout.LayoutParams) timer.getLayoutParams()).topMargin = ins.getSystemWindowInsetTop() + Theme.dp(this, 18);
            ((FrameLayout.LayoutParams) preview.getLayoutParams()).topMargin = ins.getSystemWindowInsetTop() + Theme.dp(this, 6);
            return ins;
        });
        setContentView(root);

        // pinch to zoom
        ScaleGestureDetector sg = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                if (camera != null) {
                    float z = camera.getCameraInfo().getZoomState().getValue().getZoomRatio() * d.getScaleFactor();
                    camera.getCameraControl().setZoomRatio(z);
                }
                return true;
            }
        });
        preview.setOnTouchListener((v, e) -> { sg.onTouchEvent(e); return true; });

        refreshChips();
        needPermissions();
    }

    private TextView modeChip(String s) {
        TextView t = UiKit.text(this, s, 14, Theme.TEXT_DIM, Theme.MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Theme.dp(this, 9), 0, Theme.dp(this, 9));
        return t;
    }

    private void refreshChips() {
        modePhoto.setBackground(videoMode ? null : Theme.round(Theme.LILAC, Theme.dpf(this, 18)));
        modeVideo.setBackground(videoMode ? Theme.round(Theme.LILAC, Theme.dpf(this, 18)) : null);
        modePhoto.setTextColor(videoMode ? Theme.TEXT_DIM : Theme.ON_PASTEL);
        modeVideo.setTextColor(videoMode ? Theme.ON_PASTEL : Theme.TEXT_DIM);
        shutter.video = videoMode;
        shutter.invalidate();
    }

    private void setMode(boolean video) {
        if (recording != null || video == videoMode) return;
        videoMode = video;
        refreshChips();
        bind();
    }

    // ---------- permissions + binding ----------
    private boolean has(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    private void needPermissions() {
        if (has(Manifest.permission.CAMERA)) { bind(); return; }
        requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 10);
    }

    @Override public void onRequestPermissionsResult(int c, String[] p, int[] r) {
        super.onRequestPermissionsResult(c, p, r);
        if (has(Manifest.permission.CAMERA)) bind();
        else { Toast.makeText(this, "Camera permission needed", Toast.LENGTH_LONG).show(); }
    }

    private void bind() {
        ListenableFuture<ProcessCameraProvider> f = ProcessCameraProvider.getInstance(this);
        f.addListener(() -> {
            try {
                provider = f.get();
                provider.unbindAll();
                Preview pv = new Preview.Builder().build();
                pv.setSurfaceProvider(preview.getSurfaceProvider());
                CameraSelector sel = back ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;
                if (videoMode) {
                    Recorder rec = new Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(Quality.FHD,
                                    androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                            .build();
                    videoCapture = VideoCapture.withOutput(rec);
                    camera = provider.bindToLifecycle(this, sel, pv, videoCapture);
                } else {
                    imageCapture = new ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
                    camera = provider.bindToLifecycle(this, sel, pv, imageCapture);
                }
            } catch (Exception e) {
                Toast.makeText(this, "Camera unavailable", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // ---------- capture ----------
    private String stamp() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()); }

    private void onShutter() {
        if (!has(Manifest.permission.CAMERA)) { needPermissions(); return; }
        if (videoMode) toggleRecording(); else takePhoto();
    }

    private void takePhoto() {
        if (imageCapture == null) return;
        shutter.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                .withEndAction(() -> shutter.animate().scaleX(1f).scaleY(1f).setDuration(140).start()).start();
        ImageCapture.OutputFileOptions opts;
        if (external && outputUri != null) {
            try {
                opts = new ImageCapture.OutputFileOptions.Builder(getContentResolver().openOutputStream(outputUri)).build();
            } catch (Exception e) { toast("Can't write file"); return; }
        } else {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_" + stamp());
            v.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/WearCamera");
            opts = new ImageCapture.OutputFileOptions.Builder(getContentResolver(),
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v).build();
        }
        imageCapture.takePicture(opts, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(ImageCapture.OutputFileResults r) {
                if (external) {
                    Uri u = outputUri != null ? outputUri : r.getSavedUri();
                    setResult(RESULT_OK, new Intent().setData(u));
                    finish();
                } else toast("Photo saved");
            }
            @Override public void onError(ImageCaptureException e) { toast("Couldn't take photo"); }
        });
    }

    private void toggleRecording() {
        if (videoCapture == null) return;
        if (recording != null) { recording.stop(); return; }
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_" + stamp());
        v.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= 29) v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/WearCamera");
        MediaStoreOutputOptions o = new MediaStoreOutputOptions.Builder(getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(v).build();
        androidx.camera.video.PendingRecording pr = videoCapture.getOutput().prepareRecording(this, o);
        if (has(Manifest.permission.RECORD_AUDIO)) pr = pr.withAudioEnabled();
        recording = pr.start(ContextCompat.getMainExecutor(this), ev -> {
            if (ev instanceof VideoRecordEvent.Start) {
                shutter.rec = true; shutter.invalidate();
                recStart = System.currentTimeMillis();
                timer.setVisibility(View.VISIBLE);
                handler.post(tick);
            } else if (ev instanceof VideoRecordEvent.Finalize) {
                VideoRecordEvent.Finalize fin = (VideoRecordEvent.Finalize) ev;
                recording = null;
                shutter.rec = false; shutter.invalidate();
                timer.setVisibility(View.GONE);
                handler.removeCallbacks(tick);
                if (!fin.hasError()) {
                    if (external) {
                        setResult(RESULT_OK, new Intent().setData(fin.getOutputResults().getOutputUri()));
                        finish();
                    } else toast("Video saved");
                } else toast("Recording failed");
            }
        });
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long s = (System.currentTimeMillis() - recStart) / 1000;
            timer.setText(String.format(Locale.US, "%02d:%02d", s / 60, s % 60));
            handler.postDelayed(this, 500);
        }
    };

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override protected void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }
}
