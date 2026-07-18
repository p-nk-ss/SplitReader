# Спека: Фаза 4 — полировка (P21, P22, P24, P25 + дешёвые follow-up)

> Дата: 2026-07-18 · Ветка: `refactor/phase4-polish` · Источник: `docs/refactor_plan.md` (P21, P22, P24, P25).
> Финальная фаза рефактора. Согласовано: **P23 (файлы-гиганты) отложен** (крупный/рискованный, не блокирует
> релиз); **P24 — полный `ReadingDefaults`**; **включены дешёвые follow-up** (P14/P17-хвосты, KDoc-линки).
> Import-sweep пропущен (нет ktlint-конфига).

## 1. Цель

Снять оставшийся тех-долг качества UI/навигации/констант: идиоматичный back-stack вкладок (P21),
убрать `!!` (P22), единый источник reading-констант без расхождений (P24), логировать проглоченные
исключения и устранить дублирующиеся DB-подписки (P25), плюс закрыть накопленные дешёвые follow-up
предыдущих фаз. Поведение приложения не меняется, кроме двух осознанных фиксов дефолтов (см. P24).

## 2. Задачи и дизайн

### P21 — Идиоматичная навигация вкладок · Low
**Где:** `presentation/navigation/SplitReaderNavHost.kt:77-85` — 5 таб-переходов (Home/Catalog/Almanac/
Words/Settings) используют только `launchSingleTop = true`.
**Проблема:** бэкстек накапливается, состояние вкладок не сохраняется; «Назад» неидиоматично.
**Дизайн:** каждый таб-переход получает (паттерн NowInAndroid):
```kotlin
navController.navigate(route) {
    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```
Вынести в локальный helper `fun NavHostController.navigateToTab(route: String)` (в этом же файле), заменить
5 вызовов. Account/auth-переход (`PROFILE_ROUTE`/`AUTH_ROUTE`) — **не** таб, остаётся как есть
(`launchSingleTop` только).

### P22 — Убрать `!!` на nullable UI-состоянии · Low
**Где:** `presentation/home/HomeScreen.kt:309,311` (`uiState.lastBook!!`), `:1156` (`coverBitmap!!`).
**Дизайн:** захватить в локальную `val` под `if (… != null)` и опираться на smart-cast:
- `val lastBook = uiState.lastBook; if (lastBook != null) { … book = lastBook … onOpenFromLibrary(lastBook.uri) }`.
- `val bitmap = coverBitmap; if (bitmap != null) { Image(bitmap = bitmap, …) }`.
Поведение идентично; статические предупреждения по `!!` сняты.

### P24 — Единый `ReadingDefaults` (полный) · Low
**Где расхождения:** диапазон размера шрифта `14f..24f` (`SettingsViewModel.setTextSize` coerceIn + слайдер
`SettingsControls:129 valueRange`) против `14f..30f` (`ReaderViewModel:392` pinch coerceIn); дефолт
`paragraphSpacing` = **8f** (`ReadingProgressManager.getParagraphSpacing()`) против **18f** в UI-стейтах
(`SettingsViewModel:42`, `ReaderUiState:24/55`, `ReaderViewModel:98`).
**Дизайн:** новый `domain/model/ReadingDefaults.kt` — чистый Kotlin-объект (без android), единый источник:
- **Дефолты** всех reading-настроек, дублируемых между `ReadingProgressManager` getFloat-дефолтами и
  UI-стейтами: `lineHeightMultiplier=1.5f`, `splitRatio=0.5f`, `showTranslation=true`,
  `showIllustrations=true`, `horizontalMargin=12f`, `textSize=16f`, `readingFont="SERIF"`,
  `paragraphSpacing=18f`, `letterSpacing=0f`, `textIndent=0f`, `justifyText=true`, `ttsRate=1.0f`,
  `ttsPitch=1.0f`, `readerTheme="DEFAULT"`, `navigationSideLeft=false`.
- **Диапазоны клампов**, используемые в `coerceIn`/слайдерах: `textSizeRange=14f..30f`,
  `splitRatioRange=0.3f..0.7f`, `horizontalMarginRange=4f..32f`, `ttsRateRange=0.5f..2.0f` (свести к
  фактически используемым; при реализации выверить по существующим `coerceIn`).
- **Потребители:** `ReadingProgressManager` (data→domain допустимо) подставляет `ReadingDefaults.*` в
  getFloat/getString/getBoolean-дефолты; UI data-классы (`ReaderUiState`, `ReaderViewModel.InternalState`,
  `SettingsUiState`) — в дефолты полей; `coerceIn`-вызовы и `SettingsControls` слайдеры — в диапазоны.
**Осознанные фиксы дефолтов (единственные изменения поведения фазы):**
1. **Диапазон шрифта → `14f..30f`** (объединение): слайдер Settings расширяется 24→30, совпадает с
   reader-pinch; никакая возможность не теряется.
2. **`paragraphSpacing` → `18f`** (канон UI): свежая установка получает 18f вместо ошибочных 8f. Юзеры
   с сохранённым значением не затрагиваются (читается сохранённое).

### P25 — Логи и шаринг флоу · Low
**(a)** `presentation/home/HomeViewModel.kt:132` — `catch (_: SecurityException) { }` проглатывает без
следа. Добавить `Log.w(TAG, "takePersistableUriPermission denied", e)` (ввести `TAG`/`Log`, назвать `e`).
**(b)** `presentation/almanac/AlmanacViewModel.kt:42-54` — `dailyMinutes`, `rangeMinutes`, `rangePages`
делают **три** независимых `flatMapLatest { observeDailyMinutes(range.daysBack) }` → три подписки на один
DB-запрос. Свести к одному общему флоу и деривить остальные:
```kotlin
private val dailyMinutesShared = selectedRange
    .flatMapLatest { sessionRepository.observeDailyMinutes(it.daysBack) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
val dailyMinutes = dailyMinutesShared
val rangeMinutes = dailyMinutesShared.map { d -> d.sumOf { it.minutes } }.stateIn(...)
val rangePages = dailyMinutesShared.map { d -> d.sumOf { it.minutes * 2 } }.stateIn(...)
```
Одна подписка на `observeDailyMinutes`. `rangeWords`/`timeByBook`/`timeByLang` — другие источники, не
трогаются.

### Cleanup follow-up (дешёвые хвосты) · Low
- **P14-хвост:** пользовательские записи в Keystore/prefs на тапе — `apiKeyManager.setKey` /
  `translatorEndpoints.setSecondary` в `configureProvider`/`clearProvider` (Reader/Settings VM) выполнять
  на `Dispatchers.Default` (обернуть в `viewModelScope.launch(Dispatchers.Default)` перед
  `refreshTranslatorConfig`, сохранив порядок happens-before: запись → затем refresh). Уточнить по факту при
  планировании — поведение (значение записывается до перечтения) сохранить.
- **P17-хвост:** `TranslatorEndpoints.setLibreTranslateBaseUrl` при `UrlResult.Invalid` сейчас молча не
  сохраняет — добавить `Log.w` с причиной (UI-путь остаётся основным сигналом).
- **KDoc-линки:** в `DriveRepository.kt`/`CatalogSourceClient.kt` два KDoc `[com.example.splitreader.data.…]`
  переписать так, чтобы doc не ссылался на `data.*` FQN (напр. простым текстом), — гигиена доменных доков.

## 3. Декомпозиция (SDD, 5 задач)
1. **P21** — навигация вкладок (`SplitReaderNavHost` + helper).
2. **P22** — убрать `!!` (`HomeScreen`, 2 места).
3. **P24** — `ReadingDefaults` + разводка потребителей (manager, UI-стейты, клампы, слайдеры).
4. **P25** — лог проглоченного исключения (HomeVM) + шаринг daily-minutes (AlmanacVM).
5. **Cleanup** — P14-хвост (off-main записи), P17-хвост (лог), reword 2 KDoc-линка.

## 4. Тестирование
- Полировка UI/навигации/констант — новых unit-тестов почти нет; приёмка через компиляцию + существующие
  тесты + ревью + ручную проверку (нав back-stack, дефолты на свежей установке — как в P14).
- Существующие тесты не ломаются; `:app:testDebugUnitTest` зелёный; `:app:compileDebugKotlin` успешен.

## 5. Приёмка
1. **P21:** таб-переходы используют `popUpTo(start){saveState}/launchSingleTop/restoreState`; «Назад» по
   вкладкам идиоматичен, состояние вкладок сохраняется.
2. **P22:** в `HomeScreen` нет `!!` на `lastBook`/`coverBitmap` (smart-cast через локальные `val`).
3. **P24:** `ReadingDefaults` — единственный источник reading-дефолтов/диапазонов; расхождений `24/30` и
   `8/18` нет; свежая установка даёт `paragraphSpacing=18f`, диапазон шрифта `14..30` везде.
4. **P25:** `SecurityException` логируется; `AlmanacViewModel` держит одну подписку на daily-minutes.
5. **Cleanup:** записи ключей/эндпоинтов на тапе идут вне main-потока; отклонённый `http://`-URL логируется;
   доменные KDoc не ссылаются на `data.*` FQN.
6. Поведение без изменений (кроме двух осознанных фиксов дефолтов P24); сборка + тесты зелёные.

## 6. Риск и охват
- Мелкие, изолированные правки; каждая задача компилируется и зелёная.
- P24 — чистые константы (низкий риск); единственные изменения поведения — два выверенных фикса дефолтов.
- P25(b) — рефактор флоу без смены семантики (те же значения, одна подписка); проверяется ревью.
- **Вне области:** P23 (файлы-гиганты) — отдельный проект позже. Парсер/БД/billing/архитектура (Фазы 0–3)
  не трогаются.
