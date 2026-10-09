# Kemtiz Desktop for Windows

This is a Windows desktop window, not a browser tab. It starts the Kemtiz API on this PC and opens the Kemtiz interface in Microsoft Edge WebView2.

## Download

Open the repository's Actions tab → Build Kemtiz Desktop → the latest successful run → download the Kemtiz-Desktop-Windows artifact. It contains Kemtiz.exe.

## Run

1. Start Kemtiz.exe.
2. If Windows Firewall asks, allow access on Private networks only if you plan to connect a phone over your own Wi-Fi.
3. The app opens in its own window and shows the addresses available on this PC.
4. Sign in with Google. The Web OAuth client must include http://localhost:8000 in Authorized JavaScript origins.
5. To use the same Kemtiz data from Android on the same Wi-Fi, open Kemtiz on Android → Адрес сервера → enter the private-LAN address shown by the desktop app, for example http://192.168.1.20:8000/.

## Important limits

- The Windows PC is the host. The desktop app must remain running while the phone connects.
- The PC and phone must be on the same trusted Wi-Fi/local network. This does not make Kemtiz available over the public internet.
- LAN mode uses plain HTTP, without transport encryption. Use only a network you trust; do not port-forward port 8000 or share these addresses over public Wi-Fi.
- The desktop database is stored in %APPDATA%\Kemtiz\data.
- On first run, Windows may ask for firewall permission. Allow only on a private network.
- Microsoft Edge WebView2 Runtime is required. If Kemtiz reports that it is missing, install the official Evergreen WebView2 Runtime.

## Build locally

With Python 3.12 on Windows, from the repository root:

    python -m pip install -r kemtiz/requirements.txt
    python -m pip install -r kemtiz/desktop/requirements.txt
    python kemtiz/desktop/make_icon.py
    python -m PyInstaller --noconfirm --clean --onefile --windowed --name Kemtiz --icon kemtiz/desktop/kemtiz.ico --paths kemtiz --add-data "kemtiz/server.py;." --add-data "kemtiz/web;web" --collect-all webview --collect-submodules uvicorn kemtiz/desktop/launcher.py

The output is dist/Kemtiz.exe.
