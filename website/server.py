#!/usr/bin/env python3
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parent
PORT = int(os.getenv("GHOSTLY_SITE_PORT", "8080"))
BOT_URL = os.getenv("GHOSTLY_BOT_URL", "https://t.me/Ghostlyso2Bot").strip() or "https://t.me/Ghostlyso2Bot"


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print("[GhostlySite] " + fmt % args)

    def _send_redirect(self):
        self.send_response(302)
        self.send_header("Location", BOT_URL)
        self.send_header("Cache-Control", "no-store")
        self.end_headers()

    def do_GET(self):
        path = urlparse(self.path).path

        if path in {"/go", "/telegram", "/bot"}:
            self._send_redirect()
            return

        if path in {"/", "/index.html"}:
            data = (ROOT / "index.html").read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)
            return

        self.send_response(404)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.end_headers()
        self.wfile.write(b"404 - Ghostly page not found\n")


if __name__ == "__main__":
    print(f"Ghostly website: http://127.0.0.1:{PORT}")
    print(f"Telegram redirect: {BOT_URL}")
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
