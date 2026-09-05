package com.example.splitreader.screenshot

import com.example.splitreader.presentation.almanac.AlmanacScreen
import com.example.splitreader.presentation.auth.AuthScreen
import com.example.splitreader.presentation.catalog.CatalogScreen
import com.example.splitreader.presentation.home.HomeScreen
import com.example.splitreader.presentation.profile.ProfileScreen
import com.example.splitreader.presentation.settings.SettingsScreen
import com.example.splitreader.presentation.theme.ReaderThemeKey
import com.example.splitreader.presentation.words.WordsScreen
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Full-content captures: every screen rendered at a device tall enough to show all of its
 * scrollable content at once.
 *
 * This is deliberately NOT what the user sees. [CompactScreensScreenshotTest] captures the device
 * as it is, fold included; this class exists because that fold hides defects — the Home shelf
 * header sat below it for the whole of Phase 2b and four audits in a row missed it.
 *
 * Screens are rendered bare, without AppShell: the shell has its own goldens, and what is under
 * audit here is content. ReaderContent is excluded on purpose — its "full content" is the text of
 * a book, and the reader is rebuilt in Phase 3.
 */
class FullContentScreenshotTest : ScreenshotTest() {

    @Test
    @Config(qualifiers = PHONE_XTALL)
    fun full_home_paper_1x() = captureScreen("full_home_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        HomeScreen(
            uiState = ScreenFixtures.homeUiStateRich,
            onOpenFilePicker = {},
            onOpenFromLibrary = {},
            onDeleteBook = {},
            onDismissError = {},
        )
    }

    @Test
    @Config(qualifiers = PHONE_TALL)
    fun full_catalog_paper_1x() = captureScreen("full_catalog_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        CatalogScreen(
            uiState = ScreenFixtures.catalogRich,
            driveState = ScreenFixtures.driveStateSignedOut,
            onQueryChange = {},
            onSourceSelected = {},
            onDownload = {},
            onRetry = {},
            onPickFromDrive = {},
            onSignInWithGoogle = {},
        )
    }

    @Test
    @Config(qualifiers = PHONE_TALL)
    fun full_almanac_paper_1x() = captureScreen("full_almanac_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        AlmanacScreen(
            streak = ScreenFixtures.almanacRich.streak,
            dailyMinutes = ScreenFixtures.almanacRich.dailyMinutes,
            rangeMinutes = ScreenFixtures.almanacRich.rangeMinutes,
            rangePages = ScreenFixtures.almanacRich.rangePages,
            rangeWords = ScreenFixtures.almanacRich.rangeWords,
            timeByBook = ScreenFixtures.almanacRich.timeByBook,
            timeByLang = ScreenFixtures.almanacRich.timeByLang,
            selectedRange = ScreenFixtures.almanacRich.selectedRange,
            onSelectRange = {},
            today = ScreenFixtures.almanacRich.today,
        )
    }

    @Test
    @Config(qualifiers = PHONE_XTALL)
    fun full_settings_paper_1x() = captureScreen("full_settings_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        SettingsScreen(
            state = ScreenFixtures.settingsState,
            onSetReaderTheme = {},
            onSetTargetLanguage = {},
            onSetSplitRatio = {},
            onSetShowTranslation = {},
            onSetShowIllustrations = {},
            onSetHorizontalMargin = {},
            onSetOrientationLock = {},
            onSetReadingFont = {},
            onSetTextSize = {},
            onSetLineHeight = {},
            onSetLetterSpacing = {},
            onSetTextIndent = {},
            onSetParagraphSpacing = {},
            onSetJustifyText = {},
            onSelectProvider = {},
            onConfigureProvider = { _, _, _ -> },
            onClearProvider = {},
            onRefreshTranslationUsage = {},
            onResetTranslationUsage = {},
            onDeleteLanguagePack = {},
            onClearCache = {},
            onSetTtsRate = {},
            onSetTtsPitch = {},
            onTestVoice = {},
            onSetPremiumDebug = {},
            onRestorePurchase = {},
        )
    }

    @Test
    @Config(qualifiers = PHONE_TALL)
    fun full_auth_paper_1x() = captureScreen("full_auth_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        AuthScreen(
            state = ScreenFixtures.authUiStateSignIn,
            onNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onToggleMode = {},
            onSubmit = {},
            onGoogle = {},
            onSendPasswordReset = {},
            onBack = {},
        )
    }

    @Test
    @Config(qualifiers = PHONE_TALL)
    fun full_profile_paper_1x() = captureScreen("full_profile_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        ProfileScreen(
            authState = ScreenFixtures.profileAuthStateSignedIn,
            ui = ScreenFixtures.profileAccountUiState,
            onBack = {},
            onSignOut = {},
            onResendVerification = {},
            onRefreshUser = {},
            onDeleteAccount = {},
            onReauthPassword = {},
            onReauthGoogle = {},
            onDismissReauth = {},
        )
    }

    @Test
    @Config(qualifiers = PHONE_TALL)
    fun full_words_paper_1x() = captureScreen("full_words_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        WordsScreen(
            words = ScreenFixtures.wordsRich,
            selectedWord = null,
            langFilter = ScreenFixtures.wordsLangFilterAll,
            query = "",
            onSelectWord = {},
            onClearSelection = {},
            onSetFilter = {},
            onSetQuery = {},
            onUpdateNote = { _, _ -> },
            onDelete = {},
            onSpeak = { _, _ -> },
        )
    }
}
