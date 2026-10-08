#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$(dirname "$0")"
export GHOSTLY_SITE_PORT="${GHOSTLY_SITE_PORT:-8080}"
export GHOSTLY_BOT_URL="${GHOSTLY_BOT_URL:-https://t.me/Ghostlyso2Bot}"
python server.py
