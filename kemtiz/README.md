# Kemtiz API

FastAPI backend for the native Android Kemtiz Messenger.

## Run locally

```sh
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export KEMTIZ_SECRET="a-long-random-secret"
uvicorn server:app --host 127.0.0.1 --port 8000
```

The root `/` returns JSON service information, `/health` checks service health, `/api/config` exposes the public Google OAuth Client ID, and `/ws` serves realtime events. The Android application uses native Android views and does not load an HTML client.

The Google OAuth Client ID used by the native app is included as a public identifier in the Android client and as a fallback in `server.py`. An environment variable `KEMTIZ_GOOGLE_CLIENT_ID` can override the fallback. The backend uses HTTPS/WSS.

## Render

Base URL: `https://kemtiz-api.onrender.com`.

Set a stable `KEMTIZ_SECRET` in Render for persistent sessions. SQLite defaults to `data/kemtiz.sqlite3` in this directory; ensure that directory is persistent or set `KEMTIZ_DB_PATH` / `KEMTIZ_DATA_DIR` accordingly. An ephemeral filesystem can lose accounts and chats after restart/redeploy.

## Tests

```sh
python -m unittest -v test_server
```
