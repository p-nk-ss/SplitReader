# SplitReader — Test Plan

> Живой документ. Цель — поднять покрытие с ~2% до защиты критичных путей перед релизом и
> держать регрессии под контролем в дальнейшей разработке.

## Текущее состояние (baseline)

Unit (`app/src/test`, JUnit4 + kotlinx-coroutines-test, **ручные фейки, без mock-фреймворков**):
- `HtmlChapterExtractorTest` — очистка boilerplate, Project Gutenberg
- `SynopsisExtractorTest` — нормализация/клэмп текста
- `TranslationPlannerTest` — оконное планирование перевода
- `ChapterTranslationManagerRetentionTest` — удержание перевода при прыжке вперёд
- ✅ **`AddBookToLibraryUseCaseTest`** — free-tier чокпоинт (добавлен; 10 тестов)

Instrumented (`app/src/androidTest`):
- `ParserBeginningTest` — FB2/MOBI/EPUB сохраняют начало текста (на реальных фикстурах)

**Итого: ~40 unit + instrumented тестов, 0 падений.** Покрыты парсеры/утилиты/планировщик и
теперь лимит библиотеки. ViewModels, репозитории и UI — не покрыты.

## Конвенции

- **Стиль:** ручные фейки, реализующие доменные интерфейсы (см. `AddBookToLibraryUseCaseTest`).
  Не вводим Mockito/MockK без необходимости — фейки явнее и быстрее.
- **Корутины:** `runTest { }` из `kotlinx-coroutines-test` (теперь в `testImplementation`).
- **Именование:** бэктик-имена с описанием поведения (``fun `free user at the limit cannot add`()``).
- **Где живёт тест:** чистая JVM-логика → `src/test`. Нужен Android-фреймворк (SharedPreferences,
  Room, ML Kit) → `src/androidTest`, **или** добавить **Robolectric** (см. ниже) и держать в `src/test`.

### Рекомендация по инструментам
Для ViewModel/SharedPreferences/Room-тестов без эмулятора стоит добавить **Robolectric** —
тогда они гоняются как быстрые JVM-тесты в `src/test`. Альтернатива (без новых зависимостей) —
писать их в `src/androidTest` (медленнее, нужен девайс). Решение принять до P1.

---

## Бэклог (приоритет = риск × лёгкость)

### P0 — критичная бизнес-логика (быстрые, перед релизом)

| # | Цель | Что проверяем | Где | Статус |
|---|------|---------------|-----|--------|
| 1 | `AddBookToLibraryUseCase` | premium-bypass, re-open при лимите, count < limit | `src/test` | ✅ Готово |
| 2 | `EntitlementRepositoryImpl` | persist флага через SharedPreferences, дефолт `false`, эмиссия Flow при `setPremium` | Robolectric/`androidTest` | ⬜ |
| 2b | ✅ `ApiKeyManager` (Keystore) | encrypt round-trip, выживание после рестарта, на диске не плейнтекст, очистка blank/null, диспетч по провайдеру | `androidTest` | ✅ Готово |
| 3 | `TranslateTextUseCase` + кэш | cache-hit не зовёт ML Kit; cache-miss пишет в кэш; ключ по хэшу текста | `src/test` (фейк DAO/repo) | ⬜ |
| 4 | Маппинг ответов переводчиков | DeepL/Azure/GoogleCloud/Libre DTO → доменная строка; пустой/`null` ответ; **(страхует R8 Gson-путь)** | `src/test` | ⬜ |
| 5 | `Language` / детектор | маппинг кодов ML Kit ↔ `Language` enum; неизвестный код | `src/test` (чистая часть) | ⬜ |

### P1 — ViewModels (логика состояния, фейки)

| # | Цель | Что проверяем | Статус |
|---|------|---------------|--------|
| 6 | `HomeViewModel.openBook` | при лимите → событие лимит-диалога, не открывает; `file://` каталог пропускает URI-permission | ⬜ |
| 7 | `CatalogViewModel` | search → loading/result/**error** состояния; download → ParseBook → saveBook; обработка лимита | ⬜ |
| 8 | `ReaderViewModel` | загрузка главы, интеграция планировщика перевода, выбор слова, восстановление прогресса | ⬜ |
| 9 | `AuthViewModel` | валидация формы, маппинг ошибок Firebase в user-facing, состояние верификации email | ⬜ |
| 10 | `SettingsViewModel` | чтение/запись глобальных prefs, синхронизация с ридером | ⬜ |

### P2 — репозитории (in-memory Room / фейки)

| # | Цель | Что проверяем | Статус |
|---|------|---------------|--------|
| 11 | `TranslationRepositoryImpl` | cache hit/miss, `clearAll` | ⬜ |
| 12 | `BookLibraryRepositoryImpl` | saveBook (upsert), `bookCount`, `exists`, `deleteBook` на in-memory Room | ⬜ |
| 13 | `BookmarkRepositoryImpl` / `SavedWordRepositoryImpl` / `NoteRepositoryImpl` | CRUD + Flow-эмиссии | ⬜ |
| 14 | `ReadingProgressManager` | persist глава+скролл per book | ⬜ |

### P3 — instrumented / UI

| # | Цель | Что проверяем | Статус |
|---|------|---------------|--------|
| 15 | Парсеры (расширение `ParserBeginningTest`) | EPUB иллюстрации, FB2 base64, MOBI ошибки HUFF/CDIC/AZW3/DRM | ⬜ |
| 16 | Compose UI smoke | Home (пустая/полная), Reader split-pane, Catalog error+Retry рендерятся | ⬜ |
| 17 | E2E перевод | открыть книгу → ML Kit перевод появляется в правой панели | ⬜ |
| 18 | **Онлайн-переводчики на реальной сети** | закрывает дыру из smoke-теста R8 (эмулятор не дал IPv6) | ⬜ |

---

## Связь с релизом

- **P0** — реализовать до публикации (защищает монетизацию и R8-Gson-путь).
- **#18** — обязательный ручной/инструментальный прогон до релиза: на эмуляторе онлайн-переводчики
  не проверены (IPv6). Либо реальный девайс, либо инструментальный тест с сетью.
- **P1–P3** — наращивать в ходе разработки; каждый новый ViewModel/репозиторий идёт с тестом.
