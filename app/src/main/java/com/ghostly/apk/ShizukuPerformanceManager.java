package com.ghostly.apk;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.IBinder;

import rikka.shizuku.Shizuku;

import android.content.ServiceConnection;

import com.ghostly.apk.shizuku.IPerformanceService;
import com.ghostly.apk.shizuku.PerformanceUserService;

public final class ShizukuPerformanceManager {
    private static final int REQUEST_CODE = 9401;
    private final Activity activity;

    private Shizuku.UserServiceArgs serviceArgs;
    private ServiceConnection connection;
    private IPerformanceService remote;
    private Shizuku.OnRequestPermissionResultListener permissionListener;
    private Callback pendingCallback;

    public interface Callback {
        void onSuccess(String details);
        void onFailure(String message);
    }

    public ShizukuPerformanceManager(Activity activity) {
        this.activity = activity;
        permissionListener = (requestCode, result) -> {
            if (requestCode != REQUEST_CODE) return;
            Callback callback = pendingCallback;
            pendingCallback = null;
            if (result == PackageManager.PERMISSION_GRANTED) {
                bindAndSet(true, fps, callback);
            } else if (callback != null) {
                callback.onFailure("Shizuku не дал разрешение.");
            }
        };
        Shizuku.addRequestPermissionResultListener(permissionListener);
    }

    public boolean isAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean hasPermission() {
        try {
            return isAvailable() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void enable(int fps, Callback callback) {
        if (!isAvailable()) {
            callback.onFailure(
                "Shizuku не запущен. Установи Shizuku и запусти его через «Беспроводную отладку»."
            );
            return;
        }

        if (!hasPermission()) {
            pendingCallback = callback;
            try {
                Shizuku.requestPermission(REQUEST_CODE);
            } catch (Throwable e) {
                pendingCallback = null;
                callback.onFailure("Не удалось запросить разрешение Shizuku: " + safe(e));
            }
            return;
        }

        bindAndSet(true, fps, callback);
    }

    public void disable(Callback callback) {
        if (remote != null) {
            execute(false, 60, callback);
            return;
        }

        if (!isAvailable() || !hasPermission()) {
            callback.onSuccess("Shizuku performance уже не управляется Ghostly.");
            return;
        }

        bindAndSet(false, 60, callback);
    }

    private void bindAndSet(boolean enabled, int fps, Callback callback) {
        if (remote != null) {
            execute(enabled, fps, callback);
            return;
        }

        try {
            serviceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(activity, PerformanceUserService.class)
            )
                .daemon(false)
                .processNameSuffix("ghostly_perf")
                .version(1)
                .tag("ghostly-performance");

            connection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder binder) {
                    remote = IPerformanceService.Stub.asInterface(binder);
                    execute(enabled, callback);
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    remote = null;
                }
            };

            Shizuku.bindUserService(serviceArgs, connection);
        } catch (Throwable e) {
            callback.onFailure("Не удалось запустить Shizuku UserService: " + safe(e));
        }
    }

    private void execute(boolean enabled, int fps, Callback callback) {
        new Thread(() -> {
            try {
                String result = remote.setPerformance(enabled, fps);
                if (callback != null) {
                    activity.runOnUiThread(() ->
                        callback.onSuccess(result)
                    );
                }

                if (!enabled) {
                    cleanupRemoteService();
                }
            } catch (Throwable e) {
                if (callback != null) {
                    activity.runOnUiThread(() ->
                        callback.onFailure("Команда производительности не выполнена: " + safe(e))
                    );
                }
            }
        }, "GhostlyShizukuPerf").start();
    }

    public void shutdown() {
        if (remote == null) {
            removeListener();
            return;
        }

        new Thread(() -> {
            try {
                remote.setPerformance(false, 60);
            } catch (Throwable ignored) {
            } finally {
                cleanupRemoteService();
                removeListener();
            }
        }, "GhostlyShizukuShutdown").start();
    }

    private void cleanupRemoteService() {
        try {
            if (serviceArgs != null) {
                Shizuku.unbindUserService(serviceArgs, connection, true);
            }
        } catch (Throwable ignored) {
        }
        remote = null;
        connection = null;
        serviceArgs = null;
    }

    private void removeListener() {
        try {
            if (permissionListener != null) {
                Shizuku.removeRequestPermissionResultListener(permissionListener);
            }
        } catch (Throwable ignored) {
        }
        permissionListener = null;
    }

    private static String safe(Throwable e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }
}
