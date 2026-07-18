# Спека: Фаза 3 · под-проект A — доменные модели и мапперы (P19)

> Дата: 2026-07-18 · Ветка: `refactor/phase3a-domain-models` · Источник: `docs/refactor_plan.md` (P19).
> Фаза 3 (архитектурная гигиена) декомпозирована на два под-проекта: **A — утечка Room-моделей
> (P19, этот док)** → B — утечка `data.local`-менеджеров в presentation (P20).
> Согласованные решения: **полное разделение + мапперы**; проекции DAO переименовать в `*Row`;
> круговой mapper-тест на агрегат; делать **агрегат за агрегатом** (каждая задача компилируется и зелёная).

## 1. Цель

Убрать инверсию зависимости Clean Architecture: сейчас доменные репо-интерфейсы и use-case'ы
импортируют data-типы (Room `@Entity` и projection-POJO). Стрелка зависимости смотрит domain → data
(наоборот от правил): персистентность нельзя менять, не трогая domain; Room-сущности протекают как
доменные модели в presentation. Вводим доменные модели + мапперы; репо-интерфейсы объявляем в терминах
доменных типов; RepositoryImpl мапят на границе. Поведение приложения не меняется (чистый рефактор).

## 2. Область (что течёт сегодня)

Доменные репо-интерфейсы, отдающие/принимающие data-типы:
- `BookLibraryRepository.getAllBooks(): Flow<List<BookEntity>>` (асимметрия: `saveBook(book: Book)`
  уже доменный, но чтение течёт `BookEntity`).
- `BookmarkRepository.observeForBook(): Flow<List<BookmarkEntity>>`.
- `NoteRepository`: `observeForBook(): Flow<List<NoteEntity>>`, `upsert(NoteEntity)`, `delete(NoteEntity)`.
- `SavedWordRepository`: все методы на `SavedWordEntity` (`observeAll/observeByLang/search`,
  `findByWordAndLang`, `save(): Long`, `update`, `delete`).
- `ReadingSessionRepository`: `record(ReadingSessionEntity)`, `observeDailyMinutes/observeWeeklyMinutes`
  → `Flow<List<DailyMinutes>>`, `observeTimeByBook` → `BookMinutes`, `observeTimeByLang` → `LangMinutes`.

Use-case'ы, импортирующие data-типы (в области 3A — только Room-модели):
- `AddNoteUseCase` строит `NoteEntity`.
- `EndReadingSessionUseCase` строит `ReadingSessionEntity`.
- `SaveWordUseCase` строит `SavedWordEntity`.

> `TranslateTextUseCase` импортирует `data.local.ReadingProgressManager` — это **capability-менеджер**,
> а не Room-модель. Он **вне 3A** и снимается в под-проекте B (P20). См. §6.

Presentation-потребители data-моделей (меняются вместе с их агрегатом):
- `HomeViewModel` (getAllBooks; плюс session-статистика).
- `ReaderViewModel` (`BookmarkEntity`).
- `WordsViewModel`, `WordsScreen` (`SavedWordEntity`).
- `AlmanacViewModel`, `AlmanacScreen` (`DailyMinutes/BookMinutes/LangMinutes`).

## 3. Дизайн

### 3.1. Доменные модели (`domain/model/`)
1:1 по полям с соответствующими entity, но **без Room-аннотаций** и без импорта `androidx.room`:
- `LibraryBook(uri: String, title: String, author: String, coverPath: String?, lastOpenedAt: Long,
  chapterCount: Int, synopsis: String?)` ← `BookEntity`.
  > Отдельно от существующего парсерного `domain/model/Book` (title/author/chapters/filePath/…): `Book` —
  > результат парсинга, `LibraryBook` — строка библиотеки. Не объединять.
- `Bookmark(id: Long, bookUri: String, chapterIndex: Int, paragraphIndex: Int, label: String?,
  createdAt: Long)` ← `BookmarkEntity`.
- `Note(bookUri: String, chapterIndex: Int, paragraphIndex: Int, body: String, isHighlight: Boolean,
  createdAt: Long, updatedAt: Long)` ← `NoteEntity`.
- `SavedWord(id: Long, word: String, partOfSpeech: String?, sourceLang: String, targetLang: String,
  translation: String, bookUri: String?, bookTitle: String, chapterIndex: Int, paragraphIndex: Int,
  contextSnippet: String, note: String?, savedAt: Long)` ← `SavedWordEntity`.
- `ReadingSession(id: Long, bookUri: String?, bookTitle: String, sourceLang: String, startedAt: Long,
  endedAt: Long, durationSeconds: Int, paragraphsRead: Int)` ← `ReadingSessionEntity`.
- Статистика: `DailyMinutes(day: String, minutes: Int)`, `BookMinutes(title: String, minutes: Int)`,
  `LangMinutes(lang: String, minutes: Int)` (доменные).

> Значения-по-умолчанию из entity (`id = 0`, `createdAt = System.currentTimeMillis()`) на доменной
> модели **не дублируем**: доменная модель описывает данные, а генерацию id/времени выполняет data-слой
> при маппинге в entity (см. 3.3). Исключение — где use-case сам задаёт время: оно передаётся явным полем.

### 3.2. Переименование projection-строк DAO
Чтобы доменные `DailyMinutes/BookMinutes/LangMinutes` не конфликтовали по простому имени с data-версиями,
projection-POJO в `data/local/ReadingSessionDao.kt` переименовать в `DailyMinutesRow`, `BookMinutesRow`,
`LangMinutesRow` (обновить `@Query`-возвраты и DAO-сигнатуры). Room биндит по именам столбцов —
переименование класса безопасно.

### 3.3. Мапперы (data-слой)
Extension-функции в data (напр. `data/repository/mapper/EntityMappers.kt` или по одному файлу на агрегат):
- Чтение: `BookEntity.toDomain(): LibraryBook`, `BookmarkEntity.toDomain(): Bookmark`,
  `NoteEntity.toDomain(): Note`, `SavedWordEntity.toDomain(): SavedWord`,
  `ReadingSessionEntity.toDomain(): ReadingSession`, `*Row.toDomain(): DailyMinutes/BookMinutes/LangMinutes`.
- Запись: `Note.toEntity(): NoteEntity`, `SavedWord.toEntity(): SavedWordEntity`,
  `ReadingSession.toEntity(): ReadingSessionEntity` (для методов, принимающих домен на запись).
  При маппинге домен→entity data-слой подставляет технические дефолты (`id = 0` для autoGenerate там,
  где id ещё не присвоен) — точные правила id/времени зафиксировать в плане по фактическому коду каждого
  use-case, поведение сохранить без изменений.
Мапперы чистые (1:1 копирование полей), JVM-тестируемые.

### 3.4. Репо-интерфейсы и RepositoryImpl
- Интерфейсы (domain) переводятся на доменные типы (см. §2, все `*Entity`/`*Minutes` → доменные).
- RepositoryImpl мапят: DAO возвращает entity/`*Row` → `.toDomain()`; на запись домен → `.toEntity()`.
  DAO остаётся на entity/`*Row` (Room-биндинг живёт в data).
- `SavedWordRepository.save(word: SavedWord): Long` — id остаётся `Long` (это и доменное поле `SavedWord.id`).

### 3.5. Потребители
Presentation/use-case переключаются на доменные типы. Поля называются так же, как в entity, поэтому
правки в основном — смена импорта/типа, без изменения логики построения UI/состояния.

## 4. Декомпозиция (5 задач — агрегат за агрегатом)

Каждая задача самодостаточна: модель + маппер(ы) + смена интерфейса + RepositoryImpl + потребители +
mapper-тест; заканчивается компиляцией и зелёными тестами; domain перестаёт импортировать data-модель
этого агрегата.

1. **LibraryBook** — `BookLibraryRepository.getAllBooks(): Flow<List<LibraryBook>>`; `BookLibraryRepositoryImpl`; `HomeViewModel`.
2. **Bookmark** — `BookmarkRepository.observeForBook(): Flow<List<Bookmark>>`; impl; `ReaderViewModel`.
3. **Note** — `NoteRepository` (observe/upsert/delete на `Note`); impl; `AddNoteUseCase` (+ reader-потребитель, если наблюдает `NoteEntity`).
4. **SavedWord** — `SavedWordRepository` (все методы на `SavedWord`); impl; `SaveWordUseCase`, `WordsViewModel`, `WordsScreen`.
5. **ReadingSession + статистика** — `ReadingSessionRepository` (`record(ReadingSession)` + доменные проекции), `*Row`-переименование; impl; `EndReadingSessionUseCase`, `AlmanacViewModel`, `AlmanacScreen`, `HomeViewModel` (session-статистика).

## 5. Тестирование
- Круговой mapper-тест на агрегат: `entity.toDomain().toEntity() == entity` (и наоборот, где применимо) —
  чистый JVM, ловит потерю поля при 1:1 копировании. Мелкий, по одному на агрегат.
- Существующие юнит-тесты не ломаются; проекты собираются.
- Отдельных инструментальных тестов не требуется (чистый рефактор без изменения поведения/схемы БД).

## 6. Приёмка
1. Ни один файл в `domain/**` не импортирует Room-`@Entity` (`BookEntity/BookmarkEntity/NoteEntity/
   SavedWordEntity/ReadingSessionEntity`) и projection-строки (`*MinutesRow`).
2. Репо-интерфейсы объявлены в доменных типах; RepositoryImpl инкапсулируют маппинг.
3. **Известное исключение → 3B:** `TranslateTextUseCase` всё ещё импортирует `ReadingProgressManager`
   (capability-менеджер, не Room-модель). Полностью чистый `domain/**` (ноль импортов `data.**`)
   достигается после под-проекта B. Для 3A gate ограничен entity/проекциями.
4. Поведение приложения без изменений; `:app:testDebugUnitTest` зелёный; `:app:compileDebugKotlin` успешен.

## 7. Риск и охват
- Механический, локальный по агрегатам рефактор; каждый агрегат — отдельная компилируемая, зелёная
  задача, так что риск размазан и обратим по-задачно.
- Мапперы 1:1 и покрыты круговым тестом — потеря поля ловится сразу.
- DAO/Room-биндинг не меняется по семантике (только имя projection-класса `*Row`).
- Вне области: `data.local`-менеджеры (`ReadingProgressManager`/`ApiKeyManager`/`TranslatorEndpoints`/
  `TranslationUsageTracker`/`TextToSpeechManager`) и `TranslationUsage` — это под-проект B (P20).
  Парсерный `Book`/`Chapter`, БД-схема и миграции — не трогаются.
