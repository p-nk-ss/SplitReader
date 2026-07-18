# Спека: Фаза 1 · под-проект B — рерайт FB2-парсера

> Дата: 2026-07-18 · Ветка: `refactor/phase1b-fb2` · Источник: `docs/refactor_plan.md` (P4, P5, P10).
> Фаза 1 декомпозирована по подсистемам: A (диспетчер/безопасность, слито) → **B (этот док)** → C (MOBI-главы).
> Согласованные решения: архитектура **A — чистый билдер на нейтральных событиях**; стихи **построчно** (`<v>` → абзац); тесты **extraction-first** (JVM).

## 1. Цель

Переписать `Fb2Parser.parseInternal` — ~220-строчный конечный автомат на ~20 булевых флагах — в
маленький чистый билдер, попутно закрыв потерю текста: снять обрезку абзацев >5000 символов (**P4**)
и начать читать стихи/подзаголовки/цитаты (**P5**). Флаг-суп устраняется выносом логики в
JVM-тестируемую единицу (**P10**). Поведение разбора валидных книг не должно регрессировать.

## 2. Архитектура — новый пакет `domain/parser/fb2/`

### 2.1. `Fb2Event.kt` (pure)
```kotlin
sealed interface Fb2Event {
    data class Start(val name: String, val href: String?, val id: String?) : Fb2Event
    data class Text(val text: String) : Fb2Event
    data class End(val name: String) : Fb2Event
}
```
- `name` — имя тега в нижнем регистре.
- `href` — значение первого атрибута, чьё имя оканчивается на `href` (обрабатывает `href`, `l:href`,
  `xlink:href`), c обрезанным ведущим `#` и `trim` (как в текущем коде). Разрешение — в адаптере.
- `id` — атрибут `id` (для `<binary>`), `trim`.

### 2.2. `Fb2Document.kt` (pure) — результат билдера
```kotlin
data class Fb2Document(
    val title: String,
    val author: String,
    val annotation: String,
    val chapters: List<Fb2ChapterData>,
    val coverBinaryId: String?,
    val binaries: Map<String, String>,   // binary id -> raw base64 text (referenced ones incl. cover)
)

data class Fb2ChapterData(
    val index: Int,
    val title: String,
    val paragraphs: List<String>,        // epigraph paragraphs first, then body
    val epigraphCount: Int,
    val imageRefs: List<Pair<Int, String>>,  // (final anchor into paragraphs, binary id)
)
```
- Билдер сам вычисляет финальный якорь картинки (`epiShift + preShift + anchorWithinDirect`), чтобы
  адаптер только декодировал/прикреплял.
- `binaries` собирает base64-текст лишь для нужных id (обложка + референсы) — как сейчас.

### 2.3. `Fb2DocumentBuilder.kt` (pure Kotlin, JVM-тестируемый)
Push-API:
```kotlin
class Fb2DocumentBuilder {
    fun accept(event: Fb2Event)
    fun finish(): Fb2Document
}
```
Внутреннее состояние (заменяет булевы флаги):
- `elementStack: ArrayDeque<String>` — открытые теги (нижний регистр). Контекст решается запросами к
  стеку (например «внутри `title`», «внутри `epigraph`», «внутри `coverpage`»).
- `sectionStack: ArrayDeque<SectionFrame>` — по фрейму на открытую `<section>` (перенос текущего
  приватного класса `SectionFrame` внутрь билдера): `title`, `directParagraphs`, `epigraphParagraphs`,
  `imageRefs`, `index`.
- `textBuffer: StringBuilder` — активен, когда открыт «текстовый лист» (`p`, `v`, `subtitle`, `title`,
  `text-author`, `book-title`, `first-name`, `last-name`); на закрытии сбрасывается в нужный приёмник.
- Метаданные (`title/firstName/lastName/annotation`), обложка (`coverBinaryId`), референсы
  (`referencedImageIds`, `binaries`, `currentBinaryId`), преамбула (`preambleParagraphs`,
  `preambleFlushed`).

### 2.4. Переписанный `Fb2Parser.kt` (тонкий Android-адаптер)
- `parse` (suspend) открывает поток, создаёт `XmlPullParser` (namespace-unaware), в цикле:
  `ensureActive()` → из события парсера собирает `Fb2Event` (адаптер разрешает `href`/`id`) →
  `builder.accept(event)`.
- `builder.finish()` → `Fb2Document`. Если `chapters` пуст → `throw IllegalStateException("No chapters
  found in fb2 file")` (как сейчас).
- Декод (остаётся Android, переиспользуется текущий код):
  - обложка: `coverBinaryId` → `binaries[id]` → `Base64.decode` → сохранить в `covers/<stableId>.jpg`.
  - инлайн-картинки: по каждой главе `imageRefs` → `binaries[id]` → `Base64.decode` →
    `ImageStore.save(context, bytes, "${stableId(filePath)}_$id")` → `ChapterImage(anchor, path)`.
- Сборка `Book(title, author, chapters(Chapter), filePath, coverPath, synopsis)`; `synopsis` — через
  существующий `SynopsisExtractor.build(annotation, allParagraphs)`.

## 3. Маршрутизация текста и элементов (правила билдера)

Приёмник абзаца определяется контекстом (стек + текущий фрейм):
- преамбула-эпиграф (эпиграф под `<body>` до первой секции) → `preambleParagraphs`;
- секционный эпиграф (`<epigraph>` внутри секции) → `frame.epigraphParagraphs`;
- иначе → `frame.directParagraphs`.

Элементы, рождающие абзац (при `isNotBlank()`, **без лимита 5000** — P4):
- `<p>` — как сейчас.
- `<v>` — строка стиха → отдельный абзац (P5, построчно).
- `<subtitle>` — → абзац (P5).
- `<cite>` — контейнер; его вложенные `<p>`/`<v>` → **обычные абзацы** (`directParagraphs`), даже вне
  эпиграфа (не теряем текст; эпиграф-стиль неприменим — `epigraphCount` считает только префикс главы).
- `<poem>`, `<stanza>` — прозрачные контейнеры; их `<v>`/`<subtitle>` дают абзацы; `<title>` внутри
  `<poem>` → абзац (не заголовок секции — стек различает).
- `<empty-line/>` — пропуск.
- `<text-author>` внутри `<epigraph>` → `frame.epigraphParagraphs` (как сейчас).

Спец-элементы:
- `<book-title>`/`<first-name>`/`<last-name>` → метаданные.
- `<annotation>` — весь текст внутри → `annotation` (для synopsis).
- `<coverpage><image href="#id">` → `coverBinaryId` (первый).
- `<image href="#id">` в секции (вне coverpage) → `frame.imageRefs += (directParagraphs.size to id)`;
  `referencedImageIds += id`.
- `<binary id=...>` → если id — обложка или референс, копить base64-текст в `binaries[id]`.

`<title>` секции: заголовок фрейма; префикс из титулов предков-обёрток сохраняется
(`"Цена риска · 1. ..."`). Wrapper-секции (без `directParagraphs`) не эмитят главу.

## 4. Тестирование (extraction-first)

JVM-юниты `app/src/test/.../domain/parser/fb2/Fb2DocumentBuilderTest.kt` — кормим списки `Fb2Event`,
проверяем `Fb2Document`:
1. **P4:** абзац длиной >5000 символов присутствует в главе (не отброшен).
2. **P5 verse:** секция с `<poem><stanza><v>…</v><v>…</v>` → каждая строка отдельным абзацем.
3. **P5 subtitle:** `<subtitle>` внутри секции → абзац.
4. **P5 cite:** `<cite><p>…` → абзац в основном тексте.
5. **Регресс — секции:** вложенные wrapper→leaf секции → по главе на leaf, с префиксом титула предка.
6. **Регресс — преамбула-эпиграф:** `<body><epigraph><p>` до первой секции → в начало первой главы.
7. **Регресс — секц. эпиграф:** `<epigraph><p>` + `<text-author>` внутри секции → `epigraphParagraphs`,
   `epigraphCount` верный, эпиграф идёт перед телом.
8. **Регресс — картинки:** `<image href="#img1">` в секции → `imageRefs` с верным финальным якорем;
   `binaries["img1"]` захвачен; `<coverpage>` → `coverBinaryId`.
9. **Регресс — метаданные:** `book-title`/`first-name`+`last-name`/`annotation`.
10. **Пустой ввод:** нет секций с прозой → `chapters` пуст (адаптер бросит исключение).

Регресс на реальных файлах: инструментальный `ParserBeginningTest` (FB2-кейсы; идут при наличии
локального `qa_book/`) должен продолжать проходить без изменений.

## 5. Файлы

- Новые: `domain/parser/fb2/Fb2Event.kt`, `domain/parser/fb2/Fb2Document.kt`,
  `domain/parser/fb2/Fb2DocumentBuilder.kt`; тест `app/src/test/.../domain/parser/fb2/Fb2DocumentBuilderTest.kt`.
- Переписать: `domain/parser/Fb2Parser.kt` (тонкий адаптер; `saveFb2Cover` + декод инлайн-картинок
  остаются Android). Приватный `SectionFrame` уезжает в билдер.
- Без изменений: `HtmlChapterExtractor`, прочие парсеры, модель `Chapter/ChapterImage/Book`.

## 6. Критерии приёмки

1. Абзац >5000 символов сохраняется (тест P4).
2. `<v>`/`<subtitle>`/`<cite>` дают абзацы; стихи — построчно (тесты P5).
3. Все регресс-тесты билдера зелёные (секции/префикс/преамбула/эпиграф/картинки/метаданные).
4. `Fb2Parser` не содержит прежнего флаг-супа; вся структурная логика — в `Fb2DocumentBuilder`
   (pure, без Android-импортов).
5. `:app:testDebugUnitTest` зелёный; инструментальный `ParserBeginningTest` (FB2) не сломан.

## 7. Риск и охват

- Логика вынесена в чистый билдер и покрыта юнитами до переписывания адаптера (TDD), что снижает риск
  регресса структурной обработки (самая тонкая часть — стек секций/эпиграфов/якорей).
- Android-часть (`Base64`/`File`/`ImageStore`) переиспользуется как есть — не переписывается.
- P11 (MOBI-главы) — вне этого под-проекта (это C). Никаких изменений в MOBI/EPUB/диспетчере.
