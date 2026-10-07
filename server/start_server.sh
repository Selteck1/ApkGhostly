#!/data/data/com.termux/files/usr/bin/bash
set -e

cd "$(dirname "$0")"

if ! python -c "import Astandy" >/dev/null 2>&1; then
  echo "❌ Astandy не установлен."
  echo "Выполни: pip install -r server/requirements.txt"
  exit 1
fi

echo "👻 Starting Ghostly API..."
echo "📡 Port: 8081"
echo "🌐 Health: http://127.0.0.1:8081/api/health"
if [ -n "$STANDOFF2_HANDSHAKE" ]; then
  echo "🎮 Standoff 2 API: configured"
else
  echo "⚠️ Standoff 2 API: NOT configured"
fi
echo

python server.py
