# Ghostly Voice Relay

The relay intentionally contains no room audio storage.

Client protocol:

- Text: `JOIN|ROOMCODE`
- Text response: `ROOM_COUNT|1` or `ROOM_COUNT|2`
- Text response: `ERROR|message`
- Binary frames: 320-byte G.711 μ-law, 16 kHz mono, 20 ms per packet.
- Binary frames are forwarded only to the other peer in the same room.
- Maximum room size: 2 peers.

The Android app should use `wss://<service>.onrender.com/ws`.
