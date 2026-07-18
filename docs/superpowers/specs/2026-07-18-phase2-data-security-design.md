# Спека: Фаза 2 — корректность данных и безопасность

> Дата: 2026-07-18 · Ветка: `refactor/phase2-data-security` · Источник: `docs/refactor_plan.md` (P13, P14, P15, P16, P17, P18).
> Единая спека фазы (6 независимых находок). Тестирование — **extraction-first** (чистые JVM-единицы),
> как в Фазе 1. Согласованные решения: P15 — **fail-open + TODO** (verifier закладывается, но при
> пустом ключе проверка пропускается); P17 — **отклонять `http://`** с понятной ошибкой; P14 —
> **плейсхолдер → догрузка** стартового конфига корутиной на `Dispatchers.Default`.

## 1. Цель

Закрыть корректностные и security-находки слоя данных/перевода/биллинга: коллизии ключа кэша
переводов (P13), джанк от Keystore/prefs на main-потоке (P14), отсутствие проверки подписи покупки
(P15), перенос premium-флага бэкапом (P16), тихий фейл `http://`-эндпоинта (P17), непомеченный
неофициальный эндпоинт Quick Translate (P18). Каждая находка — отдельная задача SDD с ревью.

## 2. Находки и дизайн

### P13 — Ключ кэша переводов на SHA-256 · Med (корректность)

**Проблема.** `TranslationRepositoryImpl.kt:42-43`:
`cacheKey = "${provider.name}_${text.hashCode()}_${source.code}_${target.code}"`. 32-битный
`hashCode()` по всему абзацу даёт коллизии → при совпадении хэша для другого абзаца с той же парой
провайдер/языки возвращается **чужой** закэшированный перевод.

**Дизайн.** Новая чистая единица `data/repository/TranslationCacheKey.kt`:
```kotlin
object TranslationCacheKey {
    /** Stable, collision-resistant cache key: provider + SHA-256(text) hex + source + target. */
    fun compute(
        provider: TranslationProvider,
        text: String,
        source: Language,
        target: Language,
    ): String
}
```
- Реализация: `MessageDigest.getInstance("SHA-256")` над `text.toByteArray(UTF_8)` → полный hex
  (64 симв.); формат ключа `"${provider.name}_${hex}_${source.code}_${target.code}"`. Чистый JVM,
  тестируется без эмулятора.
- `TranslationRepositoryImpl.translate` вызывает `TranslationCacheKey.compute(...)` вместо inline-строки.
- **Страховка от коллизии:** на попадании в кэш сверять исходный текст перед возвратом —
  `dao.getCached(key)?.takeIf { it.sourceText == text }?.let { return it.translatedText }`.
  Сущность `TranslationCacheEntity` уже хранит исходный текст (2-е поле), так что сверка почти
  бесплатна и делает возврат чужого перевода невозможным даже теоретически.
  > Точное имя поля исходного текста в `TranslationCacheEntity` свериться при реализации
  > (конструктор `TranslationCacheEntity(cacheKey, text, translated, targetLanguage.code)` —
  > 2-й параметр). Если геттер называется иначе — использовать фактическое имя.
- **Стейл-строки:** записи, закэшированные по старому `hashCode`, перестают матчиться (мёртвый груз;
  кэш регенерируется на лету, «очистить кэш» в Settings продолжает работать). Осознанный компромисс —
  без миграции/очистки таблицы.

**Тест** `app/src/test/.../data/repository/TranslationCacheKeyTest.kt` (JVM):
1. Детерминизм: один вход → один ключ.
2. Разный текст (в т.ч. с одинаковым `hashCode` — подобрать/не обязательно) → разные ключи.
3. Смена `provider` / `source` / `target` меняет ключ.
4. Known-vector: SHA-256 известной строки даёт ожидаемый hex-фрагмент в ключе.

### P14 — Keystore/prefs off-main-thread · Med (джанк)

**Проблема.** (1) `ReaderViewModel.kt:125` и `SettingsViewModel.kt:108` строят стартовый стейт
**синхронно в инициализаторе поля**, вызывая `buildTranslatorConfig` → `buildTranslatorConfigState`
→ `isConfigured()` по всем провайдерам → для ключевых провайдеров `ApiKeyManager.decrypt()` →
Keystore-IPC на main-потоке (до 4 расшифровок при открытии экрана). (2) `HomeViewModel.kt:88-92` —
`progressManager.getLastChapter/isFinished/getExcerpt` (SharedPreferences) внутри `combine.map` на
каждую книгу и каждую эмиссию апстрима.

**Дизайн.**
- **ReaderViewModel / SettingsViewModel:**
  - Инициализатор `_state` использует **дешёвый плейсхолдер** конфига:
    `translatorConfig = TranslatorConfigState(current = progressManager.getTranslatorProvider(), configs = emptyMap())`.
    (Чтение провайдера — дешёвый prefs-read, **без** Keystore.)
  - Ввести приватный `private fun refreshTranslatorConfig(provider: TranslationProvider)` →
    `viewModelScope.launch(Dispatchers.Default) { val cfg = buildTranslatorConfig(provider); _state.update { it.copy(translatorConfig = cfg) } }`.
  - Вызвать `refreshTranslatorConfig(...)` из `init{}` (первичная догрузка) и заменить им синхронные
    перестроения в пользовательских хендлерах (`ReaderViewModel.kt:457,464,470,475` и аналогичные в
    `SettingsViewModel`), чтобы `isConfigured()`/decrypt всегда шли на `Default`.
- **HomeViewModel:** повесить `.flowOn(Dispatchers.Default)` на собранный `combine(...).map { ... }`
  **до** `.stateIn(...)`, чтобы маппинг с per-book prefs-чтениями выполнялся вне main-потока.
- **Тестов нет** — потоковая корректность не даёт чистой единицы. Приёмка: код-ревью (нет
  Keystore/prefs-вызовов в конструкторах/на main) + ручное профилирование открытия Reader/Settings/Home
  (нет фризов). Помечено явно.

### P15 — Проверка подписи покупки, fail-open + TODO · Med (безопасность)

**Проблема.** `BillingManager` подтверждает покупку (`acknowledge`) и гейтит на `PURCHASED`, но
`Purchase.getSignature()` / `getOriginalJson()` **не проверяются** публичным ключом приложения; флаг
кэшируется в открытом `billing.xml` (тривиально правится на рутованном устройстве).
Спека: [Play Billing · Verify purchases](https://developer.android.com/google/play/billing/security#verify).

**Дизайн.** Новая чистая единица `data/billing/PurchaseVerifier.kt`:
```kotlin
object PurchaseVerifier {
    /**
     * Verifies a Play purchase's signature against the app's base64 RSA public key.
     * Returns true iff [base64Signature] is a valid SHA1withRSA signature over [signedData].
     * Returns false on any malformed input (bad key/signature/data) — never throws.
     */
    fun verify(base64PublicKey: String, signedData: String, base64Signature: String): Boolean
}
```
- Реализация — классический Play-паттерн: `X509EncodedKeySpec(Base64.decode(key))` →
  `KeyFactory("RSA")` → `Signature.getInstance("SHA1withRSA")` → `initVerify(pub)` →
  `update(signedData.toByteArray(UTF_8))` → `verify(Base64.decode(signature))`. Любой сбой
  (`InvalidKeySpecException`, `SignatureException`, `IllegalArgumentException`) → `false`.
  Чистый `java.security` + `java.util.Base64` → тестируется на JVM.
  > Использовать `java.util.Base64` (доступен на JVM и с minSdk 26), **не** `android.util.Base64`,
  > чтобы единица оставалась JVM-тестируемой без Robolectric.
- **`BuildConfig.BILLING_PUBLIC_KEY`:** в `app/build.gradle.kts` добавить
  `buildConfigField("String", "BILLING_PUBLIC_KEY", "\"\"")` (дефолт — пустая строка-плейсхолдер;
  реальный ключ подставляется перед релизом, при желании из `local.properties`/gradle-свойства).
- **Интеграция в `BillingManager`** — единая приватная проверка перед выдачей premium:
  ```kotlin
  private fun Purchase.signatureOk(): Boolean {
      val key = BuildConfig.BILLING_PUBLIC_KEY
      if (key.isBlank()) {                // fail-open: ключ не настроен (dev)
          Log.w(TAG, "BILLING_PUBLIC_KEY unset — skipping signature check (TODO before release)")
          return true
      }
      return PurchaseVerifier.verify(key, originalJson, signature)
  }
  ```
  - В `syncPurchases`: фильтр владения дополнить `&& it.signatureOk()`.
  - В `handleNewPurchase`: перед `setPremium(true)` в ветке `PURCHASED` проверить `signatureOk()`;
    при провале — не выдавать premium, `Log.w`, и (для user-flow) `_events.tryEmit(PurchaseEvent.Failed(...))`.
- **Release-блокер:** пункт в `docs/play_console_release.md` — «Выставить `BILLING_PUBLIC_KEY`
  (Play Console → Monetization setup → Licensing) перед сборкой релиза; при пустом ключе проверка
  подписи отключена».

**Тест** `app/src/test/.../data/billing/PurchaseVerifierTest.kt` (JVM):
1. Сгенерировать RSA-пару (`KeyPairGenerator("RSA", 2048)`), подписать payload `SHA1withRSA` →
   `verify(base64(pub), payload, base64(sig))` == true.
2. Изменённый payload → false.
3. Изменённая подпись → false.
4. Другой публичный ключ → false.
5. Мусорный ключ/подпись (не-base64) → false (без исключения).
> Поведение при пустом ключе (fail-open) живёт в `BillingManager.signatureOk`, не в verifier;
> отдельным юнитом не покрывается (требует Android/BuildConfig) — фиксируется ревью.

### P16 — Backup исключает `billing.xml` · Med (безопасность)

**Проблема.** `res/xml/backup_rules.xml` и `res/xml/data_extraction_rules.xml` исключают
`entitlement.xml` (это debug-оверрайд), но **реальный** кэш покупки — `billing.xml`
(`BillingManager` `PREFS = "billing"`). Облачный бэкап/перенос устройства переносит
`premium_owned=true` на новое устройство до следующей синхронизации Play.

**Дизайн.** Добавить `<exclude domain="sharedpref" path="billing.xml"/>`:
- `backup_rules.xml` — в `<full-backup-content>` (рядом с существующими exclude).
- `data_extraction_rules.xml` — в **оба** блока: `<cloud-backup>` и `<device-transfer>`.
- Существующее исключение `entitlement.xml` **сохранить** (безвредно).
- Конфиг-правка, юнит-теста нет. Приёмка — инспекция обоих xml.

### P17 — Отклонять `http://` LibreTranslate · Low

**Проблема.** `TranslatorEndpoints.normalize():52-56` **сохраняет** `http://`, если пользователь его
ввёл явно. При `targetSdk 36` cleartext заблокирован по умолчанию и network-security-config нет →
такой эндпоинт молча не подключится.

**Дизайн.** Новая чистая единица `data/local/TranslatorUrlValidator.kt`:
```kotlin
sealed interface UrlResult {
    data class Valid(val url: String) : UrlResult
    data class Invalid(val reason: String) : UrlResult
}

/** Normalizes a user-entered LibreTranslate base URL, rejecting cleartext http://. */
fun normalizeLibreUrl(raw: String): UrlResult
```
- Правила (совпадают с текущим `normalize`, минус разрешение `http`):
  - `trim`, `trimEnd('/')`.
  - начинается с `http://` (ignore-case) → `Invalid("Cleartext http is blocked on modern Android — use https://")`.
  - начинается с `https://` → `Valid(as-is)`.
  - иначе (голый хост) → `Valid("https://$trimmed")`.
  - пустой/blank вход обрабатывается вызывающим как «сброс к дефолту» (не задача валидатора; см. ниже).
- **`TranslatorEndpoints.setLibreTranslateBaseUrl`:** пустой/blank → удалить ключ (как сейчас);
  иначе прогнать через `normalizeLibreUrl`: `Valid` → сохранить `url`; `Invalid` → **не сохранять**
  (сигнал ошибки наверх). Сигнатуру уточнить при реализации (вернуть `UrlResult`, либо валидировать
  в Settings VM до вызова сеттера — см. seam ниже).
- **Seam ошибки в UI:** Settings VM валидирует ввод LibreTranslate-URL через `normalizeLibreUrl`
  **до** записи; при `Invalid` показывает `reason` пользователю и не персистит. Конкретный механизм
  (новое nullable-поле в `SettingsUiState`, напр. `translatorUrlError`, либо one-shot событие)
  выбрать на этапе плана, сверившись с тем, как Settings сейчас сохраняет secondary-значение
  (`setSecondary`/`setLibreTranslateBaseUrl`).

**Тест** `app/src/test/.../data/local/TranslatorUrlValidatorTest.kt` (JVM):
1. `http://example.com` → `Invalid`.
2. `https://libretranslate.com` → `Valid` (без изменений).
3. голый `myserver.local` → `Valid("https://myserver.local")`.
4. хвостовой слэш/пробелы → тримятся.
5. `HTTP://...` (верхний регистр) → `Invalid` (ignore-case).

### P18 — Пометка нестабильности Quick Translate · Low

**Проблема.** `QuickTranslateProvider` бьёт в недокументированный
`translate.googleapis.com/translate_a/single` (нарушает ToS, подвержен троттлингу/поломкам), но в UI
подан как обычный бесплатный тир.

**Дизайн.** Best-effort остаётся, добавляется честная пометка:
- В пикере переводчиков (`TranslatorPickerDialog` и/или зеркало в Settings) для `QUICK_TRANSLATE`
  показать подпись-предупреждение, напр. «Неофициальный эндпоинт · возможны троттлинг и сбои».
  Реализация — по месту: либо новое поле-заметка на `TranslationProvider` (напр.
  `val stabilityNote: String? = null`, заполнить только для `QUICK_TRANSLATE`), либо локальная
  строка в UI-слое. Предпочтителен доменный `stabilityNote` (одно место истины, переиспользуемо в
  Settings и Reader-пикере).
- Заметка в доках (`docs/refactor_plan.md` P18 уже описывает; добавить строку в пользовательские
  доки/`CLAUDE.md` при необходимости — не обязательно).
- Малорисковый copy-change; отдельного unit-теста не требует.

## 3. Файлы

**Новые:**
- `data/repository/TranslationCacheKey.kt` + тест.
- `data/billing/PurchaseVerifier.kt` + тест.
- `data/local/TranslatorUrlValidator.kt` + тест.

**Правки:**
- `data/repository/TranslationRepositoryImpl.kt` — ключ + сверка текста (P13).
- `presentation/reader/ReaderViewModel.kt`, `presentation/settings/SettingsViewModel.kt`,
  `presentation/home/HomeViewModel.kt` — off-main (P14).
- `data/billing/BillingManager.kt` + `app/build.gradle.kts` (`BILLING_PUBLIC_KEY`) +
  `docs/play_console_release.md` (P15).
- `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` (P16).
- `data/local/TranslatorEndpoints.kt` + Settings VM/UI seam (P17).
- Пикер переводчиков (+ возможно `domain/model/TranslationProvider`) (P18).

## 4. Порядок задач (SDD)

От изолированных к связным:
1. **P13** — `TranslationCacheKey` + wiring в репозиторий + тест.
2. **P16** — backup billing.xml (оба xml).
3. **P17** — `TranslatorUrlValidator` + `TranslatorEndpoints` + Settings seam + тест.
4. **P18** — пометка Quick Translate.
5. **P15** — `PurchaseVerifier` + `BillingManager` + `BuildConfig` + release-doc + тест.
6. **P14** — off-main в Reader/Settings/Home VM.

## 5. Критерии приёмки

1. **P13:** ключ кэша построен на SHA-256; тест на детерминизм/различие/known-vector зелёный; на
   попадании сверяется исходный текст.
2. **P14:** нет Keystore/prefs-вызовов на main-потоке при открытии Reader/Settings/Home (ревью);
   стартовый конфиг — плейсхолдер, реальный догружается на `Default`; Home-маппинг под `flowOn(Default)`.
3. **P15:** `PurchaseVerifier` корректно принимает валидную и отвергает поддельную подпись (тест на
   сгенерированной паре); `BillingManager` проверяет подпись перед выдачей premium, при пустом
   `BILLING_PUBLIC_KEY` — fail-open с логом; release-doc содержит пункт про ключ.
4. **P16:** оба backup-xml исключают `billing.xml`; премиум-флаг не переносится бэкапом.
5. **P17:** `http://` LibreTranslate-URL отвергается с понятной ошибкой в UI и не персистится;
   `https://`/голый хост принимаются; тест валидатора зелёный.
6. **P18:** Quick Translate помечен как неофициальный/нестабильный в пикере.
7. `:app:testDebugUnitTest` зелёный; существующие тесты не сломаны; проект собирается.

## 6. Риск и охват

- Все корректностные/security-находки (P13/P15/P17) вынесены в чистые единицы, покрытые JVM-юнитами
  до/вместе с интеграцией — низкий риск регресса.
- P14 — единственная находка без юнит-теста (потоки); изменение изолировано в трёх VM, поведение
  успешной загрузки не меняется (только поток исполнения + кратковременный плейсхолдер конфига).
- P15 сознательно fail-open при незаданном ключе — зафиксировано как релиз-блокер в доках.
- P16/P18 — конфиг/copy, вне тестируемого ядра.
- Изменения не затрагивают парсерный слой (Фаза 1) и БД-миграции (Фаза 0).
