from __future__ import annotations

import asyncio
import base64
import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import time
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from fastapi import Depends, FastAPI, HTTPException, Request, WebSocket, WebSocketDisconnect
import httpx
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

ROOT = Path(__file__).resolve().parent
WEB_DIR = ROOT / "web"
DATA_DIR = Path(os.environ.get("KEMTIZ_DATA_DIR", str(ROOT / "data")))
DATA_DIR.mkdir(parents=True, exist_ok=True)
DB_PATH = Path(os.environ.get("KEMTIZ_DB_PATH", str(DATA_DIR / "kemtiz.sqlite3")))
SECRET_PATH = DATA_DIR / ".token_secret"


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def get_secret() -> bytes:
    value = os.environ.get("KEMTIZ_SECRET")
    if value:
        return value.encode("utf-8")
    if not SECRET_PATH.exists():
        SECRET_PATH.write_text(secrets.token_urlsafe(64), encoding="utf-8")
        try:
            SECRET_PATH.chmod(0o600)
        except OSError:
            pass
    return SECRET_PATH.read_text(encoding="utf-8").strip().encode("utf-8")


SECRET = get_secret()
TOKEN_TTL = 60 * 60 * 24 * 30
MAX_MESSAGE = 4000

app = FastAPI(title="Kemtiz", version="0.1.0", docs_url="/api/docs", redoc_url=None)
app.mount("/assets", StaticFiles(directory=str(WEB_DIR)), name="assets")


@contextmanager
def db():
    conn = sqlite3.connect(DB_PATH, timeout=10)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys=ON")
    conn.execute("PRAGMA busy_timeout=10000")
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def init_db() -> None:
    with db() as c:
        c.execute("PRAGMA journal_mode=WAL")
        c.executescript("""
        CREATE TABLE IF NOT EXISTS users(
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          username TEXT NOT NULL COLLATE NOCASE UNIQUE,
          display_name TEXT NOT NULL,
          password_hash TEXT NOT NULL,
          phone_number TEXT,
          created_at TEXT NOT NULL,
          last_seen_at TEXT
        );
        CREATE TABLE IF NOT EXISTS friend_requests(
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          from_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          to_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          status TEXT NOT NULL DEFAULT 'pending',
          created_at TEXT NOT NULL,
          UNIQUE(from_id,to_id),
          CHECK(from_id != to_id)
        );
        CREATE TABLE IF NOT EXISTS friendships(
          user_low INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          user_high INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          created_at TEXT NOT NULL,
          PRIMARY KEY(user_low,user_high),
          CHECK(user_low < user_high)
        );
        CREATE TABLE IF NOT EXISTS chats(
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          kind TEXT NOT NULL CHECK(kind IN ('direct','group')),
          title TEXT,
          direct_key TEXT UNIQUE,
          created_by INTEGER NOT NULL REFERENCES users(id),
          created_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS chat_members(
          chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
          user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          role TEXT NOT NULL DEFAULT 'member',
          joined_at TEXT NOT NULL,
          last_read_id INTEGER DEFAULT 0,
          PRIMARY KEY(chat_id,user_id)
        );
        CREATE TABLE IF NOT EXISTS messages(
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
          sender_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          body TEXT NOT NULL,
          created_at TEXT NOT NULL,
          edited_at TEXT,
          deleted_at TEXT
        );
        CREATE INDEX IF NOT EXISTS idx_messages_chat ON messages(chat_id,id DESC);
        CREATE INDEX IF NOT EXISTS idx_members_user ON chat_members(user_id,chat_id);
        CREATE INDEX IF NOT EXISTS idx_requests_inbox ON friend_requests(to_id,status);
        CREATE TABLE IF NOT EXISTS auth_codes(
          phone_number TEXT PRIMARY KEY,
          code_hash TEXT NOT NULL,
          expires_at INTEGER NOT NULL,
          attempts INTEGER NOT NULL DEFAULT 0,
          last_sent_at INTEGER NOT NULL,
          window_started_at INTEGER NOT NULL,
          send_count INTEGER NOT NULL DEFAULT 1
        );
        CREATE TABLE IF NOT EXISTS auth_rate_limits(
          phone_number TEXT PRIMARY KEY,
          last_sent_at INTEGER NOT NULL,
          window_started_at INTEGER NOT NULL,
          send_count INTEGER NOT NULL DEFAULT 1
        );
        """)
        user_columns = {row["name"] for row in c.execute("PRAGMA table_info(users)").fetchall()}
        if "phone_number" not in user_columns:
            c.execute("ALTER TABLE users ADD COLUMN phone_number TEXT")
        c.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone_number "
                  "ON users(phone_number) WHERE phone_number IS NOT NULL")


@app.on_event("startup")
async def startup():
    init_db()


def b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def token_for(uid: int) -> str:
    now = int(time.time())
    head = b64(json.dumps({"alg":"HS256","typ":"JWT"}, separators=(",",":")).encode())
    body = b64(json.dumps({"sub":uid,"iat":now,"exp":now+TOKEN_TTL}, separators=(",",":")).encode())
    msg = (head + "." + body).encode()
    sig = b64(hmac.new(SECRET, msg, hashlib.sha256).digest())
    return head + "." + body + "." + sig


def user_id_from_token(token: str) -> int:
    try:
        head, body, sig = token.split(".")
        expected = b64(hmac.new(SECRET, (head + "." + body).encode(), hashlib.sha256).digest())
        if not hmac.compare_digest(sig, expected):
            raise ValueError("signature")
        payload = json.loads(base64.urlsafe_b64decode(body + "=" * (-len(body) % 4)))
        if int(payload["exp"]) < int(time.time()):
            raise ValueError("expired")
        return int(payload["sub"])
    except Exception:
        raise HTTPException(status_code=401, detail="Сессия истекла. Войди снова.")


def password_hash(password: str) -> str:
    salt = secrets.token_bytes(16)
    key = hashlib.scrypt(password.encode(), salt=salt, n=2**14, r=8, p=1, dklen=32)
    return "scrypt$" + b64(salt) + "$" + b64(key)


def password_ok(password: str, stored: str) -> bool:
    try:
        algo, salt_s, key_s = stored.split("$")
        if algo != "scrypt":
            return False
        salt = base64.urlsafe_b64decode(salt_s + "=" * (-len(salt_s) % 4))
        expected = base64.urlsafe_b64decode(key_s + "=" * (-len(key_s) % 4))
        actual = hashlib.scrypt(password.encode(), salt=salt, n=2**14, r=8, p=1, dklen=len(expected))
        return hmac.compare_digest(actual, expected)
    except Exception:
        return False


def public_user(row: sqlite3.Row) -> dict[str, Any]:
    return {"id": row["id"], "username": row["username"], "display_name": row["display_name"],
            "created_at": row["created_at"], "last_seen_at": row["last_seen_at"]}


def auth_user(request: Request) -> dict[str, Any]:
    header = request.headers.get("authorization", "")
    if not header.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="Нужна авторизация.")
    uid = user_id_from_token(header[7:].strip())
    with db() as c:
        row = c.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone()
    if row is None:
        raise HTTPException(status_code=401, detail="Пользователь не найден.")
    return public_user(row)


class PhoneCodeRequest(BaseModel):
    phone: str = Field(min_length=6, max_length=32)


class PhoneCodeVerify(BaseModel):
    phone: str = Field(min_length=6, max_length=32)
    code: str = Field(pattern=r"^\d{6}$")


class FriendIn(BaseModel):
    username: str = Field(min_length=3, max_length=24)


class MessageIn(BaseModel):
    body: str = Field(min_length=1, max_length=MAX_MESSAGE)


class GroupIn(BaseModel):
    title: str = Field(min_length=1, max_length=64)
    member_ids: list[int] = Field(default_factory=list, max_length=9)


class ReadIn(BaseModel):
    last_read_message_id: int = Field(ge=0)


class LiveConnections:
    def __init__(self):
        self.by_user: dict[int, set[WebSocket]] = {}
        self.lock = asyncio.Lock()

    async def add(self, uid: int, ws: WebSocket):
        async with self.lock:
            self.by_user.setdefault(uid, set()).add(ws)

    async def remove(self, uid: int, ws: WebSocket) -> bool:
        async with self.lock:
            sockets = self.by_user.get(uid)
            if not sockets:
                return True
            sockets.discard(ws)
            if not sockets:
                self.by_user.pop(uid, None)
                return True
            return False

    async def send_user(self, uid: int, data: dict[str, Any]):
        for ws in list(self.by_user.get(uid, set())):
            try:
                await ws.send_json(data)
            except Exception:
                pass

    async def chat_event(self, chat_id: int, data: dict[str, Any]):
        with db() as c:
            ids = [int(r["user_id"]) for r in c.execute(
                "SELECT user_id FROM chat_members WHERE chat_id=?", (chat_id,)).fetchall()]
        for uid in ids:
            await self.send_user(uid, data)

    async def set_presence(self, uid: int, online: bool):
        with db() as c:
            rows = c.execute(
                "SELECT CASE WHEN user_low=? THEN user_high ELSE user_low END AS fid "
                "FROM friendships WHERE user_low=? OR user_high=?", (uid, uid, uid)).fetchall()
        for row in rows:
            await self.send_user(int(row["fid"]), {"type":"presence","user_id":uid,"online":online})


live = LiveConnections()


def ensure_member(c: sqlite3.Connection, chat_id: int, uid: int):
    if not c.execute("SELECT 1 FROM chat_members WHERE chat_id=? AND user_id=?", (chat_id,uid)).fetchone():
        raise HTTPException(status_code=403, detail="Нет доступа к этому чату.")


def ensure_friends(c: sqlite3.Connection, a: int, b: int):
    lo, hi = sorted((a,b))
    if not c.execute("SELECT 1 FROM friendships WHERE user_low=? AND user_high=?", (lo,hi)).fetchone():
        raise HTTPException(status_code=403, detail="Сначала добавьте друг друга в друзья.")


def chat_info(chat_id: int, uid: int) -> dict[str, Any]:
    with db() as c:
        row = c.execute("""
          SELECT ch.*, cm.role,
            msg.body AS last_message, msg.id AS last_message_id, msg.created_at AS last_message_at,
            other.id AS other_id, other.username AS other_username, other.display_name AS other_display_name
          FROM chats ch
          JOIN chat_members cm ON cm.chat_id=ch.id AND cm.user_id=?
          LEFT JOIN messages msg ON msg.id=(SELECT MAX(id) FROM messages WHERE chat_id=ch.id AND deleted_at IS NULL)
          LEFT JOIN chat_members ocm ON ocm.chat_id=ch.id AND ocm.user_id!=?
          LEFT JOIN users other ON other.id=ocm.user_id AND ch.kind='direct'
          WHERE ch.id=?""", (uid,uid,chat_id)).fetchone()
        if row is None:
            raise HTTPException(status_code=404, detail="Чат не найден.")
        members = c.execute("""
          SELECT u.id,u.username,u.display_name,u.created_at,u.last_seen_at
          FROM chat_members cm JOIN users u ON u.id=cm.user_id
          WHERE cm.chat_id=? ORDER BY cm.joined_at""", (chat_id,)).fetchall()
    title = row["title"] or row["other_display_name"] or row["other_username"] or "Личный чат"
    return {
        "id":row["id"],"kind":row["kind"],"title":title,"role":row["role"],
        "created_at":row["created_at"],"last_message":row["last_message"],
        "last_message_id":row["last_message_id"],"last_message_at":row["last_message_at"],
        "other_user_id":row["other_id"],
        "members":[{**public_user(m),"online":int(m["id"]) in live.by_user} for m in members]
    }


@app.get("/")
def home():
    return FileResponse(WEB_DIR / "index.html")


@app.get("/sw.js")
def service_worker():
    return FileResponse(WEB_DIR / "sw.js", media_type="application/javascript", headers={"Service-Worker-Allowed": "/"})


@app.get("/health")
def health():
    with db() as c:
        users = c.execute("SELECT COUNT(*) n FROM users").fetchone()["n"]
        messages = c.execute("SELECT COUNT(*) n FROM messages").fetchone()["n"]
    return {"ok":True,"app":"Kemtiz","users":users,"messages":messages}


def normalize_phone(value: str) -> str:
    compact = re.sub(r"[\s().-]", "", value.strip())
    if compact.startswith("00"):
        compact = "+" + compact[2:]
    digits = compact[1:] if compact.startswith("+") else compact
    if not digits.isdigit():
        raise HTTPException(status_code=422, detail="Введи номер телефона с кодом страны, например +7 900 123-45-67.")
    if len(digits) == 11 and digits.startswith("8"):
        digits = "7" + digits[1:]
    elif len(digits) == 10 and digits.startswith("9"):
        digits = "7" + digits
    if not 8 <= len(digits) <= 15 or digits.startswith("0"):
        raise HTTPException(status_code=422, detail="Проверь номер телефона и укажи код страны.")
    return "+" + digits


def otp_hash(phone: str, code: str) -> str:
    raw = f"kemtiz:sms-code:{phone}:{code}".encode("utf-8")
    return hmac.new(SECRET, raw, hashlib.sha256).hexdigest()


async def send_sms_code(phone: str, code: str) -> None:
    api_id = os.environ.get("SMSRU_API_ID", "").strip()
    if not api_id:
        raise HTTPException(
            status_code=503,
            detail="SMS пока не подключены. Настрой SMSRU_API_ID в Termux — ключ SMS.RU.",
        )

    digits = phone[1:]
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.post(
                "https://sms.ru/sms/send",
                data={
                    "api_id": api_id,
                    "to": digits,
                    "msg": f"Kemtiz: код подтверждения {code}. Никому его не сообщай.",
                    "json": "1",
                },
            )
            response.raise_for_status()
            payload = response.json()
    except (httpx.HTTPError, ValueError):
        raise HTTPException(
            status_code=502,
            detail="Не удалось связаться с SMS-сервисом. Попробуй ещё раз позже.",
        )

    result = (payload.get("sms") or {}).get(digits) or {}
    if payload.get("status") != "OK" or result.get("status") != "OK":
        provider_message = result.get("status_text") or payload.get("status_text")
        provider_code = result.get("status_code") or payload.get("status_code")
        detail = provider_message or (f"код {provider_code}" if provider_code else "провайдер отклонил отправку")
        raise HTTPException(status_code=502, detail=f"SMS-сервис не отправил сообщение: {detail}.")


@app.post("/api/auth/request-code")
async def request_auth_code(body: PhoneCodeRequest, request: Request):
    phone = normalize_phone(body.phone)
    if not os.environ.get("SMSRU_API_ID", "").strip():
        raise HTTPException(
            status_code=503,
            detail="SMS пока не подключены. Нужен API-ключ SMS.RU в переменной SMSRU_API_ID.",
        )

    now = int(time.time())
    code = f"{secrets.randbelow(1_000_000):06d}"
    hashed = otp_hash(phone, code)

    with db() as c:
        rate = c.execute(
            "SELECT last_sent_at,window_started_at,send_count FROM auth_rate_limits WHERE phone_number=?",
            (phone,),
        ).fetchone()
        if rate:
            elapsed = now - int(rate["last_sent_at"])
            if elapsed < 60:
                wait = 60 - elapsed
                raise HTTPException(
                    status_code=429,
                    detail=f"Подожди {wait} сек. перед повторной отправкой кода.",
                )
            window_started = int(rate["window_started_at"])
            send_count = int(rate["send_count"])
            if now - window_started < 3600:
                if send_count >= 5:
                    raise HTTPException(
                        status_code=429,
                        detail="Для этого номера уже запрошено 5 кодов за час. Попробуй позже.",
                    )
                send_count += 1
            else:
                window_started = now
                send_count = 1
        else:
            window_started = now
            send_count = 1

        c.execute(
            """INSERT INTO auth_rate_limits(phone_number,last_sent_at,window_started_at,send_count)
               VALUES(?,?,?,?)
               ON CONFLICT(phone_number) DO UPDATE SET
                 last_sent_at=excluded.last_sent_at,
                 window_started_at=excluded.window_started_at,
                 send_count=excluded.send_count""",
            (phone, now, window_started, send_count),
        )
        c.execute(
            """INSERT INTO auth_codes(phone_number,code_hash,expires_at,attempts,last_sent_at,window_started_at,send_count)
               VALUES(?,?,?,0,?,?,?)
               ON CONFLICT(phone_number) DO UPDATE SET
                 code_hash=excluded.code_hash,
                 expires_at=excluded.expires_at,
                 attempts=0,
                 last_sent_at=excluded.last_sent_at,
                 window_started_at=excluded.window_started_at,
                 send_count=excluded.send_count""",
            (phone, hashed, now + 300, now, window_started, send_count),
        )

    try:
        await send_sms_code(phone, code)
    except HTTPException:
        with db() as c:
            c.execute("DELETE FROM auth_codes WHERE phone_number=? AND code_hash=?", (phone, hashed))
        raise

    return {"ok": True, "phone": phone, "message": "Код подтверждения отправлен по SMS."}

@app.post("/api/auth/verify-code")
def verify_auth_code(body: PhoneCodeVerify):
    phone = normalize_phone(body.phone)
    now = int(time.time())
    failure: tuple[int, str] | None = None
    fresh_user: dict[str, Any] | None = None
    uid: int | None = None

    with db() as c:
        stored = c.execute("SELECT * FROM auth_codes WHERE phone_number=?", (phone,)).fetchone()
        if stored is None:
            failure = (400, "Сначала запроси код по SMS.")
        elif int(stored["expires_at"]) <= now:
            c.execute("DELETE FROM auth_codes WHERE phone_number=?", (phone,))
            failure = (400, "Код истёк. Запроси новый.")
        elif int(stored["attempts"]) >= 5:
            c.execute("DELETE FROM auth_codes WHERE phone_number=?", (phone,))
            failure = (429, "Слишком много попыток. Запроси новый код.")
        elif not hmac.compare_digest(str(stored["code_hash"]), otp_hash(phone, body.code)):
            attempts = int(stored["attempts"]) + 1
            if attempts >= 5:
                c.execute("DELETE FROM auth_codes WHERE phone_number=?", (phone,))
                failure = (429, "Слишком много попыток. Запроси новый код.")
            else:
                c.execute("UPDATE auth_codes SET attempts=? WHERE phone_number=?", (attempts, phone))
                failure = (400, f"Неверный код. Осталось попыток: {5 - attempts}.")
        else:
            c.execute("DELETE FROM auth_codes WHERE phone_number=?", (phone,))
            row = c.execute("SELECT * FROM users WHERE phone_number=?", (phone,)).fetchone()
            now_text = utc_now()
            if row is None:
                username = "kemtiz_" + secrets.token_hex(4)
                while c.execute("SELECT 1 FROM users WHERE username=?", (username,)).fetchone():
                    username = "kemtiz_" + secrets.token_hex(4)
                display_name = "Пользователь " + phone[-4:]
                cur = c.execute(
                    """INSERT INTO users(username,display_name,password_hash,phone_number,created_at,last_seen_at)
                       VALUES(?,?,?,?,?,?)""",
                    (username, display_name, "otp-only", phone, now_text, now_text),
                )
                uid = int(cur.lastrowid)
                row = c.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone()
            else:
                uid = int(row["id"])
                c.execute("UPDATE users SET last_seen_at=? WHERE id=?", (now_text, uid))
                row = c.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone()
            fresh_user = public_user(row)

    if failure:
        raise HTTPException(status_code=failure[0], detail=failure[1])
    if uid is None or fresh_user is None:
        raise HTTPException(status_code=500, detail="Не удалось завершить вход. Попробуй снова.")
    return {"token": token_for(uid), "user": fresh_user}


@app.get("/api/me")
def me(user=Depends(auth_user)):
    return user


@app.get("/api/users/search")
def search_users(q: str="", user=Depends(auth_user)):
    q = q.strip().lower()
    if len(q) < 2:
        return []
    pattern = "%" + q.replace("%","\\%").replace("_","\\_") + "%"
    with db() as c:
        rows = c.execute(
            "SELECT * FROM users WHERE id!=? AND (username LIKE ? ESCAPE '\\' OR display_name LIKE ? ESCAPE '\\') "
            "ORDER BY username LIMIT 20", (user["id"],pattern,pattern)).fetchall()
    return [public_user(r) for r in rows]


@app.post("/api/friends/requests")
async def request_friend(body: FriendIn, user=Depends(auth_user)):
    with db() as c:
        target = c.execute("SELECT id,username FROM users WHERE username=?", (body.username.strip().lower(),)).fetchone()
        if target is None:
            raise HTTPException(status_code=404, detail="Пользователь не найден.")
        tid, uid = int(target["id"]), int(user["id"])
        if tid == uid:
            raise HTTPException(status_code=400, detail="Нельзя добавить самого себя.")
        lo, hi = sorted((tid,uid))
        if c.execute("SELECT 1 FROM friendships WHERE user_low=? AND user_high=?", (lo,hi)).fetchone():
            raise HTTPException(status_code=409, detail="Вы уже друзья.")
        reverse = c.execute("SELECT id FROM friend_requests WHERE from_id=? AND to_id=? AND status='pending'",(tid,uid)).fetchone()
        if reverse:
            raise HTTPException(status_code=409, detail="Этот пользователь уже отправил тебе заявку.")
        old = c.execute("SELECT id,status FROM friend_requests WHERE from_id=? AND to_id=?",(uid,tid)).fetchone()
        if old and old["status"] == "pending":
            raise HTTPException(status_code=409, detail="Заявка уже отправлена.")
        if old:
            c.execute("UPDATE friend_requests SET status='pending',created_at=? WHERE id=?",(utc_now(),old["id"]))
        else:
            c.execute("INSERT INTO friend_requests(from_id,to_id,status,created_at) VALUES(?,?,'pending',?)",(uid,tid,utc_now()))
    await live.send_user(tid, {"type":"friend_request"})
    return {"ok":True,"message":"Заявка отправлена."}


@app.get("/api/friends/requests")
def friend_requests(user=Depends(auth_user)):
    with db() as c:
        incoming = c.execute("""
          SELECT fr.id AS request_id,u.* FROM friend_requests fr JOIN users u ON u.id=fr.from_id
          WHERE fr.to_id=? AND fr.status='pending' ORDER BY fr.id DESC""",(user["id"],)).fetchall()
        outgoing = c.execute("""
          SELECT fr.id AS request_id,u.* FROM friend_requests fr JOIN users u ON u.id=fr.to_id
          WHERE fr.from_id=? AND fr.status='pending' ORDER BY fr.id DESC""",(user["id"],)).fetchall()
    def shape(r):
        return {**public_user(r),"request_id":r["request_id"]}
    return {"incoming":[shape(r) for r in incoming],"outgoing":[shape(r) for r in outgoing]}


@app.post("/api/friends/requests/{request_id}/{action}")
async def respond_friend(request_id: int, action: str, user=Depends(auth_user)):
    if action not in ("accept","decline"):
        raise HTTPException(status_code=404, detail="Неизвестное действие.")
    with db() as c:
        req = c.execute("SELECT * FROM friend_requests WHERE id=? AND to_id=? AND status='pending'",(request_id,user["id"])).fetchone()
        if req is None:
            raise HTTPException(status_code=404, detail="Заявка не найдена.")
        sender = int(req["from_id"])
        if action == "accept":
            lo, hi = sorted((sender,int(user["id"])))
            c.execute("INSERT OR IGNORE INTO friendships(user_low,user_high,created_at) VALUES(?,?,?)",(lo,hi,utc_now()))
            c.execute("UPDATE friend_requests SET status='accepted' WHERE id=?",(request_id,))
        else:
            c.execute("UPDATE friend_requests SET status='declined' WHERE id=?",(request_id,))
    await live.send_user(sender,{"type":"friend_list_changed"})
    await live.send_user(int(user["id"]),{"type":"friend_list_changed"})
    return {"ok":True}


@app.get("/api/friends")
def friends(user=Depends(auth_user)):
    with db() as c:
        rows = c.execute("""
          SELECT u.* FROM friendships f JOIN users u
          ON u.id=CASE WHEN f.user_low=? THEN f.user_high ELSE f.user_low END
          WHERE f.user_low=? OR f.user_high=? ORDER BY u.display_name COLLATE NOCASE""",
          (user["id"],user["id"],user["id"])).fetchall()
    return [{**public_user(r),"online":int(r["id"]) in live.by_user} for r in rows]


@app.post("/api/chats/direct/{other_id}")
def create_direct(other_id: int, user=Depends(auth_user)):
    uid = int(user["id"])
    lo, hi = sorted((uid,other_id))
    with db() as c:
        ensure_friends(c,uid,other_id)
        key = str(lo) + ":" + str(hi)
        row = c.execute("SELECT id FROM chats WHERE direct_key=?",(key,)).fetchone()
        if row:
            cid = int(row["id"])
        else:
            cur = c.execute("INSERT INTO chats(kind,direct_key,created_by,created_at) VALUES('direct',?,?,?)",(key,uid,utc_now()))
            cid = int(cur.lastrowid)
            c.executemany("INSERT INTO chat_members(chat_id,user_id,role,joined_at) VALUES(?,?,?,?)",
                [(cid,lo,"member",utc_now()),(cid,hi,"member",utc_now())])
    return chat_info(cid,uid)


@app.post("/api/chats/group")
async def create_group(body: GroupIn, user=Depends(auth_user)):
    title = body.title.strip()
    uid = int(user["id"])
    members = sorted(set(body.member_ids + [uid]))
    if not title or len(members) < 2:
        raise HTTPException(status_code=422, detail="Укажи название и выбери хотя бы одного друга.")
    with db() as c:
        for mid in members:
            if mid != uid:
                ensure_friends(c,uid,mid)
        cur = c.execute("INSERT INTO chats(kind,title,created_by,created_at) VALUES('group',?,?,?)",(title,uid,utc_now()))
        cid = int(cur.lastrowid)
        c.executemany("INSERT INTO chat_members(chat_id,user_id,role,joined_at) VALUES(?,?,?,?)",
            [(cid,mid,"owner" if mid==uid else "member",utc_now()) for mid in members])
    await live.chat_event(cid,{"type":"chat_list_changed"})
    return chat_info(cid,uid)


@app.get("/api/chats")
def chats(user=Depends(auth_user)):
    uid = int(user["id"])
    with db() as c:
        rows = c.execute("SELECT chat_id FROM chat_members WHERE user_id=?",(uid,)).fetchall()
    result = [chat_info(int(r["chat_id"]),uid) for r in rows]
    result.sort(key=lambda x:x.get("last_message_at") or x["created_at"],reverse=True)
    return result


@app.get("/api/chats/{chat_id}/messages")
def messages(chat_id: int, before_id: int|None=None, limit: int=50, user=Depends(auth_user)):
    limit = max(1,min(limit,100))
    with db() as c:
        ensure_member(c,chat_id,int(user["id"]))
        if before_id is None:
            rows = c.execute("""
              SELECT m.id,m.chat_id,m.sender_id,m.body,m.created_at,m.edited_at,
                     u.username AS sender_username,u.display_name AS sender_display_name
              FROM messages m JOIN users u ON u.id=m.sender_id
              WHERE m.chat_id=? AND m.deleted_at IS NULL ORDER BY m.id DESC LIMIT ?""",(chat_id,limit)).fetchall()
        else:
            rows = c.execute("""
              SELECT m.id,m.chat_id,m.sender_id,m.body,m.created_at,m.edited_at,
                     u.username AS sender_username,u.display_name AS sender_display_name
              FROM messages m JOIN users u ON u.id=m.sender_id
              WHERE m.chat_id=? AND m.id<? AND m.deleted_at IS NULL ORDER BY m.id DESC LIMIT ?""",
              (chat_id,before_id,limit)).fetchall()
    return [dict(r) for r in reversed(rows)]


@app.post("/api/chats/{chat_id}/messages")
async def send_message(chat_id: int, body: MessageIn, user=Depends(auth_user)):
    text = body.body.strip()
    if not text:
        raise HTTPException(status_code=422, detail="Пустое сообщение отправить нельзя.")
    with db() as c:
        ensure_member(c,chat_id,int(user["id"]))
        cur = c.execute("INSERT INTO messages(chat_id,sender_id,body,created_at) VALUES(?,?,?,?)",
            (chat_id,int(user["id"]),text,utc_now()))
        mid = int(cur.lastrowid)
        row = c.execute("""
          SELECT m.id,m.chat_id,m.sender_id,m.body,m.created_at,m.edited_at,
                 u.username AS sender_username,u.display_name AS sender_display_name
          FROM messages m JOIN users u ON u.id=m.sender_id WHERE m.id=?""",(mid,)).fetchone()
        payload = dict(row)
    await live.chat_event(chat_id,{"type":"message.new","message":payload})
    await live.chat_event(chat_id,{"type":"chat_list_changed"})
    return payload


@app.post("/api/chats/{chat_id}/read")
async def read_chat(chat_id: int, body: ReadIn, user=Depends(auth_user)):
    uid = int(user["id"])
    with db() as c:
        ensure_member(c,chat_id,uid)
        c.execute("UPDATE chat_members SET last_read_id=MAX(last_read_id,?) WHERE chat_id=? AND user_id=?",
            (body.last_read_message_id,chat_id,uid))
        ids = [int(r["user_id"]) for r in c.execute("SELECT user_id FROM chat_members WHERE chat_id=?",(chat_id,)).fetchall()]
    payload = {"type":"message.read","chat_id":chat_id,"user_id":uid,"last_read_message_id":body.last_read_message_id}
    for mid in ids:
        await live.send_user(mid,payload)
    return {"ok":True}


@app.post("/api/messages/{message_id}/delete")
async def delete_message(message_id: int, user=Depends(auth_user)):
    with db() as c:
        row = c.execute("SELECT chat_id FROM messages WHERE id=? AND sender_id=? AND deleted_at IS NULL",
            (message_id,int(user["id"]))).fetchone()
        if row is None:
            raise HTTPException(status_code=404,detail="Сообщение не найдено.")
        cid = int(row["chat_id"])
        c.execute("UPDATE messages SET body='Сообщение удалено',deleted_at=? WHERE id=?",(utc_now(),message_id))
    await live.chat_event(cid,{"type":"message.deleted","chat_id":cid,"message_id":message_id})
    await live.chat_event(cid,{"type":"chat_list_changed"})
    return {"ok":True}


@app.websocket("/ws")
async def websocket(ws: WebSocket):
    await ws.accept()
    uid = None
    try:
        try:
            first = await asyncio.wait_for(ws.receive_json(),timeout=10)
        except asyncio.TimeoutError:
            await ws.close(code=4401,reason="auth timeout")
            return
        if not isinstance(first,dict) or first.get("type")!="auth" or not isinstance(first.get("token"),str):
            await ws.close(code=4401,reason="auth required")
            return
        try:
            uid = user_id_from_token(first["token"])
        except HTTPException:
            await ws.close(code=4401,reason="invalid token")
            return
        with db() as c:
            if not c.execute("SELECT 1 FROM users WHERE id=?",(uid,)).fetchone():
                await ws.close(code=4401,reason="user missing")
                return
            c.execute("UPDATE users SET last_seen_at=? WHERE id=?",(utc_now(),uid))
        was_online = uid in live.by_user
        await live.add(uid,ws)
        if not was_online:
            await live.set_presence(uid,True)
        await ws.send_json({"type":"ready","user_id":uid})
        while True:
            data = await ws.receive_json()
            if not isinstance(data,dict):
                continue
            if data.get("type")=="ping":
                await ws.send_json({"type":"pong","time":utc_now()})
            elif data.get("type")=="typing":
                try:
                    cid = int(data.get("chat_id"))
                except (TypeError,ValueError):
                    continue
                with db() as c:
                    member = c.execute("SELECT 1 FROM chat_members WHERE chat_id=? AND user_id=?",(cid,uid)).fetchone()
                    ids = [int(r["user_id"]) for r in c.execute("SELECT user_id FROM chat_members WHERE chat_id=? AND user_id!=?",(cid,uid)).fetchall()] if member else []
                event = {"type":"typing","chat_id":cid,"user_id":uid}
                for target in ids:
                    await live.send_user(target,event)
    except WebSocketDisconnect:
        pass
    except Exception:
        try:
            await ws.close(code=1011,reason="server error")
        except Exception:
            pass
    finally:
        if uid is not None:
            offline = await live.remove(uid,ws)
            if offline:
                with db() as c:
                    c.execute("UPDATE users SET last_seen_at=? WHERE id=?",(utc_now(),uid))
                await live.set_presence(uid,False)
