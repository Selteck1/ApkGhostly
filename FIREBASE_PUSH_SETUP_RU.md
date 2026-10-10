# Kemtiz — настройка Firebase Push

## 1. Создай Firebase-проект

1. Открой https://console.firebase.google.com/ и создай проект.
2. Добавь Android-приложение с package name **com.kemtiz.app**.
3. Скачай файл `google-services.json`.

## 2. Добавь клиентскую конфигурацию

Скопируй `google-services.json` в папку `Kemtiz-PC-Server/kemtiz-data/` рядом с `kemtiz.sqlite3`.

## 3. Создай серверный ключ

В Firebase Console открой Project settings → Service accounts → Generate new private key. Сохрани файл с именем:

`Kemtiz-PC-Server/kemtiz-data/firebase-service-account.json`

Не загружай этот приватный файл в GitHub, не отправляй его в чат и не включай в серверный ZIP для друзей. Проверь, что проект использует Firebase Cloud Messaging API (HTTP v1).

## 4. Перезапусти API

Останови `start_kemtiz_server.bat` сочетанием Ctrl+C, затем запусти снова. Не удаляй `kemtiz-data`: там база аккаунтов и чатов. Проверь `https://ТВОЙ-АДРЕС/health`; поле `push_notifications` должно стать `true`.

Открой Kemtiz 3.0 на телефоне, войди и разреши уведомления. Приложение автоматически получит публичные настройки Firebase с сервера и зарегистрирует устройство. Пересобирать APK после создания проекта не требуется.

## 5. Ограничения Android

Push приходят при обычном свёрнутом или закрытом приложении, если разрешены уведомления и фоновые данные. Энергосбережение может задерживать доставку. После «Принудительной остановки» Android может блокировать push до следующего запуска. Значки включены, однако цвет точки на иконке зависит от лаунчера.
