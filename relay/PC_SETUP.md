# Настройка Ghostly VPS с ПК

Ниже самый простой вариант для Windows.

## 1. Нужен VPS

Создай VPS с Ubuntu 22.04/24.04 или Debian и получи:
- публичный IPv4;
- логин, обычно \`root\`;
- пароль или SSH-ключ.

Для первого теста достаточно одного VPS. Лучше выбирать сервер физически близко к твоему региону и смотреть не только на географию, но и на качество маршрута.

## 2. Подключись с Windows

Открой PowerShell.

Если провайдер выдал IP \`203.0.113.10\`, подключение выглядит так:

\`\`\`powershell
ssh root@203.0.113.10
\`\`\`

Подтверди fingerprint, затем введи пароль.

Если провайдер дал другого пользователя:

\`\`\`powershell
ssh username@203.0.113.10
\`\`\`

После входа команды ниже выполняются уже на VPS.

## 3. Установи Ghostly Relay

\`\`\`bash
apt update
apt install -y git
git clone https://github.com/Selteck1/ApkGhostly.git
cd ApkGhostly
sudo bash relay/install_ubuntu.sh
\`\`\`

Установщик сам:
- установит Python, iproute2, iptables и curl;
- создаст секретный токен;
- создаст systemd-сервис;
- включит IP forwarding и NAT через relay;
- откроет UDP-порт через UFW, если UFW уже активен.

## 4. Посмотри IP и токен

Публичный IP:

\`\`\`bash
curl -4 ifconfig.me
\`\`\`

Токен:

\`\`\`bash
sudo grep '^GHOSTLY_RELAY_TOKEN=' /etc/ghostly-relay.env
\`\`\`

Порт по умолчанию:

\`\`\`text
51888/UDP
\`\`\`

В приложение вводятся именно эти три значения.

## 5. Проверь сервис

\`\`\`bash
systemctl status ghostly-relay --no-pager
\`\`\`

Нормально, когда есть:

\`\`\`text
Active: active (running)
\`\`\`

Если сервис не запустился:

\`\`\`bash
journalctl -u ghostly-relay -n 100 --no-pager
\`\`\`

## 6. Открой порт у VPS-провайдера

Кроме UFW, многие VPS-провайдеры имеют отдельный Cloud Firewall/Security Group.

Нужно разрешить:

\`\`\`text
UDP 51888
\`\`\`

Источник на первый тест можно оставить \`0.0.0.0/0\`, а потом при желании ограничить.

## 7. Настрой приложение

В Ghostly:

\`\`\`text
Режим: VPS Relay
IP / домен VPS: <публичный IPv4>
UDP порт: 51888
Секретный токен: <GHOSTLY_RELAY_TOKEN>
\`\`\`

Дальше:
1. \`Проверить VPS\`
2. \`Запустить через VPS\`
3. Разрешить системный VPN
4. \`Открыть Standoff 2\`

В VPN через relay идет только Standoff 2.

## 8. Проверка после перезагрузки VPS

\`\`\`bash
systemctl is-enabled ghostly-relay
systemctl is-active ghostly-relay
\`\`\`

Ожидается:

\`\`\`text
enabled
active
\`\`\`

