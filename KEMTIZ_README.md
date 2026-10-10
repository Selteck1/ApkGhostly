# Kemtiz Messenger 3.0.0

Kemtiz — Android-мессенджер с регистрацией по логину и паролю.

## Возможности

- регистрация и вход по username/password;
- поиск пользователей, заявки в друзья, личные и групповые чаты;
- история сообщений и WebSocket-события;
- видеозвонки один-на-один через WebRTC (тестовая реализация);
- Android system notifications for messages/calls while app process and WebSocket connection are alive;
- a chat screen with a fixed composer and safe system-bar insets;
- a portrait full-screen video-call UI with floating self-preview and custom circular controls;
- explicit WebRTC audio routing, echo/noise suppression and capped call volume;
- Firebase Cloud Messaging integration for background message and incoming-call pushes (one-time server setup required);
- vibration, badge-enabled notification channels and push deep links into chats;
- persistent Tailscale URL by default and a collapsed advanced server panel;
- local network test via PC IPv4 address and public HTTPS exposure via Tailscale Funnel.

## Ограничения

Это ранняя версия, а не полноценный клон Telegram. Для push один раз положи google-services.json и firebase-service-account.json в kemtiz-data на сервере. Сквозное шифрование, отправка файлов, голосовые сообщения и постоянный TURN-релей пока не реализованы.

Для подключения из интернета используй только HTTPS. HTTP локальный IP предназначен для тестирования в доверенной Wi-Fi-сети.
