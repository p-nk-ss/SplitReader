# Working memory — Mirrolit release prep

> Resume point for the Play Store launch. Updated 2026-06-28. Branch: `feat/free-tier-book-limit`.

## Where we are right now

**Blocked on:** Google Play **Developer account verification** (user is registering; will return once approved).

All **code** release-blockers are done and verified on the emulator. Remaining work is
**organizational** (Play Console) — see `docs/play_console_release.md`.

## Developer account registration (in progress)

- Account type: **Personal / Individual** (NOT organization — no D-U-N-S needed).
- **Developer name chosen: `Emberleaf`** (umbrella/studio brand, not tied to one product; changeable later).
- "Experience" field: filled with the Mirrolit description (EN + RU drafts were provided in chat).
- Website field: user's **CV/QA site** (internal field, not shown to Play users).
- ⚠️ **Personal-account rule:** before Production access, must run a **closed test with ≥20 testers for ≥14 days**. Internal testing is available immediately. → plan ~2–3 weeks to Production; start gathering 20 testers now.

## Done this session (16 commits on the branch)

Release hardening + features:
- **Release signing** — `signingConfigs.release` reads `keystore.properties` (gitignored); falls back to unsigned if absent. Keystore: `mirrolit-upload.jks` (gitignored), password in user's manager (`mirrolit2572`). Enable **Play App Signing** in console.
- **R8** — `isMinifyEnabled` + `isShrinkResources` on; proguard keeps Gson/Retrofit DTOs + Crashlytics SourceFile. AAB ~42 MB.
- **security-crypto (alpha) removed** — `ApiKeyManager` now AES-256-GCM via Android Keystore (was EncryptedSharedPreferences). 6 instrumented tests pass.
- **Crashlytics** — plugin + dep; collection gated to release (`!BuildConfig.DEBUG`); R8 mapping uploads; `CrashReporter` seam wired into ParseBookUseCase + CatalogViewModel.
- **Google Play Billing** — `BillingManager` (one-time product **`premium_unlimited`**), `EntitlementRepositoryImpl` = billing∨debug, `PremiumViewModel`, Upgrade in library-limit dialog (Home/Catalog) + Restore in Settings. Verified on emulator: connects to Play, restore queries Play, events→toasts, no crash. **Purchase sheet itself untestable until the product exists in Play Console.**
- **Backup** — excluded `entitlement.xml` + translator secrets from cloud-backup/device-transfer.
- **i18n** — extracted Almanac + Reader hardcoded strings (full UI localization deferred to 1.1).
- **Branding** — Mirrolit logo as adaptive launcher icon (brown `#240B01` bg + logo fg at 0.64 so the wordmark clears the circular mask) + nav-rail mark; removed dead `BrandIcon`. Source: `MiroLit_logo.png`, generator: `generate_icons.py`.
- **Tests** — `AddBookToLibraryUseCaseTest` (free-tier chokepoint, 10 cases) + `ApiKeyManagerTest`. `coroutines-test` added to unit tests. Full unit suite green (~40 tests).

Store assets + docs:
- `docs/store-assets/` — `feature-graphic.png` (1024×500), 5 real screenshots (01-library, 02-reading-stats, 03-translation-engines, 04-appearance, 05-reader = real EN→UK side-by-side hero), `full-description.txt` (<4000), `short-description.txt` (<80, primary: "Read in two languages at once. Original + live translation, on-device."), README.
- `app/src/main/ic_launcher-playstore.png` — 512×512 listing icon.
- `docs/release_plan.md` — step-by-step; all code blockers checked off.
- `docs/play_console_release.md` — the Play Console runbook.
- `docs/test_plan.md` — prioritized test backlog.
- `docs/privacy_policy.md` — source draft.

## Privacy policy — HOSTED ✅

- Live URL: **https://p-nk-ss.github.io/mirrolit-legal/** (HTTP 200, verified).
- Hosted via GitHub Pages from a **separate public repo** `github.com/p-nk-ss/mirrolit-legal` (app source stays private). Source file: `C:\Users\pankass\mirrolit-legal\index.html`.
- To update: edit that index.html → `git -C C:/Users/pankass/mirrolit-legal commit -am ... && git push` → rebuilds in ~1 min.
- Contact in policy: `pankaz6jha@gmail.com`.

## Next steps when the account is approved

Follow `docs/play_console_release.md`. Order:
1. **Create app** `io.mirrolit.app`, name "Mirrolit", Free; enable **Play App Signing**.
2. **App content**: paste privacy URL (above), fill **Data Safety** (email via Firebase Auth, crash logs via Crashlytics, optional online-translator text shared with 3rd parties, books/stats local-only, deletion=Yes), content rating (IARC → Everyone), target audience, "No ads".
3. **Store listing**: paste `full-description.txt` + `short-description.txt`, upload icon (512), feature graphic, the 5 screenshots. (Public-domain content only — screenshots use Alice in Wonderland.)
4. **Set up Payments profile** (Setup → Payments profile) then create in-app product **`premium_unlimited`** (one-time, INAPP) and **Activate** it. Add **license testers** (incl. own account) under Setup → License testing.
5. Build AAB: `./gradlew :app:bundleRelease` → upload to **Internal testing**; opt-in on a device via Play; run the paid purchase + restore checklist (the part that couldn't be tested locally).
6. Personal-account gate: run **Closed testing, 20+ testers, 14 days** → apply for Production → staged rollout.

## Key facts / gotchas

- applicationId **`io.mirrolit.app`**; namespace stays `com.example.splitreader` (internal package, fine).
- versionCode `1` / versionName `1.0` — bump versionCode for every future upload.
- `FREE_BOOK_LIMIT = 3` (`AddBookToLibraryUseCase`). Premium removes it.
- `premium_unlimited` product id must match `BillingManager.PREMIUM_PRODUCT_ID`.
- Emulator can't download ML Kit models (SSL/IPv6) or reach gutenberg over IPv6 — those flows verify only on a real device/network.
- Debug-only Premium toggle lives under `BuildConfig.DEBUG` in Settings (absent in release).
