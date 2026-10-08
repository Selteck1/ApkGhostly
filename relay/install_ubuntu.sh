#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/opt/ghostly-relay"
ENV_FILE="/etc/ghostly-relay.env"
SERVICE_FILE="/etc/systemd/system/ghostly-relay.service"
PORT="${GHOSTLY_RELAY_PORT:-51888}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Запусти от root: sudo bash relay/install_ubuntu.sh"
  exit 1
fi

apt-get update
apt-get install -y python3 iproute2 iptables

mkdir -p "${APP_DIR}"
install -m 0755 relay/relay.py "${APP_DIR}/relay.py"

if [[ ! -f "${ENV_FILE}" ]]; then
  TOKEN="${GHOSTLY_RELAY_TOKEN:-$(python3 - <<'PY'
import secrets
print(secrets.token_urlsafe(32))
PY
)}"
  cat > "${ENV_FILE}" <<EOF
GHOSTLY_RELAY_TOKEN=${TOKEN}
GHOSTLY_RELAY_PORT=${PORT}
GHOSTLY_RELAY_TUN_NAME=ghostly0
GHOSTLY_RELAY_TUN_SERVER_IP=10.77.0.1
GHOSTLY_RELAY_TUN_CIDR=10.77.0.0/24
EOF
  chmod 600 "${ENV_FILE}"
fi

if command -v ufw >/dev/null 2>&1 && ufw status | grep -q "Status: active"; then
  ufw allow "${PORT}/udp" >/dev/null || true
fi

cat > "${SERVICE_FILE}" <<EOF
[Unit]
Description=Ghostly UDP Game Relay
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
EnvironmentFile=${ENV_FILE}
ExecStart=/usr/bin/python3 ${APP_DIR}/relay.py
Restart=always
RestartSec=2
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now ghostly-relay.service

TOKEN="$(sed -n 's/^GHOSTLY_RELAY_TOKEN=//p' "${ENV_FILE}")"
SERVER_IP="$(hostname -I | awk '{print $1}')"

echo
echo "=============================================="
echo "👻 Ghostly relay installed"
echo "=============================================="
echo "Server IP: ${SERVER_IP}"
echo "UDP port:  ${PORT}"
echo "Token:     ${TOKEN}"
echo
echo "Проверь:"
echo "  systemctl status ghostly-relay --no-pager"
echo "  journalctl -u ghostly-relay -n 50 --no-pager"
echo
echo "В Ghostly Booster укажи IP сервера, порт ${PORT} и этот token."
