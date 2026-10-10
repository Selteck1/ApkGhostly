# Lineup — конструктор раскидок Standoff 2

В репозитории два Android-приложения:

- **Lineup Admin** — личный редактор под пароль. Позволяет создавать блоки, прикреплять фотографии, сохранять материалы локально, собирать обновлённый APK для игроков и блокировать старые версии.
- **Lineup** — приложение игроков. В нём только просмотр блоков, описаний и фотографий, встроенных в APK. После онлайн-проверки версии содержимое можно смотреть без интернета в текущем сеансе.

## Как пользоваться
1. Открой Lineup Admin и введи пароль.
2. Добавляй блоки и нажимай «Сохранить». Они сохраняются на этом устройстве.
3. Нажми «Собрать новое APK для игроков». Приложение публикует пакет, затем GitHub Actions собирает пользовательский APK с блоками внутри.
4. Дождись завершения workflow Build Lineup user APK.
5. После появления новой сборки нажми «Заблокировать прошлые версии». Старые приложения после онлайн-проверки покажут экран блокировки и ссылку Telegram.
6. Кнопка «Изменить ссылку Telegram» меняет канал, куда отправляется игрок при блокировке.

## Обязательная первоначальная настройка публикации

### 1. GitHub-токен для сервера
Открой GitHub Settings → Developer settings → Personal access tokens → Fine-grained tokens. Создай токен только для репозитория Selteck1/ApkGhostly с разрешением **Contents: Read and write**.

Открой [lineup-content-publisher в Render](https://dashboard.render.com/web/srv-db52m6flk1mc738h7j6g) → Environment и добавь:
- LINEUP_GITHUB_TOKEN = твой Fine-grained token.

LINEUP_ADMIN_KEY уже настроен под пароль администратора. Не публикуй значения секретов и не помещай токен в исходный код. После сохранения дождись, когда сервис станет Live.

Проверка сервера: https://lineup-content-publisher.onrender.com/health. Поле githubConfigured должно быть true. На бесплатном плане Render сервис может просыпаться после простоя.

### 2. Ключ подписи пользовательского APK
Чтобы новые APK устанавливались поверх предыдущих, все версии должны иметь одну и ту же подпись. Без постоянного ключа workflow создаёт только debug APK для тестов, не публикуя его как постоянное обновление.

Создай ключ один раз на компьютере с JDK:
~~~sh
keytool -genkeypair -v -keystore lineup-release.jks -alias lineup -keyalg RSA -keysize 2048 -validity 10000
~~~
Сохрани lineup-release.jks и пароли в безопасном месте. Не загружай сам файл ключа в публичный репозиторий.

В GitHub открой Settings → Secrets and variables → Actions и добавь:
- LINEUP_KEYSTORE_BASE64 — Base64-содержимое файла keystore;
- LINEUP_KEYSTORE_PASSWORD;
- LINEUP_KEY_ALIAS — обычно lineup;
- LINEUP_KEY_PASSWORD.

Windows PowerShell для получения Base64:
~~~powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("lineup-release.jks"))
~~~
После добавления секретов очередная сборка создаст подписанный APK, опубликует его в Releases как Lineup-Client.apk и сможет устанавливаться поверх предыдущего клиентского APK.

## Ссылки
- [Сборки Admin и тестового Client](https://github.com/Selteck1/ApkGhostly/actions/workflows/android.yml)
- [Автоматическая сборка пользовательского APK](https://github.com/Selteck1/ApkGhostly/actions/workflows/client-release.yml)
- [Последний подписанный APK игрока](https://github.com/Selteck1/ApkGhostly/releases/latest/download/Lineup-Client.apk)

## Важное ограничение
Удалённая блокировка старой версии требует проверки через интернет. Без сети старое приложение не может узнать о новой политике. Поэтому пользовательский APK требует интернет для проверки версии при запуске; после успешной проверки блоки и изображения уже находятся внутри APK и доступны локально в этом сеансе.
