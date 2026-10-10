# Kemtiz Messenger 2.8.0

Kemtiz — Android-мессенджер с регистрацией по логину и паролю.

## Возможности

- регистрация и вход по username/password;
- поиск пользователей, заявки в друзья, личные и групповые чаты;
- история сообщений и WebSocket-события;
- видеозвонки один-на-один через WebRTC (тестовая реализация);
- Android system notifications for messages/calls while app process and WebSocket connection are alive;
- a chat screen with a fixed composer and an independently scrollable message history;
- local network test via PC IPv4 address and public HTTPS exposure via Tailscale Funnel.

## Ограничения

Это альфа-версия, а не полноценный клон Telegram. Надёжные push-уведомления после принудительного закрытия, сквозное шифрование, отправка файлов, голосовые сообщения и постоянный TURN-релей пока не реализованы. Для Firebase Cloud Messaging понадобятся конфигурация Firebase Android app и серверные credentials.

Для подключения из интернета используй только HTTPS. HTTP локальный IP предназначен для тестирования в доверенной Wi-Fi-сети.
