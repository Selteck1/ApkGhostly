from __future__ import annotations

import base64
import hashlib
import json
import logging
import os
import secrets
import socket
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any

import httpx
import uvicorn
from PySide6.QtCore import Qt, QThread, QTimer, Signal
from PySide6.QtGui import QAction
from PySide6.QtWidgets import (
    QApplication, QCheckBox, QDialog, QDialogButtonBox, QFormLayout,
    QFrame, QHBoxLayout, QInputDialog, QLabel, QLineEdit, QListWidget, QMenu,
    QListWidgetItem, QMainWindow, QMessageBox, QPushButton, QPlainTextEdit,
    QSplitter, QStackedWidget, QTabWidget, QVBoxLayout, QWidget, QComboBox,
)

PORT = 8000
API_BASE = f"http://127.0.0.1:{PORT}"
WEB_CLIENT_ID = "649066614178-f3ld6uvr9pupplnsq11k53673c2pft4o.apps.googleusercontent.com"

STYLES = """
QWidget { background:#0b0c12; color:#f1edf9; font-family:"Segoe UI"; font-size:10pt; }
QMainWindow { background:#0b0c12; }
QFrame#panel { background:#151622; border:1px solid #29283b; border-radius:16px; }
QFrame#sidebar { background:#10111a; border-right:1px solid #252638; }
QLabel#brand { font-size:22pt; font-weight:800; letter-spacing:2px; color:#c7b3ff; }
QLabel#subtle { color:#a39eb7; }
QLabel#section { font-size:9pt; font-weight:700; color:#9f96bd; letter-spacing:1px; }
QLabel#chatTitle { font-size:17pt; font-weight:700; }
QPushButton { background:#242437; color:#f5f0ff; border:1px solid #393750; border-radius:10px; padding:9px 13px; }
QPushButton:hover { background:#302b49; border-color:#8970d9; }
QPushButton:pressed { background:#4e3e82; }
QPushButton#primary { background:#8060d8; border:1px solid #977de6; color:white; font-weight:700; }
QPushButton#primary:hover { background:#9070e8; }
QPushButton#danger { background:#502734; border-color:#73404f; }
QLineEdit, QPlainTextEdit, QComboBox { background:#10111b; color:#f4f0fb; border:1px solid #36344b; border-radius:9px; padding:10px; selection-background-color:#7154bf; }
QLineEdit:focus, QPlainTextEdit:focus, QComboBox:focus { border-color:#9277e7; }
QListWidget, QTabWidget::pane { background:#11121c; border:1px solid #29283b; border-radius:10px; outline:0; }
QListWidget::item { padding:9px; border-bottom:1px solid #252538; }
QListWidget::item:selected { background:#302648; border:1px solid #6a51a5; border-radius:7px; }
QTabBar::tab { background:#171824; color:#a9a3bc; padding:9px 13px; border-top-left-radius:7px; border-top-right-radius:7px; }
QTabBar::tab:selected { background:#302648; color:white; }
QScrollBar:vertical { background:#11121c; width:10px; margin:4px; }
QScrollBar::handle:vertical { background:#45405e; min-height:25px; border-radius:4px; }
"""

def paths() -> tuple[Path, Path]:
    if getattr(sys, "frozen", False):
        root = Path(getattr(sys, "_MEIPASS", Path(sys.executable).parent))
    else:
        root = Path(__file__).resolve().parents[1]
    app_dir = Path(os.environ.get("APPDATA", str(Path.home()))) / "Kemtiz"
    data_dir = app_dir / "data"
    data_dir.mkdir(parents=True, exist_ok=True)
    app_dir.mkdir(parents=True, exist_ok=True)
    cfg_file = app_dir / "config.json"
    try:
        config = json.loads(cfg_file.read_text("utf-8")) if cfg_file.exists() else {}
    except Exception:
        config = {}
    config.setdefault("google_desktop_client_id", "")
    config.setdefault("token", "")
    os.environ["KEMTIZ_DATA_DIR"] = str(data_dir)
    os.environ["KEMTIZ_DB_PATH"] = str(data_dir / "kemtiz.sqlite3")
    os.environ["KEMTIZ_GOOGLE_CLIENT_ID"] = WEB_CLIENT_ID
    os.environ["KEMTIZ_DESKTOP_GOOGLE_CLIENT_ID"] = str(config.get("google_desktop_client_id", "")).strip()
    if str(root) not in sys.path:
        sys.path.insert(0, str(root))
    return app_dir, cfg_file

def save_config(path: Path, config: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(config, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(path)

def active_lan_addresses() -> list[str]:
    result: list[str] = []
    try:
        import psutil
        stats = psutil.net_if_stats()
        for interface, entries in psutil.net_if_addrs().items():
            if interface in stats and not stats[interface].isup:
                continue
            for entry in entries:
                if entry.family != socket.AF_INET:
                    continue
                ip = entry.address
                if ip.startswith(("10.", "192.168.")) or (
                    ip.startswith("172.") and 16 <= int(ip.split(".")[1]) <= 31
                ):
                    if not ip.startswith("127.") and ip not in result:
                        result.append(ip)
    except Exception:
        pass
    return result

def start_backend() -> tuple[uvicorn.Server, threading.Thread]:
    import server
    cfg = uvicorn.Config(
        server.app, host="0.0.0.0", port=PORT,
        log_level="warning", access_log=False, log_config=None,
    )
    instance = uvicorn.Server(cfg)
    thread = threading.Thread(target=instance.run, name="KemtizBackend", daemon=True)
    thread.start()
    deadline = time.monotonic() + 35
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(f"{API_BASE}/health", timeout=1.5) as response:
                if response.status == 200:
                    return instance, thread
        except Exception:
            time.sleep(0.25)
    instance.should_exit = True
    thread.join(timeout=5)
    raise RuntimeError("Сервер Kemtiz не ответил на /health за 35 секунд.")

class OAuthWorker(QThread):
    token_ready = Signal(str)
    failed = Signal(str)
    status = Signal(str)

    def __init__(self, client_id: str):
        super().__init__()
        self.client_id = client_id

    def run(self) -> None:
        listener = None
        try:
            if not self.client_id or not self.client_id.endswith(".apps.googleusercontent.com"):
                raise RuntimeError("Укажи OAuth Client ID типа «Desktop app» в настройках Kemtiz.")
            verifier = secrets.token_urlsafe(48)
            challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
            state = secrets.token_urlsafe(24)
            result: dict[str, str] = {}

            class CallbackHandler(BaseHTTPRequestHandler):
                def do_GET(self):
                    query = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query)
                    result["state"] = query.get("state", [""])[0]
                    result["code"] = query.get("code", [""])[0]
                    result["error"] = query.get("error", [""])[0]
                    page = (
                        "<!doctype html><html lang='ru'><meta charset='utf-8'>"
                        "<title>Kemtiz</title><body style='font:16px Segoe UI;background:#0b0c12;"
                        "color:#f1edf9;padding:50px'><h2>Kemtiz</h2><p>Авторизация завершена."
                        " Вернись в приложение Kemtiz.</p></body></html>"
                    ).encode("utf-8")
                    self.send_response(200)
                    self.send_header("Content-Type", "text/html; charset=utf-8")
                    self.send_header("Content-Length", str(len(page)))
                    self.send_header("Cache-Control", "no-store")
                    self.end_headers()
                    self.wfile.write(page)

                def log_message(self, *_args):
                    return

            listener = HTTPServer(("127.0.0.1", 0), CallbackHandler)
            listener.timeout = 1
            redirect_uri = f"http://127.0.0.1:{listener.server_port}"
            params = {
                "client_id": self.client_id,
                "redirect_uri": redirect_uri,
                "response_type": "code",
                "scope": "openid email profile",
                "state": state,
                "code_challenge": challenge,
                "code_challenge_method": "S256",
                "prompt": "select_account",
            }
            auth_url = "https://accounts.google.com/o/oauth2/v2/auth?" + urllib.parse.urlencode(params)
            self.status.emit("Откроется страница Google только для подтверждения аккаунта. Сам Kemtiz останется отдельным приложением.")
            import webbrowser
            if not webbrowser.open(auth_url, new=1, autoraise=True):
                raise RuntimeError("Не удалось открыть системный браузер для входа Google.")
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline and not result:
                listener.handle_request()
                if self.isInterruptionRequested():
                    raise RuntimeError("Вход отменён.")
            if not result:
                raise RuntimeError("Не получен ответ Google за 4 минуты. Попробуй снова.")
            if result.get("state") != state:
                raise RuntimeError("Не совпал защитный state параметр. Запусти вход заново.")
            if result.get("error"):
                raise RuntimeError("Google отменил вход: " + result["error"])
            code = result.get("code", "")
            if not code:
                raise RuntimeError("Google не вернул код авторизации.")
            response = httpx.post(
                "https://oauth2.googleapis.com/token",
                data={
                    "client_id": self.client_id,
                    "code": code,
                    "code_verifier": verifier,
                    "redirect_uri": redirect_uri,
                    "grant_type": "authorization_code",
                },
                timeout=20,
            )
            if response.status_code != 200:
                details = response.json() if "application/json" in response.headers.get("content-type", "") else {}
                reason = details.get("error_description") or details.get("error") or f"HTTP {response.status_code}"
                raise RuntimeError("Не удалось обменять OAuth-код: " + str(reason))
            token = str(response.json().get("id_token", ""))
            if not token:
                raise RuntimeError("Google не вернул ID-токен.")
            self.token_ready.emit(token)
        except Exception as exc:
            self.failed.emit(str(exc))
        finally:
            if listener is not None:
                try:
                    listener.server_close()
                except Exception:
                    pass

class ProfileDialog(QDialog):
    def __init__(self, profile: dict[str, Any], parent=None):
        super().__init__(parent)
        self.setWindowTitle("Заверши настройку Kemtiz")
        self.setMinimumWidth(440)
        layout = QVBoxLayout(self)
        header = QLabel("Последний шаг")
        header.setObjectName("brand")
        subtitle = QLabel(f"{profile.get('name','Google аккаунт')}\n{profile.get('email','')}\n\nПридумай уникальный username для Kemtiz.")
        subtitle.setObjectName("subtle")
        layout.addWidget(header)
        layout.addWidget(subtitle)
        form = QFormLayout()
        self.username = QLineEdit()
        email = str(profile.get("email", "kemtiz_user")).split("@")[0].lower()
        suggested = "".join(c if c.isascii() and (c.isalnum() or c == "_") else "_" for c in email)[:16].strip("_")
        self.username.setText((suggested or "kemtiz_user")[:24])
        self.country = QComboBox()
        self.country.addItems(["Не указывать", "Россия", "Германия", "Казахстан", "Беларусь", "Украина", "США", "Другая страна"])
        self.about = QLineEdit()
        self.about.setMaxLength(200)
        self.about.setPlaceholderText("Пара слов о себе (необязательно)")
        form.addRow("Username", self.username)
        form.addRow("Страна", self.country)
        form.addRow("О себе", self.about)
        layout.addLayout(form)
        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Cancel | QDialogButtonBox.StandardButton.Save)
        buttons.button(QDialogButtonBox.StandardButton.Save).setText("Создать аккаунт")
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)
        layout.addWidget(buttons)

    def payload(self) -> dict[str, str]:
        country = self.country.currentText()
        return {
            "username": self.username.text().strip(),
            "country": "" if country == "Не указывать" else country,
            "about": self.about.text().strip(),
        }

class MainWindow(QMainWindow):
    def __init__(self, config: dict[str, Any], config_path: Path):
        super().__init__()
        self.config = config
        self.config_path = config_path
        self.token = str(config.get("token", ""))
        self.me: dict[str, Any] | None = None
        self.chats: list[dict[str, Any]] = []
        self.friends: list[dict[str, Any]] = []
        self.incoming: list[dict[str, Any]] = []
        self.outgoing: list[dict[str, Any]] = []
        self.current_chat: dict[str, Any] | None = None
        self.current_messages: list[dict[str, Any]] = []
        self.pending_credential = ""
        self.oauth_worker: OAuthWorker | None = None
        self.backend: uvicorn.Server | None = None
        self.backend_thread: threading.Thread | None = None
        self.client = httpx.Client(base_url=API_BASE, timeout=5.0)
        self.setWindowTitle("Kemtiz")
        self.setMinimumSize(1050, 700)
        self.resize(1400, 900)
        self.setStyleSheet(STYLES)
        self.stack = QStackedWidget()
        self.setCentralWidget(self.stack)
        self.build_login_page()
        self.build_profile_page()
        self.build_main_page()
        self.stack.setCurrentWidget(self.login_page)
        self.timer = QTimer(self)
        self.timer.setInterval(5000)
        self.timer.timeout.connect(self.refresh_all)

    def api(self, method: str, path: str, *, body: Any = None, params: dict | None = None, token: str | None = None):
        headers = {}
        active_token = self.token if token is None else token
        if active_token:
            headers["Authorization"] = "Bearer " + active_token
        response = self.client.request(method, path, json=body, params=params, headers=headers)
        try:
            data = response.json()
        except Exception:
            data = {}
        if not response.is_success:
            detail = data.get("detail") if isinstance(data, dict) else None
            if response.status_code == 401 and active_token:
                self.token = ""
                self.config["token"] = ""
                save_config(self.config_path, self.config)
                self.stack.setCurrentWidget(self.login_page)
            raise RuntimeError(str(detail or f"Ошибка сервера {response.status_code}"))
        return data

    def build_login_page(self):
        page = QWidget()
        outer = QVBoxLayout(page)
        outer.setContentsMargins(70, 45, 70, 45)
        outer.addStretch(1)
        card = QFrame()
        card.setObjectName("panel")
        card.setMaximumWidth(640)
        card.setMinimumWidth(500)
        col = QVBoxLayout(card)
        col.setContentsMargins(42, 38, 42, 38)
        col.setSpacing(18)
        brand = QLabel("KEMTIZ")
        brand.setObjectName("brand")
        brand.setAlignment(Qt.AlignmentFlag.AlignCenter)
        tagline = QLabel("ТВОИ ЛЮДИ. ТВОИ ЧАТЫ.")
        tagline.setObjectName("section")
        tagline.setAlignment(Qt.AlignmentFlag.AlignCenter)
        title = QLabel("Будь ближе.")
        title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        title.setStyleSheet("font-size:23pt;font-weight:700")
        desc = QLabel("Отдельное настольное приложение.\nТвои чаты, друзья и группы — в одном окне.")
        desc.setAlignment(Qt.AlignmentFlag.AlignCenter)
        desc.setObjectName("subtle")
        self.google_button = QPushButton("  Продолжить с Google")
        self.google_button.setObjectName("primary")
        self.google_button.setMinimumHeight(48)
        self.google_button.clicked.connect(self.sign_in)
        self.settings_button = QPushButton("Настроить Google-вход")
        self.settings_button.clicked.connect(self.configure_google)
        self.login_status = QLabel("Для первого входа нужен OAuth Client ID типа «Desktop app».")
        self.login_status.setWordWrap(True)
        self.login_status.setObjectName("subtle")
        self.login_status.setAlignment(Qt.AlignmentFlag.AlignCenter)
        note = QLabel("Основное приложение работает нативно. Браузер открывается только на время безопасного подтверждения Google.")
        note.setWordWrap(True)
        note.setObjectName("subtle")
        note.setAlignment(Qt.AlignmentFlag.AlignCenter)
        col.addWidget(brand)
        col.addWidget(tagline)
        col.addSpacing(15)
        col.addWidget(title)
        col.addWidget(desc)
        col.addSpacing(12)
        col.addWidget(self.google_button)
        col.addWidget(self.settings_button)
        col.addWidget(self.login_status)
        col.addWidget(note)
        row = QHBoxLayout()
        row.addStretch(1)
        row.addWidget(card)
        row.addStretch(1)
        outer.addLayout(row)
        outer.addStretch(1)
        footer = QLabel("KEMTIZ DESKTOP  •  ДАННЫЕ ХРАНЯТСЯ ЛОКАЛЬНО НА ЭТОМ ПК")
        footer.setAlignment(Qt.AlignmentFlag.AlignCenter)
        footer.setObjectName("subtle")
        outer.addWidget(footer)
        self.login_page = page
        self.stack.addWidget(page)

    def build_profile_page(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(50, 50, 50, 50)
        title = QLabel("Настрой свой профиль")
        title.setObjectName("brand")
        self.profile_summary = QLabel()
        self.profile_summary.setObjectName("subtle")
        self.profile_username = QLineEdit()
        self.profile_username.setPlaceholderText("username — 3–24 латинских символа")
        self.profile_country = QComboBox()
        self.profile_country.addItems(["Не указывать", "Россия", "Германия", "Казахстан", "Беларусь", "Украина", "США", "Другая страна"])
        self.profile_about = QLineEdit()
        self.profile_about.setPlaceholderText("Пара слов о себе (необязательно)")
        save = QPushButton("Создать аккаунт")
        save.setObjectName("primary")
        save.clicked.connect(self.finish_signup)
        box = QFrame()
        box.setObjectName("panel")
        box.setMaximumWidth(600)
        form = QFormLayout(box)
        form.setContentsMargins(30, 30, 30, 30)
        form.addRow(title)
        form.addRow(self.profile_summary)
        form.addRow("Username", self.profile_username)
        form.addRow("Страна", self.profile_country)
        form.addRow("О себе", self.profile_about)
        form.addRow(save)
        row = QHBoxLayout()
        row.addStretch(1)
        row.addWidget(box)
        row.addStretch(1)
        layout.addStretch(1)
        layout.addLayout(row)
        layout.addStretch(1)
        self.profile_page = page
        self.stack.addWidget(page)

    def build_main_page(self):
        page = QWidget()
        main = QHBoxLayout(page)
        main.setContentsMargins(0, 0, 0, 0)
        main.setSpacing(0)
        sidebar = QFrame()
        sidebar.setObjectName("sidebar")
        sidebar.setMinimumWidth(320)
        sidebar.setMaximumWidth(390)
        side = QVBoxLayout(sidebar)
        side.setContentsMargins(16, 18, 16, 16)
        brand_row = QHBoxLayout()
        brand = QLabel("KEMTIZ")
        brand.setObjectName("brand")
        brand.setStyleSheet("font-size:18pt")
        logout = QPushButton("Выйти")
        logout.clicked.connect(self.logout)
        brand_row.addWidget(brand)
        brand_row.addStretch(1)
        brand_row.addWidget(logout)
        side.addLayout(brand_row)
        self.profile_label = QLabel("Не выполнен вход")
        self.profile_label.setObjectName("subtle")
        side.addWidget(self.profile_label)
        search_row = QHBoxLayout()
        self.search_input = QLineEdit()
        self.search_input.setPlaceholderText("Найти username / имя")
        self.search_input.returnPressed.connect(self.search_users)
        search = QPushButton("Найти")
        search.clicked.connect(self.search_users)
        search_row.addWidget(self.search_input)
        search_row.addWidget(search)
        side.addLayout(search_row)
        self.search_results = QListWidget()
        self.search_results.setMaximumHeight(140)
        self.search_results.itemDoubleClicked.connect(self.send_friend_request)
        side.addWidget(self.search_results)
        self.tabs = QTabWidget()
        self.friends_list = QListWidget()
        self.requests_list = QListWidget()
        self.chats_list = QListWidget()
        self.friends_list.itemDoubleClicked.connect(self.open_friend_chat)
        self.requests_list.itemDoubleClicked.connect(self.respond_to_request)
        self.chats_list.itemClicked.connect(self.open_chat)
        self.tabs.addTab(self.chats_list, "Чаты")
        self.tabs.addTab(self.friends_list, "Друзья")
        self.tabs.addTab(self.requests_list, "Заявки")
        side.addWidget(self.tabs, 1)
        self.group_button = QPushButton("+ Создать группу")
        self.group_button.clicked.connect(self.create_group)
        side.addWidget(self.group_button)
        main.addWidget(sidebar)

        content = QFrame()
        content.setStyleSheet("QFrame{background:#0d0e15}")
        col = QVBoxLayout(content)
        col.setContentsMargins(22, 20, 22, 18)
        col.setSpacing(14)
        top = QHBoxLayout()
        self.chat_title = QLabel("Добро пожаловать в Kemtiz")
        self.chat_title.setObjectName("chatTitle")
        self.chat_subtitle = QLabel("Выбери друга или открой чат")
        self.chat_subtitle.setObjectName("subtle")
        title_col = QVBoxLayout()
        title_col.addWidget(self.chat_title)
        title_col.addWidget(self.chat_subtitle)
        top.addLayout(title_col)
        top.addStretch(1)
        self.connection_label = QLabel("● ЛОКАЛЬНЫЙ СЕРВЕР")
        self.connection_label.setObjectName("section")
        top.addWidget(self.connection_label)
        col.addLayout(top)
        self.message_list = QListWidget()
        self.message_list.setWordWrap(True)
        self.message_list.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.message_list.customContextMenuRequested.connect(self.message_menu)
        col.addWidget(self.message_list, 1)
        composer = QHBoxLayout()
        self.message_input = QLineEdit()
        self.message_input.setPlaceholderText("Напиши сообщение…")
        self.message_input.returnPressed.connect(self.send_message)
        self.send_button = QPushButton("Отправить  ↗")
        self.send_button.setObjectName("primary")
        self.send_button.clicked.connect(self.send_message)
        composer.addWidget(self.message_input, 1)
        composer.addWidget(self.send_button)
        col.addLayout(composer)
        main.addWidget(content, 1)
        self.main_page = page
        self.stack.addWidget(page)

    def show_error(self, message: str):
        QMessageBox.warning(self, "Kemtiz", message)

    def configure_google(self):
        current = str(self.config.get("google_desktop_client_id", ""))
        value, ok = QInputDialog.getText(
            self, "Google OAuth", "OAuth Client ID типа Desktop app:", QLineEdit.EchoMode.Normal, current
        )
        if not ok:
            return
        value = value.strip()
        if not value.endswith(".apps.googleusercontent.com"):
            self.show_error("Это не похоже на Google OAuth Client ID. Нужен идентификатор с окончанием .apps.googleusercontent.com.")
            return
        self.config["google_desktop_client_id"] = value
        os.environ["KEMTIZ_DESKTOP_GOOGLE_CLIENT_ID"] = value
        save_config(self.config_path, self.config)
        self.login_status.setText("Google OAuth Client ID сохранён. Нажми «Продолжить с Google».")
    
    def sign_in(self):
        client_id = str(self.config.get("google_desktop_client_id", "")).strip()
        if not client_id:
            self.configure_google()
            client_id = str(self.config.get("google_desktop_client_id", "")).strip()
        if not client_id:
            return
        self.google_button.setEnabled(False)
        self.login_status.setText("Ожидаю подтверждение аккаунта Google…")
        self.oauth_worker = OAuthWorker(client_id)
        self.oauth_worker.status.connect(self.login_status.setText)
        self.oauth_worker.token_ready.connect(self.handle_google_token)
        self.oauth_worker.failed.connect(self.google_error)
        self.oauth_worker.finished.connect(lambda: self.google_button.setEnabled(True))
        self.oauth_worker.start()

    def google_error(self, message: str):
        self.login_status.setText(message)
        self.show_error(message)

    def handle_google_token(self, credential: str):
        try:
            result = self.api("POST", "/api/auth/google/start", body={"credential": credential}, token="")
            if result.get("needs_profile"):
                profile = result.get("profile") or {}
                self.pending_credential = credential
                self.profile_summary.setText(f"{profile.get('name','Google аккаунт')} · {profile.get('email','')}")
                local = str(profile.get("email", "kemtiz_user")).split("@")[0].lower()
                suggested = "".join(ch if (ch.isascii() and (ch.isalnum() or ch == "_")) else "_" for ch in local)
                self.profile_username.setText((suggested.strip("_") or "kemtiz_user")[:24])
                self.stack.setCurrentWidget(self.profile_page)
            else:
                self.accept_login(result)
        except Exception as exc:
            self.google_error(str(exc))

    def finish_signup(self):
        username = self.profile_username.text().strip()
        country = self.profile_country.currentText()
        body = {
            "credential": self.pending_credential,
            "username": username,
            "country": "" if country == "Не указывать" else country,
            "about": self.profile_about.text().strip(),
        }
        try:
            result = self.api("POST", "/api/auth/google/finish", body=body, token="")
            self.pending_credential = ""
            self.accept_login(result)
        except Exception as exc:
            self.show_error(str(exc))

    def accept_login(self, result: dict[str, Any]):
        self.token = str(result.get("token", ""))
        self.me = result.get("user") or {}
        self.config["token"] = self.token
        save_config(self.config_path, self.config)
        self.stack.setCurrentWidget(self.main_page)
        self.profile_label.setText(f"{self.me.get('display_name','Kemtiz')}  ·  @{self.me.get('username','')}")
        self.refresh_all()
        self.timer.start()

    def logout(self):
        self.timer.stop()
        self.token = ""
        self.me = None
        self.current_chat = None
        self.config["token"] = ""
        save_config(self.config_path, self.config)
        self.stack.setCurrentWidget(self.login_page)

    def refresh_all(self):
        if not self.token:
            return
        try:
            self.me = self.api("GET", "/api/me")
            self.friends = self.api("GET", "/api/friends")
            requests = self.api("GET", "/api/friends/requests")
            self.incoming = requests.get("incoming", [])
            self.outgoing = requests.get("outgoing", [])
            self.chats = self.api("GET", "/api/chats")
            self.render_people()
            self.render_chats()
            if self.current_chat:
                self.refresh_messages()
        except Exception as exc:
            self.connection_label.setText("● ОШИБКА ПОДКЛЮЧЕНИЯ")
            logging.warning("refresh failed: %s", exc)

    def render_people(self):
        current_friend = self.friends_list.currentItem()
        current_friend_id = current_friend.data(Qt.ItemDataRole.UserRole) if current_friend else None
        self.friends_list.clear()
        for person in self.friends:
            item = QListWidgetItem(f"{person['display_name']}\n@{person['username']}  ·  {'онлайн' if person.get('online') else 'не в сети'}")
            item.setData(Qt.ItemDataRole.UserRole, person["id"])
            self.friends_list.addItem(item)
            if person["id"] == current_friend_id:
                self.friends_list.setCurrentItem(item)
        self.requests_list.clear()
        for req in self.incoming:
            item = QListWidgetItem(f"⬇  {req['display_name']}\n@{req['username']}  ·  двойной щелчок: ответ")
            item.setData(Qt.ItemDataRole.UserRole, req["request_id"])
            self.requests_list.addItem(item)
        for req in self.outgoing:
            item = QListWidgetItem(f"↗  {req['display_name']}\n@{req['username']}  ·  ожидает ответа")
            item.setData(Qt.ItemDataRole.UserRole, -int(req["request_id"]))
            self.requests_list.addItem(item)
        self.profile_label.setText(f"{self.me.get('display_name','Kemtiz')}  ·  @{self.me.get('username','')}")
        self.connection_label.setText("● ПОДКЛЮЧЕНО")

    def render_chats(self):
        selected_id = self.current_chat.get("id") if self.current_chat else None
        self.chats_list.clear()
        for chat in self.chats:
            preview = chat.get("last_message") or "Нет сообщений"
            if len(preview) > 56:
                preview = preview[:53] + "…"
            item = QListWidgetItem(f"{chat.get('title','Чат')}\n{preview}")
            item.setData(Qt.ItemDataRole.UserRole, chat["id"])
            self.chats_list.addItem(item)
            if chat["id"] == selected_id:
                self.chats_list.setCurrentItem(item)

    def search_users(self):
        query = self.search_input.text().strip()
        self.search_results.clear()
        if len(query) < 2:
            return
        try:
            results = self.api("GET", "/api/users/search", params={"q": query})
            friend_ids = {x["id"] for x in self.friends}
            for person in results:
                suffix = " · уже друг" if person["id"] in friend_ids else " · двойной щелчок: заявка"
                item = QListWidgetItem(f"{person['display_name']}\n@{person['username']}{suffix}")
                item.setData(Qt.ItemDataRole.UserRole, person)
                self.search_results.addItem(item)
        except Exception as exc:
            self.show_error(str(exc))

    def send_friend_request(self, item: QListWidgetItem):
        person = item.data(Qt.ItemDataRole.UserRole)
        if not isinstance(person, dict):
            return
        if any(friend["id"] == person["id"] for friend in self.friends):
            QMessageBox.information(self, "Kemtiz", "Вы уже друзья.")
            return
        try:
            self.api("POST", "/api/friends/requests", body={"username": person["username"]})
            QMessageBox.information(self, "Kemtiz", f"Заявка отправлена: @{person['username']}")
            self.refresh_all()
        except Exception as exc:
            self.show_error(str(exc))

    def respond_to_request(self, item: QListWidgetItem):
        request_id = item.data(Qt.ItemDataRole.UserRole)
        if not isinstance(request_id, int) or request_id <= 0:
            return
        response = QMessageBox(self)
        response.setWindowTitle("Заявка в друзья")
        response.setText("Принять заявку в друзья?")
        accept = response.addButton("Принять", QMessageBox.ButtonRole.AcceptRole)
        response.addButton("Отклонить", QMessageBox.ButtonRole.DestructiveRole)
        response.addButton("Отмена", QMessageBox.ButtonRole.RejectRole)
        response.exec()
        clicked = response.clickedButton()
        action = "accept" if clicked == accept else "decline" if clicked != response.buttons()[-1] else ""
        if not action:
            return
        try:
            self.api("POST", f"/api/friends/requests/{request_id}/{action}")
            self.refresh_all()
        except Exception as exc:
            self.show_error(str(exc))

    def open_friend_chat(self, item: QListWidgetItem):
        friend_id = item.data(Qt.ItemDataRole.UserRole)
        if friend_id is None:
            return
        try:
            self.current_chat = self.api("POST", f"/api/chats/direct/{int(friend_id)}")
            self.show_chat()
            self.refresh_all()
        except Exception as exc:
            self.show_error(str(exc))

    def open_chat(self, item: QListWidgetItem):
        chat_id = item.data(Qt.ItemDataRole.UserRole)
        chat = next((entry for entry in self.chats if entry["id"] == chat_id), None)
        if chat:
            self.current_chat = chat
            self.show_chat()
            self.refresh_messages()

    def show_chat(self):
        if not self.current_chat:
            return
        self.chat_title.setText(self.current_chat.get("title", "Чат"))
        members = self.current_chat.get("members", [])
        self.chat_subtitle.setText("Группа" if self.current_chat.get("kind") == "group" else "Личный чат")
        self.refresh_messages()

    def refresh_messages(self):
        if not self.current_chat or not self.token:
            return
        try:
            messages = self.api("GET", f"/api/chats/{int(self.current_chat['id'])}/messages", params={"limit": 100})
            if messages == self.current_messages:
                return
            self.current_messages = messages
            self.message_list.clear()
            for message in messages:
                mine = int(message.get("sender_id", -1)) == int((self.me or {}).get("id", -2))
                stamp = str(message.get("created_at", ""))[:16].replace("T", " ")
                sender = "Ты" if mine else message.get("sender_display_name", "Пользователь")
                item = QListWidgetItem(f"{sender}  ·  {stamp}\n{message.get('body','')}")
                item.setData(Qt.ItemDataRole.UserRole, message)
                item.setTextAlignment(Qt.AlignmentFlag.AlignLeft)
                self.message_list.addItem(item)
            if messages:
                last_id = int(messages[-1]["id"])
                self.api("POST", f"/api/chats/{int(self.current_chat['id'])}/read", body={"last_read_message_id": last_id})
            self.message_list.scrollToBottom()
        except Exception as exc:
            logging.warning("message refresh failed: %s", exc)

    def send_message(self):
        if not self.current_chat:
            self.show_error("Сначала открой личный чат или группу.")
            return
        text = self.message_input.text().strip()
        if not text:
            return
        try:
            self.api("POST", f"/api/chats/{int(self.current_chat['id'])}/messages", body={"body": text})
            self.message_input.clear()
            self.refresh_messages()
            self.refresh_all()
        except Exception as exc:
            self.show_error(str(exc))

    def message_menu(self, position):
        item = self.message_list.itemAt(position)
        if not item:
            return
        message = item.data(Qt.ItemDataRole.UserRole)
        if not isinstance(message, dict) or not self.me or int(message.get("sender_id", -1)) != int(self.me["id"]):
            return
        menu = QMenu(self)
        delete = QAction("Удалить сообщение", self)
        menu.addAction(delete)
        delete.triggered.connect(lambda: self.delete_message(message))
        menu.exec(self.message_list.mapToGlobal(position))

    def delete_message(self, message: dict[str, Any]):
        if QMessageBox.question(self, "Удаление", "Удалить это сообщение?") != QMessageBox.StandardButton.Yes:
            return
        try:
            self.api("POST", f"/api/messages/{int(message['id'])}/delete")
            self.refresh_messages()
        except Exception as exc:
            self.show_error(str(exc))

    def create_group(self):
        if len(self.friends) < 1:
            self.show_error("Сначала добавь хотя бы одного друга.")
            return
        dialog = QDialog(self)
        dialog.setWindowTitle("Новая группа")
        layout = QVBoxLayout(dialog)
        title = QLineEdit()
        title.setPlaceholderText("Название группы")
        layout.addWidget(title)
        checks = []
        for friend in self.friends:
            check = QCheckBox(f"{friend['display_name']} (@{friend['username']})")
            check.setProperty("user_id", friend["id"])
            checks.append(check)
            layout.addWidget(check)
        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Cancel | QDialogButtonBox.StandardButton.Ok)
        buttons.button(QDialogButtonBox.StandardButton.Ok).setText("Создать")
        buttons.accepted.connect(dialog.accept)
        buttons.rejected.connect(dialog.reject)
        layout.addWidget(buttons)
        if dialog.exec() != QDialog.DialogCode.Accepted:
            return
        member_ids = [int(c.property("user_id")) for c in checks if c.isChecked()]
        try:
            self.current_chat = self.api("POST", "/api/chats/group", body={"title": title.text().strip(), "member_ids": member_ids})
            self.refresh_all()
            self.show_chat()
        except Exception as exc:
            self.show_error(str(exc))

    def closeEvent(self, event):
        self.timer.stop()
        try:
            if self.oauth_worker and self.oauth_worker.isRunning():
                self.oauth_worker.requestInterruption()
                self.oauth_worker.wait(1200)
        except Exception:
            pass
        try:
            self.client.close()
        except Exception:
            pass
        if self.backend:
            self.backend.should_exit = True
        if self.backend_thread:
            self.backend_thread.join(timeout=5)
        super().closeEvent(event)

def main():
    app_dir, config_path = paths()
    logging.basicConfig(
        filename=str(app_dir / "desktop.log"),
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
        encoding="utf-8",
    )
    app = QApplication(sys.argv)
    app.setApplicationName("Kemtiz")
    app.setOrganizationName("Kemtiz")
    app.setStyle("Fusion")

    try:
        backend, backend_thread = start_backend()
    except Exception as exc:
        QMessageBox.critical(None, "Kemtiz — запуск сервера", f"{exc}\n\nЖурнал: {app_dir / 'desktop.log'}")
        return 1

    try:
        config = json.loads(config_path.read_text("utf-8")) if config_path.exists() else {}
    except Exception:
        config = {}
    config.setdefault("google_desktop_client_id", "")
    config.setdefault("token", "")
    window = MainWindow(config, config_path)
    window.backend = backend
    window.backend_thread = backend_thread
    window.show()
    if window.token:
        try:
            user = window.api("GET", "/api/me")
            window.accept_login({"token": window.token, "user": user})
        except Exception:
            window.token = ""
            window.config["token"] = ""
            save_config(config_path, window.config)
    return app.exec()

if __name__ == "__main__":
    raise SystemExit(main())
