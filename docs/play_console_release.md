# Mirrolit — Play Console Release Runbook

> Пошаговый чек-лист публикации в Google Play. Это **организационные** шаги (вне кода).
> Все кодовые блокеры уже закрыты — см. `docs/release_plan.md`.

## Вводные (значения этого приложения)

| Поле | Значение |
|------|----------|
| Package / Application ID | `io.mirrolit.app` |
| App name | Mirrolit |
| versionName / versionCode | `1.0` / `1` |
| min / target SDK | 26 / 36 |
| Permissions | только `INTERNET` |
| Upload keystore | `mirrolit-upload.jks` (gitignored, пароль в менеджере) |
| In-app продукт | `premium_unlimited` (one-time, «unlimited library») |
| Артефакт | `app/build/outputs/bundle/release/app-release.aab` |
| Иконка листинга | `app/src/main/ic_launcher-playstore.png` (512×512) |
| Privacy policy | `docs/privacy_policy.md` → нужно захостить, см. Шаг 2 |

**Аккаунт разработчика:** нужен Google Play Developer account (разовый взнос $25, верификация личности/адреса может занять до нескольких дней — сделайте заранее).

**Порядок:** Шаг 1 → 4 (продукт) можно параллельно с 2–3, но **продукт `premium_unlimited` должен быть «Active» до заливки на internal testing**, иначе покупку не протестировать. Реальная покупка тестируется только на треке (Шаг 5), не локально.

---

## Шаг 1 — Создать приложение + Play App Signing

1. **Play Console → All apps → Create app.**
   - App name: `Mirrolit`
   - Default language: English (United States) (UI пока EN; локализация — в 1.1)
   - App or game: **App**
   - Free or paid: **Free** (монетизация — через in-app покупку, не платная установка)
   - Декларации: подтвердить Developer Program Policies + US export laws.
2. **Зафиксировать Application ID.** При первой заливке AAB консоль привяжет пакет `io.mirrolit.app` к приложению. Сменить позже **нельзя** — проверьте, что в `app/build.gradle.kts` именно `io.mirrolit.app`.
3. **Play App Signing** (включается по умолчанию для новых приложений):
   - Google хранит **app signing key**, вы — только **upload key** (`mirrolit-upload.jks`).
   - При первой загрузке AAB консоль примет ваш upload-ключ как ключ загрузки. Ничего отдельно генерировать не нужно — наш AAB уже подписан им.
   - **Сохраните бэкап `mirrolit-upload.jks` + пароль** (потеря upload-ключа решается сбросом через поддержку, но это простой на несколько дней).
4. (Опц.) Включить **2-Step Verification** на аккаунте — Google требует для публикации.

**Результат:** пустое приложение `Mirrolit` в консоли, App Signing активен.

---

## Шаг 2 — Privacy policy, Data Safety, контент-рейтинг

### 2.1 Privacy policy (URL обязателен)
Есть Firebase Auth (сбор email) → политика конфиденциальности обязательна.
1. Взять готовый черновик `docs/privacy_policy.md`, при необходимости отредактировать (контактный email, название юрлица/разработчика).
2. **Захостить по публичному URL.** Варианты:
   - GitHub Pages (бесплатно): закоммитить как `index.html`/`privacy.md` в отдельный репозиторий или ветку `gh-pages` → URL вида `https://<user>.github.io/mirrolit-privacy/`.
   - Google Sites / Notion public page / любой статический хостинг.
3. В консоли: **Policy → App content → Privacy policy** → вставить URL.

### 2.2 Data Safety форма
**App content → Data safety.** Заполнять честно по тому, что приложение реально делает:

| Тип данных | Собирается? | Куда / зачем | Заметки для формы |
|------------|-------------|--------------|-------------------|
| **Email** | Да | Firebase Auth (аккаунт/синк) | Collected, **not** shared, шифруется в передаче, можно удалить (есть «Delete account») |
| **Name** | Да (опц., при регистрации) | Firebase Auth (профиль) | Collected, not shared |
| **App activity / files** | Книги — **локально** | парсинг на устройстве | НЕ покидают устройство (если не используется онлайн-переводчик) |
| **Текст книг → онлайн-переводчик** | Опц., только если юзер настроил API-ключ | DeepL/Google Cloud/Azure/LibreTranslate | **Shared with third parties** — указать; происходит только по выбору пользователя |
| **Crash logs / диагностика** | Да | Firebase Crashlytics | Collected, for analytics/app functionality |
| **Device/other IDs** | Да (Crashlytics/Play) | диагностика | Collected |
| **Google Drive content** | Опц. | импорт выбранного файла (read-only OAuth) | обрабатывается на устройстве |

Ключевые ответы:
- **Data encrypted in transit:** Yes (HTTPS везде).
- **Users can request deletion:** Yes — в приложении есть удаление аккаунта (Firebase `delete()`), плюс контактный email.
- ML Kit перевод — **on-device**, данные не передаются (для дефолтного движка). Это сильный плюс, отметьте в описании.

### 2.3 Контент-рейтинг
**App content → Content ratings** → пройти анкету IARC.
- Категория: **Reference, News, or Educational** (или Books).
- Ответы: нет насилия/секса/наркотиков/азартных игр (само приложение). Книги — пользовательский контент из публичных каталогов; приложение не таргетирует контент. Ожидаемый рейтинг: **Everyone / PEGI 3** (возможно с пометкой про пользовательский веб-контент из каталогов).
- Указать контактный email.

### 2.4 Прочие декларации App content
Заполнить все обязательные секции (иначе релиз не уйдёт):
- **Target audience & content:** аудитория 18+ или 13+ (не «для детей» — иначе спец-требования). Рекомендую **не** отмечать «appeals to children».
- **Ads:** приложение **не** содержит рекламы → No ads. (Можно получить бейдж «No ads».)
- **News app:** No.
- **Data safety:** (см. 2.2).
- **Government app / Financial / Health:** No.
- **COVID / etc.:** No.

---

## Шаг 3 — Store listing (описания, скриншоты, иконка)

**Grow → Store presence → Main store listing.**

### 3.1 Текст
- **App name (30 симв.):** `Mirrolit`
- **Short description (80 симв.):** напр. `Read EPUB & FB2 with live side-by-side translation in 12 languages.`
- **Full description (4000 симв.):** перечислить фичи: сплит-перевод оригинал↔перевод, авто-определение языка, 12 целевых языков, on-device ML Kit (без интернета и без передачи данных), опциональные онлайн-движки (DeepL/Google/Azure/LibreTranslate), каталог публичных книг (Project Gutenberg, Standard Ebooks), импорт EPUB/FB2/MOBI, Google Drive, TTS, закладки/заметки/словарь, статистика чтения. Упомянуть free-tier (3 книги) + premium (безлимит).

### 3.2 Графика
| Ассет | Требование | Где взять |
|-------|------------|-----------|
| **App icon** | 512×512 PNG, ≤1 МБ | `app/src/main/ic_launcher-playstore.png` ✅ готов |
| **Feature graphic** | 1024×500 PNG/JPG | нужно создать (баннер с логотипом на коричневом фоне) |
| **Phone screenshots** | мин. 2 (до 8), 16:9/9:16, ≥320px | снять с устройства/эмулятора |
| **Tablet screenshots** | мин. для планшетов (7"/10") если поддерживаете планшеты | приложение `sensorLandscape` + tablet-friendly → желательны |

Скриншоты снять с реальных экранов: Library, Reader (сплит-перевод), Catalog, Almanac, Settings. Можно использовать эмулятор Pixel Tablet (уже настроен).
> **Важно:** на скриншотах не должно быть копирайт-защищённых книг — используйте книги из каталога (public domain).

### 3.3 Категоризация
- **App category:** Books & Reference.
- **Tags:** ebook reader, translation, language learning.
- **Contact details:** email (обяз.), сайт (опц.), телефон (опц.).

---

## Шаг 4 — In-app продукт `premium_unlimited` + license tester

### 4.1 Создать продукт
**Monetize → Products → In-app products → Create product.**
- **Product ID:** `premium_unlimited` — **должен точно совпадать** с `BillingManager.PREMIUM_PRODUCT_ID`. Изменить ID после создания нельзя.
- **Name / Description:** напр. `Unlimited library` / `Remove the 3-book limit forever.`
- **Price:** установить (напр. $4.99) — Google посчитает локальные цены.
- **Status:** **Activate** (иначе `queryProductDetailsAsync` вернёт пусто и кнопка Upgrade покажет «Store not ready»).

> Тип — **one-time product (managed)**, не подписка. Соответствует коду (INAPP, `acknowledgePurchase`, без consume — премиум остаётся навсегда и восстанавливается через `queryPurchasesAsync`).

> Для продажи in-app нужен **Payments profile** (Merchant account) в Play Console — настройте в **Setup → Payments profile**, иначе продукт не активировать.

### 4.2 License testers (тестовые покупки без реальных денег)
**Play Console → Setup → License testing** (на уровне аккаунта):
- Добавить Google-аккаунты тестировщиков (напр. ваш `pankaz6jha@gmail.com`).
- Эти аккаунты на тестовых треках платят «тестовой» картой — деньги не списываются, поток покупки настоящий.
- Тестировщик должен быть и в списке тестеров трека (Шаг 5), и в License testing.

### 4.3 Set `BILLING_PUBLIC_KEY` (purchase signature verification)
- **Set `billingPublicKey`** in `keystore.properties` (Play Console → Monetization setup →
  Licensing → base64 RSA public key) before `bundleRelease`. While it is blank, purchase-signature
  verification is **skipped** (fail-open) — a forged purchase would not be rejected. (P15)
- This is now also a **build-time guard**: once `keystore.properties` exists (a genuine signed
  release), `bundleRelease`/`assembleRelease` **hard-fails with a `GradleException`** if
  `billingPublicKey` is blank — a blank-key signed release can no longer be produced. CI /
  fresh clones with no `keystore.properties` are unaffected (release just builds unsigned, as
  before).

---

## Шаг 5 — Internal testing → закрытое/открытое → production

### 5.1 Подготовить и залить AAB
1. Убедиться, что `keystore.properties` на месте → собрать:
   ```
   ./gradlew :app:bundleRelease
   ```
   Артефакт: `app/build/outputs/bundle/release/app-release.aab` (подписан upload-ключом, R8 включён, Crashlytics mapping заливается).
2. **Release → Testing → Internal testing → Create new release.**
   - Загрузить `app-release.aab`.
   - Release name: `1.0 (1)`. Release notes: краткое описание.
   - Сохранить → Review → **Start rollout to Internal testing**.
3. **Testers:** создать список тестеров (email'ы) во вкладке Testers; включить туда license-tester аккаунты. Скопировать **opt-in URL**, открыть на тест-устройстве, принять приглашение, установить из Play.

> Internal testing доступен почти мгновенно (минуты), без полного ревью. Closed/Open/Production проходят ревью (от часов до нескольких дней).

### 5.2 Прогнать платный поток (то, что нельзя локально)
На устройстве с тест-аккаунтом, установив сборку **из Play** (не adb!):
- [ ] Достичь лимита 3 книги → диалог Upgrade показывает **цену** (значит `productDetails` загрузились).
- [ ] Нажать Upgrade → открывается **Play purchase sheet** с пометкой *Test card, no charge* → завершить покупку.
- [ ] Премиум разблокирован: лимит снят, Settings → Premium показывает «Premium active».
- [ ] Убить и перезапустить приложение → премиум сохранился (offline-кэш + acknowledge).
- [ ] Переустановить / другой девайс с тем же аккаунтом → **Settings → Restore purchase** возвращает премиум (`queryPurchasesAsync`).
- [ ] Проверить, что в release-сборке **нет** debug-тоггла Premium (он под `BuildConfig.DEBUG`).
- [ ] (Опц.) В Play Console отменить тестовую покупку → проверить, что при следующем sync премиум снимается.

### 5.3 Промоушн по трекам
- **Internal → Closed testing (alpha):** более широкий круг, проходит ревью. Собрать обратную связь.
- **Closed → Open testing (beta):** публичный opt-in (опц.).
- **Open/Closed → Production:** **Release → Production → Create release** (можно «промоутить» тот же AAB). Настроить **staged rollout** (напр. 20% → 100%).
- На Production заполнить **Countries/regions** (где доступно) и пройти все App content декларации (иначе кнопка Rollout заблокирована).

### 5.4 Перед Production — финальная проверка
- [ ] Все секции **App content** зелёные (Privacy policy, Data safety, Content rating, Target audience, Ads, Government).
- [ ] Store listing заполнен (иконка, feature graphic, ≥2 скриншота, описания).
- [ ] Продукт `premium_unlimited` = Active, Payments profile настроен.
- [ ] `billingPublicKey` in `keystore.properties` set to the real Licensing key (not blank) — see
      4.3 (P15). (Belt-and-suspenders: `bundleRelease` now fails outright if it's blank while a
      keystore is present, so this can't silently slip through.)
- [ ] Платный поток + restore проверены на тест-треке.
- [ ] versionCode уникален (для след. релизов инкрементить `versionCode`).

---

## Памятка по будущим релизам
- Инкрементить `versionCode` (и `versionName`) в `app/build.gradle.kts` перед каждой заливкой.
- Тот же `mirrolit-upload.jks` подписывает все апдейты — не терять.
- R8 mapping заливается автоматически (Crashlytics) → стектрейсы крашей читаемы в Firebase.
