#!/usr/bin/env python3
import json
import os
import socket
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = os.getenv("GHOSTLY_SERVER_HOST", "0.0.0.0")
PORT = int(os.getenv("GHOSTLY_SERVER_PORT", "8081"))
VERSION = "0.1.0"

class Handler(BaseHTTPRequestHandler):
    def _json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/api/health":
            self._json(200, {
                "ok": True,
                "service": "ghostly-api",
                "version": VERSION,
                "server_time": datetime.now(timezone.utc).isoformat(),
                "hostname": socket.gethostname(),
            })
            return

        if self.path == "/":
            self._json(200, {
                "ok": True,
                "service": "ghostly-api",
                "message": "Ghostly server is running",
            })
            return

        self._json(404, {
            "ok": False,
            "error": "not_found",
        })

    def log_message(self, fmt, *args):
        print("[Ghostly API] " + (fmt % args))

if __name__ == "__main__":
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"Ghostly API listening on http://{HOST}:{PORT}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Ghostly API stopped.")
    finally:
        server.server_close()
