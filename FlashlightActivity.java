package com.wearlauncher;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One huge round button. Glows when on. */
public class FlashlightActivity extends WearActivity {
    private FrameLayout btn;
    private Icons.IconView icon;
    private TextView label;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(UiKit.header(this, "Flashlight", this::finish), new LinearLayout.LayoutParams(-1, -2));

        FrameLayout center = new FrameLayout(this);
        btn = new FrameLayout(this);
        int size = Theme.dp(this, 200);
        icon = new Icons.IconView(this, Icons.FLASHLIGHT, Theme.TEXT);
        icon.setInsetFraction(0.3f);
        btn.addView(icon, new FrameLayout.LayoutParams(size, size));
        center.addView(btn, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        label = UiKit.text(this, "", 18, Theme.TEXT_DIM, Theme.MEDIUM);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        llp.bottomMargin = Theme.dp(this, 90);
        center.addView(label, llp);
        root.addView(center, new LinearLayout.LayoutParams(-1, 0, 1f));

        Theme.pressable(btn);
        btn.setOnClickListener(v -> {
            if (!Torch.toggle(this)) {
                android.widget.Toast.makeText(this, "No flashlight found", android.widget.Toast.LENGTH_SHORT).show();
            }
            btn.postDelayed(this::sync, 120);
        });
        setRoot(root);
        sync();
    }

    @Override protected void onResume() {
        super.onResume();
        sync();
    }

    private void sync() {
        boolean on = Torch.isOn(this);
        btn.setBackground(Theme.circle(on ? Theme.LIME : Theme.SURFACE));
        icon.setColor(on ? Theme.ON_PASTEL : Theme.TEXT);
        label.setText(on ? "On" : "Off");
    }
}
