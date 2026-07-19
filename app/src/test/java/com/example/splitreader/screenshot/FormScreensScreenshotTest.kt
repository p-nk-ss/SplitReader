package com.example.splitreader.screenshot

import com.example.splitreader.presentation.auth.AuthScreen
import com.example.splitreader.presentation.profile.ProfileScreen
import com.example.splitreader.presentation.settings.SettingsScreen
import com.example.splitreader.presentation.theme.ReaderThemeKey
import org.junit.Test

/**
 * Golden screenshots for the three "form" screens (Settings, Profile, Auth) across the base
 * matrix — palette {PAPER, NIGHT} x fontScale {1f, 1.3f}. All screen callbacks are no-ops: these
 * are pure rendering snapshots, not interaction tests.
 *
 * Profile uses the signed-in + email-verified fixture as its single richest state for the
 * matrix (per the task brief, not multiplied across every auth state).
 */
class FormScreensScreenshotTest : ScreenshotTest() {

    // ── Settings ────────────────────────────────────────────────────────────

    @Test
    fun settings_paper_1x() = captureScreen("settings_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
        SettingsScreen(
            state = ScreenFixtures.settingsState,
            onSetReaderTheme = {},
            onSetTargetLanguage = {},
            onSetSplitRatio = {},
            onSetShowTranslation = {},
            onSetShowIllustrations = {},
            onSetHorizontalMargin = {},
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
    fun settings_night_1x() = captureScreen("settings_night_1x", theme = ReaderThemeKey.NIGHT, fontScale = 1f) {
        SettingsScreen(
            state = ScreenFixtures.settingsState,
            onSetReaderTheme = {},
            onSetTargetLanguage = {},
            onSetSplitRatio = {},
            onSetShowTranslation = {},
            onSetShowIllustrations = {},
            onSetHorizontalMargin = {},
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
    fun settings_paper_13x() = captureScreen("settings_paper_13x", theme = ReaderThemeKey.PAPER, fontScale = 1.3f) {
        SettingsScreen(
            state = ScreenFixtures.settingsState,
            onSetReaderTheme = {},
            onSetTargetLanguage = {},
            onSetSplitRatio = {},
            onSetShowTranslation = {},
            onSetShowIllustrations = {},
            onSetHorizontalMargin = {},
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
    fun settings_night_13x() = captureScreen("settings_night_13x", theme = ReaderThemeKey.NIGHT, fontScale = 1.3f) {
        SettingsScreen(
            state = ScreenFixtures.settingsState,
            onSetReaderTheme = {},
            onSetTargetLanguage = {},
            onSetSplitRatio = {},
            onSetShowTranslation = {},
            onSetShowIllustrations = {},
            onSetHorizontalMargin = {},
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

    // ── Profile ─────────────────────────────────────────────────────────────

    @Test
    fun profile_paper_1x() = captureScreen("profile_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
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
    fun profile_night_1x() = captureScreen("profile_night_1x", theme = ReaderThemeKey.NIGHT, fontScale = 1f) {
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
    fun profile_paper_13x() = captureScreen("profile_paper_13x", theme = ReaderThemeKey.PAPER, fontScale = 1.3f) {
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
    fun profile_night_13x() = captureScreen("profile_night_13x", theme = ReaderThemeKey.NIGHT, fontScale = 1.3f) {
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

    // ── Auth ────────────────────────────────────────────────────────────────

    @Test
    fun auth_paper_1x() = captureScreen("auth_paper_1x", theme = ReaderThemeKey.PAPER, fontScale = 1f) {
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
    fun auth_night_1x() = captureScreen("auth_night_1x", theme = ReaderThemeKey.NIGHT, fontScale = 1f) {
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
    fun auth_paper_13x() = captureScreen("auth_paper_13x", theme = ReaderThemeKey.PAPER, fontScale = 1.3f) {
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
    fun auth_night_13x() = captureScreen("auth_night_13x", theme = ReaderThemeKey.NIGHT, fontScale = 1.3f) {
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
}
