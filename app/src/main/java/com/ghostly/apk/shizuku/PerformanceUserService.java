package com.ghostly.apk.shizuku;

import android.os.RemoteException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class PerformanceUserService extends IPerformanceService.Stub {

    public PerformanceUserService() {
    }

    @Override
    public String setFixedPerformance(boolean enabled) throws RemoteException {
        String value = enabled ? "true" : "false";
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{
                "cmd", "power", "set-fixed-performance-mode-enabled", value
            });

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(process.getInputStream(), out);
            copy(process.getErrorStream(), out);

            int code = process.waitFor();
            String result = out.toString(StandardCharsets.UTF_8.name()).trim();

            if (code != 0) {
                throw new RemoteException(
                    "cmd power завершился с кодом " + code +
                    (result.isEmpty() ? "" : ": " + result)
                );
            }

            return result.isEmpty() ? "OK" : result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteException("Команда была прервана.");
        } catch (Exception e) {
            throw new RemoteException(e.getMessage() == null ? "Shizuku command failed." : e.getMessage());
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
