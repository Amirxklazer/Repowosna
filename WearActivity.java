package com.wearlauncher;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Base: edge-to-edge black canvas with light system-bar icons. */
public abstract class WearActivity extends AppCompatActivity {
    protected int insetTop, insetBottom;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        WindowCompat.setDecorFitsSystemWindows(w, false);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setBackgroundColor(Theme.BG);
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
        c.setAppearanceLightStatusBars(false);
        c.setAppearanceLightNavigationBars(false);
    }

    /** Sets content and pads it by system bar insets. */
    protected void setRoot(View v) {
        setContentView(v);
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, ins) -> {
            Insets bars = ins.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = ins.getInsets(WindowInsetsCompat.Type.ime());
            insetTop = bars.top;
            insetBottom = Math.max(bars.bottom, ime.bottom);
            view.setPadding(0, insetTop, 0, insetBottom);
            onInsets();
            return ins;
        });
        ViewCompat.requestApplyInsets(v);
    }

    protected void onInsets() {}
}
