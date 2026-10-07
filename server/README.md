# Ghostly API

Минимальный backend для тестового APK.

## Termux

Из корня репозитория:

```bash
bash server/start_server.sh
```

Проверка:

```bash
curl http://127.0.0.1:8081/api/health
```

Ожидаемый ответ:

```json
{"ok":true,"service":"ghostly-api","version":"0.1.0",...}
```

Сейчас backend — это только health-check. Следующий этап можно строить поверх этой точки: авторизация, пользователи, анкеты, поиск, Premium, сообщения и админка.
