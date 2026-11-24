# Contributing

Спасибо за интерес к проекту. Это небольшое офлайн-приложение для личного дневника тренировок, поэтому главные приоритеты — простота, сохранность данных и предсказуемый UI.

## Что можно предлагать

- Исправления багов и регрессий.
- Улучшения доступности, вёрстки и работы с длинными текстами.
- Тесты и улучшение тестовой инфраструктуры.
- Небольшие улучшения из раздела «Future backlog» в `PLAN.md`.

Перед крупной фичей лучше открыть issue и обсудить её. Не расширяйте scope без согласования: `PLAN.md` — источник истины по продукту.

## Ветки

- `main` — основная ветка разработки. Все обычные PR идут сюда.
- `release` — долгоживущая релизная ветка. Она защищена ruleset: прямой push, force-push и удаление запрещены.
- Теги `vX.Y.Z` ставятся на коммиты `release`; GitHub Release содержит подписанные APK/AAB.
- **Не открывайте PR в `release`.** Внешние контрибьюторы работают только с `main`. Релизный PR `main → release` делает мейнтейнер.

## Как внести изменение

1. Сделайте fork репозитория на GitHub.
2. Склонируйте свой fork и создайте ветку от `main`:
   ```bash
   git clone https://github.com/<ваш-логин>/training-log-app.git
   cd training-log-app
   git switch -c feature/korotkoe-nazvanie
   ```
3. Внесите изменение. Держите PR сфокусированным: одна логическая правка — один PR.
4. Прочитайте `PLAN.md`, `DESIGN.md` и `AGENTS.md`. Для UI следуйте `DESIGN.md`; для данных и тестов — `AGENTS.md`.
5. Запустите проверки (см. ниже).
6. Закоммитьте и запушьте ветку в свой fork:
   ```bash
   git push -u origin feature/korotkoe-nazvanie
   ```
7. Откройте PR из `<ваш-логин>:feature/...` в `paincake00:main`. Не меняйте base на `release`.

Если ваш fork отстал от upstream, обновите `main` перед PR:

```bash
git remote add upstream https://github.com/paincake00/training-log-app.git
git fetch upstream
git switch main
git merge --ff-only upstream/main
git switch feature/korotkoe-nazvanie
git rebase main
```

## Локальная сборка

Нужны JDK 17+ и Android SDK с platform 36. JDK из Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

`local.properties` создаётся локально и не коммитится. Если его нет:

```properties
sdk.dir=/Users/<user>/Library/Android/sdk
```

Проверки перед PR:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Обе команды должны быть зелёными. Для изменений только в документации достаточно проверить diff.

## Правила кода

- Kotlin, Jetpack Compose, Material 3, Room, MVVM + Repository. Без Hilt и без сетевых библиотек.
- Бизнес-логика — вне Composables. UI отправляет события, ViewModel вызывает repository.
- Все пользовательские строки — в `res/values/strings.xml`, на русском.
- Даты — `java.time.LocalDate`; в Room хранится `epochDay`.
- Прогрессия — свободный текст. Не парсить, не нормализовать и не форматировать её.
- Не добавляйте `INTERNET` и сетевые зависимости.
- Не меняйте схему Room без миграции, теста миграции и обновлённой схемы в `app/schemas`.
- Не оставляйте `@Ignore`, отключённые тесты, мёртвый код и `TODO` без причины.
- Соблюдайте стиль окружающего кода; не переформатируйте чужие файлы без необходимости.

## Коммиты

Используйте Conventional Commits:

```text
feat: add ...
fix: ...
refactor: ...
test: ...
docs: ...
chore: ...
```

Сообщение должно объяснять, что изменено и зачем. Не используйте `update`, `changes`, `fix stuff`.

В коммит входят код, тесты, схема Room и документация, если поведение изменилось.

## Секреты и данные

- Не коммитьте `.env`, `local.properties`, keystore, пароли, `dist/`, APK/AAB, логи и `build/`.
- Не вставляйте токены в URL remote, `.git/config`, код, issue или PR.
- Не добавляйте в PR реальные пользовательские данные и скриншоты с личной информацией.
- Уязвимости и утечки не публикуйте в issue — напишите мейнтейнеру приватно.

## Pull request

В описании PR укажите:

- что изменено и зачем;
- какие сценарии проверены;
- какие команды запускались и их результат;
- есть ли изменение схемы/миграция;
- скриншоты для UI-изменений (без личных данных).

PR должен быть небольшим. Если изменения затрагивают данные, UI и тесты, лучше разделить на несколько PR, если это возможно.

## Релизы

Релизы делает мейнтейнер. Внешние контрибьюторы не получают доступ к `release` и не подписывают APK. Процесс описан в `AGENTS.md` §12.

## Лицензия

Отдельная лицензия пока не добавлена. Если вы планируете использовать код вне этого репозитория, сначала свяжитесь с автором.
