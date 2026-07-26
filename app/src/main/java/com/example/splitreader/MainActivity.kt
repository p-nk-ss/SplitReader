package com.example.splitreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.splitreader.data.local.TextToSpeechManager
import com.example.splitreader.domain.model.toActivityInfo
import com.example.splitreader.presentation.AppThemeViewModel
import com.example.splitreader.presentation.navigation.SplitReaderNavHost
import com.example.splitreader.presentation.theme.SplitReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val themeViewModel: AppThemeViewModel by viewModels()

    @Inject lateinit var textToSpeechManager: TextToSpeechManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestedOrientation = themeViewModel.orientationLock.value.toActivityInfo()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                themeViewModel.orientationLock.collect { lock ->
                    requestedOrientation = lock.toActivityInfo()
                }
            }
        }
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