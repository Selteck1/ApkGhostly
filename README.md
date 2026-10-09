# Kemtiz — Android messenger

Kemtiz is an Android-only messaging app. Registration and login use Google sign-in, followed by a unique username and optional profile details.

## Android app

- Interface bundled inside the APK; no PC/browser client is served.
- Dark violet theme and matching Kemtiz K/orbit logo.
- Google sign-in through Android Credential Manager.
- User search, friend requests, private chats, groups (up to 10 members), message history, and live updates.
- API: https://kemtiz-api.onrender.com

## Build the APK

The GitHub Actions workflow **Build Kemtiz Android APK** builds a debug APK on pushes to `main` and `kemtiz-messenger`. Open the Actions run and download the `kemtiz-android-debug` artifact.

Local build with Java 17 and Gradle 8.13:

```sh
gradle :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Backend

The FastAPI service in `kemtiz/` is API-only. Root returns service information; `/health` checks backend health; `/ws` provides realtime events. The browser/PWA frontend is bundled inside Android assets instead of being served by Render.

Set `KEMTIZ_GOOGLE_CLIENT_ID` on Render to the Google OAuth Web client ID used as the server audience. Keep `KEMTIZ_SECRET` stable across deploys and configure persistent storage or a managed database for user/chat data. Do not put secrets in the repository.

## Alpha limitations

Kemtiz does not yet provide end-to-end encryption, push notifications, account recovery, file uploads, or full abuse/rate limiting. Avoid reusing passwords or storing highly sensitive conversations until security work is complete. HTTPS remains enabled for remote API and WebSocket traffic.
