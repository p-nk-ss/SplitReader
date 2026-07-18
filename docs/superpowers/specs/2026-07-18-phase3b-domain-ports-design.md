# Спека: Фаза 3 · под-проект B — доменные интерфейсы для data-менеджеров (P20)

> Дата: 2026-07-18 · Ветка: `refactor/phase3b-domain-ports` · Источник: `docs/refactor_plan.md` (P20).
> Второй (финальный) под-проект Фазы 3: A — утечка Room-моделей (P19, слито) → **B — утечка
> data.local-менеджеров (P20, этот док)**. Согласовано: **по capability (6 задач)**; ReadingProgressManager
> прячется за **одним** интерфейсом `ReadingPreferences`.

## 1. Цель

Убрать зависимость presentation (и одного use-case) от конкретных классов `data.local.*`. Ввести
доменные интерфейсы («порты»); менеджеры остаются в data и реализуют их; presentation инъектит
интерфейсы. Это завершает Фазу 3: после 3B `domain/**` **не импортирует `data.**` вообще** (снимается
последний импорт — `ReadingProgressManager` в `TranslateTextUseCase`), а presentation тестируема с
фейками. Поведение приложения не меняется (чистый рефактор).

## 2. Область (что течёт сегодня)

`presentation`-потребители конкретных `data.local`-классов:
- `ReadingProgressManager` → `ReaderViewModel`, `SettingsViewModel`, `HomeViewModel`, `AppThemeViewModel`;
  **и `domain/usecase/TranslateTextUseCase`** (последний domain→data импорт).
- `ApiKeyManager` → `ReaderViewModel`, `SettingsViewModel`.
- `TranslatorEndpoints` → `ReaderViewModel`, `SettingsViewModel`, `TranslatorConfig`.
- `TranslationUsageTracker` (+ data-класс `TranslationUsage`) → `ReaderViewModel`, `SettingsViewModel`,
  `TranslatorConfig`, `TranslatorPickerDialog`.
- `TextToSpeechManager` → `ReaderViewModel`, `SettingsViewModel`, `WordsViewModel`.
- `TranslationDao` (сырой DAO) → `SettingsViewModel` (счётчик/очистка кэша переводов).

## 3. Дизайн

### 3.1. Паттерн экстракции (для каждого менеджера)
- Доменный интерфейс в `domain/repository/` (следуем текущему размещению интерфейсов).
- Менеджер **остаётся в `data.local`** и добавляет `: Interface` (реализует его существующими методами).
- Hilt: `@Provides`-upcast в DI-модуле — `fun provideX(m: Manager): Interface = m`. Менеджер по-прежнему
  `@Inject`-конструируется и `@Singleton`; провайдер лишь поднимает тип. (Не требует abstract `@Binds`-модуля;
  согласуется с текущими object-модулями на `@Provides`.)
- Интерфейс включает **только методы, нужные presentation**. Методы, которые дёргает лишь data-слой
  (напр. `ApiKeyManager.getGoogleCloudKey`, `TranslatorEndpoints.getLibreTranslateBaseUrl`,
  `TranslationUsageTracker.record`), **остаются конкретными** на классе — data→data допустимо, интерфейс
  не раздувается.

### 3.2. Интерфейсы и объём

| Менеджер | Интерфейс | Методы интерфейса (для presentation) |
|----------|-----------|--------------------------------------|
| `ReadingProgressManager` | `ReadingPreferences` | весь публичный API менеджера (per-book прогресс + глобальные настройки + `readerThemeName: StateFlow<String>`) — единый интерфейс, менеджер implements |
| `TextToSpeechManager` | `SpeechSynthesizer` | `speak(text, langCode)`, `setRate(rate)`, `setPitch(pitch)`, `shutdown()` |
| `TranslationUsageTracker` | `TranslationUsageStats` | `usage(provider): TranslationUsage`, `reset(provider)` (`record` остаётся на классе) |
| `ApiKeyManager` | `TranslatorKeyStore` | `getKey(provider): String?`, `setKey(provider, value)` |
| `TranslatorEndpoints` | `TranslatorEndpointStore` | `getSecondary(provider): String`, `setSecondary(provider, value)` |

> `ReadingPreferences` — большой интерфейс (~40 методов). Он **зеркалит фактический публичный API**
> `ReadingProgressManager` (при реализации свериться с текущим файлом — включить все публичные члены,
> включая `readerThemeName`). Это осознанный компромисс (единый интерфейс, YAGNI) вместо разбиения на
> Progress/Preferences.

### 3.3. `TranslationUsage` → domain
Data-класс `TranslationUsage(charactersThisMonth, monthlyLimit)` переносится в `domain/model/`
(его возвращает `TranslationUsageStats.usage` и он течёт в UI). Импорты в presentation обновляются на
доменный тип; `TranslationUsageTracker` (data) строит доменный `TranslationUsage`.

### 3.4. Утечка `TranslationDao` в SettingsViewModel
Закрывается методами на **существующем** `TranslationRepository` (domain), а не новым интерфейсом:
добавить `suspend fun cachedCount(): Int` и `suspend fun clearCache()` (имена/сигнатуры выверить по
фактическому использованию DAO в `SettingsViewModel` при планировании — сохранить поведение). `SettingsViewModel`
инъектит `TranslationRepository` вместо `TranslationDao`.

## 4. Декомпозиция (6 задач — по capability)

Каждая задача: интерфейс(+перенос модели, где нужно) + `@Provides` + свап потребителей;
компилируется, тесты зелёные, соответствующий `data.local`-класс исчезает из presentation
(а для задачи 1 — и из domain).

1. **ReadingPreferences** — `ReadingProgressManager: ReadingPreferences`; `@Provides`; свап
   `ReaderViewModel/SettingsViewModel/HomeViewModel/AppThemeViewModel` + `TranslateTextUseCase`
   (**снимает последний domain→data импорт**).
2. **SpeechSynthesizer** — `TextToSpeechManager`; свап `ReaderViewModel/SettingsViewModel/WordsViewModel`.
3. **TranslationUsageStats + TranslationUsage→domain** — `TranslationUsageTracker`; свап
   `ReaderViewModel/SettingsViewModel/TranslatorConfig/TranslatorPickerDialog`.
4. **TranslatorKeyStore** — `ApiKeyManager`; свап `ReaderViewModel/SettingsViewModel`.
5. **TranslatorEndpointStore** — `TranslatorEndpoints`; свап `ReaderViewModel/SettingsViewModel/TranslatorConfig`.
6. **TranslationRepository count/clear** — убрать `TranslationDao` из `SettingsViewModel`.

## 5. Тестирование
- Тонкая экстракция интерфейсов (Android-backed менеджеры, без новой логики) — новых unit-тестов почти
  нет; приёмка через компиляцию + существующие тесты + grep-гейты (как в P14).
- `TranslationUsage`-перенос и `TranslationRepository` count/clear при желании покрываются мелким
  JVM/фейк-тестом, если это даёт чистую единицу; не обязательно.
- Существующие тесты не ломаются.

## 6. Приёмка (3B и вся Фаза 3)
1. `presentation/**` не импортирует `data.local.{ReadingProgressManager, TextToSpeechManager,
   TranslationUsageTracker, ApiKeyManager, TranslatorEndpoints, TranslationDao, TranslationUsage}` —
   только доменные интерфейсы/модели.
2. **`domain/**` не импортирует `com.example.splitreader.data.**` вообще** (grep-гейт пуст) — цель Фазы 3.
3. Менеджеры остаются в `data.local`, реализуют доменные интерфейсы; Hilt связывает интерфейс→менеджер.
4. Поведение без изменений; `:app:testDebugUnitTest` зелёный; `:app:compileDebugKotlin` успешен.

## 7. Риск и охват
- Механическая экстракция интерфейсов, изолированная по capability; каждая задача компилируется и зелёная.
- Риск — забыть публичный член в большом `ReadingPreferences`: снимается сверкой с фактическим файлом +
  компиляцией (менеджер должен реализовать интерфейс — компилятор поймает недостающее).
- Вне области: сами реализации менеджеров (логика prefs/Keystore/TTS) не меняются; парсер, БД-схема,
  billing — не трогаются. Два KDoc-док-линка на `data.*` в `DriveRepository`/`CatalogSourceClient`
  (не импорты) — косметика, вне P20.
