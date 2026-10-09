# Kemtiz Messenger

Kemtiz — alpha-мессенджер для браузера на ПК и мобильных устройств, с Android-оболочкой.

## Авторизация через Google

На экране входа используется официальный Google Identity Services. Пользователь выбирает аккаунт Google в стандартном окне, а Kemtiz получает подтверждённые Google имя, email и аватар. Пароль не запрашивается и не сохраняется. Для первого входа пользователь задаёт только:
- уникальный `@username` (обязателен);
- страну (необязательно);
- короткое описание профиля (необязательно).

ID-токен проверяется на сервере с помощью официальной библиотеки `google-auth`. Для входа и создания аккаунта сервер принимает только токены, выпущенные Google с нужной audience, действующим сроком и подтверждённой почтой.

## Настроить Google OAuth

Открой [Google Cloud Console — Clients](https://console.cloud.google.com/auth/clients), создай/выбери проект и закончи настройку Google Auth Platform (название приложения, email поддержки, consent screen). На этапе тестирования добавь свой Google-адрес в список test users.

Нужны OAuth-клиенты для веба и Android:

1. **Web application client** — это серверный Client ID, который будет использоваться и веб-кнопкой, и Credential Manager внутри Android APK. Скопируй ID вида `123456789-abcdef.apps.googleusercontent.com`; это публичный идентификатор, не пароль.
2. В настройках Web client добавь разрешённые JavaScript origins. Для локального теста на компьютере используй `http://localhost:8000`. Для продакшена добавь фактический HTTPS-origin. Не добавляй путь вроде `/login` — нужен только origin.
3. **Android client** — создай отдельный OAuth client типа Android, package name `com.kemtiz.app`, и SHA-1 сертификата подписи APK. Для APK из раздела Releases/Artifacts вычисли SHA-1 с Android SDK Build Tools командой `apksigner verify --print-certs app-debug.apk`. Возьми строку `Signer #1 certificate SHA-1 digest` без двоеточий и укажи её в Android client. Этот fingerprint зависит от сертификата конкретной сборки, поэтому после пересборки debug APK может потребоваться обновить Android client.

Подробности: [официальная настройка Google Sign-In для веба](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid) и [Google Sign-In для Android](https://developer.android.com/identity/sign-in/credential-manager-siwg?hl=en).


## Запуск в Termux

В Termux сначала останови старый процесс сервера сочетанием `CTRL+C`, затем выполни:

```bash
cd "$HOME/KemtizProject" || exit 1
git pull --ff-only origin kemtiz-messenger || exit 1
cd "$HOME/KemtizProject/kemtiz" || exit 1
source .venv/bin/activate || exit 1
pkg install -y rust binutils
export ANDROID_API_LEVEL="$(getprop ro.build.version.sdk)"
python -m pip install --upgrade pip
pip install -r requirements.txt || exit 1
mkdir -p "$HOME/.config/kemtiz"
touch "$HOME/.config/kemtiz/env.sh"
chmod 600 "$HOME/.config/kemtiz/env.sh"

read -r -p "Вставь Google OAuth Client ID: " GOOGLE_CLIENT_ID
if [ -z "$GOOGLE_CLIENT_ID" ]; then
  echo "Client ID не введён."
  exit 1
fi

grep -v '^export KEMTIZ_GOOGLE_CLIENT_ID=' "$HOME/.config/kemtiz/env.sh" > "$HOME/.config/kemtiz/env.tmp"
printf 'export KEMTIZ_GOOGLE_CLIENT_ID=%q\n' "$GOOGLE_CLIENT_ID" >> "$HOME/.config/kemtiz/env.tmp"
mv "$HOME/.config/kemtiz/env.tmp" "$HOME/.config/kemtiz/env.sh"
chmod 600 "$HOME/.config/kemtiz/env.sh"
source "$HOME/.config/kemtiz/env.sh"

python -m uvicorn server:app --host 127.0.0.1 --port 8000
```

Открой в браузере на том же телефоне: `http://127.0.0.1:8000`. Для ПК нужен сервер, доступный с ПК, либо настроенный HTTPS-туннель/домен с тем же origin, добавленным в Google Cloud.

## Android

Android APK получает отдельную фирменную adaptive launcher icon Kemtiz. При нажатии на кнопку Google в APK открывается системный Credential Manager, который показывает аккаунты устройства и возвращает ID-токен в тот же серверный поток. В обычном браузере используется официальный веб-компонент Google Identity Services. Обе версии используют один и тот же Web OAuth Client ID в качестве server client ID.

## Текущее состояние

- Вход/регистрация Google и завершение профиля.
- Поиск людей по username, заявки в друзья, личные чаты и группы.
- История и отправка текстовых сообщений, read-маркеры, онлайн-статус и typing.
- Кастомная SVG-иконка, Android adaptive icon и адаптивная desktop-first раскладка.

Это альфа-версия: перед публичным запуском дополнительно настрой HTTPS, конфиденциальность, abuse/rate limits, резервное копирование и правила сервиса.
