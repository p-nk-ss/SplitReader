# Phase 2 — Data Correctness & Security Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close six independent data/translation/billing correctness & security findings (P13–P18) from `docs/refactor_plan.md`, each as its own reviewed task.

**Architecture:** Extraction-first — the correctness/security cores (cache key, purchase-signature check, URL validation) become small pure JVM-testable units, wired into thin Android callers. Threading fix (P14) moves Keystore/prefs off the main thread. Config fixes (P16, P18) are backup-rule and copy changes.

**Tech Stack:** Kotlin 2.0.21, JUnit4 (JVM, `app/src/test`, hand-written fakes, no mock framework). `java.security` + `java.util.Base64` for signature verification (JVM-testable, no Robolectric).

## Global Constraints

- New pure units carry **no `android.*` imports** — string/`java.security`/`java.util.Base64` only, so they run under `:app:testDebugUnitTest` without a device.
- Hand-written tests, JUnit4, no mock framework.
- Commit trailer on every commit: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- JVM tests: `./gradlew :app:testDebugUnitTest` (no device). Full compile: `./gradlew :app:compileDebugKotlin`.
- P15 is **fail-open**: when `BuildConfig.BILLING_PUBLIC_KEY` is blank, signature verification is skipped (dev). Never make a blank key reject purchases.
- P17: `http://` LibreTranslate URLs are **rejected** (never silently upgraded); `https://` and bare hosts are accepted (bare → `https://` prepended).
- Do not touch the parser layer (Phase 1) or DB migrations (Phase 0).

---

## File Structure

- `data/repository/TranslationCacheKey.kt` — **new** pure cache-key builder (P13).
- `data/billing/PurchaseVerifier.kt` — **new** pure RSA signature verifier (P15).
- `data/local/TranslatorUrlValidator.kt` — **new** pure LibreTranslate URL validator (P17).
- `data/repository/TranslationRepositoryImpl.kt` — **modify**: use cache key + verify source text (P13).
- `data/billing/BillingManager.kt` — **modify**: verify signature before granting premium (P15).
- `app/build.gradle.kts` — **modify**: `BILLING_PUBLIC_KEY` buildConfigField (P15).
- `data/local/TranslatorEndpoints.kt` — **modify**: route Libre URL through validator (P17).
- `presentation/reader/TranslatorPickerDialog.kt` — **modify**: inline URL error in `ApiKeyDialog` (P17).
- `domain/model/TranslationProvider.kt` — **modify**: `stabilityNote` field (P18).
- `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` — **modify**: exclude `billing.xml` (P16).
- `presentation/reader/ReaderViewModel.kt`, `presentation/settings/SettingsViewModel.kt`, `presentation/home/HomeViewModel.kt` — **modify**: off-main-thread (P14).
- `docs/play_console_release.md` — **modify**: BILLING_PUBLIC_KEY release note (P15).
- Tests: `TranslationCacheKeyTest.kt`, `PurchaseVerifierTest.kt`, `TranslatorUrlValidatorTest.kt`.

---

## Task 1: P13 — SHA-256 translation cache key

**Files:**
- Create: `app/src/main/java/com/example/splitreader/data/repository/TranslationCacheKey.kt`
- Create: `app/src/test/java/com/example/splitreader/data/repository/TranslationCacheKeyTest.kt`
- Modify: `app/src/main/java/com/example/splitreader/data/repository/TranslationRepositoryImpl.kt`

**Interfaces:**
- Produces: `object TranslationCacheKey { fun compute(provider: TranslationProvider, text: String, source: Language, target: Language): String }`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/example/splitreader/data/repository/TranslationCacheKeyTest.kt`:
```kotlin
package com.example.splitreader.data.repository

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationCacheKeyTest {

    private fun key(text: String, p: TranslationProvider = TranslationProvider.MLKIT,
                    s: Language = Language.ENGLISH, t: Language = Language.RUSSIAN) =
        TranslationCacheKey.compute(p, text, s, t)

    @Test fun deterministic_sameInputSameKey() {
        assertEquals(key("Hello world"), key("Hello world"))
    }

    @Test fun differentText_differentKey() {
        assertNotEquals(key("Hello world"), key("Goodbye world"))
    }

    @Test fun providerChangesKey() {
        assertNotEquals(key("x", p = TranslationProvider.MLKIT), key("x", p = TranslationProvider.DEEPL))
    }

    @Test fun targetLanguageChangesKey() {
        assertNotEquals(key("x", t = Language.RUSSIAN), key("x", t = Language.GERMAN))
    }

    @Test fun sourceLanguageChangesKey() {
        assertNotEquals(key("x", s = Language.ENGLISH), key("x", s = Language.GERMAN))
    }

    @Test fun containsProviderAndLangCodes() {
        val k = TranslationCacheKey.compute(
            TranslationProvider.MLKIT, "x", Language.ENGLISH, Language.RUSSIAN,
        )
        assertTrue(k.startsWith("MLKIT_"))
        assertTrue(k.endsWith("_${Language.ENGLISH.code}_${Language.RUSSIAN.code}"))
    }

    @Test fun knownVector_sha256OfEmpty() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val k = key("")
        assertTrue(k.contains("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
    }
}
```
> If `Language.ENGLISH`/`RUSSIAN`/`GERMAN` enum constant names differ, use the actual constants from `domain/model/Language.kt` (the test only needs three distinct languages). `.code` is the persisted language code already used by the repository.

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*TranslationCacheKeyTest"`
Expected: FAIL — unresolved reference `TranslationCacheKey`.

- [ ] **Step 3: Implement the pure key builder**

`app/src/main/java/com/example/splitreader/data/repository/TranslationCacheKey.kt`:
```kotlin
package com.example.splitreader.data.repository

import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.TranslationProvider
import java.security.MessageDigest

/**
 * Builds a collision-resistant cache key for a translation. The text is hashed with SHA-256
 * (hex) rather than [String.hashCode], whose 32-bit space collides across a book and could
 * otherwise return another paragraph's cached translation for the same provider/language pair.
 */
object TranslationCacheKey {

    fun compute(
        provider: TranslationProvider,
        text: String,
        source: Language,
        target: Language,
    ): String = "${provider.name}_${sha256Hex(text)}_${source.code}_${target.code}"

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (b in digest) {
                val v = b.toInt() and 0xFF
                append(HEX[v ushr 4])
                append(HEX[v and 0x0F])
            }
        }
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*TranslationCacheKeyTest"`
Expected: PASS (7 tests). Fix the implementation, not the test, on any failure.

- [ ] **Step 5: Wire into the repository + add source-text verification**

In `TranslationRepositoryImpl.kt`, replace the body of `translate` and the private `cacheKey` helper.

Replace lines 22-30 (`translate`):
```kotlin
    override suspend fun translate(text: String, sourceLanguage: Language, targetLanguage: Language): String {
        val provider = resolveProvider(sourceLanguage, targetLanguage)
        val cacheKey = TranslationCacheKey.compute(provider.id, text, sourceLanguage, targetLanguage)
        dao.getCached(cacheKey)?.takeIf { it.originalText == text }?.let { return it.translatedText }
        val translated = provider.translate(text, sourceLanguage, targetLanguage)
        if (provider.id.tracksUsage) usageTracker.record(provider.id, text.length)
        dao.insert(TranslationCacheEntity(cacheKey, text, translated, targetLanguage.code))
        return translated
    }
```

Delete the now-unused private `cacheKey` function (lines 42-43):
```kotlin
    private fun cacheKey(provider: TranslationProvider, text: String, source: Language, target: Language): String =
        "${provider.name}_${text.hashCode()}_${source.code}_${target.code}"
```
The `import com.example.splitreader.domain.model.TranslationProvider` becomes unused after deleting `cacheKey` — remove it if the compiler warns (it is not referenced elsewhere in the file).

- [ ] **Step 6: Compile and run the full unit suite**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all unit tests pass.

- [ ] **Step 7: Confirm no Android imports in the new unit**

Run: `grep -n "import android" app/src/main/java/com/example/splitreader/data/repository/TranslationCacheKey.kt || echo "clean: no android imports"`
Expected: `clean: no android imports`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/splitreader/data/repository/TranslationCacheKey.kt \
  app/src/test/java/com/example/splitreader/data/repository/TranslationCacheKeyTest.kt \
  app/src/main/java/com/example/splitreader/data/repository/TranslationRepositoryImpl.kt
git commit -m "fix(translation): SHA-256 cache key + source-text verify (P13)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: P16 — Exclude billing.xml from backup

**Files:**
- Modify: `app/src/main/res/xml/backup_rules.xml`
- Modify: `app/src/main/res/xml/data_extraction_rules.xml`

**Interfaces:** none (resource config only).

- [ ] **Step 1: Add billing.xml exclusion to backup_rules.xml**

In `backup_rules.xml`, inside `<full-backup-content>`, after the existing `entitlement.xml` line, add:
```xml
    <!-- Premium purchase cache written by BillingManager (prefs name "billing"); source of truth is
         Play Billing. Excluding it stops a backup/restore from carrying a stale/forced premium flag
         to another device before the next Play sync. -->
    <exclude domain="sharedpref" path="billing.xml"/>
```
Keep the existing `entitlement.xml` exclusion.

- [ ] **Step 2: Add billing.xml exclusion to both blocks of data_extraction_rules.xml**

In `data_extraction_rules.xml`, add the same line inside **both** `<cloud-backup>` and `<device-transfer>` (after each `entitlement.xml` exclusion):
```xml
        <exclude domain="sharedpref" path="billing.xml"/>
```

- [ ] **Step 3: Verify the files parse and both mention billing.xml**

Run: `grep -c "billing.xml" app/src/main/res/xml/backup_rules.xml app/src/main/res/xml/data_extraction_rules.xml`
Expected: `backup_rules.xml:1` and `data_extraction_rules.xml:2`.

- [ ] **Step 4: Compile (resource sanity)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` (no resource errors).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/xml/backup_rules.xml app/src/main/res/xml/data_extraction_rules.xml
git commit -m "fix(backup): exclude billing.xml so premium flag isn't restored across devices (P16)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: P17 — Reject cleartext http:// LibreTranslate URLs

**Files:**
- Create: `app/src/main/java/com/example/splitreader/data/local/TranslatorUrlValidator.kt`
- Create: `app/src/test/java/com/example/splitreader/data/local/TranslatorUrlValidatorTest.kt`
- Modify: `app/src/main/java/com/example/splitreader/data/local/TranslatorEndpoints.kt`
- Modify: `app/src/main/java/com/example/splitreader/presentation/reader/TranslatorPickerDialog.kt`

**Interfaces:**
- Produces: `sealed interface UrlResult { data class Valid(val url: String); data class Invalid(val reason: String) }` and `fun normalizeLibreUrl(raw: String): UrlResult` — consumed by `TranslatorEndpoints` and `ApiKeyDialog`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/example/splitreader/data/local/TranslatorUrlValidatorTest.kt`:
```kotlin
package com.example.splitreader.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatorUrlValidatorTest {

    @Test fun httpsIsValidUnchanged() {
        assertEquals(UrlResult.Valid("https://libretranslate.com"),
            normalizeLibreUrl("https://libretranslate.com"))
    }

    @Test fun bareHostGetsHttpsPrefix() {
        assertEquals(UrlResult.Valid("https://myserver.local"),
            normalizeLibreUrl("myserver.local"))
    }

    @Test fun trailingSlashAndSpacesTrimmed() {
        assertEquals(UrlResult.Valid("https://example.com"),
            normalizeLibreUrl("  https://example.com/  "))
    }

    @Test fun httpIsRejected() {
        val r = normalizeLibreUrl("http://example.com")
        assertTrue(r is UrlResult.Invalid)
    }

    @Test fun httpUppercaseIsRejected() {
        assertTrue(normalizeLibreUrl("HTTP://example.com") is UrlResult.Invalid)
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*TranslatorUrlValidatorTest"`
Expected: FAIL — unresolved reference `normalizeLibreUrl` / `UrlResult`.

- [ ] **Step 3: Implement the validator**

`app/src/main/java/com/example/splitreader/data/local/TranslatorUrlValidator.kt`:
```kotlin
package com.example.splitreader.data.local

/** Result of validating a user-entered translator base URL. */
sealed interface UrlResult {
    data class Valid(val url: String) : UrlResult
    data class Invalid(val reason: String) : UrlResult
}

/**
 * Normalizes a LibreTranslate base URL, rejecting cleartext `http://`. Modern Android
 * (targetSdk 36) blocks cleartext by default and the app ships no network-security-config, so an
 * `http://` endpoint would silently fail to connect — we reject it up front with a clear reason.
 * A bare host (no scheme) is upgraded to `https://`; an explicit `https://` is kept as-is.
 */
fun normalizeLibreUrl(raw: String): UrlResult {
    val trimmed = raw.trim().trimEnd('/')
    return when {
        trimmed.startsWith("http://", ignoreCase = true) ->
            UrlResult.Invalid("Cleartext http:// is blocked on modern Android — use https://")
        trimmed.startsWith("https://", ignoreCase = true) -> UrlResult.Valid(trimmed)
        else -> UrlResult.Valid("https://$trimmed")
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*TranslatorUrlValidatorTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Route TranslatorEndpoints through the validator (data-layer safety net)**

In `TranslatorEndpoints.kt`, change `setLibreTranslateBaseUrl` (lines 19-23) to reject invalid URLs by not persisting them, and replace the private `normalize` (lines 52-56) with the shared validator:
```kotlin
    fun setLibreTranslateBaseUrl(url: String?) {
        if (url.isNullOrBlank()) {
            prefs.edit().remove(KEY_LIBRE).apply()
            return
        }
        // Invalid (http://) URLs are not persisted; the UI validates first and shows the reason.
        val result = normalizeLibreUrl(url)
        if (result is UrlResult.Valid) {
            prefs.edit().putString(KEY_LIBRE, result.url).apply()
        }
    }
```
Delete the now-unused private `normalize` function (lines 52-56).

- [ ] **Step 6: Add inline URL error to the config dialog (user-facing reject)**

In `TranslatorPickerDialog.kt`, `ApiKeyDialog`, validate the secondary field when it is a URL before calling `onSave`, and show the reason inline.

After `var secondaryInput by remember { mutableStateOf(secondaryValue) }` (line 286), add:
```kotlin
    var urlError by remember { mutableStateOf<String?>(null) }
```

Change the Save button's `onClick` (lines 373-378) to validate first:
```kotlin
                onClick = {
                    val secondary = if (secondaryLabel != null) secondaryInput.trim() else null
                    if (secondaryIsUrl && !secondary.isNullOrBlank()) {
                        when (val r = normalizeLibreUrl(secondary)) {
                            is UrlResult.Invalid -> { urlError = r.reason; return@DialogButton }
                            is UrlResult.Valid -> Unit
                        }
                    }
                    urlError = null
                    onSave(input.trim().ifBlank { null }, secondary)
                },
```
> `DialogButton`'s `onClick` is a plain lambda, so `return@DialogButton` is not valid there. Instead extract the validation into a local `fun` or guard with an `if/else` so Save is only called when valid. Use this shape:
> ```kotlin
>                 onClick = {
>                     val secondary = if (secondaryLabel != null) secondaryInput.trim() else null
>                     val invalid = if (secondaryIsUrl && !secondary.isNullOrBlank())
>                         (normalizeLibreUrl(secondary) as? UrlResult.Invalid)?.reason else null
>                     if (invalid != null) {
>                         urlError = invalid
>                     } else {
>                         urlError = null
>                         onSave(input.trim().ifBlank { null }, secondary)
>                     }
>                 },
> ```
> Use the `if/else` form (no non-local return).

Render the error under the secondary field. Inside the `if (secondaryLabel != null) { ... }` block (after the secondary `Box`, before its closing brace ~line 364), add:
```kotlin
            if (urlError != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = urlError!!,
                    fontFamily = Newsreader,
                    fontSize = 11.sp,
                    color = DangerTone,
                )
            }
```
Add imports if missing: `import com.example.splitreader.data.local.normalizeLibreUrl`, `import com.example.splitreader.data.local.UrlResult`. `DangerTone` and `Newsreader` are already imported in this file.

- [ ] **Step 7: Compile and run the full unit suite**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all unit tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/splitreader/data/local/TranslatorUrlValidator.kt \
  app/src/test/java/com/example/splitreader/data/local/TranslatorUrlValidatorTest.kt \
  app/src/main/java/com/example/splitreader/data/local/TranslatorEndpoints.kt \
  app/src/main/java/com/example/splitreader/presentation/reader/TranslatorPickerDialog.kt
git commit -m "fix(translator): reject cleartext http:// LibreTranslate URL with clear error (P17)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: P18 — Mark Quick Translate as unofficial/unstable

**Files:**
- Modify: `app/src/main/java/com/example/splitreader/domain/model/TranslationProvider.kt`
- Modify: `app/src/main/java/com/example/splitreader/presentation/reader/TranslatorPickerDialog.kt`

**Interfaces:**
- Produces: `TranslationProvider.stabilityNote: String?` — consumed by `ProviderRow`.

- [ ] **Step 1: Add the stabilityNote field to the enum**

In `TranslationProvider.kt`, add a constructor parameter after `helpUrl` (line 15):
```kotlin
    val helpUrl: String? = null,
    val stabilityNote: String? = null,
```
Set it on `QUICK_TRANSLATE` (after its `tracksUsage = false,` at line 31):
```kotlin
    QUICK_TRANSLATE(
        displayName = "Quick Translate (free, online)",
        requiresApiKey = false,
        requiresNetwork = true,
        category = TranslationProviderCategory.FREE,
        description = "Fast online translation. No setup; needs internet.",
        tracksUsage = false,
        stabilityNote = "Unofficial endpoint — may be rate-limited or break without notice.",
    ),
```
> Trim the "May be unstable." tail from the `description` (shown above) since the dedicated note now carries that meaning. All other providers leave `stabilityNote` at its `null` default.

- [ ] **Step 2: Render the note in ProviderRow**

In `TranslatorPickerDialog.kt`, `ProviderRow`, after the description `Text` block (ends line 222) and before the `if (provider.requiresApiKey)` block, add:
```kotlin
            if (provider.stabilityNote != null) {
                Spacer(Modifier.height(sp.xxs))
                Text(
                    text = provider.stabilityNote!!,
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    letterSpacing = 0.3.sp,
                    color = if (selected) palette.bg.copy(alpha = 0.85f) else WarnTone,
                )
            }
```
`WarnTone`, `JetBrainsMono`, and `sp` (`LocalSpacing.current`) are already available in this file/scope.

- [ ] **Step 3: Compile and run the full unit suite**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/splitreader/domain/model/TranslationProvider.kt \
  app/src/main/java/com/example/splitreader/presentation/reader/TranslatorPickerDialog.kt
git commit -m "feat(translator): flag Quick Translate as unofficial/unstable in the picker (P18)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: P15 — Verify Play purchase signature (fail-open until key set)

**Files:**
- Create: `app/src/main/java/com/example/splitreader/data/billing/PurchaseVerifier.kt`
- Create: `app/src/test/java/com/example/splitreader/data/billing/PurchaseVerifierTest.kt`
- Modify: `app/src/main/java/com/example/splitreader/data/billing/BillingManager.kt`
- Modify: `app/build.gradle.kts`
- Modify: `docs/play_console_release.md`

**Interfaces:**
- Produces: `object PurchaseVerifier { fun verify(base64PublicKey: String, signedData: String, base64Signature: String): Boolean }`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/example/splitreader/data/billing/PurchaseVerifierTest.kt`:
```kotlin
package com.example.splitreader.data.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class PurchaseVerifierTest {

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKeyB64: String = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    private fun sign(data: String): String {
        val sig = Signature.getInstance("SHA1withRSA").apply {
            initSign(keyPair.private)
            update(data.toByteArray(Charsets.UTF_8))
        }
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    @Test fun validSignaturePasses() {
        val data = """{"orderId":"abc","productId":"premium_unlimited"}"""
        assertTrue(PurchaseVerifier.verify(publicKeyB64, data, sign(data)))
    }

    @Test fun tamperedDataFails() {
        val data = """{"orderId":"abc","productId":"premium_unlimited"}"""
        val signature = sign(data)
        assertFalse(PurchaseVerifier.verify(publicKeyB64, data + "x", signature))
    }

    @Test fun tamperedSignatureFails() {
        val data = "payload"
        val bad = sign(data).let { if (it.startsWith("A")) "B" + it.drop(1) else "A" + it.drop(1) }
        assertFalse(PurchaseVerifier.verify(publicKeyB64, data, bad))
    }

    @Test fun wrongPublicKeyFails() {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val otherB64 = Base64.getEncoder().encodeToString(other.public.encoded)
        val data = "payload"
        assertFalse(PurchaseVerifier.verify(otherB64, data, sign(data)))
    }

    @Test fun garbageInputReturnsFalseNotThrow() {
        assertFalse(PurchaseVerifier.verify("not-base64!!", "data", "also-not-base64!!"))
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*PurchaseVerifierTest"`
Expected: FAIL — unresolved reference `PurchaseVerifier`.

- [ ] **Step 3: Implement the verifier**

`app/src/main/java/com/example/splitreader/data/billing/PurchaseVerifier.kt`:
```kotlin
package com.example.splitreader.data.billing

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies a Google Play purchase's signature against the app's base64-encoded RSA public key
 * (Play Console → Monetization setup → Licensing). Returns true iff [base64Signature] is a valid
 * SHA1withRSA signature of [signedData] under that key. Any malformed input returns false — never
 * throws. Uses only java.security + java.util.Base64 so it is JVM-unit-testable without a device.
 */
object PurchaseVerifier {

    fun verify(base64PublicKey: String, signedData: String, base64Signature: String): Boolean =
        try {
            val keyBytes = Base64.getDecoder().decode(base64PublicKey)
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(keyBytes))
            Signature.getInstance("SHA1withRSA").run {
                initVerify(publicKey)
                update(signedData.toByteArray(Charsets.UTF_8))
                verify(Base64.getDecoder().decode(base64Signature))
            }
        } catch (t: Throwable) {
            false
        }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*PurchaseVerifierTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Add the BILLING_PUBLIC_KEY build config field**

In `app/build.gradle.kts`, inside `defaultConfig { ... }`, add (leave the value an empty string placeholder — the real key is set before release):
```kotlin
        buildConfigField("String", "BILLING_PUBLIC_KEY", "\"\"")
```
`buildFeatures { buildConfig = true }` is already enabled — no change needed there.

- [ ] **Step 6: Verify signature before granting premium in BillingManager**

In `BillingManager.kt`, add the import:
```kotlin
import com.example.splitreader.BuildConfig
```
Add a private helper next to `isPremium()` (after line 208):
```kotlin
    /**
     * True if the purchase's signature verifies against BILLING_PUBLIC_KEY. Fail-open: when the key
     * is unset (dev builds), verification is skipped so the purchase flow still works. TODO(P15):
     * set BILLING_PUBLIC_KEY before release so forged purchases are rejected.
     */
    private fun Purchase.signatureOk(): Boolean {
        val key = BuildConfig.BILLING_PUBLIC_KEY
        if (key.isBlank()) {
            Log.w(TAG, "BILLING_PUBLIC_KEY unset — skipping signature check (TODO before release)")
            return true
        }
        return PurchaseVerifier.verify(key, originalJson, signature)
    }
```
In `syncPurchases`, tighten the owned filter (lines 157-159):
```kotlin
            val ownedPremium = purchases.filter {
                it.isPremium() && it.purchaseState == Purchase.PurchaseState.PURCHASED && it.signatureOk()
            }
```
In `handleNewPurchase`, guard the PURCHASED branch (lines 185-190):
```kotlin
            Purchase.PurchaseState.PURCHASED -> {
                if (!purchase.signatureOk()) {
                    Log.w(TAG, "Purchase signature verification failed; not granting premium")
                    _events.tryEmit(PurchaseEvent.Failed(FALLBACK_ERR))
                    return
                }
                acknowledgeIfNeeded(purchase)
                setPremium(true)
                _events.tryEmit(PurchaseEvent.Success)
            }
```

- [ ] **Step 7: Add the release-checklist note**

In `docs/play_console_release.md`, add a line under the relevant billing/release-prep section:
```markdown
- **Set `BILLING_PUBLIC_KEY`** (Play Console → Monetization setup → Licensing → base64 RSA public key)
  in the release build config before `bundleRelease`. While it is blank, purchase-signature
  verification is **skipped** (fail-open) — a forged purchase would not be rejected. (P15)
```
> Place it near the existing billing/`premium_unlimited` setup steps. If no such section exists, add it under the pre-release checklist.

- [ ] **Step 8: Compile and run the full unit suite**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all unit tests pass.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/splitreader/data/billing/PurchaseVerifier.kt \
  app/src/test/java/com/example/splitreader/data/billing/PurchaseVerifierTest.kt \
  app/src/main/java/com/example/splitreader/data/billing/BillingManager.kt \
  app/build.gradle.kts docs/play_console_release.md
git commit -m "feat(billing): verify purchase signature before granting premium; fail-open until key set (P15)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: P14 — Move Keystore/prefs off the main thread

**Files:**
- Modify: `app/src/main/java/com/example/splitreader/presentation/reader/ReaderViewModel.kt`
- Modify: `app/src/main/java/com/example/splitreader/presentation/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/example/splitreader/presentation/home/HomeViewModel.kt`

**Interfaces:** none new (internal threading change).

**Note:** No unit test — this is thread-affinity, not pure logic. Acceptance is code review (no Keystore/prefs on the main thread at VM construction) plus manual profiling that Reader/Settings/Home open without a frame hitch.

- [ ] **Step 1: ReaderViewModel — placeholder config in the initializer, populate on Default**

In `ReaderViewModel.kt`, add the import:
```kotlin
import kotlinx.coroutines.Dispatchers
```
Change the initializer field (line 125) from the synchronous build to a cheap placeholder:
```kotlin
            translatorConfig = TranslatorConfigState(current = progressManager.getTranslatorProvider(), configs = emptyMap()),
```
Add a private helper (near `buildTranslatorConfig`, line 68):
```kotlin
    private fun refreshTranslatorConfig(provider: TranslationProvider) {
        viewModelScope.launch(Dispatchers.Default) {
            val cfg = buildTranslatorConfig(provider)
            _state.update { it.copy(translatorConfig = cfg) }
        }
    }
```
In the existing `init { ... }` block (line 189), add a first-load call:
```kotlin
        refreshTranslatorConfig(progressManager.getTranslatorProvider())
```

- [ ] **Step 2: ReaderViewModel — route on-demand rebuilds through the Default helper**

Replace the four synchronous `buildTranslatorConfig` rebuilds (lines ~457, 464, 470, 475) so the Keystore-touching build runs on `Default`. For the provider-switch (line 457):
```kotlin
        _state.update { it.copy(translatorProvider = provider) }
        refreshTranslatorConfig(provider)
```
For the key save / clear / usage-refresh sites (464, 470, 475), replace:
```kotlin
        _state.update { it.copy(translatorConfig = buildTranslatorConfig(it.translatorProvider)) }
```
with:
```kotlin
        refreshTranslatorConfig(_state.value.translatorProvider)
```
> Verify against the actual method bodies: keep any non-config state update (e.g. setting the provider) synchronous; only the `translatorConfig` rebuild moves to `refreshTranslatorConfig`. Do not change method signatures.

- [ ] **Step 3: SettingsViewModel — same pattern**

In `SettingsViewModel.kt`, add the import:
```kotlin
import kotlinx.coroutines.Dispatchers
```
In `loadState()` (line 108), replace the synchronous build with a placeholder:
```kotlin
        translatorConfig = TranslatorConfigState(current = progressManager.getTranslatorProvider(), configs = emptyMap()),
```
> Add the import for `TranslatorConfigState` if not already present: `import com.example.splitreader.presentation.reader.TranslatorConfigState`.
Add the helper (near `selectProvider`, line 191):
```kotlin
    private fun refreshTranslatorConfig(provider: TranslationProvider) {
        viewModelScope.launch(Dispatchers.Default) {
            val cfg = buildTranslatorConfig(provider)
            _state.update { it.copy(translatorConfig = cfg) }
        }
    }
```
In the existing `init { ... }` (line 74), add:
```kotlin
        refreshTranslatorConfig(progressManager.getTranslatorProvider())
```
Rewrite the four handlers (lines 191-214) to use it:
```kotlin
    fun selectProvider(provider: TranslationProvider) {
        progressManager.setTranslatorProvider(provider)
        _state.update { it.copy(translatorProvider = provider) }
        refreshTranslatorConfig(provider)
    }

    fun configureProvider(provider: TranslationProvider, key: String?, secondary: String?) {
        if (key != null) apiKeyManager.setKey(provider, key)
        if (provider.secondaryLabel != null && secondary != null) translatorEndpoints.setSecondary(provider, secondary)
        refreshTranslatorConfig(_state.value.translatorProvider)
    }

    fun clearProvider(provider: TranslationProvider) {
        apiKeyManager.setKey(provider, null)
        refreshTranslatorConfig(_state.value.translatorProvider)
    }

    fun refreshTranslationUsage() {
        refreshTranslatorConfig(_state.value.translatorProvider)
    }
```
> `apiKeyManager.setKey` and `translatorEndpoints.setSecondary` write to Keystore/prefs. They are user-initiated (tap Save), not screen-open, so they stay on the caller's coroutine here; if profiling shows a hitch, wrap them inside the same `launch(Dispatchers.Default)` in a follow-up. Keep scope to construction-time off-loading for this task.

- [ ] **Step 4: HomeViewModel — run the per-book prefs mapping off the main thread**

In `HomeViewModel.kt`, add the imports:
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
```
In the `uiState` chain (lines 71-107), insert `.flowOn(Dispatchers.Default)` between the closing `}` of the `combine { ... }` transform and `.stateIn(`:
```kotlin
    }.flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )
```
This moves the `progressManager.getLastChapter/isFinished/getExcerpt` SharedPreferences reads (lines 88-92) off the main thread.

- [ ] **Step 5: Compile and run the full unit suite**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all existing tests pass (no new tests here).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/splitreader/presentation/reader/ReaderViewModel.kt \
  app/src/main/java/com/example/splitreader/presentation/settings/SettingsViewModel.kt \
  app/src/main/java/com/example/splitreader/presentation/home/HomeViewModel.kt
git commit -m "perf(vm): build translator config + per-book prefs off the main thread (P14)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Definition of Done (maps to spec §5)

1. **P13** — cache key is SHA-256-based; determinism/difference/known-vector tests green; cache read verifies `originalText == text`. *(Task 1)*
2. **P16** — both backup XML files exclude `billing.xml`. *(Task 2)*
3. **P17** — `http://` Libre URL is rejected with a clear UI reason and not persisted; `https://`/bare host accepted; validator test green. *(Task 3)*
4. **P18** — Quick Translate shows an unofficial/unstable note in the picker. *(Task 4)*
5. **P15** — `PurchaseVerifier` accepts valid and rejects forged signatures (generated-keypair test); `BillingManager` verifies before granting premium; blank `BILLING_PUBLIC_KEY` → fail-open with log; release doc carries the key note. *(Task 5)*
6. **P14** — no Keystore/prefs work on the main thread at Reader/Settings/Home construction; config populated on `Dispatchers.Default`; Home mapping under `flowOn(Default)`. *(Task 6)*
7. `:app:testDebugUnitTest` green; `:app:compileDebugKotlin` succeeds; existing tests unbroken. *(All tasks)*
