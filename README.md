# Road Alert — GPS-предупреждения о камерах

Android-приложение, которое заранее предупреждает водителя о камерах контроля
скорости по GPS и локальной базе координат. Это **не** радиоэлектронный
радар-детектор: приложение не принимает радарные сигналы и не вмешивается
в работу камер.

Реализовано по `ANTIRADAR_TECH_SPEC_CLAUDE_CODE_V2.md`.

## Быстрый старт

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

APK: `D:/roadalert-build/app/outputs/apk/debug/app-debug.apk`

Требуется JDK 17+ и Android SDK (platform 36, build-tools 36). Путь к SDK —
в `local.properties` (файл не коммитится):

```properties
sdk.dir=D:/Android/AndroidSdk
```

> **Путь проекта содержит кириллицу** (…\Рабочий стол\Антирадар).
> Windows-инструменты и форкнутые JVM Gradle передают такие пути в системной
> кодировке и ломаются (тестовый worker не находит классы, aapt2 не открывает
> файлы). Поэтому включены две настройки: `android.overridePathCheck=true`
> в `gradle.properties` и автоматический перенос каталога сборки в ASCII-путь
> `D:/roadalert-build` (см. `settings.gradle.kts`). Исходники остаются на месте;
> для проектов с ASCII-путём поведение не меняется.

## Что уже работает

| Возможность | Где искать |
|---|---|
| Экраны Compose (Home / Drive / HUD / Settings / About / Onboarding) | `ui/` |
| Локальная база камер, Room + R-tree | `data/database/`, `data/camera/` |
| Определение камеры впереди, фильтр по направлению | `detection/` |
| Машина состояний предупреждений | `alerts/AlertStateMachine.kt` |
| Голос (TextToSpeech, русские фразы) | `alerts/VoiceAlertManager.kt` |
| GPS foreground service, работа при выключенном экране | `drive/`, `location/` |
| Overlay поверх навигатора | `overlay/OverlayController.kt` |
| Автозапуск при подключении к автомобилю | `bluetooth/` |
| Безопасное обновление базы + WorkManager | `data/updates/`, `work/` |
| Yandex Mobile Ads (demo-блоки, запрет fullscreen в поездке) | `ads/` |
| Сборка базы из OpenStreetMap | `tools/osm/`, `.github/workflows/update-cameras.yml` |
| Проверки перед релизом | `gradle/release-checks.gradle.kts` |

## Приватность по умолчанию

- GPS включается только на время поездки, запущенной пользователем.
- История поездок никуда не отправляется, серверной части у версии 1.0 нет.
- Точные координаты не передаются рекламному SDK (`setLocationTracking(false)`).
- `ACCESS_BACKGROUND_LOCATION` не запрашивается.
- Полноэкранная реклама во время активной поездки заблокирована на уровне
  бизнес-логики (`AdEligibility` + `DriveStateHolder.isTripActive`).

## База камер

Источник — OpenStreetMap (© OpenStreetMap contributors, ODbL).
GitHub Actions раз в сутки собирает `camera_database.json.gz`, `metadata.json`
и `SHA256SUMS`; приложение скачивает обновление, проверяет SHA-256, разбирает
и импортирует его в одной транзакции. Битое обновление не может испортить
рабочую базу.

Локальная сборка базы без сети:

```bash
cd tools/osm
python build_database.py --output out --sample
python -m unittest discover -p "test_*.py"
```

## Что должен сделать владелец руками

Кратко (полный список — `release/rustore/RELEASE_CHECKLIST_RU.md`):

1. Выбрать уникальный package name и финальное название.
2. Создать release keystore и сохранить его в двух безопасных местах.
3. Заполнить `keystore.properties` (по образцу `.example`).
4. Заменить `developer@example.com` и адреса-заглушки на реальные.
5. Зарегистрироваться в RuStore, загрузить сборку, заполнить декларацию разрешений.
6. После публикации — РСЯ: подтверждение приложения, production ad units,
   `ads.properties`, платёжный профиль.
7. Проверить приложение в реальной поездке: симулятор её не заменяет.

## Документы

- `legal/PRIVACY_POLICY_RU.md`, `docs/privacy/index.html` (публикуется через GitHub Pages)
- `legal/OSM_ATTRIBUTION.md`
- `release/rustore/PERMISSIONS_JUSTIFICATION_RU.md`
- `release/rustore/STORE_LISTING_RU.md`
- `release/rustore/RELEASE_CHECKLIST_RU.md`

## Статус проверок

| Проверка | Команда | Результат |
|---|---|---|
| Сборка debug | `./gradlew assembleDebug` | APK 18 МБ |
| Unit-тесты | `./gradlew testDebugUnitTest` | 91 тест, 0 падений |
| Lint | `./gradlew lintDebug` | 0 ошибок, 0 предупреждений |
| Тесты pipeline | `python -m unittest discover -s tools/osm -p "test_*.py"` | 28 тестов |
| demo-блоки только в debug | `python tools/ci/check_demo_ads.py` | OK |
| Защита релиза | `./gradlew assembleRelease` | блокируется до выбора package name, ключа и адреса базы |

## Чего в 1.0 сознательно нет

- участки средней скорости считаются только архитектурно (`FeatureFlags.AVERAGE_SPEED_SECTIONS_ENABLED = false`);
- пользовательские сообщения о камерах выключены (`FeatureFlags.USER_CAMERA_REPORTS_ENABLED = false`) — это Phase 2 с backend и модерацией;
- нет карт, маршрутов, аккаунтов, подписки и своего сервера.
