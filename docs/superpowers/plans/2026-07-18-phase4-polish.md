# Phase 4 — Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining quality tech-debt (P21, P22, P24, P25 + cheap follow-ups) — idiomatic tab back-stack, no `!!`, a single `ReadingDefaults` constant source, logged swallowed exceptions, one shared Almanac DB subscription — with no behavior change beyond two deliberate default fixes.

**Architecture:** Small isolated edits. The one substantive piece is `ReadingDefaults` (a pure domain constants object) that `ReadingProgressManager`, the UI state classes, the `coerceIn` clamps, and the sliders reference so defaults/ranges live in one place.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose + Navigation. Mostly compile + existing tests + review (like P14); no new unit tests unless noted.

## Global Constraints

- Only TWO behavior changes are allowed, both in P24: text-size range unified to `14f..30f` everywhere it is used; fresh-install `paragraphSpacing` default `8f → 18f`. Every other constant moves into `ReadingDefaults` preserving its EXACT current value.
- `ReadingDefaults` lives in `domain/model/` — pure Kotlin, no `android.*` (data may depend on domain).
- P23 (splitting giant files) is OUT of scope (deferred).
- Commit trailer on every commit: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- Verify per task: `:app:compileDebugKotlin :app:testDebugUnitTest` green.

---

## File Structure

- `presentation/navigation/SplitReaderNavHost.kt` — **modify** (P21).
- `presentation/home/HomeScreen.kt` — **modify** (P22).
- `domain/model/ReadingDefaults.kt` — **new** (P24).
- `data/local/ReadingProgressManager.kt`, `presentation/reader/{ReaderUiState,ReaderViewModel}.kt`, `presentation/settings/SettingsViewModel.kt`, `presentation/ui/SettingsControls.kt` — **modify** (P24 wiring).
- `presentation/home/HomeViewModel.kt`, `presentation/almanac/AlmanacViewModel.kt` — **modify** (P25).
- `presentation/reader/ReaderViewModel.kt`, `presentation/settings/SettingsViewModel.kt`, `data/local/TranslatorEndpoints.kt`, `domain/catalog/CatalogSourceClient.kt`, `domain/repository/DriveRepository.kt` — **modify** (cleanup).

---

## Task 1: P21 — Idiomatic tab back-stack

**Files:** Modify `presentation/navigation/SplitReaderNavHost.kt`.

- [ ] **Step 1: Add the helper + import**

Add import: `import androidx.navigation.NavGraph.Companion.findStartDestination`.
Add a private helper (top-level in the file, below imports):
```kotlin
/** Navigates to a top-level tab with saved-state restore and a single instance on the back stack. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
```

- [ ] **Step 2: Use it for the 5 tab callbacks**

Replace lines 77-81 so each tab uses the helper:
```kotlin
        onNavigateToHome = { navController.navigateToTab(HOME_ROUTE) },
        onNavigateToCatalog = { navController.navigateToTab(CATALOG_ROUTE) },
        onNavigateToAlmanac = { navController.navigateToTab(ALMANAC_ROUTE) },
        onNavigateToWords = { navController.navigateToTab(WORDS_ROUTE) },
        onNavigateToSettings = { navController.navigateToTab(SETTINGS_ROUTE) },
```
Leave `onNavigateToAccount` (lines 82-85) unchanged — profile/auth is not a tab, keep its `launchSingleTop`-only navigate.

- [ ] **Step 3: Compile + test**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` → BUILD SUCCESSFUL.

- [ ] **Step 4: Commit** — `fix(nav): idiomatic tab back-stack (popUpTo/saveState/restoreState) (P21)` with the trailer.

---

## Task 2: P22 — Remove `!!` in HomeScreen

**Files:** Modify `presentation/home/HomeScreen.kt`.

- [ ] **Step 1: lastBook block (lines ~305-314)**

Replace:
```kotlin
        if (uiState.lastBook != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueReadingHero(
                    book = uiState.lastBook!!,
                    minutesToday = uiState.minutesToday,
                    onContinue = { onOpenFromLibrary(uiState.lastBook!!.uri) },
                )
            }
        }
```
with:
```kotlin
        val lastBook = uiState.lastBook
        if (lastBook != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueReadingHero(
                    book = lastBook,
                    minutesToday = uiState.minutesToday,
                    onContinue = { onOpenFromLibrary(lastBook.uri) },
                )
            }
        }
```

- [ ] **Step 2: coverBitmap block (lines ~1154-1160)**

Replace the `if (coverBitmap != null) { Image(bitmap = coverBitmap!!, …) }` with a captured local:
```kotlin
        val bitmap = coverBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
```
> Verify the exact surrounding lines in the file; only the null-check + the two `!!` usages change. If `coverBitmap` is a composable parameter/`val` already, the local capture is still correct for smart-cast.

- [ ] **Step 3: Confirm no `!!` remains on these**

Run: `grep -n "lastBook!!\|coverBitmap!!" app/src/main/java/com/example/splitreader/presentation/home/HomeScreen.kt || echo clean` → `clean`.

- [ ] **Step 4: Compile + test** → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit** — `refactor(home): replace !! with smart-cast locals (P22)` with the trailer.

---

## Task 3: P24 — Single `ReadingDefaults` source

**Files:** Create `domain/model/ReadingDefaults.kt`; modify `data/local/ReadingProgressManager.kt`, `presentation/reader/ReaderUiState.kt`, `presentation/reader/ReaderViewModel.kt`, `presentation/settings/SettingsViewModel.kt`, `presentation/ui/SettingsControls.kt`.

- [ ] **Step 1: Create the constants object**

`domain/model/ReadingDefaults.kt`:
```kotlin
package com.example.splitreader.domain.model

/**
 * Single source of truth for reader preference defaults and clamp ranges, so the persistence layer
 * ([data.local.ReadingProgressManager]) and the UI states cannot drift apart (they used to disagree
 * on the paragraph-spacing default and the text-size range).
 */
object ReadingDefaults {
    // Defaults
    const val LINE_HEIGHT = 1.5f
    const val SPLIT_RATIO = 0.5f
    const val SHOW_TRANSLATION = true
    const val SHOW_ILLUSTRATIONS = true
    const val HORIZONTAL_MARGIN = 12f
    const val TEXT_SIZE = 16f
    const val READING_FONT = "SERIF"
    const val PARAGRAPH_SPACING = 18f // was 8f in the persistence layer — 18f is the UI-designed value
    const val LETTER_SPACING = 0f
    const val TEXT_INDENT = 0f
    const val JUSTIFY_TEXT = true
    const val TTS_RATE = 1.0f
    const val TTS_PITCH = 1.0f
    const val READER_THEME = "DEFAULT"
    const val NAVIGATION_SIDE_LEFT = false

    // Clamp ranges (used by coerceIn and matching sliders)
    val TEXT_SIZE_RANGE = 14f..30f // unified (Settings used 14..24, Reader used 14..30)
    val SPLIT_RATIO_RANGE = 0.3f..0.7f
    val HORIZONTAL_MARGIN_RANGE = 4f..32f
    val LINE_HEIGHT_RANGE = 1.1f..2.5f
    val LETTER_SPACING_RANGE = 0f..2f
    val TEXT_INDENT_RANGE = 0f..48f
    val PARAGRAPH_SPACING_RANGE = 4f..48f
    val TTS_RATE_RANGE = 0.5f..2.0f
    val TTS_PITCH_RANGE = 0.5f..2.0f
}
```

- [ ] **Step 2: Wire `ReadingProgressManager` getters to the defaults**

In `data/local/ReadingProgressManager.kt` add `import com.example.splitreader.domain.model.ReadingDefaults` and replace the literal fallbacks in the getters with the constants (values IDENTICAL except `paragraphSpacing`, which changes `8f → ReadingDefaults.PARAGRAPH_SPACING` = 18f — the intended fix):
- `getLineHeightMultiplier()` `1.5f` → `ReadingDefaults.LINE_HEIGHT`
- `getSplitRatio()` `0.5f` → `ReadingDefaults.SPLIT_RATIO`
- `getShowTranslation()` `true` → `ReadingDefaults.SHOW_TRANSLATION`
- `getShowIllustrations()` `true` → `ReadingDefaults.SHOW_ILLUSTRATIONS`
- `getHorizontalMargin()` `12f` → `ReadingDefaults.HORIZONTAL_MARGIN`
- `getTextSize()` `16f` → `ReadingDefaults.TEXT_SIZE`
- `getReadingFontName()` `"SERIF"` → `ReadingDefaults.READING_FONT`
- `getParagraphSpacing()` `8f` → `ReadingDefaults.PARAGRAPH_SPACING`  ← the fix
- `getLetterSpacing()` `0f` → `ReadingDefaults.LETTER_SPACING`
- `getTextIndent()` `0f` → `ReadingDefaults.TEXT_INDENT`
- `getJustifyText()` `true` → `ReadingDefaults.JUSTIFY_TEXT`
- `getTtsRate()` `1.0f` → `ReadingDefaults.TTS_RATE`
- `getTtsPitch()` `1.0f` → `ReadingDefaults.TTS_PITCH`
- `getReaderThemeName()` `"DEFAULT"` → `ReadingDefaults.READER_THEME`
- `isNavigationLeft()` `false` → `ReadingDefaults.NAVIGATION_SIDE_LEFT`
Leave `getTargetLanguage()` (uses `Language.ENGLISH.code`) as-is.

- [ ] **Step 3: Wire UI-state field defaults**

Replace the literal defaults in the state data classes with `ReadingDefaults.*` (values identical, incl. `paragraphSpacing = ReadingDefaults.PARAGRAPH_SPACING`):
- `presentation/reader/ReaderUiState.kt` (both the `Success` state and any nested defaults at lines ~24, ~55).
- `presentation/reader/ReaderViewModel.kt` `InternalState` defaults (~line 98 and neighbors).
- `presentation/settings/SettingsViewModel.kt` `SettingsUiState` defaults (~line 42 and neighbors).
Add the `ReadingDefaults` import to each. Change only the default *values* — keep field names/types.

- [ ] **Step 4: Wire the `coerceIn` clamps to the ranges**

In `SettingsViewModel.kt` and `ReaderViewModel.kt`, replace the two-arg `coerceIn(a, b)` clamp calls for reader prefs with the range form `coerceIn(ReadingDefaults.X_RANGE)`:
- splitRatio `coerceIn(0.3f, 0.7f)` → `coerceIn(ReadingDefaults.SPLIT_RATIO_RANGE)`
- horizontalMargin `coerceIn(4f, 32f)` → `coerceIn(ReadingDefaults.HORIZONTAL_MARGIN_RANGE)`
- **textSize** `coerceIn(14f, 24f)` (Settings) and `coerceIn(14f, 30f)` (Reader) → BOTH `coerceIn(ReadingDefaults.TEXT_SIZE_RANGE)` (= 14..30; the Settings clamp widening 24→30 is the intended fix)
- lineHeight `coerceIn(1.1f, 2.5f)` → `coerceIn(ReadingDefaults.LINE_HEIGHT_RANGE)`
- letterSpacing `coerceIn(0f, 2f)` → `coerceIn(ReadingDefaults.LETTER_SPACING_RANGE)`
- textIndent `coerceIn(0f, 48f)` → `coerceIn(ReadingDefaults.TEXT_INDENT_RANGE)`
- paragraphSpacing `coerceIn(4f, 48f)` → `coerceIn(ReadingDefaults.PARAGRAPH_SPACING_RANGE)`
- ttsRate `coerceIn(0.5f, 2.0f)` → `coerceIn(ReadingDefaults.TTS_RATE_RANGE)`
- ttsPitch `coerceIn(0.5f, 2.0f)` → `coerceIn(ReadingDefaults.TTS_PITCH_RANGE)`
> `Float.coerceIn(range: ClosedFloatingPointRange<Float>)` is the correct overload. Do NOT touch the many `Int` `coerceIn(0, text.length …)` calls in ReaderScreen/TranslationPlanner/etc. — those are index guards, unrelated to reader prefs.

- [ ] **Step 5: Fix the text-size slider range (the P24 UI fix)**

In `presentation/ui/SettingsControls.kt:129`, change the text-size slider `valueRange = 14f..24f` → `valueRange = ReadingDefaults.TEXT_SIZE_RANGE` (add the import). This is the flagged fix (slider now matches the reader's 14..30).
> Leave the OTHER sliders' literal `valueRange`s as they are (e.g. the line-height slider `1.20f..2.00f` intentionally differs from its clamp and is NOT part of the P24 finding). Do not widen them.

- [ ] **Step 6: Compile + test**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` → BUILD SUCCESSFUL. Verify no divergent literals remain for the two flagged constants:
`grep -rn "14f..24f\|paragraph_spacing\", 8f\|getFloat(\"paragraph_spacing\", 8" app/src/main/java/com/example/splitreader || echo clean` → `clean`.

- [ ] **Step 7: Commit** — `refactor(reader): single ReadingDefaults source; unify text-size range 14..30 and paragraph-spacing default 18 (P24)` with the trailer.

---

## Task 4: P25 — Log swallowed exception + share Almanac flow

**Files:** Modify `presentation/home/HomeViewModel.kt`, `presentation/almanac/AlmanacViewModel.kt`.

- [ ] **Step 1: Log the swallowed SecurityException (HomeViewModel ~line 130-132)**

Add `import android.util.Log`. Add a companion `TAG` if none exists (`private const val TAG = "HomeViewModel"`). Change:
```kotlin
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
```
to:
```kotlin
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.w(TAG, "takePersistableUriPermission denied for $uri", e)
            }
```

- [ ] **Step 2: Share the daily-minutes flow (AlmanacViewModel lines 42-54)**

Replace the three separate `flatMapLatest { observeDailyMinutes(...) }` (`dailyMinutes`, `rangeMinutes`, `rangePages`) with one shared upstream + derived flows:
```kotlin
    private val dailyMinutesShared: StateFlow<List<DailyMinutes>> = selectedRange
        .flatMapLatest { range -> sessionRepository.observeDailyMinutes(range.daysBack) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dailyMinutes: StateFlow<List<DailyMinutes>> = dailyMinutesShared

    val rangeMinutes: StateFlow<Int> = dailyMinutesShared
        .map { days -> days.sumOf { it.minutes } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val rangePages: StateFlow<Int> = dailyMinutesShared
        .map { days -> days.sumOf { it.minutes * 2 } } // rough approx: 2 pages/min
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
```
Leave `rangeWords`, `timeByBook`, `timeByLang` unchanged (different sources).

- [ ] **Step 3: Compile + test** → BUILD SUCCESSFUL.

- [ ] **Step 4: Commit** — `refactor(stats): log denied URI permission; share Almanac daily-minutes subscription (P25)` with the trailer.

---

## Task 5: Cleanup follow-ups (P14-tail, P17-tail, KDoc links)

**Files:** Modify `presentation/settings/SettingsViewModel.kt`, `presentation/reader/ReaderViewModel.kt`, `data/local/TranslatorEndpoints.kt`, `domain/catalog/CatalogSourceClient.kt`, `domain/repository/DriveRepository.kt`.

- [ ] **Step 1: P14-tail — key/endpoint writes off the main thread**

In `SettingsViewModel.configureProvider` and `clearProvider` (and the equivalent handlers in `ReaderViewModel`), the `apiKeyManager.setKey(...)` / `translatorEndpoints.setSecondary(...)` writes run on the caller (main) thread on a user tap. Wrap the write + the subsequent config refresh in ONE `viewModelScope.launch(Dispatchers.Default) { … }` so the Keystore/prefs write happens off-main AND still happens-before the config rebuild. Example shape for `configureProvider`:
```kotlin
    fun configureProvider(provider: TranslationProvider, key: String?, secondary: String?) {
        viewModelScope.launch(Dispatchers.Default) {
            if (key != null) apiKeyManager.setKey(provider, key)
            if (provider.secondaryLabel != null && secondary != null) translatorEndpoints.setSecondary(provider, secondary)
            val cfg = buildTranslatorConfig(_state.value.translatorProvider)
            _state.update { if (it.translatorProvider == provider) it.copy(translatorConfig = cfg) else it }
        }
    }
```
> Read the actual current bodies first (they call `refreshTranslatorConfig(...)`). Preserve the provider-match guard from Phase 2 and the write→refresh ordering; do the writes and the rebuild inside the SAME `Default` coroutine (do not leave the write on main and only move the rebuild). `Dispatchers` is already imported in both VMs from Phase 2. Keep method signatures unchanged.

- [ ] **Step 2: P17-tail — log rejected cleartext URL**

In `data/local/TranslatorEndpoints.kt` `setLibreTranslateBaseUrl`, when `normalizeLibreUrl(url)` returns `UrlResult.Invalid`, add a log instead of silently dropping. Add `import android.util.Log` + a `TAG`, and in the `Invalid` branch:
```kotlin
        when (val result = normalizeLibreUrl(url)) {
            is UrlResult.Valid -> prefs.edit().putString(KEY_LIBRE, result.url).apply()
            is UrlResult.Invalid -> Log.w(TAG, "Rejected LibreTranslate URL: ${result.reason}")
        }
```
> Match the file's actual current structure (from Phase 2 it persists only on `Valid`); keep that behavior, only add the `Log.w` on `Invalid`.

- [ ] **Step 3: KDoc doc-links — drop the `data.*` FQN reference**

- `domain/catalog/CatalogSourceClient.kt:10`: reword the KDoc so it does not link `[com.example.splitreader.data.repository.CatalogRepositoryImpl]` — replace the bracketed FQN with plain text (e.g. "the catalog repository implementation").
- `domain/repository/DriveRepository.kt:8`: same for `[com.example.splitreader.data.auth.DriveAuthClient]` → plain text (e.g. "the Drive auth client").
Comments only — no code change.

- [ ] **Step 4: Compile + test** → BUILD SUCCESSFUL. Confirm domain KDoc no longer references `data.*` FQNs:
`grep -rn "\[com.example.splitreader.data" app/src/main/java/com/example/splitreader/domain || echo clean` → `clean`.

- [ ] **Step 5: Commit** — `chore: off-main key writes (P14-tail); log rejected URL (P17-tail); domain KDoc no data links` with the trailer.

---

## Definition of Done (maps to spec §5)

1. **P21** — the 5 tab callbacks use `navigateToTab` (popUpTo start + saveState/launchSingleTop/restoreState); account nav unchanged. *(Task 1)*
2. **P22** — no `lastBook!!`/`coverBitmap!!` in HomeScreen; smart-cast via locals. *(Task 2)*
3. **P24** — `ReadingDefaults` is the single source; the persistence layer and UI states reference it; text-size range is `14f..30f` everywhere (incl. its slider); fresh-install `paragraphSpacing` default is `18f`; no other constant changed value. *(Task 3)*
4. **P25** — the `SecurityException` is logged; `AlmanacViewModel` holds ONE `observeDailyMinutes` subscription feeding `dailyMinutes`/`rangeMinutes`/`rangePages`. *(Task 4)*
5. **Cleanup** — key/endpoint writes run on `Dispatchers.Default`; a rejected `http://` LibreTranslate URL is logged; domain KDoc has no `data.*` doc-links. *(Task 5)*
6. Behavior unchanged except the two P24 default fixes; `:app:testDebugUnitTest` green; `:app:compileDebugKotlin` succeeds. *(All tasks)*
