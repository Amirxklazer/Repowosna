package com.wearlauncher;

import android.content.Intent;

/** What panels need from the launcher. */
public interface Host {
    void closePanel();
    void startApp(Intent intent);
    void openPanel(String name);
    default void requestHome() {}
}
