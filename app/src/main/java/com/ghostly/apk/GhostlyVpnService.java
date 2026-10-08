package com.ghostly.apk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.content.pm.ServiceInfo;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class GhostlyVpnService extends VpnService {
    public static final String ACTION_START = "com.ghostly.apk.START";
    public static final String ACTION_STOP = "com.ghostly.apk.STOP";
    public static final String EXTRA_HOST = "host";
    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_TOKEN = "token";

    private static final int NOTIFICATION_ID = 7801;
    private static final int MTU = 1280;
    private static final String GAME_PACKAGE = "com.axlebolt.standoff2";

    private static volatile boolean running = false;

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicLong sequence = new AtomicLong(1);

    private DatagramSocket relaySocket;
    private ParcelFileDescriptor vpnInterface;
    private ParcelFileDescriptor inputFd;
    private ParcelFileDescriptor outputFd;
    private Thread tunReader;
    private Thread relayReader;
    private String token;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (ACTION_STOP.equals(intent.getAction())) {
            stopTunnel();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(intent.getAction())) {
            String host = intent.getStringExtra(EXTRA_HOST);
            int port = intent.getIntExtra(EXTRA_PORT, 51888);
            token = intent.getStringExtra(EXTRA_TOKEN);

            cleanupResources();
            promoteToForeground(host, port);
            startTunnel(host, port, token);
        }

        return START_NOT_STICKY;
    }

    private void startTunnel(String host, int port, String secret) {
        if (host == null || host.trim().isEmpty() || secret == null || secret.isEmpty()) {
            stopSelf();
            return;
        }

        new Thread(() -> {
            try {
                relaySocket = new DatagramSocket();
                relaySocket.setSoTimeout(1000);
                if (!protect(relaySocket)) {
                    throw new Exception("Не удалось защитить UDP socket от VPN loop.");
                }
                relaySocket.connect(new InetSocketAddress(host.trim(), port));

                byte[] hello = RelayProtocol.handshake(secret);
                relaySocket.send(new DatagramPacket(hello, hello.length));

                byte[] buf = new byte[512];
                DatagramPacket reply = new DatagramPacket(buf, buf.length);
                relaySocket.receive(reply);

                RelayProtocol.HandshakeReply handshake =
                    RelayProtocol.parseHandshakeReply(reply.getData(), reply.getLength(), secret);

                VpnService.Builder builder = new VpnService.Builder()
                    .setSession("Ghostly Booster")
                    .setMtu(MTU)
                    .addAddress(handshake.virtualIp, 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("1.1.1.1");

                try {
                    builder.addAllowedApplication(GAME_PACKAGE);
                } catch (Exception e) {
                    throw new Exception(
                        "Standoff 2 не установлен или пакет игры изменился: " + GAME_PACKAGE
                    );
                }

                vpnInterface = builder.establish();
                if (vpnInterface == null) throw new Exception("Android не создал VPN-интерфейс.");

                inputFd = ParcelFileDescriptor.dup(vpnInterface.getFileDescriptor());
                outputFd = ParcelFileDescriptor.dup(vpnInterface.getFileDescriptor());

                active.set(true);
                running = true;
                startTunnelThreads();

            } catch (Exception e) {
                running = false;
                active.set(false);
                cleanupResources();
                stopSelf();
            }
        }, "GhostlyVpnStart").start();
    }

    private void promoteToForeground(String host, int port) {
        NotificationManager manager =
            (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        String channelId = "ghostly_booster";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                channelId,
                "Ghostly Booster",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Сетевой relay Ghostly для Standoff 2");
            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder =
            Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);

        builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("👻 Ghostly Boost")
            .setContentText("Relay " + host + ":" + port + " • Standoff 2")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE);

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                builder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            );
        } else {
            startForeground(NOTIFICATION_ID, builder.build());
        }
    }

    private void startTunnelThreads() {
        tunReader = new Thread(this::readFromTun, "GhostlyTunReader");
        relayReader = new Thread(this::readFromRelay, "GhostlyRelayReader");
        tunReader.start();
        relayReader.start();
    }

    private void readFromTun() {
        try (FileInputStream in = new FileInputStream(inputFd.getFileDescriptor())) {
            byte[] buffer = new byte[65535];
            while (active.get()) {
                int n = in.read(buffer);
                if (n <= 0) continue;

                byte[] packet = new byte[n];
                System.arraycopy(buffer, 0, packet, 0, n);

                byte[] wrapped = RelayProtocol.data(
                    sequence.getAndIncrement(),
                    packet,
                    token
                );
                relaySocket.send(new DatagramPacket(wrapped, wrapped.length));
            }
        } catch (Exception ignored) {
        } finally {
            active.set(false);
            running = false;
        }
    }

    private void readFromRelay() {
        long lastKeepalive = System.nanoTime();

        try (FileOutputStream out = new FileOutputStream(outputFd.getFileDescriptor())) {
            byte[] buffer = new byte[65535];

            while (active.get()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    relaySocket.receive(packet);

                    RelayProtocol.Packet parsed =
                        RelayProtocol.parsePacket(packet.getData(), packet.getLength(), token);

                    if (parsed.type == RelayProtocol.TYPE_DATA && parsed.payload.length > 0) {
                        out.write(parsed.payload);
                        out.flush();
                    }
                } catch (java.net.SocketTimeoutException timeout) {
                    // send keepalive below
                } catch (Exception ignored) {
                }

                if (System.nanoTime() - lastKeepalive > 5_000_000_000L) {
                    byte[] ping = RelayProtocol.keepalive(
                        sequence.getAndIncrement(),
                        token
                    );
                    relaySocket.send(new DatagramPacket(ping, ping.length));
                    lastKeepalive = System.nanoTime();
                }
            }
        } catch (Exception ignored) {
        } finally {
            active.set(false);
            running = false;
        }
    }

    private synchronized void stopTunnel() {
        active.set(false);
        running = false;

        try { if (relaySocket != null) relaySocket.close(); } catch (Exception ignored) {}
        try { if (inputFd != null) inputFd.close(); } catch (Exception ignored) {}
        try { if (outputFd != null) outputFd.close(); } catch (Exception ignored) {}
        try { if (vpnInterface != null) vpnInterface.close(); } catch (Exception ignored) {}

        relaySocket = null;
        inputFd = null;
        outputFd = null;
        vpnInterface = null;

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopTunnel();
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        stopTunnel();
        super.onRevoke();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
}
