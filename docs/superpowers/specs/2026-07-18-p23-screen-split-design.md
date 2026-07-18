# Спека: P23 — разбиение файлов-гигантов (экраны)

> Дата: 2026-07-18 · Ветка: `refactor/p23-screen-split` · Источник: `docs/refactor_plan.md` (P23).
> Отдельный проект после завершения основного рефактора (P1–P25). Согласовано: **ReaderScreen — 2 задачи,
> Home/Words по 1 (4 задачи)**; **только экраны, ReaderViewModel отложен**.

## 1. Цель

Разнести три больших Compose-экрана по когезивным файлам, чтобы каждый файл имел одну ясную
ответственность и умещался в контекст при чтении/правке. **Чисто механический перенос** top-level
композаблов/хелперов; поведение UI не меняется.

Текущие размеры: `ReaderScreen.kt` **2146**, `HomeScreen.kt` **1322**, `WordsScreen.kt` **980**.

## 2. Ключевой принцип (низкий риск)

В Kotlin импорт — это `пакет.имя`, не файл. Перенос top-level деклараций **между файлами одного
пакета** не требует правок импортов на местах вызова. Единственное необходимое изменение —
`private` → `internal` для тех приватных деклараций, что после переноса вызываются через границу файла
(компилятор укажет их «unresolved reference»); чисто локальные приватные хелперы остаются `private`.
Публичные/`internal` декларации переносятся без смены модификатора (их путь `пакет.имя` не зависит от
файла). **Тела переносятся дословно** — никакой правки логики, разметки или сигнатур.

## 3. Задачи и раскладка файлов

Каждая задача создаёт новые файлы в том же пакете, вырезает соответствующие декларации из экрана-ядра,
бампит видимость по необходимости, компилируется и проходит тесты.

### Задача 1 — ReaderScreen · A (панель + выделение) · пакет `presentation.reader`
- **`ReaderPane.kt`** ← `BookSpread`, `ParagraphItem`, `TranslationBubble`, `BubbleChip`,
  `TranslationPlaceholder`, `Illustration`, `PageEdgeTap`, `PageGutter`.
- **`ReaderSelection.kt`** ← `ParagraphActionsOverlay`, `HandleSide`, `handleAnchor`, `drawMarker`,
  `snapToWordStart`, `snapToWordEnd`.

### Задача 2 — ReaderScreen · B (chrome + диалоги) · пакет `presentation.reader`
- **`ReaderChrome.kt`** ← `ReaderTopBar`, `LangChip`, `ChapterMasthead`, `ReaderStatusFooter`,
  `TranslationBanner`, `TranslationErrorBanner`.
- **`ReaderDialogs.kt`** ← `EditorialDialog` (уже `internal`, переиспользуется в пакете),
  `LanguagePickerDialog`, `DisplaySettingsDialog`, `ChapterPickerDialog`, `BookmarksDialog`, `toRoman`.
- **Ядро `ReaderScreen.kt`** сохраняет: `ReaderRoute`, `ReaderLoadingScreen`, `ReaderErrorScreen`,
  `ReaderContent`.

### Задача 3 — HomeScreen · пакет `presentation.home`
- **`HomeCovers.kt`** ← `CoverMotif`, `CoverSpec`, `coverSpec`, `BookCover`, `drawMotif`, `ProgressRule`,
  `BookCoverCard`, `HomeHeaderSkeleton`, `SkeletonCoverCard`.
- **`HomeSections.kt`** ← `LibraryHeader`, `LibrarySearchBar`, `NoBooksMatch`, `StreakRibbon`, `StreakBar`,
  `ContinueReadingHero`, `ShelfHeader`, `FilterPill`, `EmptyLibrary`, `formatLastOpened`.
- **Ядро `HomeScreen.kt`** сохраняет: `HomeRoute`, `HomeScreen`.
  > `BookCover`/`ProgressRule` — публичные, могут использоваться из других пакетов; путь `presentation.home.BookCover`
  > при переносе внутри пакета не меняется, импорты у внешних вызывающих не трогаются.

### Задача 4 — WordsScreen · пакет `presentation.words`
- **`WordsMasterPane.kt`** ← `MasterPane`, `LangPill`, `MasterSearchField`, `DateGroupHeader`,
  `WordListItem`, `EmptyMaster`.
- **`WordsDetailPane.kt`** ← `DetailPane`, `EmptyDetail`, `WordDetail`, `ContextQuoteCard`, `BookInfoRow`,
  `ActionRow`, `ActionChip`, `NotesCard`, `NoteDialog`, `relativeDate`.
- **Ядро `WordsScreen.kt`** сохраняет: `WordsRoute`, `WordsScreen`.

## 4. Правила исполнения (для каждой задачи)
1. Создать новые файлы с корректным `package` и минимально необходимыми импортами (перенести
   использованные импорты; удалить из ядра ставшие ненужными).
2. Вырезать перечисленные декларации из ядра **дословно** и вставить в новый файл без изменений тела.
3. Компилировать; на каждый «unresolved reference: X» (приватная декларация, ставшая cross-file) —
   поднять её видимость `private` → `internal` (не выше). Повторять до зелёной сборки.
4. Не менять разметку, сигнатуры, порядок аргументов, значения — только расположение и видимость.

## 5. Тестирование
- UI-тестов нет; приёмка каждой задачи — `:app:compileDebugKotlin :app:testDebugUnitTest` зелёные
  (успешная компиляция доказывает, что все ссылки разрешились после переноса) + ревью подтверждает,
  что диф — **только перенос деклараций + бампы видимости**, тела байт-идентичны, логика/разметка не
  тронуты.
- Существующие тесты не ломаются.

## 6. Приёмка
1. Каждый экран-файл ядра значительно уменьшен (ReaderScreen/HomeScreen/WordsScreen ниже «гигантского»
   порога); новые файлы когезивны и названы по ответственности.
2. Никаких изменений поведения/разметки; переносы дословны; изменена только видимость (не выше `internal`).
3. Импорты на местах вызова не менялись (перенос в пределах пакета); внешние публичные ссылки
   (`BookCover`/`ProgressRule`) продолжают резолвиться.
4. `:app:compileDebugKotlin` + `:app:testDebugUnitTest` зелёные на каждой задаче.

## 7. Риск и охват
- Механический перенос в пределах пакета; компилятор — сильный страж (любая забытая ссылка = ошибка
  сборки). Риск логического регресса минимален (тела не меняются).
- Внимание к Compose-специфике: перенести все нужные импорты (androidx.compose.*), не потерять
  `@Composable`/`@OptIn`-аннотации при вырезании.
- **Вне области:** `ReaderViewModel` (разбиение логики VM — отдельно позже); любая правка поведения/UX;
  прочие экраны (Settings/Almanac/Catalog/Auth/Profile) — не трогаются.
