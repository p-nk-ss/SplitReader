# Split Reader (brand: **Mirrolit**)

## What This App Does

Mirrolit is a multi-language e-book reader with real-time translation. Users open EPUB, FB2, or classic MOBI files, and the app displays the original text alongside a live translation in a split-pane layout. Source language is auto-detected; target language is user-selectable (12 languages). The default translator is on-device ML Kit; optional online engines (Quick Translate, DeepL, Google Cloud, Azure, LibreTranslate) are available with the user's own API key. Translations are cached in Room. The library has a **free tier of 3 books**; a one-time **Google Play Billing** purchase (`premium_unlimited`) unlocks an unlimited library.

> The Gradle project/module is still named "Split Reader" and the source package is `com.example.splitreader`, but the shipping app is branded **Mirrolit** (`applicationId` `io.mirrolit.app`, label "Mirrolit").

## Package & Build

| Property | Value |
|----------|-------|
| applicationId (Play) | `io.mirrolit.app` |
| namespace / source package | `com.example.splitreader` |
| App label | Mirrolit |
| Min SDK | 26 |
| Target SDK / Compile SDK | 36 |
| versionCode / versionName | `1` / `1.0` |
| Kotlin | 2.0.21 |
| AGP | 8.11.2 |

## Release & signing

- **Signing:** `app/build.gradle.kts` reads `keystore.properties` (root, **gitignored**) to sign release with the upload key `mirrolit-upload.jks`. Absent file → release builds unsigned (so CI/fresh clones still build). See `keystore.properties.template`. Enable **Play App Signing** in the console.
- **R8:** release has `isMinifyEnabled = true` + `isShrinkResources = true`. Keep-rules in `proguard-rules.pro` cover Gson/Retrofit DTOs and Crashlytics `SourceFile`.
- **Crashlytics:** crash collection is gated to release builds (`isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG` in `SplitReaderApplication`); the R8 mapping uploads automatically.
- Release artifact: `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
- Launch-readiness docs live in `docs/`: `release_plan.md`, `play_console_release.md`, `test_plan.md`, `privacy_policy.md`, and `store-assets/`. Working context / resume point: `memory.md` (repo root).

## Architecture

Single-module app. Clean Architecture + MVVM. **Jetpack Compose** (Material 3) — no Fragments/XML layouts; screens are composables hosted by a single `NavHost`.

```
app/src/main/java/com/example/splitreader/
├── SplitReaderApplication.kt   # @HiltAndroidApp; gates Crashlytics collection to release
├── MainActivity.kt
├── presentation/
│   ├── navigation/             # SplitReaderNavHost (routes) + AppShell (left nav rail w/ Mirrolit logo)
│   ├── home/                   # Library (HomeRoute/Screen + HomeViewModel)
│   ├── reader/                 # Reading screen; split-pane render + dialogs (ReaderViewModel)
│   ├── settings/               # Settings (SettingsViewModel); appearance/translation/TTS/premium
│   ├── catalog/                # Free-book catalog (CatalogViewModel); Gutenberg + Standard Ebooks
│   ├── auth/ profile/          # Firebase email/Google sign-in, account/profile, delete account
│   ├── premium/                # PremiumViewModel + PurchaseEventEffect (Billing UI seam)
│   ├── almanac/ words/         # Reading-stats + saved-vocabulary screens
│   ├── theme/                  # Palettes, Type.kt (fonts + ReadingFont), spacing/radii tokens
│   └── ui/                     # Shared widgets + LibraryLimitDialog
├── domain/
│   ├── model/                  # Book, Chapter, Language, TranslationProvider, CatalogBook…
│   ├── parser/                 # BookParser registry + EpubParser, Fb2Parser, MobiParser, HtmlChapterExtractor
│   ├── repository/             # Translation/BookLibrary/Entitlement/Bookmark/Note/SavedWord/… interfaces
│   ├── usecase/                # ParseBookUseCase, TranslateTextUseCase, AddBookToLibraryUseCase…
│   ├── CrashReporter.kt        # crash-reporting seam (impl in data/crash)
│   └── LanguageDetector.kt     # ML Kit Language ID wrapper
├── data/
│   ├── local/                  # Room (AppDatabase/DAOs), ReadingProgressManager, ApiKeyManager (Keystore), TTS
│   ├── billing/                # BillingManager (Google Play Billing)
│   ├── crash/                  # FirebaseCrashReporter
│   ├── auth/ catalog/ translator/  # GoogleAuthClient, catalog clients, online-translator APIs
│   └── repository/             # *RepositoryImpl (incl. EntitlementRepositoryImpl = billing ∨ debug)
└── di/                         # AppModule, DatabaseModule, ParserModule, CatalogModule,
                                # TranslatorModule, AuthModule, DriveModule, CrashModule
```

## Key Tech

| Concern | Library |
|---------|---------|
| DI | Hilt 2.52 |
| Database | Room 2.7.0 |
| UI | Jetpack Compose + Material 3 (BOM 2024.09.03) |
| Navigation | AndroidX Navigation Compose (single `NavHost`) |
| Async | Coroutines 1.9.0 + Flow |
| Translation (on-device) | ML Kit Translate 17.0.3 |
| Language detection | ML Kit Language ID 17.0.6 |
| Online translators | Retrofit 2.11 + OkHttp 4.12 (DeepL, Google Cloud, Azure, LibreTranslate, Quick Translate) |
| Auth | Firebase Auth (BOM 33.7.0) + Credential Manager / Google Identity |
| Crash reporting | Firebase Crashlytics (release-only) |
| Billing | Google Play Billing 7.1.1 (one-time `premium_unlimited`) |
| Secrets at rest | Android Keystore AES-256-GCM (`ApiKeyManager`) |
| EPUB / MOBI HTML parsing | Jsoup 1.17.2 |
| FB2 parsing | Android XML parser |
| MOBI parsing | Hand-rolled PDB + PalmDOC (classic MOBI; HUFF/CDIC & AZW3/KF8 not yet supported) |
| JSON | Gson 2.10.1 |

## Navigation

Programmatic Compose `NavHost` in `presentation/navigation/SplitReaderNavHost.kt`. Routes: `HOME_ROUTE` (start), `READER_ROUTE` (`reader?path={path}`), `CATALOG_ROUTE`, `ALMANAC_ROUTE`, `WORDS_ROUTE`, `SETTINGS_ROUTE`, plus auth/profile routes. `AppShell` wraps the host with the left editorial nav rail (hidden while reading); the rail mark is the Mirrolit logo (`drawable-nodpi/mirrolit_logo.png`).

## Auth & account

Firebase Authentication: email/password (with verification email) + Google Sign-In via Credential Manager. Profile screen supports email-verification status, password reset, and account deletion (with re-authentication). Reading without an account is fully supported.

## Monetization & entitlement

- `AddBookToLibraryUseCase` is the single chokepoint enforcing `FREE_BOOK_LIMIT = 3` (per-device current library size, not a lifetime tally). Premium bypasses it; re-opening an owned book is an upsert, never "new".
- `EntitlementRepository.isPremium` = **Billing-owned OR debug-override**. `EntitlementRepositoryImpl` combines `BillingManager.premium` with a local debug flag (the Settings "Premium (debug)" toggle, `BuildConfig.DEBUG` only).
- `BillingManager` (`data/billing`) owns the Play `BillingClient` for the one-time INAPP product **`premium_unlimited`**: loads price, launches the purchase, acknowledges, and re-queries owned purchases on connect + on user-initiated **Restore** (Settings). Premium is cached in prefs for offline. `PremiumViewModel` + `PurchaseEventEffect` are the UI seam; the library-limit dialog's Upgrade button starts the purchase.
- The `premium_unlimited` product id **must match** `BillingManager.PREMIUM_PRODUCT_ID`; create + Activate it in Play Console (needs a Payments profile) before the purchase flow works.

## Catalog (free books)

`CatalogScreen` lets users discover and read free **public-domain** books with translation, no login required. Two sources behind a `CatalogSourceClient` multibinding (`di/CatalogModule`): **Project Gutenberg** (official OPDS at `gutenberg.org/ebooks/search.opds/`, parsed with Jsoup) and **Standard Ebooks**. EPUB/cover URLs are built deterministically from the id; `downloadEpub` streams the DRM-free EPUB into `filesDir/catalog/<id>.epub` and returns a `file://` Uri; `CatalogViewModel` then runs the existing `ParseBookUseCase` → `EpubParser` → `AddBookToLibraryUseCase`, so a catalog book lands in the library (and is gated by the free-tier limit) and reads like any imported file. Google Drive import is also supported (read-only, picked file). Purchased Google Play Books are intentionally **not** supported (Adobe ACS4 DRM). `HomeViewModel.openBookFromLibrary` skips the persisted-URI-permission check for `file://` (app-private) catalog/Drive downloads.

## Settings & reading customization

`SettingsScreen` is the global preferences editor. The reader's appearance/translation prefs are persisted as **single global keys** in `ReadingProgressManager`, so Settings and the reader's in-session `DisplaySettingsDialog`/`TranslatorPickerDialog` share one source of truth (no per-book scoping). Typography (typeface via `ReadingFont`, font size, line height, paragraph/letter spacing, first-line indent, justification, hyphenation) is rendered through the shared `TypographyControls`. Settings also hosts: translation-engine config (mirrors the reader's `TranslatorPickerDialog`; API keys stored via `ApiKeyManager`), clear-translation-cache, TTS rate/pitch, a **Premium** section (status + Restore purchase), a debug-only Premium toggle, and About. Bookmarks are surfaced via the reader top-bar button + an in-reader dialog.

## Supported Languages (12)

EN, RU, DE, FR, ES, IT, ZH, JA, PT, AR, KO, TR — defined in `Language.kt` enum (these are translation target languages; the UI itself is currently English-only — full localization is a 1.1 item).

## Important Patterns

- ViewModels injected via `@HiltViewModel`; Activities annotated `@AndroidEntryPoint`.
- `ReadingProgressManager` persists last chapter + scroll position per book via SharedPreferences.
- `TranslationCacheEntity` keyed by text hash — check cache before calling ML Kit.
- `ParseBookUseCase` injects a `Set<BookParser>` registry (Hilt `@IntoSet` in `di/ParserModule`) and delegates to the first parser whose `canParse(fileName, mimeType, header)` matches — adding a format is a new `BookParser` + one binding, no dispatcher change. It also records parse failures as non-fatals via `CrashReporter`.
- Translator API keys are encrypted at rest with an Android Keystore key (`ApiKeyManager`), with a session-only in-memory fallback if the Keystore is unavailable; the prefs file (`translator_keys_enc`) is excluded from backup.

## Tests

Unit tests in `app/src/test` (JUnit4 + `kotlinx-coroutines-test`, **hand-written fakes, no mock framework**): parsers/utilities, `TranslationPlanner`, and `AddBookToLibraryUseCaseTest` (free-tier rules). Instrumented in `app/src/androidTest`: `ParserBeginningTest`, `ApiKeyManagerTest` (Keystore round-trip). Backlog in `docs/test_plan.md`. Note: ML Kit model download and Gutenberg over IPv6 don't work on the emulator — verify those flows on a real device.

## Skills
Read and follow: ~/.claude/skills/android/SKILL.md
