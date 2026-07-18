# Спека: Фаза 1 · под-проект C — разбиение глав MOBI

> Дата: 2026-07-18 · Ветка: `refactor/phase1c-mobi` · Источник: `docs/refactor_plan.md` (P11).
> Последний под-проект Фазы 1 (после A — диспетчер/безопасность, B — рерайт FB2).
> Согласовано: fallback **консервативный** (по заголовкам только при отсутствии разрывов); уровень —
> **мельчайший присутствующий** (h1→h2→h3); тесты **extraction-first** (JVM).

## 1. Проблема (P11)

`MobiParser.buildChapters` режет декомпрессированный HTML только по `<mbp:pagebreak>`
(`PAGEBREAK.split(html)`). Книги без этого тега дают один фрагмент → `HtmlChapterExtractor` берёт
первый заголовок, всё остальное — «main» → **одна гигантская глава без навигации**.

## 2. Дизайн

### 2.1. Новая чистая единица `domain/parser/MobiChapterSplitter.kt`
```kotlin
object MobiChapterSplitter {
    /** Splits decompressed MOBI HTML into per-chapter fragments. */
    fun split(html: String): List<String>
}
```
Алгоритм:
1. **Первичный разрез — разрывы страниц.** Regex `PAGE_BREAK` ловит `<mbp:pagebreak …>` **и** любой
   тег с инлайновым `page-break-before` в атрибутах. `val byBreaks = PAGE_BREAK.split(html).map { it.trim() }
   .filter { it.isNotEmpty() }`.
2. Если `byBreaks.size >= 2` → **вернуть `byBreaks`** (разрывы есть — доверяем им, по заголовкам не
   дорезаем; консервативное правило).
3. Иначе **fallback по заголовкам:** найти мельчайший присутствующий уровень среди `h1`,`h2`,`h3`
   (первый, который встречается в html). Если ни один не найден → вернуть `listOf(html)` (нет
   сигнала — один фрагмент, неизбежно). Если уровень `hN` найден — резать строку **перед** каждым
   `<hN` через lookahead (тег остаётся в начале следующего фрагмента), trim, отбросить пустые;
   `ifEmpty` → `listOf(html)`.

Regex-ориентиры (финализировать при реализации):
- `PAGE_BREAK = Regex("<\\s*mbp:pagebreak\\b[^>]*>|<[^>]*\\bpage-break-before\\b[^>]*>", RegexOption.IGNORE_CASE)`
- присутствие уровня: `Regex("<\\s*hN[\\s/>]", IGNORE_CASE).containsMatchIn(html)`
- разрез: `html.split(Regex("(?=<\\s*hN[\\s/>])", IGNORE_CASE))`

Строковый подход намеренно совпадает с текущим `PAGEBREAK.split`; «разрезанная» вложенность (незакрытый
`<div>` в одном фрагменте, орфанный `</div>` в другом) безопасна — `HtmlChapterExtractor` использует
Jsoup, который толерантен и извлекает текст (`.text()`), так что контент не теряется.

### 2.2. Изменение `MobiParser.buildChapters`
Единственная правка тела: заменить
```kotlin
        val fragments = PAGEBREAK.split(html).map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { listOf(html) }
```
на
```kotlin
        val fragments = MobiChapterSplitter.split(html)
```
Остальной цикл (`fragment → HtmlChapterExtractor.parse → Chapter`) **без изменений**. Удалить
`private val PAGEBREAK` из `companion object` (логика переехала в сплиттер).

### 2.3. Без изменений
`HtmlChapterExtractor`, прочие парсеры, модель `Chapter`, разбор PalmDOC/EXTH/обложки.

## 3. Тестирование (extraction-first)

JVM-юниты `app/src/test/.../domain/parser/MobiChapterSplitterTest.kt` — вход html-строка, проверяем
список фрагментов:
1. **Регресс:** html с двумя `<mbp:pagebreak>` → 3 фрагмента (поведение не изменилось).
2. **P11-фикс:** html без разрывов, с несколькими `<h2>` → фрагментов по числу `h2` (каждый начинается
   с `<h2>`).
3. **Нет сигнала:** html без разрывов и без заголовков → ровно 1 фрагмент.
4. **page-break-before:** html с `<div style="page-break-before:always">` → режется на этом маркере.
5. **Мельчайший уровень:** html c `<h1>` и `<h2>` → режется только по `<h1>` (число фрагментов = число `h1`).
6. **Консервативность:** html, где есть И `<mbp:pagebreak>` (≥2 фрагмента) И `<h2>` внутри → результат =
   разрез по разрывам (по заголовкам НЕ дорезается).
7. **Пред-контент:** html, начинающийся с абзацев до первого `<h2>` → первый фрагмент содержит пред-контент.

Регресс на реальных файлах: инструментальный `ParserBeginningTest` (2 MOBI-кейса) — «начало книги не
потеряно» продолжает проходить (книги без разрывов теперь дают больше глав; начало цело).

## 4. Файлы

- Новые: `domain/parser/MobiChapterSplitter.kt`; тест `app/src/test/.../domain/parser/MobiChapterSplitterTest.kt`.
- Изменить: `domain/parser/MobiParser.kt` — одна строка в `buildChapters`; удалить `PAGEBREAK`-константу.

## 5. Критерии приёмки

1. Книга без `<mbp:pagebreak>`, но с заголовками, даёт несколько глав, а не одну (тест P11).
2. Книга с разрывами страниц разбивается как прежде — по разрывам, без дорезания по заголовкам (тест регресса + консервативности).
3. Книга без разрывов и без заголовков даёт один фрагмент (без краша).
4. `MobiParser` не содержит инлайновой логики разбиения; она в `MobiChapterSplitter` (pure, JVM-тестируемый).
5. `:app:testDebugUnitTest` зелёный; инструментальный `ParserBeginningTest` (MOBI) не сломан.

## 6. Риск и охват

- Изменение аддитивно и изолировано: путь книг с разрывами страниц не меняется (правило ≥2 фрагментов);
  меняется только ветка «разрывов нет».
- Строковый сплиттер покрыт JVM-юнитами до правки `MobiParser`; поведение экстрактора не трогается.
- P4/P5/P10 (FB2) и P6–P9/P12 (диспетчер) — уже сделаны (B и A); здесь только MOBI-разбиение.
