package com.wearlauncher;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;

/** Shared flashlight control used by Quick Settings and the Flashlight app. */
public final class Torch {
    private Torch() {}

    private static boolean on = false;
    private static String camId;
    private static boolean registered = false;

    private static void init(Context c) {
        if (registered) return;
        CameraManager cm = (CameraManager) c.getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (flash != null && flash && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    camId = id;
                    break;
                }
            }
            cm.registerTorchCallback(new CameraManager.TorchCallback() {
                @Override public void onTorchModeChanged(String id, boolean enabled) {
                    if (id.equals(camId)) on = enabled;
                }
            }, null);
            registered = true;
        } catch (Exception ignored) {}
    }

    public static boolean available(Context c) {
        init(c);
        return camId != null;
    }

    public static boolean isOn(Context c) {
        init(c);
        return on;
    }

    public static boolean set(Context c, boolean enable) {
        init(c);
        if (camId == null) return false;
        try {
            CameraManager cm = (CameraManager) c.getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
            cm.setTorchMode(camId, enable);
            on = enable;
            return true;
        } catch (CameraAccessException | IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean toggle(Context c) {
        return set(c, !isOn(c));
    }
}
