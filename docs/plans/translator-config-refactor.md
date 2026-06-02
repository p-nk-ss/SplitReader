# Plan: Translator-provider config refactor

> Origin: surfaced by the `compose-expert` code review of branch `fix/minor-fixes-2`.
> Two coupled goals: (A) make `TranslatorPickerDialog` data-driven from enum metadata
> (kill the ~8 `when (provider)` blocks with `else ->` fall-through), and (B) eliminate
> the 8-field provider-state duplication across 4 layers + collapse the per-provider
> callback explosion into a single configure callback.

## Constraints (do not violate)
- **Single-module app.** No new modules.
- **Custom design system.** UI uses `LocalReaderPalette` / `LocalSpacing` / `LocalRadii`
  and fonts `Newsreader` / `JetBrainsMono`. **Do NOT introduce `MaterialTheme.*` tokens.**
- **Hilt `@IntoMap` provider registry must keep working** (`TranslatorBindingsModule`,
  `@TranslationProviderKey`). Do not touch the binding mechanism.
- **No provider regressions.** Free: `MLKIT` (+ any GoogleWeb). Advanced (need key):
  `GOOGLE_CLOUD`, `DEEPL`, `LIBRE_TRANSLATE`, `AZURE`.
- **No new third-party deps.** Fix the unstable-`Map` recomposition by annotating the
  shared state `@Immutable` (a stability promise) — NOT by adding kotlinx-collections-immutable.
- Region/URL semantics must be preserved exactly (see Phase 0 facts).

---

## Phase 0 — Ground truth (already gathered; reference only)

Source files + exact signatures confirmed by two fact-gathering subagents (confidence 95%).
Paths relative to `app/src/main/java/com/example/splitreader/`.

### Enum + interface
- `domain/model/TranslationProvider.kt:5-58` — enum ctor:
  `(displayName, requiresApiKey, requiresNetwork, category, description, tracksUsage)`.
  Entries: `MLKIT` (FREE, no key), `LIBRE_TRANSLATE`, `GOOGLE_CLOUD`, `DEEPL`, `AZURE`
  (all ADVANCED, key required). Companion `fromName()` at 54-57.
  `TranslationProviderCategory { FREE, ADVANCED }` at line 3.
- `domain/translator/TranslationProviderApi.kt:6-14` — `id`, `isConfigured()`,
  `supports()` (default true), `suspend translate(text, source, target)`.

### Data layer
- `data/local/ApiKeyManager.kt` — `get/set{GoogleCloud,DeepL,LibreTranslate,Azure}Key`
  at lines 60-74; companion `KEY_*` consts at 76-83.
- `data/local/TranslatorEndpoints.kt` — `getLibreTranslateBaseUrl():String` (15-16),
  `setLibreTranslateBaseUrl(url:String?)` (18-22), `getAzureRegion():String` (24-25),
  `setAzureRegion(region:String?)` (27-33), `private normalize()` (35-39), consts 41-46
  (`DEFAULT_LIBRE_URL`, `DEFAULT_AZURE_REGION="global"`).
- `data/local/TranslationUsageTracker.kt` — `record(provider,chars)` (24-29),
  `usage(provider):TranslationUsage` (31-34), `reset(provider)` (36-38),
  `private limitFor()` (47-52: GOOGLE_CLOUD/DEEPL=500_000, AZURE=2_000_000, else null).

### Provider impls — secondary-config shape
| Provider | secondary config | `isConfigured()` |
|---|---|---|
| MLKIT | none | n/a |
| GOOGLE_CLOUD | none | `keys.getGoogleCloudKey() != null` |
| DEEPL | none | `keys.getDeepLKey() != null` |
| LIBRE_TRANSLATE | **Base URL** | key set **OR** URL ≠ `DEFAULT_LIBRE_URL` (special!) |
| AZURE | **Region** (default "global") | `keys.getAzureKey() != null` |

> ⚠️ LibreTranslate's `isConfigured()` depends on the **secondary value**, not just the key.
> The shared `ProviderConfig.configured` flag MUST be computed from the provider's own
> `isConfigured()` (via the DI registry) — NOT from `getKey() != null` alone.

### Presentation duplication (the 4 layers)
8 fields duplicated in each of: `ReaderViewModel.InternalState` (75-109),
`ReaderUiState.Success` (40-72), `SettingsUiState` (24-53), `TranslatorPickerState`
(`TranslatorPickerDialog.kt:49-58`). Fields: `translatorProvider`,
`googleCloudKeyConfigured`, `deepLKeyConfigured`, `libreTranslateKeyConfigured`,
`libreBaseUrl`, `azureKeyConfigured`, `azureRegion`, `translationUsage`.
`TranslatorPickerState` renames them (`*KeyConfigured` → `*Configured`, `translatorProvider` → `current`).

Setter sites:
- `ReaderViewModel.kt` setters 439-494 (`setTranslatorProvider`, `setGoogleCloudKey`,
  `setDeepLKey`, `setLibreTranslateKey`, `setLibreBaseUrl`, `setAzureKey`, `setAzureRegion`,
  `refreshTranslationUsage` 446-449, `resetTranslationUsage` 451-454), private
  `retranslateCurrentChapter()` 496-510. **Reader setters conditionally call
  `retranslateCurrentChapter()` when the edited provider == active provider**; `setTranslatorProvider`
  always retranslates.
- `SettingsViewModel.kt` setters 175-218 (same names, **no** retranslate — Settings is not reading).
- `ReaderScreen.kt` — `ReaderContent` params 245-284 (10 provider callbacks at 274-283),
  builds `TranslatorPickerState` + wires callbacks at 447-472.
- `SettingsScreen.kt` — `SettingsRoute` wiring 55-85, `SettingsScreen` params 87-115,
  builds picker state 302-328.
- `TranslatorPickerDialog.kt` — the ~8 `when (provider)` blocks live at 101-107
  (configured), 124-178 (`ApiKeyDialog` config: existingPresent/secondaryLabel/secondaryValue/
  secondaryPlaceholder/secondaryIsUrl/onSave/onClear), 406-411 (helpUrl).

### Verification baseline
- Build: `.\gradlew.bat :app:compileDebugKotlin` (Windows). Must be green before & after every phase.
- There are no provider unit tests in the repo (gap noted). Verification is build + grep + manual smoke.

---

## Phase 1 — Enrich the enum with UI metadata (additive, green)

**Goal:** Define per-provider UI config once, on the enum, so the dialog stops branching.

**What to implement** (`domain/model/TranslationProvider.kt`):
Add nullable metadata params to the ctor (keep existing 6 first, append new):
```kotlin
val secondaryLabel: String? = null,      // null = provider has no secondary field
val secondaryPlaceholder: String? = null,
val secondaryIsUrl: Boolean = false,
val helpUrl: String? = null,             // the "Get a key: …" hint text
```
Fill per entry (copy values verbatim from current `when` blocks in
`TranslatorPickerDialog.kt` so behavior is byte-identical):
- `GOOGLE_CLOUD` → `helpUrl = "console.cloud.google.com → Translation API → API key"`.
- `DEEPL` → `helpUrl = "deepl.com/pro-api (free plan)"`.
- `LIBRE_TRANSLATE` → `secondaryLabel = "Server URL"`, `secondaryPlaceholder = "https://…"`,
  `secondaryIsUrl = true`, `helpUrl = null` (current code shows no help line for Libre).
- `AZURE` → `secondaryLabel = "Region"`, `secondaryPlaceholder = "global"`,
  `secondaryIsUrl = false`, `helpUrl = "portal.azure.com → Translator resource → Keys and Endpoint"`.
- `MLKIT` → all defaults (null/false).

**Anti-pattern guard:** do NOT delete the `when` blocks yet — this phase only adds data.
Do NOT change the existing 6 ctor params or entry order (`fromName()` and `@IntoMap` rely on names).

**Verify:**
- `.\gradlew.bat :app:compileDebugKotlin` green.
- Grep each new field appears on all 5 entries as intended.

---

## Phase 2 — Provider-keyed data-layer accessors (additive, green)

**Goal:** Let one callback route to the right store without a `when` in the ViewModel.

**What to implement:**
- `ApiKeyManager.kt`: add
  ```kotlin
  fun getKey(provider: TranslationProvider): String?      // dispatch over existing get*Key()
  fun setKey(provider: TranslationProvider, value: String?) // dispatch over existing set*Key()
  ```
  Implement as a single `when (provider)` *inside ApiKeyManager only* (one authoritative
  site, fine for providers with no key → return null / no-op). Keep existing methods for now.
- `TranslatorEndpoints.kt`: add
  ```kotlin
  fun getSecondary(provider: TranslationProvider): String   // Azure→region, Libre→url, else ""
  fun setSecondary(provider: TranslationProvider, value: String?) // routes to existing setters
  ```
  Reuse existing `getAzureRegion/setAzureRegion` and `getLibreTranslateBaseUrl/setLibreTranslateBaseUrl`
  (preserve their normalize/lowercase logic). Non-secondary providers → `""` / no-op.

**Anti-pattern guard:** keep the existing per-provider methods (providers still call them);
do not duplicate the normalize/lowercase logic — delegate to the existing setters.

**Verify:** build green; grep that `getKey`/`setKey`/`getSecondary`/`setSecondary` exist and
that no normalize/lowercase logic was copy-pasted (it must delegate).

---

## Phase 3 — Shared immutable state type (additive new file, green)

**Goal:** One state object both screens build and hand straight to the dialog.

**What to implement** — new file
`presentation/reader/TranslatorConfig.kt` (same package as the dialog so it stays `internal`):
```kotlin
@Immutable
data class ProviderConfig(
    val configured: Boolean,
    val secondaryValue: String = "",   // region / base url; "" when provider has none
)

@Immutable
data class TranslatorConfigState(
    val current: TranslationProvider,
    val configs: Map<TranslationProvider, ProviderConfig>,
    val usage: Map<TranslationProvider, TranslationUsage> = emptyMap(),
)
```
> `@Immutable` is the stability promise that fixes the review's unstable-`Map` finding —
> the compiler then treats the whole object as stable and can skip the dialog. No new dep.

Add a single factory used by BOTH view models (put it next to the data class or as a
top-level `fun` taking the registry + stores):
```kotlin
fun buildTranslatorConfigState(
    providers: Map<TranslationProvider, @JvmSuppressWildcards TranslationProviderApi>,
    endpoints: TranslatorEndpoints,
    usageTracker: TranslationUsageTracker,
    current: TranslationProvider,
): TranslatorConfigState
```
- `configured` MUST come from `providers[p]?.isConfigured() ?: (p.category == FREE)`
  (this preserves LibreTranslate's URL-based configured logic — see Phase 0 ⚠️).
- `secondaryValue` from `endpoints.getSecondary(p)`.
- `usage` from `TranslationProvider.entries.associateWith { usageTracker.usage(it) }`.

> Both ViewModels already receive the `@IntoMap` `Map<TranslationProvider, TranslationProviderApi>`?
> CHECK: if not currently injected, add it to their constructors (it's the same map Hilt already
> builds for `TranslationRepositoryImpl`). This is the only DI change and it's additive.

**Anti-pattern guard:** do not wire it into the UI yet; this phase only adds types + factory.

**Verify:** build green; the factory compiles against the real registry type.

---

## Phase 4 — Cutover (coordinated; complete in ONE session, then build)

> This is the only phase that edits many files at once. Intermediate states will not
> compile — do all sub-steps before building. Order: ViewModels → dialog → call sites → delete.

**4a. ViewModels expose a tiny provider API.** In `ReaderViewModel` and `SettingsViewModel`:
```kotlin
fun selectProvider(provider: TranslationProvider)                      // = old setTranslatorProvider
fun configureProvider(provider: TranslationProvider, key: String?, secondary: String?)
fun clearProvider(provider: TranslationProvider)                       // sets key=null
// keep refreshTranslationUsage() / resetTranslationUsage(provider)
```
- `configureProvider`: `if (key != null) apiKeyManager.setKey(provider, key)`;
  `if (provider.secondaryLabel != null && secondary != null) endpoints.setSecondary(provider, secondary)`;
  then rebuild state. **Reader only:** if `provider == active` → `retranslateCurrentChapter()`
  (preserve exact current condition). `clearProvider`: `apiKeyManager.setKey(provider, null)` + rebuild
  (+ Reader retranslate-if-active).
- Replace the duplicated 8 state fields in `InternalState` / `SettingsUiState` /
  `ReaderUiState.Success` with a single `translatorConfig: TranslatorConfigState`
  (keep `translatorProvider` only if other code reads it — grep first; otherwise read
  `translatorConfig.current`). Build it via `buildTranslatorConfigState(...)` in init/loadState
  and after every mutation.
- Delete the 6 old per-provider setters (`setGoogleCloudKey`…`setAzureRegion`) once nothing references them.

**4b. Rewrite `TranslatorPickerDialog.kt` data-driven:**
- New signature:
  ```kotlin
  @Composable internal fun TranslatorPickerDialog(
      state: TranslatorConfigState,
      onSelect: (TranslationProvider) -> Unit,
      onConfigure: (provider, key: String?, secondary: String?) -> Unit,
      onClear: (TranslationProvider) -> Unit,
      onResetUsage: (TranslationProvider) -> Unit,
      onDismiss: () -> Unit,
  )
  ```
- Delete `TranslatorPickerState` (replaced by `TranslatorConfigState`).
- `ProviderRow` reads `state.configs[provider]?.configured ?: false`.
- `ApiKeyDialog`: pull `secondaryLabel/secondaryPlaceholder/secondaryIsUrl/helpUrl` from
  `provider.*` (Phase 1), `secondaryValue` from `state.configs[provider]?.secondaryValue ?: ""`.
  Its `onSave` → `onConfigure(provider, key, secondary)`; its `onClear` → `onClear(provider)`.
- **Remove all ~8 `when (provider)` blocks** (101-107, 124-178, 406-411). The only branching
  left should be over `provider.category` for the Free/Advanced section split.

**4c. Update call sites:**
- `ReaderScreen.kt`: drop the 6 `onSetXxx` params from `ReaderContent` (keep `onRefreshTranslationUsage`,
  `onResetTranslationUsage`); pass `state.translatorConfig` + `onConfigure = viewModel-routed` +
  `onClear`. Update `ReaderRoute` wiring to `viewModel::configureProvider` / `::clearProvider` /
  `::selectProvider`.
- `SettingsScreen.kt`: same — collapse the 6 callbacks in `SettingsScreen`/`SettingsRoute`
  (55-115) to `onConfigureProvider`/`onClearProvider`/`onSelectProvider`; build picker from
  `state.translatorConfig` (302-328).

**4d. Delete now-dead code:** old `TranslatorPickerState`, old UiState provider fields, old
ViewModel setters, and (if fully unused) the old per-provider `ApiKeyManager`/`TranslatorEndpoints`
public methods — but KEEP any still used by provider impls (`LibreTranslateProvider`/`AzureTranslationProvider`
read `getLibreTranslateBaseUrl()`/`getAzureRegion()`; those stay).

**Anti-pattern guards:**
- Do not lose LibreTranslate's URL-based `configured` logic (use `isConfigured()`, Phase 0 ⚠️).
- Do not lose the Reader-only retranslate-if-active behavior.
- Do not introduce `MaterialTheme.*`; keep `LocalReaderPalette/Spacing/Radii`.
- Keep `LaunchedEffect(Unit){ onRefreshTranslationUsage() }`-on-open behavior in both screens.

**Verify:** build green; app launches.

---

## Phase 5 — Verification

1. **Build:** `.\gradlew.bat :app:compileDebugKotlin` green; `.\gradlew.bat assembleDebug`.
2. **Grep guards (expect ZERO hits in presentation layer):**
   - `azureKeyConfigured|deepLKeyConfigured|googleCloudKeyConfigured|libreTranslateKeyConfigured`
     outside the data-layer dispatch — should be gone from UiStates.
   - `onSetGoogleCloudKey|onSetDeepLKey|onSetAzureKey|onSetAzureRegion|onSetLibreUrl` — gone.
   - `TranslatorPickerState` — gone (replaced).
   - `when (provider)` inside `TranslatorPickerDialog.kt` — only the category split may remain.
3. **Grep guards (expect hits):** `@Immutable` on `ProviderConfig` + `TranslatorConfigState`;
   `buildTranslatorConfigState(` used by both view models.
4. **Manual smoke (each provider, both Reader picker AND Settings picker):**
   - Select MLKIT → translates offline.
   - Add a GOOGLE_CLOUD / DEEPL key → row shows "Key configured", translation works.
   - LIBRE_TRANSLATE: set a custom Server URL with NO key → row shows configured (URL-based).
   - AZURE: add key, leave region "global" → no region header; set region "westeurope" → header sent.
   - Clear a key → row reverts to "Tap to add API key".
   - Reader: changing the ACTIVE provider's key retranslates the current chapter; changing a
     non-active provider does not.
   - Usage bar + reset-counter still work.
5. **Regression:** confirm `@IntoMap` registry untouched (`git diff TranslatorModule.kt` = no change).

---

## Phase summary
| Phase | Scope | Build state |
|---|---|---|
| 1 | Enum UI metadata | green (additive) |
| 2 | Provider-keyed data accessors | green (additive) |
| 3 | `@Immutable` shared state + factory | green (additive) |
| 4 | Coordinated cutover (dialog + VMs + screens + deletes) | green only at end of phase |
| 5 | Build + grep + manual smoke | — |

**Net effect:** new provider = 1 enum entry (with metadata) + 1 provider impl + 1 `@IntoMap`
binding + (optional) key const + secondary store wiring. Zero edits to the dialog, the UiStates,
or the screen signatures.
