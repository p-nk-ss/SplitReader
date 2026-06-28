# SplitReader — План релиза 1.0

> **Дата составления:** 2026-06-28
> **Целевой релиз:** Google Play, версия `1.0` (`versionCode = 1`)
> **Принятая стратегия:** релизим сразу с покупкой премиума (Google Play Billing). Free-tier лимит
> **3 книги** остаётся; премиум снимает лимит (unlimited library).

---

## 0. Резюме

Функционально приложение — готовый MVP: все 8 экранов рабочие, Firebase Auth реальный,
парсеры (EPUB/FB2/MOBI) и 6 движков перевода работают. К публикации мешают **конфигурация
сборки** и **недоделанная монетизация** — это и есть основной объём работ перед релизом.

Архитектурный плюс: entitlement уже спроектирован как «шов» (seam). `EntitlementRepository`
— единственный источник правды о премиуме, а `AddBookToLibraryUseCase` уже спрашивает у него
`isPremium` перед добавлением книги. **Phase 2 заменяет только реализацию репозитория на
Google Play Billing — вызывающий код не меняется.** Это сильно упрощает задачу.

Оценка объёма: **~4–6 рабочих дней** до загрузки в Play Console (internal testing).

---

## 1. Карта проблем (по приоритету)

| # | Категория | Проблема | Серьёзность |
|---|-----------|----------|-------------|
| 1 | Сборка | Нет release signing config — AAB подпишется debug-ключом, Play отклонит | 🔴 Блокер |
| 2 | Сборка | `isMinifyEnabled = false` в release — нет R8, большой AAB, debug-логи в проде | 🔴 Блокер |
| 3 | Сборка | ProGuard-правила минимальны (только ML Kit/GMS) — после minify риск runtime-крашей | 🔴 Блокер |
| 4 | Монетизация | Покупка премиума — заглушка (Toast «coming soon»), нет Play Billing | 🔴 Блокер |
| 5 | Зависимости | `security-crypto = 1.1.0-alpha06` — alpha в шифровании ключей переводчиков | 🔴 Блокер |
| 6 | Стабильность | Нет crash-репортинга (Crashlytics не подключён, хотя Firebase уже есть) | 🟡 Важно |
| 7 | Безопасность | `allowBackup = true` — осознанно решить | 🟡 Важно |
| 8 | Чистота | 11 вызовов `Log.d/e/w` попадут в прод (закроется после #2) | 🟡 Важно |
| 9 | Локализация | 6 хардкоженных строк (AlmanacScreen, ReaderScreen) не в `strings.xml` | 🟢 Мелочь |
| 10 | Play Console | Листинг, скриншоты, политика приватности, контент-рейтинг, Data Safety | 🔴 Блокер* |
| 11 | Качество | ~2% покрытия тестами; монолитные файлы; MOBI HUFF/CDIC/AZW3 без поддержки | 🟢 1.1 |

\* Блокер не кода, а процесса публикации.

---

## 2. Пошаговый план

### Шаг 1 — Release signing (Блокер #1)

**Проблема:** в `app/build.gradle.kts` нет `signingConfigs`; release-сборка унаследует debug-ключ.
Play Store такие AAB не принимает.

**Решение:**
1. Сгенерировать upload keystore:
   ```bash
   keytool -genkeypair -v -keystore mirrolit-upload.jks -alias upload \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Хранить пароли **вне репозитория** — в `keystore.properties` (добавить в `.gitignore`):
   ```properties
   storeFile=../mirrolit-upload.jks
   storePassword=***
   keyAlias=upload
   keyPassword=***
   ```
3. В `app/build.gradle.kts`:
   ```kotlin
   val keystoreProps = Properties().apply {
       val f = rootProject.file("keystore.properties")
       if (f.exists()) load(f.inputStream())
   }
   android {
       signingConfigs {
           create("release") {
               storeFile = file(keystoreProps.getProperty("storeFile"))
               storePassword = keystoreProps.getProperty("storePassword")
               keyAlias = keystoreProps.getProperty("keyAlias")
               keyPassword = keystoreProps.getProperty("keyPassword")
           }
       }
       buildTypes {
           release {
               signingConfig = signingConfigs.getByName("release")
               // ...
           }
       }
   }
   ```
4. Рекомендуется **Play App Signing** (Google хранит app-ключ, мы — только upload-ключ).

**Проверка:** `./gradlew bundleRelease` создаёт подписанный `.aab`; `jarsigner -verify` проходит.

---

### Шаг 2 — Включить R8/минификацию + ресурсы (Блокеры #2, #3)

**Проблема:** `isMinifyEnabled = false` (build.gradle.kts:25). Нет обфускации, AAB больше,
debug-логи в проде. ProGuard-правила содержат только 2 keep для ML Kit/GMS.

**Решение:**
1. В release-блоке:
   ```kotlin
   release {
       isMinifyEnabled = true
       isShrinkResources = true
       signingConfig = signingConfigs.getByName("release")
       proguardFiles(
           getDefaultProguardFile("proguard-android-optimize.txt"),
           "proguard-rules.pro"
       )
   }
   ```
2. Расширить `app/proguard-rules.pro` keep-правилами для рефлексии:
   - **Room** (entities/DAO), **Hilt/Dagger**, **Gson** (модели каталога/переводчиков —
     `@SerializedName`, без обфускации полей), **Retrofit/OkHttp**, **Firebase Auth**,
     **Credential Manager / Google ID**.
   - Многие библиотеки поставляют consumer-rules автоматически — но Gson-модели и
     Room-сущности нужно явно `-keep`, иначе runtime-краши на парсинге/миграциях.
3. `Log.d` автоматически вырезается R8 при optimize — снимает проблему #8.

**Проверка (обязательно!):** собрать `bundleRelease`, установить из `.aab` (через `bundletool`
или internal testing track) и **прогнать smoke-тест ключевых потоков**: импорт книги, перевод,
каталог, auth, покупка. Minify-краши проявляются только в release-сборке.

---

### Шаг 3 — Стабилизировать security-crypto (Блокер #5)

**Проблема:** `gradle/libs.versions.toml:25` — `securityCrypto = "1.1.0-alpha06"`, alpha-версия
используется для `EncryptedSharedPreferences` (хранение API-ключей переводчиков).

**Решение (выбрать одно):**
- **A.** Поднять до самой свежей стабильной/rc ветки `androidx.security:security-crypto`
  (либо переехать на `security-crypto-ktx`), проверить совместимость API.
- **B.** Если стабильной нет под нужды — заменить на Jetpack DataStore + ручное шифрование
  через `Tink`/`KeyStore`. Дороже, но без alpha-зависимости.

**Рекомендация:** вариант A. Зашифрованные ключи переводчиков — опциональная фича (онлайн-движки),
не критичный путь, но alpha в крипте лучше убрать до релиза.

**Проверка:** ввести API-ключ переводчика (напр. DeepL), перезапустить процесс — ключ читается.

---

### Шаг 4 — Google Play Billing: покупка премиума (Блокер #4) ⭐ главное

**Текущее состояние:**
- `EntitlementRepository` (domain) — интерфейс-seam, уже внедрён в `AddBookToLibraryUseCase`.
- `EntitlementRepositoryImpl` — Phase 1: локальный флаг в SharedPreferences + debug-`setPremium()`.
- `FREE_BOOK_LIMIT = 3` (`AddBookToLibraryUseCase.kt:49`) — лимит на *текущее* число книг
  (per device), премиум обходит лимит через `isPremium`.
- UI: `LibraryLimitDialog` + кнопка «Upgrade» в `HomeScreen.kt:183` и `CatalogScreen.kt` сейчас
  показывают Toast `library_upgrade_coming_soon`.

**Решение — реализовать Phase 2:**

1. **Зависимость:** добавить `com.android.billingclient:billing-ktx` (актуальная стабильная,
   Billing Library 7+) в `libs.versions.toml` + `app/build.gradle.kts`.

2. **Play Console:** создать продукт.
   - Тип: **one-time product (INAPP)** «Unlimited library» — *решение принято*: разовая покупка,
     лимит снимается навсегда. Честнее перед пользователем для фичи «безлимит книг» и проще в
     реализации/restore, чем подписка.
   - Зафиксировать `productId`, напр. `premium_unlimited`.

3. **Новый слой `data/billing/`:**
   - `BillingManager` — обёртка над `BillingClient`: подключение, `queryProductDetails`,
     `launchBillingFlow`, `PurchasesUpdatedListener`, **обязательно `acknowledgePurchase`**
     (неподтверждённые покупки Google авто-возвращает через 3 дня).
   - На старте/возврате в приложение — `queryPurchasesAsync` для **восстановления покупки**
     (reinstall, новое устройство).

4. **Заменить реализацию seam (вызывающий код НЕ трогаем):**
   - `EntitlementRepositoryImpl` → `isPremium: Flow<Boolean>` теперь выводится из состояния
     Billing (есть подтверждённая покупка `premium_unlimited` → `true`).
   - Кэшировать последнее известное состояние локально (offline-доступ к премиуму между запусками).
   - `setPremium()` оставить только под `BuildConfig.DEBUG` (как и задумано интерфейсом).
   - Обновить DI-binding в соответствующем модуле.

5. **UI покупки:**
   - В `LibraryLimitDialog` `onUpgrade` → запуск `launchBillingFlow` (вместо Toast).
   - Показать цену из `ProductDetails` (не хардкодить).
   - Состояния: загрузка / успех (закрыть диалог, лимит снят реактивно через `isPremium`) /
     отмена / ошибка.
   - В `ProfileScreen`/`SettingsScreen` добавить «Restore purchases» и индикацию статуса премиума.
   - Убрать строку `library_upgrade_coming_soon` после миграции.

6. **Edge-cases:** покупка в процессе (`PENDING`), отказ платежа, отсутствие Google Play на
   устройстве, refund/возврат (снять премиум при отзыве).

**Проверка:** залить internal testing build, через **license tester**-аккаунт пройти покупку
(тестовая оплата), убедиться: лимит снят, премиум переживает перезапуск и переустановку
(restore), debug-`setPremium` недоступен в release.

---

### Шаг 5 — Crashlytics (Важно #6)

**Проблема:** Firebase уже подключён (Auth/Drive, есть `google-services.json`), но Crashlytics
нет — в проде не будет видимости падений.

**Решение:**
1. Добавить `firebase-crashlytics` (через существующий Firebase BOM) + Gradle-плагин
   `com.google.firebase.crashlytics`.
2. Включить отчёты только для release-сборок.
3. Заменить «глухие» `catch (e: Exception)` в критичных местах (`ParseBookUseCase:45`,
   `CatalogViewModel:85,125`) на логирование нефатала в Crashlytics + информативное сообщение.

**Проверка:** тестовый краш виден в Firebase Console.

---

### Шаг 6 — Backup-политика (Важно #7)

**Проблема:** `AndroidManifest.xml:21` — `allowBackup = true`. Секреты уже исключены
backup-rules, но стоит решить осознанно.

**Решение:** оставить `true`, но **проверить `dataExtractionRules`/`fullBackupContent`** —
убедиться, что исключены `entitlement` prefs и зашифрованные ключи переводчиков. Либо
выставить `allowBackup = false`, если бэкап настроек не нужен.

---

### Шаг 7 — Мелочи перед сборкой (#8, #9)

- Хардкоженные строки → `strings.xml`: `AlmanacScreen.kt` (168–169, 202, 286, 356),
  `ReaderScreen.kt:1845` (~15 мин).
- (Опц.) namespace `com.example.splitreader` оставляем — на Play влияет только `applicationId`
  (`io.mirrolit.app`, корректный). Менять namespace необязательно и рискованно.

---

### Шаг 8 — Подготовка Play Console (Блокер #10, процессный)

- [ ] Создать приложение в Play Console (applicationId `io.mirrolit.app`).
- [ ] **Политика приватности** (URL обязателен — есть Firebase Auth, сбор email).
- [ ] **Data Safety** форма: какие данные собираются (email/аккаунт, файлы книг локально),
      ML Kit on-device, опциональные онлайн-переводчики отправляют текст на сторонние API —
      указать честно.
- [ ] Контент-рейтинг (анкета IARC).
- [ ] Листинг: название, описания, **скриншоты** (телефон + планшет), feature graphic, иконка.
- [ ] Настроить **Play App Signing**.
- [ ] Объявить in-app продукт `premium_unlimited` (из Шага 4).
- [ ] Target API level 36 — соответствует требованиям Play на 2026.

---

### Шаг 9 — Финальная проверка релиза

- [ ] `./gradlew bundleRelease` собирает подписанный `.aab` с включённым R8.
- [ ] Smoke-тест release-сборки на физическом устройстве: импорт EPUB/FB2/MOBI, перевод
      (offline ML Kit + 1 онлайн-движок), каталог (Gutenberg + Standard Ebooks), auth
      (регистрация + Google), достижение лимита 3 книги, **покупка премиума + restore**, TTS,
      закладки/заметки, статистика.
- [ ] Проверить, что debug-`setPremium` недоступен, логирование вырезано.
- [ ] Залить на **internal testing**, проверить покупку через license-tester, затем closed/open
      testing → production.

---

## 3. Что осознанно откладываем на 1.1

| Тема | Причина отсрочки |
|------|------------------|
| Локализация UI (12 языков сейчас только для перевода книг, не интерфейса) | Не блокер; первый рынок — англоязычный |
| Тестовое покрытие ViewModel/Repository/UI (~2% сейчас) | Риск регрессий есть, но не блокирует публикацию |
| Рефактор монолитов (`ReaderScreen` ~2145, `HomeScreen` ~1315, `ReaderViewModel`) | Техдолг, поведение корректное |
| MOBI HUFF/CDIC, AZW3/KF8 | Дают понятные ошибки, не краши; отдельные парсеры (seam уже есть) |

---

## 4. Контрольный чек-лист блокеров

```
🔴 Код / сборка:
  [ ] 1. Release signing config + keystore вне репозитория
  [ ] 2. isMinifyEnabled = true + isShrinkResources = true
  [ ] 3. Расширенные ProGuard-правила (Room, Gson, Hilt, Retrofit, Firebase)
  [ ] 4. Google Play Billing — реальная покупка премиума + restore (заменить Toast)
  [ ] 5. security-crypto: alpha → стабильная

🟡 Желательно до релиза:
  [ ] 6. Firebase Crashlytics (release-only)
  [ ] 7. Проверить/решить allowBackup + exclude секретов
  [ ] 8. Log.* — снимется автоматически после R8
  [ ] 9. Хардкоженные строки → strings.xml

🔴 Процесс публикации:
  [ ] 10. Play Console: privacy policy, Data Safety, рейтинг, листинг, скриншоты,
          App Signing, объявить in-app продукт premium_unlimited
```

**Оценка:** ~4–6 рабочих дней до загрузки в internal testing (основной объём — Шаг 4, Billing).
