#!/data/data/com.termux/files/usr/bin/bash
set -e

cd "$(dirname "$0")"
echo "👻 Starting Ghostly API..."
echo "📡 Port: 8081"
echo "🌐 Health: http://127.0.0.1:8081/api/health"
echo

python server.py
