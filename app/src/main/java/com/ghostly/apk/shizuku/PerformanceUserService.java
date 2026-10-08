package com.ghostly.apk.shizuku;

import android.os.RemoteException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class PerformanceUserService extends IPerformanceService.Stub {

    private static final String GAME_PACKAGE = "com.axlebolt.standoff2";

    public PerformanceUserService() {
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public String setFixedPerformance(boolean enabled) throws RemoteException {
        return setPerformance(enabled, 60);
    }

    @Override
    public String setPerformance(boolean enabled, int fps) throws RemoteException {
        if (fps != 60 && fps != 90) {
            throw new RemoteException("Поддерживаются профили только 60 и 90 FPS.");
        }

        StringBuilder details = new StringBuilder();

        if (enabled) {
            try {
                details.append(runCommand(
                    "cmd", "game", "set",
                    "--mode", "performance",
                    "--fps", String.valueOf(fps),
                    GAME_PACKAGE
                ));
            } catch (Exception e) {
                details.append("Game Mode ").append(fps)
                    .append(" FPS недоступен: ").append(e.getMessage());
            }

            details.append("\n");

            try {
                details.append(runCommand(
                    "cmd", "power",
                    "set-fixed-performance-mode-enabled",
                    "true"
                ));
            } catch (Exception e) {
                details.append("Fixed Performance недоступен: ")
                    .append(e.getMessage());
            }

            return details.toString().trim();
        }

        try {
            details.append(runCommand(
                "cmd", "game", "reset",
                "--mode", "performance",
                GAME_PACKAGE
            ));
        } catch (Exception e) {
            details.append("Game Mode reset: ").append(e.getMessage());
        }

        details.append("\n");

        try {
            details.append(runCommand(
                "cmd", "power",
                "set-fixed-performance-mode-enabled",
                "false"
            ));
        } catch (Exception e) {
            details.append("Fixed Performance reset: ").append(e.getMessage());
        }

        return details.toString().trim();
    }

    private static String runCommand(String... command) throws Exception {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(command);

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            Thread outThread = new Thread(() -> copyQuiet(processInput(process), stdout), "ghostly-shizuku-out");
            Thread errThread = new Thread(() -> copyQuiet(processError(process), stderr), "ghostly-shizuku-err");
            outThread.start();
            errThread.start();

            int code = process.waitFor();
            outThread.join(2000);
            errThread.join(2000);

            String out = stdout.toString(StandardCharsets.UTF_8.name()).trim();
            String err = stderr.toString(StandardCharsets.UTF_8.name()).trim();

            if (code != 0) {
                throw new Exception(
                    "код " + code +
                    (err.isEmpty() ? (out.isEmpty() ? "" : ": " + out) : ": " + err)
                );
            }

            return out.isEmpty() ? "OK" : out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Команда была прервана.");
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static InputStream processInput(Process process) {
        return process.getInputStream();
    }

    private static InputStream processError(Process process) {
        return process.getErrorStream();
    }

    private static void copyQuiet(InputStream in, ByteArrayOutputStream out) {
        try {
            byte[] buffer = new byte[1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        } catch (Exception ignored) {
        } finally {
            try { in.close(); } catch (Exception ignored) {}
        }
    }
}
