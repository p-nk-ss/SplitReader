# Working memory — Mirrolit

> Resume point. Updated **2026-07-18**. Branch: **`main`** (refactor merged + pushed; `main == origin/main`).
> Earlier release-prep was on `feat/free-tier-book-limit` (fully in `main`; that branch can be deleted).

## TL;DR — where we are

- **Code:** all release-blockers done + a full 26-finding hardening refactor (Phases 0–4 + P23) **complete, merged to `main`, pushed to origin**. Unit suite green; app compiles.
- **Blocked on (unchanged):** Google Play **Developer account verification** (personal account) + the **organizational Play Console** setup. Nothing code-side blocks these.
- **Paid version:** code is done and emulator-verified; the actual purchase flow needs Play Console product + licensing key (see "Remaining — paid version").

---

## ✅ Done — refactor program (2026-07, all merged to `main`)

Full audit → 26 findings → phased fix. Each phase: brainstorm → spec → plan → subagent-driven-development
(fresh subagent per task, per-task review, **opus** final whole-branch review) → FF-merge. Specs/plans in
`docs/superpowers/{specs,plans}/`. SDD ledger: `.superpowers/sdd/progress.md` (gitignored scratch).

- **Phase 0 — DB safety:** added missing `MIGRATION_2_3` (bookmarks/notes/saved_words/reading_sessions),
  `exportSchema=true` (`app/schemas/**` committed), destructive fallback debug-only, `MigrationTest`.
- **Phase 1 — parser hardening:** deterministic parser selection (`priority`), OOM/zip-bomb bound
  (`MAX_DECOMPRESSED≈300MB`), signed-`u32`/PalmDOC-distance-0/cooperative-cancellation, FB2 rewritten to a
  pure `Fb2DocumentBuilder` (verse `<v>`/subtitles/cite, no 5000-char truncation), MOBI heading-fallback
  chapter split (`MobiChapterSplitter`), content-addressed cover/image names (`stableId`, SHA-256).
- **Phase 2 — data/security:** SHA-256 translation-cache key + source-text verify; off-main-thread
  Keystore/prefs; `PurchaseVerifier` (RSA) + **release build-guard** for `BILLING_PUBLIC_KEY`; backup
  excludes `billing.xml`; LibreTranslate rejects cleartext `http://`; Quick Translate marked unofficial.
- **Phase 3 — Clean Architecture:** domain models + `data/repository/mapper` (P19) and capability ports
  `ReadingPreferences`/`SpeechSynthesizer`/`TranslationUsageStats`/`TranslatorKeyStore`/`TranslatorEndpointStore`
  (P20). **`domain/**` now imports nothing from `data.**`.**
- **Phase 4 — polish:** idiomatic tab back-stack (`popUpTo/saveState/restoreState`); removed `!!`;
  single `ReadingDefaults` (fixed font-size range 14..30 + paragraph-spacing default 18); logged swallowed
  `SecurityException`; one shared Almanac daily-minutes subscription; off-main key writes; misc.
- **P23 — split giant screens:** ReaderScreen 2146→463 (+ ReaderPane/ReaderSelection/ReaderChrome/
  ReaderDialogs), HomeScreen 1322→310 (+ HomeCovers/HomeSections), WordsScreen 980→201 (+ WordsMasterPane/
  WordsDetailPane). Pure within-package moves, byte-identical bodies.

## ✅ Done earlier — release hardening + features (pre-refactor, in `main`)

- **Release signing** — `signingConfigs.release` reads `keystore.properties` (gitignored); unsigned fallback.
  Keystore `mirrolit-upload.jks` (gitignored, pw in user's manager `mirrolit2572`). Enable **Play App Signing**.
- **R8** — minify + shrinkResources on; proguard keeps Gson/Retrofit DTOs + Crashlytics SourceFile. AAB ~42 MB.
- **ApiKeyManager** — AES-256-GCM via Android Keystore (dropped security-crypto alpha).
- **Crashlytics** — release-only collection; R8 mapping uploads; `CrashReporter` seam.
- **Google Play Billing** — `BillingManager` (one-time `premium_unlimited`), `EntitlementRepositoryImpl` =
  billing∨debug, `PremiumViewModel`, Upgrade in library-limit dialog + Restore in Settings. Emulator-verified
  (connect/restore/events); **purchase sheet itself untestable until the product exists in Play Console**.
- **Backup** — excluded translator secrets, `entitlement.xml`, and (Phase 2) `billing.xml`.
- **Branding** — Mirrolit adaptive launcher icon + nav-rail mark.
- **Store assets + docs** — `docs/store-assets/` (feature graphic, 5 screenshots, full/short descriptions),
  `ic_launcher-playstore.png` (512), `docs/{release_plan,play_console_release,test_plan,privacy_policy}.md`.
- **Privacy policy HOSTED ✅** — https://p-nk-ss.github.io/mirrolit-legal/ (separate public repo
  `github.com/p-nk-ss/mirrolit-legal`; source `C:\Users\pankass\mirrolit-legal\index.html`; contact
  `pankaz6jha@gmail.com`).

---

## ⏳ Remaining — paid version (premium) · Play Console + key, code done

1. **Payments profile** — Setup → Payments profile.
2. **Create + Activate** in-app product **`premium_unlimited`** (one-time, INAPP). Id must match
   `BillingManager.PREMIUM_PRODUCT_ID`. Until it exists, the real purchase sheet can't be tested.
3. **`BILLING_PUBLIC_KEY`** — put the base64 licensing key (Play Console → Monetization setup → Licensing)
   into `keystore.properties` as `billingPublicKey`. **A signed `bundleRelease` now FAILS if it's blank**
   (P15 build-guard) — so this is a hard pre-release step. `PurchaseVerifier` fails-open only in dev.
4. **License testers** (incl. own account) + run the **purchase + restore** checklist on Internal testing
   (the part that couldn't be verified locally).

## ⏳ Remaining — organizational (not paid) · Play Console

- **Developer account verification** — personal/individual (name **Emberleaf**). ⚠️ Personal accounts need a
  **closed test: 20+ testers, 14 days** before Production access → plan ~2–3 weeks; start gathering testers.
- **Create app** `io.mirrolit.app` (Free); enable Play App Signing.
- **App content** — privacy URL (hosted ✅), Data Safety (email via Firebase Auth, crash logs, optional
  online-translator text to 3rd parties, books/stats local, deletion=Yes), content rating (IARC→Everyone),
  target audience, No ads.
- **Store listing** — paste descriptions, upload icon/feature-graphic/5 screenshots (public-domain only).
- Build AAB `./gradlew :app:bundleRelease` → Internal testing → Closed (20+/14d) → Production staged rollout.
- Follow `docs/play_console_release.md`.

## ⏳ Remaining — code (non-blocking, optional)

- **`ReaderViewModel` split** — deferred from P23 (`TODO(architecture)` in `ReaderViewModel.kt`); logic/state,
  riskier than the pure composable moves, so left as a separate future project.
- **Prune duplicated imports** in the Home/Words split files (P23 used a `sed` byte-move that copied full
  import blocks — warnings only). Needs ktlint config or IDE "optimize imports".
- **Stale TODO** in `ReaderScreen.kt` header still says "~1.7k lines" — file is now ~463 lines; remove it.
  (The equivalent WordsScreen TODO was already removed.)
- **Full UI localization** — 1.1 item (target languages are for translation; UI is English-only).
- Test backlog: `docs/test_plan.md`.

---

## Key facts / gotchas

- applicationId **`io.mirrolit.app`**; namespace stays `com.example.splitreader` (internal, fine).
- versionCode `1` / versionName `1.0` — bump versionCode for every future upload.
- `FREE_BOOK_LIMIT = 3` (`AddBookToLibraryUseCase`). Premium removes it.
- `premium_unlimited` product id must match `BillingManager.PREMIUM_PRODUCT_ID`.
- Emulator can't download ML Kit models (SSL/IPv6) or reach Gutenberg over IPv6 — verify on a real device.
- Debug-only Premium toggle lives under `BuildConfig.DEBUG` in Settings (absent in release).
- `git commit` trailer used throughout the refactor: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
