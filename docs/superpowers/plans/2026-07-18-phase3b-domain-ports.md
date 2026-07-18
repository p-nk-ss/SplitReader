# Phase 3B — Domain Interfaces for Data Managers (P20) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove presentation's (and the last use-case's) dependency on concrete `data.local.*` managers by introducing domain interfaces the managers implement (P20), one capability per task — completing Phase 3 (domain fully clean of `data.**`).

**Architecture:** Per capability: declare a domain interface in `domain/repository/`; the existing manager stays in `data.local` and gains `: Interface` with `override` on the mirrored members; Hilt provides the interface via a `@Provides` upcast in `AppModule` (`fun provideX(impl: Manager): Interface = impl`); consumers inject the interface. Interfaces expose only what presentation needs; data-only methods stay concrete on the class. Pure refactor — no behavior change.

**Tech Stack:** Kotlin 2.0.21, Hilt 2.52, JUnit4 (JVM). Thin interface extraction — mostly no new tests; acceptance is compile + existing tests + grep gates (like P14).

## Global Constraints

- Domain interfaces in `domain/repository/`, NO `androidx.*`/`data.*` imports beyond domain models.
- Managers keep their location in `data.local`, implement the interface, keep data-only methods concrete.
- Hilt binding = `@Provides @Singleton` upcast in `AppModule` (object module, matches existing style) — the manager remains `@Inject`-constructed/`@Singleton`; the provider only upcasts.
- Presentation injects interfaces; the manager's public API and behavior are unchanged.
- Commit trailer on every commit: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- Verify per task: `:app:compileDebugKotlin :app:testDebugUnitTest` green.

---

## File Structure

- `domain/repository/{ReadingPreferences,SpeechSynthesizer,TranslationUsageStats,TranslatorKeyStore,TranslatorEndpointStore}.kt` — **new** interfaces.
- `domain/model/TranslationUsage.kt` — **new** (moved from `data.local`).
- `data/local/{ReadingProgressManager,TextToSpeechManager,TranslationUsageTracker,ApiKeyManager,TranslatorEndpoints}.kt` — **modify** (implement interface, `override`).
- `di/AppModule.kt` — **modify** (5 `@Provides` upcasts).
- `domain/repository/TranslationRepository.kt` + `data/repository/TranslationRepositoryImpl.kt` — **modify** (cache count/clear).
- Consumers: `presentation/reader/{ReaderViewModel,TranslatorConfig,TranslatorPickerDialog}.kt`, `presentation/settings/SettingsViewModel.kt`, `presentation/home/HomeViewModel.kt`, `presentation/AppThemeViewModel.kt`, `presentation/words/WordsViewModel.kt`, `domain/usecase/TranslateTextUseCase.kt`.

---

## Task 1: ReadingPreferences (ReadingProgressManager)

**Files:**
- Create: `domain/repository/ReadingPreferences.kt`
- Modify: `data/local/ReadingProgressManager.kt` (add `: ReadingPreferences` + `override`), `di/AppModule.kt`, consumers.

**Interfaces:**
- Produces: `interface ReadingPreferences` mirroring the full public API of `ReadingProgressManager`.

- [ ] **Step 1: Declare the interface**

`domain/repository/ReadingPreferences.kt`:
```kotlin
package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import kotlinx.coroutines.flow.StateFlow

/** Persists per-book reading position and reader display/translation preferences. */
interface ReadingPreferences {
    fun saveProgress(bookUri: String, chapterIndex: Int, scrollPosition: Int, scrollOffset: Int = 0)
    fun getLastBookUri(): String?
    fun getLastChapter(bookUri: String): Int
    fun getLastScrollPosition(bookUri: String, chapterIndex: Int): Int
    fun getLastScrollOffset(bookUri: String, chapterIndex: Int): Int
    fun saveExcerpt(bookUri: String, text: String)
    fun getExcerpt(bookUri: String): String?
    fun markFinished(bookUri: String)
    fun isFinished(bookUri: String): Boolean
    fun clearProgress(bookUri: String)
    fun saveTargetLanguage(language: Language)
    fun getTargetLanguage(): Language
    fun saveNavigationSideLeft(isLeft: Boolean)
    fun isNavigationLeft(): Boolean
    val readerThemeName: StateFlow<String>
    fun saveReaderTheme(themeName: String)
    fun getReaderThemeName(): String
    fun saveLineHeightMultiplier(multiplier: Float)
    fun getLineHeightMultiplier(): Float
    fun saveSplitRatio(ratio: Float)
    fun getSplitRatio(): Float
    fun saveShowTranslation(show: Boolean)
    fun getShowTranslation(): Boolean
    fun saveShowIllustrations(show: Boolean)
    fun getShowIllustrations(): Boolean
    fun saveHorizontalMargin(margin: Float)
    fun getHorizontalMargin(): Float
    fun setTranslatorProvider(provider: TranslationProvider)
    fun getTranslatorProvider(): TranslationProvider
    fun saveTextSize(size: Float)
    fun getTextSize(): Float
    fun saveReadingFont(name: String)
    fun getReadingFontName(): String
    fun saveParagraphSpacing(spacing: Float)
    fun getParagraphSpacing(): Float
    fun saveLetterSpacing(spacing: Float)
    fun getLetterSpacing(): Float
    fun saveTextIndent(indent: Float)
    fun getTextIndent(): Float
    fun saveJustifyText(justify: Boolean)
    fun getJustifyText(): Boolean
    fun saveTtsRate(rate: Float)
    fun getTtsRate(): Float
    fun saveTtsPitch(pitch: Float)
    fun getTtsPitch(): Float
}
```

- [ ] **Step 2: Make the manager implement it**

In `data/local/ReadingProgressManager.kt`: add `import com.example.splitreader.domain.repository.ReadingPreferences`; change the class header to `class ReadingProgressManager @Inject constructor(...) : ReadingPreferences {`; add the `override` modifier to every member declared in the interface (all `fun`s above, plus `override val readerThemeName`). The compiler will flag any member missing `override` or any interface member not implemented — resolve until it builds. (Private members like `_readerThemeName`, `prefs` stay as-is.)
> **Kotlin rule:** an `override` may NOT repeat a default value. The interface declares the default (`scrollOffset: Int = 0`); in the manager's `override fun saveProgress(...)` REMOVE the `= 0` from `scrollOffset` (keep the default only on the interface). The compiler enforces this.

- [ ] **Step 3: Hilt binding**

In `di/AppModule.kt`, add imports (`data.local.ReadingProgressManager`, `domain.repository.ReadingPreferences`) and:
```kotlin
    @Provides @Singleton
    fun provideReadingPreferences(impl: ReadingProgressManager): ReadingPreferences = impl
```

- [ ] **Step 4: Swap consumers to the interface**

For each consumer, change the constructor-injected type `ReadingProgressManager` → `ReadingPreferences` and the import from `data.local.ReadingProgressManager` → `domain.repository.ReadingPreferences`. All method calls are identical (the interface mirrors the API). Consumers:
`domain/usecase/TranslateTextUseCase.kt`, `presentation/reader/ReaderViewModel.kt`, `presentation/settings/SettingsViewModel.kt`, `presentation/home/HomeViewModel.kt`, `presentation/AppThemeViewModel.kt`.
Find every reference: `grep -rn "ReadingProgressManager" app/src/main/java/com/example/splitreader`.
> `TextToSpeechManager` (data) injects the concrete `ReadingProgressManager` — leave that as-is (data→data). Only `presentation/**` and `TranslateTextUseCase` swap to the interface.

- [ ] **Step 5: Compile, test, verify**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` → BUILD SUCCESSFUL.
Verify the domain no longer imports this manager and presentation uses the interface:
`grep -rn "data.local.ReadingProgressManager" app/src/main/java/com/example/splitreader/domain app/src/main/java/com/example/splitreader/presentation || echo clean` → `clean`.
> This removes the LAST `data.**` import from `domain/**`. Also confirm: `grep -rn "com.example.splitreader.data" app/src/main/java/com/example/splitreader/domain || echo DOMAIN-CLEAN` → `DOMAIN-CLEAN` (Phase 3 goal). If any other `data.` import remains, report it (do not fix outside this task's scope).

- [ ] **Step 6: Commit** — `refactor(arch): ReadingPreferences interface; presentation + use-case depend on it, not ReadingProgressManager (P20)` with the trailer.

---

## Task 2: SpeechSynthesizer (TextToSpeechManager)

**Files:**
- Create: `domain/repository/SpeechSynthesizer.kt`
- Modify: `data/local/TextToSpeechManager.kt`, `di/AppModule.kt`, consumers.

- [ ] **Step 1: Interface** — `domain/repository/SpeechSynthesizer.kt`:
```kotlin
package com.example.splitreader.domain.repository

/** On-device text-to-speech: speaks text and adjusts rate/pitch. */
interface SpeechSynthesizer {
    fun speak(text: String, langCode: String)
    fun setRate(rate: Float)
    fun setPitch(pitch: Float)
    fun shutdown()
}
```

- [ ] **Step 2: Implement** — `TextToSpeechManager`: add `import ...domain.repository.SpeechSynthesizer`; header `... : SpeechSynthesizer {`; add `override` to `speak`/`setRate`/`setPitch`/`shutdown`. (It keeps injecting the concrete `ReadingProgressManager` — unchanged.)

- [ ] **Step 3: Hilt binding** — in `AppModule`:
```kotlin
    @Provides @Singleton
    fun provideSpeechSynthesizer(impl: TextToSpeechManager): SpeechSynthesizer = impl
```
(add imports `data.local.TextToSpeechManager`, `domain.repository.SpeechSynthesizer`).

- [ ] **Step 4: Consumers** — swap `TextToSpeechManager`→`SpeechSynthesizer` (import + injected type) in `presentation/reader/ReaderViewModel.kt`, `presentation/settings/SettingsViewModel.kt`, `presentation/words/WordsViewModel.kt`. Find: `grep -rn "TextToSpeechManager" app/src/main/java/com/example/splitreader/presentation`.

- [ ] **Step 5: Compile, test, verify** — build green; `grep -rn "data.local.TextToSpeechManager" app/src/main/java/com/example/splitreader/presentation || echo clean` → `clean`.

- [ ] **Step 6: Commit** — `refactor(arch): SpeechSynthesizer interface for TTS (P20)` with the trailer.

---

## Task 3: TranslationUsageStats + TranslationUsage → domain

**Files:**
- Create: `domain/repository/TranslationUsageStats.kt`, `domain/model/TranslationUsage.kt`
- Modify: `data/local/TranslationUsageTracker.kt`, `di/AppModule.kt`, `data/repository/TranslationRepositoryImpl.kt` (if it references `TranslationUsage`), consumers.

- [ ] **Step 1: Move the model** — create `domain/model/TranslationUsage.kt`:
```kotlin
package com.example.splitreader.domain.model

/** Monthly translation-character usage for a provider, for quota display. */
data class TranslationUsage(
    val charactersThisMonth: Long,
    val monthlyLimit: Long?,
)
```
Delete the `data class TranslationUsage(...)` declaration from `data/local/TranslationUsageTracker.kt`.

- [ ] **Step 2: Interface** — `domain/repository/TranslationUsageStats.kt`:
```kotlin
package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationUsage

/** Reads and resets per-provider monthly translation usage. */
interface TranslationUsageStats {
    fun usage(provider: TranslationProvider): TranslationUsage
    fun reset(provider: TranslationProvider)
}
```

- [ ] **Step 3: Implement** — `TranslationUsageTracker`: add `import ...domain.model.TranslationUsage` and `import ...domain.repository.TranslationUsageStats`; header `... : TranslationUsageStats {`; `override fun usage(...)` and `override fun reset(...)`. `record(...)` stays concrete (data-only). Its `usage()` already builds `TranslationUsage(...)` — now the domain type; compiles unchanged.

- [ ] **Step 4: Hilt binding** — in `AppModule`:
```kotlin
    @Provides @Singleton
    fun provideTranslationUsageStats(impl: TranslationUsageTracker): TranslationUsageStats = impl
```

- [ ] **Step 5: Consumers** — update every reference to the moved `TranslationUsage` import (`data.local`→`domain.model`) and swap injected `TranslationUsageTracker`→`TranslationUsageStats` in presentation. Files: `presentation/reader/ReaderViewModel.kt`, `presentation/settings/SettingsViewModel.kt`, `presentation/reader/TranslatorConfig.kt`, `presentation/reader/TranslatorPickerDialog.kt`.
Find both: `grep -rn "TranslationUsageTracker\|TranslationUsage" app/src/main/java/com/example/splitreader/presentation`.
> `data/repository/TranslationRepositoryImpl.kt` injects `TranslationUsageTracker` and calls `record(...)` (data-only) — it may KEEP the concrete class (data→data). Only fix its `TranslationUsage` import if it references the type. Do not force the interface on the data-layer caller of `record`.

- [ ] **Step 6: Compile, test, verify** — build green; `grep -rn "data.local.TranslationUsage" app/src/main/java/com/example/splitreader/presentation || echo clean` → `clean`.

- [ ] **Step 7: Commit** — `refactor(arch): TranslationUsageStats interface + TranslationUsage domain model (P20)` with the trailer.

---

## Task 4: TranslatorKeyStore (ApiKeyManager)

**Files:**
- Create: `domain/repository/TranslatorKeyStore.kt`
- Modify: `data/local/ApiKeyManager.kt`, `di/AppModule.kt`, consumers.

- [ ] **Step 1: Interface** — `domain/repository/TranslatorKeyStore.kt`:
```kotlin
package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.TranslationProvider

/** Stores per-provider translation API keys (encrypted at rest by the implementation). */
interface TranslatorKeyStore {
    fun getKey(provider: TranslationProvider): String?
    fun setKey(provider: TranslationProvider, value: String?)
}
```

- [ ] **Step 2: Implement** — `ApiKeyManager`: add `import ...domain.repository.TranslatorKeyStore`; header `... : TranslatorKeyStore {`; add `override` to `getKey(provider)` and `setKey(provider, value)`. Per-provider getters/setters (`getGoogleCloudKey`, etc.) stay concrete (used by data-layer translator providers).

- [ ] **Step 3: Hilt binding** — in `AppModule`:
```kotlin
    @Provides @Singleton
    fun provideTranslatorKeyStore(impl: ApiKeyManager): TranslatorKeyStore = impl
```

- [ ] **Step 4: Consumers** — swap `ApiKeyManager`→`TranslatorKeyStore` (import + injected type) in `presentation/reader/ReaderViewModel.kt`, `presentation/settings/SettingsViewModel.kt`. Find: `grep -rn "ApiKeyManager" app/src/main/java/com/example/splitreader/presentation`.
> Data-layer translator providers that inject `ApiKeyManager` for per-provider getters stay concrete — do not change them.

- [ ] **Step 5: Compile, test, verify** — build green; `grep -rn "data.local.ApiKeyManager" app/src/main/java/com/example/splitreader/presentation || echo clean` → `clean`.

- [ ] **Step 6: Commit** — `refactor(arch): TranslatorKeyStore interface for API keys (P20)` with the trailer.

---

## Task 5: TranslatorEndpointStore (TranslatorEndpoints)

**Files:**
- Create: `domain/repository/TranslatorEndpointStore.kt`
- Modify: `data/local/TranslatorEndpoints.kt`, `di/AppModule.kt`, consumers.

- [ ] **Step 1: Interface** — `domain/repository/TranslatorEndpointStore.kt`:
```kotlin
package com.example.splitreader.domain.repository

import com.example.splitreader.domain.model.TranslationProvider

/** Stores per-provider secondary translator config (LibreTranslate base URL / Azure region). */
interface TranslatorEndpointStore {
    fun getSecondary(provider: TranslationProvider): String
    fun setSecondary(provider: TranslationProvider, value: String?)
}
```

- [ ] **Step 2: Implement** — `TranslatorEndpoints`: add `import ...domain.repository.TranslatorEndpointStore`; header `... : TranslatorEndpointStore {`; add `override` to `getSecondary` and `setSecondary`. The specific `getLibreTranslateBaseUrl`/`getAzureRegion`/setters stay concrete (data-layer translator providers use them).

- [ ] **Step 3: Hilt binding** — in `AppModule`:
```kotlin
    @Provides @Singleton
    fun provideTranslatorEndpointStore(impl: TranslatorEndpoints): TranslatorEndpointStore = impl
```

- [ ] **Step 4: Consumers** — swap `TranslatorEndpoints`→`TranslatorEndpointStore` (import + injected type) in `presentation/reader/ReaderViewModel.kt`, `presentation/settings/SettingsViewModel.kt`, `presentation/reader/TranslatorConfig.kt`. Find: `grep -rn "TranslatorEndpoints" app/src/main/java/com/example/splitreader/presentation`.
> `TranslatorConfig.kt`'s `buildTranslatorConfigState(...)` takes `endpoints: TranslatorEndpoints` — change the parameter type to `TranslatorEndpointStore` (it only calls `getSecondary`). Update the callers in Reader/Settings VM accordingly (they already pass their injected instance, now the interface).

- [ ] **Step 5: Compile, test, verify** — build green; `grep -rn "data.local.TranslatorEndpoints" app/src/main/java/com/example/splitreader/presentation || echo clean` → `clean`.

- [ ] **Step 6: Commit** — `refactor(arch): TranslatorEndpointStore interface for endpoints (P20)` with the trailer.

---

## Task 6: TranslationRepository cache count/clear (remove TranslationDao from SettingsViewModel)

**Files:**
- Modify: `domain/repository/TranslationRepository.kt`, `data/repository/TranslationRepositoryImpl.kt`, `presentation/settings/SettingsViewModel.kt`

- [ ] **Step 1: Extend the repository interface** — `domain/repository/TranslationRepository.kt`:
```kotlin
interface TranslationRepository {
    suspend fun translate(text: String, sourceLanguage: Language, targetLanguage: Language): String

    /** Number of cached translations (for the Settings storage display). */
    suspend fun cachedCount(): Int

    /** Clears all cached translations. */
    suspend fun clearCache()
}
```

- [ ] **Step 2: Implement** — `TranslationRepositoryImpl` (already injects `dao: TranslationDao`):
```kotlin
    override suspend fun cachedCount(): Int = dao.count()
    override suspend fun clearCache() = dao.clearAll()
```

- [ ] **Step 3: Swap the SettingsViewModel dependency** — in `SettingsViewModel.kt`:
- Remove `import com.example.splitreader.data.local.TranslationDao` and the `private val translationDao: TranslationDao` constructor param; inject `private val translationRepository: TranslationRepository` instead (add its import `com.example.splitreader.domain.repository.TranslationRepository`).
- `refreshCacheCount()`: `translationDao.count()` → `translationRepository.cachedCount()`.
- `clearTranslationCache()`: `translationDao.clearAll()` → `translationRepository.clearCache()`.
> If `TranslationRepository` is already injected elsewhere in the VM, reuse it; otherwise add the param. Confirm no other `translationDao` reference remains in the file.

- [ ] **Step 4: Compile, test, verify** — build green; `grep -rn "TranslationDao" app/src/main/java/com/example/splitreader/presentation || echo clean` → `clean`.

- [ ] **Step 5: Commit** — `refactor(arch): cache count/clear via TranslationRepository; drop TranslationDao from SettingsViewModel (P20)` with the trailer.

---

## Definition of Done (maps to spec §6)

1. Five capability interfaces exist in `domain/repository/`; the managers implement them and stay in `data.local`; Hilt provides each via an `AppModule` upcast. *(Tasks 1–5)*
2. `presentation/**` imports none of `data.local.{ReadingProgressManager, TextToSpeechManager, TranslationUsageTracker, ApiKeyManager, TranslatorEndpoints, TranslationDao, TranslationUsage}` — only domain interfaces/models. Verify: `grep -rn "data.local.\(ReadingProgressManager\|TextToSpeechManager\|TranslationUsageTracker\|ApiKeyManager\|TranslatorEndpoints\|TranslationDao\|TranslationUsage\)" app/src/main/java/com/example/splitreader/presentation || echo clean` → `clean`. *(Tasks 1–6)*
3. **`domain/**` imports no `com.example.splitreader.data.**` at all** (Phase 3 goal): `grep -rn "com.example.splitreader.data" app/src/main/java/com/example/splitreader/domain || echo DOMAIN-CLEAN` → `DOMAIN-CLEAN`. *(Task 1 removes the last one)*
4. Behavior unchanged; `:app:testDebugUnitTest` green; `:app:compileDebugKotlin` succeeds. *(All tasks)*
