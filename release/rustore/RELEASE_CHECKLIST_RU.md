# Чек-лист релиза

## 1. Перед сборкой release

- [ ] Выбран уникальный `applicationId` (не `ru.example.*`) — заменить в `app/build.gradle.kts` и `namespace`
- [ ] Выбрано финальное название приложения (без слова «Dev»)
- [ ] `versionCode` увеличен, `versionName` соответствует релизу
- [x] `CAMERA_DB_BASE_URL` = `https://raw.githubusercontent.com/lisuner5945-stack/road-alert/main/database/`
- [x] `PRIVACY_POLICY_URL` = `https://lisuner5945-stack.github.io/road-alert/privacy/` (включить Pages из каталога `docs/`)
- [ ] В `legal/PRIVACY_POLICY_RU.md` и `site/privacy/index.html` заменены все «ЗАПОЛНИТЬ»
- [ ] Реальный e-mail разработчика вместо `developer@example.com`

## 2. Ключ подписи

- [ ] Создан release keystore:
      `keytool -genkeypair -v -keystore road-alert-release.jks -alias road-alert -keyalg RSA -keysize 4096 -validity 10000`
- [ ] `.jks`, alias и пароли сохранены минимум в двух безопасных местах
- [ ] Создан `keystore.properties` по образцу `keystore.properties.example`
- [ ] `git status` не показывает `*.jks`, `keystore.properties`, `local.properties`

## 3. Автоматические проверки

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleRelease
```

Задача `verifyReleaseConfig` выполняется автоматически перед упаковкой release
и роняет сборку, если:

- `applicationId` начинается с `ru.example`
- имя приложения содержит «Dev»
- не настроена release-подпись
- при `ADS_ENABLED=true` остались `demo-*` блоки или production ID не заданы
- версия начинается с `1.0`, но реклама включена
- отсутствует Privacy Policy или OSM attribution
- адрес базы не HTTPS, содержит localhost или placeholder
- в манифесте есть `ACCESS_BACKGROUND_LOCATION` или другие запрещённые разрешения
- `debuggable=true` или включено меню разработчика

## 4. Проверка на реальном устройстве

- [ ] Первый запуск и онбординг понятны
- [ ] Запрос разрешения на геолокацию появляется при «Начать поездку»
- [ ] Скорость отображается, GPS ловит
- [ ] Экран выключен 10+ минут — предупреждения продолжают работать
- [ ] Голос слышен через динамик и через Bluetooth автомобиля
- [ ] Работа при открытом стороннем навигаторе
- [ ] Overlay появляется только после выданного разрешения
- [ ] Bluetooth-сценарий: автозапуск или уведомление в один тап
- [ ] Airplane mode: предупреждения работают, приложение не падает
- [ ] Возврат интернета: база обновляется
- [ ] Расход батареи за час поездки приемлемый
- [ ] Карта камер открывается, знаки читаются, атрибуция OpenStreetMap видна
- [ ] Реальная дорожная поездка: нет ложных срабатываний на встречных камерах

## 5. Материалы для RuStore

- [ ] Иконка 512×512, 1:1, ≤ 1 МБ, фон заполнен
- [ ] Минимум 3 скриншота с реальными экранами (≤ 3 МБ каждый)
- [ ] Название ≤ 30 символов, совпадает с названием установленного приложения
- [ ] Краткое описание ≤ 80 символов
- [ ] Подробное описание ≤ 4000 символов, без обещаний «100% камер»
- [ ] Заполнена декларация разрешений (`PERMISSIONS_JUSTIFICATION_RU.md`)
- [ ] Указан реальный e-mail и ссылка на Privacy Policy

## 6. После публикации (РСЯ)

- [ ] Зарегистрирован Яндекс ID и аккаунт РСЯ
- [ ] Приложение добавлено из RuStore, магазин выбран верно
- [ ] Право на приложение подтверждено (e-mail или `app-ads.txt`)
- [ ] Пройдена модерация, созданы production ad units
- [ ] ID вставлены в `ads.properties` (`adsEnabled=true`), собран monetized release
- [ ] Обновление загружено в RuStore
- [ ] Заполнен платёжный профиль

> Собственные клики и показы по своей рекламе запрещены правилами РСЯ.

---

## Приложение: где что лежит после сборки

Каталог сборки вынесен из кириллического пути (см. README):

```
D:\roadalert-build\app\outputs\apk\debug\app-debug.apk
D:\roadalert-build\app\outputs\apk\release\app-release.apk
D:\roadalert-build\app\outputs\bundle\release\app-release.aab
D:\roadalert-build\app\reports\lint-results-debug.html
D:\roadalert-build\app\reports\tests\testDebugUnitTest\index.html
```
