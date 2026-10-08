package com.ghostly.apk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.WindowManager;

public final class PerformanceController {
    private PerformanceController() {}

    public static void enable(Activity activity) {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
            float max = 0f;
            android.view.Display display = activity.getDisplay();
            if (display != null) {
                for (android.view.Display.Mode mode : display.getSupportedModes()) {
                    max = Math.max(max, mode.getRefreshRate());
                }
            }
            if (max > 0f) {
                lp.preferredRefreshRate = max;
                activity.getWindow().setAttributes(lp);
            }
        }
        requestIgnoreBattery(activity);
    }

    public static void disable(Activity activity) {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        lp.preferredRefreshRate = 0f;
        activity.getWindow().setAttributes(lp);
    }

    private static boolean isBatteryUnrestricted(Context context) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    public static void requestIgnoreBattery(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 23 && !isBatteryUnrestricted(context)) {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + context.getPackageName()));
                context.startActivity(i);
            }
        } catch (Exception ignored) {}
    }
}
