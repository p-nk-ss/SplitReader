package com.example.splitreader.screenshot

import com.example.splitreader.domain.model.AuthState
import com.example.splitreader.domain.model.AuthUser
import com.example.splitreader.domain.model.Book
import com.example.splitreader.domain.model.Bookmark
import com.example.splitreader.domain.model.CatalogBook
import com.example.splitreader.domain.model.CatalogSource
import com.example.splitreader.domain.model.Chapter
import com.example.splitreader.domain.model.Language
import com.example.splitreader.domain.model.SavedWord
import com.example.splitreader.domain.model.TranslationProvider
import com.example.splitreader.domain.model.TranslationState
import com.example.splitreader.domain.model.TranslationUsage
import com.example.splitreader.domain.model.stats.BookMinutes
import com.example.splitreader.domain.model.stats.DailyMinutes
import com.example.splitreader.domain.model.stats.LangMinutes
import com.example.splitreader.domain.usecase.StreakResult
import com.example.splitreader.presentation.almanac.TimeRange
import com.example.splitreader.presentation.auth.AuthMode
import com.example.splitreader.presentation.auth.AuthUiState
import com.example.splitreader.presentation.catalog.CatalogUiState
import com.example.splitreader.presentation.catalog.DriveUiState
import com.example.splitreader.presentation.home.BookItem
import com.example.splitreader.presentation.home.HomeUiState
import com.example.splitreader.presentation.profile.AccountUiState
import com.example.splitreader.presentation.reader.NavigationSide
import com.example.splitreader.presentation.reader.ProviderConfig
import com.example.splitreader.presentation.reader.ReaderUiState
import com.example.splitreader.presentation.reader.TranslatorConfigState
import com.example.splitreader.presentation.reader.WordSelection
import com.example.splitreader.presentation.settings.SettingsUiState
import com.example.splitreader.presentation.theme.ReaderThemeKey
import com.example.splitreader.presentation.theme.ReadingFont
import com.example.splitreader.presentation.words.LangFilter

/**
 * Deterministic, ready-made fake state for the screen screenshot tests (Tasks 6-9). Every fixture is
 * a "settled" (isLoading = false, content present) rendering of its screen's stateless composable —
 * never a loading/skeleton state, since the real skeleton uses an infinite shimmer animation
 * (`presentation.theme.Motion.kt`) that would make Roborazzi goldens non-deterministic.
 *
 * All timestamps are fixed epoch-millis literals derived from [FIXED_TIME_MS]; nothing here calls
 * `System.currentTimeMillis()`, `Date()`, `LocalDate.now()`, or any RNG, so goldens are byte-stable
 * across recording runs and machines.
 */
object ScreenFixtures {

    /** 2023-11-14T22:13:20Z — the single fixed "now" every fixture below is derived from. */
    private const val FIXED_TIME_MS = 1_700_000_000_000L

    // ── Home (home/HomeScreen.kt private HomeScreen) ───────────────────────────
    // HomeScreen(uiState: HomeUiState, onOpenFilePicker, onOpenFromLibrary, onDeleteBook, onDismissError)

    val homeBooks: List<BookItem> = listOf(
        BookItem(
            uri = "file:///books/moby-dick.epub",
            title = "Moby-Dick",
            author = "Herman Melville",
            coverPath = null,
            chapterCount = 135,
            lastChapterIndex = 12,
            isFinished = false,
            lastOpenedAt = FIXED_TIME_MS,
            synopsis = "The voyage of the whaling ship Pequod, narrated by Ishmael.",
            excerpt = "Call me Ishmael. Some years ago—never mind how long precisely...",
        ),
        BookItem(
            uri = "file:///books/pride-and-prejudice.epub",
            title = "Pride and Prejudice",
            author = "Jane Austen",
            coverPath = null,
            chapterCount = 61,
            lastChapterIndex = 0,
            isFinished = false,
            lastOpenedAt = FIXED_TIME_MS - 86_400_000L,
            synopsis = "It is a truth universally acknowledged...",
            excerpt = null,
        ),
        BookItem(
            uri = "file:///books/war-and-peace.epub",
            title = "War and Peace",
            author = "Leo Tolstoy",
            coverPath = null,
            chapterCount = 361,
            lastChapterIndex = 361,
            isFinished = true,
            lastOpenedAt = FIXED_TIME_MS - 172_800_000L,
        ),
        BookItem(
            uri = "file:///books/dracula.epub",
            title = "Dracula",
            author = "Bram Stoker",
            coverPath = null,
            chapterCount = 27,
            lastChapterIndex = 3,
            isFinished = false,
            lastOpenedAt = FIXED_TIME_MS - 259_200_000L,
        ),
        BookItem(
            uri = "file:///books/frankenstein.epub",
            title = "Frankenstein",
            author = "Mary Shelley",
            coverPath = null,
            chapterCount = 24,
            lastChapterIndex = 0,
            isFinished = false,
            lastOpenedAt = FIXED_TIME_MS - 345_600_000L,
        ),
        BookItem(
            uri = "file:///books/sherlock-holmes.epub",
            title = "The Adventures of Sherlock Holmes",
            author = "Arthur Conan Doyle",
            coverPath = null,
            chapterCount = 12,
            lastChapterIndex = 1,
            isFinished = false,
            lastOpenedAt = FIXED_TIME_MS - 432_000_000L,
        ),
    )

    val homeUiStateRich = HomeUiState(
        books = homeBooks,
        isLoading = false,
        errorMessage = null,
        streakDays = 12,
        weeklyMinutes = 140,
        savedWordsThisWeek = 23,
        minutesToday = 18,
        weeklyGoal = 180,
        userName = "Alex",
    )

    val homeUiStateEmpty = HomeUiState(
        books = emptyList(),
        isLoading = false,
        errorMessage = null,
        streakDays = 0,
        weeklyMinutes = 0,
        savedWordsThisWeek = 0,
        minutesToday = 0,
        weeklyGoal = 180,
        userName = null,
    )

    // ── Words (words/WordsScreen.kt public WordsScreen) ────────────────────────
    // WordsScreen(words, selectedWord, langFilter, query, onSelectWord, onSetFilter, onSetQuery,
    //             onUpdateNote, onDelete, onSpeak)

    val wordsRich: List<SavedWord> = listOf(
        SavedWord(
            word = "ineffable",
            sourceLang = "en",
            targetLang = "ru",
            translation = "невыразимый",
            bookUri = "file:///books/moby-dick.epub",
            bookTitle = "Moby-Dick",
            chapterIndex = 3,
            paragraphIndex = 12,
            contextSnippet = "There is some ineffable sadness in the way the whale swims.",
            partOfSpeech = "adjective",
            note = "From chapter 3, the whale-watching scene.",
            id = 1L,
            savedAt = FIXED_TIME_MS,
        ),
        SavedWord(
            word = "querulous",
            sourceLang = "en",
            targetLang = "ru",
            translation = "ворчливый",
            bookUri = "file:///books/pride-and-prejudice.epub",
            bookTitle = "Pride and Prejudice",
            chapterIndex = 1,
            paragraphIndex = 4,
            contextSnippet = "Mrs. Bennet's querulous tone irritated her husband.",
            partOfSpeech = "adjective",
            note = null,
            id = 2L,
            savedAt = FIXED_TIME_MS - 3_600_000L,
        ),
        SavedWord(
            word = "Blutrausch",
            sourceLang = "de",
            targetLang = "en",
            translation = "bloodlust",
            bookUri = "file:///books/dracula.epub",
            bookTitle = "Dracula",
            chapterIndex = 5,
            paragraphIndex = 2,
            contextSnippet = "Ein Blutrausch überkam ihn in der Nacht.",
            partOfSpeech = null,
            note = null,
            id = 3L,
            savedAt = FIXED_TIME_MS - 7_200_000L,
        ),
        SavedWord(
            word = "reverie",
            sourceLang = "en",
            targetLang = "ru",
            translation = "мечтательность",
            bookUri = "file:///books/frankenstein.epub",
            bookTitle = "Frankenstein",
            chapterIndex = 2,
            paragraphIndex = 7,
            contextSnippet = "I was lost in a deep reverie, thinking of my creation.",
            partOfSpeech = "noun",
            note = null,
            id = 4L,
            savedAt = FIXED_TIME_MS - 10_800_000L,
        ),
    )

    val wordsEmpty: List<SavedWord> = emptyList()

    val wordsSelectedWord: SavedWord = wordsRich.first()

    val wordsLangFilterAll: LangFilter = LangFilter.All
    val wordsLangFilterEnglish: LangFilter = LangFilter.Lang("en")

    // ── Catalog (catalog/CatalogScreen.kt private CatalogScreen) ───────────────
    // CatalogScreen(uiState: CatalogUiState, driveState: DriveUiState, onQueryChange,
    //               onSourceSelected, onDownload, onRetry, onPickFromDrive, onSignInWithGoogle)

    val catalogBooks: List<CatalogBook> = listOf(
        CatalogBook(
            source = CatalogSource.GUTENBERG,
            id = "1661",
            title = "The Adventures of Sherlock Holmes",
            author = "Arthur Conan Doyle",
            languages = listOf("en"),
            coverUrl = null,
            epubUrl = "https://www.gutenberg.org/ebooks/1661.epub.images",
        ),
        CatalogBook(
            source = CatalogSource.GUTENBERG,
            id = "84",
            title = "Frankenstein",
            author = "Mary Shelley",
            languages = listOf("en"),
            coverUrl = null,
            epubUrl = "https://www.gutenberg.org/ebooks/84.epub.images",
        ),
        CatalogBook(
            source = CatalogSource.STANDARD_EBOOKS,
            id = "charles-dickens/a-christmas-carol",
            title = "A Christmas Carol",
            author = "Charles Dickens",
            languages = listOf("en"),
            coverUrl = null,
            epubUrl = "https://standardebooks.org/ebooks/charles-dickens/a-christmas-carol/downloads/a-christmas-carol.epub",
        ),
    )

    val catalogRich = CatalogUiState(
        query = "",
        selectedSource = CatalogSource.GUTENBERG,
        books = catalogBooks,
        isLoading = false,
        errorMessage = null,
        downloadingId = null,
        hasSearched = true,
        showLimitDialog = false,
    )

    val catalogEmpty = CatalogUiState(
        query = "zzz-no-match",
        selectedSource = CatalogSource.GUTENBERG,
        books = emptyList(),
        isLoading = false,
        errorMessage = null,
        downloadingId = null,
        hasSearched = true,
        showLimitDialog = false,
    )

    val driveStateSignedOut = DriveUiState(
        isSignedInWithGoogle = false,
        isBusy = false,
        errorMessage = null,
        showLimitDialog = false,
    )

    val driveStateSignedIn = DriveUiState(
        isSignedInWithGoogle = true,
        isBusy = false,
        errorMessage = null,
        showLimitDialog = false,
    )

    // ── Settings (settings/SettingsScreen.kt public SettingsScreen) ────────────
    // SettingsScreen(state: SettingsUiState, on*... 20 callbacks)

    val settingsTranslatorConfig = TranslatorConfigState(
        current = TranslationProvider.MLKIT,
        configs = mapOf(
            TranslationProvider.MLKIT to ProviderConfig(configured = true),
            TranslationProvider.DEEPL to ProviderConfig(configured = false),
        ),
        usage = mapOf(
            TranslationProvider.DEEPL to TranslationUsage(charactersThisMonth = 12_450L, monthlyLimit = 500_000L),
        ),
    )

    val settingsState = SettingsUiState(
        readerTheme = ReaderThemeKey.PAPER,
        targetLanguage = Language.RUSSIAN,
        splitRatio = 0.5f,
        showTranslation = true,
        showIllustrations = true,
        horizontalMargin = 12f,
        readingFont = ReadingFont.SERIF,
        textSize = 16f,
        lineHeightMultiplier = 1.5f,
        letterSpacing = 0f,
        textIndent = 0f,
        paragraphSpacing = 18f,
        justifyText = true,
        translatorProvider = TranslationProvider.MLKIT,
        translatorConfig = settingsTranslatorConfig,
        cachedTranslationCount = 128,
        ttsRate = 1.0f,
        ttsPitch = 1.0f,
        isPremium = false,
    )

    // ── Profile (profile/ProfileScreen.kt public ProfileScreen) ────────────────
    // ProfileScreen(authState: AuthState, ui: AccountUiState, onBack, onSignOut,
    //               onResendVerification, onRefreshUser, onDeleteAccount, onReauthPassword,
    //               onReauthGoogle, onDismissReauth)

    val profileUserVerified = AuthUser(
        uid = "uid-1",
        email = "reader@example.com",
        displayName = "Alex Reader",
        isEmailVerified = true,
        isFromGoogle = false,
    )

    val profileUserUnverified = AuthUser(
        uid = "uid-2",
        email = "newuser@example.com",
        displayName = null,
        isEmailVerified = false,
        isFromGoogle = false,
    )

    val profileAuthStateSignedIn: AuthState = AuthState.SignedIn(profileUserVerified)
    val profileAuthStateUnverified: AuthState = AuthState.SignedIn(profileUserUnverified)
    val profileAuthStateSignedOut: AuthState = AuthState.SignedOut

    val profileAccountUiState = AccountUiState(
        isWorking = false,
        showReauth = false,
    )

    // ── Auth (auth/AuthScreen.kt public AuthScreen) ────────────────────────────
    // AuthScreen(state: AuthUiState, onNameChange, onEmailChange, onPasswordChange, onToggleMode,
    //            onSubmit, onGoogle, onSendPasswordReset, onBack)

    val authUiStateSignIn = AuthUiState(
        mode = AuthMode.SIGN_IN,
        name = "",
        email = "reader@example.com",
        password = "hunter22",
        emailError = null,
        passwordError = null,
        isLoading = false,
        generalError = null,
    )

    val authUiStateRegister = AuthUiState(
        mode = AuthMode.REGISTER,
        name = "Alex Reader",
        email = "newuser@example.com",
        password = "",
        emailError = null,
        passwordError = null,
        isLoading = false,
        generalError = null,
    )

    // ── Reader (reader/ReaderScreen.kt private ReaderContent) ──────────────────
    // ReaderContent(state: ReaderUiState.Success, on*... ~30 callbacks)

    val readerBook = Book(
        title = "Moby-Dick",
        author = "Herman Melville",
        chapters = listOf(
            Chapter(
                index = 0,
                title = "Chapter 1: Loomings",
                paragraphs = listOf(
                    "Call me Ishmael. Some years ago—never mind how long precisely—having little or " +
                        "no money in my purse, and nothing particular to interest me on shore, I " +
                        "thought I would sail about a little and see the watery part of the world.",
                    "It is a way I have of driving off the spleen and regulating the circulation.",
                    "Whenever I find myself growing grim about the mouth; whenever it is a damp, " +
                        "drizzly November in my soul; whenever I find myself involuntarily pausing " +
                        "before coffin warehouses.",
                ),
            ),
            Chapter(
                index = 1,
                title = "Chapter 2: The Carpet-Bag",
                paragraphs = listOf(
                    "I stuffed a shirt or two into my old carpet-bag, tucked it under my arm, and " +
                        "started for Cape Horn and the Pacific.",
                    "Quitting the good city of old Manhatto, I duly arrived in New Bedford.",
                ),
            ),
        ),
        filePath = "file:///books/moby-dick.epub",
        coverPath = null,
        synopsis = "The voyage of the whaling ship Pequod, narrated by Ishmael.",
    )

    val readerBookmarks: List<Bookmark> = listOf(
        Bookmark(
            id = 1L,
            bookUri = "file:///books/moby-dick.epub",
            chapterIndex = 0,
            paragraphIndex = 1,
            label = "Opening line",
            createdAt = FIXED_TIME_MS,
        ),
    )

    val readerWordSelection = WordSelection(
        word = "spleen",
        chapterIndex = 0,
        paragraphIndex = 1,
        startChar = 20,
        endChar = 26,
        translation = "селезёнка",
    )

    val readerTranslatorConfig = TranslatorConfigState(
        current = TranslationProvider.MLKIT,
        configs = mapOf(TranslationProvider.MLKIT to ProviderConfig(configured = true)),
    )

    val readerChapterTranslations: Map<Int, List<String>> = mapOf(
        0 to listOf(
            "Зовите меня Измаил. Несколько лет назад — неважно, сколько именно — не имея почти " +
                "никаких денег в кошельке и ничего особенного, что интересовало бы меня на берегу, " +
                "я решил немного поплавать и посмотреть на водную часть мира.",
            "Это способ, которым я разгоняю сплин и восстанавливаю кровообращение.",
            "Всякий раз, когда я замечаю, что мрачнею у рта; всякий раз, когда в моей душе " +
                "устанавливается сырой, моросящий ноябрь.",
        ),
    )

    val readerContentState = ReaderUiState.Success(
        book = readerBook,
        currentChapterIndex = 0,
        sourceLanguage = Language.ENGLISH,
        targetLanguage = Language.RUSSIAN,
        translationState = TranslationState.Idle,
        chapterTranslations = readerChapterTranslations,
        pendingScrollPosition = -1,
        pendingScrollOffset = 0,
        textSize = 16f,
        lineHeightMultiplier = 1.5f,
        readingFont = ReadingFont.SERIF,
        letterSpacing = 0f,
        textIndent = 0f,
        paragraphSpacing = 18f,
        justifyText = true,
        splitRatio = 0.5f,
        showTranslation = true,
        showIllustrations = true,
        readerTheme = ReaderThemeKey.PAPER,
        navigationSide = NavigationSide.RIGHT,
        horizontalMargin = 12f,
        bookmarks = readerBookmarks,
        isCurrentPositionBookmarked = false,
        wordSelection = null,
        translatorProvider = TranslationProvider.MLKIT,
        translatorConfig = readerTranslatorConfig,
    )

    // ── Almanac (almanac/AlmanacScreen.kt public stateless AlmanacScreen) ──────
    // AlmanacScreen(streak, dailyMinutes, rangeMinutes, rangePages, rangeWords, timeByBook,
    //               timeByLang, selectedRange, onSelectRange)

    val almanacDailyMinutes: List<DailyMinutes> = listOf(
        DailyMinutes("2023-11-08", 32),
        DailyMinutes("2023-11-09", 45),
        DailyMinutes("2023-11-10", 0),
        DailyMinutes("2023-11-11", 60),
        DailyMinutes("2023-11-12", 18),
        DailyMinutes("2023-11-13", 22),
        DailyMinutes("2023-11-14", 40),
    )

    val almanacTimeByBook: List<BookMinutes> = listOf(
        BookMinutes("Moby-Dick", 120),
        BookMinutes("Pride and Prejudice", 85),
        BookMinutes("Dracula", 40),
    )

    val almanacTimeByLang: List<LangMinutes> = listOf(
        LangMinutes("en", 180),
        LangMinutes("de", 45),
        LangMinutes("ru", 20),
    )

    val almanacStreak = StreakResult(current = 12, longest = 30)

    /** Bundles the 8 stateless-`AlmanacScreen` values so a screenshot test can pass one fixture. */
    data class AlmanacFixture(
        val streak: StreakResult,
        val dailyMinutes: List<DailyMinutes>,
        val rangeMinutes: Int,
        val rangePages: Int,
        val rangeWords: Int,
        val timeByBook: List<BookMinutes>,
        val timeByLang: List<LangMinutes>,
        val selectedRange: TimeRange,
    )

    val almanacRich = AlmanacFixture(
        streak = almanacStreak,
        dailyMinutes = almanacDailyMinutes,
        rangeMinutes = 217,
        rangePages = 434,
        rangeWords = 23,
        timeByBook = almanacTimeByBook,
        timeByLang = almanacTimeByLang,
        selectedRange = TimeRange.WEEK,
    )

    val almanacEmpty = AlmanacFixture(
        streak = StreakResult(current = 0, longest = 0),
        dailyMinutes = emptyList(),
        rangeMinutes = 0,
        rangePages = 0,
        rangeWords = 0,
        timeByBook = emptyList(),
        timeByLang = emptyList(),
        selectedRange = TimeRange.WEEK,
    )
}
