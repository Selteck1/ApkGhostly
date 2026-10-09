# Kemtiz Messenger — early MVP

Kemtiz is the working name. It can be changed later without changing the message database or protocol.

## What exists in this first foundation

- Responsive web/PWA interface for phone and desktop browsers.
- Username + password registration and login.
- Passwords hashed with Python's built-in scrypt implementation.
- Signed 30-day session tokens (the secret is generated locally and stored outside source code).
- User search and friend requests.
- Accept/decline friend requests.
- Direct chats between accepted friends.
- Group creation (up to 10 members total).
- Message history and live message delivery over WebSocket.
- Online presence, typing indicator, read marker endpoint.
- Soft deletion of a user's own message.
- SQLite WAL database; no separate database service required during phone-hosted testing.
- Health endpoint at `/health`.

## Important alpha limitations

This is a functional foundation, not a production-safe public messenger yet. It currently has no end-to-end encryption, push notifications, file uploads, account recovery, robust abuse/rate limiting, moderation tools, or multi-server presence. Do not use a password that you use for other services. Keep the server private until security hardening is complete. The current typing indicator is basic and the read marker is stored, but per-message read receipts are not yet shown in the interface.

## Run locally on Android with Termux

Install Python in Termux, then from the repository root:

```sh
pkg update
pkg install python
python -m venv .venv
source .venv/bin/activate
pip install -r kemtiz/requirements.txt
cd kemtiz
uvicorn server:app --host 127.0.0.1 --port 8000
```

Open `http://127.0.0.1:8000` in the phone's browser.

The SQLite database and generated token secret are stored in `kemtiz/data/`. Back up that directory. **Never commit it to Git.**

## Access from another device

For a temporary test, use a secure HTTPS tunnel (for example Cloudflare Tunnel) pointing to `http://127.0.0.1:8000`. Use the generated HTTPS URL on the PC/second phone; the browser automatically selects WSS for realtime traffic. A temporary quick-tunnel URL can change after a restart. Do not publish the address widely until auth throttling, rate limits, abuse protection and recovery are implemented.

## Moving to a VPS later

The app reads `KEMTIZ_DB_PATH`, `KEMTIZ_DATA_DIR`, and `KEMTIZ_SECRET` from the environment. Keep the same token secret during migration. For production, migrate SQLite data to PostgreSQL, put the API behind HTTPS (Nginx/Caddy), add automated encrypted backups, error monitoring and push notifications. The browser UI is served by the same backend, so phone and desktop use the same API and account system.

## API notes

- Interactive API docs: `/api/docs`
- Health: `/health`
- WebSocket: `/ws`; the first frame must be JSON `{"type":"auth","token":"..."}`
- API sessions use `Authorization: Bearer <token>`.

## License

Not chosen yet.
