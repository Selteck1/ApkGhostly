from __future__ import annotations

import ipaddress
import logging
import os
import socket
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

APP_TITLE = "Kemtiz"
PORT = 8000
DEFAULT_GOOGLE_CLIENT_ID = (
    "649066614178-f3ld6uvr9pupplnsq11k53673c2pft4o.apps.googleusercontent.com"
)


def app_paths() -> tuple[Path, Path]:
    if getattr(sys, "frozen", False):
        bundle_root = Path(getattr(sys, "_MEIPASS", Path(sys.executable).parent))
    else:
        bundle_root = Path(__file__).resolve().parents[1]

    appdata = Path(os.environ.get("APPDATA", str(Path.home()))) / "Kemtiz"
    data_dir = appdata / "data"
    appdata.mkdir(parents=True, exist_ok=True)
    data_dir.mkdir(parents=True, exist_ok=True)

    os.environ.setdefault("KEMTIZ_DATA_DIR", str(data_dir))
    os.environ.setdefault("KEMTIZ_DB_PATH", str(data_dir / "kemtiz.sqlite3"))
    os.environ.setdefault("KEMTIZ_GOOGLE_CLIENT_ID", DEFAULT_GOOGLE_CLIENT_ID)

    if str(bundle_root) not in sys.path:
        sys.path.insert(0, str(bundle_root))
    return bundle_root, appdata


def popup(kind: str, title: str, message: str) -> None:
    """Show a Windows-native dialog even when PyInstaller runs without a console."""
    import tkinter as tk
    from tkinter import messagebox

    root = tk.Tk()
    root.withdraw()
    try:
        root.attributes("-topmost", True)
    except Exception:
        pass
    try:
        if kind == "error":
            messagebox.showerror(title, message, parent=root)
        elif kind == "warning":
            messagebox.showwarning(title, message, parent=root)
        else:
            messagebox.showinfo(title, message, parent=root)
    finally:
        root.destroy()


def lan_addresses() -> list[tuple[str, str]]:
    """Return active private IPv4 addresses, labelled by Windows interface name."""
    found: list[tuple[str, str]] = []
    try:
        import psutil

        stats = psutil.net_if_stats()
        for interface, addresses in psutil.net_if_addrs().items():
            status = stats.get(interface)
            if status is not None and not status.isup:
                continue
            for address in addresses:
                if address.family != socket.AF_INET:
                    continue
                try:
                    parsed = ipaddress.ip_address(address.address)
                except ValueError:
                    continue
                if parsed.is_private and not parsed.is_loopback and not parsed.is_link_local:
                    pair = (interface, address.address)
                    if pair not in found:
                        found.append(pair)
    except Exception:
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            try:
                sock.connect(("192.0.2.1", 80))
                addr = sock.getsockname()[0]
                parsed = ipaddress.ip_address(addr)
                if parsed.is_private and not parsed.is_loopback:
                    found.append(("Сетевой адаптер", addr))
            finally:
                sock.close()
        except OSError:
            pass
    return found


def port_is_available() -> bool:
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        sock.bind(("127.0.0.1", PORT))
        return True
    except OSError:
        return False
    finally:
        sock.close()


def wait_until_ready(url: str, timeout: float = 35.0) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=1.5) as response:
                if response.status == 200:
                    return True
        except (urllib.error.URLError, TimeoutError, OSError):
            pass
        time.sleep(0.35)
    return False


def main() -> None:
    _, appdata = app_paths()
    log_path = appdata / "desktop.log"
    logging.basicConfig(
        filename=str(log_path),
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
        encoding="utf-8",
    )

    if not port_is_available():
        popup(
            "error",
            "Kemtiz — порт занят",
            "Не удалось запустить Kemtiz: порт 8000 уже используется.\n\n"
            "Закрой другой экземпляр Kemtiz или приложение, использующее порт 8000, "
            "и попробуй снова.",
        )
        return

    try:
        import uvicorn
        import webview

        import server  # noqa: PLC0415
    except Exception as exc:
        logging.exception("Не удалось загрузить Kemtiz")
        popup(
            "error",
            "Kemtiz — ошибка запуска",
            f"Не удалось загрузить компоненты приложения:\n{exc}\n\n"
            f"Журнал: {log_path}",
        )
        return

    config = uvicorn.Config(
        server.app,
        host="0.0.0.0",
        port=PORT,
        log_level="warning",
        access_log=False,
        log_config=None,
    )
    api_server = uvicorn.Server(config)
    thread = threading.Thread(target=api_server.run, name="KemtizAPI", daemon=True)
    thread.start()

    if not wait_until_ready(f"http://127.0.0.1:{PORT}/health"):
        api_server.should_exit = True
        thread.join(timeout=5)
        logging.error("Kemtiz did not become ready; inspect the log above.")
        popup(
            "error",
            "Kemtiz — сервер не запустился",
            "Внутренний сервер не ответил за 35 секунд.\n\n"
            f"Проверь журнал:\n{log_path}",
        )
        return

    addresses = lan_addresses()
    lan_lines = [f"• {interface}: http://{address}:{PORT}" for interface, address in addresses]
    if not lan_lines:
        lan_lines = ["Не удалось определить IP-адрес. Выполни ipconfig в командной строке Windows."]

    popup(
        "info",
        "Kemtiz Desktop запущен",
        "Приложение откроется в отдельном окне.\n\n"
        f"На этом ПК:\nhttp://localhost:{PORT}\n\n"
        "Для телефона или другого устройства в той же доверенной Wi-Fi/локальной сети:\n"
        + "\n".join(lan_lines)
        + "\n\n"
        "На телефоне открой Kemtiz → «Адрес сервера» и укажи один из этих адресов.\n"
        "Оба устройства должны быть в одной сети, а Windows Firewall должен разрешить "
        "Kemtiz в частной сети.\n\n"
        "Локальный режим использует HTTP без шифрования. Не используй его в общественных "
        "Wi-Fi и не открывай порт 8000 в интернет.",
    )

    try:
        window = webview.create_window(
            "Kemtiz",
            f"http://localhost:{PORT}",
            width=1440,
            height=920,
            min_size=(960, 640),
            background_color="#0c0d12",
        )

        def shutdown() -> None:
            api_server.should_exit = True

        window.events.closed += shutdown
        webview.start(gui="edgechromium", debug=False)
    except Exception as exc:
        logging.exception("Ошибка графического окна")
        popup(
            "error",
            "Kemtiz — ошибка окна",
            "Не удалось открыть окно приложения.\n\n"
            "Установи Microsoft Edge WebView2 Runtime и попробуй снова.\n\n"
            f"Подробности: {exc}\nЖурнал: {log_path}",
        )
    finally:
        api_server.should_exit = True
        thread.join(timeout=8)


if __name__ == "__main__":
    main()
