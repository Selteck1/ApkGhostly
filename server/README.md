# Lineup content publisher — настройка Render

Сервис принимает .lineup пакет на POST /publish, коммитит его в content/packs/community.lineup, после чего workflow пересчитывает каталог и собирает пользовательский APK.

## Render Environment
Открой сервис lineup-content-publisher в Render:
- LINEUP_GITHUB_REPO = Selteck1/ApkGhostly
- LINEUP_GITHUB_BRANCH = main
- LINEUP_ADMIN_KEY = пароль владельца для доступа к издательским endpoint'ам
- LINEUP_GITHUB_TOKEN = Fine-grained GitHub token, ограниченный репозиторием Selteck1/ApkGhostly, с разрешением Contents: Read and write.

Не сохраняй GitHub token в репозитории, логах или APK. Администраторский APK отправляет введённый пароль в заголовке X-Lineup-Admin-Key только для защищённых endpoint'ов.

## Endpoints
- GET /health — проверка настройки.
- POST /publish — принять и проверить ZIP .lineup и сохранить пакет в GitHub.
- POST /policy/lock — поднять минимальную разрешённую ревизию клиентского APK.
- POST /policy/telegram — изменить Telegram HTTPS-ссылку для обновления.
- GET /policy — прочитать опубликованную политику.

## Подпись пользовательского APK
Для постоянных публичных версий добавь GitHub Actions secrets LINEUP_KEYSTORE_BASE64, LINEUP_KEYSTORE_PASSWORD, LINEUP_KEY_ALIAS, LINEUP_KEY_PASSWORD. Пока они не настроены, CI загружает только debug APK artifact, не стабильный release.
