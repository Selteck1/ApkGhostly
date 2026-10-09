# Kemtiz Desktop for Windows

Kemtiz.exe is a native Qt desktop application, not a website inside a browser. It displays its own Kemtiz window and connects to the local Kemtiz backend started by the executable.

## Important note about Google sign-in

The Kemtiz interface itself does not run in a browser. Google requires installed desktop apps to use the system browser for the account authorization step; this is the supported OAuth flow and avoids the embedded-browser restriction.

### One-time OAuth setup

1. Open [Google Cloud Console — Clients](https://console.cloud.google.com/auth/clients).
2. In the same Google Cloud project used by Kemtiz, create a new OAuth client with type **Desktop app**.
3. Copy the new Desktop Client ID (ending with .apps.googleusercontent.com).
4. Start Kemtiz.exe, click **Настроить Google-вход**, and paste that Desktop Client ID. It is saved in %APPDATA%\Kemtiz\config.json.
5. Keep the existing **Web application** OAuth client and its Client ID. Do not replace it: the desktop and web clients have different purposes.
6. If the consent screen is in testing mode, add your Google account to its test users list.

The executable uses OAuth authorization code + PKCE and a loopback callback on 127.0.0.1. It does not embed a client secret.

## Build and download

Open the repository's **Actions** tab → **Build Kemtiz Desktop and Android** → the latest successful run. The preview release is also published at [GitHub Releases](https://github.com/Selteck1/ApkGhostly/releases). The Windows artifact contains Kemtiz.exe; the Android artifact contains Kemtiz-Android-LAN.apk and Android-OAuth-SHA1.txt.

## Android Google chooser fix

The Android app has a fallback Credential Manager sign-in request for new/unapproved Google accounts. If the chooser still fails, open the latest preview release and read Android-OAuth-SHA1.txt. In Google Cloud Console, open the **Android OAuth client** with package name com.kemtiz.app and set that exact SHA-1 fingerprint. GitHub Actions publishes the SHA-1 fingerprint for the exact APK in each release. After installing a newly built APK, use the fingerprint shipped with that same release; a new debug build can have a different SHA-1. Install the matching Kemtiz-Android-LAN.apk from that release.

## Data and privacy

- Desktop messages and accounts are stored in %APPDATA%\Kemtiz\data on this PC.
- Keep the EXE running while using the desktop app.
- For a phone to connect to the desktop server, both devices must use the same trusted Wi-Fi. The app reports the PC's private LAN address at startup; configure that address in Android's Адрес сервера setting.
- LAN HTTP is not encrypted. Do not connect over public Wi-Fi, do not forward port 8000 from your router, and do not expose this development server to the internet.
- Microsoft Edge or Google Chrome must be installed for Google's one-time sign-in step. The messenger itself remains in its own native Qt window.

## Build locally

With Python 3.12 on Windows, from the repository root:

    python -m pip install -r kemtiz/requirements.txt
    python -m pip install -r kemtiz/desktop/requirements.txt
    python kemtiz/desktop/make_icon.py
    python -m PyInstaller --noconfirm --clean --onefile --windowed --name Kemtiz --icon kemtiz/desktop/kemtiz.ico --paths kemtiz --add-data "kemtiz/server.py;." --add-data "kemtiz/web;web" --collect-all PySide6 --collect-all shiboken6 --collect-submodules uvicorn --hidden-import server kemtiz/desktop/launcher.py

The output is dist/Kemtiz.exe.
