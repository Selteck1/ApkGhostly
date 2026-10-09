from __future__ import annotations

import base64
import hashlib
import io
import json
import logging
import os
import secrets
import socket
import sys
import threading
import time
import urllib.parse
import urllib.request
from http.server import HTTPServer
from pathlib import Path
from typing import Any

import httpx
import qrcode
import uvicorn
from PySide6.QtCore import Qt, QTimer
from PySide6.QtGui import QAction, QPixmap
from PySide6.QtWidgets import (
    QApplication, QCheckBox, QDialog, QDialogButtonBox, QFormLayout,
    QFrame, QHBoxLayout, QInputDialog, QLabel, QLineEdit, QListWidget, QMenu,
    QListWidgetItem, QMainWindow, QMessageBox, QPushButton, QPlainTextEdit,
    QSplitter, QStackedWidget, QTabWidget, QVBoxLayout, QWidget, QComboBox,
)

PORT = 8000
LOCAL_API_BASE = f"http://127.0.0.1:{PORT}"
API_BASE = LOCAL_API_BASE
USE_REMOTE_SERVER = False
# Public Google OAuth client ID for Android Google Sign-In validation; this is not a client secret.
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
    global API_BASE, USE_REMOTE_SERVER
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
    configured_server = str(config.get("server_url", "")).strip().rstrip("/")
    if configured_server:
        parsed = urllib.parse.urlsplit(configured_server)
        if parsed.scheme in ("http", "https") and parsed.netloc:
            API_BASE = configured_server
            USE_REMOTE_SERVER = True
        else:
            API_BASE = LOCAL_API_BASE
            USE_REMOTE_SERVER = False
    else:
        API_BASE = LOCAL_API_BASE
        USE_REMOTE_SERVER = False
    config.setdefault("token", "")
    os.environ["KEMTIZ_DATA_DIR"] = str(data_dir)
    os.environ["KEMTIZ_DB_PATH"] = str(data_dir / "kemtiz.sqlite3")
    os.environ["KEMTIZ_GOOGLE_CLIENT_ID"] = WEB_CLIENT_ID
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
        self.desktop_qr_session_id = ""
        self.desktop_qr_poll_secret = ""
        self.desktop_qr_deadline = 0.0
        self.backend: uvicorn.Server | None = None
        self.backend_thread: threading.Thread | None = None
        self.client = httpx.Client(base_url=API_BASE, timeout=8.0)
        self.setWindowTitle("Kemtiz")
        self.setMinimumSize(1050, 700)
        self.resize(1400, 900)
        self.setStyleSheet(STYLES)
        self.stack = QStackedWidget()
        self.setCentralWidget(self.stack)
        self.build_login_page()
        self.build_main_page()
        self.stack.setCurrentWidget(self.login_page)
        self.timer = QTimer(self)
        self.timer.setInterval(5000)
        self.timer.timeout.connect(self.refresh_all)
        self.qr_timer = QTimer(self)
        self.qr_timer.setInterval(2200)
        self.qr_timer.timeout.connect(self.poll_desktop_qr)

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
        outer.setContentsMargins(40, 30, 40, 30)
        outer.addStretch(1)
        card = QFrame()
        card.setObjectName("panel")
        card.setMaximumWidth(620)
        card.setMinimumWidth(500)
        col = QVBoxLayout(card)
        col.setContentsMargins(38, 30, 38, 30)
        col.setSpacing(14)

        brand = QLabel("KEMTIZ")
        brand.setObjectName("brand")
        brand.setAlignment(Qt.AlignmentFlag.AlignCenter)
        tagline = QLabel("ТВОИ ЛЮДИ. ТВОИ ЧАТЫ.")
        tagline.setObjectName("section")
        tagline.setAlignment(Qt.AlignmentFlag.AlignCenter)
        title = QLabel("Вход через телефон")
        title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        title.setStyleSheet("font-size:22pt;font-weight:750")
        desc = QLabel("Открой Kemtiz на телефоне, нажми «Подключить компьютер»,\nотсканируй этот QR-код и подтверди вход.")
        desc.setAlignment(Qt.AlignmentFlag.AlignCenter)
        desc.setWordWrap(True)
        desc.setObjectName("subtle")

        self.qr_image = QLabel()
        self.qr_image.setObjectName("qrImage")
        self.qr_image.setFixedSize(264, 264)
        self.qr_image.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.qr_image.setStyleSheet("QLabel#qrImage{background:#fff;border:8px solid #fff;border-radius:14px}")
        self.qr_image.setText("Создаю QR-код…")
        qr_row = QHBoxLayout()
        qr_row.addStretch(1)
        qr_row.addWidget(self.qr_image)
        qr_row.addStretch(1)

        self.refresh_qr_button = QPushButton("↻  Обновить QR-код")
        self.refresh_qr_button.setObjectName("primary")
        self.refresh_qr_button.setMinimumHeight(44)
        self.refresh_qr_button.clicked.connect(self.start_desktop_qr_login)
        self.server_button = QPushButton("Настроить сервер")
        self.server_button.clicked.connect(self.configure_server)
        self.login_status = QLabel("Создаю защищённый сеанс входа…")
        self.login_status.setWordWrap(True)
        self.login_status.setObjectName("subtle")
        self.login_status.setAlignment(Qt.AlignmentFlag.AlignCenter)
        note = QLabel("Код действует 3 минуты. Подтверждай вход только для своего компьютера.\nПароль Google на ПК вводить не нужно.")
        note.setWordWrap(True)
        note.setObjectName("subtle")
        note.setAlignment(Qt.AlignmentFlag.AlignCenter)
        if USE_REMOTE_SERVER:
            endpoint_text = "Общий сервер: " + API_BASE
        else:
            addresses = active_lan_addresses()
            if addresses:
                endpoint_text = "Адрес сервера для телефона: " + "   ·   ".join(
                    f"http://{address}:{PORT}" for address in addresses[:3]
                )
            else:
                endpoint_text = "Сервер на этом ПК: http://127.0.0.1:8000"
        self.server_address_hint = QLabel(endpoint_text)
        self.server_address_hint.setWordWrap(True)
        self.server_address_hint.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        self.server_address_hint.setObjectName("section")
        self.server_address_hint.setAlignment(Qt.AlignmentFlag.AlignCenter)
        connect_note = QLabel("Перед сканированием открой Kemtiz на телефоне и подключи его к этому же серверу. Для локального сервера оба устройства должны быть в одной Wi-Fi сети.")
        connect_note.setWordWrap(True)
        connect_note.setObjectName("subtle")
        connect_note.setAlignment(Qt.AlignmentFlag.AlignCenter)

        for widget in (brand, tagline, title, desc):
            col.addWidget(widget)
        col.addSpacing(4)
        col.addLayout(qr_row)
        col.addWidget(self.refresh_qr_button)
        col.addWidget(self.server_button)
        col.addWidget(self.login_status)
        col.addWidget(self.server_address_hint)
        col.addWidget(connect_note)
        col.addWidget(note)
        row = QHBoxLayout()
        row.addStretch(1)
        row.addWidget(card)
        row.addStretch(1)
        outer.addLayout(row)
        outer.addStretch(1)
        footer = QLabel("KEMTIZ DESKTOP  •  ПОДТВЕРЖДЕНИЕ ВХОДА НА ТЕЛЕФОНЕ")
        footer.setAlignment(Qt.AlignmentFlag.AlignCenter)
        footer.setObjectName("subtle")
        outer.addWidget(footer)
        self.login_page = page
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

    def configure_server(self):
        current = str(self.config.get("server_url", ""))
        value, ok = QInputDialog.getText(
            self, "Сервер Kemtiz",
            "Адрес общего HTTPS-сервера (оставь пустым для локального сервера на этом ПК):",
            QLineEdit.EchoMode.Normal, current
        )
        if not ok:
            return
        value = value.strip().rstrip("/")
        if value:
            parsed = urllib.parse.urlsplit(value)
            if parsed.scheme not in ("http", "https") or not parsed.netloc:
                self.show_error("Укажи полный адрес, например https://kemtiz.example.com. Для публичного сервера обязательно используй HTTPS.")
                return
            if parsed.scheme != "https" and parsed.hostname not in ("localhost", "127.0.0.1"):
                confirm = QMessageBox.question(
                    self, "Незащищённое соединение",
                    "Этот адрес использует HTTP. Данные могут быть перехвачены. Продолжить?",
                    QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
                    QMessageBox.StandardButton.No,
                )
                if confirm != QMessageBox.StandardButton.Yes:
                    return
        self.config["server_url"] = value
        save_config(self.config_path, self.config)
        QMessageBox.information(
            self, "Kemtiz",
            "Адрес сохранён в настройках пользователя Windows. Перезапусти Kemtiz, чтобы подключиться к выбранному серверу."
        )

    def start_desktop_qr_login(self):
        self.qr_timer.stop()
        self.desktop_qr_session_id = ""
        self.desktop_qr_poll_secret = ""
        self.desktop_qr_deadline = 0.0
        self.qr_image.clear()
        self.qr_image.setText("Создаю QR-код…")
        self.refresh_qr_button.setEnabled(False)
        self.login_status.setText("Создаю защищённый QR-сеанс…")
        try:
            result = self.api(
                "POST", "/api/auth/desktop/qr/start",
                body={"device_name": "Kemtiz на компьютере Windows"}, token=""
            )
            self.desktop_qr_session_id = str(result["session_id"])
            self.desktop_qr_poll_secret = str(result["poll_secret"])
            self.desktop_qr_deadline = time.monotonic() + int(result.get("expires_in", 180))
            qr = qrcode.make(str(result["qr_payload"]))
            image_buffer = io.BytesIO()
            qr.save(image_buffer, format="PNG")
            pixmap = QPixmap()
            if not pixmap.loadFromData(image_buffer.getvalue(), "PNG"):
                raise RuntimeError("Не удалось подготовить изображение QR-кода.")
            self.qr_image.setText("")
            self.qr_image.setPixmap(pixmap.scaled(
                248, 248, Qt.AspectRatioMode.KeepAspectRatio,
                Qt.TransformationMode.SmoothTransformation,
            ))
            self.login_status.setText("Ожидаю подтверждение на телефоне… QR-код действует 3 минуты.")
            self.refresh_qr_button.setEnabled(True)
            self.qr_timer.start()
        except Exception as exc:
            self.qr_image.setText("QR-код недоступен")
            self.login_status.setText(
                "Не удалось создать QR-сеанс. Проверь подключение к серверу.\n" + str(exc)
            )
            self.refresh_qr_button.setEnabled(True)

    def poll_desktop_qr(self):
        if not self.desktop_qr_session_id or not self.desktop_qr_poll_secret:
            self.qr_timer.stop()
            return
        if time.monotonic() >= self.desktop_qr_deadline:
            self.qr_timer.stop()
            self.login_status.setText("QR-код истёк. Нажми «Обновить QR-код» и отсканируй новый.")
            self.refresh_qr_button.setEnabled(True)
            return
        try:
            session_id = self.desktop_qr_session_id
            poll_secret = self.desktop_qr_poll_secret
            result = self.api(
                "POST", f"/api/auth/desktop/qr/{session_id}/status",
                body={"poll_secret": poll_secret}, token=""
            )
            status = str(result.get("status", ""))
            if status == "pending":
                return
            self.qr_timer.stop()
            if status == "approved":
                login = self.api(
                    "POST", f"/api/auth/desktop/qr/{session_id}/exchange",
                    body={"poll_secret": poll_secret}, token=""
                )
                self.desktop_qr_session_id = ""
                self.desktop_qr_poll_secret = ""
                self.accept_login(login)
            elif status == "denied":
                self.login_status.setText("Вход отклонён на телефоне. При необходимости создай новый QR-код.")
                self.refresh_qr_button.setEnabled(True)
            elif status == "expired":
                self.login_status.setText("QR-код истёк. Нажми «Обновить QR-код».")
                self.refresh_qr_button.setEnabled(True)
            elif status == "consumed":
                self.login_status.setText("Этот QR-код уже использован. Создай новый сеанс.")
                self.refresh_qr_button.setEnabled(True)
            else:
                self.login_status.setText("Неизвестное состояние QR-сеанса. Создай новый код.")
                self.refresh_qr_button.setEnabled(True)
        except Exception as exc:
            self.login_status.setText("Нет связи с сервером — пробую снова… " + str(exc))

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
        self.qr_timer.stop()
        self.token = ""
        self.me = None
        self.current_chat = None
        self.config["token"] = ""
        save_config(self.config_path, self.config)
        self.stack.setCurrentWidget(self.login_page)
        QTimer.singleShot(150, self.start_desktop_qr_login)

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
        self.qr_timer.stop()
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
        if USE_REMOTE_SERVER:
            health = httpx.get(API_BASE + "/health", timeout=12.0)
            if health.status_code != 200 or not health.json().get("ok"):
                raise RuntimeError("Выбранный сервер не ответил корректно на /health.")
            backend, backend_thread = None, None
        else:
            backend, backend_thread = start_backend()
    except Exception as exc:
        QMessageBox.critical(None, "Kemtiz — подключение к серверу", f"{exc}\n\nПроверь адрес сервера и подключение к интернету. Журнал: {app_dir / 'desktop.log'}")
        return 1

    try:
        config = json.loads(config_path.read_text("utf-8")) if config_path.exists() else {}
    except Exception:
        config = {}
    config.setdefault("server_url", "")
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
            window.start_desktop_qr_login()
    else:
        window.start_desktop_qr_login()
    return app.exec()

if __name__ == "__main__":
    raise SystemExit(main())
