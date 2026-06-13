# Google Play Release Audit Plan

> **Audit, not implementation.** This plan enumerates audit domains, the exact files each domain inspects, what to look for, success criteria, and complexity. Phases 2–5 (parallel audit → deep diagnosis → fix review → verification) execute only after this plan is confirmed.

**Goal:** Determine whether SplitReader (`com.example.splitreader`, versionCode 1) is releasable on Google Play, and produce CRITICAL/WARNING/PASS findings with concrete fixes.

**Scope:** Single-module Compose app. No ads SDK, no billing SDK, no analytics SDK observed in `app/build.gradle.kts`. Network = OkHttp/Retrofit to Project Gutenberg + 4 cloud translation providers; on-device ML Kit translation/language-id.

**Baseline facts already gathered (pre-plan recon):**
- `targetSdk=36`, `compileSdk=36`, `minSdk=26` — meets Play's API-level floor.
- Release build: `isMinifyEnabled=false` (no R8 shrink/obfuscation).
- `versionCode=1`, `versionName="1.0"`.
- Manifest permissions: `INTERNET`, `READ_EXTERNAL_STORAGE` (maxSdk 32), `READ_MEDIA_DOCUMENT_VISUAL_USER_SELECTED`.
- `allowBackup=true` with `backup_rules.xml` + `data_extraction_rules.xml`.
- No `network_security_config.xml` referenced.
- No `FileProvider` declared; catalog downloads use app-private `filesDir`.
- `androidx.security:security-crypto:1.1.0-alpha06` present (API-key encryption).
- `applicationId = com.example.splitreader` (placeholder `com.example.*`).

---

## Audit Domain Checklist

### 1. MANIFEST — complexity: S
- [ ] **Files:** `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`, merged manifest under `app/build/intermediates/merged_manifest/`.
- [ ] **Look for:** permissions declared vs actually used; `android:exported` correctness on every component; `allowBackup` / data-extraction posture; presence/absence of a Privacy Policy mechanism; `<queries>` correctness; debuggable flag in release; missing `applicationId` rename off `com.example`.
- [ ] **Success:** every permission maps to a real call site; all exported components intentional; backup rules don't export secrets; `com.example` placeholder flagged (Play rejects `com.example.*`).

### 2. SDK_COMPAT — complexity: S
- [ ] **Files:** `app/build.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`.
- [ ] **Look for:** `targetSdk>=34` (have 36 ✓); `versionCode` strategy; `isMinifyEnabled=false` impact; 64-bit (pure-Kotlin/no NDK → auto OK, confirm no native libs); deprecated/removed APIs at SDK 36; signing config for release (none in gradle → flag).
- [ ] **Success:** confirm API floor met; flag missing release signing config; flag no-shrink as size/obfuscation warning, not blocker.

### 3. SECURITY — complexity: L
- [ ] **Files:** `data/local/ApiKeyManager.kt`, `data/local/TranslatorEndpoints.kt`, all `data/translator/**` + `data/translator/api/**`, `data/catalog/GutenbergOpdsApi.kt`, `di/TranslatorModule.kt`, `di/CatalogModule.kt`, `local.properties`, `gradle.properties`, any `res/values/*.xml` strings.
- [ ] **Look for:** hardcoded API keys/tokens/secrets; cleartext HTTP URLs (esp. LibreTranslate default endpoint); OkHttp logging interceptor leaking bodies in release; missing cert pinning (informational); how user-entered API keys are stored (EncryptedSharedPreferences expected).
- [ ] **Success:** zero hardcoded secrets in VCS; all base URLs HTTPS or cleartext justified+scoped; logging interceptor gated to debug.

### 4. PRIVACY — complexity: L
- [ ] **Files:** all `*.kt` for `Log.` calls; `LanguageDetector.kt`; translation providers (text sent off-device); `data/local/*Manager.kt`, Room entities under `data/local/*Entity.kt`; `ReadingProgressManager.kt`, `TranslationUsageTracker.kt`.
- [ ] **Look for:** PII collection (none expected — no accounts); IMEI/MAC/SERIAL/Advertising-ID usage; book text + API keys written to logs; what leaves the device (book text → cloud translators) and whether that needs Data Safety disclosure; encryption-at-rest of cached translations/keys.
- [ ] **Success:** identify every off-device data flow for the Data Safety form; no device identifiers; no sensitive data in logcat.

### 5. BILLING — complexity: S
- [ ] **Files:** `app/build.gradle.kts` deps; grep for `billing`, `BillingClient`, `purchase`, `sku`, paywall code.
- [ ] **Look for:** any monetization. Project memory mentions "freemium" intent — verify whether any payment path exists; if a non-Play payment system exists it's CRITICAL.
- [ ] **Success:** confirm no billing present (so no Play Billing requirement triggered), or flag any alternative payment.

### 6. BACKGROUND — complexity: S
- [ ] **Files:** grep `WakeLock`, `AlarmManager`, `JobScheduler`, `WorkManager`, `Service`, `ForegroundService`; `TextToSpeechManager.kt`; manifest.
- [ ] **Look for:** wakelocks, exact alarms, foreground services, battery-optimization-exemption requests (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
- [ ] **Success:** confirm no background-execution policy surface (expected clean); note TTS lifecycle correctness.

### 7. STORAGE — complexity: M
- [ ] **Files:** manifest permissions; `HomeViewModel.kt`, `CatalogViewModel.kt`, `CatalogRepositoryImpl.kt`, `ParseBookUseCase.kt`, parsers reading content URIs; any `getExternalStorage`/`MediaStore`/`FileProvider` usage.
- [ ] **Look for:** scoped-storage compliance on API 29+; `READ_EXTERNAL_STORAGE maxSdkVersion=32` correctness; `READ_MEDIA_DOCUMENT_VISUAL_USER_SELECTED` justification (is it actually used? photo-picker perm on a book reader is suspicious); SAF/content-URI persisted-permission handling; absence of `WRITE_EXTERNAL_STORAGE` (good).
- [ ] **Success:** justify each storage permission or flag for removal; confirm catalog writes stay in app-private dir.

### 8. CONTENT_ADS — complexity: M
- [ ] **Files:** `data/catalog/**`, `CatalogRepositoryImpl.kt`, `HtmlChapterExtractor.kt`, Gutenberg trademark-strip logic; grep for `AdMob`, `ads`, `MobileAds`.
- [ ] **Look for:** ads SDK (expected none); UGC surface (none — content is user files + Gutenberg public domain); Project Gutenberg trademark/license compliance for commercial distribution; COPPA/target-audience (not a kids app); content rating implications of arbitrary user-imported books.
- [ ] **Success:** confirm no ads; confirm trademark-strip covers Play listing risk; note content-rating/UGC stance for the listing questionnaire.

---

## Phase Gates (run after Phase 2 completes)
- [ ] **Phase 3 — Deep diagnosis:** for each ❌ CRITICAL: root cause, all occurrences, concrete code fix, regression risk.
- [ ] **Phase 4 — Fix review:** correctness/completeness of each fix; cross-check against the cited Google Play policy.
- [ ] **Phase 5 — Verification:** every checklist item above resolved; Data Safety checklist generated; Release Readiness Score computed; no fix introduces a new issue.

## Complexity Summary
| Domain | Complexity |
|--------|-----------|
| Manifest | S |
| SDK_Compat | S |
| Security | L |
| Privacy | L |
| Billing | S |
| Background | S |
| Storage | M |
| Content_Ads | M |
