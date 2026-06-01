package com.example.splitreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splitreader.data.local.TextToSpeechManager
import com.example.splitreader.presentation.AppThemeViewModel
import com.example.splitreader.presentation.navigation.SplitReaderNavHost
import com.example.splitreader.presentation.theme.SplitReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val themeViewModel: AppThemeViewModel by viewModels()

    @Inject lateinit var textToSpeechManager: TextToSpeechManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val themeKey by themeViewModel.themeKey.collectAsStateWithLifecycle()
            SplitReaderTheme(readerThemeKey = themeKey) {
                SplitReaderNavHost()
            }
        }
    }

    override fun onDestroy() {
        // Release the shared on-device TTS engine when the app is actually closing,
        // not on configuration-change recreations (the manager is a process-wide singleton).
        if (isFinishing) textToSpeechManager.shutdown()
        super.onDestroy()
    }
}
