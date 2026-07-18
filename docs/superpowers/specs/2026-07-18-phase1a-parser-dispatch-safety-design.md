# Спека: Фаза 1 / под-проект A — Диспетчер и безопасность парсеров

> Дата: 2026-07-18 · Ветка: `refactor/phase1-parser` · Источник: `docs/refactor_plan.md` (P6, P7, P8, P9, P12).
> Часть Фазы 1 (парсер). Декомпозиция Фазы 1 — **по подсистемам**: A (этот док) → B (FB2-рерайт: P4/P5/P10) → C (MOBI-главы: P11).
> Согласованные решения: **extraction-first** тестирование; `MAX_DECOMPRESSED ≈ 300 МБ` (фикс-константа, DoS-бэкстоп).

## 1. Цель

Сделать выбор парсера детерминированным и корректным, а сам разбор — устойчивым к битым/враждебным
файлам, без потери данных и без OOM. Достигается выносом чистой байтовой/диспетчерной логики в
маленькие JVM-тестируемые единицы; парсеры-обёртки над Android-API утоньшаются.

Закрываемые находки: **P6** (чтение заголовка), **P7** (недетерминированный выбор парсера),
**P8** (OOM/zip-bomb), **P9** (знаковый `u32` / distance-0 / отмена), **P12** (коллизии имён файлов).

## 2. Принципы (согласовано)

- **Extraction-first:** чистая логика → отдельные единицы + быстрые JVM-юнит-тесты (без эмулятора);
  инструментальные тесты — только где обязателен реальный Android-пайплайн.
- **Изоляция:** каждая новая единица имеет одну ответственность и явный интерфейс, тестируется независимо.
- **Без функциональных регрессий:** существующий `ParserBeginningTest` (инструментальный) обязан
  продолжать проходить.

## 3. Новые чистые единицы (`app/src/main/java/com/example/splitreader/domain/parser/util/`)

### 3.1. `StreamReading.kt` — надёжное/ограниченное чтение (P6 + P8)
```kotlin
/** Reads up to [n] bytes, looping until EOF or [n] reached (InputStream.read may return short). */
fun readUpTo(input: InputStream, n: Int): ByteArray

/** Reads the whole stream but throws [BookTooLargeException] once [maxBytes] is exceeded. */
fun readAllBounded(input: InputStream, maxBytes: Long): ByteArray

class BookTooLargeException(message: String) : Exception(message)
```
- `readUpTo`: цикл `read` в буфер до заполнения/EOF; возвращает `copyOf(read)`. Заменяет одиночный
  `stream.read` в детекте заголовка.
- `readAllBounded`: чтение чанками со счётчиком; при превышении — бросок с понятным сообщением
  («Book is too large to open safely»). Используется как zip-bomb/OOM-бэкстоп.

### 3.2. `ParserSelector.kt` — детерминированный выбор (P7)
```kotlin
/** Picks the highest-priority parser whose canParse matches; deterministic even over a Set. */
fun selectParser(
    parsers: Collection<BookParser>,
    fileName: String, mimeType: String, header: ByteArray,
): BookParser?
```
- Реализация: `parsers.filter { it.canParse(...) }.maxByOrNull { it.priority }` (тай-брейк —
  стабильный вторичный ключ, например по имени класса, чтобы результат был воспроизводим).

### 3.3. `MobiCodec.kt` — байтовые примитивы MOBI/PalmDOC (P9)
Вынос из `MobiParser` (сейчас приватные методы):
```kotlin
object MobiCodec {
    fun u16(b: ByteArray, off: Int): Int
    fun u32(b: ByteArray, off: Int): Long          // Long: избегаем знакового переполнения оффсетов/размеров
    fun trailingDataSize(data: ByteArray, size: Int, flags: Int): Int
    fun palmDocDecompress(input: ByteArray, length: Int): ByteArray   // guard: distance == 0 → stop copy
}
```
- `u32` → `Long` (`... and 0xFFFFFFFFL`) там, где значение используется как размер/оффсет; вызовы в
  `MobiParser` приводятся к `Int` через `.toIntExact`-подобную проверку диапазона (или явный
  `coerceIn`) на границах.
- `palmDocDecompress`: при `distance == 0` прерывать копирование ссылки (защита от порчи данных на
  битом потоке), сохранив текущее поведение для валидных потоков.

### 3.4. `FileKeys.kt` — контент-адресные имена (P12)
```kotlin
/** Stable, collision-resistant id for a resource key (e.g. book uri) — hex of SHA-256, truncated. */
fun stableId(key: String): String
```
- Заменяет `key.hashCode().toLong().and(0x7FFFFFFFL)` в `EpubParser`, `MobiParser`, `Fb2Parser`
  (имена файлов обложек/картинок). Усечение до ~16 hex-символов достаточно против коллизий.

## 4. Изменения в существующих файлах

### 4.1. `BookParser.kt` (P7)
- Добавить `val priority: Int get() = 0` (дефолт). Спец-парсеры с магией байт задают выше:
  MOBI/EPUB (магия/сигнатуры) — выше, FB2 по generic `text/xml` — ниже.
- Ужесточить пересечения `canParse`: FB2 сохраняет сильный сигнал `header.contains("<FictionBook")`
  и расширения; общий `mimeType == "text/xml"/"application/xml"` остаётся, но проигрывает по
  приоритету более специфичным парсерам (решается селектором, а не порядком `Set`).

### 4.2. `ParseBookUseCase.kt` (P6, P7)
- `readHeaderBytes` → через `readUpTo(stream, 256)`.
- Выбор парсера → через `selectParser(parsers, fileName, mimeType, header)`.

### 4.3. `EpubParser.kt` (P8, P12)
- Чтение zip-entry: аккумулировать распакованный размер; при превышении `MAX_DECOMPRESSED` бросать
  `BookTooLargeException` (ловится в `ParseBookUseCase` → `ParseResult.Error`).
- Обложку брать из уже загруженного `imageMap` (устранить повторное открытие zip); если по какой-то
  причине не найдено в карте — обложка отсутствует (без второго прохода).
- Имена файлов → `stableId(uri.toString())` вместо `hashCode`.

### 4.4. `MobiParser.kt` (P8, P9, P12)
- Проверка размера файла до полного `readBytes` (бэкстоп; при превышении — `BookTooLargeException`).
- Использовать `MobiCodec` вместо приватных байтовых методов.
- В цикле декомпрессии текстовых записей — `coroutineContext.ensureActive()` (кооперативная отмена).
- Имена файлов обложки → `stableId`.

### 4.5. `MobiFile.kt` (P9)
- Байтовые ридеры (`u16/u32`) переиспользуют `MobiCodec` (единый источник, устранить дубль).

### 4.6. `Fb2Parser.kt` (P8, P9, P12)
- В основном цикле pull-парсера — `coroutineContext.ensureActive()`.
- Имена файлов обложки/картинок → `stableId`.
- (Границы FB2-контента — P4/P5 — вне A; это под-проект B.)

## 5. Константы

- `MAX_DECOMPRESSED = 300L * 1024 * 1024` (~300 МБ). DoS-бэкстоп, не UX-лимит: легальные книги
  (в т.ч. тяжёлые иллюстрированные) проходят; zip-bomb/патология отсекается до OOM. Разместить в
  `StreamReading.kt` или общем `ParserLimits`.

## 6. Тестирование (extraction-first)

JVM-юниты (`app/src/test/.../domain/parser/util/`):
- **`StreamReadingTest`**: `readUpTo` с «рваным» `InputStream` (возвращает по 1 байту) → полные N байт;
  EOF раньше N → усечение. `readAllBounded`: поток > лимита → `BookTooLargeException`; ≤ лимита → байты.
- **`ParserSelectorTest`**: фейковые `BookParser` с пересекающимися `canParse` и разными `priority` →
  детерминированный победитель; при равенстве — стабильный тай-брейк; нет совпадений → `null`.
- **`MobiCodecTest`**: `u32` со старшим установленным битом → корректное неотрицательное `Long`;
  `palmDocDecompress` с `distance == 0` не портит выход и не зацикливается; эталонный PalmDOC-блок ↔
  ожидаемый распакованный текст (round-trip на известном примере).
- **`FileKeysTest`**: `stableId` детерминирован; разные входы → разные id; отсутствие коллизий на
  наборе реальных uri-подобных строк.

Инструментальные:
- Регресс: `ParserBeginningTest` проходит без изменений (гарантия, что вынос логики не сломал разбор).
- (Опц.) один e2e на OOM-гард: крафт-EPUB с сильно сжатым большим entry → `ParseResult.Error`
  (`BookTooLargeException`), приложение не падает.

## 7. Критерии приёмки

1. Детект MOBI по магии `BOOKMOBI` (offset 60) не срывается при коротком первом `read` (тест `readUpTo`).
2. При пересекающихся `canParse` выбор парсера детерминирован и задаётся `priority` (тест селектора).
3. Файл, распаковка которого превышает `MAX_DECOMPRESSED`, даёт `ParseResult.Error`, а не OOM/краш.
4. `u32`-оффсеты со старшим битом не уходят в минус; PalmDOC на `distance == 0` не портит данные.
5. Обложки/картинки именуются контент-адресно (`stableId`); нет перезаписи между разными книгами.
6. Все новые JVM-юниты зелёные; `ParserBeginningTest` не сломан; `:app:testDebugUnitTest` зелёный.

## 8. Риск и охват

- Изменения изолированы в парсерном слое; поведение успешного разбора валидных книг не меняется
  (только устойчивость к патологии + детерминизм выбора + имена файлов).
- Вынос логики в `MobiCodec`/утилиты — рефактор без смены семантики для валидных потоков; покрыт
  round-trip тестом и регрессом `ParserBeginningTest`.
- P4/P5/P10 (FB2-контент) и P11 (MOBI-главы) — **не в этом под-проекте** (B и C соответственно).
