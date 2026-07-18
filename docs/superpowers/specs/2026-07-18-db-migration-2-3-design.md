# Спека: безопасная миграция БД v2→v3 (Фаза 0, P1–P3)

> Дата: 2026-07-18 · Ветка: `refactor/audit-fixes` · Приоритет: **High** (потеря данных)
> Источник задач: `docs/refactor_plan.md` (P1 дыра миграции, P2 exportSchema, P3 тесты).
> Статус решений: тест-стратегия **C (гибрид)**, деструктивный fallback **debug-only** — согласовано.

## 1. Проблема

`AppDatabase` на `version = 4`, но зарегистрированы только `MIGRATION_1_2` и
`MIGRATION_3_4` — **`MIGRATION_2_3` отсутствует**. В связке с
`fallbackToDestructiveMigration()` любой апгрейд с реальной схемы v2 (где ещё нет таблиц
`bookmarks`, `notes`, `saved_words`, `reading_sessions`) приводит к **полному удалению
базы** без предупреждения.

### Восстановленная из git история версий (авторитетно)

| Версия | Коммит | Схема | Миграции в коде на тот момент |
|--------|--------|-------|-------------------------------|
| v2 | `58caba5` (initial) | `translation_cache`, `books` (без `synopsis`) | `MIGRATION_1_2` |
| v3 | `db1818d` (редизайн, Almanac/Vocabulaire) | v2 + `bookmarks`, `notes`, `saved_words`, `reading_sessions` | **всё ещё только `MIGRATION_1_2`** ← источник бага |
| v4 | `b90c8d6` | v3 + `books.synopsis` | `MIGRATION_1_2`, `MIGRATION_3_4` |

Проверено: 4 сущности v3 (`Bookmark/Note/SavedWord/ReadingSession`) **идентичны** от v3 до
HEAD; `BookEntity` отличается только добавленным в v4 `synopsis`. Значит `MIGRATION_3_4` эти
4 таблицы не трогает → их финальную (v4) форму обязана создать именно `MIGRATION_2_3`, и
брать её SQL из текущих сущностей корректно.

## 2. Официальная опора (Room 2.6/2.7)

- Экспорт схем — **Room Gradle Plugin**: `plugins { id("androidx.room") }` +
  `room { schemaDirectory("$projectDir/schemas") }` + `exportSchema = true` на классе БД.
- `MigrationTestHelper` читает `N.json` из ассетов androidTest
  (`androidTest.assets.srcDir("$projectDir/schemas")`), создаёт БД версии N, `runMigrationsAndValidate`
  проверяет конечную схему.
- `fallbackToDestructiveMigration()` без аргументов в 2.7 помечен deprecated → явная
  `fallbackToDestructiveMigration(dropAllTables = true)`; точную сигнатуру финализируем по линтеру.

Источник: [Android · Migrate your Room database](https://developer.android.com/training/data-storage/room/migrating-db-versions).

## 3. Дизайн решения

### 3.1. Включение экспорта схем (build)
- `gradle/libs.versions.toml`: в `[plugins]` добавить `room = { id = "androidx.room", version.ref = "room" }`.
- Корневой `build.gradle.kts`: `alias(libs.plugins.room) apply false`.
- `app/build.gradle.kts`: применить `alias(libs.plugins.room)`; добавить
  `room { schemaDirectory("$projectDir/schemas") }`; в `android { sourceSets { getByName("androidTest").assets.srcDir("$projectDir/schemas") } }`;
  зависимость `androidTestImplementation(libs.room.testing)` (2.7.0, добавить алиас в каталог).
- `AppDatabase.kt`: `exportSchema = true`.
- Собрать один раз → появляется `app/schemas/com.example.splitreader.data.local.AppDatabase/4.json`.
  **Закоммитить** `app/schemas/**`.

### 3.2. `MIGRATION_2_3`
- Взять точные `CREATE TABLE`/`CREATE INDEX` для `bookmarks`, `notes`, `saved_words`,
  `reading_sessions` **из сгенерированного `4.json`** (`createSql` каждой таблицы), выверить
  байт-в-байт (столбцы, `NOT NULL`, дефолты, FK, имена индексов).
- Оформить `private val MIGRATION_2_3 = object : Migration(2, 3) { … }`, использовать
  `CREATE TABLE IF NOT EXISTS` (защитно).
- Черновой SQL — в `docs/refactor_plan.md` §3.A; финал сверить с `4.json`.
- Зарегистрировать: `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`.

### 3.3. Политика fallback (debug-only)
- В `provideDatabase` строить билдер условно:
  ```kotlin
  Room.databaseBuilder(context, AppDatabase::class.java, "splitreader.db")
      .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
      .apply { if (BuildConfig.DEBUG) fallbackToDestructiveMigration(dropAllTables = true) }
      .build()
  ```
- Релиз: деструктивного fallback нет → отсутствующая/битая миграция падает громко
  (`IllegalStateException`) в тестах/дев-сборках, а не стирает данные пользователю.
- (Опц.) `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)` можно оставить в
  обоих конфигурациях — безопасно при откате версии в разработке.
- Импорт `com.example.splitreader.BuildConfig` в `DatabaseModule`.

### 3.4. Тестирование — гибрид C

**Основной (обязательный) — тест сохранности данных v2→v4** (`androidTest`, инструментальный):
1. Вручную создать файл БД: `PRAGMA user_version = 2` + точные v2-таблицы
   (`books` — из существующего `MIGRATION_1_2`, т.е. без `synopsis`; `translation_cache` —
   `createSql` из `4.json`, таблица неизменна с v1).
2. Залить тестовые строки: одна книга (`books`), одна запись кэша (`translation_cache`).
3. Закрыть; открыть настоящий `AppDatabase` (Room, все миграции) на том же файле → Room
   прогоняет 2→3→4.
4. Ассерты: строка книги и запись кэша **сохранились**; новые таблицы рабочие — вставка
   `bookmark` c FK на существующую книгу проходит; открытие не бросает исключений (значит
   конечная схема v4 валидна — Room валидирует её сам при открытии).

**Бонус (если исторические JSON достаются дёшево)** — регенерировать `2.json`/`3.json` из
git-worktree коммитов `58caba5` (v2) и `db1818d` (v3) с временно включённым экспортом,
положить в `app/schemas/**`, добавить `MigrationTestHelper`-тесты `migrate2To3`,
`migrate3To4`, `migrateAll` с диф-валидацией схемы. Не блокирует фазу.

## 4. Затрагиваемые файлы

- `gradle/libs.versions.toml` — плагин `room`, алиас `room-testing`.
- `build.gradle.kts` (root) — `alias(libs.plugins.room) apply false`.
- `app/build.gradle.kts` — применить плагин, `room{}`, androidTest assets, тест-зависимость.
- `app/src/main/java/.../data/local/AppDatabase.kt` — `exportSchema = true`.
- `app/src/main/java/.../di/DatabaseModule.kt` — `MIGRATION_2_3`, регистрация, debug-only fallback.
- `app/schemas/**` — новый, сгенерированный (+ опц. восстановленные `2.json`/`3.json`).
- `app/src/androidTest/java/.../data/local/MigrationTest.kt` — новый тест(ы).

## 5. Критерии приёмки

1. Сборка генерирует схему; `4.json` закоммичен в `app/schemas/**`.
2. `MIGRATION_2_3` зарегистрирована в порядке `1_2, 2_3, 3_4`; деструктивный fallback только в debug.
3. Инструментальный тест сохранности v2→v4 зелёный: данные книги/кэша выжили, `bookmark`
   с FK вставляется, открытие без исключений.
4. Существующие тесты (`AddBookToLibraryUseCaseTest`, `ApiKeyManagerTest`, парсеры и др.) не сломаны.

## 6. Риск и охват

- Изменение затрагивает только оставшихся v2-пользователей; пути v3→v4 и v4 неизменны.
- `CREATE TABLE IF NOT EXISTS` — защита от повторного применения.
- Инструментальные тесты идут на эмуляторе штатно (SQLite работает, в отличие от ML Kit).
- Откат: изменения аддитивны и изолированы в БД-слое; при проблеме — revert PR фазы 0.
