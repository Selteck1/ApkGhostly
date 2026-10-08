package com.ghostly.apk;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class RelayProtocol {
    static final byte[] MAGIC = new byte[]{'G','H','L','Y'};
    static final byte VERSION = 1;
    static final byte TYPE_HANDSHAKE = 1;
    static final byte TYPE_HANDSHAKE_REPLY = 2;
    static final byte TYPE_DATA = 3;
    static final byte TYPE_KEEPALIVE = 4;
    static final int HMAC_SIZE = 16;
    static final int HEADER_SIZE = 4 + 1 + 1 + 8 + 2;

    private RelayProtocol() {}

    static byte[] handshake(String token) throws Exception {
        byte[] t = token.getBytes(StandardCharsets.UTF_8);
        if (t.length == 0 || t.length > 65535) throw new IllegalArgumentException("Неверный токен.");
        ByteBuffer b = ByteBuffer.allocate(4 + 1 + 1 + 2 + t.length);
        b.order(ByteOrder.BIG_ENDIAN);
        b.put(MAGIC).put(VERSION).put(TYPE_HANDSHAKE).putShort((short)t.length).put(t);
        return b.array();
    }

    static byte[] data(long seq, byte[] payload, String token) throws Exception {
        if (payload.length > 65535) throw new IllegalArgumentException("Слишком большой пакет.");
        ByteBuffer b = ByteBuffer.allocate(HEADER_SIZE + payload.length + HMAC_SIZE);
        b.order(ByteOrder.BIG_ENDIAN);
        b.put(MAGIC).put(VERSION).put(TYPE_DATA).putLong(seq).putShort((short)payload.length).put(payload);
        byte[] body = b.array();
        byte[] tag = hmac16(body, token);
        System.arraycopy(tag, 0, body, body.length - HMAC_SIZE, HMAC_SIZE);
        return body;
    }

    static byte[] keepalive(long seq, String token) throws Exception {
        ByteBuffer b = ByteBuffer.allocate(HEADER_SIZE + HMAC_SIZE);
        b.order(ByteOrder.BIG_ENDIAN);
        b.put(MAGIC).put(VERSION).put(TYPE_KEEPALIVE).putLong(seq).putShort((short)0);
        byte[] body = b.array();
        byte[] tag = hmac16(body, token);
        System.arraycopy(tag, 0, body, body.length - HMAC_SIZE, HMAC_SIZE);
        return body;
    }

    static HandshakeReply parseHandshakeReply(byte[] data, int length, String token) throws Exception {
        if (length != 4 + 1 + 1 + 4 + HMAC_SIZE) throw new IllegalArgumentException("Неверный ответ relay.");
        for (int i = 0; i < 4; i++) if (data[i] != MAGIC[i]) throw new IllegalArgumentException("Неверный relay.");
        if (data[4] != VERSION || data[5] != TYPE_HANDSHAKE_REPLY) throw new IllegalArgumentException("Неверная версия relay.");

        byte[] signed = Arrays.copyOfRange(data, 0, 10);
        byte[] expected = hmac16(signed, token);
        byte[] actual = Arrays.copyOfRange(data, 10, 10 + HMAC_SIZE);
        if (!MessageDigest.isEqual(expected, actual)) throw new IllegalArgumentException("Неверный токен relay.");

        String ip = (data[6] & 0xff) + "." +
                    (data[7] & 0xff) + "." +
                    (data[8] & 0xff) + "." +
                    (data[9] & 0xff);
        return new HandshakeReply(ip);
    }

    static Packet parsePacket(byte[] data, int length, String token) throws Exception {
        if (length < HEADER_SIZE + HMAC_SIZE) throw new IllegalArgumentException("Пакет relay слишком короткий.");
        for (int i = 0; i < 4; i++) if (data[i] != MAGIC[i]) throw new IllegalArgumentException("Неверный relay.");
        if (data[4] != VERSION) throw new IllegalArgumentException("Неподдерживаемая версия relay.");

        int payloadLength = ByteBuffer.wrap(data, 14, 2).order(ByteOrder.BIG_ENDIAN).getShort() & 0xffff;
        int expectedLength = HEADER_SIZE + payloadLength + HMAC_SIZE;
        if (expectedLength != length) throw new IllegalArgumentException("Неверная длина relay-пакета.");

        byte[] signed = Arrays.copyOfRange(data, 0, HEADER_SIZE + payloadLength);
        byte[] expected = hmac16(signed, token);
        byte[] actual = Arrays.copyOfRange(data, expectedLength - HMAC_SIZE, expectedLength);
        if (!MessageDigest.isEqual(expected, actual)) throw new IllegalArgumentException("Неверная подпись relay.");

        long seq = ByteBuffer.wrap(data, 6, 8).order(ByteOrder.BIG_ENDIAN).getLong();
        byte type = data[5];
        byte[] payload = Arrays.copyOfRange(data, HEADER_SIZE, HEADER_SIZE + payloadLength);
        return new Packet(type, seq, payload);
    }

    private static byte[] hmac16(byte[] data, String token) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Arrays.copyOf(mac.doFinal(data), HMAC_SIZE);
    }

    static final class HandshakeReply {
        final String virtualIp;
        HandshakeReply(String ip) { this.virtualIp = ip; }
    }

    static final class Packet {
        final byte type;
        final long sequence;
        final byte[] payload;
        Packet(byte type, long sequence, byte[] payload) {
            this.type = type;
            this.sequence = sequence;
            this.payload = payload;
        }
    }
}
