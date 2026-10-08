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
        if (enabled) {
            StringBuilder details = new StringBuilder();

            try {
                details.append(runCommand(
                    "cmd", "game", "set",
                    "--mode", "performance",
                    "--fps", "60",
                    GAME_PACKAGE
                ));
            } catch (Exception e) {
                details.append("Game Mode 60 FPS недоступен: ")
                    .append(e.getMessage());
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

        StringBuilder details = new StringBuilder();

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

            copy(process.getInputStream(), stdout);
            copy(process.getErrorStream(), stderr);

            int code = process.waitFor();

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

    private static void copy(InputStream in, ByteArrayOutputStream out) throws Exception {
        byte[] buffer = new byte[1024];
        int n;

        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
    }
}
