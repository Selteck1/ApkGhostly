# Ghostly UDP Relay

Это лёгкий IPv4 UDP-relay для Ghostly Booster. Он не меняет пакеты Standoff 2: Android-приложение получает системный VPN-интерфейс, отправляет сырые IPv4-пакеты по авторизованному UDP-туннелю на relay, а Linux VPS маршрутизирует их дальше в интернет через обычный NAT.

Схема:

```
Standoff 2
   ↓
Android VpnService
   ↓  UDP + HMAC
Ghostly Relay (VPS)
   ↓  Linux routing/NAT
Internet / Standoff 2
```

## Что нужно

Ubuntu/Debian VPS с:
- публичным IPv4;
- открытым UDP-портом 51888 (можно изменить);
- root-доступом.

Root нужен только **на VPS** для создания TUN-интерфейса и настройки NAT. На Android root не нужен.

## Установка

Склонируй репозиторий на VPS:

```bash
git clone https://github.com/Selteck1/ApkGhostly.git
cd ApkGhostly
sudo bash relay/install_ubuntu.sh
```

После установки появятся:
- `ghostly-relay.service`;
- `/etc/ghostly-relay.env`;
- интерфейс `ghostly0`;
- UDP listener на 51888.

Проверка:

```bash
systemctl status ghostly-relay --no-pager
journalctl -u ghostly-relay -n 50 --no-pager
```

## Настройка APK

В Ghostly Booster:
1. IP / домен relay;
2. UDP порт;
3. Secret token, который напечатал установщик;
4. «Проверить relay»;
5. «Запустить Ghostly Boost»;
6. Android покажет системное подтверждение VPN.

В текущей версии VPN разрешает только пакет Standoff 2:
`com.axlebolt.standoff2`.

Это сделано намеренно: весь остальной трафик телефона не отправляется через relay.

## Как добиться реально меньшего ping

Relay полезен только тогда, когда маршрут:

```
телефон → relay → сервер игры
```

быстрее исходного:

```
телефон → провайдер → сервер игры
```

Поэтому один relay не гарантирует 50 → 20 ms. Лучше иметь несколько VPS в разных сетях/локациях и сравнивать их RTT. Ghostly Booster уже показывает RTT до relay, а окончательную победу измеряй по ping/jitter/loss внутри Standoff 2.

## Ограничения текущей версии

- Только IPv4 tunnel.
- Нет шифрования содержимого трафика внутри relay, только HMAC-аутентификация.
- Relay не модифицирует игровые пакеты.
- Это маршрутизатор, а не «FPS unlocker»: FPS зависит от устройства, температуры и настроек игры.

Для первого теста достаточно одного VPS. Когда найдём лучший регион/провайдера, можно добавить несколько relay и автоматический выбор.
