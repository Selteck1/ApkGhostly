const express = require("express");
const http = require("http");
const WebSocket = require("ws");

const app = express();
app.get("/", (_req, res) => res.json({
  ok: true,
  service: "Ghostly Voice Relay",
  protocol: "wss://.../ws"
}));

const server = http.createServer(app);
const wss = new WebSocket.Server({ server, path: "/ws" });
const rooms = new Map();

function leave(ws) {
  const room = ws.room;
  if (!room) return;
  const peers = rooms.get(room);
  if (!peers) return;
  peers.delete(ws);
  for (const peer of peers) {
    if (peer.readyState === WebSocket.OPEN) {
      peer.send(`ROOM_COUNT|${peers.size}`);
    }
  }
  if (peers.size === 0) rooms.delete(room);
  ws.room = null;
}

wss.on("connection", (ws) => {
  ws.isAlive = true;
  ws.on("pong", () => { ws.isAlive = true; });

  ws.on("message", (data, isBinary) => {
    if (!isBinary) {
      const text = data.toString();
      if (text.startsWith("JOIN|")) {
        const room = text.slice(5).trim().toUpperCase();
        if (!/^[A-Z0-9]{6,16}$/.test(room)) {
          ws.send("ERROR|Неверный код комнаты");
          return;
        }
        if (ws.room) leave(ws);

        let peers = rooms.get(room);
        if (!peers) {
          peers = new Set();
          rooms.set(room, peers);
        }
        if (peers.size >= 2) {
          ws.send("ERROR|Комната уже заполнена");
          ws.close(1008, "room full");
          return;
        }

        ws.room = room;
        peers.add(ws);
        ws.send(`ROOM_COUNT|${peers.size}`);
        for (const peer of peers) {
          if (peer !== ws && peer.readyState === WebSocket.OPEN) {
            peer.send(`ROOM_COUNT|${peers.size}`);
          }
        }
        return;
      }
      return;
    }

    const room = ws.room;
    if (!room) return;
    const peers = rooms.get(room);
    if (!peers) return;

    for (const peer of peers) {
      if (peer !== ws && peer.readyState === WebSocket.OPEN) {
        peer.send(data, { binary: true });
      }
    }
  });

  ws.on("close", () => leave(ws));
  ws.on("error", () => leave(ws));
});

const heartbeat = setInterval(() => {
  for (const ws of wss.clients) {
    if (!ws.isAlive) {
      ws.terminate();
      continue;
    }
    ws.isAlive = false;
    ws.ping();
  }
}, 30000);

function shutdown() {
  clearInterval(heartbeat);
  for (const ws of wss.clients) {
    try { ws.close(1001, "server shutdown"); } catch (_) {}
  }
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 5000).unref();
}

process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);

const port = Number(process.env.PORT || 10000);
server.listen(port, "0.0.0.0", () => {
  console.log(`Ghostly Voice Relay listening on ${port}`);
});
