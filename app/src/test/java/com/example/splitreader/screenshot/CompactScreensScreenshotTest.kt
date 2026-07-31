package com.example.splitreader.screenshot

import com.example.splitreader.presentation.almanac.AlmanacScreen
import com.example.splitreader.presentation.auth.AuthScreen
import com.example.splitreader.presentation.catalog.CatalogScreen
import com.example.splitreader.presentation.profile.ProfileScreen
import com.example.splitreader.presentation.settings.SettingsScreen
import com.example.splitreader.presentation.theme.ReaderThemeKey
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Portrait/compact goldens. Device is overridden per method via [Config] — see the qualifier
 * constants in ScreenshotTest.kt.
 *
 * These five screens are NOT modified by Phase 2b; their goldens exist to audit how the existing
 * layouts survive a 411dp-wide window. They render the bare screen, with no AppShell around it,
 * exactly like the landscape goldens do. Shell-wrapped captures live in the `shell_*` goldens.
 */
class CompactScreensScreenshotTest : ScreenshotTest() {

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun catalog_portrait_paper_1x() = captureScreen(
        "catalog_portrait_paper_1x",
        theme = ReaderThemeKey.PAPER,
        fontScale = 1f,
    ) {
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
    @Config(qualifiers = PHONE_PORTRAIT)
    fun almanac_portrait_paper_1x() = captureScreen(
        "almanac_portrait_paper_1x",
        theme = ReaderThemeKey.PAPER,
        fontScale = 1f,
    ) {
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
        )
    }

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun settings_portrait_paper_1x() = captureScreen(
        "settings_portrait_paper_1x",
        theme = ReaderThemeKey.PAPER,
        fontScale = 1f,
    ) {
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
            onClearCache = {},
            onSetTtsRate = {},
            onSetTtsPitch = {},
            onTestVoice = {},
            onSetPremiumDebug = {},
            onRestorePurchase = {},
        )
    }

    /**
     * Settings rendered on an unrealistically tall device so the whole column fits in one frame.
     *
     * This closes a Phase 2a debt: the Orientation selector sits below the 800dp capture fold on
     * the landscape reference device, so it shipped with zero golden coverage. This is a check on
     * the section's *content*; how Settings actually looks on a phone is `settings_portrait_paper_1x`.
     */
    @Test
    @Config(qualifiers = PHONE_TALL)
    fun settings_tall_orientation_paper_1x() = captureScreen(
        "settings_tall_orientation_paper_1x",
        theme = ReaderThemeKey.PAPER,
        fontScale = 1f,
    ) {
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
            onClearCache = {},
            onSetTtsRate = {},
            onSetTtsPitch = {},
            onTestVoice = {},
            onSetPremiumDebug = {},
            onRestorePurchase = {},
        )
    }

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun auth_portrait_paper_1x() = captureScreen(
        "auth_portrait_paper_1x",
        theme = ReaderThemeKey.PAPER,
        fontScale = 1f,
    ) {
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
    @Config(qualifiers = PHONE_PORTRAIT)
    fun profile_portrait_paper_1x() = captureScreen(
        "profile_portrait_paper_1x",
        theme = ReaderThemeKey.PAPER,
        fontScale = 1f,
    ) {
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
}
