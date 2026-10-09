# Kemtiz API

FastAPI backend for the Android-only Kemtiz Messenger.

## Run locally

```sh
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export KEMTIZ_GOOGLE_CLIENT_ID="your-google-web-client-id"
export KEMTIZ_SECRET="a-long-random-secret"
uvicorn server:app --host 127.0.0.1 --port 8000
```

The root `/` returns JSON service information, `/health` checks service health, `/api/config` exposes the public Google client ID, and `/ws` serves realtime events. No browser/PWA UI is served by this backend.

## Render

Base URL: `https://kemtiz-api.onrender.com`.

Configure `KEMTIZ_GOOGLE_CLIENT_ID` and a stable `KEMTIZ_SECRET` as environment variables. SQLite defaults to `data/kemtiz.sqlite3` inside this directory; ensure that directory is persistent or set `KEMTIZ_DB_PATH` / `KEMTIZ_DATA_DIR` accordingly. An ephemeral filesystem can lose accounts and chats on restart/redeploy.

CORS allows only `https://appassets.androidplatform.net`, used by Android WebViewAssetLoader. Remote API and realtime WebSocket use HTTPS/WSS.

## Tests

```sh
python -m unittest -v test_server
```
