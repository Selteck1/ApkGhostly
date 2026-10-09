# Kemtiz Messenger — Android-only MVP

Kemtiz is focused exclusively on Android. Registration/login remain Google sign-in; first-time registration asks the user to set a unique username and optional country/about fields.

## Features

- Native Google account picker via Android Credential Manager.
- UI bundled inside the APK; Render does not serve a browser/PWA client.
- Username search and friend requests.
- Direct chats with accepted friends and groups up to 10 participants.
- Message history, online presence, typing indicator, read-marker endpoint, and WebSocket updates.
- Dark UI with a matching violet K/orbit icon.

## API deployment

Base URL: `https://kemtiz-api.onrender.com`

The Android client calls the API over HTTPS and uses WSS for realtime messages. The API root returns JSON service information; `/health` checks health. CORS is restricted to the internal Android WebView origin.

Render environment variables:
- `KEMTIZ_GOOGLE_CLIENT_ID`: Google OAuth Web application client ID used to validate ID-token audience.
- `KEMTIZ_SECRET`: stable private signing secret for sessions.
- `KEMTIZ_DATA_DIR` / `KEMTIZ_DB_PATH`: database path configuration if using SQLite on Render.

SQLite data and the token-signing secret must survive restarts/deploys. If Render's filesystem is ephemeral, configure persistent storage or migrate to managed PostgreSQL before users rely on the app.

## Build

```sh
gradle :app:assembleDebug
```

GitHub Actions builds and uploads a debug APK. Google sign-in for a distributed APK depends on the correct Android OAuth client package name and signing-certificate SHA-1 in Google Cloud Console.

## Limitations

This is an early alpha, not yet a fully secure production messenger. End-to-end encryption, push notifications, account recovery, file uploads, advanced anti-abuse controls, and production monitoring are not implemented.
