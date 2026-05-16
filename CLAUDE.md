# Split Reader

## What This App Does

SplitReader is a multi-language e-book reader with real-time ML Kit translation. Users open EPUB or FB2 files, and the app displays the original text alongside a live translation in a split-pane layout. Source language is auto-detected; target language is user-selectable (12 languages). Translations are cached in Room to avoid re-translating.

## Package & Build

| Property | Value |
|----------|-------|
| Package | `com.example.splitreader` |
| Min SDK | 26 |
| Target SDK / Compile SDK | 36 |
| Kotlin | 2.0.21 |
| AGP | 8.11.2 |

## Architecture

Single-module app. Clean Architecture + MVVM. No Compose — uses Fragments, Activities, RecyclerView, ViewBinding.

```
app/src/main/java/com/example/splitreader/
├── SplitReaderApplication.kt   # @HiltAndroidApp
├── MainActivity.kt             # NavHostFragment host
├── presentation/
│   ├── home/                   # Book selection screen (HomeFragment + HomeViewModel)
│   └── reader/                 # Reading screen (ReaderActivity + ReaderViewModel + ParagraphAdapter)
├── domain/
│   ├── model/                  # Book, Chapter, Language, TranslationState
│   ├── parser/                 # BookParser interface, EpubParser, Fb2Parser
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
| Navigation | AndroidX Navigation 2.7.7 (Fragment) |
| Async | Coroutines 1.7.3 + Flow |
| Translation | ML Kit Translate 17.0.2 |
| Language detection | ML Kit Language ID 17.0.4 |
| EPUB parsing | Jsoup 1.17.2 |
| FB2 parsing | Android XML parser |
| JSON | Gson 2.10.1 |

## Navigation

Single nav graph (`nav_graph.xml`). Start destination: `HomeFragment`. HomeFragment navigates to `ReaderActivity` passing a serialized `Book` as `bookJson` argument.

## Supported Languages (12)

EN, RU, DE, FR, ES, IT, ZH, JA, PT, AR, KO, TR — defined in `Language.kt` enum.

## Important Patterns

- ViewModels injected via `@HiltViewModel`; Activities/Fragments annotated `@AndroidEntryPoint`
- `ReadingProgressManager` persists last chapter + scroll position per book via SharedPreferences
- `TranslationCacheEntity` keyed by text hash — check cache before calling ML Kit
- `ParseBookUseCase` detects format by file extension, delegates to `EpubParser` or `Fb2Parser`

## Skills
Read and follow: ~/.claude/skills/android/SKILL.md