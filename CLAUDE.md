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

- **Signing:** `app/build.gradle.kts` reads `keystore.properties` (root, **gitignored**) to sign release with the upload key `mirrolit-upload.jks`. Absent file → release builds unsigned (so CI/fresh clones still build). See `keystore.properties.template`. Enable **Play App Signing** in the console. `keystore.properties` also holds `billingPublicKey` (see Monetization); a signed release fails to build if it is blank.
- **R8:** release has `isMinifyEnabled = true` + `isShrinkResources = true`. Keep-rules in `proguard-rules.pro` cover Gson/Retrofit DTOs and Crashlytics `SourceFile`.
- **Crashlytics:** crash collection is gated to release builds (`isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG` in `SplitReaderApplication`); the R8 mapping uploads automatically.
- Release artifact: `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
- Launch-readiness docs live in `docs/`: `release_plan.md`, `play_console_release.md`, `test_plan.md`, `privacy_policy.md`, and `store-assets/`. Working context / resume point: `memory.md` (repo root).

## Architecture

Single-module app. Clean Architecture + MVVM. **Jetpack Compose** (Material 3) — no Fragments/XML layouts; screens are composables hosted by a single `NavHost`.

> **Dependency direction is enforced:** after the 2026-07 refactor (Phases 0–4 + P23, all merged to `main`), `domain/**` imports **nothing** from `data.**`. Repositories/use-cases speak domain models; the persistence/capability managers live in `data.local` behind domain interfaces (ports). Mappers translate `@Entity`↔domain in `data/repository/mapper`. See "Refactor status" below.

```
app/src/main/java/com/example/splitreader/
├── SplitReaderApplication.kt   # @HiltAndroidApp; gates Crashlytics collection to release
├── MainActivity.kt
├── presentation/
│   ├── navigation/             # SplitReaderNavHost (routes) + AppShell (left nav rail w/ Mirrolit logo)
│   ├── home/                   # Library: HomeScreen (core) + HomeCovers + HomeSections; HomeViewModel
│   ├── reader/                 # Reading screen, split across ReaderScreen (core Route/Content) +
│   │                           #   ReaderPane/ReaderSelection/ReaderChrome/ReaderDialogs; ReaderViewModel
│   ├── settings/               # Settings (SettingsViewModel); appearance/translation/TTS/premium
│   ├── catalog/                # Free-book catalog (CatalogViewModel); Gutenberg + Standard Ebooks
│   ├── auth/ profile/          # Firebase email/Google sign-in, account/profile, delete account
│   ├── premium/                # PremiumViewModel + PurchaseEventEffect (Billing UI seam)
│   ├── almanac/                # Reading-stats screen (AlmanacViewModel)
│   ├── words/                  # Saved-vocabulary: WordsScreen (core) + WordsMasterPane + WordsDetailPane
│   ├── theme/                  # Palettes, Type.kt (fonts + ReadingFont), spacing/radii tokens
│   └── ui/                     # Shared widgets + LibraryLimitDialog
├── domain/
│   ├── model/                  # Parsed Book/Chapter; persisted-aggregate models LibraryBook, Bookmark,
│   │                           #   Note, SavedWord, ReadingSession, stats/*; Language, TranslationProvider,
│   │                           #   TranslationUsage, ReadingDefaults (single source of reader defaults/ranges)
│   ├── parser/                 # BookParser registry + EpubParser, Fb2Parser (fb2/ builder), MobiParser
│   │                           #   (MobiCodec/MobiChapterSplitter), HtmlChapterExtractor, util/ (FileKeys…)
│   ├── repository/             # Interfaces in DOMAIN types: Translation/BookLibrary/Entitlement/Bookmark/
│   │                           #   Note/SavedWord/ReadingSession + capability ports: ReadingPreferences,
│   │                           #   SpeechSynthesizer, TranslationUsageStats, TranslatorKeyStore, TranslatorEndpointStore
│   ├── usecase/                # ParseBookUseCase, TranslateTextUseCase, AddBookToLibraryUseCase…
│   ├── CrashReporter.kt        # crash-reporting seam (impl in data/crash)
│   └── LanguageDetector.kt     # ML Kit Language ID wrapper
├── data/
│   ├── local/                  # Room (AppDatabase/DAOs, Migrations), ReadingProgressManager,
│   │                           #   ApiKeyManager (Keystore), TranslatorEndpoints, TTS — impl the domain ports
│   ├── billing/                # BillingManager + PurchaseVerifier (Google Play Billing)
│   ├── crash/                  # FirebaseCrashReporter
│   ├── auth/ catalog/ translator/  # GoogleAuthClient, catalog clients, online-translator APIs
│   └── repository/             # *RepositoryImpl (incl. EntitlementRepositoryImpl = billing ∨ debug);
│                               #   mapper/ (@Entity↔domain toDomain()/toEntity())
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
- **Purchase-signature verification:** `PurchaseVerifier` (RSA `SHA1withRSA`) checks `Purchase.signature` against `BuildConfig.BILLING_PUBLIC_KEY` before granting premium, on both grant paths. **Fail-open** when the key is blank (dev/CI). The key is sourced from `billingPublicKey` in `keystore.properties`; a **release build-guard** in `app/build.gradle.kts` fails `bundleRelease`/`assembleRelease` if the key is blank while a keystore is present, so a signed release can't ship with the check disabled. Set it from Play Console → Monetization setup → Licensing before release.

## Catalog (free books)

`CatalogScreen` lets users discover and read free **public-domain** books with translation, no login required. Two sources behind a `CatalogSourceClient` multibinding (`di/CatalogModule`): **Project Gutenberg** (official OPDS at `gutenberg.org/ebooks/search.opds/`, parsed with Jsoup) and **Standard Ebooks**. EPUB/cover URLs are built deterministically from the id; `downloadEpub` streams the DRM-free EPUB into `filesDir/catalog/<id>.epub` and returns a `file://` Uri; `CatalogViewModel` then runs the existing `ParseBookUseCase` → `EpubParser` → `AddBookToLibraryUseCase`, so a catalog book lands in the library (and is gated by the free-tier limit) and reads like any imported file. Google Drive import is also supported (read-only, picked file). Purchased Google Play Books are intentionally **not** supported (Adobe ACS4 DRM). `HomeViewModel.openBookFromLibrary` skips the persisted-URI-permission check for `file://` (app-private) catalog/Drive downloads.

## Settings & reading customization

`SettingsScreen` is the global preferences editor. The reader's appearance/translation prefs are persisted as **single global keys** in `ReadingProgressManager`, so Settings and the reader's in-session `DisplaySettingsDialog`/`TranslatorPickerDialog` share one source of truth (no per-book scoping). Typography (typeface via `ReadingFont`, font size, line height, paragraph/letter spacing, first-line indent, justification, hyphenation) is rendered through the shared `TypographyControls`. Settings also hosts: translation-engine config (mirrors the reader's `TranslatorPickerDialog`; API keys stored via `ApiKeyManager`), clear-translation-cache, TTS rate/pitch, a **Premium** section (status + Restore purchase), a debug-only Premium toggle, and About. Bookmarks are surfaced via the reader top-bar button + an in-reader dialog.

## Supported Languages (12)

EN, RU, DE, FR, ES, IT, ZH, JA, PT, AR, KO, TR — defined in `Language.kt` enum (these are translation target languages; the UI itself is currently English-only — full localization is a 1.1 item).

## Important Patterns

- ViewModels injected via `@HiltViewModel`; Activities annotated `@AndroidEntryPoint`. Presentation injects **domain interfaces**, never `data.local.*` managers (Hilt `@Provides`-upcast in `AppModule` binds each interface to its manager).
- **Persistence models:** repository interfaces expose domain models (`LibraryBook`, `Bookmark`, `Note`, `SavedWord`, `ReadingSession`, `stats/*`); `RepositoryImpl` maps at the DAO boundary via `data/repository/mapper` extension fns (`toDomain()/toEntity()`). Room `@Entity`/projection rows never cross into `domain`.
- **Reading prefs:** `ReadingProgressManager` (behind the `ReadingPreferences` port) persists per-book progress + global reader prefs. Defaults & clamp ranges come from `domain/model/ReadingDefaults` (single source — no more drift between manager and UI state). Config building/Keystore decrypt runs off the main thread (`Dispatchers.Default`).
- **Translation cache:** `TranslationCacheEntity` keyed by `TranslationCacheKey.compute` (SHA-256 of text, not `hashCode`); on a cache hit the stored `originalText` is verified before returning (collision-proof).
- **DB migrations:** `data/local/Migrations.kt` holds `MIGRATION_1_2/2_3/3_4` (all registered); `exportSchema = true` (`app/schemas/**` committed); destructive fallback is `BuildConfig.DEBUG`-only. Migration tests in `androidTest`.
- `ParseBookUseCase` injects a `Set<BookParser>` registry (Hilt `@IntoSet` in `di/ParserModule`); `selectParser` picks the highest-`priority` match (deterministic) — adding a format is a new `BookParser` + one binding. Parse failures logged as non-fatals via `CrashReporter`. Stream reads are size-bounded (`MAX_DECOMPRESSED ≈ 300 MB`) against OOM/zip-bombs; cover/image filenames are content-addressed (`stableId`, SHA-256), not `hashCode`.
- Translator API keys are encrypted at rest with an Android Keystore key (`ApiKeyManager` behind `TranslatorKeyStore`), session-only in-memory fallback if the Keystore is unavailable; the prefs file (`translator_keys_enc`) is excluded from backup. LibreTranslate URLs reject cleartext `http://`.

## Tests

Unit tests in `app/src/test` (JUnit4 + `kotlinx-coroutines-test`, **hand-written fakes, no mock framework**): parsers/utilities (`MobiCodec`, `MobiChapterSplitter`, `Fb2DocumentBuilder`, `StreamReading`, `ParserSelector`, `FileKeys`), `TranslationPlanner`, `AddBookToLibraryUseCaseTest` (free-tier rules), and the Phase-2/3 pure units (`TranslationCacheKey`, `PurchaseVerifier`, `TranslatorUrlValidator`, entity `*MapperTest`s). Instrumented in `app/src/androidTest`: `ParserBeginningTest`, `ApiKeyManagerTest`, `MigrationTest`. Backlog in `docs/test_plan.md`. Note: ML Kit model download and Gutenberg over IPv6 don't work on the emulator — verify those flows on a real device.

## Refactor status (2026-07, all merged to `main` + pushed)

A full audit → 26-finding hardening program is **complete**. Phases 0–4 + P23, each via brainstorm → spec → plan → subagent-driven-development (per-task review + opus final review) → FF-merge. Specs/plans in `docs/superpowers/`.
- **Phase 0** — DB migration `2→3` gap + tests (data-loss fix).
- **Phase 1** — parser hardening: deterministic dispatch, OOM/zip-bomb bound, signed-`u32`/PalmDOC/cancellation, FB2 rewrite (verse/subtitles), MOBI chapter splitting, content-addressed filenames.
- **Phase 2** — SHA-256 cache key, off-main Keystore, purchase-signature verify + release build-guard, backup `billing.xml`, https-only LibreTranslate, Quick Translate note.
- **Phase 3** — Clean Architecture: domain models + mappers (P19) and capability ports (P20); **domain is now fully isolated from data**.
- **Phase 4** — polish: idiomatic tab back-stack, no `!!`, `ReadingDefaults`, logging/flow-sharing.
- **P23** — split the giant screen files (Reader/Home/Words) into cohesive same-package files.

**Deferred (non-blocking):** `ReaderViewModel` split (`TODO(architecture)` in it); prune duplicated imports in the Home/Words split files (needs ktlint/IDE optimize-imports); stale `TODO` in `ReaderScreen.kt` header (file is now ~460 lines, not 1.7k); full UI localization (1.1 item). See `memory.md` for the release-prep resume point and remaining Play Console / premium setup.

## Skills
Read and follow: ~/.claude/skills/android/SKILL.md
