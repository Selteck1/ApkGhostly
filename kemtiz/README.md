# Kemtiz API

FastAPI backend for the native Android Kemtiz Messenger.

## Authentication

The native Android client supports username/password registration and login:

- `POST /api/auth/password/register`
- `POST /api/auth/password/login`

Usernames are 3–24 characters (ASCII letters, digits and underscore; the first character must be a letter or digit). Passwords must be 8–128 characters. Passwords are stored as salted PBKDF2-HMAC-SHA256 hashes; the API never returns password hashes.

## Run locally

```sh
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export KEMTIZ_SECRET="a-long-random-secret"
uvicorn server:app --host 127.0.0.1 --port 8000
```

The root `/` returns JSON service information, `/health` checks service health, and `/ws` serves realtime events. The Android app uses native Android views; the client has no embedded HTML interface. Older Google-auth endpoints remain in the backend for compatibility, but the current app presents username/password authentication.

## Render

Base URL: `https://kemtiz-api.onrender.com`.

Set a stable `KEMTIZ_SECRET` in Render for persistent sessions. SQLite defaults to `data/kemtiz.sqlite3` in this directory; ensure that directory is persistent or set `KEMTIZ_DB_PATH` / `KEMTIZ_DATA_DIR` accordingly. An ephemeral filesystem can lose accounts and chats after restart/redeploy. Make sure Render deploys this repository's `kemtiz` directory after updating the source.

Render settings should be:
- **Root Directory:** `kemtiz`
- **Build Command:** `pip install -r requirements.txt`
- **Start Command:** `uvicorn server:app --host 0.0.0.0 --port $PORT`

After deployment, open `/health`. The updated server reports `api_version: "0.3.0"` and `password_auth: true`. If these fields are missing, Render is still running an older backend and the Android registration route will not work.

## Tests

```sh
python -m unittest -v test_server
```
