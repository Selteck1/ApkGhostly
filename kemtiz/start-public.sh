#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

ROOT="$HOME/KemtizProject/kemtiz"
CONFIG="$HOME/.config/kemtiz/env.sh"
LOG_DIR="$HOME/.config/kemtiz"
SERVER_LOG="$LOG_DIR/server.log"
TUNNEL_LOG="$LOG_DIR/tunnel.log"

if [ ! -d "$ROOT" ]; then
  echo "Не найдена папка проекта: $ROOT"
  exit 1
fi
if [ ! -f "$CONFIG" ]; then
  echo "Не найдена конфигурация $CONFIG"
  echo "Сначала настрой KEMTIZ_GOOGLE_CLIENT_ID."
  exit 1
fi
# shellcheck disable=SC1090
source "$CONFIG"
if [ -z "${KEMTIZ_GOOGLE_CLIENT_ID:-}" ]; then
  echo "KEMTIZ_GOOGLE_CLIENT_ID не задан в $CONFIG"
  exit 1
fi
if ! command -v cloudflared >/dev/null 2>&1; then
  echo "Не найден cloudflared."
  echo "Сначала установи его по инструкции в kemtiz/README.md."
  exit 1
fi

mkdir -p "$LOG_DIR"
cd "$ROOT"
source "$ROOT/.venv/bin/activate"

if curl -fsS http://127.0.0.1:8000/health >/dev/null 2>&1; then
  echo "На порту 8000 уже работает Kemtiz."
  echo "Останови старый сервер через CTRL+C, затем запусти этот скрипт заново."
  exit 1
fi

echo "Запускаю сервер Kemtiz..."
python -m uvicorn server:app --host 127.0.0.1 --port 8000 >>"$SERVER_LOG" 2>&1 &
SERVER_PID=$!

cleanup() {
  echo
  echo "Останавливаю туннель и локальный сервер..."
  kill "$SERVER_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

READY=0
for _ in $(seq 1 40); do
  if curl -fsS http://127.0.0.1:8000/health >/dev/null 2>&1; then
    READY=1
    break
  fi
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "Kemtiz не стартовал. Последние строки журнала:"
    tail -n 50 "$SERVER_LOG" || true
    exit 1
  fi
  sleep 1
done

if [ "$READY" -ne 1 ]; then
  echo "Сервер не ответил за 40 секунд. Журнал:"
  tail -n 50 "$SERVER_LOG" || true
  exit 1
fi

echo
echo "Kemtiz запущен локально."
echo "Сейчас создаю временный HTTPS-адрес для ПК и других телефонов."
echo "Не закрывай Termux и не нажимай CTRL+C, пока тестируешь."
echo "Адрес появится ниже как https://....trycloudflare.com"
echo "Журнал туннеля: $TUNNEL_LOG"
echo

set +e
cloudflared tunnel --url http://127.0.0.1:8000 2>&1 | tee "$TUNNEL_LOG"
STATUS=${PIPESTATUS[0]}
set -e
exit "$STATUS"
