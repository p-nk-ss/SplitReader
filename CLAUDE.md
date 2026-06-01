# Split Reader

## What This App Does

SplitReader is a multi-language e-book reader with real-time ML Kit translation. Users open EPUB, FB2, or classic MOBI files, and the app displays the original text alongside a live translation in a split-pane layout. Source language is auto-detected; target language is user-selectable (12 languages). Translations are cached in Room to avoid re-translating.

## Package & Build

| Property | Value |
|----------|-------|
| Package | `com.example.splitreader` |
| Min SDK | 26 |
| Target SDK / Compile SDK | 36 |
| Kotlin | 2.0.21 |
| AGP | 8.11.2 |

## Architecture

Single-module app. Clean Architecture + MVVM. **Jetpack Compose** (Material 3) — no Fragments/XML layouts; screens are composables hosted by a single `NavHost`.

```
app/src/main/java/com/example/splitreader/
├── SplitReaderApplication.kt   # @HiltAndroidApp
├── MainActivity.kt             # setContent { SplitReaderTheme { SplitReaderNavHost() } }
├── presentation/
│   ├── navigation/             # SplitReaderNavHost (routes) + AppShell (left nav rail)
│   ├── home/                   # Library screen (HomeRoute/HomeScreen + HomeViewModel)
│   ├── reader/                 # Reading screen (ReaderRoute/ReaderScreen + ReaderViewModel); split-pane render + dialogs
│   ├── settings/               # Settings screen (SettingsRoute/SettingsScreen + SettingsViewModel)
│   ├── almanac/ words/         # Reading-stats + saved-vocabulary screens
│   ├── theme/                  # Palettes, Type.kt (fonts + ReadingFont enum), spacing/radii tokens
│   └── ui/                     # Shared widgets: SettingsControls (Slider/Toggle/Typography)
├── domain/
│   ├── model/                  # Book, Chapter, Language, TranslationState
│   ├── parser/                 # BookParser registry interface + EpubParser, Fb2Parser, MobiParser, shared HtmlChapterExtractor
│   ├── repository/             # TranslationRepository interface
│   ├── usecase/                # ParseBookUseCase, TranslateTextUseCase
│   └── LanguageDetector.kt     # ML Kit Language ID wrapper
├── data/
│   ├── local/                  # AppDatabase (Room), TranslationDao, TranslationCacheEntity, ReadingProgressManager (SharedPrefs)
│   └── repository/             # TranslationRepositoryImpl
└── di/
    ├── AppModule.kt            # Parsers, TranslationRepository
    └── DatabaseModule.kt       # AppDatabase, TranslationDao
```

## Key Tech

| Concern | Library |
|---------|---------|
| DI | Hilt 2.52 |
| Database | Room 2.7.0 |
| UI | Jetpack Compose + Material 3 |
| Navigation | AndroidX Navigation Compose (single `NavHost`) |
| Async | Coroutines 1.7.3 + Flow |
| Translation | ML Kit Translate 17.0.2 |
| Language detection | ML Kit Language ID 17.0.4 |
| EPUB / MOBI HTML parsing | Jsoup 1.17.2 |
| FB2 parsing | Android XML parser |
| MOBI parsing | Hand-rolled PDB + PalmDOC (classic MOBI; HUFF/CDIC & AZW3/KF8 not yet supported) |
| JSON | Gson 2.10.1 |

## Navigation

Programmatic Compose `NavHost` in `presentation/navigation/SplitReaderNavHost.kt`. Routes: `HOME_ROUTE` (start), `READER_ROUTE` (`reader?path={path}`), `ALMANAC_ROUTE`, `WORDS_ROUTE`, `SETTINGS_ROUTE`. `AppShell` wraps the host with the left editorial nav rail (hidden while reading). Home navigates to the reader by passing the book file path as the `path` argument.

## Settings & reading customization

`SettingsScreen` is the global preferences editor. The reader's appearance/translation prefs are persisted as **single global keys** in `ReadingProgressManager`, so Settings and the reader's in-session `DisplaySettingsDialog`/`TranslatorPickerDialog` share one source of truth (no per-book scoping). Typography (typeface via `ReadingFont`, font size, line height, paragraph/letter spacing, first-line indent, justification, hyphenation) is rendered through the shared `TypographyControls` and applied at the reader's paragraph render sites. Settings also hosts: translation engine config (mirrors the reader's `TranslatorPickerDialog`), clear-translation-cache (`TranslationDao.clearAll`), TTS rate/pitch (`TextToSpeechManager`), and About. Bookmarks (existing `BookmarkRepository`/`ToggleBookmarkUseCase`) are surfaced via the reader top-bar bookmark button + an in-reader bookmarks dialog.

## Supported Languages (12)

EN, RU, DE, FR, ES, IT, ZH, JA, PT, AR, KO, TR — defined in `Language.kt` enum.

## Important Patterns

- ViewModels injected via `@HiltViewModel`; Activities/Fragments annotated `@AndroidEntryPoint`
- `ReadingProgressManager` persists last chapter + scroll position per book via SharedPreferences
- `TranslationCacheEntity` keyed by text hash — check cache before calling ML Kit
- `ParseBookUseCase` injects a `Set<BookParser>` registry (Hilt `@IntoSet` in `di/ParserModule`) and delegates to the first parser whose `canParse(fileName, mimeType, header)` matches — adding a format is a new `BookParser` + one binding, no dispatcher change

## Skills
Read and follow: ~/.claude/skills/android/SKILL.md