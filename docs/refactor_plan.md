# Split Reader / Mirrolit — план рефакторинга и харднинга

> Дата составления: 2026-07-18 · Ветка на момент аудита: `feat/free-tier-book-limit`
> Основано на сквозном аудите проекта (парсерный слой + всё приложение).
> Документ = единый источник задач. Реализация идёт **пофазно**; каждая фаза
> самодостаточна, тестируема и может быть слита отдельным PR.

## 0. Как читать документ

- **Каталог проблем** (раздел 1) — пронумерованные находки `P#` с локацией, сутью,
  почему это важно, ссылкой на спеку/контракт и предлагаемым решением.
- **Пофазный план** (раздел 2) — порядок работ, сгруппированный по риску и связности,
  а не по слою. Каждая фаза: цель, задачи (со ссылками на `P#`), тесты, критерии приёмки.
- **Приложения** (раздел 3) — эталонный SQL миграции, чек-лист, стратегия тестирования.

Severity: **High** — риск потери данных/краха у пользователя · **Med** — баги данных,
джанк, безопасность · **Low** — качество, идиоматичность, тех-долг.

---

## 1. Каталог проблем

### 1.A. Данные и миграции БД

#### P1 — Дыра в миграции БД + `fallbackToDestructiveMigration()` → тихая потеря данных · **High**
- **Где:** `di/DatabaseModule.kt:53-54`, `data/local/AppDatabase.kt:15-16`.
- **Суть:** БД на `version = 4`, но зарегистрированы только `MIGRATION_1_2` и `MIGRATION_3_4`.
  **`MIGRATION_2_3` отсутствует.** Именно в схеме v3 появились таблицы `bookmarks`,
  `notes`, `saved_words`, `reading_sessions`. Пользователь, побывавший на v2, при апдейте
  упирается в отсутствующий шаг, и `fallbackToDestructiveMigration()` **удаляет всю базу**
  (библиотека, закладки, заметки, слова, статистика чтения) без предупреждения.
- **Почему важно:** необратимая потеря пользовательских данных на обновлении. Хуже того,
  сам `fallbackToDestructiveMigration()` в релизе означает, что *любая* будущая
  недостающая/ошибочная миграция так же молча сотрёт данные вместо явного падения.
- **Спека:** [Room · Migrate your DB](https://developer.android.com/training/data-storage/room/migrating-db-versions) — все шаги версий обязаны быть покрыты `Migration`; деструктивный fallback — только для dev.
- **Решение:** добавить `MIGRATION_2_3` (эталонный SQL — приложение 3.A), убрать
  `fallbackToDestructiveMigration()` из релиза (в debug можно оставить), см. P2/P3.

#### P2 — `exportSchema = false` → миграции нельзя валидировать · **Med**
- **Где:** `data/local/AppDatabase.kt:16`.
- **Суть:** Room не выгружает JSON-историю схемы, поэтому `MigrationTestHelper` не может
  проверить корректность миграций, а расхождение SQL с сущностью всплывёт только в рантайме.
- **Спека:** [Room · Test migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions#test).
- **Решение:** включить `exportSchema = true`, прописать `room.schemaLocation` в
  `build.gradle.kts`, закоммитить сгенерированные `app/schemas/**` в git.

#### P3 — Нет тестов миграций · **Med** (следствие P1/P2)
- **Решение:** добавить `MigrationTestHelper`-тесты на все пары (1→2, 2→3, 3→4) и
  `migrateAll` (1→4). Только после этого удалять деструктивный fallback безопасно.

### 1.B. Парсерный слой (зона акцента)

#### P4 — FB2 молча теряет абзацы длиннее 5000 символов · **Med (корректность)**
- **Где:** `domain/parser/Fb2Parser.kt:174` — `if (text.isNotBlank() && text.length < 5000)`.
- **Суть:** любой абзац `<p>` длиннее 5000 символов отбрасывается целиком.
- **Почему важно:** тихая потеря текста книги; магическое число без обоснования.
- **Решение:** убрать верхнюю границу (или заменить на защиту от аномалий с логом, а не
  тихим отбрасыванием; разумный предел — десятки-сотни КБ, не 5000).

#### P5 — FB2 читает только `<p>`: стихи и подзаголовки выпадают · **Med (корректность)**
- **Где:** `domain/parser/Fb2Parser.kt:145` (обрабатывается только тег `p`).
- **Суть:** не обрабатываются `<v>` (строки стиха), `<stanza>`/`<poem>`, `<subtitle>`,
  `<cite>`, `<empty-line/>`. По схеме FB2 поэзия строится на `<v>`, а не `<p>`.
- **Почему важно:** стихотворные/поэтические разделы выходят **пустыми**; подзаголовки
  внутри секции теряются.
- **Спека:** [FictionBook 2.0 schema](http://www.fictionbook.org/index.php/Eng:FictionBook_2.0) — элементы `poem/stanza/v/subtitle`.
- **Решение:** расширить конечный автомат на `<v>`, `<subtitle>`, `<cite>` (в рамках P10
  проще всего сделать при переписывании на pull-парсер со стеком элементов).

#### P6 — Чтение заголовка файла не гарантирует заполнение буфера · **Med (корректность)**
- **Где:** `domain/usecase/ParseBookUseCase.kt:69` — одиночный `stream.read(bytes)`.
- **Суть:** контракт `InputStream.read(byte[])` разрешает вернуть *меньше* байт, чем есть.
  Детект MOBI читает `header[60..68]` («BOOKMOBI»); при коротком чтении проверка молча
  срывается, и файл роутится только по имени/MIME.
- **Спека:** [`InputStream.read(byte[])`](https://docs.oracle.com/javase/8/docs/api/java/io/InputStream.html#read-byte:A-) — «reads *some number* of bytes».
- **Решение:** дочитывать до нужной длины (цикл `read`, либо `readNBytes`/`DataInputStream.readFully`).

#### P7 — Недетерминированный выбор парсера (Set + `firstOrNull`) · **Med (корректность)**
- **Где:** `di/ParserModule.kt` (`@IntoSet`) + `domain/usecase/ParseBookUseCase.kt:37`.
- **Суть:** реестр — Hilt `Set<BookParser>` без гарантии порядка обхода, а берётся первый
  подходящий. Предикаты `canParse` пересекаются (FB2 ловит `text/xml`/`application/xml`;
  EPUB/FB2 сниффят строку заголовка). При двойном совпадении победитель не определён.
- **Решение:** заменить на упорядоченный `List<BookParser>` с явным приоритетом
  (специфичные магические байты → раньше), либо ввести числовой `priority` в интерфейс и
  сортировать. Ужесточить `canParse` (FB2 не должен претендовать на любой `text/xml`).

#### P8 — Весь файл в память, без лимита размера (OOM/zip-bomb) · **Med (устойчивость)**
- **Где:** `MobiParser.kt:35` (`readBytes()` целиком); `EpubParser.kt:46-47` (все text- и
  image-entry в две карты в памяти) **+ повторное открытие zip ради обложки** `EpubParser.kt:196`.
- **Суть:** крупная или злонамеренно сжатая (zip-bomb) книга разворачивается в RAM целиком.
- **Почему важно:** OOM/DoS на устройствах с minSdk 26; двойное чтение zip — лишний I/O.
- **Решение:** ввести предел распакованного размера и/или числа entry, стримить обложку из
  уже прочитанных байт (переиспользовать `imageMap`, не открывать zip второй раз), для MOBI —
  проверять размер файла до `readBytes()`.

#### P9 — Знаковый `u32`, distance-0 в PalmDOC, нет отмены · **Low/Med (устойчивость)**
- **Где:** `MobiParser.kt:214-218` (`u32` → знаковый `Int`), `MobiParser.kt:276` (PalmDOC
  back-reference при `distance == 0` копирует из незаписанной области → порча данных),
  циклы декомпрессии/пулла/zip без `ensureActive()`.
- **Спека:** MOBI/PalmDOC (kindleunpack `getSizeOfTrailingDataEntries`, PalmDOC LZ77).
- **Решение:** маскировать разрядные поля в `Long` (`and 0xFFFFFFFFL`) там, где значение
  используется как размер/оффсет; при `distance == 0` прерывать копирование; добавить
  `coroutineContext.ensureActive()` в тела длинных циклов.
  > Примечание: бинарные оффсеты MOBI (`firstImageIndex` 0x6C, EXTH-флаг 0x80,
  > extra-data-flags 0xF2 при `mobiHeaderLength ≥ 0xE4`) сверены с kindleunpack — **корректны**,
  > трогать не нужно.

#### P10 — `Fb2Parser.parseInternal` — метод ~220 строк на ~20 булевых флагах · **Low (тех-долг)**
- **Где:** `domain/parser/Fb2Parser.kt:41-264`.
- **Суть:** гигантский конечный автомат с флагами `insideBody/insideTitle/insideParagraph/…`;
  именно из-за него возникли дыры P4/P5. Не тестируется поэлементно, хрупок к вариациям FB2.
- **Решение:** переписать на явный стек открытых элементов (element-stack) с маленькими
  хендлерами на тег; вынести декодирование `<binary>` и сборку обложки в отдельные функции.
  Это же естественно закрывает P4 и P5.

#### P11 — Разбиение MOBI на главы только по `<mbp:pagebreak>` · **Low (качество/будущее)**
- **Где:** `MobiParser.kt:291` (`PAGEBREAK`), `buildChapters` с `.ifEmpty { listOf(html) }`.
- **Суть:** книги, где границы глав заданы filepos-якорями или CSS `page-break-before`,
  схлопываются в **одну гигантскую главу** без навигации.
- **Решение:** дополнить сплиттер: помимо `mbp:pagebreak` учитывать заголовки `<h1..h3>`
  и/или элементы с `page-break-before` как дополнительные точки разреза; как fallback —
  резать по заголовкам, найденным `HtmlChapterExtractor`.

#### P12 — `String.hashCode()` как имя файла обложки/картинки · **Low (корректность)**
- **Где:** `MobiParser.kt:181`, `EpubParser.kt:70,193`, `Fb2Parser.kt:242,277`.
- **Суть:** 32-битный `hashCode` даёт коллизии → разные книги перетирают обложки друг друга
  или картинки цепляются не к той книге.
- **Решение:** использовать контент-адресацию (SHA-256 от `uri`/байт) или гарантированно
  уникальный ключ книги вместо `hashCode()`.

### 1.C. Данные, перевод, безопасность

#### P13 — Кэш переводов по `text.hashCode()` → чужой перевод из кэша · **Med (корректность)**
- **Где:** `data/repository/TranslationRepositoryImpl.kt:42-43`
  (`cacheKey = "${provider}_${text.hashCode()}_${source}_${target}"`).
- **Суть:** 32-битный хэш по всей книге даёт коллизии; коллизия возвращает **неверный**
  закэшированный перевод для другого абзаца с той же парой провайдер/языки.
- **Решение:** ключ на основе SHA-256 от текста (или полноценного контент-хэша), при желании
  оставить hashCode как быстрый пре-фильтр, но сверять сам текст.

#### P14 — Keystore/`SharedPreferences` на main-потоке при инициализации ViewModel · **Med (джанк)**
- **Где:** `ReaderViewModel.kt:125`, `SettingsViewModel.kt:108`, `TranslatorConfig.kt:37`
  (вызов `buildTranslatorConfig` в конструкторе → `isConfigured()` → AES-GCM decrypt через
  Keystore-демон на каждый провайдер); `HomeViewModel.kt:88-92` (чтение `SharedPreferences`
  по каждой книге в `combine` на `Dispatchers.Main`).
- **Суть:** синхронные Keystore-IPC и чтение XML-prefs на главном потоке при открытии
  экрана → подлагивания; в Home ещё и повторяется на каждую эмиссию апстрим-флоу.
- **Решение:** вынести построение `TranslatorConfig` и per-book prefs-чтения в
  `Dispatchers.IO`/`Default` (например, `flowOn`, ленивое `stateIn`, или предвычисление
  в репозитории); не дергать Keystore в конструкторе.

#### P15 — Entitlement полностью на клиенте, без проверки подписи покупки · **Med (безопасность)**
- **Где:** `data/billing/BillingManager.kt:54,157-214`.
- **Суть:** покупка корректно подтверждается (`acknowledge`) и гейтится на `PURCHASED`, но
  `Purchase.getSignature()`/`getOriginalJson()` не проверяются публичным ключом приложения, а
  флаг кэшируется в открытом `SharedPreferences` (`billing` → `premium_owned`), тривиально
  правится на рутованном устройстве.
- **Спека:** [Play Billing · Verify purchases](https://developer.android.com/google/play/billing/security#verify).
- **Решение:** добавить локальную проверку подписи (`Security.verifyPurchase` с Base64 public
  key из Play Console). Для разового IAP серверная валидация опциональна — зафиксировать как
  осознанный компромисс.

#### P16 — Backup-правила исключают не тот файл (уезжает premium-флаг) · **Med (безопасность)**
- **Где:** `res/xml/backup_rules.xml:16`, `res/xml/data_extraction_rules.xml:13` (исключён
  `entitlement.xml` — это debug-оверрайд) против реального кэша покупки `billing.xml`
  (`BillingManager.kt:52`).
- **Суть:** облачный бэкап/перенос устройства **переносит** `premium_owned=true` на новое
  устройство (до следующей синхронизации Play в `connect()`).
- **Решение:** добавить `<exclude ... path="billing.xml"/>` в оба xml-файла (сохранив
  существующее исключение).

#### P17 — Кастомный `http://` для LibreTranslate при заблокированном cleartext · **Low**
- **Где:** `data/translator/TranslatorEndpoints.kt:52-56` (`normalize` пропускает `http://`).
- **Суть:** с `targetSdk 36` cleartext по умолчанию заблокирован и network-security-config
  нет, поэтому `http://`-эндпоинт молча не подключится.
- **Решение:** форсить `https` либо явно отклонять `http://` с понятной ошибкой в UI.

#### P18 — Quick Translate использует неофициальный googleapis-эндпоинт · **Low**
- **Где:** `data/translator/QuickTranslateProvider.kt` (+ `di/TranslatorModule.kt:76`),
  `translate.googleapis.com/translate_a/single`.
- **Суть:** недокументированный эндпоинт, нарушает ToS Google, подвержен троттлингу/поломкам.
- **Решение:** оставить как best-effort бесплатный тир, но пометить в UI/доках как
  нестабильный; долгосрочно — вытеснить официальным провайдером.

### 1.D. Архитектура и качество

#### P19 — Инверсия зависимостей Clean Architecture: domain импортирует data-типы · **Med**
- **Где:** интерфейсы репозиториев отдают Room-`@Entity` (`domain/repository/BookLibraryRepository.kt:3`
  → `BookEntity`; аналогично Bookmark/Note/SavedWord/ReadingSession); use-case'ы импортируют
  data-типы, в т.ч. `domain/usecase/TranslateTextUseCase.kt:3` тянет конкретный
  `data.local.ReadingProgressManager`.
- **Суть:** стрелка зависимости смотрит domain → data (наоборот от правил). Персистентность
  нельзя менять, не трогая domain; Room-сущности протекают как доменные модели.
- **Решение (постепенно):** ввести доменные модели и мапперы data↔domain; репозиторные
  интерфейсы объявить в терминах доменных типов; `ReadingProgressManager` спрятать за
  доменным интерфейсом (например `ReadingPreferences`).

#### P20 — Presentation зависит от конкретных `data.local.*` менеджеров · **Low/Med**
- **Где:** `ReaderViewModel.kt:58-65`, `SettingsViewModel.kt:58-63` (инъекция конкретных
  `ApiKeyManager`, `TranslatorEndpoints`, `TranslationUsageTracker`, `ReadingProgressManager`,
  `TextToSpeechManager`).
- **Решение:** ввести доменные интерфейсы для этих возможностей, инъектировать их — упрощает
  тесты/фейки. Делать вместе с P19.

#### P21 — Навигация вкладок без `popUpTo/saveState/restoreState` · **Low**
- **Где:** `presentation/navigation/SplitReaderNavHost.kt:77-85` (только `launchSingleTop`).
- **Суть:** бэкстек накапливается, состояние вкладок не сохраняется; поведение «Назад»
  неидиоматично (ср. паттерн NowInAndroid).
- **Решение:** `popUpTo(startDestination){ saveState = true }; restoreState = true`.

#### P22 — `!!` на nullable UI-состоянии · **Low**
- **Где:** `presentation/home/HomeScreen.kt:309,311,1156` (`uiState.lastBook!!`, `coverBitmap!!`).
- **Решение:** заменить на `?.let`/smart-cast локальной переменной.

#### P23 — Файлы-гиганты с самодокументированным `TODO(architecture)` · **Low**
- **Где:** `ReaderScreen.kt` (~1.7к строк), `HomeScreen.kt` (~1к), `WordsScreen.kt` (~900),
  `ReaderViewModel.kt`.
- **Решение:** выделить композаблы/секции и подэкраны; разнести ответственность. Плановая,
  не срочная работа.

#### P24 — Расхождение констант и дефолтов · **Low**
- **Где:** предел размера шрифта `SettingsViewModel.kt:155` (`14f..24f`) против
  `ReaderViewModel.kt:376` (`14f..30f`); дефолт `paragraphSpacing` `8f`
  (`ReadingProgressManager.getParagraphSpacing()`) против `18f` в `ReaderViewModel`/UI.
- **Решение:** единый источник констант (общий объект `ReadingDefaults`).

#### P25 — Мелкие проглатывания исключений / прочее · **Low**
- **Где:** `HomeViewModel.kt:129` (`catch (_: SecurityException) {}` без лога);
  `AlmanacViewModel.kt:42-54` (три отдельных `flatMapLatest` на один источник → 3 одинаковых
  запроса).
- **Решение:** логировать проглоченное; шарить один апстрим-флоу в Almanac.

#### P26 — Тонкое покрытие тестами критических путей · **Med**
- **Суть:** нет тестов на миграции БД, billing/entitlement, репозитории, кэш переводов.
  Две самые рисковые зоны (миграции, billing) не покрыты.
- **Решение:** добавлять тесты в рамках соответствующих фаз (см. раздел 2), а не отдельным
  «тестовым» блоком в конце.

---

## 2. Пофазный план реализации

Порядок: сначала снимаем риск потери данных, затем харднинг парсера (зона акцента), затем
корректность данных/безопасность, затем архитектурная гигиена, в конце — полировка.
Каждая фаза — отдельный PR с зелёными тестами.

### Фаза 0 — Безопасность данных (БД-миграции) · закрывает P1, P2, P3 · **приоритет High**
1. Включить `exportSchema = true` (`AppDatabase.kt`), настроить `room.schemaLocation` в
   `app/build.gradle.kts`, собрать проект, закоммитить `app/schemas/**`.
2. Добавить `MIGRATION_2_3` по эталонному SQL (приложение 3.A), выверив его против
   выгруженной схемы v3/v4 (столбцы, `NOT NULL`, индексы, FK — точь-в-точь).
3. Зарегистрировать миграцию: `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`.
4. Убрать `fallbackToDestructiveMigration()` из релизной сборки (допустимо оставить
   только под `BuildConfig.DEBUG`).
5. Добавить `MigrationTestHelper`-тесты: 1→2, 2→3, 3→4 и сквозной 1→4.
- **Критерий приёмки:** приложение, поднятое с БД v2, после апдейта сохраняет все данные;
  миграционные тесты зелёные; схемы закоммичены.

### Фаза 1 — Корректность и устойчивость парсера · зона акцента
Подфаза 1a — **корректность** (P4, P5, P6, P7):
1. FB2: убрать лимит 5000 символов (P4).
2. Дочитывание заголовка до нужной длины в `ParseBookUseCase.readHeaderBytes` (P6).
3. Заменить `Set<BookParser>` на упорядоченный `List` с явным приоритетом; ужесточить
   пересекающиеся `canParse` (P7).
4. FB2: поддержать `<v>`, `<subtitle>`, `<cite>` (P5) — если делаем 1c, удобнее там.

Подфаза 1b — **устойчивость к битым/враждебным файлам** (P8, P9):
5. Предел распакованного размера/числа entry для EPUB; проверка размера MOBI; стриминг
   обложки EPUB из уже прочитанных байт без повторного открытия zip (P8).
6. Маскирование разрядных полей MOBI в `Long`; защита PalmDOC при `distance == 0`;
   `ensureActive()` в длинных циклах (P9).

Подфаза 1c — **качество/тех-долг парсера** (P10, P11, P12):
7. Переписать `Fb2Parser.parseInternal` на element-stack с маленькими хендлерами; вынести
   декодирование `<binary>` и обложку (P10) — попутно закрывает P5.
8. Расширить сплиттер глав MOBI (заголовки/`page-break-before` в дополнение к
   `mbp:pagebreak`) (P11).
9. Контент-адресные имена файлов обложек/картинок вместо `hashCode()` (P12).
- **Тесты:** юнит-тесты на FB2 со стихами/подзаголовками/длинным абзацем; MOBI без
  `mbp:pagebreak`; короткое чтение заголовка; битые входы (усечённый zip, distance-0)
  не роняют приложение, а дают `ParseResult.Error`.
- **Критерий приёмки:** новые тесты зелёные; существующие парсер-тесты не сломаны;
  ручная проверка на реальном устройстве (эмулятор не тянет ML Kit/Gutenberg-IPv6).

### Фаза 2 — Корректность данных и безопасность · P13, P15, P16, P14, P17, P18
1. Контент-хэш (SHA-256) для ключа кэша переводов (P13) + тест на отсутствие коллизий.
2. Локальная проверка подписи покупки в `BillingManager` (P15).
3. Исключить `billing.xml` из `backup_rules.xml` и `data_extraction_rules.xml` (P16).
4. Вынести построение `TranslatorConfig`/Keystore-decrypt и per-book prefs-чтения с
   main-потока (P14).
5. Форсить `https` / явная ошибка для LibreTranslate (P17); пометка нестабильности
   Quick Translate (P18).
- **Критерий приёмки:** тест кэша переводов на коллизии зелёный; премиум-флаг не переносится
  бэкапом; открытие Reader/Settings без джанка (профилирование); поддельная подпись покупки
  отвергается.

### Фаза 3 — Архитектурная гигиена · P19, P20
1. Ввести доменные модели + мапперы; репозиторные интерфейсы перевести на доменные типы,
   убрать утечку Room-`@Entity` в domain (P19).
2. Спрятать `ReadingProgressManager` и прочие `data.local.*` за доменными интерфейсами;
   presentation инъектирует интерфейсы (P20).
- **Замечание:** делать инкрементально, репозиторий за репозиторием, чтобы каждый шаг
  собирался и проходил тесты. Это самая объёмная фаза — допускается разбить на несколько PR.
- **Критерий приёмки:** `domain/**` не импортирует `data.**`; сборка/тесты зелёные.

### Фаза 4 — Полировка · P21, P22, P23, P24, P25
1. `popUpTo/saveState/restoreState` в навигации вкладок (P21).
2. Убрать `!!` в `HomeScreen` (P22).
3. Единый `ReadingDefaults` для констант/дефолтов (P24).
4. Логирование проглоченных исключений; шаринг апстрим-флоу в Almanac (P25).
5. Выделение композаблов из файлов-гигантов (P23) — по возможности, не блокирует релиз.
- **Критерий приёмки:** поведение «Назад» по вкладкам идиоматично; статические предупреждения
  по `!!` сняты; константы из одного источника.

---

## 3. Приложения

### 3.A. Эталонный SQL для `MIGRATION_2_3`

> Черновик по текущим сущностям (`BookmarkEntity`, `NoteEntity`, `SavedWordEntity`,
> `ReadingSessionEntity`). **Перед фиксацией сверить с выгруженной схемой v3** (после включения
> `exportSchema`): порядок столбцов, `NOT NULL`, дефолты, имена индексов и FK должны совпадать
> байт-в-байт, иначе Room упадёт на валидации при открытии.

```sql
-- bookmarks
CREATE TABLE IF NOT EXISTS `bookmarks` (
    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    `bookUri` TEXT NOT NULL,
    `chapterIndex` INTEGER NOT NULL,
    `paragraphIndex` INTEGER NOT NULL,
    `label` TEXT,
    `createdAt` INTEGER NOT NULL,
    FOREIGN KEY(`bookUri`) REFERENCES `books`(`uri`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_bookmarks_bookUri` ON `bookmarks` (`bookUri`);

-- notes (составной PK)
CREATE TABLE IF NOT EXISTS `notes` (
    `bookUri` TEXT NOT NULL,
    `chapterIndex` INTEGER NOT NULL,
    `paragraphIndex` INTEGER NOT NULL,
    `body` TEXT NOT NULL,
    `isHighlight` INTEGER NOT NULL,
    `createdAt` INTEGER NOT NULL,
    `updatedAt` INTEGER NOT NULL,
    PRIMARY KEY(`bookUri`, `chapterIndex`, `paragraphIndex`),
    FOREIGN KEY(`bookUri`) REFERENCES `books`(`uri`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_notes_bookUri` ON `notes` (`bookUri`);

-- saved_words
CREATE TABLE IF NOT EXISTS `saved_words` (
    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    `word` TEXT NOT NULL,
    `partOfSpeech` TEXT,
    `sourceLang` TEXT NOT NULL,
    `targetLang` TEXT NOT NULL,
    `translation` TEXT NOT NULL,
    `bookUri` TEXT,
    `bookTitle` TEXT NOT NULL,
    `chapterIndex` INTEGER NOT NULL,
    `paragraphIndex` INTEGER NOT NULL,
    `contextSnippet` TEXT NOT NULL,
    `note` TEXT,
    `savedAt` INTEGER NOT NULL,
    FOREIGN KEY(`bookUri`) REFERENCES `books`(`uri`) ON UPDATE NO ACTION ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS `index_saved_words_bookUri` ON `saved_words` (`bookUri`);
CREATE INDEX IF NOT EXISTS `index_saved_words_sourceLang` ON `saved_words` (`sourceLang`);

-- reading_sessions
CREATE TABLE IF NOT EXISTS `reading_sessions` (
    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    `bookUri` TEXT,
    `bookTitle` TEXT NOT NULL,
    `sourceLang` TEXT NOT NULL,
    `startedAt` INTEGER NOT NULL,
    `endedAt` INTEGER NOT NULL,
    `durationSeconds` INTEGER NOT NULL,
    `paragraphsRead` INTEGER NOT NULL,
    FOREIGN KEY(`bookUri`) REFERENCES `books`(`uri`) ON UPDATE NO ACTION ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS `index_reading_sessions_startedAt` ON `reading_sessions` (`startedAt`);
CREATE INDEX IF NOT EXISTS `index_reading_sessions_bookUri` ON `reading_sessions` (`bookUri`);
```

### 3.B. Сводная таблица приоритетов

| ID | Проблема | Severity | Фаза |
|----|----------|----------|------|
| P1 | Дыра миграции БД + деструктивный fallback | High | 0 |
| P2 | `exportSchema = false` | Med | 0 |
| P3 | Нет тестов миграций | Med | 0 |
| P4 | FB2 теряет абзацы >5000 символов | Med | 1a |
| P5 | FB2 читает только `<p>` (стихи выпадают) | Med | 1a/1c |
| P6 | Чтение заголовка не заполняет буфер | Med | 1a |
| P7 | Недетерминированный выбор парсера | Med | 1a |
| P8 | Весь файл в память, без лимита (OOM) | Med | 1b |
| P9 | Знаковый u32 / distance-0 / нет отмены | Low/Med | 1b |
| P10 | `parseInternal` ~220 строк на флагах | Low | 1c |
| P11 | MOBI-главы только по `mbp:pagebreak` | Low | 1c |
| P12 | `hashCode()` как имя файла обложки | Low | 1c |
| P13 | Кэш переводов по `hashCode()` | Med | 2 |
| P14 | Keystore/prefs на main-потоке | Med | 2 |
| P15 | Entitlement без проверки подписи | Med | 2 |
| P16 | Backup исключает не тот файл | Med | 2 |
| P17 | `http://` LibreTranslate | Low | 2 |
| P18 | Неофициальный googleapis-эндпоинт | Low | 2 |
| P19 | Domain импортирует data-типы | Med | 3 |
| P20 | Presentation → конкретные `data.local.*` | Low/Med | 3 |
| P21 | Навигация без `popUpTo/saveState` | Low | 4 |
| P22 | `!!` на nullable UI-состоянии | Low | 4 |
| P23 | Файлы-гиганты | Low | 4 |
| P24 | Расхождение констант | Low | 4 |
| P25 | Проглатывание исключений / дубль-запросы | Low | 4 |
| P26 | Тонкое покрытие тестами | Med | 0–2 (по фазам) |

### 3.C. Общие принципы исполнения

- Один PR = одна фаза (крупные фазы 1/3 можно дробить на подфазы-PR).
- Каждая фаза начинается с тестов на затрагиваемое поведение (TDD, где применимо).
- Парсерные изменения проверяются на реальном устройстве (эмулятор не тянет ML Kit и
  Gutenberg по IPv6).
- Ничего не мержим в `main` без зелёной сборки и тестов.
